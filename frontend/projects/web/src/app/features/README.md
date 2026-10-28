# features/

One folder per business feature (`home`, then `accounts`, `profile`…), each with its own routes file, lazy-loaded from `app.routes.ts` with `loadChildren`. Inside a feature: pages (route components), components, data-access services, models.

- May import: its own folder, `core/`, `shared/`.
- **Never imports another feature** (enforced by `eslint-plugin-boundaries`). Something two features need moves to `shared/` (UI) or `core/` (services).
