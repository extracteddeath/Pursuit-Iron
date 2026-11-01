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
