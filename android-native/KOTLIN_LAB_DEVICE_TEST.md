# Kotlin Lab — isolated development device-test candidate

This one-time **development** APK is for a real phone test of the isolated
`architecture/kotlin-engine-rebuild` branch. It is **not** a release,
production cutover, or a declaration that the new coaching engine is
scientifically superior to the JavaScript version.

## What has actually passed before this marker

- Generated four-day program from saved athlete setup, gym, explicit starting
  loads, and provisional versioned policy.
- Native draft audit/signoff, prepared and issued workouts, logged warmups and
  working sets, durable Finish, saved corrections, cold history replay and
  selection of the next programmed training day.
- Signed ordinary program V2 rep coaching using actual owner-verified native
  history. Versioned V1 replay remains unchanged.
- Future-week rep proposal via reviewed week-specific exercise edit and
  renewed signed cycle. Completed old-revision sessions keep their calendar
  positions; old unfinished work requires explicit recovery.
- Native Compose Kotlin Lab launcher exists in `src/debug` and is separate
  from the legacy MainActivity, which remains untouched.

The milestone job re-executes engine and native-storage tests, Android
source-journey guards and an Android debug APK assembly on **this exact SHA**
before publishing a short-lived artifact.

## Isolation and safe install

The candidate uses application ID
`io.github.extracteddeath.pursuitiron.kotlinlab`, not the other
developer's `.preview` application ID and not the Play Store package. It
uses its own Android app-private storage. The previous native preview and
web app are not replaced.

## Android testing

Open **Pursuit Iron Kotlin Lab**, then check:
1. Setup/gym/first generated program and saved coach review.
2. Reopen the application and resume the saved program.
3. Prepare, issue, log warmups and working sets, and finish a workout.
4. Leave or restart during logging; return to the unfinished workout.
5. View finished history, correct a set, verify next workout.
6. Review next-session V2 reps, compare a future-week edit and reapprove it.
7. Check smaller phone layouts, dark/light theme, accessibility, RIR input,
   equipment increments and visible failure messages.

Report functional and layout defects before treating this preview as usable.
No migration of existing web history or automatic switching to the new
engine is performed. Do not enter irreplaceable training history in a test
build until full backup/recovery acceptance.

## Kotlin Lab device acceptance candidate 2 — improved training interaction

This new one-time build tests **UI and flow improvements**, not a changed
training algorithm or a production release. It keeps the same isolated
`.kotlinlab` Android package and a larger Gradle-provided build number, so
the first Kotlin Lab test APK can be upgraded in place without replacing the
other developer's preview.

- Debug Kotlin Lab is the ONLY launcher activity; the original MainActivity
  launcher remains available to the non-debug/release variant.
- Plan, Train, History, Coach have persistent Material 3 navigation, with
  setup, gym, first-program builder, future weeks and settings in More.
- Once a custom workout is open, the bottom tab strip is hidden. **Minimize**
  leaves its native durable journal intact and the Train area can resume it.
- Progress is counted from actual committed MAIN visits. The first outstanding
  set, including a required amplifier follow-up, is the expanded editor;
  other unrecorded sets have an explicit Log action.
- The prescribed weight and floor reps are editable *starting values* in the
  logging form. They are not claims about actual performance and nothing is
  recorded until the athlete taps Complete. RIR stays empty/unreported unless
  explicitly entered.
- Native workout history, fresh-owner validation, correction and Finish remain
  the same durable sources.

**Device checks for this candidate:**
1. Confirm there is one **Pursuit Iron Kotlin Lab** launcher entry.
2. Verify all four navigation destinations and the More menu, in light/dark
   colors and with Android large font.
3. Start a generated workout and confirm only the next set editor expands;
   the weight and target rep floor are prefilled but editable.
4. Log a set with unknown RIR, and verify the saved history reports
   unreported RIR; log another with explicit RIR and confirm the distinction.
5. Minimize mid-workout, restart the app and resume the exact journal.
6. Check amplifier follow-ups, skip, corrections, Finish and next-day
   continuation. Verify no historical workout was overwritten.
7. Observe layout at small width and with the keyboard open; log issues
   before using this as the regular workout app.

These checks are for the human device tester. Passing CI does not claim
that this physical-device acceptance has happened.
