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
