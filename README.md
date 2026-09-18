# Investment Tracker

Investment Tracker is a local full-stack web application for recording an investment portfolio, tracking CS2 market items, viewing stored price observations and inspecting a public Steam inventory. It also includes account registration with email activation and a restricted Support Panel for administrators and support users.

The repository contains both parts of the application:

- `investment-tracker/` — Spring Boot REST API and database access;
- `react/investment-tracker/` — React web interface;
- `LOCAL_SETUP.md` — complete setup, backup and recovery instructions.

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
| Spring Boot | 3.5.16 |
| Gradle | 8.8 wrapper included in the repository |
| MySQL | 8.0 or newer |
| Node.js | 20.x |
| React | 18.3.1 |
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

Frontend production build from `react/investment-tracker/`:

```powershell
npm ci
npm run build
```

## External data

An internet connection is required for catalogue synchronization, price updates, currency conversion, email delivery and Steam public-inventory requests. Price values are external market estimates and are not guaranteed to match a Steam Community Market listing at the same moment.

## Database persistence

The database is local and is not part of the Git repository. Cloning the code onto another computer creates a new empty installation unless a MySQL backup is restored. See the backup and recovery section in [LOCAL_SETUP.md](LOCAL_SETUP.md).

## Deployment status

The documented configuration is intended for local development and demonstration. A public deployment additionally requires HTTPS, production secrets, a restricted database user, secure cookies, explicit CORS origins and managed database migrations.
