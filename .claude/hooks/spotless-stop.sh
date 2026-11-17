#!/usr/bin/env bash
# Stop: format and check the Java/Kotlin DSL files of backend/ and e2e/, only for a module
# with a changed .java or .gradle.kts file. Gradle is not started when nothing changed.
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_jq 1

input="$(cat)"
# A previous Stop hook already kept Claude going: do not loop.
[ "$(jq -r '.stop_hook_active // false' <<<"$input")" = "true" ] && exit 0

root="$(project_root)"
cd "$root" || exit 0
changed="$( { git diff --name-only HEAD; git ls-files --others --exclude-standard; } 2>/dev/null)"

failed=0
for module in backend e2e; do
  grep -Eq "^${module}/.*(\.java|\.gradle\.kts)$" <<<"$changed" || continue
  if [ ! -f "$module/gradlew" ]; then
    echo "spotless skipped in $module/: no gradlew found" >&2
    continue
  fi
  # Run through bash: the wrapper is not executable in git for every module.
  {
    (cd "$module" && bash ./gradlew --console=plain -q spotlessApply spotlessCheck) >"${TMPDIR:-/tmp}/spotless-$module.log" 2>&1 || {
      echo "spotless failed in $module/:" >&2
      tail -n 40 "${TMPDIR:-/tmp}/spotless-$module.log" >&2
      failed=1
    }
  }
done
exit $((failed * 2))
