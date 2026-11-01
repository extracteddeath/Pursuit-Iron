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

## Milestone 6 — audited synthetic workout outline parity and lifetime safety

This explicitly triggers one Android API-36 emulator verification of the
new `workoutOutline` **synthetic-only** contract. The test must:

- Generate a fixed full-body program using the canonical JavaScript engine.
- Verify a passing engine audit; project exercise order, set counts, rep/RIR
  bounds, rest and duration without Kotlin-generated prescriptions.
- Require both `readOnly: true` and `syntheticOnly: true`, with strict source
  provenance and Kotlin typed response parsing.
- Reject `deleteHistory` without poisoning the running sandbox.
- Re-run the previous progression and canonical exercise-catalog checks.
- Close the service and reject post-close requests.

No production APK build, migration of real workouts, personalized program
generation, storage write or promotion to the live Compose workout UI.

## Milestone 7 — bounded canonical backup schema probe on real Android V8

Verify a **strictly synthetic** v13 snapshot with the newly allowlisted
`backupSchemaCheck` entry. The API-36 emulator must execute the canonical
`parseStoredData` export in the pinned AndroidX isolated JavaScript engine;
reject malformed source and an attempted write method; prove that the result
contains only collection counts, source/version provenance and disabled
import/workout flags; decode through the Kotlin typed gate. Re-run the
existing progression, exercise lookup, synthetic outline and sandbox
lifecycle proofs.

This milestone builds only disposable instrumentation test APKs inside CI.
It does **not** produce a distributable native preview, read user backups,
approve imported history, or permit personal workouts.

## Milestone 7a — block an unexported Android schema probe before emulator boot

The first Milestone 7 emulator **failed** on the synthetic schema test:
`TypeError: PursuitIronBridge.dispatchBackupSchemaJson is not a function`.
The Android entry imported the method but omitted it from the public IIFE
exports. Node-only protocol and bundle-structure checks did not detect this.

The fix adds the missing named export and evaluates the **actual esbuild
IIFE** under a bounded Node VM in the bundle-verification workflow.
The bundle test must invoke a canonical v13 schema probe successfully and
refuse a write request. The Android emulator milestone is rerun with the
same synthetic-only acceptance cases; nothing is imported or published.
