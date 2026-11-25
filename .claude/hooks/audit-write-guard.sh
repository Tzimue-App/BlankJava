#!/usr/bin/env bash
# PreToolUse guard of the claude-env-auditor subagent (declared in its frontmatter, so it only
# runs while that subagent is active). Needs bash and jq. Fails closed: any doubt blocks (exit 2).
#   Write|Edit : only inside <project root>/.audits/claude-env/
#   Bash       : read-only allowlist, no redirection, no chaining (pipes only between allowed
#                commands), no argument naming a file of the Read deny rules of .claude/settings.json
#   Agent|Task : only the claude-env-analyst subagent (`Agent(name)` restrictions are ignored in
#                subagent frontmatter, so this hook is what enforces it)
# Allowed calls answer with an explicit "allow" so a background run does not stop on a prompt.
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_jq 2

input="$(cat)"
block() { echo "audit guard: $1" >&2; exit 2; }

# Fail closed: unparseable input or a missing tool name is refused, never waved through.
tool="$(jq -r '.tool_name // empty' <<<"$input" 2>/dev/null)" || block "hook input is not valid JSON."
[ -n "$tool" ] || block "hook input has no tool_name."
allow() {
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"audit guard: %s"}}\n' "$1"
  exit 0
}

# tokenize <command>: splits a one-line command into the words bash would see after quote removal,
# and into pipe segments. Fills `words` and `word_seg` (segment index of each word), sets
# `seg_count`. Single quotes are literal; double quotes refuse `$`, backquote and backslash; outside
# quotes, every character that would redirect, chain, substitute, expand or glob is refused.
tokenize() {
  local s="$1" n=${#1} i c q="" word="" inword=0 seg_words=0
  words=() word_seg=() seg_count=0
  end_word() {
    if [ "$inword" = 1 ]; then
      words+=("$word"); word_seg+=("$seg_count"); seg_words=$((seg_words + 1)); word=""; inword=0
    fi
  }
  for ((i = 0; i < n; i++)); do
    c="${s:i:1}"
    if [ "$q" = "'" ]; then
      if [ "$c" = "'" ]; then q=""; else word+="$c"; fi
      continue
    fi
    if [ "$q" = '"' ]; then
      case "$c" in
        '"') q="" ;;
        '$' | '`' | '\') block "\$, backquote and backslash are refused inside double quotes (use single quotes)." ;;
        *) word+="$c" ;;
      esac
      continue
    fi
    case "$c" in
      ' ' | $'\t') end_word ;;
      "'" | '"') q="$c"; inword=1 ;;
      '|')
        [ "${s:i+1:1}" = '|' ] && block "chaining is refused."
        end_word
        [ "$seg_words" -gt 0 ] || block "empty pipe segment."
        seg_count=$((seg_count + 1)); seg_words=0
        ;;
      '>' | '<' | ';' | '&' | '`' | '$' | '\' | '(' | ')')
        block "redirection, chaining (; & ||), substitution and unquoted \$ ( ) \\ are refused." ;;
      '*' | '?' | '[' | '{' | '}')
        block "unquoted glob and brace patterns (* ? [ { }) are refused: name the files, or use the Glob tool." ;;
      *) word+="$c"; inword=1 ;;
    esac
  done
  [ -z "$q" ] || block "unterminated quote."
  end_word
  [ "$seg_words" -gt 0 ] || block "empty pipe segment."
  seg_count=$((seg_count + 1))
}

# jq program reading one word per line and printing the first one that names a path matched by a
# `Read(//...)` deny rule of settings.json, the rule's own folder included (`infra/secrets/**` also
# matches `infra/secrets`).
# Each word is checked whole and from after every `:` or `=` (`HEAD:path`, `--opt=path`), with
# backslashes read as `/`, relative to the hook's cwd, `.` and `..` resolved, case folded (as the
# Read rules on Windows). Fails when settings.json has no such rule. Glob to regex as in
# tests/test_claude_settings.py: `*` and `?` stay within a folder, a class keeps a leading ^ or !
# literal and reads `--` as a range ending at `-`.
# shellcheck disable=SC2016
secret_match='
def seg_re:
  [scan("\\[[^\\]]*\\]|.")]
  | map(if startswith("[") and length > 1 and endswith("]") then
          "[" + (.[1:-1] | gsub("--"; "-\\-") | if startswith("^") or startswith("!") then "\\" + . else . end) + "]"
        elif . == "*" then "[^/]*"
        elif . == "?" then "[^/]"
        elif test("^[A-Za-z0-9_]$") or (explode[0] > 127) then .
        else "\\" + . end)
  | join("");
def glob_re:
  split("/") as $p | ($p | length) as $n
  | reduce range(0; $n) as $i ("";
      . + (if $p[$i] == "**" then
             (if $i == 0 then "(?:^|.*/)" elif $i == $n - 1 then "(?:/.*)?" else "(?:/[^/]*)*/" end)
           else
             (if $i > 0 and $p[$i - 1] != "**" then "/" else "" end) + ($p[$i] | seg_re)
           end))
  | "^" + . + "$";
def slashes: gsub("\\\\"; "/");
def resolve:
  split("/")
  | reduce .[] as $s ([];
      if $s == "" or $s == "." then .
      elif $s == ".." then (if length > 0 and .[-1] != ".." then .[:-1] else . + [$s] end)
      else . + [$s] end)
  | join("/");
def candidates:
  . as $t | [$t] + [range(0; length) as $i | select($t[$i:$i + 1] == ":" or $t[$i:$i + 1] == "=") | $t[$i + 1:]];
($s[0].permissions.deny // []
  | map(select(type == "string" and startswith("Read(//") and endswith(")")) | .[7:-1] | glob_re)) as $res
| if ($res | length) == 0 then error("no Read(//...) deny rule in .claude/settings.json") else . end
| ($cwd | slashes) as $c
| first(inputs as $w
    | $w | candidates[] | slashes
    | (if test("^(/|~|[A-Za-z]:/)") or $c == "" then . else $c + "/" + . end) | resolve
    | . as $p | select(any($res[]; . as $r | $p | test($r; "i")))
    | $w) // empty
'

case "$tool" in
  Write | Edit)
    file="$(jq -r '.tool_input.file_path // empty' <<<"$input")"
    [ -n "$file" ] || block "no target path."
    rel="$(relative_target "$file")"
    case "$rel" in
      *..*) block "'..' in the path is refused: $rel" ;;
      .audits/claude-env/?*) allow "write inside .audits/claude-env/" ;;
      *) block "writes are limited to .audits/claude-env/ (target: $rel)." ;;
    esac
    ;;

  Agent | Task)
    sub="$(jq -r '.tool_input.subagent_type // empty' <<<"$input")"
    [ "$sub" = "claude-env-analyst" ] || block "only the claude-env-analyst subagent may be spawned (asked: '${sub:-none}')."
    allow "spawn of claude-env-analyst"
    ;;

  Bash)
    cmd="$(jq -r '.tool_input.command // empty' <<<"$input")"
    [ -n "$cmd" ] || block "empty command."
    case "$cmd" in *$'\n'*) block "multi-line commands are refused." ;; esac
    tokenize "$cmd"
    # Every pipe segment must be an allowed read-only command.
    for ((s = 0; s < seg_count; s++)); do
      w=()
      for ((k = 0; k < ${#words[@]}; k++)); do
        [ "${word_seg[k]}" -eq "$s" ] && w+=("${words[k]}")
      done
      case "${w[0]}" in
        ls | wc | head | tail | pwd | date) ;;
        jq)
          # jq modules (import, include, -L) load files that the path check below cannot see.
          for a in "${w[@]:1}"; do
            case "$a" in -L* | --library-path*) block "jq option '$a' refused." ;; esac
            [[ "$a" =~ (^|[^A-Za-z0-9_$])(import|include)([^A-Za-z0-9_]|$) ]] && block "jq import and include are refused."
          done
          ;;
        claude)
          [ "${w[1]:-}" = "--version" ] && [ "${#w[@]}" -eq 2 ] || block "only 'claude --version' is allowed."
          ;;
        git)
          case "${w[1]:-}" in
            status | log | rev-parse | check-ignore | diff | show | ls-files) ;;
            branch)
              for a in "${w[@]:2}"; do
                case "$a" in
                  --show-current | --list | -a | -r | -v | -vv) ;;
                  *) block "git branch: argument '$a' refused (read-only listing only)." ;;
                esac
              done
              ;;
            *) block "git subcommand '${w[1]:-}' is not in the read-only list." ;;
          esac
          for a in "${w[@]}"; do
            case "$a" in --output* | --ext-diff | --textconv | --no-index) block "git option '$a' refused." ;; esac
          done
          ;;
        *) block "command '${w[0]}' is not in the read-only list (ls, wc, head, tail, pwd, date, jq, claude --version, git status|log|diff|show|ls-files|rev-parse|check-ignore|branch listing)." ;;
      esac
    done
    # No word may name a secret file: the Read deny rules do not cover Bash.
    settings="$(dirname "${BASH_SOURCE[0]}")/../settings.json"
    cwd="$(jq -r '.cwd // empty' <<<"$input")"
    # One word per line on stdin (a word has no newline: multi-line commands are refused above).
    denied="$(printf '%s\n' "${words[@]}" | jq -nRr --slurpfile s "$settings" --arg cwd "$cwd" "$secret_match" 2>/dev/null)" \
      || block "cannot read the Read deny rules of .claude/settings.json."
    [ -z "$denied" ] || block "'$denied' names a file protected by the Read deny rules of .claude/settings.json."
    allow "read-only command"
    ;;

  *) exit 0 ;;
esac
