# Android native engine sandbox device milestone

## Milestone 1 — real on-device progression decision parity

This file is intentionally updated only when a real Android emulator/device
verification milestone is due. Its changes trigger the expensive device test.

- Compile the isolated `androidx.javascriptengine` library and instrumentation APK.
- Export and hash-verify the canonical JavaScript engine dependency closure.
- Bundle the allowlisted read-only bridge with pinned esbuild 0.28.2.
- Boot an Android 36 Google APIs emulator.
- Verify the engine handshake and **all seven** golden progression cases.
- Confirm that the bridge continues to reject `deleteHistory` and writes.
- Fail the milestone if the emulator lacks an Android JS sandbox or parity fails.

No app preview APK is uploaded. No production or user data is modified.
Passing a synthetic subset does not authorize live training, logging or
native engine replacement. Manual workflow dispatch remains available.

## Milestone 2 — diagnose browser-sandbox progression mismatch

The first emulator check booted and accepted the handshake but the first
progression result failed. This milestone reruns the seven real-device cases
and, only upon a synthetic `ENGINE_UNAVAILABLE` failure, invokes a separate
diagnostic-only JS bundle to report the exception name/message. This bundle is
limited to synthetic test fixtures and never installed into a product APK.
