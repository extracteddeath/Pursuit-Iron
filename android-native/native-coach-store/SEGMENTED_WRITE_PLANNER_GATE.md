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

## Candidate immutable shard + atomic root publication (rehearsal)

`NativeKotlinCandidateSegmentedRootFileStore` is an **internal, inactive**
secondary root namespace. Bootstrap requires both authenticated full
Composer/custom journal replay and exact parity with the version-2 shadow.
It never creates an athlete merely because no candidate root exists. The
candidate loads durable staged redo, verifies the actual existing shard(s),
checks unique workout/receipt IDs, reruns the pure transaction planner
against the on-disk root, forces a new immutable shard, and atomically
replaces its *own* checksummed root. The WAL is retained unchanged. A retry
after publication compares the exact parent root and patch identity and
returns an idempotent acknowledgment; a stale unrelated root fails closed.

Failure-injection tests cover a crash after a shard is written but before
the root swap (orphan ignored; repeat safely commits) and loss of
acknowledgment after root publication (cold restart reports replay, never
double-counts). Tests also cover old workout correction, root/shard
corruption, owner revocation, and the fact that version-2 remains unchanged.

This is **NOT a new history authority**. Candidate publication does not
authenticate a post-bootstrap journal/Finish/correction and cannot
publish Last/PR/progression. It still scans historical shards to prove
uniqueness, carries a bounded root descriptor table, and has not passed
Android device power-loss/backup testing. A production handoff must
replace these engineering-only entrypoints with one replay-authenticated,
account-scoped writer and a scalable unique workout index. Do not merge
or wire this candidate into the existing app.

## Replay-authenticated v2 mirror gate

The candidate now also supports
`publishReplayVerifiedV2Mirror(owner, athlete, replayReader)` as an
**internal migration rehearsal**, distinct from the original unauthenticated
candidate-test publication seam. Under the owner transaction fence it requires
the current v2 manifest to have finalized the exact next revision and count,
full independent Composer/custom journal + saved-correction replay, and exact
whole-history equality between the proposed candidate root and every
authoritatively saved manifest entry. It refuses pending source transactions,
fabricated entries, stale proposals and contradictory revisions before a
candidate root swap. Exact replay after a lost acknowledgment is allowed.
Any material staged beyond the v2 4,096-entry cap remains test-only.

This is a strict **mirror of a pre-existing v2 commit**, not the segmented
source of truth: it can neither Finish a workout nor issue progress changes.
It remains O(all existing segments) and does not provide device power-loss,
backup/restore or single-writer cutover guarantees. Once the verified
segmented writer becomes primary, an independent journal verifier and unique
workout ID index must replace the v2 source check.
