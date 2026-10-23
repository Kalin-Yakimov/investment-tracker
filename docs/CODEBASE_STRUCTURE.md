# Codebase structure

Updated: 2026-10-12.

Source files are grouped by application feature. A feature owns its views,
requests and related business logic; genuinely shared infrastructure lives in
`shared`, `config` or the existing authentication/security packages. Moving and
renaming code does not change public API URLs, request/response fields, UI
labels, database schema or existing configuration keys.

## Frontend

Source root: `react/investment-tracker/src/`.

```text
src/
  App.jsx                        Route composition
  index.jsx                      Application bootstrap
  config/                        Existing UI timing/settings
  layout/                        Navbar and its styles
  features/
    auth/                        Session service, route guards, forms
      pages/                     Login, registration, activation, recovery
      components/                Username/password dialogs
      styles/
    portfolio/                   Overview, details, edit page, PortfolioApi
      components/                Register form, history, pinned items
      utils/                     Portfolio-specific formatting
      styles/
    price-tracker/               PriceTrackerPage and PriceTrackerApi
      components/                Search, table and dialogs
      hooks/                     usePriceTracker
      styles/
    catalogue/                   CatalogueApi and useCatalogueSearch
    inventory/                   Calculator page, InventoryApi, result session
      styles/
    support/                     SupportPanelPage and SupportPanelApi
      components/                Overview, users, audit, tickets, system views
      hooks/                     useSupportPanel
      utils/                     Support-specific formatting
      styles/
    contact/                     Contact dialog, ContactApi and styles
  shared/
    api/                         Isolated Axios client and response helpers
    components/                  Card, charts, pagination, period selector
    hooks/                       Request/modal scopes, history and tooltip hooks
    utils/                       Request scope, chart model and data utilities
    NotFoundPage.jsx
  testUtils/                     Test-only helpers
```

Tests stay next to the module they exercise. Feature API modules use the shared
HTTP client; they do not each install token interceptors. `AuthService` manages
the current browser session and installs the existing interceptors once.

`useCatalogueSearch` belongs to the catalogue feature even though portfolio and
price tracking both call it. Sharing a feature API is preferable to copying its
search implementation into each consumer.

CSS is relocated with its feature without changing selectors or page layout.
The portfolio overview still imports the PriceTracker stylesheet for existing
shared market-chart/button styles; no new global styling system is introduced.
Stage 8 replaces CRA with Vite/Vitest and moves the HTML entry to the frontend
root. The unconfigured `reportWebVitals.js` callback scaffold is removed; it
never received a callback in the application. Tooling configuration is in
`vite.config.js`, `eslint.config.js` and `scripts/check-runtime.cjs`. See
[FRONTEND_TOOLING.md](FRONTEND_TOOLING.md) for details.
Stage 9 adds `scripts/smoke-build.js`, frontend route integration tests, backend
`workflow/` integration tests and root `.gitlab-ci.yml`. These are verification
entry points, not new production features; see [FINAL_VERIFICATION.md](FINAL_VERIFICATION.md).

## Backend

Source root: `investment-tracker/src/main/java/com/kalin/investment_tracker/`.

```text
investment_tracker/
  auth/                          Authentication endpoints and policies
  security/                      JWT/session enforcement and related security
  user/, role/, email/           Accounts, permissions and email delivery
  admin/                         Restricted support operations, jobs and audit
  support/                       Contact tickets
  portfolio/                     PortfolioController, PortfolioService, DTOs
  tracking/                      TrackedItemController/Service/Mapper and DTOs
  pricing/                       PriceFeedService, feed/rate clients, scheduler
                                 Price persistence, snapshots and repositories
  catalogue/                     CsItem model, search, synchronization and DTOs
  inventory/                     Inventory endpoints, loading and valuation
  item/                          Shared Item model, status and repository
  shared/
    web/                         PageResponse and ApiMessageResponse
    http/                        Shared Retry-After header parsing
    text/                        String normalization
  config/                        Cache, HTTP, executor and application settings
  exception/                     Application exceptions
  handler/                       Global HTTP exception mapping
  validation/                    Request limits
```

Backend tests mirror these packages under `src/test/java/`. The `item` package
remains shared because the current `Item` entity represents both portfolio
records and tracked records. Type-scoped ownership queries remain separate:
moving files does not turn a tracked item into a portfolio record or weaken
access control.

Entity simple names, table/column mappings and repository queries are retained.
The legacy `PriceHistory` entity/repository remains because deletion and cleanup
still use it; the unused `PriceHistoryDto` alone was removed. No migration or
real database access is part of this organization change.

`PriceFeedService` coordinates CSGOTrader refreshes. Its name no longer implies
that prices come directly from Steam. Feed downloading, currency conversion,
retry policy, persistence and pure DTO mapping keep their separate classes.

The separately approved catalogue search improvement adds `CatalogueSearchQuery`
for bounded normalization and the `CsItemSearchRepository` fragment implemented
by `CsItemSearchRepositoryImpl`. Matching and relevance ranking happen in the
database before the eight-result limit. `CsItemService` coordinates the existing
cache; it no longer ranks a truncated candidate list. The obsolete
`CsItemSpecification` and unused `searchByName` method were removed. Entity/table
names and internal `findBestMatch` metadata lookup are unchanged. See
[CATALOGUE_SEARCH.md](CATALOGUE_SEARCH.md) for behavior and verification boundaries.

## Important renames

| Previous name | Current name |
|---|---|
| Frontend `Home` | `PortfolioOverviewPage` |
| Frontend `AdminPanel` | `SupportPanelPage` |
| Frontend `PriceTracker` | `PriceTrackerPage` |
| Frontend `AddItem` | `RegisterPortfolioItem` |
| Frontend `ViewItem` | `PortfolioItemDetailsPage` |
| Frontend `EditItem` | `EditPortfolioItemPage` |
| Frontend `UserService` | `AuthService` |
| Frontend domain `*Service` modules | `PortfolioApi`, `PriceTrackerApi`, `CatalogueApi`, `InventoryApi`, `ContactApi`, `SupportPanelApi` |
| Frontend `ProtectedRoutes` / `AdminRoute` | `ProtectedRoute` / `SupportPanelRoute` |
| Backend `ItemController` / `ItemService` | `PortfolioController` / `PortfolioService` |
| Backend `ItemResponseDto` | `PortfolioItemResponseDto` |
| Backend `SteamPriceController` / `SteamItemDto` | `TrackedItemController` / `TrackedItemDto` |
| Backend `SteamPriceService` | `PriceFeedService` |
| Backend `ScheduledPriceUpdateConfig` | `PriceUpdateScheduler` |
| Backend `exeption` package | `exception` |

Frontend `PortfolioApi` calls the server; backend `PortfolioService` implements
portfolio business rules. They have different responsibilities despite serving
the same feature. Existing routes such as `/admin`, `/tracker`, `/viewItem/:id`
and their backend endpoints are deliberately unchanged.

## Bounded DRY improvements

- `TablePagination`: identical portfolio-list and transaction-history navigation
  now shares markup, labels and edge-button behavior. Support pagination has
  different behavior and is not forced into this component.
- `PriceTimeframeSelector`: PriceTracker and pinned items share the unchanged
  7D/1M/3M controls.
- `apiData` / `apiError`: API response unwrapping and existing error-payload
  handling have one implementation. Cancellation and network error objects
  retain their identity. Session-changing operations retain their own guards.
- `PortfolioService.applyRequest`: adding and editing records share catalogue
  linking/manual-name normalization and field assignment.
- `PortfolioService.findOwnedPortfolioItem`: detail, update and delete share
  the same portfolio-only ownership lookup.

Previously extracted charts, history helpers, request scopes and API client are
also reused. Similar-looking code with different meaning is intentionally kept
separate: portfolio dates versus UTC publication dates, portfolio versus tracked
ownership, and different pricing/valuation rules. A generic abstraction is not
introduced merely to reduce line count.

## Organization follow-up verification checkpoint (2026-10-11)

- Backend: 299 tests, 298 passed, 1 skipped, no failures/errors; `bootJar` built.
- Frontend: 89 tests, all passed; production build completed.
- Four new backend cases cover add/edit with catalogue/manual records. Five new
  frontend cases cover pagination, period selection and response/error handling.
- The skipped `PriceFeedCacheMeasurement` is a manual external-network
  measurement, not a successful automated benchmark.
- Tests use disposable H2 databases or mocks. Real MySQL data, provider APIs
  and a running application were not touched by this follow-up.

Automated checks do not establish full manual coverage or production readiness.
Existing frontend-tooling warnings are reserved for Stage 8. Manual review and
commit/push/merge approval remain separate steps.

## Backend reuse follow-up (2026-10-12)

The backend-only review additionally removes the nested duplicate
`AdminDtos.PageResponse`; Support Panel endpoints now use the same
`shared.web.PageResponse` as portfolio endpoints with identical JSON fields.
Issuing/clearing refresh cookies shares a private cookie builder, plain and
authentication errors share dedicated response builders, and tracked catalogue
resolution shares a trusted-value factory. User-detail audit access reuses the
existing read-only access recording method.

`shared.http.RetryAfter` parses numeric and HTTP-date provider hints for both
price refreshes and Steam inventory requests. Each caller retains its own
status checks, minimum cooldown and retry state. No timing policy is changed.
Details and verification are in [BACKEND_REUSE.md](BACKEND_REUSE.md).
