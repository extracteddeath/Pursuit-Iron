# M229 — Versioned domain contracts and acyclic projection

Added six versioned wire contracts for requests, programs, prescriptions, workout logs, adaptation decisions and cycle state, with matching TypeScript declarations. Unversioned production values and the explicitly defined v0 payload envelope migrate to v1 value envelopes. Migration is immutable, idempotent and lossless; it does not invent observed effort, completed work or ownership. Future versions and malformed record structures are rejected. Request normalization and audited-program projection accept the versioned records while preserving existing plain-value callers and generation results.

Separated shell identity, prescription ownership and weekly-cell projection into `shell-projection.js`. Volume repair now depends directly on these read-only primitives; the adapter/repair cycle is gone. Every production JavaScript module is checked for missing imports and cycles. The standalone exporter includes the declaration file.

Validation: six migration families, invalid/future versions, roundtrips, malformed set counts, envelope/plain generation parity; 64-module acyclic graph; all 25 adaptation contracts including all 13 immutable Build 816 golden projections. Existing persistence-store migrations remain authoritative for backups; these contracts version engine boundary records and do not rewrite user stores.
