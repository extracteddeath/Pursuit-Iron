# Language-neutral engine contracts — future Kotlin migration

This folder records external behavioral expectations, **not implementation formulas**.
The JavaScript engine in `modules/engine-api.js` remains canonical until a feature is
separately certified and deliberately promoted. The Kotlin protocol DTO and
`ParityGatedProgressionRouter` are inactive infrastructure for a future switch.

## Why this enables Kotlin

- Inputs and outputs are schema-first and carry stable exercise IDs, loads, reps,
  RIR, revision identity and source provenance. Kotlin is not forced to mimic React.
- Golden cases are fixed expectations checked against the JS engine. Kotlin
  implementations must replay the same cases **plus broader independent fixtures**
  and compare results without tolerance-based rounding for equipment increments.
- Features are promoted individually, not by a global "Kotlin enabled" flag.
  The per-feature gate requires exact protocol, canonical source and fixture digests,
  plus explicit approved backend identity. Uncertified backends fail closed.
- A backend execution failure is returned as an error; there is no automatic
  second execution on a different engine (especially important for future writes).
- `progressionPreview` is diagnostic and read-only. An engine backend that can
  write training history requires a new transactional contract with idempotency,
  edit clocks, tombstones and durable rollback.

## Suggested port order

1. Pure data normalization and input validation.
2. Equipment-specific loading steps and plate inventory / unit semantics.
3. Double progression, RIR and fatigue-aware set suggestions.
4. Session duration, readiness and injury restrictions.
5. Program planning, weekly volume and adaptive blocks.
6. Workout history, correction-safe analytics, personal records.
7. Stateful execution and full native storage migration **last**.

A parity match is not evidence that the historical JS policy is perfect.
When fixing a known engine defect, add a new golden expectation and independently
validate the intended behavior before updating either implementation.

## Constraints

There is no embedded JavaScript runtime on Android yet and no Kotlin calculation
engine. These are typed contracts and read-only JS fixtures only; do not claim
a usable native training engine, import a personal backup, or enable real logging.

Kotlin domain-level unit tests run with `:domain-contract:testDebugUnitTest` in a separate workflow; this **compiles the Kotlin engine seam without generating or deploying an APK**. The JavaScript golden cases remain the baseline until the platform-specific JSON codec is implemented and validated.

## Runtime and fixture digests

`snapshot-identity.mjs` verifies every runtime file's bytes against the exported canonical snapshot, then derives deterministic SHA-256 digests for the canonical dependency closure and fixed progression fixtures. CI runs this **after** independent standalone-engine parity. The result is an identity candidate for future Android packaging, **not a signed certificate and not proof that an Android JavaScript sandbox has executed**. A runtime must compare exact pinned digests before granting a Kotlin or JS backend permission to return training decisions.

The Kotlin gate now requires explicit `EngineFeature.PROGRESSION_PREVIEW`, the exact supported protocol version, and two complete lower-case SHA-256 digests. A blank or merely descriptive digest cannot activate an implementation. Identity equality still does not itself certify runtime execution: an independent trusted verifier must supply approved source/fixture identities before promotion.

The `:engine-runtime` Android library is intentionally disconnected from UI and performs only a guarded canonical JavaScript sandbox handshake. Its byte digest covers the **packaged bundle**, whereas the canonical dependency-closure digest and golden fixture digest measure different inputs. All three must be pinned and compared before Kotlin/JS engine certification.

## Native sandbox packaging manifest

The packaging workflow now generates a **three-part, ephemeral provenance manifest** containing the canonical dependency-closure SHA-256, golden-fixture SHA-256 and exact bundled IIFE byte SHA-256. The manifest also records source count, fixture count, bundler identity and explicit `approvedForWorkoutExecution: false` / `androidSandboxParityVerified: false` flags. Tests reject corrupted bundles and altered certifications. The manifest is not user-editable approval and is not promoted to the native app without an independent device test and trusted pinning step.

The Android instrumentation workflow now builds a full expected-result oracle from the live canonical JS source for each synthetic regression case. The emulator compares **every field and ordered set target**, in addition to the human-reviewed fixed expected subset, rather than merely checking action and suggested weight. No real workouts or private history are serialized.
