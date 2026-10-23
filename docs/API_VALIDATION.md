# API validation and record boundaries

Stage 3 verification checkpoint: 2026-10-10 (Europe/Sofia).

## Record ownership and type

Portfolio records and tracked market items still share the existing `item` table.
No entity, column, table or data migration is introduced by this stage.

Individual queries now require all three conditions: the requested item ID, the
authenticated user's ID, and the correct record type:

- Portfolio: `steamFoundCheck` is `false` or `null` (legacy records).
- PriceTracker: `steamFoundCheck` is `true`.

Portfolio reads, edits and deletion cannot act on a tracked record, even if it
belongs to the same account. Tracked details, history, settings, pinning and
deletion cannot act on a portfolio record. A missing, foreign or wrong-type item
returns the same generic HTTP 404 without revealing another user's records.
Batch price updates continue to check every distinct ID before queuing work;
a mixed or unauthorized batch is rejected as a whole.

The broad `findByIdAndUserId` query was removed, not left as an alternative path.
The catalogue relation and the permissions for ADMIN, SUPPORT and USER are unchanged.

## Request limits

Shared limits are in `validation/ApiRequestLimits.java`.

| Input | Allowed range |
|---|---|
| Page index | 0 or greater |
| Portfolio/history/audit page size | 1 to 100 |
| Support users/portfolio/activity/tickets page size | 1 to 50 |
| Price-update batch | 1 to 1000 IDs, each non-null and positive |
| Item name, market name, catalogue ID, search query | At most 255 characters |
| Portfolio filter | One of the existing UI filter names; at most 32 characters |
| Support reason header | Non-blank, at most 500 characters; existing service minimum still applies |
| Inventory profile input | Non-blank, at most 500 characters |

Portfolio amounts must be non-negative and fit the existing decimal(38,2)
storage. Optional price notifications must fit decimal(18,2). More than two
decimal places are rejected rather than silently rounded by the database.
JSON fractional quantities and item IDs are rejected instead of truncated.
Integer strings used by the existing forms remain accepted.

Tracked creation requires a non-blank catalogue ID or market name. Its existing
quantity default (zero/omitted becomes one in the service) is preserved.
Administrative lock/role changes require an explicit boolean: missing/null is
invalid, while an explicit `false` remains valid.

Invalid pagination now returns HTTP 400 instead of being silently clamped.
Normal page sizes, endpoint URLs, DTO response shapes and UI behavior remain
unchanged. Future clients sending more than 1000 update IDs must split batches.

## Errors and transaction boundaries

Missing query parameters or headers, malformed IDs/enums, and parameter/body
validation failures return HTTP 400. Unsupported request content types return
415, and missing resources return 404. Rejected raw values are not echoed or
logged by the new parameter error handlers. Unexpected errors still return a
generic 500; return-value validation errors are not mislabeled as client errors.

The implementation uses built-in Spring MVC parameter validation, without
class-level `@Validated` on controllers. Both body and method-parameter
validation exceptions are handled. See [Spring MVC validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html).

Portfolio add, update and delete now include the item change and its activity
entry in one transaction. An activity-write failure rolls back the item change.
Portfolio reads map catalogue data to DTOs inside a read-only transaction;
Open Session in View remains disabled. Existing summary-cache annotations remain;
the summary cache now defers puts/evictions until a successful transaction commit.
Rollback does not evict a valid summary or publish uncommitted summary data. Other
caches and their configured lifetimes are unchanged.

Sorting adds an item-ID tie-breaker after the chosen sort field so equal dates,
quantities or prices do not produce an ambiguous order across pages. Total-price
sorting remains database-side before pagination. Audit pagination uses a long
offset, avoiding integer overflow on a very large page index.

External price-loading transactions, task scheduling and cache/source freshness
are reserved for the next external-services stage; this stage does not redesign them.

## Verified checks and limits

- Backend: 148 tests in 22 suites; 147 passed, 1 existing manual network
  measurement skipped, no failures or errors. `bootJar` built successfully.
- New coverage: 32 HTTP validation cases, 21 item-boundary/transaction cases and
  2 administrative-pagination cases and 4 cache commit/rollback cases; existing
  tests were retained and adapted.
- Frontend: 15 existing tests passed in 5 suites. No frontend code or dependencies
  changed. The existing React testing-library deprecation warning remains.
- `git diff --check` passed. No real MySQL connection, real email delivery or
  running application was used for this stage's checks.

Integration cases use disposable H2 databases with simulated price services,
task execution and activity failures. MVC cases check request/response validation
with mocked services; they are not full JWT/role-security end-to-end tests or
MySQL-specific concurrency/load tests. Manual browser checks remain advisable
after restarting the application, particularly portfolio CRUD, filters/history,
tracked settings/pinning and support pagination.

At the verification checkpoint, Stage 3 changes were awaiting review on
`security-reliability-refactor`; no Stage 3 commit, push or merge had been performed.
