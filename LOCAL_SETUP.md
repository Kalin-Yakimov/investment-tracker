# Investment Tracker - local and production configuration

The project uses a Spring Boot backend, a React frontend and MySQL 8.

## Required local environment variables

Secrets are not stored in the repository. Define these variables in the terminal or in the IDE run configuration before starting the backend:

```text
DB_PASSWORD=your_mysql_password
MAIL_USERNAME=your_gmail_address
MAIL_PASSWORD=your_gmail_app_password
JWT_SECRET=a_base64_encoded_secret_of_at_least_32_random_bytes
```

Optional variables and examples are listed in `investment-tracker/.env.example`.

Generate a suitable JWT secret in PowerShell with:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Copy the printed value into `JWT_SECRET`. Never commit it.

## Starting locally

1. Start MySQL and make sure the `investment` database exists.
2. In `investment-tracker`, run `.\gradlew.bat bootRun`.
3. In `react/investment-tracker`, run `npm start`.

The default addresses are `http://localhost:8080` and `http://localhost:3000`.

## Authentication behavior

- Access tokens are valid for 30 minutes and remain only in browser memory.
- Refresh tokens are stored in an `HttpOnly` cookie and are rotated after every use.
- “Remember me” sessions are valid for 60 days.
- Sessions without “Remember me” use a session cookie and expire on the server after 24 hours.
- Logout revokes the current server-side session.
- Password changes and password resets revoke all existing sessions.
- Reuse of an already rotated refresh token revokes the associated session.

## Production

Use the `prod` Spring profile and provide secrets through the hosting environment. Production requires HTTPS, `AUTH_COOKIE_SECURE=true`, the exact frontend address in `CORS_ALLOWED_ORIGINS`, and a database migration before `JPA_DDL_AUTO=validate` is enabled.

Do not publish the example values from the `.env.example` files.
