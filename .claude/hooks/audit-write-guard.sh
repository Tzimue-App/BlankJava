#!/usr/bin/env bash
# PreToolUse guard of the claude-env-auditor subagent (declared in its frontmatter, so it only
# runs while that subagent is active). Needs bash and jq. Fails closed: any doubt blocks (exit 2).
#   Write|Edit : only inside <project root>/.audits/claude-env/
#   Bash       : read-only allowlist, no redirection, no chaining (pipes only between allowed
#                commands), no argument naming a file of the Read deny rules of .claude/settings.json
#   Agent|Task : only the claude-env-analyst subagent (`Agent(name)` restrictions are ignored in
#                subagent frontmatter, so this hook is what enforces it)
# Allowed calls answer with an explicit "allow" so a background run does not stop on a prompt.
# Fail closed: an unexpected error (an exit status other than 0 and 2) becomes a refusal.
trap 'rc=$?; [ "$rc" -eq 0 ] || [ "$rc" -eq 2 ] || { echo "audit guard: unexpected error (exit $rc)." >&2; exit 2; }' EXIT
hook_dir="$(dirname "${BASH_SOURCE[0]}")"
. "$hook_dir/lib.sh"
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

# ignored_listing: for git status and ls-files, refuses `--ignored` and its prefixes (git accepts a
# unique prefix; from `--i`, `=value` stripped). Reads `w`.
ignored_listing() {
  local a
  for a in "${w[@]:2}"; do
    [[ "$a" == --?* && "--ignored" == "${a%%=*}"* ]] \
      && block "git option '$a' refused (it lists the ignored secret files)."
  done
  return 0
}

# tokenize <command>: splits a one-line command into the words bash would see after quote removal,
# and into pipe segments. Fills `words` and `seg_start`: the index in `words` of the first word of
# each segment, then the word count (segment s ends before words[seg_start[s+1]]). Single quotes
# are literal; double quotes refuse `$`, backquote and backslash; outside quotes, every character
# that would redirect, chain, substitute, expand or glob is refused.
tokenize() {
  local s="$1" n=${#1} i c q="" word="" inword=0
  words=() seg_start=(0)
  end_word() {
    if [ "$inword" = 1 ]; then words+=("$word"); word=""; inword=0; fi
  }
  # end_seg: the segment must have added words; the next one (or the end marker) starts here.
  end_seg() {
    [ "${#words[@]}" -gt "${seg_start[${#seg_start[@]} - 1]}" ] || block "empty pipe segment."
    seg_start+=("${#words[@]}")
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
        end_word; end_seg
        ;;
      '>' | '<' | ';' | '&' | '`' | '$' | '\' | '(' | ')')
        block "redirection, chaining (; & ||), substitution and unquoted \$ ( ) \\ are refused." ;;
      '*' | '?' | '[' | '{' | '}')
        block "unquoted glob and brace patterns (* ? [ { }) are refused: name the files, or use the Glob tool." ;;
      *) word+="$c"; inword=1 ;;
    esac
  done
  [ -z "$q" ] || block "unterminated quote."
  end_word; end_seg
}

# jq helpers shared by the checks below: backslashes as `/`, `.` and `..` resolved.
# shellcheck disable=SC2016
jq_defs='
def slashes: gsub("\\\\"; "/");
def resolve:
  split("/")
  | reduce .[] as $s ([];
      if $s == "" or $s == "." then .
      elif $s == ".." then (if length > 0 and .[-1] != ".." then .[:-1] else . + [$s] end)
      else . + [$s] end)
  | join("/");
'

# jq program reading one word per line (the hook input as $in) and printing the first one that names
# a path matched by a `Read(//...)` deny rule of settings.json, the rule's own folder included
# (`infra/secrets/**` also matches `infra/secrets`).
# Each word is checked whole, from after every `:` or `=` (`HEAD:path`, `--opt=path`) and, for a
# short option (`-f.env`), from after every character of it; with backslashes read as `/`, relative
# to the hook's cwd, case folded (as the Read rules on Windows). Each path is also checked as Win32
# opens it: trailing dots and spaces of a folder or file name dropped (`x.pem.`), and each name cut
# at its first `:` (`x.pem:stream`); then `.` and `..` are resolved. Fails when settings.json has no
# such rule. Glob to regex as in tests/test_claude_settings.py: `*` and `?` stay within a folder, a
# class keeps a leading ^ or ! literal and reads `--` as a range ending at `-`.
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
def candidates:
  . as $t
  | [$t]
    + [range(0; length) as $i | select($t[$i:$i + 1] == ":" or $t[$i:$i + 1] == "=") | $t[$i + 1:]]
    + (if startswith("-") and (startswith("--") | not) then [range(1; length) as $i | $t[$i:]] else [] end);
def names(f): split("/") | map(f) | join("/");
def trimmed: if . == "." or . == ".." then . else sub("[. ]+$"; "") end;
def win32: [., names(trimmed), names(split(":")[0] // ""), names(split(":")[0] // "" | trimmed)][];
($s[0].permissions.deny // []
  | map(select(type == "string" and startswith("Read(//") and endswith(")")) | .[7:-1] | glob_re)) as $res
| if ($res | length) == 0 then error("no Read(//...) deny rule in .claude/settings.json") else . end
| ($in.cwd // "" | slashes) as $c
| first(inputs as $w
    | $w | candidates[] | slashes
    | (if test("^(/|~|[A-Za-z]:/)") or $c == "" then . else $c + "/" + . end) | win32 | resolve
    | . as $p | select(any($res[]; . as $r | $p | test($r; "i")))
    | $w) // empty
'

# jq program reading one word per line (the project root as $root, the hook input as $in) and
# printing the first one that, resolved from the hook's cwd, is outside the project root (a word
# starting with `~` or drive-relative, like `C:x`, counts as outside). `/c/x` is read as `c:/x`; a Windows root compares case folded.
# shellcheck disable=SC2016
outside_root='
def winpath: slashes | sub("^/(?<d>[A-Za-z])/"; "\(.d):/");
($root | winpath | resolve) as $r0
| ($r0 | test("^[A-Za-z]:")) as $win
| (if $win then $r0 | ascii_downcase else $r0 end) as $r
| ($in.cwd // $root | winpath) as $c
| first(inputs as $w
    | $w | winpath
    | if startswith("~") or test("^[A-Za-z]:([^/]|$)") then $w
      else (if test("^(/|[A-Za-z]:/)") then . else $c + "/" + . end) | resolve
        | (if $win then ascii_downcase else . end)
        | select(. != $r and (startswith($r + "/") | not)) | $w
      end) // empty
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
    # The tokenizer is quadratic in the command length: cap it well inside the hook timeout.
    [ "${#cmd}" -le 4096 ] || block "commands over 4096 characters are refused."
    tokenize "$cmd"
    # The path check reads a short-option word from after each of its characters (`-f.env`), which
    # is quadratic in its length: cap the total, or a timed-out hook would let the call through.
    short=0
    for a in "${words[@]}"; do
      [[ "$a" == -* && "$a" != --* ]] && short=$((short + ${#a}))
    done
    [ "$short" -le 64 ] || block "short options over 64 characters in all are refused."
    exempt=() diff_words=()
    # Every pipe segment must be an allowed read-only command.
    for ((s = 0; s + 1 < ${#seg_start[@]}; s++)); do
      w=("${words[@]:seg_start[s]:seg_start[s + 1] - seg_start[s]}")
      for a in "${w[@]}"; do
        # NTFS streams (`x.pem::$DATA`) open a file under another name.
        case "$a" in *::*) block "'::' is refused (NTFS stream)." ;; esac
        # Windows 8.3 short names (ENV~1, SECRET~1) open a file under another name. Not for git:
        # it reads no untracked file content, and revisions need `HEAD~1`.
        [ "${w[0]}" != git ] && [[ "$a" =~ ~[0-9] ]] && block "'$a': '~<digit>' (Windows short name) is refused."
      done
      case "${w[0]}" in
        head | tail | pwd | date) ;;
        wc)
          # --files0-from (or a prefix of it, from `--f`) reads file names the path check never sees.
          for a in "${w[@]:1}"; do
            [[ "$a" == --?* && "--files0-from" == "${a%%=*}"* ]] && block "wc option '$a' refused (it reads a list of file names)."
          done
          ;;
        ls)
          for a in "${w[@]:1}"; do
            [[ "$a" =~ ^-[^-]*R ]] && block "ls -R is refused (it lists the secret folders)."
            [[ "$a" == --?* && "recursive" == "${a#--}"* ]] && block "ls --recursive is refused (it lists the secret folders)."
          done
          ;;
        jq)
          from_file=0 filter_k=0
          for a in "${w[@]:1}"; do
            case "$a" in -L* | --library-path*) block "jq option '$a' refused." ;; esac
            [[ "$a" == --from-file || "$a" =~ ^-[^-]*f ]] && from_file=1
          done
          # The filter is the first argument that is neither an option nor an option's value. It is
          # left out of the path check (`jq .env f.json`) only when every option before it is known.
          k=1
          while [ "$from_file" = 0 ] && [ "$k" -lt "${#w[@]}" ]; do
            a="${w[k]}"
            case "$a" in
              --arg | --argjson | --slurpfile | --rawfile) k=$((k + 3)); continue ;;
              --indent) k=$((k + 2)); continue ;;
              --null-input | --raw-input | --slurp | --compact-output | --raw-output | --raw-output0 | \
                --join-output | --ascii-output | --sort-keys | --color-output | --monochrome-output | \
                --unbuffered | --exit-status | --seq | --stream | --stream-errors | --tab | --binary | \
                --args | --jsonargs) ;;
              -*) [[ "$a" =~ ^-[nrjascCMSeRhV]+$ ]] || break ;;
              *) exempt+=($((seg_start[s] + k))); filter_k=$k; break ;;
            esac
            k=$((k + 1))
          done
          # jq modules (import, include) load files that the path check cannot see. Only the filter
          # is checked when it is known; otherwise (or with -f) every argument is.
          if [ "$filter_k" -gt 0 ]; then kw=("${w[filter_k]}"); else kw=("${w[@]:1}"); fi
          for a in "${kw[@]}"; do
            [[ "$a" =~ (^|[^A-Za-z0-9_$])(import|include)([^A-Za-z0-9_]|$) ]] && block "jq import and include are refused."
          done
          ;;
        claude)
          [ "${w[1]:-}" = "--version" ] && [ "${#w[@]}" -eq 2 ] || block "only 'claude --version' is allowed."
          ;;
        git)
          case "${w[1]:-}" in
            log | rev-parse | check-ignore | show) ;;
            status) ignored_listing ;;
            diff)
              # Implicit --no-index (diff any two files): git checks it when exactly two arguments
              # remain after the leading options (and a `--`), and one is outside the repository.
              k=2 after_dd=0
              while [ "$k" -lt "${#w[@]}" ] && [[ "${w[k]}" == -* ]]; do
                if [ "${w[k]}" = -- ]; then after_dd=1; k=$((k + 1)); break; fi
                k=$((k + 1))
              done
              if [ $((${#w[@]} - k)) -eq 2 ] \
                && { [ "$after_dd" = 1 ] || [[ "${w[k]}" != -* && "${w[k + 1]}" != -* ]]; }; then
                block "git diff with exactly two revisions or paths is refused (implicit --no-index): write 'git diff a..b' or 'git diff a b --'."
              fi
              # Second layer: every path word must stay inside the project, with no `..` segment.
              after_dd=0
              for a in "${w[@]:2}"; do
                if [ "$after_dd" = 0 ] && [ "$a" = -- ]; then after_dd=1; continue; fi
                [ "$after_dd" = 1 ] || [[ "$a" != -* ]] || continue
                [[ "/${a//\\//}/" == */../* ]] && block "git diff: '$a' has a '..' segment."
                diff_words+=("$a")
              done
              ;;
            ls-files)
              ignored_listing
              exclude_standard=0 others=0
              for a in "${w[@]:2}"; do
                [[ "$a" =~ ^-[^-]*i ]] && block "git ls-files -i is refused (it lists the ignored secret files)."
                [ "$a" = --exclude-standard ] && exclude_standard=1
                [[ "$a" =~ ^-[^-]*o || ( "$a" == --?* && "--others" == "${a%%=*}"* ) ]] && others=1
              done
              [ "$others" = 0 ] || [ "$exclude_standard" = 1 ] \
                || block "git ls-files -o is refused without --exclude-standard (it lists the ignored secret files)."
              ;;
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
            case "$a" in
              --output* | --ext-diff | --textconv | --no-index) block "git option '$a' refused." ;;
              # Pathspec globs and magic reach names the literal path check does not see.
              *'*'* | *'?'* | *'['* | :/* | ':!'* | ':^'* | ':('*)
                block "git: '$a' refused (pathspec glob or magic)." ;;
            esac
          done
          ;;
        *) block "command '${w[0]}' is not in the read-only list (ls, wc, head, tail, pwd, date, jq, claude --version, git status|log|diff|show|ls-files|rev-parse|check-ignore|branch listing)." ;;
      esac
    done
    if [ "${#diff_words[@]}" -gt 0 ]; then
      outside="$(printf '%s\n' "${diff_words[@]}" | jq -nRr --arg root "$(project_root)" --argjson in "$input" "$jq_defs$outside_root" 2>/dev/null)" \
        || block "cannot check the git diff paths."
      [ -z "$outside" ] || block "git diff: '$outside' is outside the project (implicit --no-index)."
    fi
    # No word may name a secret file: the Read deny rules do not cover Bash.
    checked=()
    for ((k = 0; k < ${#words[@]}; k++)); do
      [[ " ${exempt[*]} " == *" $k "* ]] || checked+=("${words[k]}")
    done
    # One word per line on stdin (a word has no newline: multi-line commands are refused above).
    denied="$(printf '%s\n' "${checked[@]}" | jq -nRr --slurpfile s "$hook_dir/../settings.json" --argjson in "$input" "$jq_defs$secret_match" 2>/dev/null)" \
      || block "cannot read the Read deny rules of .claude/settings.json."
    [ -z "$denied" ] || block "'$denied' names a file protected by the Read deny rules of .claude/settings.json."
    allow "read-only command"
    ;;

  *) exit 0 ;;
esac
