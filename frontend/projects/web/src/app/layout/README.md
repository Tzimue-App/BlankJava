# layout/

Application frame: `shell/` (header, routed content, footer), `footer/` (versions of the running system), `not-found/` (unknown routes).

- May import: `core/`, `shared/`.
- Never imports `features/` (enforced by `eslint-plugin-boundaries`).
