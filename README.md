# BlankJava

Full-stack Java foundation: a Spring Boot REST API, an Angular frontend and a Selenium end-to-end test suite, running locally with Docker Compose. The first feature is user accounts: **register, log in, and see who you are logged in as**.

| Component | Stack | Version file |
|---|---|---|
| `api/` | OpenAPI 3.1 contract (source of truth for backend and frontend) | `api/openapi.yaml` (`info.version`) |
| `backend/` | Java 25, Spring Boot 4, Spring Security (JWT RS256, Argon2id), Spring Data JPA, Flyway, PostgreSQL | `backend/gradle.properties` |
| `frontend/` | Angular multi-project workspace, feature-based and lazy-loaded, Vitest, served by Nginx (TLS) | `frontend/package.json` |
| `e2e/` | Selenium 4, JUnit, Page Objects, Allure report | `e2e/gradle.properties` |
| *project* | Release version and metadata | `package.json` |

- Architecture, security, logging, testing and delivery: [docs/technical_analysis.md](docs/technical_analysis.md)
- Milestones and issues: [docs/roadmap.md](docs/roadmap.md)
- One-time GitHub configuration: [docs/github_setup.md](docs/github_setup.md)

## How work flows

```text
dev ──▶ v0.1.7-a (milestone branch) ──▶ feat/14-register-page (one branch per issue)
         ▲            │                          │
         │            └──── PR, human review ◀───┘
         └── PR + Selenium report + human review ──▶ dev ──▶ test ──▶ main
```

- Start a milestone from `dev`:

```bash
python bump_version.py --start-milestone 0.1.7
```

- Each issue PR fills the `[Unreleased]` section of [CHANGELOG.md](CHANGELOG.md) and sets `Bump-<component>:` for each component it changes.
- Merges into `dev`, `test` and `main` release `vX.Y.Z-a`, `vX.Y.Z-b` and `vX.Y.Z` automatically (e.g. `v0.1.7-a` → `v0.1.7-b` → `v0.1.7`).
- Issues follow [.github/ISSUE_GUIDELINES.md](.github/ISSUE_GUIDELINES.md).
