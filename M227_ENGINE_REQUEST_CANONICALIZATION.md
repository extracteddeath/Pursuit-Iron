# M227 Engine Request Canonicalization — Build 816 / Engine 0.64.16

M227 is the fail-closed request-boundary pass after M226.

- Equipment IDs, per-day overrides, loading units, split names, and volume strategy are canonicalized.
- Superset/bodyweight booleans have one explicit meaning; string truthiness cannot change behavior.
- Preferred/avoided exercise lists and nested request records must have the expected container types.
- Custom exercise IDs/names/equipment tokens are normalized and malformed boolean flags are rejected.
- Normalization remains immutable.
- Harmless presentation variance must produce the same canonical request and the same seeded program.

Permanent gate: `verification/m227-request-canonicalization-test.mjs`.

Semantically ambiguous input is rejected rather than guessed.
