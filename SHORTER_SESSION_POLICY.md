# One-session time limits (Build 898)

The original web app's Home → Preview → Time available offers **Full / 45 / 30 / 20 minutes**. Program-day starts also open this preview. The generator's saved program is never rewritten by this choice. `planShorterSession` is exported by the canonical engine API and included in the standalone engine and offline shell.

## Cutting policy

1. Keep the day’s key lift and a complementary compound (when present). Keep at least two of their originally prescribed working sets; never add sets to a smaller prescription.
2. Preserve every set of primary/secondary strength work, per-set target protocols and executable percentage schemes, including tiers and AMRAP targets. They can make a short limit infeasible.
3. Remove optional final-set amplifiers first, then extra sets above two, starting with accessories, followed by supporting compounds and then key hypertrophy work.
4. If necessary, omit optional exercises. Duplicate muscle work goes before unique work at the same movement priority; compound work and focus muscles have higher priority. Keep original exercise order and slot identities.
5. Never shorten rest, omit retained lifts’ warmups, change load/reps/RIR, add supersets, or use harder techniques to compensate. Technique protocols retained in a session keep their internal rest and set structure.
6. If protected work exceeds the limit, show its estimated duration and disable that shortened start. The lifter can choose a longer limit or Full. This is an estimate, not a countdown enforcing a deadline.

## Timing and persistence

The conservative clock includes a five-minute preparation allowance, per-lift warmup/setup, working-set execution, rest, full technique extensions (up to five myo minis) and slower observed personal pace. The estimate gives no speculative superset discount. Rest pricing uses the greater of the canonical cell’s rest and the workout’s configured timer duration. No faster-than-model personal pace is used to squeeze in more work.

The plan lives on the session day and in the live recovery snapshot, with program/day/week/exercise identities. Omitted execution rows are filtered without reindexing slots. Resume restores the same cuts and all logged/manual rows. The saved program and future weeks remain unchanged. Manually adding/swapping sets during a workout is still possible; the original time estimate then becomes advisory.

History stores the reduced prescription, completion denominator and time-limit plan alongside actual performance. Omitted exercises are excluded from the history adapter’s planned session. A changed time-limited exposure is marked `prescriptionEdited`, making it neutral for earned progression/stall/failure decisions while retaining its actual logged volume, PRs and exercise history. No automatic catch-up volume is scheduled. Choosing a limit that already fits the complete session does not suppress ordinary progression.

## Validation

`verification/shorter-session-test.mjs` exercises generated/custom programs, three goals/splits, early/late/deload weeks, budgets, deterministic cuts, monotonic work, immutable source plans, protected protocols, warmups, reduced-dose logging and canonical replay. The browser contract covers 320/430/900px preview, touch targets, over-budget blocking, real start/log/minimize/reload/finish and saved-program isolation. Both are owned by the permanent CI registry.
