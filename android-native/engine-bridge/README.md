# Canonical JavaScript engine bridge — protocol v1

This is a **transport boundary**, not an alternate engine. The browser and native shell must not implement progression formulas independently. The only authoritative calculation owner is `modules/engine-api.js`.

The pure-JavaScript dispatcher in `protocol.mjs` supports an allowlisted handshake and a canonical export identity probe. It does not open Android WebView, expose arbitrary module exports, read local storage, or issue workout prescriptions. It can be exercised in Node before implementing an Android JavaScript runtime.

## Transport

Input and output are JSON objects. Requests: `{protocolVersion:1,id:"...",method:"handshake",params:{}}` or `{protocolVersion:1,id:"...",method:"catalogSummary",params:{}}`. All responses include `protocolVersion`, `id`, `ok` and either `result` or `error:{code,message}`. Unknown methods, malformed params, invalid IDs, non-finite numbers and unsupported versions fail closed. The adapter must not evaluate arbitrary JS strings from a caller.

The `catalogSummary` method reads only the canonical engine's `EXERCISES` and `TEMPLATES` exports and returns counts, not full data. `handshake` identifies the source and the capabilities explicitly implemented by the dispatcher; it does not certify full engine parity.

## Android integration constraints

- Use an isolated, local-only JS execution context; never inject the engine into a remote page or expose native file/storage objects to it.
- Bundle a pinned snapshot of the canonical dependency closure from `engine-lab/canonical-export.mjs`, with a manifest digest verified before execution.
- Every prescription/history mutation must pass independent fixture parity tests against the web engine before adding a method to the allowlist.
- Marshal only JSON-safe values; preserve IDs and revision ordinals exactly; never convert load/rep precision or substitute defaults in Kotlin.
- No backup import/write until a separate versioned schema validator and round-trip tests are approved.
