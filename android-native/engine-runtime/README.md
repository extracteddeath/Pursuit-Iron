# Experimental Android JavaScript sandbox boundary

This is an **unconnected Android library**, not a production engine service.

The Java-only `CanonicalJsSandboxProbe` uses stable
`androidx.javascriptengine:javascriptengine:1.1.1` and a separate
out-of-process JavaScript isolate. This is compatible with a future Compose/Kotlin
integration without coupling formulas to UI components.

- The input is an in-memory UTF-8 IIFE bundle created offline from the
  canonical source. No URL, WebView or network loading.
- The caller must supply the expected **bundle byte SHA-256** from a trusted
  build manifest. A string supplied by the user is not sufficient authority.
- The probe checks support at runtime, rejects main-thread blocking calls,
  applies finite timeouts and closes the isolate and sandbox.
- Only the read-only `handshake` is executed. Exact protocol fields,
  canonical source identity and explicit `engineCertified: false` are checked.
- No arbitrary request interface, workout persistence, backup import or
  native user-specific data exposure is present.
- The sandbox is restricted to one per app process. Before connecting the
  native app, refactor this proof into an app-scoped service managing lifecycle,
  asynchronous calls, isolation crashes, retries and cancellation.
- Source-closure SHA and fixture SHA from `engine-contract/` remain separate
  from the SHA-256 of the actual *bundled* JavaScript bytes. All three must be
  accounted for before any per-feature parity certification.

This module is CI-compiled independently of the preview APK. Android device
sandbox support and parity remain **unverified**.

The independent `Canonical Android engine JS bundle proof` workflow produces a disposable, version-pinned IIFE from `engine-bridge/protocol.mjs`. A verified bundle SHA-256 is only a packaging identity, not engine approval. The Android module still requires actual on-device handshake and all golden progression cases before the gateway can become eligible.

The APK-free module workflow now also runs local Java unit tests for bundle byte integrity, malformed hashes, zero-length payloads and non-ASCII input. The tests cannot establish real Android sandbox support or on-device execution; those are explicit future gates.

## On-device proof (opt-in milestone)

Run `Android sandbox on-device engine proof (milestone)` through manual dispatch or by explicitly updating `DEVICE_PROOF_MILESTONE.md`; routine source commits must not start an emulator. The workflow exports and parity-verifies the canonical engine closure, produces an esbuild IIFE, packages the exact script with its SHA-256 as **temporary instrumentation-test assets**, then boots a disposable Android emulator and runs `:engine-runtime:connectedDebugAndroidTest`. The test checks actual sandbox support, the canonical handshake, all seven synthetic progression fixtures, and the rejection of a write-capable method. A mismatch or unsupported emulator causes a **hard failure**, not a false pass. No production APK artifact or user data is involved. A passing emulator test would prove this read-only fixture subset on that emulator; it would not yet certify full engine features, interruption recovery or live user-data access.

Instrumentation-only exception diagnostics now reside in `src/androidTest/` rather than in the shipping Java library; the main sandbox probe never exposes test exception introspection or read/write arbitrary engine operations.

A real emulator trace verified that Android JavaScriptEngine lacks the `structuredClone` global used by the canonical equipment inventory defaults. The Android-only bundle now initializes a bounded fallback that copies plain data without modifying web engine modules. The following milestone retests actual V8 progression outputs against a full-field canonical Node oracle rather than simply trusting the handshake.

## Reusable disconnected read-only worker

`CanonicalJsReadOnlyService` manages a single-worker AndroidX sandbox lifecycle: external bundle SHA-256 verification, handshake, bounded read-only progression JSON requests, fail-closed errors, an exclusive per-process sandbox lease, and non-blocking cleanup. The class is **not connected to Compose or workout data**; its outputs are unapproved diagnostics until independently routed through the pinned Kotlin feature gate. It intentionally has no retry on engine failure and no write-capable methods. The next instrumented device milestone must verify reuse, explicit close and crash behavior before any user-facing integration.

The next emulator milestone also validates the **reusable read-only service**: it loads a pinned bundle once, performs two canonical progression calls with distinct request IDs, decodes a result via the strict Kotlin codec, explicitly closes the worker and awaits confirmed sandbox lease release. This checks real lifecycle reuse without enabling the engine in Compose.

A Kotlin `SandboxProgressionBackend` now adapts the serial read-only Java worker to the shared `ProgressionBackend` suspend contract. Its `certification` is **always null**, so the native `ParityGatedProgressionRouter` cannot select it. A future independent verifier must pin source and fixture digests and explicitly approve the feature before it can supply a displayed workout suggestion. The adapter never implements any training policy or automatically falls back to alternate engines.

The worker now exposes `lookupExercisesAsync` beside `previewAsync`, with a second native-side allowlist gate: 1–12 unique canonical IDs, exactly one `ids` parameter, strict protocol and result provenance, and a 65-KiB response limit. Both share the same single serial sandbox and never expose writes or custom/user records. The Kotlin codec validates the returned exercise data before any later UI use.

The emulator lifecycle test now checks a third request through the same worker for canonical exercise metadata, preserves the original canonical IDs and missing-ID result, validates the full response through the Kotlin `ExerciseCatalogCodec`, and rejects a simulated write-method attempt. It does not load any real user exercise program.
