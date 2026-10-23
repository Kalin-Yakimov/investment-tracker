# Authentication lifecycle — production roadmap Stage 3

Implemented and verified locally on 2026-10-21, on `production-auth-migrations`.
The owner approved commit and publication to GitLab/GitHub on 2026-10-21.
No merge or deployment is included in this checkpoint. The real
developer database, stored password hashes, mail settings and secrets were not
changed. No schema migration is needed and the adopted V1 SQL is unchanged.

## Scope and preserved behavior

This completes audit P1-5 (client JWT decoding), P1-8 (consistent new-password
rules) and P1-9 (expected account-operation failures), while preserving and
regression-testing the earlier P0-1/P0-2 logout/concurrency groundwork.

- Access tokens remain **30 minutes**.
- Remembered refresh sessions remain **60 days**; non-remembered sessions retain
  the existing 24-hour/session-cookie behavior.
- Sign out still revokes all active sessions, including expired-access-token
  fallback through an active protected refresh cookie. It is not changed to
  current-device-only sign-out.
- Password change/reset still revokes existing sessions atomically. Invalid
  input or a wrong current password must not revoke them. Invalid new reset
  credentials are rejected before a valid one-time code can be consumed.
- Refresh rotation/replay detection, account-first serialization, conditional
  last-used updates and current role reloads remain in place.

## One policy for all new-password paths

`auth/PasswordPolicy.java` is used by both the `@ValidPassword` HTTP constraint
and service entry points. Direct service calls cannot bypass it. Registration,
change and reset require **at least 10 Unicode code points**, explicitly selected
by the owner. Login/current-password validation deliberately does not apply the
new minimum to existing shorter passwords. No forced password reset or mass
password rehash is performed.

BCrypt's limit is **72 UTF-8 bytes**, not 72 visible characters. The server and
`features/auth/passwordPolicy.js` enforce that boundary and reject invalid
Unicode text. Examples: 72 ASCII characters, 36 Cyrillic `я` characters or 18
four-byte emoji fit the limit. A 37-character Cyrillic password does not.
Passwords are never trimmed, normalized or silently truncated. Leading/trailing
spaces in a nonblank password are significant. Existing inputs beyond the
encoder's supported byte limit are rejected; such accounts need the reset flow,
not a silent compatibility truncation.

The UI shares its validation/helper text across registration, reset and change.
It accepts spaces, Unicode and passphrases without mandatory symbol/case rules.
The backend remains authoritative; browser validation is convenience only.

**Security-policy decision:** 10 is the owner's chosen minimum, not a claim of
full OWASP/NIST password-policy compliance. Current OWASP guidance recommends
15 when MFA is absent. No MFA, breached-password service or password blocklist
was added in this stage; passwords are not sent to any new external service.
Those product-security decisions remain relevant at final production review.
BCrypt hashes and cost are retained, not replaced with a new hashing scheme.

## Expected account errors are not server failures

| Operation failure | HTTP | Existing/new business code |
| --- | --- | --- |
| Current password is incorrect | 400 | 300 |
| New password confirmation differs | 400 | 301 |
| Display username belongs to another account | 409 | 305 |
| Invalid password length/encoding or request field | 400 | Validation errors / safe error text |

`AccountActionException` carries fixed public business messages. The handler
does not expose passwords, submitted codes, SQL or account entities. Unexpected
internal failures still return a generic 500; they are not all recategorized as
client mistakes. Sign-in credential failures retain the existing 401 contract.
The enum spelling for code 301 was corrected without changing its numeric code.

`User.getUsername()` remains the email identity required by Spring Security.
Display-name collision checks compare the existing owner's immutable account ID,
not that email. This permits one's own unchanged/case-adjusted display name and
rejects another owner's name according to real MySQL collation, including accent
and case-insensitive matches. Database uniqueness still handles concurrent
collisions. Username changes do not change email or revoke valid sessions.

Profile forms now display the API's actual public `error`/`validationErrors`,
rather than hiding them behind a generic fallback. Expected 400/409 responses
do not trigger access-token refresh or client sign-out. Password/username forms
and logout do not print credential-bearing error objects to the browser console.

## Correct client JWT metadata

`features/auth/jwtPayload.js` handles Base64URL alphabet/padding, bytes and strict
UTF-8 decoding before JSON parsing. Invalid format, invalid UTF-8, truncated JSON
or a non-object payload returns null without throwing or logging the token.
`AuthService` accepts a positive safe numeric account identifier and defensively
handles display-name/email/authority types. Malformed metadata does not mark the
UI authenticated. Cyrillic/emoji names remain readable; renewal for the same
account does not accidentally change its frontend session identity.

This function **does not verify signatures**. Displayed roles and ProtectedRoute
are not authorization controls. Signature/issuer/audience/expiry/session checks
and resource authorization remain on the server. In-memory access tokens and
HttpOnly refresh cookies are retained; no token is moved to localStorage.

## Verification

Backend: `./gradlew test bootJar runtimeDependencyAudit` with Java 21:
430 tests, **429 passed**, one existing manual measurement disabled, zero
failures/errors; archive build succeeds. OSV checked 119 resolved runtime Maven
components and returned zero advisory matches at the recorded timestamp.

MySQL: `scripts/Test-MySql.ps1` with an isolated official MySQL 8.4.11 server:
**14/14 passed**. This includes migration/adoption/full startup, both refresh vs
revocation orderings, login vs reset, current account/role reloads after old
repeatable-read snapshots, pending writes, unrelated-account login independence
and actual case/accent-insensitive display-name ownership. Only disposable test
databases were used; the server was stopped afterward.

Frontend: `npm.cmd run verify` (ESLint, Vitest, build, production HTTP smoke).
All **169 tests** and **17 production HTTP smoke checks** passed; lint and build
succeeded. Tests cover password character/byte boundaries, legacy credentials, malformed
tokens, Unicode names, real profile rendering, form submission/error behavior,
unchanged sessions after expected errors and existing request-lifecycle races.
Provider HTTP/mail are mocked. No live Gmail/Steam/provider requests, real
account password changes or public deployment were performed.

Existing frontend upstream sourcemap and large-chunk warnings remain. Remote
Remote GitHub/GitLab pipeline results must be checked separately after publication.
Real browser acceptance and the complete production checklist remain separate:
see [MANUAL_VERIFICATION.md](MANUAL_VERIFICATION.md) and
[PRODUCTION_PLAN.md](PRODUCTION_PLAN.md). Stages 4–8 are not implemented here.

Network failure cannot prove a server logout completed. In-flight operations
authorized before revocation may finish, and another open device notices
revocation on its next protected request, not through a new live notification.

## References

- [OWASP authentication/password controls](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP BCrypt input limits](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#input-limits-of-bcrypt)
- [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
