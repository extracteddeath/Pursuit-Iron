# Engine parity gate

A successful `handshake` or `catalogSummary` is **not** parity certification. Native may only report `EngineStatus.Ready` after it has checked a pinned canonical source digest, replayed deterministic fixtures for actual training methods, verified matching output values and revision identity, and proven that backup reads/writes preserve the original schema. Until then `UnconnectedEngineGateway` must remain the production UI owner.

The bridge's read-only `progressionPreview` diagnostics are tested against canonical progression fixtures. This still does **not** certify Android execution, and its data cannot be used as a live set recommendation. No new method may write saved workouts until the Android runtime and transactional storage are independently verified.

Run `node android-native/engine-bridge/protocol-test.mjs` from repository root. The dispatcher imports the **current canonical engine**, so it catches accidental export drift but does not independently certify Android execution.

A separate language-neutral contract and immutable golden-case set now live in `android-native/engine-contract/`. The Kotlin DTO is deliberately typed rather than `Map<String, String>` because observed sets and RIR require nested arrays, numbers and nulls. Actual JSON codec, JS runtime, source-digest attestation and real backend certification are future work.
