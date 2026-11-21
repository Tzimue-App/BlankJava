---
paths:
  - "frontend/**"
---

# Frontend

- Respect the import boundaries of `frontend/README.md` (*Structure*): `core/`, `shared/`, `layout/`, `features/<name>/`; `npm run lint` checks them.
- Browser globals (`window`, `localStorage`, `navigator`…) only through `core/platform/tokens.ts`.
- Every refresh goes through `AuthSession.refresh()` (Web Lock across tabs, `frontend/README.md`, *Session*); never call the refresh endpoint directly.
- The access token stays in memory in `AuthSession`: never in `localStorage`, `sessionStorage` or a cookie.
- Values are interpolated, never bound as HTML (`[innerHTML]`, `bypassSecurityTrust*`).
- Every field, button and error message has a `data-testid` for the Selenium journeys.
- Run one spec with `npm test -- …`, not `npx ng test`, so the generated client exists (`CLAUDE.md`).
