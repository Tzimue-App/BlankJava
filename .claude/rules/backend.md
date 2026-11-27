---
paths:
  - "backend/**"
---

# Backend

- A public route goes into `SecurityConfig.PUBLIC_ROUTES` and into `PUBLIC` of `SecurityIntegrationTest`, and its operation declares `security: []` (`backend/README.md`, *Adding an endpoint*).
- Slice tests name their controller (`@WebMvcTest(MyController.class)`) and use `@ApiSecurity` for routes that go through the access rules.
- MockMvc tests of an API route add `.andExpect(ContractValidation.matchesContract())`.
- Integration tests carry `@Tag("integration")`; they run with `./gradlew integrationTest`, not `test`.
- Log lines identify a user by id, never by e-mail; mask any e-mail that may reach a log with `LogMasking`; never log a password, token or key.
- A schema change is a new `V<n>__<name>.sql` in `src/main/resources/db/migration/`; never edit an existing migration.
