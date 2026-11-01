# Native preview build milestones

Edit this file **only when the native preview has a significant user-facing change or a toolchain validation milestone**. Routine engine-contract and Compose UI commits should not create a new APK.

## M1 — SDK 37 / Material 3 Expressive toolchain validation

- Upgrade AGP 9.1.1, Gradle 9.3.1, SDK 37 and Kotlin Compose plugin 2.4.21.
- Verify compiled Material 3 1.5.0-beta02 and Compose BOM 2026.09.00.
- Native preview remains a no-data demo; no real set logging or history writes.
- APK success is a toolchain gate, **not** a training-engine or device-acceptance certificate.
