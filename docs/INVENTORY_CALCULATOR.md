# Steam inventory calculator

Stage 5 of the approved refactoring sequence, 2026-10-10.

## Data flow

1. Validate a SteamID64, Steam Community profile URL or custom profile name.
   URL input must use the exact Steam Community host, without credentials or a
   custom port. Requests are constructed from validated identifiers and always
   use fixed HTTPS Steam endpoints; the input URL itself is never fetched.
2. Resolve custom names through the public profile XML endpoint. Direct IDs
   do not need this lookup. XML processing forbids DOCTYPE and external
   DTD/schema access, with a 131,072-character limit. An unresolved name is a
   bad-input error; unavailable or malformed provider data is not an empty
   inventory. Optional name/avatar failure uses a generic profile label.
3. Load CS2 inventory context 730/2, in English, with 2,000 asset entries
   requested per page. Follow Steam's cursor fields, sequentially and within
   the configured limits. There are no automatic retry loops.
4. Validate the complete response, then aggregate assets and value the
   inventory. Catalogue reads use batches of at most 500 market names.
5. Cache only a successful result under the resolved SteamID64. Return the
   existing DTO endpoint and display the result in the existing calculator UI.

Responsibilities are split across SteamProfileReference (input),
SteamCommunityClient (provider responses), InventoryValuationService
(aggregation/valuation) and the existing SteamInventoryService facade.
The controller remains a thin, validated HTTP boundary.

## Quantities and completeness

- Quantity is the positive integral Steam asset **amount**, not the number of
  JSON rows. **totalItemCount** and category counts include these amounts.
- Descriptions match assets using both **classid** and **instanceid**.
- Identical repeated asset IDs are counted once. Conflicting duplicates,
  missing descriptions, changing advertised inventory counts, malformed data,
  repeated/missing cursors, later-page errors and exceeded limits fail the
  calculation instead of returning a silently truncated total.
- When **total_inventory_count** is present, it is checked against distinct
  asset entries, not against the sum of stack quantities.
- A successful, explicitly empty response is an empty inventory. Missing
  assets without explicit zero count, error payloads or unsuccessful responses
  are not.
- Trade availability comes from descriptions. A grouped row is **tradable**
  only when all its units are tradable. **tradableQuantity** and
  **nonTradableQuantity** represent mixed groups; these do not affect valuation.

Pagination and stack semantics follow fields used by
[Valve's Steam Community inventory client](https://community.akamai.steamstatic.com/public/javascript/economy_v2.js).
This is a public Community endpoint, not the authenticated Steamworks inventory
API and not a guarantee of availability or an officially stated request quota.
Steam may change its response format or an inventory while pages are loading.
The checks detect known inconsistencies; they cannot create an atomic snapshot
of Steam's remote inventory.

## Price meaning

- Prices remain CSGOTrader **24-hour average estimates**, converted to EUR.
  They are not live Steam listing prices or historical Steam sale records.
- One immutable price publication and its conversion rate are captured for
  the whole calculation. No per-item HTTP price requests are made.
- A missing item price is null for **unitPrice** and **totalPrice**, displayed
  as **N/A**, not converted to a zero-valued item.
- **totalValue** and **categoryValues** are subtotals of priced units.
  **unpricedItemCount** counts units without a price, including items without a
  market hash name. The UI labels a partial total as **Known Value** and
  explains which item count is unpriced.
- A display name alone is not used as a pricing identifier. Such items remain
  in ownership/category counts but are not assigned another item's price.
- If no usable price feed has been loaded, a non-empty inventory with market
  names fails with a temporary-unavailable error rather than caching an
  all-zero valuation. An empty inventory or one without market names needs no feed.
- A previously loaded feed can be retained during a price-source outage,
  according to Stage 4. This is an estimate from available cached data, not
  a promise of current prices or a freshly received exchange rate.
- The most valuable item still means highest **unit price**, not highest
  grouped total. Table rows sort by grouped known value, descending, with
  unavailable values last and name-based tie ordering.

Inventory results themselves are not stored in MySQL. Catalogue records are
read, not changed by valuation. The shared price service retains its existing
Stage 4 refresh/persistence behavior: a normal feed refresh may update tracked
prices and observations independently of this calculator.
No new database entities or schema migrations were introduced.

## Cache and browser session

Both successful vanity resolution and successful valuation default to a
15-minute Caffeine expiry after write. Valuations are keyed by resolved
SteamID64, so a direct ID and the corresponding URL/custom name share a result.
Spring's synchronized cache loading lets concurrent callers for the same key
share a load within one backend instance. Errors are not cached as results.

The existing cache entry bound (CACHE_MAXIMUM_SIZE, default 1,000 per cache)
is preserved. It is not a measured memory budget or proof of capacity.
Multiple server instances do not share these caches/cooldowns; a public
large-scale deployment still needs coordinated limits and capacity testing.

The browser preserves the last successful result for the signed-in user,
through route changes and reloads in that tab, until sign-out, tab/session
closure or a replacement successful calculation. A failed new search retains
the previous successful result and explicitly says so.
No calculation timestamp was added. Old v1 browser results are discarded once
because they may contain the former quantity/zero-price mistakes; new results
use v2 and retain the same session behavior.

## Configurable loading bounds

| Environment variable | Default | Meaning |
|---|---:|---|
| CACHE_INVENTORY_CALCULATOR_TTL | 15m | Successful valuation and vanity-resolution TTL |
| INVENTORY_STEAM_MAXIMUM_PAGES | 5 | Maximum sequential inventory pages |
| INVENTORY_STEAM_MAXIMUM_ASSETS | 10000 | Maximum distinct asset entries/descriptions |
| INVENTORY_STEAM_MAXIMUM_QUANTITY | 100000 | Maximum total units including stacks |
| INVENTORY_STEAM_PAGE_DELAY_MS | 1000 | Delay before each additional page |
| INVENTORY_STEAM_FAILURE_RETRY_INTERVAL_MS | 60000 | Minimum Steam HTTP 429/503 cooldown |

Steam HTTP 429/503 Retry-After, when valid and later than the minimum,
extends a shared in-process cooldown. Optional profile requests also respect
that cooldown. It is not a guaranteed ban-prevention mechanism.
Existing external HTTP connect/read and response-byte limits also apply.
The page bound limits the number of sequential requests; it is not a separate
hard deadline for the entire calculation.

XML security uses the
[JDK JAXP security mechanisms](https://docs.oracle.com/en/java/javase/21/security/java-api-xml-processing-jaxp-security-guide.html).
Cache loading uses
[Spring's synchronized caching support](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html).

## Verification

Verified using simulated HTTP responses, mocked repositories and the existing
H2 integration tests. No real Steam accounts, real inventories, SMTP delivery
or real MySQL databases were used/changed by this stage's verification.

- Backend: **281 tests in 33 suites; 280 passed, 1 skipped, 0 failures/errors**.
  This adds 91 tests to the Stage 4 checkpoint. The same skipped
  SteamPriceCacheMeasurement (renamed PriceFeedCacheMeasurement in the Stage 7
  organization follow-up) remains an explicitly manual network measurement.
- Backend application archive (bootJar): successfully built.
- Frontend: **19 tests in 5 suites, all passed**.
- Frontend production build: successful.
- Coverage includes input normalization/rejection, stack amounts, pagination,
  duplicate/incomplete data, empty/private inventories, provider cooldown,
  XML external-entity rejection, missing/partial prices, catalogue identity
  and batching, canonical/concurrent caching, recovery after failed loads,
  API error/null-price serialization and browser-session isolation/migration.
- Existing React testing act and CRA/Babel tooling warnings remain; package
  upgrades are reserved for the tooling stage.

These checks are not a live Steam acceptance test, a load benchmark, a public
deployment audit or a promise that all provider response variants are supported.
Manual testing of a chosen public inventory remains an explicit follow-up.
