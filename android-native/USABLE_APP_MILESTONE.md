# Pursuit Iron: the next usable milestone

The target is a usable coach and app. A growing count of internal tests is
evidence against specific bugs, not proof that this target has been reached.
The latest baseline has five direct comparison scenarios where both engines
pass. Overall superiority and a complete usable Kotlin app are not yet proven.

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
| 1 | Honest direct engine comparisons | Five existing cases pass on both engines. Expand into rep targets and actual machine/bar stock; record raw results and explain differences. |
| 2 | A reachable Kotlin journey | Debug-only Kotlin Lab launcher, setup-to-program action and real factual History screen added. Main app ownership is unchanged. Android/device acceptance is pending. |
| 3 | Generate a useful first program | Headless generator exists. The product still needs versioned starting coaching policies, sufficient reviewed exercise coverage and a native review/save flow from the saved setup. Do not use test fixtures as user programs. |
| 4 | Finish-to-next-workout loop | Native logging and history transactions exist. Connect automatic day selection, next-session approval/issuance and the History editor through the actual app journey. |
| 5 | Cold restart and phone acceptance | Native storage has recovery tests. Saved-profile resume, full journey interaction, layout, TalkBack, backup and physical-device verification still need acceptance. |

Defer additional narrow load-consent variants unless one blocks this loop or
the direct comparison exposes a real defect. Keep the existing independent
progression rules and data owners; do not build parallel engines or journals.

## What counts as progress in updates

Report the changed user behavior or the executed comparison result first.
Include what can actually be opened, generated or completed, what still blocks
the loop, and the next concrete deliverable. Test counts are supporting evidence.
Do not call Kotlin better overall until the complete comparison requirements
are met; identify a narrow demonstrated improvement as a narrow improvement.
