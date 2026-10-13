# Build 864 / R79 — training calendar accuracy and legibility

Baseline: Build 863, merged PR #149. Engine 0.65.7 and the saved history model are unchanged.

## Correctness and design
- Read date-only legacy/imported workout records (YYYY-MM-DD) as a **local calendar day**, rather than UTC midnight that becomes the previous day in North American time zones. Timestamped sessions keep their existing local-time behavior.
- Use the same local-date interpretation for the training-calendar index and the Sessions consistency summary. Calculate the last 28 calendar dates by advancing local **dates** rather than subtracting a fixed number of milliseconds across daylight-saving transitions.
- Replace repeated single-character S/T weekday labels with Sun/Mon/Tue/Wed/Thu/Fri/Sat, with full weekday names available through the abbreviation title.
- Replace consecutive-*day* streak metrics below the calendar (misleading for planned rest days) with distinct **training days this month** and **workouts this month**. Keep the existing weekly streak summary separate.
- Show the full session and exercise names in selected-date details instead of clipping them to ellipses. Continue to show the logged sets and session evidence.

## Regression and release acceptance
- The existing R65 real-browser suite still checks four window sizes and two themes, seven days per calendar week, day-selection details and collapsed milestones.
- It now checks readable weekday labels and monthly facts, and exercises an isolated date-only record in an America/New_York browser to catch the off-by-one error.
- The training engine, completed-set logging, adaptive prescription, theme tokens, amplifier and Home layout are outside this change.
- Publish only after all four GitHub workflows pass at the final PR SHA; verify Pages and post-merge workflows. Physical Android IME/TalkBack and larger visual/style consolidation still need real-device evidence.

No inferred sessions, no history rewrites, and no new recurring daily-streak pressure.
