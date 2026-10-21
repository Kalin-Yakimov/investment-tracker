# Investment Tracker — clean Windows setup and recovery

This guide is sufficient to rebuild the local development environment after reinstalling Windows or moving the project to another computer. Commands use PowerShell unless a step explicitly says **Command Prompt**.

No real passwords, tokens, email addresses or database data belong in this repository.

## 1. Software to install

Install the following tools before cloning the project:

| Software | Required version | Purpose |
|---|---:|---|
| Git for Windows | Current stable version | Clone and update the repository |
| Java JDK | 21 | Run and build the Spring Boot backend |
| Node.js | 24 LTS, at least 24.15 | Run and build the React frontend |
| npm | Included with Node.js 24 | Install frontend dependencies |
| MySQL Server | 8.4.11 LTS recommended | Store application data |
| MySQL Workbench | Optional | Graphical database administration |

IntelliJ IDEA and Visual Studio Code are optional. The project can be started entirely from two PowerShell windows.

Verify the installations:

```powershell
git --version
java -version
node --version
npm --version
mysql --version
```

Java must report version 21. Use Node.js 24 LTS (24.15 or newer). Node.js 20 is
not supported by the current frontend toolchain. A compatible Node.js 22.22.2+
installation is also accepted, but Node.js 24 LTS is recommended for a new setup.
Gradle does not need a separate installation because `gradlew.bat` downloads
Gradle 8.14.6 when it is first used and verifies its distribution checksum.
Spring Boot is 4.1.1. Existing MySQL 8.0.37 was also verified, but it is not the
recommended target for a fresh production installation. The upgrade does not
replace a system MySQL service automatically.

Download the Windows x64 `.msi` installer from [the official Node.js site](https://nodejs.org/en/download),
choose the 24 LTS line and keep the normal installer defaults. After installing,
close and reopen PowerShell and your IDE before checking `node --version`.
Only a normal Node.js installation is required; no machine-specific runtime paths are configured.

## 2. Clone the repository

Choose a working folder and run:

```powershell
git clone https://gitlab.com/yakimovs/Investment-tracker.git
cd Investment-tracker
```

If the GitLab repository is private, GitLab will require authentication. Use Git Credential Manager, SSH, or a GitLab access token. Do not put an access token inside the clone URL saved in scripts or documentation.

## 3. Create the MySQL database

Start MySQL Server and connect through MySQL Workbench or the MySQL command-line client.

For local development, create the database and a dedicated application user. Replace the password placeholder locally; do not copy the real value into this repository.

```sql
CREATE DATABASE investment
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'investment_app'@'localhost'
  IDENTIFIED BY 'REPLACE_WITH_A_STRONG_LOCAL_PASSWORD';

GRANT ALL PRIVILEGES ON investment.*
  TO 'investment_app'@'localhost';

FLUSH PRIVILEGES;
```

Using the MySQL `root` account also works locally, but a dedicated user is safer and makes the required permissions clearer.

Do not manually create application tables. Flyway creates them from versioned
SQL and Hibernate uses `validate`, never automatic `update`. The database itself
must already exist. An existing non-empty database must first pass the explicit
adoption procedure in [DATABASE_MIGRATIONS.md](docs/DATABASE_MIGRATIONS.md).
The user granted full schema permissions above is a **local-development**
convenience, not the production runtime privilege policy. Production separates
migration credentials from the restricted runtime account.

## 4. Prepare the required private values

The backend needs these values:

| Variable | Required | Local example or meaning |
|---|---|---|
| `DB_URL` | Optional | Defaults to `jdbc:mysql://localhost:3306/investment` |
| `DB_USERNAME` | Recommended | `investment_app`, or another local MySQL user |
| `DB_PASSWORD` | Yes | Password chosen for the MySQL user |
| `MAIL_USERNAME` | Yes | Gmail address used by the application |
| `MAIL_PASSWORD` | Yes | Gmail App Password, not the normal Gmail password |
| `JWT_SECRET` | Yes | Random Base64 value containing at least 32 random bytes |
| `ADMIN_EMAIL` | Recommended | Application account that should receive the `ADMIN` role |
| `CORS_ALLOWED_ORIGINS` | Optional | Defaults to `http://localhost:3000` |
| `APP_FRONTEND_URL` | Optional | Defaults to `http://localhost:3000` |
| `AUTH_COOKIE_SECURE` | Optional locally | Defaults to `false`; production must use `true` with HTTPS |

The complete safe template is `investment-tracker/.env.example`. It contains names and placeholders only.

### Generate a JWT secret

Generate a new secret on the computer where the application will run:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtGenerator.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$jwtGenerator.Dispose()
```

Save the printed value in a password manager and set it as `JWT_SECRET`. Do not commit or send it. Generating a new JWT secret after reinstalling is valid, but all tokens issued with the old secret become invalid.

### Prepare Gmail for application mail

Use a dedicated Gmail account for the application when possible.

1. Enable 2-Step Verification for the Gmail account.
2. Open Google Account → Security → App passwords.
3. Create a new App Password for Investment Tracker.
4. Set the Gmail address as `MAIL_USERNAME`.
5. Set the generated 16-character App Password as `MAIL_PASSWORD`.

The App Password is different from the normal Google account password. Google shows it only once, but it can be revoked and replaced later.

## 5. Configure environment variables on Windows

The backend does not automatically read `investment-tracker/.env.example` or a newly created `.env` file.

For persistent user variables on Windows 10:

1. Open Start and search for `Edit environment variables for your account`.
2. In **User variables**, select **New**.
3. Add each required name and its private value.
4. Close and reopen PowerShell, IntelliJ IDEA and any other program that starts the backend.

Alternatively, values can be set temporarily in the current PowerShell window. The following values are placeholders:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/investment"
$env:DB_USERNAME="investment_app"
$env:DB_PASSWORD="REPLACE_LOCALLY"
$env:MAIL_USERNAME="REPLACE_LOCALLY"
$env:MAIL_PASSWORD="REPLACE_LOCALLY"
$env:JWT_SECRET="REPLACE_LOCALLY"
$env:ADMIN_EMAIL="REPLACE_LOCALLY"
```

Temporary values disappear when that PowerShell window is closed.

## 6. Configure the React frontend

The frontend contains no private credential. Create its ignored local configuration from the safe example:

```powershell
cd react\investment-tracker
Copy-Item .env.example .env
```

The default local value is:

```text
VITE_API_URL=http://localhost:8080
```

`VITE_ITEM_SEARCH_DEBOUNCE_MS=1000` configures the existing one-second search
delay. The previous `REACT_APP_API_URL` and `REACT_APP_ITEM_SEARCH_DEBOUNCE_MS`
names remain supported as fallbacks, so an existing ignored `.env` does not
need to be edited just to migrate. Explicit `VITE_` settings take precedence.

Do not place database, Gmail or JWT secrets in any frontend variable. `VITE_`
variables are public and included in the browser build. Restart the frontend
after changing its `.env`; backend secret configuration is separate and unchanged.

Return to the repository root before following the next sections:

```powershell
cd ..\..
```

## 7. Start the backend

Open the first PowerShell window:

```powershell
cd investment-tracker
.\gradlew.bat bootRun
```

On the first run, Gradle downloads the required build files. The backend connects
to MySQL, applies pending Flyway migrations, validates the schema and synchronizes
the CS item catalogue. A non-empty database without migration history fails
safely instead of being changed automatically. Internet access is required and
the first catalogue synchronization can take longer than later startups.

The backend is ready when the console reports that Tomcat started on port 8080.

## 8. Start the frontend

Open a second PowerShell window from the repository root:

```powershell
cd react\investment-tracker
npm ci
npm start
```

`npm ci` installs exactly the dependency versions recorded in `package-lock.json`. Run it after a new clone or when the lock file changes.

Open `http://localhost:3000` in your browser. Vite is bound to localhost on port
3000 and will fail clearly if that port is already in use instead of silently
choosing another port. Keep both PowerShell windows open while using the application.

If `npm ci` reports an unsupported Node version, update Node.js first; do not
disable `engine-strict` or use `--force`/`--legacy-peer-deps` to bypass it.

## 9. Create the initial administrator

`ADMIN_EMAIL` does not create an account by itself.

1. Set `ADMIN_EMAIL` to the email address that will be used for the administrator.
2. Start the application and register that account normally.
3. Activate it using the email message.
4. Restart the backend so the startup initializer can find the account and grant the `ADMIN` role.
5. Sign out and sign in again so the newly issued token contains the updated authority.

The Support Panel button should then appear in the profile menu. An administrator can grant or remove the `SUPPORT` role for other accounts.

## 10. Verify the installation

Check the following:

- `http://localhost:3000` opens the React application;
- registration sends an activation email;
- sign-in succeeds after activation;
- portfolio records can be created and displayed;
- PriceTracker can search the synchronized catalogue;
- the Support Panel appears for the configured administrator;
- local API documentation opens at `http://localhost:8080/swagger-ui.html`.

Activation messages can initially appear in the Gmail Spam folder.

## 11. Run verification commands

Backend tests:

```powershell
cd investment-tracker
.\gradlew.bat test
```

Frontend verification:

```powershell
cd react\investment-tracker
npm ci
npm run lint
npm test
npm run build
npm run smoke:build
```

`npm test` runs Vitest once and exits; `npm run test:watch` enables watch mode.
`npm run verify` runs lint, tests, build and production HTTP smoke together.
The smoke check opens a temporary loopback port and closes it after checking
the built files; it does not start the backend or access your database.
Production output remains
in `react/investment-tracker/build/`. `npm run preview` serves that output locally
on port 3000 for checking; it is not a production hosting server.

Create React App and its maintenance warnings are no longer part of the setup.
See [the tooling migration notes](docs/FRONTEND_TOOLING.md) for verification and
remaining limitations.
The final browser checklist is in [MANUAL_VERIFICATION.md](docs/MANUAL_VERIFICATION.md).
GitLab verification requires no personal application secrets; it does not
deploy the website. See [FINAL_VERIFICATION.md](docs/FINAL_VERIFICATION.md).

## 12. Back up the local database

Git stores the source code, not the contents of MySQL. Without a database backup, users, portfolio records, tracked items, price history, contact requests and audit entries are lost when the local database is removed.

### MySQL Workbench method

1. Open **Server → Data Export**.
2. Select the `investment` schema.
3. Select **Export to Self-Contained File**.
4. Choose a location outside the Git repository.
5. Select both structure and data, then start the export.

Store the export in an encrypted backup location. A database export can contain email addresses, password hashes, tokens and user activity; it must not be committed or shared publicly.

### Command-line method

For Windows PowerShell, the repository also provides a helper that prompts for
the password and creates a uniquely named file in the ignored `backups/database`
folder. Run it from the repository root with your actual database username:

```powershell
.\scripts\Backup-LocalDatabase.ps1 -Username investment_app
```

Add `-WhatIf` to preview the destination without connecting to MySQL. See
[DATABASE_MIGRATIONS.md](docs/DATABASE_MIGRATIONS.md) for restore verification and
the verified-backup and explicit-adoption procedure. Every application profile
now uses Flyway migrations and Hibernate `validate`. Do not start against an
unadopted non-empty database until those checks have succeeded.

Run this in **Command Prompt**, not PowerShell, and enter the database password when requested:

```bat
mysqldump -u investment_app -p --single-transaction --routines --triggers investment > investment_backup.sql
```

Keep `investment_backup.sql` outside the repository or inside the ignored root `backups` folder.

## 13. Restore a database backup

Install MySQL, recreate the database and application user as described above, then import the saved file.

With MySQL Workbench, use **Server → Data Import**, choose **Import from Self-Contained File**, select `investment`, and start the import.

From **Command Prompt**:

```bat
mysql -u investment_app -p investment < investment_backup.sql
```

Restore the database before starting the backend. For a Flyway-managed database,
validate its migration history; never switch back to Hibernate `update`.
Follow the adoption/recovery procedure in `docs/DATABASE_MIGRATIONS.md`.

## 14. What must be saved before reinstalling Windows

Keep these items in a password manager or other encrypted storage, not in Git:

- access to the application Gmail account and its recovery method;
- the current Gmail App Password, or permission to generate a new one;
- the MySQL username and password, if the same installation will be retained;
- the JWT secret, if existing sessions must remain valid;
- a recent MySQL database backup, if existing application data is needed;
- GitLab account access and recovery information.

It is safe to create a new MySQL password, Gmail App Password and JWT secret on a completely new installation. They do not have to match the old values when no old sessions or database installation must be preserved.

## 15. Common problems

### The backend reports a missing placeholder

One or more required environment variables are absent. Recheck `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD` and `JWT_SECRET`, then restart the terminal or IDE.

### MySQL reports `Access denied`

Confirm `DB_USERNAME` and `DB_PASSWORD`, verify that MySQL Server is running, and repeat the `GRANT ALL PRIVILEGES ON investment.*` statement with an administrator account.

### The database is unknown

Create the empty `investment` database. Flyway creates tables but not the MySQL
database itself. If an existing database has no migration history, follow the
adoption procedure rather than enabling automatic baseline or Hibernate update.

### Port 8080 or 3000 is already in use

Stop the older backend/frontend process before starting another one. The default frontend configuration expects the backend on port 8080.

### Activation email is not received

Check the Spam folder, verify the Gmail App Password and confirm that 2-Step Verification is still enabled. Do not use the normal Gmail password as `MAIL_PASSWORD`.

### Steam inventory cannot be loaded

The Steam inventory must be public. Steam can also temporarily rate-limit requests. The application does not require a Steam account password or Steam session cookie.

### Price or catalogue information is unavailable

Confirm internet access. The application depends on external catalogue, CSGOTrader price, exchange-rate and Steam public endpoints. External data may be delayed or temporarily unavailable.

## 16. Production warning

The default instructions describe local development and demonstration. Before a public deployment:

- use HTTPS and set `AUTH_COOKIE_SECURE=true`;
- set exact trusted values in `CORS_ALLOWED_ORIGINS`;
- use a dedicated least-privilege database account;
- store secrets in the hosting platform's secret manager;
- keep Flyway migrations and Hibernate `validate`; use separate migration credentials;
- disable or protect development-only endpoints;
- arrange encrypted database backups and log retention;
- never use example values as real production secrets.
