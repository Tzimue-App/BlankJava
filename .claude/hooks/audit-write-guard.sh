#!/usr/bin/env bash
# PreToolUse guard of the claude-env-auditor subagent (declared in its frontmatter, so it only
# runs while that subagent is active). Needs bash and jq. Fails closed: any doubt blocks (exit 2).
#   Write|Edit : only inside <project root>/.audits/claude-env/
#   Bash       : read-only allowlist, no redirection, no chaining (pipes only between allowed commands)
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
    # No redirection, chaining, substitution, background, backslash escapes or multi-line commands.
    case "$cmd" in
      *'>'* | *'<'* | *';'* | *'&'* | *'`'* | *'$('* | *'\'* | *$'\n'*)
        block "redirection, chaining (; & ||), substitution and multi-line commands are refused." ;;
      *'||'*) block "chaining is refused." ;;
    esac
    # Every pipe segment must be an allowed read-only command.
    IFS='|' read -r -a segments <<<"$cmd"
    for seg in "${segments[@]}"; do
      read -r -a w <<<"$seg"
      [ "${#w[@]}" -gt 0 ] || block "empty pipe segment."
      case "${w[0]}" in
        ls | wc | head | tail | pwd | date | jq) ;;
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
            case "$a" in --output* | --ext-diff | --textconv) block "git option '$a' refused." ;; esac
          done
          ;;
        *) block "command '${w[0]}' is not in the read-only list (ls, wc, head, tail, pwd, date, jq, claude --version, git status|log|diff|show|ls-files|rev-parse|check-ignore|branch listing)." ;;
      esac
    done
    allow "read-only command"
    ;;

  *) exit 0 ;;
esac
