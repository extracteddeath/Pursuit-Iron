# M220 — day-specific double progression

Pursuit Iron 4.0.0 build 810 · Pursuit Engine 0.64.10.

## Reported defect and reproduction

The supplied screenshots show the same Seated Calf Raise in two occurrences of a custom program:

| Occurrence | Prior session | Working sets |
| --- | --- | --- |
| Lower | September 23 | 205 lb × 20, 16, 16, 12, 12 |
| Legs | September 28 | 210 lb × 20, 17, 15 |

Lower's Last column used its own five-set session, but the custom-program suggestion selected the latest exercise session anywhere in the program. It therefore asked for 210 lb and described the three-set Legs session. The new regression test failed with `210 !== 205` before the fix.

## Change

Custom suggestions and Last values now select one shared raw history record: newest session of the same program/day first, another day in that program only when this day has no evidence, then exercise history from another program only when this program has none. Existing per-day evidence cannot be replaced by a newer unrelated occurrence.

Lower now holds at 205 lb; Legs retains its own 210 lb reference. A three-set session cannot satisfy Lower's five-set progression requirement. All five prescribed sets at the rep ceiling still earn the next load step. Existing rep-floor safety and loadable-equipment rounding remain in place.

The generated-program bridge already evaluated day-specific workouts, but selected prior-session details and changed-week reference globally within the program. Those references now prefer that same day too.

Resuming a custom workout refreshes only pending rows explicitly owned by the automatic prescription, whose load still matches their saved target. Completed, manual, directly edited, added, warm-up and technique rows are preserved. Reconciliation runs on restoration, not on each keystroke. Saved history and program doses are not rewritten.

## Validation

- Full release integrity passed: current hashes, 52 authored JS parse checks, 74 offline entries and existing regression suites.
- All 27 current engine-contract gates passed: generation 8, adaptation 16, quality 3.
- Nine production browser gates passed: update/restart, volume repair, workout prescriptions/variants, executable prescription synchronization, cycle duration, Android recovery, M218 phone polish, M219 UI integration, and M220 day progression.
- M220 semantic checks cover the exact screenshot history, separate day loads, insufficient/all-prescribed-set qualification, same-program and cross-program initialization, input order, immutable history, shared suggestion/Last reference, generated bridge agreement and protected resume values.
- M220 browser check confirms a fresh five-set Lower workout starts at 205 lb and uses its own Last values. On resume, untouched rows change from 210 to 205 while a completed 210 lb set and a manually entered 212.5 lb set survive.
- The phone dismissal harness explicitly centers its target button before clicking, preventing longer release notes from placing it under the fixed navigation.
- Phone screenshot reviewed. Browser verification used headless Chromium; physical-device certification is not asserted.

M219's reviewed UI integration remains included. Only the workout-history adapter changed among the 58 non-App runtime/vendor files; the engine's evaluator and programming policy remain unchanged.
