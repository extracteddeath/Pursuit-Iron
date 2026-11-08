# Isolated Kotlin Lab disposable QA candidate

This is a single **development-device** milestone, not a release or
Kotlin production cutover. It builds one signed Android debug APK plus a
non-production debug AAB after re-running the native and Kotlin test gates.

Each run has a distinct installed package
`io.github.extracteddeath.pursuitiron.kotlinlabtrial<RUN_NUMBER>` and
launcher label `Pursuit Iron Lab Trial <RUN_NUMBER>`. The GitHub runner
signs its APK with an ordinary **temporary debug key**. Because the app
identity changes with the run number, this build cannot overwrite prior
Kotlin Lab, production or the other developer's native preview. Neither
does it update prior trial installs or transfer their data. Never record
irreplaceable training history in this disposable build.

The debug AAB is an engineering artifact only, **not a Play Store release
bundle**. Both outputs are bound to the source commit, exact per-run app
ID, signer fingerprint and SHA-256 receipt. Device acceptance, complete
feature parity, backup/migration and demonstrated coaching superiority
are NOT yet certified. Persistent in-place upgrades still require the
private stable certificate described in `KOTLIN_LAB_SIGNING_SETUP.md`.

## One-time milestone correction

The first workflow push was rejected by GitHub's YAML parser because the
milestone conditional contained an unquoted colon. This marker explicitly
retries only the corrected workflow with the same isolated trial contract.
