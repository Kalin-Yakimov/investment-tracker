# Structural refactoring (Stage 7)

Updated: 2026-10-11. Work branch: `security-reliability-refactor`.

This stage reorganizes existing code. It does not introduce new application
features, redesign pages, change API routes, change the database schema or alter
the daily price-history policy. Commit, push and merge require separate approval.

## Frontend boundaries

- `pages/AdminPanel.jsx` composes the Support Panel sections. Its existing
  requests, polling and actions are owned by `hooks/useSupportPanel.js`;
  presentation is under `components/support/`.
- `pages/PriceTracker.jsx` composes search, the tracked-items table and the
  detail/delete dialogs. `hooks/usePriceTracker.js` owns their existing state,
  request lifecycle and actions. Views are under `components/prices/`.
- `pages/Home.jsx` retains portfolio and pinned-item loading. The portfolio
  history and pinned-item views are under `components/portfolio/`.
- `useRequestScope`, `useCatalogueSearch` and `usePriceHistories` remain shared
  request-lifecycle helpers. Reads still support cancellation; writes are not
  repeated just because the user leaves their page. Session/version guards are
  retained.

### One application HTTP client

`service/ApiClient.js` owns an isolated Axios instance. It attaches credentials
only to the configured backend origin, manages the existing session/version
checks and delegates token refresh to `UserService`. The authentication service
installs these interceptors when imported by the application bootstrap. Domain
services do not manage tokens themselves, and global Axios defaults/interceptors
are no longer changed.

The domain API modules are:

- `PortfolioService`: portfolio records, totals and transaction history;
- `PriceService`: tracked items, bookmarks and price history;
- `CatalogueService`: catalogue search;
- `InventoryService`: public inventory calculation;
- `ContactService`: contact requests;
- `AdminService`: restricted Support Panel requests;
- `UserService`: authentication and the current browser session only.

API routes, request fields, pagination/filter parameters and support-reason
headers are retained. Item forms still receive Axios error objects; existing
domain methods that previously exposed a server error payload keep that contract.
Cancellation is not converted into a successful result or a generic error.

### Shared price charts

`components/prices/PriceCharts.jsx` supplies compact and detailed charts.
`utils/priceChartModel.js` builds their shared dataset and options;
`utils/priceHistory.js` retains UTC publication-day filtering, chronological
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
- `SteamPriceService`: refresh coordination, locking, publication-date guards,
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

Unused frontend aliases and helpers were removed from the mixed `UserService`
after verifying that they had no callers. Backend API endpoints were not removed.

## Verification

- Backend: 295 tests in 34 suites, 294 passed, 1 skipped, 0 failures/errors.
- Frontend: 84 tests in 18 suites, all passed.
- Backend `test bootJar`: successful.
- Frontend production build: successful.
- Existing regression tests were retained and adapted to the new boundaries.
  Added tests cover domain API requests/session isolation, shared chart data and
  tooltip placement/cleanup, item details/history pagination, pure DTO conversion
  and cache-only display reads.

The skipped backend test remains `SteamPriceCacheMeasurement`, an explicitly
manual external-network measurement. It is not counted as passed. Backend tests
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
