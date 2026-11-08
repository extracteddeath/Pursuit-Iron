# Kotlin Lab: next-set advice reaches the actual workout

Date: 2026-11-08. Scope: G3/G5, isolated Kotlin rebuild branch.

## Changed athlete behavior

The generated first program is saved and logged as a lossless custom cycle.
Previously its workout screen never called the existing next-set coach, so
the engine's immediate recovery/load guidance was unreachable in that flow.
Generated and custom workouts now review the next unrecorded main set from
the reopened native journal, using the current confirmed gym and saved coach
preferences. The advice appears beside that set's editable form.

A completed set below the rep floor can offer the next smaller physically
available weight within the review limit. A difficult set that meets the floor
can advise more recovery. An already prescribed lighter backoff is recognized
without an additional reduction. These are the existing `InWorkoutCoachDomain`
rules, shared by both journal types; no second training engine was introduced.

The screen adds readiness and an explicit movement-restriction control. All
load/rep changes remain the athlete's choice in the set fields; advice never
overwrites the issued targets, entered actuals, or any earlier record.

## Ownership and opt-out

- Native ownership reopens the journal, current source and coach settings under
  the existing owner fence. The UI cannot supply a trusted performance history.
- Standard mode permits next-set advice. Progression Only and Manual suppress
  fatigue advice for **both** custom and composer journals; the latter path
  previously failed to read the saved setting. Restrictions remain a boundary.
- Corrupt settings suppress advice without disabling normal workout logging.
- A changed gym/catalog/profile revision or movement variant supplies no
  physical loading authority to an old workout. Logging/resume remains available.
- Stable slot IDs keep repeated exercises separate. Saved corrections replace
  the earlier effective observation; recorded later sets prevent advice for a
  still-open middle set. Results are bound to workout, set and journal revision.
- Supersets, amplifiers, bodyweight and heterogeneous rep schemes are outside
  this ordinary straight-set adapter. It does not guess their recovery effects.

## Executed evidence

`KotlinJournalLiveSetCoachDomainTest` exercises both journal types, independent
slots, corrections, out-of-order records, nullable actual RIR, advanced-set
refusal, fatigue opt-outs and restrictions. The complete local headless engine
suite passes 712 tests.

`NativeKotlinGeneratedProgramJourneyTest` builds the real four-day first
program, prepares/issues it, records warmups and an under-floor working set,
cold-reopens advice, then saves a correction and observes the advice change.
It checks Progression Only and Manual after cold reads, changed source
authority, corrupt preferences, continued logging and immutable targets.
All five generated-program native journey tests and the complete 461-test
native persistence suite pass locally. Android compilation and the full
source-only workflow are also required in CI.

## Limits and next work

This connects existing immediate coaching to the usable workout flow. It does
not fit an individual fatigue model, measure actual rest, predict later-set
rep capacity, or diagnose chronic fatigue. Personalized forecasting still
needs subsequent-performance calibration and independent comparison evidence.
Android interaction, backup/migration and full program/cycle quality remain
open product gates. No APK or production cutover is part of this pass.
