# Structural refactoring (Stage 7)

Updated: 2026-10-11. Work branch: `security-reliability-refactor`.

This stage reorganizes existing code. It does not introduce new application
features, redesign pages, change API routes, change the database schema or alter
the daily price-history policy. Commit, push and merge require separate approval.

## Frontend boundaries

- `features/support/SupportPanelPage.jsx` composes the Support Panel sections.
  Its existing requests, polling and actions are owned by
  `features/support/hooks/useSupportPanel.js`; presentation is under
  `features/support/components/`.
- `features/price-tracker/PriceTrackerPage.jsx` composes search, the tracked-items
  table and the detail/delete dialogs. The feature's `hooks/usePriceTracker.js`
  owns their existing state, request lifecycle and actions. Views are under
  `features/price-tracker/components/`.
- `features/portfolio/PortfolioOverviewPage.jsx` retains portfolio and pinned-item
  loading. History and pinned-item views are under `features/portfolio/components/`.
- `shared/hooks/useRequestScope` and `usePriceHistories` remain shared
  request-lifecycle helpers. `features/catalogue/useCatalogueSearch` is reused
  by catalogue consumers. Reads still support cancellation; writes are not
  repeated just because the user leaves their page. Session/version guards remain.

### One application HTTP client

`shared/api/ApiClient.js` owns an isolated Axios instance. It attaches credentials
only to the configured backend origin, manages the existing session/version
checks and delegates token refresh to `AuthService`. The authentication service
installs these interceptors when imported by the application bootstrap. Domain
services do not manage tokens themselves, and global Axios defaults/interceptors
are no longer changed.

The domain API modules are:

- `PortfolioApi`: portfolio records, totals and transaction history;
- `PriceTrackerApi`: tracked items, bookmarks and price history;
- `CatalogueApi`: catalogue search;
- `InventoryApi`: public inventory calculation;
- `ContactApi`: contact requests;
- `SupportPanelApi`: restricted Support Panel requests;
- `AuthService`: authentication and the current browser session only.

API routes, request fields, pagination/filter parameters and support-reason
headers are retained. Item forms still receive Axios error objects; existing
domain methods that previously exposed a server error payload keep that contract.
Cancellation is not converted into a successful result or a generic error.

### Shared price charts

`shared/components/PriceCharts.jsx` supplies compact and detailed charts.
`shared/utils/priceChartModel.js` builds their shared dataset and options;
`shared/utils/priceHistory.js` retains UTC publication-day filtering, chronological
chart points, newest-first log sorting and the shared percentage-change function.
`useChartPointTooltip` is used by the PriceTracker and pinned-item charts.

Existing chart sizes, colours, empty states, point tooltips and category-axis
spacing are retained. This is a structural extraction, not a change to the
meaning or timing of the displayed observations. Portfolio transaction dates
keep their separate formatter; they are not treated as UTC feed publications.

## Backend price responsibilities

- `CsgotraderFeedClient`: conditional/forced HTTP downloads, bounded gzip
  decoding, JSON validation and source-version construction. No database writes
  or currency lookup occur here.
- `UsdToEurRateService`: received USD/EUR conversion rate and the existing
  previously received/stored-rate fallback. No invented fixed rate is used.
- `PriceRefreshCooldown`: existing retry-delay policy, including provider
  `Retry-After` headers for HTTP 429/503.
- `PriceFeedService`: refresh coordination, locking, publication-date guards,
  cold-start revalidation, immutable in-memory snapshots and retry deadlines.
- `MarketPricePersistenceService`: existing transactional price/history writes.
  The coordinator still publishes the new cache only after persistence succeeds.
- `TrackedItemMapper`: pure conversion of an item and an optional cached price
  into the existing DTO. It makes no HTTP requests and performs no writes.

Tracked-item listing makes one explicit refresh check and then maps items using
cache-only reads. A mapper no longer hides external requests inside DTO
conversion. Other existing operations retain their explicit refresh paths.

The daily UTC uniqueness, unchanged next-day observations, latest-publication
replacement, stale-publication rejection and stored-price fallback are retained.
See [DAILY_PRICE_HISTORY.md](DAILY_PRICE_HISTORY.md) for that separate correction.

## Removed unused configuration

The unreferenced `priceHistoryChart` and `trackedItems` Caffeine cache definitions
and their TTL properties were removed. They had no cache consumers and their
removal does not disable price-history loading or the price-feed snapshot.

The four used Caffeine caches remain: `csItemsSearch`, `portfolioSummary`,
`inventoryCalculatorCache` and `steamProfileResolution`. Their TTLs, size limits
and transaction-aware invalidation behavior are unchanged. The separate price
snapshot/check interval is also unchanged.

Unused frontend aliases and helpers were removed from the former mixed
`UserService` (now `AuthService`) after verifying that they had no callers.
Backend API endpoints were not removed.

## Initial Stage 7 verification checkpoint

- Backend: 295 tests in 34 suites, 294 passed, 1 skipped, 0 failures/errors.
- Frontend: 84 tests in 18 suites, all passed.
- Backend `test bootJar`: successful.
- Frontend production build: successful.
- Existing regression tests were retained and adapted to the new boundaries.
  Added tests cover domain API requests/session isolation, shared chart data and
  tooltip placement/cleanup, item details/history pagination, pure DTO conversion
  and cache-only display reads.

At this checkpoint the skipped backend test was `SteamPriceCacheMeasurement`
(now `PriceFeedCacheMeasurement`), an explicitly manual external-network
measurement. It is not counted as passed. Backend tests
use test databases/mocked external services; this stage does not connect to or
modify the real MySQL database.

Existing frontend tooling warnings remain: deprecated testing-library `act`
usage and the undeclared Babel private-property plugin dependency in CRA.
`--detectOpenHandles` also reported a timeout originating from that Babel plugin,
not an application polling/request timer. Tooling changes belong to Stage 8.

These checks do not replace a manual review of portfolio actions, chart dialogs,
inventory navigation and Support Panel workflows, and do not establish load
capacity or production readiness. A running backend must be restarted to load
the refactored classes; no application restart or live data refresh was performed
as part of this stage.

## Separately approved organization and DRY follow-up

After the initial Stage 7 commit/push, source organization and naming changes
were approved along with a bounded duplication review. Frontend code is grouped
under `features/` and `shared/`; backend packages group catalogue, portfolio,
tracking, pricing and inventory responsibilities. The misspelled `exeption`
package is now `exception`. API routes, JSON fields and JPA entity names/mappings
are unchanged. See [CODEBASE_STRUCTURE.md](CODEBASE_STRUCTURE.md) for the tree,
rename guide and feature boundaries.

The follow-up extracts shared table pagination, 7D/1M/3M controls and API
response/error handling. Portfolio add/edit share field mapping; detail,
update and delete share a portfolio-only ownership lookup. Authentication
guards and different portfolio/tracked-item rules are not generalized away.

The empty `Footer.jsx`, unreferenced source `logo.svg` and unused `PriceHistoryDto`
were removed. The live legacy `PriceHistory` model and repository are retained.
No application configuration, dependency versions, cache settings, data policy
or UI styles were changed by the follow-up.

Follow-up verification:

- Backend: 299 tests in 35 suites, 298 passed, 1 skipped, 0 failures/errors.
- Frontend: 89 tests in 21 suites, all passed.
- Backend `test bootJar` and frontend production build: successful.
- Four new backend add/edit mapping cases and five frontend pagination, period
  selection and response/error cases were added; existing tests were retained.
- No real database connection, external provider request or application restart
  was performed. Existing tooling warnings and Stage 8 remain deferred.

Commit/push for the organization and backend DRY follow-ups were approved on
2026-10-12. Manual workflow review and merge remain separate steps.

The additional backend-only review of 2026-10-12 and its latest results
(342 tests, 341 passed, 1 skipped; backend build successful) are documented in
[BACKEND_REUSE.md](BACKEND_REUSE.md). The initial checkpoint totals above are
retained as historical results.
