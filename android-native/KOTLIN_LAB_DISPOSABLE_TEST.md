# Isolated Kotlin Lab disposable QA candidate

This is a single **development-device** milestone, not a release or
Kotlin production cutover. It builds one signed Android test APK after
re-running the native and Kotlin test gates. AAB generation is deferred until
a Play Store listing is being prepared.

Each run has a distinct installed package
`io.github.extracteddeath.pursuitiron.kotlinlabtrial<RUN_NUMBER>` and
launcher label `Pursuit Iron Lab Trial <RUN_NUMBER>`. The GitHub runner
signs its APK with an ordinary **temporary debug key**. Because the app
identity changes with the run number, this build cannot overwrite prior
Kotlin Lab, production or the other developer's native preview. Neither
does it update prior trial installs or transfer their data. Never record
irreplaceable training history in this disposable build.

The APK is bound to the source commit, exact per-run app
ID, signer fingerprint and SHA-256 receipt. Device acceptance, complete
feature parity, backup/migration and demonstrated coaching superiority
are NOT yet certified. Persistent in-place upgrades still require the
private stable certificate described in `KOTLIN_LAB_SIGNING_SETUP.md`.

## One-time milestone correction

The first workflow push was rejected by GitHub's YAML parser because the
milestone conditional contained an unquoted colon. This marker explicitly
retries only the corrected workflow with the same isolated trial contract.


## Trial 3 — original Amethyst UI baseline

This subsequent disposable milestone packages commit
`9be3c6daa48a6b7821dab33911975fe1a27956ce` and the tested
Amethyst-first native appearance behavior. Verify that a fresh install
opens with Amethyst rather than Lime; open More → Appearance to select one
of the original 20 web palettes, and choose a theme again from Settings.
Confirm the theme changes immediately and persists after a force-stop and
cold restart, including light-theme system bar contrast. A deliberately
selected Lime theme remains available but is NEVER the implicit default.

Like previous trials, this is a new per-run package signed with a disposable
debug certificate. It does **not** update Trial 2 or share local training data,
and its debug AAB is **not** a Play Store release artifact.

## Trial 4 — first-install Amethyst build after theme contract alignment

Trial 3 was refused before packaging because an older onboarding source test
still demanded the web app's original Lime DEFAULT_ID. The UI and palette
were correct; the test was updated to deliberately require the new native
Amethyst DEFAULT_ID while retaining exact original web palette data and
explicitly selected Lime. The Kotlin source workflow now executes BOTH
the original 20-palette parity test and onboarding appearance contract.

This follow-up packages the corrected native UI at commit
`9b4836d68519cb5367eeae311100c30fb86d8ae8` plus this one-off marker.
Verify fresh-install Amethyst, selectable themes, saved preference on reopen,
and stable/production app isolation. The artifact is disposable, with its
own run-number package identity and debug-only certificate.

## Target/Last workout milestone — APK only

The next disposable APK includes the persistent Target/Last reference in both
Kotlin workout paths, original curated exercise names throughout Plan, Train,
History and Coach, and separate warm-up/working-set counts. Amethyst remains
the first-install theme and all original palettes remain selectable.

Verify that switching Target/Last leaves unsubmitted weight/reps/RIR untouched;
Last shows the latest matching program day and exact slot, including corrected,
skipped and failed results. Unknown actual RIR must stay unreported. A first
workout with no matching history shows an empty reference and can still be
logged. Minimize/resume, rest, confirmed Finish and History remain connected.

The workflow now assembles, verifies and uploads **only the test APK**. No AAB
is generated. This is another isolated per-run install with a temporary debug
certificate, not an in-place update of a prior trial. Do not uninstall a prior
trial to install this one; their training data remains separate.

The APK-only packaging guard also runs in the routine source workflow, so
source readiness and the device milestone enforce the same output contract.

## Startup correction after visual emulator review

The emulator screenshots exposed a false equipment failure on fresh setup
and white system-bar backgrounds under dark-theme icons. The next APK treats
an unopened athlete as ordinary setup guidance without reading protected gym
data, and paints the selected theme behind system bars before insetting content.
Verify fresh launch and cold restart, no equipment-failure banner before setup,
and readable status/navigation bars. These changes do not alter the engine
source tree or the recorded load-review comparisons.

## Guided setup and first-program handoff

This milestone replaces normal onboarding ID entry with named controls,
an explicit balanced-training choice, named exclusions and facilities,
device-time-zone selection and optional advanced sections. Changing units
requires confirmation before existing entered weights can be cleared.
Android Back returns to the previous setup step with entries preserved.
After an explicitly confirmed setup is durably read back, the screen opens
the first-program builder. Program approval is surfaced near the next-workout
card and still requires the current full-cycle audit and saved source revision.

Use the real Android UI to validate setup, finite gym inventory, a generated
four-day program, approval, separate-history consent, issued set logging,
Target/Last, recovery, Finish and saved History. These checks do not establish
personalized forecasting superiority or physical-device acceptance.

## Plan and workout UI composition

The next APK places the next-workout action first, followed by a readable
program outline with named exercises and separate working/warm-up counts.
Technical cycle identifiers, policy diagnostics and editing tools sit under
Open full program; incomplete custom drafts still open their required review.
Prepared workouts retain all their exact per-set targets with readable names,
warm-up labels and no null-RIR text. Empty saved-review controls are optional.

After an independently verified Finish, the logger reveals Workout saved,
History and next-workout actions at the top, with recorded sets available on
request. Active-set Complete sits on the right at ordinary font sizes; large
text stacks the fields and gives Complete full width. No prescription,
approval, actual-RIR or journal-write rule changes in this UI pass.

Verify the full Android setup-to-History journey again, including opening and
closing the full-program editor, retaining an unsubmitted rep value across a
1.6 font-scale configuration change, logging a set with that layout, restart
recovery and a visible saved-workout result without manually scrolling up.
Keep Amethyst as default and produce only an APK.
