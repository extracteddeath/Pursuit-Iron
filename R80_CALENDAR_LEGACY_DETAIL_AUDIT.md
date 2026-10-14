# Build 865 / R80 — truthful legacy workout facts in calendar details

Baseline: Build 864 (merged PR #150). Engine 0.65.7, local training dates, program prescriptions and stored workout history remain unchanged.

## Display-only repair

Some older recorded sessions contain completed per-exercise sets, `tonnage` and elapsed `duration` in seconds, but do not have modern `setsDone`, `volume` or `durationMin`. The calendar detail incorrectly rendered `0 sets · 0 lb` even when a valid workout was logged.

- Read canonical `setsDone`, `volume` and `durationMin` first.
- For older entries, count only sets explicitly marked completed, use `tonnage` as a fallback, and convert legacy elapsed seconds to rounded minutes.
- Never interpret `totalSets` (the prescribed set target) as completed work.
- Omit genuinely absent metrics rather than manufacturing zeros. If no summary metadata exists, show “Summary not recorded.”
- Let selected-session name and summary wrap on narrow phones without a separate, floating detail card.

## Regression and release acceptance

The permanent R65 browser route test already seeds an ordinary historical fixture with two completed working sets, 1,320 lb of tonnage and a 2,400-second duration. It now asserts the actual selected-calendar detail shows **2 sets · 1,320 lb · 40m**, in both responsive themes and on the imported local-date scenario.

Only the calendar detail presentation and the existing regression assertions change. No editing, migration, auto-completion, training decision or duplicate history owner is introduced. Release requires all four final-head workflows, then Pages and post-merge checks. Physical Android/TalkBack acceptance remains separate.
