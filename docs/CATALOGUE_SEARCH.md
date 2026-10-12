# Catalogue search

Updated: 2026-10-12. Applies to the shared Portfolio and PriceTracker suggestions.

## Why the previous search produced unrelated results

The previous specification used substring matching, so `ak` could match inside
`Snakebite` or `StatTrak`. The service retrieved at most 40 unordered rows and
ranked those rows in Java afterward. A more relevant item outside that initial
candidate set could never reach the eight displayed suggestions.

## Shared matching and ordering

`CatalogueSearchQuery` validates and normalizes the input. Case, repeated spaces,
pipes, brackets and other separators do not need to match the catalogue's exact
format. Hyphenated words also have a compact representation, making `M4A1-S` /
`m4a1s` and `Five-SeveN` / `fiveseven` searchable without weapon-specific aliases.
Unicode letters and digits are retained. Repeated terms do not add SQL predicates.
The existing 255-character limit and a maximum of 32 distinct terms bound the
query size. Empty or punctuation-only input does not search the whole catalogue.

`CsItemSearchRepositoryImpl` is a Spring Data repository fragment. It queries
stored `cs_items` rows, requiring every typed term to begin a word in the display
name or market name. Partial words work, and the term order need not be identical.
All terms must match the same name; unrelated display/market aliases are not
combined into an artificial match.

The database ranks all matches before limiting the response to eight rows:

1. Exact normalized name.
2. Exact subject name after a leading StatTrak/Souvenir modifier.
3. Names beginning with the typed phrase.
4. Names whose subject begins with the first typed term.
5. A phrase beginning at another word boundary, then other all-term matches.

Within tied groups, complete-word matches take priority. Normal variants precede
StatTrak/Souvenir variants when otherwise tied; name length, normalized name and
ID make the remaining ordering stable. Typing `ak red` can legitimately prefer
Red Laminate to Redline because `red` is a complete word in Red Laminate. Adding
the rest of `redline` narrows the results; the search does not guess the user's
intended finish from an ambiguous prefix.

Examples checked include `ak re`, `ak redline`, `m4a1s hyper`, `five seven heat`,
`spirit holo 2021`, `revolution case` and `music kit`. The rules are generic, not
hardcoded for these examples. Typo correction, abbreviations for wear grades and
translation/transliteration are not added by this change.

## Boundaries and caching

- API routes, DTO fields, eight-result limit, selection/addition behavior and
  stored catalogue identifiers are unchanged.
- No entity/schema migration, catalogue deletion or price-provider request is
  required for search. Existing excluded categories remain excluded from results.
- The existing Caffeine search cache, configured TTL and catalogue-sync
  invalidation remain. Equivalent formatting uses one normalized key. Validation
  runs before a cache lookup, so a previously cached result cannot bypass limits.
- The existing 1000 ms frontend debounce and stable dropdown are unchanged.
  Previously shown suggestions can remain visible during that delay; the latest
  response then replaces them. Cancellation/stale-response guards remain intact.
- The old `CsItemSpecification`, unused `searchByName` query and Java relevance
  scoring were removed. Internal `findBestMatch` metadata lookup is unchanged.

## Verification and remaining manual check

34 new automated cases cover normalization, word matching, generic categories,
ranking before the limit, deterministic ordering, exclusions and real Caffeine
cache reuse/invalidation. Repository cases run the actual native fragment query
and entity mapping against isolated H2 fixtures. Cache cases use real proxies
with mocked database/HTTP dependencies, not a real external catalogue sync.

The same production repository SQL and bound parameters were additionally
executed through JDBC in an explicit **read-only transaction** against local
MySQL 8.0.37, containing 44,635 catalogue rows. Nine searches completed and
returned the expected types of matches. No Spring application startup,
synchronization, migration or database write was performed. These uncached
queries took approximately 450–540 ms locally; this small sequential check is
not a concurrency/load benchmark. Normalized regex matching can scan catalogue
rows. A larger-scale deployment may need indexed normalized fields or a dedicated
search index; no such schema change is silently included here.

Restart the running backend in the IDE to load the new implementation and clear
its old in-memory search entries. Refresh the browser and compare several queries
in Portfolio and PriceTracker without submitting records. That UI check remains
separate from the database/automated checks. No commit, push or merge is performed
by this follow-up.
