# Authentication security

## Production-readiness follow-up: P0-1 and P0-2

- Sign out still revokes **all** of the account's active sessions. It now works
  with an expired/missing access token by identifying the account through an
  active refresh cookie. An unknown, expired or already-revoked cookie is an
  idempotent no-op; it cannot revoke a subsequent independent login.
- Cookie issuance and deletion retain matching path/name/HttpOnly/Secure/SameSite
  attributes. Access tokens remain 30 minutes; remembered sessions remain 60 days.
- `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout` require
  `X-Requested-With: XMLHttpRequest`. The frontend sends it; CORS permits it only
  for explicitly configured origins. Browser forms cannot add this header.
  Untrusted origins, including `Origin: null`, remain rejected by CORS.
- Only POST logout allows an invalid/expired Bearer token to fall back to its
  cookie. Other protected endpoints still reject that token. A successful logout
  clears the refresh cookie even when no usable credentials remain.
- Login locks the account before password verification and holds that lock
  through session creation. Refresh first resolves an immutable owner ID, then
  locks the account before the token row. Password changes/reset, verification
  codes, revocation, administrative role/account changes and bootstrap admin
  assignment use the same account serialization point.
- Locking reloads current security state, including already-managed entities.
  Refresh updates only `last_used_at`, guarded by active status in the database;
  it never saves a stale full session over a revocation. Account refresh does not
  cascade into loading/locking portfolio items; normal item persistence/removal
  and orphan-removal behavior are retained.
- In-flight requests already authorized before revocation may finish. Network
  failure during logout cannot prove server-side revocation; do not describe a
  failed HTTP request as a completed server logout.

Real-filter H2 workflow tests cover expired-token/missing-token sign-out across
devices, repeat sign-out, stale cookies after a fresh login, refresh replay,
password changes, roles, account locks and cookie-auth CSRF requests. The separate
MySQL suite exercises InnoDB lock ordering, both refresh/revoke orderings,
login/reset races and reload of already-managed state. See
[DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md) for running `mysqlTest` safely.

This is not a declaration that all production requirements are complete.

Reference: [OWASP custom-header CSRF protection](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#employing-custom-request-headers-for-ajaxapi).

## Earlier verification-code hardening (Stage 2)

Implemented on `security-reliability-refactor`. This stage hardens verification
codes and account operations; it does not change portfolio, inventory or price
tracking functionality. Access tokens remain valid for 30 minutes and remembered
refresh sessions for 60 days.

## User-visible changes

- Account activation now submits **email plus six-digit code** in a POST body.
  Codes are no longer put in the activation API URL or resolved across all users.
- Registration supplies the email to the activation form. Only that email is
  kept temporarily in browser session storage, never the code or password.
- The activation form and sign-in page provide access to requesting another
  activation code. A resend does not require a signed-in account.
- Expired codes are rejected; entering an expired code no longer automatically
  sends another email. Resending is an explicit, rate-limited operation.
- Too many requests return HTTP 429 with `Retry-After`. Forms display the
  server's message and prevent duplicate pending submissions.
- Password recovery and activation resends give generic acknowledgements,
  regardless of whether the email exists or needs activation. They acknowledge
  a request, not guaranteed email delivery. Database-dependent processing times
  are not claimed to be perfectly identical.

## Default budgets

| Action | Default |
|---|---|
| Code lifetime | 15 minutes |
| Code requests for an existing account | 3 per hour, at least 60 seconds apart |
| Public code requests from one IP | 20 per hour |
| Registration requests from one IP | 5 per hour |
| Code verification for an account and purpose | 5 attempts per 15 minutes |
| Code verification from one IP | 30 attempts per 15 minutes |
| Sign-in attempts for one account | 10 per 15 minutes |
| Sign-in attempts from one IP | 30 per 15 minutes |
| Security budget entries in memory | At most 10,000 |
| Expired code retention | 7 days after expiration |
| Code cleanup | Daily at 04:00, server timezone |

Attempts include successful submissions, not only incorrect ones. Activation
and password-reset email requests share an account's sending budget, including
Support Panel resends. Sending a new code does not reset the attempt window.
Changing email letter case or using an equivalent spelling accepted by database
collation does not create another budget: existing accounts are keyed by their
resolved user ID. Unknown accounts are keyed by a hash of the normalized input.

Settings are centralized under `security.verification.*` in
`investment-tracker/src/main/resources/application.properties`. Invalid duration
or count settings fail configuration validation. Adjust policy deliberately;
users behind a shared IP/NAT share the IP-level budget.

## Code storage and concurrency

- New codes are generated with `SecureRandom`, remain six digits and are stored
  as HMAC-SHA-256 values bound to the user ID and code purpose.
- The encoder uses `SECURITY_VERIFICATION_SECRET` when supplied; otherwise it
  uses the existing JWT secret with a separate purpose prefix. A secret must
  contain at least 32 Base64-encoded bytes. No new secret is required locally.
- Rotating the effective verification secret invalidates pending hashed codes;
  users then request new ones. No secret is written into this repository.
- Already-issued, unexpired legacy codes remain usable with email plus code.
  Only newly issued codes use the protected format. No bulk conversion is needed.
- Issuance avoids reusing values found in that user's retained code history.
  The six-digit value is not a globally unique or permanent identifier.
- A pessimistic user-row lock serializes issuance, code consumption and password
  changes. A successful use invalidates all outstanding codes for that purpose.
- A fifth incorrect verification invalidates the current code in the database.
  That invalidation survives a restart. It does not lock the user's account.
- Password changes/resets, code invalidation, session revocation and their audit
  record share a transaction. Unexpected failure rolls all of them back.
- Old expired rows are deleted only from the verification-code `token` table;
  active codes, users, portfolio data and refresh-session history are not targets.

## Email delivery

Verification emails are published as transaction-bound events and queued only
**after commit**. A rollback does not enqueue an email. SMTP work uses a separate
executor (1 core worker, at most 2 workers, 100 queued jobs), without replacing
the normal application executor used by maintenance operations.

SMTP connection timeout is 5 seconds; read/write timeouts are 10 seconds. Before
sending, the worker skips codes already consumed, superseded, removed or expired.
Queue rejection and delivery errors are handled, logged without SMTP contents,
addresses, passwords or raw codes, and recorded as `VERIFICATION_EMAIL_FAILED`
in the existing audit log using a separate transaction. If audit storage itself
fails, a redacted error is logged. Neither failure undoes the committed account.
The user can request another code, subject to the same budgets.

### Deployment boundaries that still matter

- Rate budgets are bounded **per running backend process**, not distributed or
  durable. Restarting clears their counters; it does not revive a code already
  invalidated in MySQL. Before multi-instance deployment, replace the in-memory
  budget store with shared atomic limits (for example Redis) and add edge limits.
- The mail queue is **in memory**, not a transactional outbox. A crash can lose
  a pending email even though its code/account was committed. Before requiring
  guaranteed retry/recovery, add a durable outbox with delivery state and bounded
  retry, using the database migration and verified-backup procedure. No delivery
  guarantee or production readiness is claimed by this stage.
- A trusted reverse proxy must strip forged forwarding headers and supply the
  real client address. Public direct access with trusted arbitrary forwarding
  headers would weaken IP-level limits. Use HTTPS and restrict direct backend
  access in production.
- Gmail delivery/spam placement and provider quotas are not application guarantees.
  Use an appropriate transactional mail service and monitoring for public deployment.

## Checks and rollout

Verified on 2026-10-09:

- Backend: 89 tests in 19 suites; 88 passed, 1 existing manual measurement test
  skipped, no failures/errors. Backend application archive built successfully.
- Frontend: 15 tests passed in 5 suites; production build completed successfully.
- The existing React testing-library deprecation and CRA/Babel maintenance
  warning remain for the dedicated dependency/tooling stage.
- At this verification checkpoint, no commit, push or merge had been performed;
  both stages were awaiting publication approval on the same work branch.

Automated checks cover legacy compatibility, duplicate historical code values,
wrong-account use, one-time use, expiration, fifth-attempt invalidation,
simultaneous consumption, rollback, session revocation, protected storage,
after-commit mail dispatch, SMTP failure, queue rejection, retention, HTTP
validation/limits and frontend activation behavior. Integration checks run on
isolated H2 databases with fake SMTP, not on the real MySQL database or Gmail.
They are not a MySQL-specific concurrency/load test or a real delivery test.

At the historical 2026-10-09 checkpoint, no JPA entity/table/column change was
required and Flyway remained disabled. That refactoring Stage 2 did not start the real application, access the
real MySQL database, send real mail, or perform a backup. Before applying future
schema changes, follow [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md).

The later production-readiness Stages 1–2 are different: on 2026-10-21 the
developer database completed verified backup/restore and explicit V1 adoption,
and Flyway became the default schema owner. See `PRODUCTION_PLAN.md` and
`RUNTIME_UPGRADE.md` for the current checkpoint.

Restart both frontend and backend together before manual validation: the old
activation API using GET with only `token` is intentionally unsupported. Manually
check registration/activation, requesting another code after the cooldown,
password recovery, rejection of reused codes, and forced sign-in after changing
the password. Use test accounts, check the spam folder, and do not log real codes.

## References

- [OWASP password-recovery guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)
- [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
