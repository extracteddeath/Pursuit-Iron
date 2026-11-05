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
`publishReplayVerifiedV2Mirror(owner, athlete, replayReader, receiptVerifier)` as an
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

## Independent native receipt verifier (candidate migration precondition)

`NativeKotlinIndependentHistoryReceiptVerifier.verify` now reopens the
authenticated Composer/custom workout journals and saved-correction receipts
under the shared owner fence **without querying the version-2 ledger**.
It verifies one exact proposed completion receipt, original Finish event,
revision, issued athlete, latest corrected event (if applicable), and complete
factual history projection. It fails closed on a missing custom saved-correction
store instead of inferring that there are no edits. Tests exercise generated
and athlete-authored custom Finishes/corrections, unexpected edits, wrong
receipt identities, wrong revision, and revoked owner access.

One valid receipt does NOT establish the completeness of a lifetime history,
prove an existing workout ID is unique, authorize a root transition, or
publish Last/PR/progression. This verifier is deliberately not part of the
active app, v2 transaction writer or any training decision. The next
single-writer gate must combine these exact journal receipts with a durable
owner-verified unique workout index, WAL root CAS and migration enrollment.

## Candidate root integration of exact native receipt verification

The internal `publishReplayVerifiedV2Mirror` path now also calls
`NativeKotlinIndependentHistoryReceiptVerifier` for the **exact staged**
Finish/custom Finish or generated/custom saved correction. Merely matching
the full v2 manifest is insufficient: the specific source operation must
exist as a fully replayed durable journal/correction receipt. The verifier,
whole-history replay, candidate WAL and root CAS run under the same
owner transaction fence. Missing or forged receipts fail closed.

The candidate remains a read-only v2 *mirror* as far as the app is concerned,
and the older test-only root-publication seam is not authorized for
production. The new check does not eliminate the need for explicit
lifetime-history enrollment, durable unique identity indexing or
single-writer migration.

## Candidate-to-v2 parity preflight (read-only)

`NativeKotlinCandidateSegmentedRootFileStore.reviewAuthenticatedV2Parity`
adds a second, **non-mutating** gate after a candidate root has been staged or
mirrored. Under one authenticated owner fence it requires a full handoff review:
current v2 source, the independently checked segmented shadow, and the
replayed generated/custom workout and correction journals must all match.
It reopens and checks **every** candidate immutable shard, verifies unique
workout and saved receipt identities, and requires exact row-for-row parity
with the current authoritative manifest at the same revision.

A candidate root that published synthetic test-only history, has an incomplete
redo, references corrupted shards, belongs to a revoked owner, or was made
stale by a v2 correction fails the review. Because staged WAL files are
deliberately retained after successful mirrored commits, the gate accepts
a WAL only when its exact parent, patch digest, revision and count are
already represented by the committed root. The independent shadow must be
synchronized to the newly finalized source revision before a positive review.

**Important:** passing this snapshot comparison does not transfer history
write authority, open Compose history reads, approve progression or certify
backup/device durability. The production migration still needs an
account-scoped unique-workout index, full legacy import verification,
cross-process and physical-device crash testing, Android backup/restore
tests, and an exclusive atomic source-owner cutover with rollback policy.
The original JS app, current v2 history and other Android development branches
remain unchanged.

## Candidate workout identity index (shadow-only migration rehearsal)

`NativeKotlinCandidateWorkoutIdentityIndexFileStore` now persists a
checksum-framed, per-owner **auxiliary** lookup table of exact workout IDs,
saved Finish receipt IDs and completion ordinals. Building is permitted only
after the candidate root passes `reviewAuthenticatedV2Parity` (including full
v2/shadow/journal replay), and it records that candidate's exact root digest
and manifest revision. A repeated build of the identical index is idempotent;
a newer fully verified root may refresh an older index. Conflicting indices
at the same or higher revision and corrupted existing bytes are *not*
silently overwritten. Crash-before-index-swap retains an old, detectable
stale index; an orphan temporary file does not become authoritative.

Both workout-ID and saved-receipt lookups fail closed on owner revocation,
missing index, corrupt bytes, stale root digest, pending uncommitted redo or
unverified source history. Null lookup results **never authorize** inserting
a new workout. A correction keeps the stable receipt/ordinal identity but
requires the index to be refreshed after the candidate root changes.

**No new history writer was introduced.** The current format is deliberately
bounded by the existing 4,096-completion v2 migration source and loads a
whole lookup table. Each lookup still performs complete authenticated parity
replay and thus is *not* a scalable or O(1) history authority. Production
requires a paged/indexed identity owner, atomic index-and-root cutover,
crash-safe directory durability, backup/restore certification, verified
legacy migration and a strict single-writer protocol before any active use.

## Exact redo acknowledgement fingerprint (candidate-only)

The candidate segmented root now hashes the **entire typed redo operation**,
including the expected revision, original set or custom amplifier visit key,
corrected event ID, actual outcome, weight, unit, reps and RIR. Distinct
correction payloads with a reused event ID can no longer be mistaken for an
already published patch after acknowledgment loss. Finish events also bind
their expected journal revision, and Composer/custom kinds have separate tags.
Regression tests exercise collisions without writing to the canonical v2 ledger.

This changes only the internal candidate acknowledgment fingerprint schema.
An older experimental candidate root/WAL pair whose last-patch digest used the
shortened format must fail closed and be explicitly reconciled, not silently
treated as the same committed transaction. This does not create an
authenticated single writer, a scalable durable index, directory fsync, or
device-tested power-loss recovery.

## Paged identity directory layout (non-authoritative planner)

`NativeSegmentedIdentityPageDomain` introduces a separate proof-of-layout
for the future replacement identity index: **independent workout-ID and
receipt-ID sort orders**, at most 128 rows per checksummed page, stable
long completion ordinals and an exact root/revision identity. In-memory
tests cover 4,097 identities, boundary lookups, an empty index, missing
ordinal, cross-page duplicate IDs, stale roots, revoked/incorrect scopes
and a mutated selected page. A null lookup or positive match has **zero**
write, edit, PR, coaching, or migration authority.

This does **not** replace the existing 4,096-row v2-backed candidate
index or expand current production history. It is a pure, caller-supplied
candidate snapshot, NOT a persistent index; building still consumes the
complete source history. Before use, a native one-writer protocol must
persist immutable pages, authenticate their COMPLETE directory and owner,
prove source-journal and legacy parity, atomically couple receipt/workout
directories with the segmented history root, and recover a lost commit.

The page planner also has a **candidate-only copy-on-write Finish projection**.
It rejects a stale source root or revision, duplicate workout/receipt ID,
incorrect completion ordinal and malformed selected pages. A successful
proposal changes at most one old page in each key tree (with a bounded
128-row split when full); untouched page objects remain unchanged. Tests
rehearse the 4,096-to-4,097 transition and a full page split. No directory,
WAL, journal, root or index is published by this reducer, and it cannot
authenticate a caller-supplied absence proof or grant write authority.

## Persistent candidate paged identity mirror — not a source of truth

`NativeKotlinCandidatePagedIdentityFileStore` persists separate, immutable,
SHA-256-bound workout/receipt key pages and a checksummed owner/athlete-scoped
directory. Each page is at most 128 identities. Page publication forces file
bytes before a final atomic directory replacement; a crash can leave orphan
pages but cannot make them part of a committed directory. Read-only cold
reopen audits **both** page trees and verifies exact row-for-row parity against
the currently replay-authenticated v2/candidate root. Missing, corrupt,
cross-owner, stale, or superseded material fails closed. Corrupt existing
indexes are NEVER silently repaired.

**Migration rehearsal only:** although the on-disk format uses long ordinals
and can represent more than 4,096 identities, its authenticated build source
is still the legacy 4,096-entry v2 ledger. Every lookup currently replays
complete parity and opens every candidate page, so no scalable O(1) access
or independent uniqueness ownership is claimed. The two page trees are not
yet atomically coupled to the segmented workout root. Further work must
replace the source gate, implement transaction/redo-coupled index updates,
validate on-device power-loss behavior and backup/restore, and prove a
single native writer before cutover.

## Candidate paired-root/index checkpoint (single candidate acknowledgment)

`NativeKotlinCandidatePairedHistoryCheckpointFileStore` now durably records
ONE verified revision/count/root digest plus the EXACT checksum-framed paged
directory's **disk-file digest** in a SHA-256 framed, owner-scoped checkpoint.
The checkpoint only publishes after complete authenticated source-to-candidate
root parity and independently reopened **both-tree** workout/receipt parity.
If a crash updates only the root or only the index, the old checkpoint is stale
and a read must fail closed; a lost acknowledgment of the checkpoint's own
atomic rename is idempotently recovered. Corrupt existing checkpoints are never
silently replaced. A verified newer root+index pair may advance the checkpoint;
revisions cannot go backward.

This is a read-only candidate *pairing acknowledgment*, not an atomic
transaction spanning the two source files. A future single-writer WAL must
coordinate journal receipt, root, BOTH paged trees, directory force/sync and
the final commit marker under one exclusive native owner before any history
cutover. Candidate checkpoint reads do NOT issue training, authorize a
workout Finish/correction or expose history to Compose.

## Source-verified paged identity incremental refresh

The candidate disk index now uses the bounded page planner when one
replay-authenticated v2 Finish adds a completion, and reuses unchanged typed
content-addressed page references across both Finish and saved-correction
refreshes. A correction changing no workout/receipt identities only replaces
the directory bound to the new segmented-root revision. No old immutable page
may be altered. On every path it still constructs the independently verified
complete source layout, checks old-page integrity, authenticates candidate
root parity, and verifies every projected row before publishing. Unmatched
revisions or unexpected legacy changes fall back to the full source layout.

This is an **I/O optimization for a candidate mirror**, not evidence of
scalable latency, a removed v2 4,096-cap, an atomic cross-file writer or
production cutover. Source and page verification still scan lifetime records.

## Post-commit recovery of candidate root/index/checkpoint

`NativeKotlinCandidateHistoryMirrorRecoveryCoordinator` rehearses recovery
**only after the canonical v2 Finish/correction and replay-verified candidate
segmented root already agree**. Under the same cross-process owner fence it
authenticates the full source/root, audits the current paged index, permits
an exact source-verified stale-index refresh, and acknowledges one matching
paired checkpoint. Cold retries are idempotent; failure injections separate
crash after index replacement from lost checkpoint acknowledgment.
A corrupt persisted directory, invalid seal, revoked owner, or missing
directory referred to by a previously committed seal is refused, never
silently recreated.

It does NOT replay an uncommitted v2 journal, finalize a redo WAL,
commit a candidate segmented root, write history facts, or grant new-workout
IDs; these still require a future **single transaction-owning native writer**.
The checkpoint's own atomic rename is not a substitute for jointly fsynced
root+index authority or real-device backup/power-loss certification.

## Candidate exact-redo transaction intent (not the authoritative WAL)

`NativeKotlinCandidateHistoryTransactionIntentFileStore` durably freezes
the exact, typed staged redo patch digest, parent root, expected revision and
completion count before the source-history mirror commit. A later candidate
acknowledgment is permitted **only** if full generated/custom journal replay,
already-committed v2 ledger, candidate segmented root, both identity trees
and the exact paired root/index checkpoint match that frozen operation.
Wrong original root, reused correction event ID with a different performed
load/reps/RIR, incomplete directory or missing checkpoint cannot be
acknowledged. Lost acknowledgment is byte-for-byte idempotent. Corrupt
existing intent bytes are rejected, never overwritten.

This is a durable, isolated rehearsal marker for one staged operation only.
It does not become the real transaction writer, clear/release the underlying
redo WAL, make the v2 ledger optional, guarantee parent-directory durability
or grant any history/PR/progression/Finish authority. Testing remains
essential before Android cutover.

## Durable candidate mirror recovery intent (optional WAL gate)

`NativeKotlinCandidateMirrorRecoveryIntentFileStore` now persists a
checksum-framed, per-owner immutable **PREPARED** target root/revision/count
before candidate index reconciliation, and advances to **ACKNOWLEDGED** only
after complete authenticated root and both-index checkpoint review.
The coordinator accepts it through an explicitly optional candidate-only
dependency; existing production history and the v2 writer are untouched.
An identical retry after index publication or lost checkpoint acknowledgment
is idempotent. A contradictory root while PREPARED refuses. An acknowledged
previous revision cannot advance unless the previous recorded checkpoint
still exists with the exact persisted digest. Corrupt intent bytes fail closed.

This journal does **not** participate in the original Finish or saved-edit
transaction and cannot publish/restore a segmented root. It is a checkpoint
reconciliation WAL rehearsal, not the final native single-writer authority.
Physical Android directory-fsync/backup and rollback evidence are still
missing.

## End-to-end candidate staged replay recovery (still v2-owned)

`NativeKotlinCandidateStagedReplayRecoveryCoordinator` links the previously
isolated stages under the **same cross-process owner lock**: confirm the exact
saved redo, durably freeze its operation identity as a transaction ticket,
verify that the original v2 Finish/saved correction and original journal have
already been committed, publish/reopen the segmented candidate root, refresh
the verified shadow, reconcile both paged identity trees plus their paired
checkpoint, and finally acknowledge that SAME durable transaction ticket.
It cannot create a workout completion from WAL-only evidence, clear a redo,
rewrite a corrupt checkpoint/index/ticket, authorize any training decision, or
transfer authority away from the current v2 history store.

Failure-injected tests cover crashes before and after the candidate root
swap, after root verification, after shadow refresh, lost checkpoint/index
material, cold restart/idempotence and revoked or wrong athlete identity.
The separately isolated transaction-intent and mirror-recovery-intent stores
have their own corruption and retry regressions. For a future cutover, the
single production writer must replace v2 with a journal-first exclusive native
commit protocol, coordinate root/index directory fsync, and prove real device
power-loss/backup restores, 4,097+ workouts and multi-cycle training accuracy.
Neither this coordinator nor its successful tests constitute that cutover.
