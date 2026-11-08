# Pursuit Iron: the next usable milestone

The target is a usable coach and app. A growing count of internal tests is
evidence against specific bugs, not proof that this target has been reached.
The earlier one-set rep-target superiority claim is withdrawn: that restriction
was introduced by the assistant, not requested by the athlete or established by
research. The remaining seven frozen comparisons show parity. New target-RIR
estimates remove the cap but still require individual prediction validation.
Overall superiority and a complete usable Kotlin app are not yet proven.
See [the plain-language results](ENGINE_COMPARISON_SUMMARY.md).

## Finish line for the next testable app

Using the original themes and the athlete's real choices, a person must be
able to complete this loop without engineering IDs or authoring coaching rules:

1. Open the Kotlin app and enter or reopen their saved training setup.
2. Choose a goal, available days, split, gym and session-time preference.
3. Receive a complete feasible program: exercises, sets, reps, goal RIR,
   attainable weights, warmups, rest and an honest duration estimate.
4. Review and save the program, then start the correct day's workout directly.
5. Log actual sets, correct a mistake, minimize/resume and finish the workout.
6. See the finished workout in history and the correct next workout on return.
7. Restart the app and recover both the profile and unfinished/finished work.

Pass this loop in source integration first, then on Android. A collection of
screens that need hand-built grants or manually supplied muscle-dose policies
does not meet this finish line. A source compilation does not mean phone testing
passed. Keep the APK freeze until the agreed full journey is ready to test.

## Work now follows visible outcomes

| Priority | Deliverable | Current evidence / missing work |
| --- | --- | --- |
| 1 | Honest direct engine comparisons | Seven historical parity cases; the one-set rep-quality claim is explicitly withdrawn. New rep estimates require held-out individual validation and fresh source measurement. Program/cycle quality and overall superiority remain unproven. |
| 2 | A reachable Kotlin journey | Debug-only Kotlin Lab connects saved setup, generated/custom program review, Train and editable native History. Returning from setup now opens saved programs; a single owned program reopens directly, while multiple programs require a choice. Android interaction acceptance is pending. |
| 3 | Generate a useful first program | Saved athlete choices and explicitly calibrated, attainable loads feed a versioned provisional first-block policy, complete prescription and native draft save. Original calibration recovers the policy after restart only if regeneration matches the saved program. Broader catalog coverage, program-quality review and the goal/split/gym matrix remain open. Test fixtures are not user programs. |
| 4 | Finish-to-next-workout loop | Exact cycle review/consent, preparation, issuance, logging, corrections and manifest-backed Finish are connected. Reopening a signed generated plan checks the next session automatically; existing prepared/issued work takes precedence. Train checks for unfinished workouts on entry, and verified History links back to the program. A nine-session native-file integration covers week boundaries, authored exceptions, lost Finish acknowledgments and fresh readers. This is not Android interaction proof. |
| 5 | Cold restart and phone acceptance | Explicit saved-profile resume, generated-policy recovery and durable unfinished/finished work have native source paths. Existing web history still needs verified migration; a separate new Kotlin history requires explicit opt-in and a complete empty-native-inventory check. Custom coaching-policy recovery, full Android journey/layout/TalkBack, backup and physical-device acceptance remain open. |

Defer additional narrow load-consent variants unless one blocks this loop or
the direct comparison exposes a real defect. Improve the existing independent progression domains and preserve data owners;
do not build parallel engines or journals. The objective is feasible work at
the intended RIR, informed by individual experience/history, accumulated fatigue
and current-session fatigue, across daily sessions, programs and cycles.

## Current continuation — 2026-11-07

The inherited checkpoint's latest CI failed in history-enrollment compilation:
`requireEmptyNativeInventory` was declared at the public entry point but neither
forwarded to nor declared on the private implementation. The continuation wires
the requirement through both paths and adds a regression that refuses an empty
history enrollment when an unfinished native Composer workout already exists.
No history format or training calculation changes are needed for this repair.

The generated-program source milestone is now exercised by
`NativeKotlinGeneratedProgramJourneyTest`: a four-day Upper/Lower program from
the real starter catalog completes every warmup and working set, corrections,
Finish retries, cold reads and selection of week two. The test first exposed
and now reproduces the previous starter-policy bug: zero ceilings on
indirect-only muscles prohibited ordinary compound lifts. New v2 plans allow
that declared exposure while preserving direct-coverage gaps. V1 saved plans
retain exact replay. Ordinary prepared workouts can now reopen through a fresh
exact-snapshot/consent preflight. See
[the executed program and limits](GENERATED_PROGRAM_JOURNEY_CHECKPOINT.md).

Warmup-aware composition now selects new v3 weeks using the actual per-visit
warmup/rest durations and independently matches the final prescription audit.
The formerly overlong three-day case now fits 40–60 minutes. Saved v1/v2
cycles and untimed native approval bytes remain exact; native tests cold-open
and issue both saved v2 programs and new timed approvals. See
[the executed timing comparison](WARMUP_TIMING_CHECKPOINT.md).

The broader program-quality matrix, individual fatigue accuracy, legacy-history
migration and Android interaction acceptance remain open. A source-tested
generated week is not full product acceptance or proof of coaching superiority.

## What counts as progress in updates

Report the changed user behavior or the executed comparison result first.
Include what can actually be opened, generated or completed, what still blocks
the loop, and the next concrete deliverable. Test counts are supporting evidence.
Do not call Kotlin better overall until the complete comparison requirements
are met; identify a narrow demonstrated improvement as a narrow improvement.
