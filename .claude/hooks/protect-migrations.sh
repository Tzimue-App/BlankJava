#!/usr/bin/env bash
# PreToolUse (Edit|Write): block any change to an existing Flyway migration. A new file is allowed.
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_jq 2

input="$(cat)"
file="$(jq -r '.tool_input.file_path // empty' <<<"$input")"
[ -n "$file" ] || exit 0

rel="$(relative_target "$file")"
case "$rel" in
  backend/src/main/resources/db/migration/*) ;;
  *) exit 0 ;;
esac

if [ -e "$(project_root)/$rel" ]; then
  echo "Blocked: ${rel##*/} is an existing Flyway migration and migrations are forward-only (an edited migration breaks every database that already applied it). Add a new V<n>__<name>.sql instead." >&2
  exit 2
fi
exit 0
