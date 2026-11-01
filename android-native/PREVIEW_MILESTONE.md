# Native preview build milestones

**APK BUILD FREEZE — active.** Historical milestones below are retained as
development records, not triggers. Neither editing this file nor manually
dispatching the workflow can create an APK until the same native revision
passes real onboarding -> program generation -> personalized workout ->
transactional logging -> interruption/resume -> completed session ->
correct history/progression acceptance. See
`android-native/verification/no-apk-before-end-to-end-test.mjs`.
The preview and device workflows are explicitly disabled, and routine
sandbox CI compiles tests without assembling instrumentation APKs.

Edit this file **only when the native preview has a significant user-facing change or a toolchain validation milestone**. Routine engine-contract and Compose UI commits should not create a new APK.

## M1 — SDK 37 / Material 3 Expressive toolchain validation

- Upgrade AGP 9.1.1, Gradle 9.3.1, SDK 37 and Kotlin Compose plugin 2.4.21.
- Verify compiled Material 3 1.5.0-beta02 and Compose BOM 2026.09.00.
- Native preview remains a no-data demo; no real set logging or history writes.
- APK success is a toolchain gate, **not** a training-engine or device-acceptance certificate.

## M2 — Material 3 Expressive native workout-outline layout

- The native **Plan** screen has a new, shared Material 3 exercise-outline
  component with an interactive, clearly labeled layout example.
- Example rows demonstrate sets, rep/RIR ranges, rest and readable hierarchy,
  without pretending to be the user's actual training program.
- The `OutlinePresentationPolicy` accepts only bounded, correctly sourced,
  `readOnly` AND `syntheticOnly` engine results; the installable preview
  deliberately passes `outline = null`.
- Kotlin presentation tests, canonical JavaScript bundle verification,
  Android UI compilation and the previous real-emulator synthetic outline
  parity milestone have succeeded before scheduling this build.
- The normal source-change workflow compiles Compose **without** an APK; this
  explicit milestone authorizes one installable new native UI preview.

**No engine-runtime dependency in the app**, no live user data, no set
completion, no program mutation and no change to the web app or `main`.

## M3 — explicit canonical read-only backup schema check in native Plan

This is a significant, user-visible **debug preview** milestone. The earlier
workflow-triggered build from commit `6605dc6` failed because a public
Compose signature exposed an internal-only schema verifier type. The code
change at `aef52c6` fixes that Kotlin visibility mismatch; this milestone
build intentionally targets the corrected branch.

- The same explicit file picker now distinguishes a plain SHA-256 fingerprint
  from a *user-initiated* small-source canonical schema check.
- Files up to 50 MiB can be fingerprinted. Only whole copies at or under
  128 KiB, and subject to the canonical protocol character/byte limits,
  are eligible for read-only parsing. Nothing is imported or saved.
- The preview build process exports the immutable canonical JS dependency
  closure, verifies its checksum and standalone parity, bundles a read-only
  IIFE, invokes its actual schema API, and embeds the matching manifest and
  code as **debug-only APK assets**.
- Android independently verifies the packaged SHA-256 and disabled
  workout-certification flags before constructing the sandbox worker.
- The verifier sends no writes; only canonical collection counts are shown.
  All training screens remain preview-only and the JS engine remains the
  sole owner of stored-data schema decisions.

No native workout prescriptions, history restoration, program adoption,
live data synchronization or production web changes. Future normal source
commits should not trigger another preview APK.
