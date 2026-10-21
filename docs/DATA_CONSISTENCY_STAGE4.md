# Data consistency — production roadmap Stage 4

Implemented locally on `production-auth-migrations`, 2026-10-21, after published
Stage 3 commit `9ce96b1`. This checkpoint addresses audit P1-1, P1-2 and P1-3.
The owner approved commit and publication of Stage 4 to GitLab/GitHub on
2026-10-21. No merge or deployment is included.

## Atomic tracked-item creation (P1-1)

`TrackedItemService` resolves the catalogue and prepares the feed **without** a
database transaction. `TrackedItemCreationService.create` is a separate proxied
transaction that checks duplicates, initializes the price/shared observation,
inserts the item and writes its activity event. Any failure rolls back those
changes together. The response is built inside that transaction. It performs
no HTTP, and a failure after a successful insert cannot leave a half-created
tracking record behind.

An independent feed refresh during preparation may commit prices for existing
tracked items even if the subsequent add fails. That is intentional: a valid
market publication is independent of whether one user's new item is accepted.
The new item's insert, any observation first created for it, and its audit
event are the atomic unit, not the earlier network refresh.

Creation acquires the existing database publication guard **before** item writes,
matching the price writer's lock order. The duplicate check inside this lock is
authoritative; the earlier check only avoids unnecessary work. Competing adds
produce one record; an already-tracked item returns HTTP **409**, not a second
record or an authentication failure. The database index is the final backstop,
including writes outside these services.

## Narrow, non-destructive V2 migration

`V2__unique_tracked_items.sql` adds one generated column and a unique index:

- `tracked_market_hash_name` equals `market_hash_name` for tracked rows.
- It is `NULL` for ordinary/legacy portfolio transactions.
- `UNIQUE (user_id, tracked_market_hash_name)` prevents duplicate tracking for
  one owner, while allowing other owners and repeated purchases/sales.
- Database collation governs equality, consistent with the duplicate query.

The generated column is read-only to JPA and ignored by JSON. No public DTO or
frontend field is added. V1 is unchanged. The one InnoDB `ALTER TABLE` fails
atomically when pre-existing duplicate tracking rows exist; it **never** merges,
deletes or changes user records to make the migration succeed.

On 2026-10-21 a read-only preflight of the developer database found **zero**
duplicate tracking groups. Its migration history was still V1. No V2 migration,
application startup or data cleanup was performed against that database by this
stage's verification. Normal next startup applies pending V2 via Flyway before
Hibernate validation. Stop older app instances before upgrading. If another
installation has duplicates, stop and review them with its owner; do not bypass
the constraint or run blind Flyway repair/clean.

V1 adoption remains a separate operation. The maintenance tool now has a guarded
`prepare-v1-reference` action for an empty disposable `investment_reference_*`
database, so shipping V2 does not accidentally make legacy V1 adoption compare
against a newer schema. See [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md).

## Commit-before-publication pricing (P1-2)

`MarketPricePersistenceService.persistFeed`, selected refreshes and dashboard
refreshes use an independent `REQUIRES_NEW` transaction. `PriceFeedService`
replaces its immutable, volatile publication **only after the proxied writer
returns successfully**, which includes transaction commit. A commit failure
keeps the previous cache, current database prices and history intact.

The coordinator refuses refresh/download operations inside an existing database
transaction. Mutation response mapping now uses pure cache reads. Editing an
alert or pin therefore cannot accidentally perform HTTP while holding item
locks. The removed `recordCurrentSnapshotForItem` path is replaced by the atomic,
database-only creation boundary.

New tracking rows use the prepared committed publication when it is not older
than recorded data. With a cold cache, missing market price or an older local
publication, creation uses the latest stored observation for that market name,
or leaves the price pending if none exists. It does not backdate a new record,
fabricate missing days or overwrite newer shared observations. One source
version cannot silently acquire a different recorded conversion rate/date for
a newly tracked market name.

## Consistent item mutations (P1-3)

Tracked settings, bookmark toggles and deletion use an owner/type-scoped current
locking read from `ItemMutationRepository`. Pending work is flushed first, and
only the relevant item is detached and reloaded through a locking query. No
unrelated account/session entities or collections are cleared.

This matters with Hibernate 7 and InnoDB repeatable-read: returning a previously
managed entity, or refreshing an already-locked entity using a non-locking SQL
read, can still expose the old transaction snapshot. The regression test first
loads an old item, commits a price update on another connection, then mutates
the item and verifies the new price is retained and visible.

`@DynamicUpdate` narrows ORM writes to dirty fields. Price bulk updates already
change **only `price`**, while user mutations serialize on the affected row.
Both lock orderings preserve prices, pin state, alerts and unrelated fields.
Two overlapping toggles both execute; they do not collapse into one change.
This is a pessimistic/field-specific strategy, not an incomplete `@Version`
annotation that bulk writers could ignore. Independent full-form portfolio
edits still use the existing last-submitted-values contract; this does not add
a user-visible conflict-resolution interface or change purchase/sale semantics.

## Verification

- Backend: full `test bootJar`, **445 tests: 444 passed, one existing manual
  measurement disabled**, zero failures/errors; application archive builds.
- MySQL: **23/23** migration, startup, rollback, uniqueness and concurrency tests
  pass on isolated **8.4.11**, and **23/23** on isolated **8.0.37** for current
  developer-version compatibility. Both temporary servers were stopped.
- Migration tests cover fresh V1+V2 startup, V1 upgrade with preserved rows,
  duplicate-upgrade refusal without partial DDL/deletion, retained V1 adoption,
  repeated portfolio rows, cross-owner tracking and case-insensitive uniqueness.
- Transaction integration tests use real Spring proxies/commits, not a mocked
  writer. They inspect the old cache during `beforeCommit`, deliberately fail
  commit, and verify database/cache/history rollback. HTTP clients are mocked.
- Frontend regression: lint, **169/169** tests, build and **17/17** production
  HTTP smoke checks pass. No frontend design or workflow was changed.

No real provider/mail calls, user password changes, real database migration or
deployment were performed. Browser/manual acceptance remains unchecked.
Existing upstream frontend sourcemap and bundle-size warnings remain. Remote
pipeline results for the published Stage 3 commit are not inferred from local
tests. Stage 4 publication is approved; Stages 5–8 need separate approval.

## Boundaries and remaining work

The short creation transaction shares the publication guard with price writers;
a large refresh can temporarily delay a new tracked-item insert. HTTP happens
before that lock. Load/lock-wait measurements, quotas and pagination remain
Stage 6; no distributed cache or microservices were added.

Caches remain process-local. Independent instances can retain different cached
publication ages, although guarded database writes reject older publications.
Freshness metadata/UI and stale fallback indicators remain Stage 5. Financial
values remain CSGOTrader 24-hour averages converted to EUR, **not** exact Steam
sales on the shown date. Feed cadence, cache intervals, daily history rules,
retention, connected chart lines, calculator retention and token lifetimes are
unchanged. These fixes alone do not declare the product production-ready.

## Primary references

- [MySQL generated columns](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html)
- [InnoDB unique indexes on virtual generated columns](https://dev.mysql.com/doc/refman/8.4/en/create-table-secondary-indexes.html)
- [Spring independent transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
