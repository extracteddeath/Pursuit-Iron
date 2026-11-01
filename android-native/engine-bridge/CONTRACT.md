# Engine parity gate

A successful `handshake` or `catalogSummary` is **not** parity certification. Native may only report `EngineStatus.Ready` after it has checked a pinned canonical source digest, replayed deterministic fixtures for actual training methods, verified matching output values and revision identity, and proven that backup reads/writes preserve the original schema. Until then `UnconnectedEngineGateway` must remain the production UI owner.

Run `node android-native/engine-bridge/protocol-test.mjs` from repository root. The dispatcher imports the **current canonical engine**, so it catches accidental export drift but does not independently certify Android execution.
