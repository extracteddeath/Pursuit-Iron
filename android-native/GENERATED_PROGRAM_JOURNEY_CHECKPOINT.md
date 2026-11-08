# Generated-program training loop — 2026-11-07

## Concrete outcome

The Kotlin first-program generator can now produce and save a four-day
Upper/Lower program from the shipped starter catalog, ordinary setup and
explicit starting-load calibrations. Native file integration completes all
four sessions, including warmups, real working sets, active corrections,
lost-acknowledgment retries, saved-history correction and cold selection of
the first session in week two. This closes the previously documented small
custom-fixture-only evidence gap. It is source/JVM evidence, not phone acceptance.

The old starter policy assigned zero MAV/MRV to muscles without a selected
direct exercise. That prohibited compound lifts whose declared indirect credits
included those muscles: the four-day reproducer emitted zero chest and quad
work and refused the week. Version 2 keeps the direct-coverage gap explicit,
sets no direct minimum for indirect-only muscles, and permits their provisional
reference target/ceiling. Unexposed muscles retain zero bounds. The same input
now passes full-cycle review with bench, squat, row and hinge work present.

These reference bounds remain planning assumptions. They are not measured
personal MEV, MAV or MRV and do not prove an optimal advanced program.

## Reproducible example

This is test data, not the athlete's real strength or an approved training plan:
advanced hypertrophy, four days (Monday/Tuesday/Thursday/Friday), Upper/Lower,
40–60 minutes, 15 minutes of general preparation/transitions, no hard time cap,
no supersets, no direct triceps on lower days, and one barbell exercise per day.
The confirmed gym has a rack/flat bench, a 45-lb bar with finite plate pairs,
and 5–50-lb dumbbells in 5-lb increments. Every working load is separately
calibrated in the fixture; inventory does not establish personal ability.

| Day | Exercises and working sets | Warmup sets | Working sets | Estimated minutes |
| --- | --- | --- | --- | --- |
| Monday | Back squat 4; DB RDL 4; crunch 4; DB calf raise 4 | 2 | 16 | 60 |
| Tuesday | Bench 4; one-arm DB row 4; DB curl 2; lateral raise 4; overhead triceps 2 | 2 | 16 | 60 |
| Thursday | Back squat 4; DB RDL 4; crunch 4; DB calf raise 4 | 2 | 16 | 60 |
| Friday | Bench 4; one-arm DB row 4; DB curl 2; lateral raise 4; overhead triceps 2 | 2 | 16 | 60 |

The full week contains 64 working sets and eight warmups. Warmups receive zero
working-dose credit. Lats, front/rear delts, brachialis, wrist flexors/extensors
and glutes still lack a selected **direct** movement; indirect credit is not
advertised as complete direct coverage. This example is not an all-muscle or
all-gym quality certificate. Estimated minutes are arithmetic projections,
not measured workout duration.

## Existing owners and invariants

- **G1 generation:** `CoachFirstProgramStartDomain` still delegates selection,
  composition, equipment checks and final set/time audit to existing owners.
  Version 2 includes a lighter rehearsal set for compound roles when confirmed
  stock provides a positive load at or below 80% of the calibrated working
  weight. Hypertrophy uses five reps; strength/peak uses three. Time is counted
  separately; no suitable load means no fabricated warmup weight.
- **G4 saved identity:** the existing immutable `NativeFirstProgramSeed` retains
  the actual selected policy version. Cold recovery supports v1 and v2 and
  still requires exact regeneration of the saved cycle. No storage format changed.
- **G5 recovery:** `reopenPreparedWorkout` now handles ordinary preparations.
  Its fresh native preflight must match the complete saved snapshot and consent
  revision/fingerprint; a second disk read rejects replacement during recovery.
  The ordinary Issue button uses the same exact comparison. Actual issuance
  independently rechecks its existing native authority.
- **History:** existing preparation, journal, shared manifest and correction
  owners handle every write. All four sessions finish exactly once after
  retries; a correction to the oldest completion preserves newer chronology,
  immutable original targets and the week-two cursor. Missing actual RIR stays null.

The relevant regression families are L14–L16 (dose/time/warmups), L17 and L23
(history/restart). Their broad release gates remain open.

## Warmup policy provenance and limits

[Viveiros et al.](https://pubmed.ncbi.nlm.nih.gov/39593476/) compared three
specific warmup protocols in 15 trained men and found greater subsequent
session volume with the lower-repetition, higher-load protocol. This informs
a low-volume rehearsal starting point; it does not validate the app's exact
rounding, three-rep strength variant, timing assumptions or every exercise.
[Ribeiro et al.](https://doi.org/10.6063/motricidade.21143) found mixed results
across resistance-training warmup studies. The v2 template is explicitly a
provisional implementation, not a universal optimum or injury-prevention claim.

The final full-session audit can reject a once-feasible composition after
warmup time is added. A regression keeps that refusal visible. The composer
does not yet optimize the whole search with these per-visit warmups included;
it must be improved rather than hiding time or padding working volume.

## Verification

`CoachFirstProgramStartDomainTest` checks indirect-only versus direct dose,
exact lighter stock, zero warmup working credit, duration refusal and v1 replay.
`NativeKotlinGeneratedProgramJourneyTest` reproduces the old failure on the
same real catalog and executes the full four-day native workflow above.

The source-only validation command is:

```sh
gradle :kotlin-engine:testDebugUnitTest :native-coach-store:testDebugUnitTest \
  :app:compileDebugKotlin :app:processDebugMainManifest --no-daemon
```

Local results: 688 engine tests and 444 native storage tests, zero failures or
skips. The independent Node comparison-oracle suite passes 17 tests; the
offline isolation, feature-scope and superiority-validator self-tests pass.
Android Kotlin compilation and the main-manifest source check pass on this
same revision. No APK task ran.

A separate executable comparison against the pre-change engine verifies that
v1's full review and converted saved cycle remain byte-identical as text.
The SHA-256 of those combined deterministic outputs is
`9728ef1ce14ccb9ea66c4c2f3bc9431f811747b98761b59661f8e75ca8efe134`.
This is compatibility evidence, not a JS/Kotlin coaching superiority measurement.

## Remaining usable-app work

Complete the Kotlin Lab journey on Android, including large text, keyboard,
rotation/process death and the ordinary prepared-recovery button. Broaden the
goal/split/experience/gym matrix, make composition aware of full warmup timing,
improve catalog and direct-muscle coverage, and validate individual fatigue
predictions against held-out training. Legacy-history migration, backup,
custom-policy recovery and the remaining release features are still open.
No APK, production cutover or overall-superiority certificate is part of this pass.
