# Investment Tracker

Investment Tracker is a local full-stack web application for recording an investment portfolio, tracking CS2 market items, viewing stored price observations and inspecting a public Steam inventory. It also includes account registration with email activation and a restricted Support Panel for administrators and support users.

The repository contains both parts of the application:

- `investment-tracker/` — Spring Boot REST API and database access;
- `react/investment-tracker/` — React web interface;
- `LOCAL_SETUP.md` — complete setup, backup and recovery instructions.
- `docs/DATABASE_MIGRATIONS.md` — real MySQL migrations, isolated tests and safe existing-database adoption.
- `docs/AUTH_SECURITY.md` — verification-code safeguards, limits and deployment boundaries.
- `docs/API_VALIDATION.md` — request limits, ownership/type boundaries and verification results.
- `docs/EXTERNAL_SERVICES.md` — external HTTP limits, atomic price refreshes and background-job policies.
- `docs/DAILY_PRICE_HISTORY.md` — daily price observations, stale-publication protection and existing-database adoption.
- `docs/INVENTORY_CALCULATOR.md` — bounded Steam inventory loading, quantity/price semantics and verification.
- `docs/FRONTEND_REQUEST_LIFECYCLE.md` — request cancellation, stale-response guards, polling and frontend verification.
- `docs/STRUCTURAL_REFACTOR.md` — frontend/backend responsibility boundaries, shared charts and verification.
- [docs/CODEBASE_STRUCTURE.md](docs/CODEBASE_STRUCTURE.md) — source tree, feature ownership, naming and shared code.
- [docs/BACKEND_REUSE.md](docs/BACKEND_REUSE.md) — shared backend pagination, response/cookie builders and provider-header parsing.
- [docs/FRONTEND_TOOLING.md](docs/FRONTEND_TOOLING.md) — Vite/Vitest setup, supported Node versions and migration verification.
- [docs/FINAL_VERIFICATION.md](docs/FINAL_VERIFICATION.md) — integrated checks, GitLab CI and remaining delivery boundaries.
- [docs/MANUAL_VERIFICATION.md](docs/MANUAL_VERIFICATION.md) — short browser acceptance checklist (Bulgarian).
- [docs/CATALOGUE_SEARCH.md](docs/CATALOGUE_SEARCH.md) — shared word matching, relevance ranking, caching and verification.
- [docs/GITHUB_ACTIONS.md](docs/GITHUB_ACTIONS.md) — GitHub verification workflow and safe history-transfer boundaries.
- `docs/REFACTOR_PLAN.md` — historical refactoring plan (not the production roadmap).
- [docs/PRODUCTION_PLAN.md](docs/PRODUCTION_PLAN.md) — the current sequential production-readiness roadmap.
- [docs/PRODUCTION_P0_PROGRESS.md](docs/PRODUCTION_P0_PROGRESS.md) — security, migration and runtime verification checkpoints.
- [docs/RUNTIME_UPGRADE.md](docs/RUNTIME_UPGRADE.md) — supported runtime baseline and dependency review.

## Main functionality

- Account registration, email activation, sign-in and password recovery.
- Short-lived access tokens and server-managed refresh sessions.
- Portfolio records with filtering, pagination and transaction history.
- CS2 item catalogue synchronized from an external catalogue source.
- Price tracking based on CSGOTrader 24-hour average prices.
- Locally accumulated price observations for 7-day, 1-month and 3-month charts.
- Steam inventory value calculation for public inventories.
- Contact requests submitted by authenticated users.
- Restricted Support Panel with roles, session management, maintenance operations and an audit log.

## Technology versions

| Component | Version used by the project |
|---|---:|
| Java | JDK 21 |
| Spring Boot | 4.1.1 |
| Gradle | 8.14.6 checksum-verified wrapper included in the repository |
| MySQL | 8.4.11 LTS target; existing local 8.0.37 also verified |
| Node.js | 24 LTS, at least 24.15 |
| React | 18.3.1 |
| Vite | 8.3.4 |
| Vitest | 5.0.3 |
| Chart.js | 4.5.1 |

Gradle does not have to be installed separately because the project contains the Gradle Wrapper. MySQL Workbench and development IDEs are optional.

## Quick start

For a clean Windows computer, follow [LOCAL_SETUP.md](LOCAL_SETUP.md). It explains:

1. which programs and versions to install;
2. how to clone the repository;
3. how to create the MySQL database and database user;
4. how to configure secrets without committing them;
5. how to start the backend and frontend;
6. how to create or restore an administrator account;
7. how to back up and restore the database.

The local application normally uses:

- frontend: `http://localhost:3000`;
- backend: `http://localhost:8080`;
- local API documentation: `http://localhost:8080/swagger-ui.html`.

## Configuration and secrets

Real credentials are intentionally not stored in Git. The backend requires local values for the database password, mail account and JWT signing secret. Safe templates are available in:

- `investment-tracker/.env.example`;
- `react/investment-tracker/.env.example`.

The backend does **not** automatically load a `.env` file. Configure its values as operating-system environment variables or in the IDE run configuration. The frontend can load its own ignored `.env` file.

Never commit any of the following:

- MySQL passwords or database exports;
- Gmail account passwords or Gmail App Passwords;
- JWT signing secrets;
- recovery codes, tokens or private user data.

## Running tests

Backend tests from `investment-tracker/`:

```powershell
.\gradlew.bat test
```

Frontend checks from `react/investment-tracker/`:

```powershell
npm ci
npm run lint
npm test
npm run build
npm run smoke:build
```

`npm run verify` runs all four frontend checks. The smoke check serves the built
files temporarily on loopback; it does not start the backend or use real data.
GitLab CI and GitHub Actions run frontend/backend verification and collect test
reports after an approved push to the respective site. Neither deploys the
application. See [GITHUB_ACTIONS.md](docs/GITHUB_ACTIONS.md) for the second hosting
destination and explicit dual-push commands, and
[FINAL_VERIFICATION.md](docs/FINAL_VERIFICATION.md) for coverage and limitations.

## External data

An internet connection is required for catalogue synchronization, price updates, currency conversion, email delivery and Steam public-inventory requests. Price values are external market estimates and are not guaranteed to match a Steam Community Market listing at the same moment.

## Database persistence

The database is local and is not part of the Git repository. Cloning the code onto another computer creates a new empty installation unless a MySQL backup is restored. See the backup and recovery section in [LOCAL_SETUP.md](LOCAL_SETUP.md).

Flyway manages schema changes by default and Hibernate only validates the schema.
New empty databases receive the versioned SQL automatically. Existing non-empty
databases require [the backup, restore-test and explicit adoption procedure](docs/DATABASE_MIGRATIONS.md)
before startup; automatic baseline and destructive clean are disabled. The
developer database completed this procedure on 2026-10-21. This does not adopt
other installations automatically. A Windows backup helper is available at
`scripts/Backup-LocalDatabase.ps1`; never commit the private exports it produces.

## Deployment status

The documented configuration is intended for local development and demonstration. A public deployment additionally requires HTTPS, production secrets, a restricted database user, secure cookies, explicit CORS origins and managed database migrations.
