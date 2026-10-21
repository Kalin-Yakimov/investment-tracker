# Resource limits and performance — production Stage 6

Implemented and verified on 2026-10-21 on `production-auth-migrations`, after
Stage 5 commit `c516f6f` was pushed to both GitLab and GitHub. The owner approved
committing and pushing Stage 6 to both remotes. Merging and deployment remain
separate, unauthorized actions. This is not the older refactoring Stage 6.
No real developer database, account, credentials or external service was used
by the verification runs. No new migration is required by this stage.

## What remains unchanged

The user-approved chart bounds, line style, above-point tooltip, date semantics
and price calculations are unchanged. Search debounce remains 1000 ms; catalogue
search TTL remains 12 hours. Inventory caching remains 15 minutes, with the same
quantity, privacy, partial-price and result-retention behavior. The shared price
feed still checks on its existing schedule. Portfolio history is not page-cached;
valid repeated purchase/sale records remain allowed. Authentication lifetimes,
global logout and refresh rotation are unchanged.

## P0-7: admission before expensive work

`RequestProtectionFilter` runs inside the security chain before cookie/JWT work.
It consumes a direct-peer IP budget, then reads at most the configured body limit
plus one byte. An oversized declared length fails immediately; missing length
and chunked bodies cannot bypass the actual byte check. Accepted bytes are
preserved for normal JSON parsing. Oversized bodies receive JSON HTTP 413.
CORS handling remains earlier in the chain; OPTIONS preflight is not counted.
The new filters are explicitly constructed in the security configuration, not
auto-registered a second time as servlet filter beans.

After JWT validation, `AuthenticatedRequestBudgetFilter` consumes an account
budget and, where applicable, a write/expensive-operation budget. A different
access token or IP cannot reset the authenticated account budget. Logout is
exempt from account budgets so that a used-up inventory/write allowance does
not prevent revocation; the peer/network perimeter still applies.

`ApiRequestBudget` uses monotonic one-minute windows and hashed identities in a
separate bounded Caffeine registry. Per-key spending is serialized, including
concurrent calls. Live budgets are not discarded to admit a new identity when
the registry fills: new keys receive temporary HTTP 503 instead. Exhausted
budgets receive HTTP 429. Both provide `Retry-After`; messages do not contain
request bodies, tokens or credentials. Auth-specific login/code protections
remain separate and unchanged. Nothing automatically retries a write.

### Configurable defaults

| Environment variable | Default | Rationale |
| --- | ---: | --- |
| `API_MAXIMUM_REQUEST_BODY_BYTES` | 65536 | Fits a 1000-ID batch and a 2000-character contact message; rejects bulk JSON abuse |
| `API_REQUESTS_PER_IP_MINUTE` | 3000 | Generous shared-peer allowance; not a DDoS boundary |
| `API_REQUESTS_PER_USER_MINUTE` | 600 | Allows page/history browsing and ordinary searching |
| `API_WRITES_PER_USER_MINUTE` | 60 | Allows ordinary editing without unbounded write bursts |
| `API_INVENTORIES_PER_USER_MINUTE` | 10 | Protects a much more expensive user-triggered operation |
| `API_PRICE_UPDATES_PER_USER_MINUTE` | 2 | Normal prices are already shared/cached; explicit update/validation is limited |
| `API_CONTACT_REQUESTS_PER_USER_MINUTE` | 5 | Discourages ticket spam without blocking an ordinary support request |
| `API_MAINTENANCE_PER_USER_MINUTE` | 4 | Limits repeated administrative feed/catalogue triggers |
| `API_MAXIMUM_RATE_LIMIT_ENTRIES` | 20000 | Bounds admission-registry memory |
| `TRACKED_ITEMS_PER_USER` | 1000 | Bounds account tracking work; comfortably above ordinary manual portfolios |

These are initial, adjustable **single-instance** budgets, not subscription
tiers or experimentally certified production throughput. A fixed window can
allow a burst on either side of a boundary. Registry pressure, shared NAT/proxy
traffic and normal workload must be observed and tuned before release. The
inventory allowance counts user attempts, including cache hits, not Steam calls.
The normal browser retains its calculator result and does not recalculate just
because the user navigates back.

### Proxy and deployment boundary

The limiter never interprets a caller-supplied forwarding header itself. The
production profile now defaults to `server.forward-headers-strategy=none`, rather
than trusting arbitrary forwarded client IPs. A trusted reverse-proxy contract,
blocked direct backend access and stripped/rebuilt forwarding headers must be
configured explicitly in Stage 7 before enabling forwarding. Otherwise a proxy
is treated as the shared direct peer; tune its aggregate allowance accordingly.
Public edge rate/connection/body/deadline protection is still required.
This application filter does not stop a slow client before the servlet reads,
network-level flooding, or distributed attacks.

## Bounded Steam work

`SteamWorkLimiter` is shared by the facade, valuation and HTTP client:

| Environment variable | Default | Work bounded |
| --- | ---: | --- |
| `INVENTORY_STEAM_MAXIMUM_CONCURRENT_LOOKUPS` | 16 | Complete requests, including vanity lookup and same-profile cache waiters |
| `INVENTORY_STEAM_MAXIMUM_CONCURRENT_CALCULATIONS` | 2 | Actual uncached downloads/valuation work |
| `INVENTORY_STEAM_MAXIMUM_CONCURRENT_REQUESTS` | 2 | Simultaneous Steam HTTP calls across profiles |
| `INVENTORY_STEAM_MINIMUM_REQUEST_INTERVAL_MS` | 1000 | Shared spacing of admitted HTTP starts, including XML/profile calls |
| `INVENTORY_STEAM_MAXIMUM_REQUEST_WAIT_MS` | 2000 | Maximum reserved start wait, not an unbounded HTTP queue |

Full admission fails promptly with HTTP 503 and `Retry-After: 2`. Permits are
released in `finally`, including failure and interruption. Cache single-flight
still shares one calculation for the same profile; its waiters are now bounded
as well. Cache hits do not use an uncached calculation slot. Existing page,
asset, quantity, HTTP byte/time and provider `Retry-After` limits remain.
Cooldown is checked again after a paced wait before another HTTP call begins.
No HTTP wait or calculation is placed inside a new database transaction.

These limits reduce traffic; they do **not** guarantee that Steam will accept it
or promise an official anonymous Community API quota. Real 429/503 responses
still establish the existing shared cooldown. No additional Steam login,
credentials, scraper or paid provider was introduced.

## P1-11: bounded data and cache loading

### Tracking, pinned items and support views

`GET /api/items` now returns the existing shared `PageResponse` shape, not an
unbounded array: `content`, `number`, `size`, `totalElements`, `totalPages`,
`first`, `last`. Default size is 20, maximum 100. Filtering by owner, record
type and optional `dashboard=true` happens in SQL **before** pagination.
Ordering is stable newest ID first. The catalogue relation is fetched together
with the page; price metadata is loaded in bounded batches, not per row.

The tracker loads histories only for its current page, with the existing four
concurrent reads. Adding returns to page 1. Removing the last row of a final
page loads the previous valid page, and focus returns after the enabled search
control has been committed to the DOM. Existing failed-read retry behavior is
preserved. Pinned items request only dashboard pages, ten at a time on scroll;
previously loaded rows remain visible during another request. Unpinning reloads
the first page because offset pages shift after removal and must not skip rows.

Support tracked-item and active-session views also return `PageResponse`, with
default size 20 and maximum 50. Reason/role/target checks and IP masking for
non-admin support users remain in place. Session validity and revocation were
not changed. The unbounded repository methods for these views were removed.
Backend and frontend must be updated/restarted together for the new contracts.
Numbered pagination renders at most seven page-number buttons rather than one
button for every database page. The history itself is already bounded by the
120-day window and daily uniqueness; no artificial chart points are introduced.

New tracked insertions check the account quota **inside** the transaction, after
the shared database write guard. Concurrent different-item requests cannot
both pass a final available slot. Duplicate tracking still reports its existing
conflict. Existing over-quota accounts are not truncated or deleted: reads,
updates and removal remain available; only additional tracking is refused with
HTTP 409. The quota is unrelated to portfolio transaction history. No portfolio
records, active sessions, shared prices or user data are automatically removed.

### Separate cache capacities

`CacheProperties` validates positive, bounded policies. Existing TTL property
names remain valid; `CACHE_MAXIMUM_SIZE` remains a fallback for small caches.

| Cache | Capacity control | Default |
| --- | --- | ---: |
| Catalogue search | `CACHE_CS_ITEMS_SEARCH_MAXIMUM_SIZE` | 1000 entries |
| Portfolio totals | `CACHE_PORTFOLIO_SUMMARY_MAXIMUM_SIZE` | 5000 entries |
| Steam vanity resolution | `CACHE_STEAM_PROFILE_RESOLUTION_MAXIMUM_SIZE` | 1000 entries |
| Inventory results | `CACHE_INVENTORY_MAXIMUM_WEIGHT` | 20000 weight units |

An inventory weighs `100 + returned item rows`, so an empty result has a cost
and a 10000-row result costs 10100 units rather than the same unit as a tiny
result. This is a bounded footprint proxy, **not** a measured byte-size or a
guarantee about JVM heap consumption. The completed result lists are immutable;
do not mutate cached DTOs or grow them after insertion. Actual heap/container
limits and profiling remain part of release validation.

Catalogue and summary cache invalidation still waits for successful commit.
Summary loading now uses single-flight as well, so concurrent reads do not
stampede and an eviction cannot be overtaken by an in-flight cache population.
Tests retain commit/rollback checks and add overlapping load/eviction coverage.

### Frontend payload and test-runner capacity

React `lazy`/`Suspense` loads the protected feature routes on demand. Sign-in no
longer eagerly imports charts, inventory results, date pickers and support
tables. Existing error boundaries handle a failed feature import with recovery
instead of a blank page. There is a visible loading state and no new dependency.
The main minified JS entry dropped from approximately 809 KB / 247 KB gzip to
288 KB / 92 KB gzip. Other chunks load when needed: this is not a claim that all
code for a complete visit disappeared. No build chunk exceeds 500 KB in this
checkpoint. Vitest keeps per-file isolation but caps workers at four, avoiding
unbounded concurrent jsdom environments while Java tests may also be running.

## Verification

- Backend: 481 tests, 480 passed and one existing explicitly manual network
  measurement skipped; `bootJar` passed. Tests include the real security chain,
  request bytes/rate races, permission-preserving session pages, cache behavior,
  failed work and interruption, and the existing financial/auth regressions.
- MySQL 8.4.11: 26 isolated migration/startup/concurrency/consistency tests passed.
  A same-account two-item race with one remaining slot accepts exactly one.
- MySQL 8.0.37: the same 26 isolated tests passed; the original system service
  and developer database were not reused or modified.
- Controlled MySQL fixture: 1000 tracked rows return a 20-row page in 5 SQL
  statements, including catalogue data and bulk metadata, with no per-row query.
  Measured locally at 70.30 ms on 8.4.11 and 93.33 ms on 8.0.37. Pinned
  filtering and disjoint next pages are checked against those actual rows.
- Frontend: zero-warning lint, all 216 tests in 37 files, production build and
  17 production HTTP smoke checks passed. Component tests cover current-page history
  requests, retained pinned rows during paging, existing date/form/cache behavior,
  and bounded page navigation.
  The dependency's existing datepicker missing-sourcemap warning remains
  non-blocking; the large-build-chunk warning is no longer present after splitting.
- Actual browser walkthrough used synthetic data on an isolated Vite/API fixture:
  41 items rendered as 20/20/1, deleting the final item returned to page 2 of 2,
  focus was restored after asynchronous reload, pinned items loaded 10 then 20,
  and calculator results survived lazy route changes. No real backend, database,
  provider, email or credentials were used. The temporary tab/servers were closed.

The tests are evidence for these boundaries, not a production P95/SLO, load test
of thousands of real users, live provider-availability test or DDoS certification.
Per-process counters, pacing and caches reset on restart and are not shared
across replicas. Multi-instance deployment needs shared/edge admission and
coordinated cache invalidation/provider budgets, not merely additional replicas.
Stages 7 and 8 remain required and are not authorized by this stage.

## References

- [OWASP API4 resource consumption](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)
- [Spring Security servlet filter architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Caffeine eviction and weighting](https://github.com/ben-manes/caffeine/wiki/Eviction)
