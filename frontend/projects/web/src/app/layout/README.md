# layout/

Application frame: `shell/` (header, routed content, footer), `header/` (brand, _Log out_ while logged in), `footer/` (versions of the running system), `not-found/` (unknown routes).

- May import: `core/`, `shared/`.
- Never imports `features/` (enforced by `eslint-plugin-boundaries`).
