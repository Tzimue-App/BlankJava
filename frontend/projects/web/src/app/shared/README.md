# shared/

Presentational building blocks reused by several features: components, pipes, directives, validators (`validators/account-validators.ts`: the account rules of the backend, for the register and login forms). No business state, no HTTP calls.

- May import: `shared/` only (enforced by `eslint-plugin-boundaries`).
