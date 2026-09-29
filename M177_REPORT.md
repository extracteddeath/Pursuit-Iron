# M177 editable history merge integrity

App **3.227.0**, build **783**, Pursuit Engine **0.63.2**, store schema **13**.

M164 made completed workout history editable, but the persistence layer still carried the older invariant that a logged session was immutable. History therefore bypassed the shared `updatedAt`/tombstone setter and same-id reconciliation could not reliably distinguish a corrected session from its stale copy. M177 closes that cross-layer gap without replacing the newer store migrations already present in M176.

## What changed

- History mutations now use the same centrally stamped setter as other mutable syncable records.
- Store hydration uses the raw history setter, preventing boot/load from changing edit clocks.
- Schema 13 gives legacy history an `updatedAt` baseline of zero while preserving schema migrations 8–12.
- Merge conflicts prefer per-record `updatedAt`; legacy ties fall back to the newer store snapshot and then a deterministic signature tie-breaker.
- The `perf` cache is rebuilt from the newest retained chronological session for each exercise, preserving corrected evidence without allowing an older corrected workout to masquerade as the latest workout.
- History deletions now produce tombstones; undo/re-edit resurrects only when its edit clock is newer than the deletion.

## Validation

Focused M177 tests cover schema migration, prior-migration preservation, two-way merge ordering, legacy ties, deletion-vs-edit ordering, and chronology-safe perf rebasing. The complete production release-integrity suite also runs before publication. Physical installed-PWA behavior remains a separate device certification step.
