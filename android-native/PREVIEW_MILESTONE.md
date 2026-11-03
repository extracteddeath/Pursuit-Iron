# Native preview build milestones

**APK BUILD FREEZE — active for routine changes.** Historical milestones below
are development records, not recurring build triggers. M5 authorizes exactly
one named, source-checked debug device-testing candidate: only a push changing
`android-native/M5_DEVICE_TEST_APK.md` with an explicit M5 commit prefix can
run preview APK assembly. Manual dispatches, normal Android/UI commits, and
the separate device-proof workflow stay disabled. CI must repeat the actual
canonical training-journey source checks and Kotlin domain tests before
assembling that candidate. The APK will *enable* on-device interruption,
focus, large text and end-to-end acceptance; it is not a release-ready app or
a corrected training engine. See
`android-native/verification/no-apk-before-end-to-end-test.mjs`.
Routine sandbox CI compiles tests without assembling instrumentation APKs.

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

## M4 — historical onboarding-to-Train proof and the usability re-freeze

The user authorized one installable debug test once onboarding, canonical
program save and real workout start were wired. GitHub Actions successfully
built that APK from `9c4df320`. Initial device feedback confirmed the
native UI is still a technical preview and not a usable release.

**APK FREEZE — active again.** The next build requires a coherent real
onboarding -> workout logging -> finish -> history/resume journey, plus
material improvement to actual native UX. Ordinary source checks do not
assemble APKs; the dedicated preview and device jobs are disabled.

## M5 — source-checked native core development APK

This is a **single M5 development APK** to verify real Android onboarding,
program review/save, Today/Train, acknowledged set logging and corrections,
Finish, history, interruption/resume and the original theme system. The
canonical bridge now passes a continuous two-unit lifecycle test from
onboarding through correction and next training day; the native UI compiled
on the same Kotlin source and the existing isolated domain/journal tests
remain required. M5's build job reruns the source checks and domain tests
against the exact marker commit before packaging.

The code still relies on the existing training engine and has unimplemented
advanced features. A passing APK job is not physical-device acceptance,
Kotlin-engine correctness, a production rollout or a reason to merge into
`main`. The other developer's native branch and active web app are untouched.
Keep ordinary builds frozen after this single marker-triggered candidate.
