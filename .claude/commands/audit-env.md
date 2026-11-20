---
description: Audit the whole Claude environment (read-only, local report in .audits/claude-env/)
argument-hint: [optional sub-scope, e.g. "hooks" or "skills"]
disable-model-invocation: true
---

Delegate the audit to the `claude-env-auditor` subagent, **in the background**, without analyzing anything yourself. Optional arguments: `$ARGUMENTS` (e.g. a sub-scope). When it finishes, show only its summary and the report path, then wait for my questions so we can discuss the findings.
