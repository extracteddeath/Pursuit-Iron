# M166 set schedule & history volume integrity

App **3.216.0**, build **772**, Pursuit Engine **0.62.5**.

## Fixed

- Custom/user-authored week set schedules are resolved by the selected week before entering shared UI/runtime consumers. A saved `5 / 3 / 3 / 4` schedule remains `5 / 3 / 3 / 4`; it is no longer treated as a corrupt generated set array and collapsed to the fallback.
- Generated Pursuit Next prescriptions still use the M165 canonical set-count boundary and immutable-engine fallback, so the Home/Program multiplied-set bug remains fixed everywhere that consumes `computeCell` / `getNextShellCell`.
- History tonnage is now ledger-first: real working sets are summed as `weight × reps`; cached `h.volume` is only used for old entries that genuinely lack per-set history. This repairs previously saved incorrect aggregates without rewriting the user's history.
- Sub/drop/myo extension rows are not counted as separate working-set tonnage. Weekly recap converts each session to the displayed unit before adding/comparing totals.
- Edited history now caches the same working-set-only tonnage definition used by readers.

## Regression coverage

The release suite retains progression-safety, custom 10–15 double progression, history editing, engine authority, full module import, Programs interactions and set-display integrity tests. M166 adds an executable history-volume test for stale cached totals, unit conversion, sub-set exclusion and mixed-unit weekly recap.
