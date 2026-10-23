# Daily market price history

Each market hash name and price source has at most one observation per UTC
publication day. The UTC date is derived from the provider publication timestamp,
not from the user's timezone, page visit or application restart.

- A newer publication on the same UTC day replaces that day's observation.
- A publication on another UTC day creates a point even when its price is unchanged.
- Receiving an already stored publication is idempotent; no percentage threshold applies.
- Missing/offline days are not filled with invented observations.
- When provider dates are missing, existing content/version recognition still reuses
  the first stored receipt timestamp. Unchanged undated content is not evidence of a new day.
- The log is newest first. Both tracker and pinned-item charts use the same UTC
  grouping and display dates, but plot points oldest first.

Daily observations retain the actual latest publication time, source version,
USD price, EUR conversion rate and EUR price. The unique day constraint protects
against duplicate inserts. A single database write-guard row per source serializes
the short persistence transactions across application instances; external HTTP
downloads occur outside this lock. Current tracked prices and history commit
together. Unrelated portfolio records and tracking preferences are not updated.

A publication older than the source's latest stored observation is rejected,
including after an application restart. The date is rechecked while holding the
database write guard, preventing an older writer from winning a concurrent race.
On cold start, an older dated CDN response is conditionally revalidated once;
if it remains older or unusable, stored prices are left intact.
The same publication must retain its USD values and recorded conversion rate.
Failures retain the previous cache and database values instead of publishing an
uncommitted or fabricated price.

## Existing MySQL installations

The new JPA mapping works for a new empty database. Do **not** rely on Hibernate
`update` to adopt an existing populated snapshot table: it cannot select the right
daily winners or safely backfill the new non-null date column.

1. Stop every application instance that can write to this database.
2. Export `market_price_snapshot`, including its schema and data, to private storage.
   Keep credentials out of files, command arguments, logs and Git.
3. Restore the export in an isolated verification database (or session-local
   temporary tables when database creation is not permitted), and compare every
   restored row and the backup checksum with the original.
4. Rehearse `scripts/sql/20261010_daily_market_price_history.sql` in that verification
   database. Confirm one row per item/source/UTC day; the retained row must be the
   original row with the latest `observed_at`, breaking ties by highest ID.
5. Run the same one-time script on the original while writers remain stopped.
   Confirm the expected counts, no missing days, no null/misaligned dates, both
   uniqueness constraints and the initialized `market_price_write_lock` row.
6. Restart the new application code. Keep the backup for recovery.

The script touches only market-history schema/data and its write guard. It does
not update users, credentials, portfolio transactions or current item prices.
MySQL DDL is not transactionally reversible: a failed adoption must be inspected
and recovered from the verified backup, not blindly rerun.

This targeted adoption does not enable Flyway or establish a full schema baseline.
Existing production installations must adopt it explicitly before using the new
code with `ddl-auto=validate`. Fresh schemas initialize the guard through the
service's startup initializer, safely tolerating concurrent application startup.

## Verification

Automated checks cover same-day replacement, unchanged next-day prices, UTC
midnight, idempotence, duplicate protection, stale responses after restart,
concurrent writers, rollback and newest-first display. H2 integration checks
do not by themselves establish MySQL compatibility; the MySQL adoption rehearsal
and post-adoption checks are required separately.

### Verified local adoption, 2026-10-10

- Exported the original 300 market observations to the Git-ignored backups directory.
- Restored the export in session-local temporary MySQL tables and verified all
  columns against the original. No extra database privileges were granted.
- Rehearsed the exact migration logic and verified all retained daily winners.
- Adopted the original table with writers stopped: 240 daily rows retained,
  60 superseded same-day rows removed. Every available item/source/day remains.
- Verified the non-null UTC date column, unique daily index and unchanged item
  record count/checksum. The backup remains available for recovery.
- Backend: 292 tests, 291 passed, one explicitly manual network test skipped.
  Frontend: 66 tests passed. Backend archive and frontend production build passed.
- Restarted the new backend with schema validation (no Hibernate schema updates).
  It loaded the latest stored provider publication successfully and recorded
  zero extra observations on receiving that same publication.

These are local correctness/recovery checks, not production load or provider
accuracy certification. Publication and merging remain separate user decisions.
