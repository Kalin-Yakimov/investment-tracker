# Frontend request lifecycle — Stage 6

This is the historical refactoring Stage 6 from 2026-10-10, **not** Stage 6 of
the current production roadmap. For subsequent timeout, retry, chart, form and
accessibility changes see [FRONTEND_RELIABILITY_STAGE5.md](FRONTEND_RELIABILITY_STAGE5.md).

This stage preserves the existing screens and business behavior. It changes
how the React interface owns pending work, accepts responses and cleans up
after navigation. There are no backend, database-schema or real MySQL changes
in Stage 6. Previously approved Stage 5 changes remain separate.

## Request ownership

RequestScope is a small transport-independent lifecycle helper, used through
useRequestScope. A named read has an AbortController and only its latest
request may update that screen. Replacement, component unmount or a changed
authentication session invalidates the old result. Its late completion cannot
finish a newer request or hide the newer loading indicator.

Writes use a non-abortable, non-replaceable entry. Repeated submissions are
blocked while the original request is pending. Closing a screen invalidates
its UI result; it does **not** undo an operation already sent to the server.
This lock is local to the mounted component, not global server idempotency.
No automatic retry after a network failure has been added.

Signals use [Axios cancellation](https://axios-http.com/docs/cancellation).
Cleanup and accepting only the current response follow
[React effect lifecycle guidance](https://react.dev/reference/react/useEffect).
React StrictMode setup/cleanup is covered by tests.

## Screen behavior

- Catalogue search is shared between Portfolio registration and PriceTracker.
  Its existing configurable 1000 ms default debounce remains unchanged. Old
  queries cannot resurrect a dropdown after a newer query, selection or clear.
  The later user-approved 2026-10-12 follow-up retains successful suggestions
  while a valid edited query debounces/loads, replacing them only with the
  current response. Clearing/selecting, short input, empty results or a current
  failure clears the list. This is UI continuity, not an additional cache.
- Portfolio filtering/pagination loads only the requested records. It no longer
  reloads unrelated pinned items or totals on every filter/page change.
  Adding a portfolio record still refreshes the Home component and its totals.
- Price histories use a queue with at most four active requests per mounted
  history hook. Completing one history does not duplicate pending siblings.
  Bookmark/list updates retain already loaded histories; removed items are
  canceled and pruned. Failure does not create an endless retry loop.
  A new mount or an explicit history revision permits another load.
- Inventory calculation is cancelable because it is a lookup, not a portfolio
  write. A late response after leaving the page cannot save a browser result.
  Successfully saved results still persist in the existing owned browser session.
- Support Panel detail/tab reads are invalidated when selecting another user
  or leaving a section. Contact-request filters/pagination accept only the
  current result. Independent requests no longer share an unsafe loading boolean.
- Existing job polling (2 seconds while a job is running) and new-ticket polling
  (30 seconds) skip a tick if the same request is still pending. A manual
  maintenance action forces a fresh overview so a pre-action response cannot
  hide the newly started job. Leaving the panel clears intervals and reads.
- Modal submissions remain locked until settlement even after close/reopen.
  Delayed close callbacks and notification timers are cleaned up so an older
  timer cannot close a reopened modal or dismiss a replacement notice.
- Public account forms ignore late UI updates/navigation after unmount.
  Item detail/edit routes remount on a changed item ID. Protected screens remount
  on a changed user ID, preventing reuse of another account's local UI data.

Transport/polling/concurrency constants are in
react/investment-tracker/src/config/uiConfig.js. They are not price-cache TTLs
or access/refresh-token expiration settings.

## Authentication response guards

Backend requests carry an internal session version. Both successful old-session
responses and old-session 401 errors are rejected after that version changes.
An aborted read does not trigger refresh/logout or get retried after a refresh.
Concurrent current-session 401 responses still share one refresh.

Login/logout wait for an already running refresh, and its late token is not
adopted after that transition. Refresh HTTP waiting is bounded to 15 seconds;
it is deliberately shared rather than canceled when a single screen leaves.
Username/password changes cannot replace credentials or sign out a newer
account. Only the exact backend origin receives automatically attached JWTs.

These guards do not replace backend authentication/ownership checks. They are
not proof against all cross-tab cookie races, XSS or account compromise.
The existing server token policies and single authentication retry after a
current-session 401 remain unchanged.

## Verification

- Frontend: **60 tests in 13 suites, all passed**, including 41 new Stage 6 tests.
- Full suite also passed with --detectOpenHandles; no open handles reported.
- Frontend production build: successful.
- Tests use deferred mock replies, fake timers or an in-memory Axios adapter.
  No real API, Steam inventory, price feed, SMTP or MySQL was contacted.
- Scenarios cover stale filters/searches, user/tab changes, polling overlap,
  history queue/StrictMode, duplicate writes, modal reopen/timers, late inventory
  session writes, aborted 401/refresh and account-transition responses.
- Stage 6 does not rerun or replace the recorded Stage 5 backend test results.
- Existing testing-library act and CRA/Babel warnings are unchanged; dependency
  upgrades remain reserved for the tooling stage.

## Remaining acceptance checks

No live browser acceptance test or load benchmark is claimed here. A manual
smoke pass should exercise navigation, rapid filter/search changes, Support
Panel user/tab switches, normal registration/editing/pinning/contact workflows,
sign-in/out, password change and inventory-result persistence.

Client cancellation may stop receiving a response without stopping server work
or an already recorded support audit event. Server-side transaction/ownership
and background-job controls remain necessary. No frontend portfolio cache,
new price observations, visual redesign or provider refresh-policy change is
introduced by Stage 6.
