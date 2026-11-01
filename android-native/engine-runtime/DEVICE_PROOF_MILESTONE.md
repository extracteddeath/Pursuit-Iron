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

## Milestone 3 — canonical loading default clone compatibility

The preceding Android API-36 emulator run successfully initialized the engine
and confirmed that the **first progression call failed because** the sandbox
lacks the Web `structuredClone` global. The Android-only entry now supplies
a restricted clone implementation exclusively for plain factory inventory
records, leaving all canonical policy modules untouched. This run must prove
all seven cases with **full field-for-field canonical result comparison** and
a denied `deleteHistory` method. No live workout data or build promotion.

## Milestone 4 — serial read-only engine worker and lifecycle

After the successful Android canonical progression parity milestone, the engine
runtime now includes a separate **non-UI**, read-only sandbox service. This
emulator run verifies its actual Android lifecycle: validate the bundled SHA,
perform a canonical handshake, serve two distinct bounded progression previews
in one sandbox, decode with the typed Kotlin codec, close, release the process
sandbox lease, and refuse future requests after close. The seven-case full
parity suite still runs. No app preview APK, history write, production data,
or permission to surface recommendations in Compose.

## Milestone 5 — canonical exercise metadata via the reusable Android sandbox

- Verify the entire canonical EXERCISES catalog in bounded Node/CI batches.
- Build the **read-only** Android bridge using the verified source-closure snapshot.
- In the API 36 emulator, fetch a fixed list of canonical exercise IDs through
  the existing serial JavaScript sandbox worker, preserving requested order,
  equipment and rep-range facts.
- Decode the result through the strict Kotlin ExerciseCatalogCodec, including
  explicit missing exercise IDs. Reject any attempt to invoke deleteHistory.
- Re-run the seven existing full-field double progression cases and lifecycle tests.
- Close the isolate and refuse subsequent requests; keep data writes disabled.
- Fail on a mismatch or untrusted response. No production APK is created or uploaded.

This is a **native read-only compatibility milestone**, not a training launch
or permission to import user workouts, change programs or enable prescription UI.
