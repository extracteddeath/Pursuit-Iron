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
