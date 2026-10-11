# Refactoring sequence

Updated: 2026-10-11. Branch: `security-reliability-refactor`.

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
7. **Structural refactoring** — implemented, awaiting user review. Smaller page
   views/hooks, shared charts, isolated HTTP/domain services, separated price
   responsibilities and removal of proven unused remnants.
8. **Dependencies and tooling** — not started. CRA-to-Vite migration, compatible
   dependency/test-tool updates and updated Node/runtime setup instructions.
9. **Final verification and delivery** — not started. Cross-feature automated and
   manual checks, role-based workflows, GitLab CI and final documentation.

After Stage 6, a separately approved daily-history correction introduced one
observation per item/source/UTC publication day and newest-first history logs.
That correction is retained; it is not undone by Stage 7.

Verification checkpoints are recorded in [REFACTOR_BASELINE.md](REFACTOR_BASELINE.md).
Stage 7 boundaries and results are in [STRUCTURAL_REFACTOR.md](STRUCTURAL_REFACTOR.md).
