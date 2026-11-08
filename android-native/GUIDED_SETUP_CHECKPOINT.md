# Guided setup and first-program handoff — 2026-11-08

## Shipped test APK

Trial 9 is built from `3949ab1137de1880d4b33e31cd1e4e76711b70ad`.
Source/JVM/native-store/Android compile checks passed in
[run 42511496560](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/42511496560).
The APK-only build passed in
[run 42511496443](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/42511496443).
No AAB was produced. Amethyst remains the first-install default and all
20 original palettes remain available.

- Package: `io.github.extracteddeath.pursuitiron.kotlinlabtrial9`
- Version code: `9`
- APK SHA-256: `ad9b5219f2d768ad50430aa00767aad5a2fb51c5861db0438700fc8cfd2c1dff`
- Signing: disposable CI debug certificate; this is a separate installation.
  Earlier trial data remains in its earlier app. Stable update-safe signing
  and verified migration remain open.

## Concrete changes

Normal setup uses named split, time, schedule, muscle-priority, exercise and
facility choices. Internal identity fields and optional raw IDs are under
advanced controls. Balanced priorities and allowing all eligible exercises
are explicit selections; training choices are not silently inferred.

Changing units after entering weights requires a choice to keep the current
weights/units or change units and clear the entered inventory. Confirmed,
durably verified setup opens the first-program builder directly. Plan places
the program-approval action beside the next-workout control, while retaining
the existing source revision, full-cycle audit and explicit consent checks.

## Actual Android UI evidence

The API 35 emulator uses the exact signed APK above. The driver enters the
same finite-gym, advanced four-day Upper/Lower profile described in
`GENERATED_PROGRAM_JOURNEY_CHECKPOINT.md`, through visible Android controls.
It never seeds a private database or substitutes hidden host calls.

[Run 42515915160](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/42515915160)
**passed** using driver commit `9398cd20696fdc278bc5f5433bbb876082bd87cf`.
The captured result, event trace, accessibility trees and actual screenshots
were downloaded and inspected after the run. The diagnostic ZIP is artifact
`13631019780`, SHA-256
`843e7db69d77664da293dc5f58c06d2a5e64cb9bd0eea50489d9246fbc032422`.

| Check | Observed result |
| --- | --- |
| Setup and unit cancellation | Exact dumbbell stock retained; reviewed setup saved and builder opened |
| First program | Four-day generated draft saved, fully reviewed and explicitly approved |
| History ownership | Separate native history explicitly enrolled through the consent control |
| Target/Last | Unsubmitted six-rep entry retained through both display changes |
| Native writes and process restart | `2 of 18 sets recorded` before and after recovery; the saved six reps at 105 lb also remained visible |
| Finish | 18 explicit Complete actions; Session 1 saved with `16 of 16 working sets completed` plus two warmups |
| Missing actual RIR | History reports `0 of 16` completed working sets with actual RIR; no actual RIR was invented |
| Next program day | `Week 1 · Tuesday · Upper` selected after the Monday Lower completion |

Earlier failed runner attempts were corrected for scrolling and the actual
reopened-program navigation. No app or engine code changed after the signed
APK build to obtain this result.

Captured fresh and reviewed setup screens use the exact Amethyst background
`#0C0913` for both system bars, with readable icons and no false equipment
failure banner.

The driver followed the existing Resume route, verified exactly two committed
sets after restart, completed the remaining synthetic visits, committed
Finish and opened saved History. It cleared 17 active rests through the real
reset control between synthetic records; the last completed visit has no
post-workout rest. This is not a simulation of physical exercise or recovery.

## What this does not establish

This pass does not certify physical Android devices, large text, screen
readers, foldables, backup, legacy-history migration, exhaustive feature
parity, optimal programming, or more accurate physiological predictions.
The independent engine source is unchanged in this UI pass. The current
five matched load-review pairs remain parity evidence; overall superiority
remains `NOT_PROVEN_BETTER` with 5 of 49 required current pairs.

Next work should address remaining phone UX/acceptance and concrete engine
quality gaps: full warmup-aware composition, broader direct-muscle/catalog
coverage, wider goal/split/gym testing and held-out personal training results.
The actual Plan screen still exposes technical cycle/policy identifiers and
dense audit prose, and the Finish-to-History link requires returning to the
workout summary. Those are concrete UI follow-ups; this is not acceptance
of the final premium layout.
The existing web/production app and the other native development branches
remain outside this test milestone.
