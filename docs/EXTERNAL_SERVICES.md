# External services and price refresh reliability

Stage 4, 2026-10-10. Work branch: `security-reliability-refactor`.

This is the historical refactoring checkpoint. Later production stages added
versioned migrations, stricter transaction boundaries and nullable source/FX
timestamps. Current freshness behavior and verification are documented in
[FRONTEND_RELIABILITY_STAGE5.md](FRONTEND_RELIABILITY_STAGE5.md); the old
no-schema-change statement below describes the 2026-10-10 work only.
Production Stage 6 subsequently added shared Steam concurrency/start-spacing
budgets and weighted cache capacity; see [RESOURCE_LIMITS_STAGE6.md](RESOURCE_LIMITS_STAGE6.md).

## Scope and unchanged behavior

The application still reads CSGOTrader `last_24h` USD averages and displays EUR
values. It does not obtain live Steam listing prices. The 7-day, 1-month and
3-month charts still use locally recorded observations; missing offline dates
are not reconstructed or invented. Existing URLs, DTOs, catalogue category
exclusions, interface and authentication settings are preserved.

No schema/model changes or migration activation are included. Stage 4 checks
use mocked provider replies and H2, not the real MySQL database or external
providers. No actual price refresh, catalogue synchronization, inventory lookup
or email delivery was performed during these checks.

## HTTP limits

The shared `RestTemplate` now applies these configurable defaults to catalogue,
price, currency and inventory requests:

| Property | Default | Purpose |
|---|---:|---|
| `external.http.connect-timeout` | `5s` | Connection timeout |
| `external.http.read-timeout` | `20s` | Socket read timeout |
| `external.http.maximum-response-bytes` | `33554432` | 32 MiB response body limit, including bodies without Content-Length |
| `price.feed.maximum-decoded-bytes` | `67108864` | 64 MiB price-feed limit after gzip decoding |

These are byte limits and connection/read timeouts, **not a guaranteed total
wall-clock deadline**. A response that keeps delivering data within each socket
read timeout can take longer. An upstream gateway/whole-request deadline remains
a deployment consideration. Timeouts cannot be set to zero (infinite) through
the validated HTTP properties. Limits are adjustable in `application.properties`
or their documented environment variables; increase them deliberately if a
legitimate provider file outgrows a limit.

The timeout behavior follows the [Spring HTTP request factory documentation](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/client/SimpleClientHttpRequestFactory.html).

## Price publication lifecycle

1. One refresh lock protects downloads and price/history writers in this backend
   instance. Concurrent cold lookups share the first download; their maximum
   lock wait defaults to 25 seconds. Readers of an already loaded cache keep
   using the previous complete publication while another thread refreshes it.
2. Parse and validate the downloaded prices before writing anything. The USD
   values, EUR conversion rate, source version, source date and HTTP validators
   form one immutable cache publication. There is no `clear()`/`putAll()` gap.
3. A separate transactional service updates current prices and stores shared
   history observations together. A failed history insert rolls back the price
   update. Only price columns of still-tracked records are updated: concurrent
   user settings, quantities, notifications and pinning are not overwritten by
   saving stale whole entities. Selected IDs are handled in batches of 500.
4. Publish the new cache only after that transaction commits. On failure the
   old cache and database values remain available; no fabricated zero price or
   empty publication replaces them.

Snapshots remain shared by market hash name and source, not duplicated per account.
There is one observation per UTC publication day; a later same-day publication
replaces that daily value. Unique day/version constraints and a source-specific
database write guard protect concurrent persistence transactions. See
[DAILY_PRICE_HISTORY.md](DAILY_PRICE_HISTORY.md) for the daily policy and the
required adoption procedure for existing populated MySQL installations.
Portfolio purchase/sale records are not
price-update targets. Price refreshes do not create legacy transaction-history
rows.

### Source identity and dates

When present, `Last-Modified` is included in publication identity and used as
the observation date. Normal HTTP checks prefer `If-Modified-Since` over ETag:
the provider can republish unchanged values under the same ETag with a new date.
That newer publication creates a daily observation on another UTC day even if
the numeric price is unchanged; on the same day it replaces the existing row.
A 304 reply produces no extra observation.

When no publication date is provided, an ETag or decoded-content hash identifies
the data, and the first local receipt time is used. Repeated receipt of an
already recorded version reuses its stored time/rate, including after restart.
Long ETags are hashed to fit the existing version column. Older dated responses
cannot overwrite a newer in-memory or stored publication, including after restart.
A cold start receiving a dated response older than the latest stored publication
performs one conditional revalidation of that stale CDN copy, then fails safely
if the provider still cannot return usable current data. Dates over five minutes
into the future are rejected. Changed values under an unchanged version are rejected
for an already loaded publication or an already stored tracked observation.

This is a reliability safeguard, not independent verification of the provider's
market estimates. A date without provider metadata is a local receipt timestamp,
not proof that a Steam transaction occurred then.

### EUR conversion

The hardcoded emergency `0.92` exchange rate has been removed. A new publication
normally uses the received positive USD/EUR rate. If currency lookup fails, the
last received in-memory rate or last stored snapshot rate can be reused. If
neither is available, the publication is not applied and an explicit refresh
reports failure. Thus an outage may leave stale data rather than invent a rate.

The same publication reuses its conversion rate on subsequent refreshes and
after restart, keeping the displayed value consistent with recorded history.
USD is normalized to 4 decimal places and the rate to 8, matching existing
snapshot columns; EUR is rounded to 2 places.

Pre-existing database rates have no provenance marker. They are reused for
compatibility, **not certified as previously fetched or recent**. Retained historical
conversion values are not recalculated; superseded same-day rows are consolidated
only by the documented adoption procedure. The fallback has no freshness guarantee; source availability,
exchange-rate provenance and monitoring remain relevant for public deployment.

## Intervals, failures and manual refreshes

| Property | Default |
|---|---:|
| `price.feed.check-interval-ms` | `7200000` (2 hours) |
| `price.feed.failure-retry-interval-ms` | `60000` (1 minute) |
| `price.feed.refresh-wait-ms` | `25000` |
| `price.update.rate-ms` | `7200000` |
| `price.update.initial-delay-ms` | `5000` |

The two-hour successful-check interval and five-second startup check are
unchanged. Manual Support Panel refresh bypasses that interval and conditional
HTTP validators, but **does not bypass a failure cooldown**. Price-feed HTTP
429/503 `Retry-After` headers (seconds or HTTP date) extend the cooldown when
longer than the configured minimum. Invalid headers use the minimum. There is
no rapid automatic retry loop; the next eligible request or scheduled job can
try again. Cache reads during a cooldown may continue using previous data.

An explicit job fails on provider/persistence errors; it does not report that
an old cached publication was freshly loaded. Public job errors do not include
raw provider responses or database exception details.

On restart, the memory cache, job status and retry cooldown reset. Stored prices
and observations remain. The next check captures only the latest available
publication; it cannot recover prices from days when the application was off.

## Background jobs and catalogue synchronization

- Manual and scheduled system jobs atomically claim the same running-job guard.
  They cannot run the same maintenance operation in parallel in one instance.
- Boot's application executor has 2 core workers, maximum 4 workers and queue
  capacity 50. The verification-email executor is separate and unchanged.
  Queue rejection is reported as HTTP 503 and releases the job/account guard.
- An account can have only one selected-item price update queued/running at a
  time; success, failure and queue rejection all release that guard.
- Failed jobs do not stay RUNNING. `Last completed` retains the last successful
  completion; a first failed attempt does not invent a completion time. These
  states are in memory and reset at startup.
- Catalogue full/incremental parsing is shared, accepts object/array feeds and
  rejects empty/unusable responses. Invalid/excluded entries are skipped; a
  wholly unusable feed is a failure. Synchronization upserts accepted entries;
  it does not delete user records. The existing startup excluded-category
  cleanup and missing-name repair are preserved.
- Catalogue writes are transactional. Search-cache eviction waits for commit
  and is cancelled on rollback, including synchronization called at startup.
- A catalogue-provider failure at startup does not prevent using an existing
  stored catalogue. With an empty database, catalogue-dependent features remain
  unavailable until a later successful sync; this is not reported as success.
- Daily legacy-history and snapshot pruning now allows errors to escape its
  transaction so both deletions roll back together. Existing retention periods
  and schedule are unchanged.

## Deployment boundaries

Refresh/job/account guards and cooldowns are local to one process, not distributed
locks. Multiple backend instances need coordinated scheduling, refresh ownership
and provider budgets. A uniqueness race can cause a transaction to roll back;
the old cache is retained, but that is not distributed job coordination.

This stage does not certify production readiness, load capacity, MySQL-specific
locking behavior, provider latency or live API availability. Inventory pagination
and inventory-data correctness are reserved for Stage 5; only its shared HTTP
timeouts/body cap change here. No frontend behavior or dependency upgrade is
included.

Before operating with a large history table, examine MySQL execution plans for
publication/rate metadata lookups; additional indexes may be needed. No such
schema change is silently introduced here. It requires the backup/migration
procedure and an explicit deployment check.

## Verification

Results are recorded after the complete deterministic suite and application
archive build, rather than inferred from individual tests. The manual network
measurement remains disabled and is not counted as a passed test or benchmark.

- Backend: **190 tests in 27 suites; 189 passed, 1 skipped, 0 failures/errors**.
  This is 42 more tests than the Stage 3 checkpoint; previous coverage was
  retained and the price-service tests were adapted to the separated writer.
- Backend application archive (`bootJar`): successfully built.
- Frontend: **15 tests passed in 5 suites**; frontend source/dependencies are
  unchanged. The existing testing-library `ReactDOMTestUtils.act` deprecation
  warning remains for the later tooling stage.
- Checked cases include unchanged/new/restarted publications; missing currency
  with/without a stored fallback; failure cooldown and Retry-After; malformed,
  gzip and oversized responses; warm/cold concurrent readers; H2 rollback of
  price/history changes; preservation of user settings and record types;
  catalogue commit/rollback cache behavior; atomic job claims; queue rejection
  and per-account guard release; actual HTTP timeout/executor configuration.
- No live-provider, load, real-MySQL or manual-browser result is claimed.
- At this checkpoint changes remain local and uncommitted; commit, push and
  merge await user approval.
