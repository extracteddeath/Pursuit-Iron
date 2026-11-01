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
