---
paths:
  - ".github/workflows/**"
---

# GitHub Actions workflows

- `pr-gate` is the only check required by the rulesets: a new job of the pull request suite goes into its `needs`, never into the rulesets.
- Tool versions are pinned (`spectral-cli@6.17.0`, `ng-openapi-gen@1.1.0`, `java-version`, `python-version`); an API tool version changes in every place `api/README.md` (*Pinned tools*) lists, in the same PR.
- `permissions` stay minimal: grant a new scope on the job that needs it, not on the whole workflow.
- Never print a secret: pass it through `env:`, never `echo` it or put it in a command line or a step summary.
