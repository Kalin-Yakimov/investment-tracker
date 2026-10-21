# Frontend correctness and resilience — production Stage 5

Implemented and verified locally on 2026-10-21 on `production-auth-migrations`,
after Stage 4 commit `5dd3983`. This is the production roadmap's Stage 5, not the
older numbered refactoring stages. The owner approved commit and push on
2026-10-21; merging and deployment remain unauthorized. No real application
database was changed or started by the implementation/test runs.

## Preserved behavior

- React/Spring Boot/MySQL remain the architecture; no state-management or chart
  adapter dependency was added.
- Prices remain CSGOTrader `last_24h` USD averages converted to EUR, not live
  Steam listing prices or guaranteed transaction values.
- The 7D/1M/3M selectors use recorded observations only. Missing days are not
  fabricated, and the line connects available points.
- Small charts remain 180 × 44 px, with the same dark grid, single point and
  gray No data treatment. The existing tooltip remains above the selected point
  and is attached to the document body, outside the chart's clipping area.
- The calculator retains the successful result across navigation for the same
  signed-in account. Successful replacement and logout/account isolation remain
  unchanged. No calculation-time label was added.
- Search debounce stays 1000 ms. Suggestions stay present while the next search
  runs. Existing filtering, server pagination, repeated portfolio transactions,
  30-minute access tokens, remembered refresh lifetime and global logout remain.

## P1-4: price provenance and freshness

Tracked and pinned item DTOs now include `priceFreshness`. The price itself is
unchanged; metadata describes **that publication and conversion rate**, rather
than making every page load look like a new market observation.

| Field | Meaning |
| --- | --- |
| `sourcePublishedAt` | Provider `Last-Modified`, when actually supplied |
| `observedAt` | Existing observation date: publication time, or first receipt without a provider date |
| `lastSuccessfulCheck` | Successful local HTTP check, including 304; not a new publication |
| `fxRateAsOf` | `time_last_update_unix` supplied with the USD/EUR quote |
| `storedFallback` | Persisted data used without confirming a matching current cached publication |
| `sourceStale` / `fxStale` | Known timestamp exceeds its configured display threshold |
| `status` | `FRESH`, `STALE`, `UNKNOWN`, `STORED_FALLBACK` or `UNAVAILABLE` |

Missing timestamps stay unknown. A successful check cannot make an old source
publication or FX quote recent. Reused conversion quotes retain their original
date across outages and restarts. Provider timestamps over five minutes in the
future are rejected. Metadata is captured with the cache publication atomically,
so a concurrent refresh cannot attach the next publication's check to an older
cached value. Stored prices are read in batches, not once per item. A newer
persisted publication takes precedence over an older local cache; a locking-read
item price is not paired with inconsistent older transaction-snapshot metadata.
Item setting changes and response mapping never perform provider HTTP calls.

Prices expose a compact hover/focus explanation. A small `!` marks uncertain or
old metadata; no new large explanatory banner was added. A trend's explanation
uses the actual first/last available observations, not a promise of a complete
week or month. Source attribution links appear below the relevant sections.
The free FX provider's [official documentation](https://www.exchangerate-api.com/docs/free)
requires attribution; the UI now includes it. Inventory results remain cached
estimates and do not acquire invented per-item publication timestamps.

### Additive V3 and settings

`V3__price_freshness_metadata.sql` adds nullable `source_published_at` and
`fx_rate_as_of` to `market_price_snapshot`. Existing rows keep NULL metadata.
No historical date is inferred or recalculated, and V1/V2 are unchanged.

| Environment variable | Default | Effect |
| --- | ---: | --- |
| `PRICE_FEED_MAXIMUM_PUBLICATION_AGE_MS` | 129600000 (36 hours) | Warn about a known old source publication |
| `PRICE_FEED_MAXIMUM_FX_AGE_MS` | 259200000 (3 days) | Warn about a known old conversion quote |

These are configurable warning policies, not a guarantee about provider update
times. They do not change the two-hour source-check interval, scheduler, failure
cooldown, cache lifetimes, stored amounts or retention. Positive values are
required. Tuning and production monitoring remain later operational work.

V3 was tested on disposable MySQL 8.0.37 and 8.4.11. The real developer database
was not touched: the next normal backend startup will apply pending V2 and V3.
Follow [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md) for safe upgrades.

## P1-6: recoverable failures

- Portfolio rows, totals, pinned items, tracked items, individual history charts,
  item details and catalogue suggestions distinguish failure from real emptiness.
  A failed read no longer masquerades as No data, zero totals or missing items.
- Explicit Retry repeats only the failed read. Successful data is not rewritten
  by an old route/account response. Existing cancellation, four-request history
  concurrency and write guards remain in place.
- The API client now has a finite 120-second transport timeout. Inventory uses
  300 seconds for bounded Steam pagination/profile resolution/cold price work;
  auth refresh keeps its separate 15-second timeout. These settings live in
  `src/config/uiConfig.js`, not scattered literals.
- Network failures/timeouts do not automatically repeat writes. A mutation
  timeout explains that it may already have succeeded and asks the user to
  inspect the data before submitting again. Existing one-time 401 session
  refresh remains distinct from a network replay.
- Route and root error boundaries replace a blank rendering failure with a
  generic recovery screen. No stack, request headers or token is displayed.
  Route boundaries reset on route/account changes. Error boundaries do not
  replace ordinary API error handling or guarantee recovery from every browser
  failure.
- Failed deletion stays open with an actionable error. Successful tracking
  removal does not falsely claim to delete shared market observations.

## P1-7: elapsed-time charts and dates

Chart.js uses a linear numeric UTC timestamp X axis without an additional date
adapter. A four-day gap takes four times the horizontal distance of a one-day
gap. The data is still chronological; the history table remains newest first.
The same implementation is used for tracker, detailed and pinned charts.
The horizontal bounds match the first and last available observations: automatic
tick rounding must not leave unused dates at either end. A single observation
keeps its centered point. Independent vertical price scaling remains unchanged;
similar shapes are not an assertion of equal percentage changes.
Invalid, negative and future chart points are excluded; a zero price is valid.
Zero or one point never creates a fabricated percentage change.

Portfolio `LocalDate` values use a shared calendar-date parser/formatter, not
ambiguous locale strings or UTC conversion. Invalid dates are rejected rather
than rolling into another month. UTC price observations and date-only purchase
dates are deliberately different concepts.

## P1-10: forms

Registration and editing share validation and an explicit request payload:
positive whole quantity up to the Java integer limit, non-negative price with
at most 36 integer digits and two decimals (matching the backend), valid date/status/name, and a
catalogue selection when registering. Numeric 0 and text "0" are accepted;
blank/negative/overprecision values are not. Price is sent as a decimal string
to avoid introducing an extra large-number rounding conversion in the payload.
The backend remains authoritative for validation and calculations.

Names linked to a catalogue item are read-only during editing, preventing a
display name that contradicts the linked subject. Legacy manual names remain
editable. Errors retain entered values, loading failure disables Save and offers
Retry, and Cancel stays available outside the disabled fieldset. Duplicate
client-side sorting of an already filtered/paginated server page was removed.

## P1-13: keyboard controls

The shared catalogue combobox exposes listbox/options, expanded/busy/active
states, Arrow Up/Down, Enter and Escape. Item names are real buttons/links rather
than mouse-only cells. Focus outlines and filter/date labels are explicit.
Tracker dialogs reuse the already installed React Bootstrap Modal with a focus
trap, Escape handling and focus restoration. After a deleted trigger disappears,
focus returns to the stable search input. The history table is a text alternative
to the canvas chart. This is targeted accessibility work, not a full WCAG audit
or a claim that every screen has been certified.

## Verification checkpoint

| Check | Result |
| --- | --- |
| Backend `test bootJar` | 459 tests: 458 passed, 1 existing explicitly manual test skipped; build passed |
| Disposable MySQL 8.4.11 | 24 migration/startup/transaction/concurrency tests passed |
| Disposable MySQL 8.0.37 | Same 24 tests passed; existing system service/data not used |
| Frontend lint | Passed with zero warnings |
| Frontend Vitest | 212 tests in 37 files passed (including the chart-boundary correction) |
| Frontend production build | Passed; known large-chunk warning remains for later performance work |
| Production HTTP smoke | 17 checks passed, no backend/provider calls |

Tests exercise metadata/stored fallback and conversion dates, 304 without fake
freshness, upgrade preservation, real transaction regressions, retry versus
empty states, finite transport budgets without mutation replay, true time-axis
spacing, calendar validation, zero price, read-only catalogue identity,
keyboard selection, modal Escape/focus and post-delete focus recovery.

An actual in-app browser walkthrough used a separate Vite instance on port 3001
and a loopback-only in-memory fixture on 18081. It verified:

- a synthetic 503 history error, Retry and a recovered chart;
- three observations with a four-day/one-day spacing and newest-first log;
- the original 180 × 44 frame and above-point tooltip in a detailed chart;
- keyboard catalogue navigation, Escape dismissal and restored item focus;
- pinned prices/history and a date-only portfolio record;
- successful editing/saving of price 0 with a read-only catalogue name;
- inventory result retention when navigating away and returning.

The fixture used no real credentials, MySQL, Steam, CSGOTrader or mail. Its test
session was not a backend security test. Temporary servers were stopped and
the test tab was closed. The fixture lives only under ignored `build/`, not
in the deliverable source. Component tests cover the remaining failure/focus
cases. The dependency's existing datepicker missing-sourcemap warning is still
non-blocking. No live provider availability/accuracy, real-user acceptance,
full browser E2E suite, full accessibility certification or production deployment
is claimed. The owner has now approved starting Stage 6; Stages 7–8 remain
pending explicit approval.

## References

- [Chart.js Cartesian axes](https://www.chartjs.org/docs/latest/axes/cartesian/index.html)
- [WAI-ARIA combobox pattern](https://www.w3.org/WAI/ARIA/apg/patterns/combobox/)
- [React Bootstrap Modal](https://react-bootstrap.github.io/docs/components/modal/)
