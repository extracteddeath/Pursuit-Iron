# Canonical JavaScript engine bridge — protocol v1

This is a **transport boundary**, not an alternate engine. The browser and native shell must not implement progression formulas independently. The only authoritative calculation owner is `modules/engine-api.js`.

The pure-JavaScript dispatcher in `protocol.mjs` supports an allowlisted handshake, canonical catalog identity probe and a read-only double-progression diagnostic proof. It does not open Android WebView, expose arbitrary module exports, read local storage, or issue workout prescriptions. It can be exercised in Node before implementing an Android JavaScript runtime.

## Transport

Input and output are JSON objects. Requests: `{protocolVersion:1,id:"...",method:"handshake",params:{}}` or `{protocolVersion:1,id:"...",method:"catalogSummary",params:{}}`. All responses include `protocolVersion`, `id`, `ok` and either `result` or `error:{code,message}`. Unknown methods, malformed params, invalid IDs, non-finite numbers and unsupported versions fail closed. The adapter must not evaluate arbitrary JS strings from a caller.

The `catalogSummary` method reads only the canonical engine's `EXERCISES` and `TEMPLATES` exports and returns counts, not full data. `handshake` identifies the source and the capabilities explicitly implemented by the dispatcher; it does not certify full engine parity. `progressionPreview` accepts only a bounded synthetic set schema (exercise ID, 1–8 observed sets, rep floor/ceiling, RIR and limited equipment list). It calls the actual canonical `evaluateWorkoutProgression` and returns a **diagnostic**, not a writable prescription. Input schemas are deny-by-default and the JSON payload has a 16 KiB ceiling. Both this proof and the catalog remain disabled in the native app.

## Android integration constraints

- Use an isolated, local-only JS execution context; never inject the engine into a remote page or expose native file/storage objects to it.
- Bundle a pinned snapshot of the canonical dependency closure from `engine-lab/canonical-export.mjs`, with a manifest digest verified before execution.
- Every prescription/history mutation must pass independent fixture parity tests against the web engine before adding a method to the allowlist.
- Marshal only JSON-safe values; preserve IDs and revision ordinals exactly; never convert load/rep precision or substitute defaults in Kotlin.
- No backup import/write until a separate versioned schema validator and round-trip tests are approved.

An experimental unconnected Android library lives in `android-native/engine-runtime/`. Its worker-thread-only `CanonicalJsSandboxProbe` verifies an externally pinned bundle SHA-256, checks AndroidX sandbox availability and performs a read-only canonical handshake in an out-of-process isolate. The native Compose app does not depend on it yet, and a handshake is **not** runtime parity certification.

## CI-only offline single-file bundle

`.github/workflows/android-engine-bundle.yml` now bundles the real read-only canonical bridge with **esbuild 0.28.2**. The output is a single IIFE named `PursuitIronBridge`; CI checks its syntax, source module dependencies, import closure, maximum byte size and SHA-256. The ephemeral bundle is not committed, not connected to Android UI and is not an APK. Bundling success is a prerequisite for actual AndroidX JS sandbox execution; it is **not** proof of on-device parity.

**Source ownership tightened:** the bundle workflow now first exports the exact canonical engine dependency snapshot, verifies its file hashes and standalone parity, and copies only the two read-only bridge entry files into that temporary tree. esbuild reads exclusively from this verified snapshot; its input metadata is checked against the canonical manifest. This avoids accidentally bundling unreviewed production-web files from the working checkout. The result remains a disposable, unconnected test artifact.

## Scoped sandbox compatibility (verified issue)

API-36 emulator instrumentation confirmed `ReferenceError: structuredClone is not defined` in the canonical loading helper. Android bundles are now initialized via `android-entry.mjs`, whose local data-only fallback handles arrays/plain inventory records and primitive values but rejects non-plain objects/accessors. The web engine and its formulas are unchanged. The fallback is covered by unit tests, while **real Android feature parity remains subject to the next emulator milestone**.

## Bounded canonical exercise lookup

`exerciseLookup` accepts exactly `{ids:[...]} ` with 1–12 unique, syntax-valid canonical exercise IDs. It reads the authoritative training-domain `EXERCISES` catalog and projects only `id`, `name`, `part`, `type`, `equipment`, rep-floor/ceiling and movement `pattern`. Requested ordering is preserved and missing IDs are returned separately. It does not expose custom exercises, stored programs, user workouts, or potentially unbounded exercise metadata. All invalid schemas reject, and the native Compose UI is still unconnected.

Catalog lookup is next verified in an Android emulator through the existing serial sandbox, using a typed Kotlin codec to guard IDs, equipment, rep ranges, missing IDs, and source provenance. The canonical training policies remain unchanged.

## Read-only synthetic workout outline

The `workoutOutline` diagnostic accepts only `{split,dayIndex}`, with split one of `full_body` (3 days), `upper_lower` (4 days), or `ulppl` (5 days) and a bounded day index. It calls the **canonical** `generateNextProgramForShell` with the fixed, non-user 60-minute configuration already used in standalone parity tests, requires an audited passing engine program and projects exercise order, role, sets, rep bounds, RIR bounds, rest seconds and source duration. It does not include user history, actual plans or weekly load prescriptions. `readOnly` and `syntheticOnly` are both enforced, and the feature remains disconnected from Compose. It is not suitable for suggesting a personal session.

The Kotlin `WorkoutOutlineCodec` and `SandboxWorkoutOutline` adapter preserve generated exercise order, roles, rep bounds, RIR and rest while enforcing `syntheticOnly` and source identity. An emulator test verifies the end-to-end wire and that write-method attempts are blocked. No UI or persistent storage integration has been authorized.

## Canonical backup schema probe (not native import)

A dedicated `backupSchemaCheck` method uses the same canonical
`parseStoredData` export as the browser, constrained to an explicitly
version-13, fully shaped source and a **65,536-character input**. The normal
engine JSON channel retains its 16-KiB limit; the separate schema-only
channel is capped at 192 KiB. It returns only five collection counts plus
`readOnly`, `imported:false`, `approvedForWorkoutExecution:false`
and source/version provenance. It never returns personal records or even
names. Unknown/write methods cannot enter the larger channel. Both the native
Java worker and Kotlin decoder independently validate the method, shape,
bounds and response.

**This is a small-source interoperability proof, not full backup support.**
Larger backed-up stores and alternative schema versions fail closed.
The Compose app does not depend on the sandbox module and cannot send a
selected file to this endpoint. Exact snapshot digest, revision/tombstone
checks and device lifecycle certification remain separate gates.

## Resolved milestone-7 integration gap (verify on device)

The first Android emulator schema test exposed a packaging mismatch:
`dispatchBackupSchemaJson` was implemented but not exported by
`android-entry.mjs`. Bundle verification now runs the actual IIFE and
requires the exported method plus a canonical v13 parse and write rejection,
rather than trusting the source-level dispatcher alone.
