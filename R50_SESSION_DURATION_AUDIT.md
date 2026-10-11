# R50 — One user-facing session ETA across Home, Plan, Preview and Program

## Real defect
The October 11 Android screenshot showed an Upper session as **~91 min on Home** but **80 min in Plan**. The app used `estimateMinutesFor` (transitions plus median historical pace) on Home but `estimateMinutes` (the generation budget) in Plan and Preview. A week such as **6h35m** could also use the uncalibrated budget instead of the sum of days shown to the athlete.

## Repair
- Keep the existing canonical physics, `daySeconds`, warm-up/rest/setup and the `paceFactor` four-session minimum. No new magic minutes or altered training dose.
- In `planOverview`, compute the existing pace exactly once from `history` and `saved`. The Plan day, Up Next and week-total readouts are all sums of `estimateMinutesFor` for that week's actual exercises and prescription.
- The Plan week-picker now reads `plan.weeks[].minutes` instead of taking a separate uncalibrated `plannedWeek().mins` number. Planned sets are untouched.
- Preview and Program day cards use that same user-facing estimate, with the same history calibration supplied at the root. Home was already correct.
- Preserve `estimateMinutes` for generator hard budgets, plan-target verdicts, session historical `estMin`, `plannedWeek`'s existing default behavior and all persisted history. This is a presentation consistency fix, **not** a training engine update.

## Acceptance
The M219 executable integration contract now calibrates four comparable completed sessions at **78/60 = 1.3×**, asserts Plan Up Next/day/week equality with the existing user-facing estimator and verifies that the raw budget remains distinct. Existing phone/browser, workout logger, program duration, release integrity, PWA/offline and engine suites still gate publication.

## Caveats / follow-up
Actual elapsed workout time can differ from an estimate. Program target/fit badges deliberately retain the prescribed schedule budget rather than treating a slower personal pace as a faulty generated program. After release inspect one saved user's Home → Plan → Preview → Program path and the first incomplete and completed working set on a real Android phone. CSS specificity consolidation remains the next isolated design-system task; do not mix it into this ETA correction.
