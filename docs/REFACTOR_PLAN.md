# Refactoring sequence

Updated: 2026-10-12. Branch: `security-reliability-refactor`.

The implementation follows separately approved stages. Completion of a stage
does not authorize starting the next one, publishing changes or merging branches.

1. **Preparation and data safety** — completed. Baseline checks, private backup
   helper and migration safeguards; Flyway remains disabled by default.
2. **Email codes and authentication** — completed. Verification-code safeguards,
   abuse limits and account/session security.
3. **API validation and ownership** — completed. Request validation, item-type
   boundaries, user ownership and transaction consistency.
4. **External services, caches and price refreshes** — completed. Bounded HTTP,
   atomic cache publication, provider failures and background-job guards.
5. **Inventory calculator** — completed. Bounded public-inventory loading,
   quantities, unavailable-price semantics and canonical-profile caching.
6. **Frontend correctness and request lifecycle** — completed. Cancellation,
   stale-response/session guards, bounded history requests and safe polling.
7. **Structural refactoring** — implemented, committed and pushed, including
   feature-folder/naming and backend/frontend DRY follow-ups. Smaller page
   views/hooks, shared components, isolated HTTP/domain APIs, separated price
   responsibilities and removal of proven unused remnants. Merge remains
   separately controlled.
8. **Dependencies and tooling** — implemented and verified with Node.js 24.19.0;
   commit/push approved on 2026-10-12. Vite/Vitest migration, compatible
   dependency updates, ESLint checks and updated runtime/setup instructions.
   The separately approved system update to Node.js 24.20.0 and npm 11.19.0
   is complete; manual workflow review and merge remain separate steps.
9. **Final verification and delivery** — implemented; local automated checks
   complete. Integrated API/role/session workflows, frontend route/profile
   access, production HTTP smoke, GitLab CI jobs and acceptance documentation.
   User browser acceptance and the first real GitLab pipeline remain pending;
   commit/push approved on 2026-10-12; merge still requires separate approval.

After Stage 6, a separately approved daily-history correction introduced one
observation per item/source/UTC publication day and newest-first history logs.
That correction is retained; it is not undone by Stage 7.

Verification checkpoints are recorded in [REFACTOR_BASELINE.md](REFACTOR_BASELINE.md).
Stage 7 boundaries and results are in [STRUCTURAL_REFACTOR.md](STRUCTURAL_REFACTOR.md).
The current source tree and naming guide are in [CODEBASE_STRUCTURE.md](CODEBASE_STRUCTURE.md).
The additional backend duplication review is in [BACKEND_REUSE.md](BACKEND_REUSE.md).
Stage 8 tooling, verification and boundaries are in [FRONTEND_TOOLING.md](FRONTEND_TOOLING.md).
Stage 9 results and delivery boundaries are in [FINAL_VERIFICATION.md](FINAL_VERIFICATION.md).
The user browser checklist is in [MANUAL_VERIFICATION.md](MANUAL_VERIFICATION.md).

On 2026-10-12 the user approved committing and pushing all pending changes to
`security-reliability-refactor`, including Stage 8/9, the browser date-picker fix,
stable dropdown and generic catalogue-search follow-ups. This does not authorize
merging or claim that the first real GitLab pipeline has passed.
