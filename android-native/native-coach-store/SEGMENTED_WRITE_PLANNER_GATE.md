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
