#!/usr/bin/env bash
# PostToolUse (Edit|Write): run Prettier on the edited file when it is under frontend/.
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

input="$(cat)"
file="$(jq -r '.tool_input.file_path // empty' <<<"$input")"
[ -n "$file" ] || exit 0

rel="$(relative_target "$file")"
case "$rel" in
  frontend/node_modules/*|frontend/dist/*|frontend/coverage/*|frontend/.angular/*) exit 0 ;;
  frontend/*) ;;
  *) exit 0 ;;
esac
[ -f "$(project_root)/$rel" ] || exit 0

cd "$(project_root)/frontend" || exit 0
[ -x node_modules/.bin/prettier ] || [ -x node_modules/.bin/prettier.cmd ] || exit 0
# Files Prettier does not handle or that are ignored are skipped silently.
if ! out="$(npx --no-install prettier --write --log-level warn --ignore-unknown "${rel#frontend/}" 2>&1)"; then
  echo "$out" >&2
  exit 1
fi
exit 0
