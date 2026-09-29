# M163 prescription-integrity hotfix

App **3.213.0**, build **769**, Pursuit Engine **0.62.5**. Based on M162.

## Findings

Two separate issues were investigated after the reported workout/progression bug.

1. **`2,2` in Program View was a real rendering/shape bug.** Pursuit Next internally represents an effort target as a pair such as `[2,2]`. Most generated shell snapshots serialize that as `"2"`, but an older/imported/runtime-shaped cell can still carry the pair. The shell's effort formatter stringified the array, producing `2,2 RIR`.
2. **Workout completion does not directly rewrite canonical weekly set prescriptions.** The M162 diff also did not change volume allocation or program topology. A separate cursor defect could, however, make a finite program *appear* to lose sets: week advancement used the raw number of history rows divided by days/week. Repeated or duplicate rows could therefore jump the visible/current prescription into a later week or deload, where set counts may intentionally be lower.

## Fix

- RIR arrays and legacy comma-shaped ranges normalize before display; `[2,2]` is `2 RIR`, `[1,2]` is `1–2 RIR`.
- Array-shaped rep ranges normalize at the Next→shell boundary; `[5,8]` becomes `5-8`.
- During the first pass through a finite block, modern history with valid `weekIndex` + `dayId` advances only after **distinct planned days** for the week are completed. Repeating the same day does not advance the week.
- Legacy/mixed history keeps the previous conservative count fallback rather than guessing missing week/day identity.
- The M162 rep-floor load correction is unchanged.

## Scope / limitation

This fixes a proven display defect and a proven false-week-advance defect. It does **not** claim that the user's local program data actually lost sets because this release artifact cannot read the phone's IndexedDB. If set totals are still lower after the cursor fix, an exported app backup is needed to compare the exact saved week prescriptions and determine whether the lower volume is an intentional week taper, an older program edit/regeneration, or stored-data corruption.
