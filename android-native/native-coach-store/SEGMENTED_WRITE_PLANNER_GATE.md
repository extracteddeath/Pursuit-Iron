# Segmented-history writer transaction planner — candidate only

`NativeSegmentedHistoryWritePlanDomain` models a future **single-writer**
segmented native history protocol without touching disk or changing authority.
It preserves the existing four operation types: Composer Finish, custom
Finish, Composer saved correction and custom saved correction. It rejects
pending redo, stale manifest revisions/digests, inconsistent completion
ordinals, wrong correction kind, wrong receipt/revision, and malformed
segments. A future root writer can apply each proposal as a copy-on-write
update to a **single 128-entry segment**, even after the legacy 4,096
completion ceiling.

This is intentionally **not** a durable writer. Its prepared state and
proposed patches are untrusted calculations. A production owner must first
prove a unique athlete/workout lookup, authenticate the current owner,
replay-verify source journals and post-Finish edits, persist the write-ahead
intent, force the new immutable segment, then atomically commit a root index
and recover interrupted transactions. No proposal is evidence that those
steps happened. The existing v2 ledger remains the only durable history
writer and `NativeKotlinSegmentedHistoryHandoffGate` remains read-only.
In particular, a missing existing workout lookup cannot be inferred from
an absent UI row, caller-provided null or a partial page.

These tests demonstrate the mutation arithmetic at boundaries 129 and
4,097, old correction page retention, both custom operation types, and
stale/duplicate/refusal safety. They are not device or crash endurance
acceptance of a future persistent segmented writer.

## Durable candidate redo journal (next migration seam)

`NativeKotlinSegmentedRedoFileStore` durably records the exact pending
Composer Finish, custom Finish, Composer saved correction, or custom saved
correction intent under the existing owner-scoped approval fence. The file is
per-athlete, SHA-256 checksummed, length bounded and updated through a forced
temporary file followed by atomic rename. On cold restart, a reader gets
the original operation, base revision, base root digest, correction target,
and optional staged copy-on-write patch without inventing or dropping an
unacknowledged event. Repeating an identical prepare/stage is idempotent;
competing writes or corrupt records fail closed.

**No authority switch or actual segmented commit has occurred.** The new
store deliberately has no method to publish the proposed segment, swap
the authoritative index/root, discard or clear the pending redo, import an
athlete, or infer a valid journal from the WAL. Base heads and targets are
supplied by a candidate caller; the source-to-disk and journal-replay trust
gates must be built into a single authenticated writer before any cutover.
Existing v2 history stays independent and untouched. A checksum detects
accidental alteration, not malicious writes by an unauthenticated process.
A forced file and atomic rename are not yet a verified device-level
power-loss guarantee: parent-directory fsync, orphan recovery, backup/restore
and cross-process failure injection remain mandatory.

Regression tests cover exact cold-open of prepared and staged redo;
idempotence; both generated/custom history operations; persisted performed
weights/repetitions/RIR; wrong segment/root or correction receipts; owner
revocation and isolation; checksum corruption; and a staged workout 4,097
that never changes the legacy v2 manifest. The store is NOT wired to
Compose, production history readers, the live JS engine, or a second writer.
