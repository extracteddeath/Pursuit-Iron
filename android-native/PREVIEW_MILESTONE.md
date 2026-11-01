# Native preview build milestones

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
