# Backup intake — read-only canonical validation

The web App callbacks own persistence, merge, compatibility and Undo. The backup panel is UI-only, and browser storage is protected by `modules/app-storage-ownership.js`. Android must never bypass those owners.

Two read-only commands inspect an explicitly supplied backup **copy**:
- `node android-native/data-migration/inspect-backup.mjs /path/to/copy.json` shows structural keys and counts without record values.
- `node android-native/data-migration/validate-canonical-backup.mjs /path/to/copy.json` uses the actual web `parseStoredData` and `STORE_VERSION` exports, returning only schema versions and collection counts. This may migrate **in memory** but never writes to disk.

Current canonical store schema is **v13**. `parseStoredData` validates list/object shapes, programs, cycles, reminders and rejects newer versions; `migrateStore` migrates earlier schema versions. History revisions use `updatedAt` and tombstones during `mergeStores`. The Android app must reuse the same rules rather than inventing a Kotlin merge algorithm.

Neither command imports a backup into Android, stores a copy, or certifies native round-trip safety. Do not commit real backups or their output if sensitive. The next gate is deterministic full-record round-trip and conflict resolution parity using **synthetic** fixtures, then Android runtime execution.

The read-only `roundtrip-revisions-test.mjs` uses **synthetic** records to check v13 parsing, migration from v12, per-session edit-clock precedence, tombstones, explicit resurrection and chronology-safe current performance. This is a contract/parity step, not a stored Android importer; it never writes user files.

## Read-only saved-program identity (inactive in the app)

`read-only-program-reference.mjs` accepts **only an explicitly supplied v13
snapshot string**, validates it with the canonical `parseStoredData` owner, and
derives an immutable five-field identity for one unambiguous entry in `saved`:
program ID, per-program `updatedAt`, store `savedAt`, schema version, and
SHA-256 of the **exact UTF-8 snapshot bytes**. It rejects invalid or ambiguous
metadata, and refuses deleted programs whose tombstones are at/after the
record revision. `matchesCurrentReadOnlyProgram` checks the same reference
again against current bytes, failing closed if any record or other store field
changes. Older backups can still be inspected with the previous commands,
but must be migrated and revalidated through the canonical owner before
ever qualifying for the v13 reference gate.

This helper does not import, deserialize program details for display, cache
private data, resolve `drafts`/`custom`/`cycles`, execute native JS, grant an
engine backend permission or enable Workout. There is no saved-program picker
in the Android preview. The test suite uses only invented data and canonical
`mergeStores` outcomes, including equal/newer tombstones and resurrection.
For future Android use the snapshot provenance, device-safe reading, complete
data lifecycle, and native DTO decoding still require independent acceptance.

## Source-bound program structure transport (no app import)

The **inactive** `read-only-program-transport.mjs` projects only bounded
saved-program metadata from exact canonical **v13** bytes. The caller supplies
a `ReadOnlyProgramReference` for the same current snapshot. The canonical
`parseStoredData` validates the source; exact snapshot SHA-256, saved-program
edit clock and tombstones are rechecked before projection.

The payload contains: read-only/source/schema markers, exact snapshot
reference, program ID/name, goal/custom status/weeks, and ordered day IDs,
labels and stable exercise IDs. It contains **no working loads, rep targets,
RIR, history, personal records or write instructions**. Ambiguous or malformed
structure fails closed; unrecognized layouts are not guessed or normalized.

`domain-contract/ReadOnlyProgramTransportCodec.kt` independently checks
the exact keys, all scalar types, bounds, source and expected reference.
The same wholly synthetic v13 snapshot and wire envelope are tested by Node
and Kotlin via `src/test/resources/read-only-transport-golden.json`.

**Remaining barriers:** native source acquisition/import consent, authentic
snapshot provenance, current-store revision/tombstone revalidation on device,
lifecycle refresh, access controls, privacy review and full device acceptance.
The app does not call this code, show personal data, store the snapshot or
unlock workout execution. Do not use this diagnostic payload to drive
prescriptions or analytics.

## Read-only native Plan presentation gate

A separate Kotlin `ReadOnlyProgramPresentationPolicy` checks the DTO against
an independently provided current reference and tombstone clock. Only its
`Structure` result may reach the shared `SavedProgramOverviewPanel` Compose
component, which shows the saved plan name, goal, weeks and day/exercise counts
without guessing exercise labels or training prescriptions. The installed
preview supplies no source or current-reference evidence, so `PlanScreen`
continues to show the explicitly illustrative `WorkoutOutlinePanel(null)`.
This is **not** device data-import certification. The future authoritative
data-source adapter must verify the current store on each display/resume.

## Native document picker: byte screening, not import

The Compose Plan preview has an **explicit, user-initiated Android document
picker**. The selected provider stream is read on Dispatchers.IO in 32-KiB
chunks, rejected when empty, stalled or larger than **50 MiB**, fingerprinted
with SHA-256, and closed. No file bytes, name, source URI, persistable URI
permission or raw error are saved. Compose holds a temporary byte count and
short fingerprint until cleared or the screen is recreated.

This is deliberately NOT canonical JSON validation. Because the native
preview does not run the trusted v13 JavaScript parser, screening does not
authorize the program presentation gate or any workout action. Future work
must certify a read-only canonical parser on device, consent, safe source
lifetimes and current revision/tombstone verification before showing user
programs; do not implement an independent Kotlin merge.

## Future raw byte provenance gate (unconnected)

`ReadOnlyBackupSourceGate.exactText` is a pure Kotlin helper that can
compare a second in-memory copy against the *original* user-selected
`UnverifiedBackupFingerprint` before submitting it to the canonical JS
schema probe. It checks exact byte count and SHA-256, enforces a 128-KiB
small-source proof cap and 65,536-character transport cap, rejects
malformed UTF-8 instead of silently replacing bytes, and refuses UTF-8 BOM.
No backup is opened by this helper, and there is no app call site yet.

A matching fingerprint is only evidence that two copies agree; it is
not source authentication or user consent for data import. The file picker
still closes the original copy after fingerprinting, so later integration
must explicitly design a safe source lifetime and clear user intent.

## Explicit small-backup validation interaction (still unconnected)

`ReadOnlyBackupCandidateReader.screen` scans a selected stream once,
streaming the full SHA-256 and keeping **only a complete <=128-KiB candidate**
temporarily. Large sources still receive their complete SHA-256 up to 50 MiB
but **never** create a partial JSON candidate. The stream closes on failure
or success. Kotlin unit tests check exact-threshold behavior, matching
fingerprints, source mutation, over-limit and stalled streams.

`BackupCopyScreeningPanel` now separates user actions: always-available
fingerprint, plus an optional explicit **Validate small backup structure**
action only when a canonical verifier is injected. Small bytes are sent
off the main thread and zeroed in a finally block. Compose state holds
only the hash/count and validated collection counts; no URI or snapshot.
Unsupported or rejected sources never activate program presentation.

The shipping preview intentionally supplies `verifier=null`. Next
milestone: package a verified/pinned canonical JS bundle and attach an
app-scoped read-only inspector; ensure failures, interrupted sessions and
config changes never introduce write access or source retention.

## Explicit saved-program backup inventory (not current Plan)

The isolated `backupProgramInventory` method lists only **active saved
entries inside the selected v13 backup copy**. It validates all program IDs
and edit clocks, checks source tombstones, and returns bounded names, day
counts and exercise-slot totals. An equal/newer deletion clock removes the
entry; duplicate IDs, malformed metadata or over-24-program collections
reject the whole read. The metadata remains an ephemeral Compose selection
until cleared; raw backup bytes are zeroed after use.

This is intentionally distinct from the inactive *current-reference* saved
program gate. A snapshot fingerprint cannot establish a currently active
program. No snapshot rows are adopted, no native workout targets generated,
and the web store is unchanged.

### Expandable backup-only day outline

The read-only backup inventory can now expand a saved program into its
**source-backed day labels and exercise-slot counts**. Each day is bounded,
the Kotlin codec verifies that all per-day counts sum exactly to the program
total, and no exercise IDs, set counts, loads, RIR, history or target
prescriptions cross the wire. Missing optional labels are displayed simply
as `Day N`, never presented as inferred workout names. M3 cards use a
single disclosure action; no activation, set logging or native writes.

## Larger bounded snapshot intake — 1 MiB, not full import

The strictly read-only JSON inspection path now accepts **complete selected
copies up to 1 MiB**, a significant increase from the former 128 KiB proof.
Kotlin source consistency requires the same exact bytes and SHA-256 digest,
rejects malformed UTF-8, BOM and limits content to 1 MiB decoded
characters. Both schema and inventory RPC requests are capped at **4 MiB
encoded JSON**, with independent Java and JavaScript guards. The ordinary
fingerprint-only path continues streaming files up to 50 MiB; anything above
1 MiB is fingerprinted but **never** truncated and passed to the canonical
parser. The 4 MiB wire limit can also reject a source smaller than 1 MiB
when JSON escaping expands it — this fails closed, not a silent reduction.

This is a deliberate memory and time bound; it does not certify typical
large backups, enable a local data store, or import training histories.
On-device larger-source acceptance must be separately measured before
widening the limit again.


## Explicit checked-deletion and coherent current-Plan gate (not connected)

The native Plan presentation no longer interprets a null tombstone clock as a
verified absence. It requires current-store evidence explicitly marked either
CheckedAbsent or CheckedAt(clock), independently of the decoded program DTO.
Only the policy can construct the approved read-only presentation within the
domain module; Compose cannot self-authorize arbitrary program summaries.

A future authoritative current-store adapter must read reference and deletion
evidence, project only read-only program structure, and fetch evidence again.
The CurrentReadOnlyPlanGateway rejects changed revisions, changed tombstones,
missing records, failures and invalid IDs; cancellations propagate. Presentation
must refresh on resume and canonical store changes. Any later workout mutation
requires its own independent current-revision check.

The installable native preview still supplies no current-store adapter. Its Plan
remains illustrative; offline backup fingerprints and inventories are NOT active
training programs. No backup import, history writing, merge algorithm, workout
execution or web production change is introduced by this contract.


## Ephemeral native Plan lifecycle gate (no personal store attached)

ReadOnlyPlanPresentationSession immediately clears previously approved Plan
structure on background, navigation away, switching IDs, canonical store change
notifications and new refreshes. A generation ticket rejects delayed or
out-of-order results. Synthetic tests suspend source projection and then
resume after an invalidation, ensuring old results cannot reappear.

The Compose host listens for ON_START, ON_STOP and ON_DESTROY. Its transient
presentation does not use rememberSaveable or persistence. Leaving the Plan
destination drops the current program ID; a new storeChangeKey recreates
evidence. The preview supplies only UnconnectedCurrentReadOnlyPlanSource
and a null activeProgramId, so this does not display a real athlete's plan.

An eventual authoritative adapter MUST emit all canonical store revisions
and independently verify the current record and deletion clock. This host
cannot detect changes the source fails to report; training actions will
still require independent current-revision checks. No import, storage write,
personal workout activation or distributable Android build is introduced.

## Detached, non-forgeable read-only Plan presentation

The approved current-Plan wrapper is intentionally a normal class, not a
Kotlin data class: consumers cannot use a generated public `copy()` to forge
approval without calling the current-revision policy. The policy copies
source-owned day and exercise collections before validation, wrapping the
new lists in unmodifiable containers. An external mutable source list can
change later without altering already-approved UI structure. This is a
display-only defense, not authorization for exercise logging, prescription,
or backup activation.

## Mandatory canonical source-change observation (inactive native adapter)

Every future `CurrentReadOnlyPlanSource` must now register a disposable
`subscribeCanonicalChanges` listener. The source must signal each committed
canonical revision, tombstone, merge, user sign-out or source replacement.
Compose invalidates the read-only Plan session immediately when a signal
arrives, forwards the signal to its main-thread state via a conflated channel,
and reruns current-store evidence checks. If registration fails, Plan remains
Unavailable rather than displaying unmonitored personal structure.

The subscription is released when its Compose host is disposed. This
contract does not create a current canonical store, assume a backup is live,
or enable program edits/workout execution. The existing externally supplied
`storeChangeKey` remains an additional invalidation path.

## Plan-only observer lifetime and pre-render authority check

The Compose host subscribes to canonical store notifications only while a
non-null selected Plan ID is active. Navigating to Today, Train or Insights
disposes the observer and clears private presentation state. Each rendering
pass also verifies that the transient session still holds the exact same
approved presentation object; a source update that invalidates the session
therefore cannot be reused by a subsequent recomposition while the callback
is being delivered to Compose's main thread.


## Canonical current-Plan projection transport adapter (not connected)

The new CanonicalCurrentPlanSourceAdapter connects a future certified
CanonicalCurrentPlanReadPort to CurrentReadOnlyPlanGateway. The port returns
a current-store reference including exact digest and program revision,
an explicitly checked tombstone, and a bounded canonical JavaScript
read-only program transport. The adapter checks revision and deletion,
decodes through the strict ReadOnlyProgramTransportCodec, independently
policy-inspects the result, and delegates committed-change subscriptions.
The gateway re-fetches current evidence before and after projection.

No current store port implementation exists in the native app. The adapter
has no storage access, and does NOT authenticate bytes, import an offline
backup, update a program, merge stores, or enable workouts. A future native
device-certified canonical owner must implement the port and ensure exact
source-byte trust, current revision, and all change notifications.

Kotlin tests use the same wholly synthetic v13 wire fixture as canonical JS,
including deletions, revision drift, malformed/writable payloads, missing
records, subscription release, failures and cancellation.


## Exact-byte SHA gate for a future current-store owner (not connected)

ExactCurrentSnapshotProjectionPort accepts a future authoritative owner that
transfers ownership of a complete current v13 store byte copy, and a
device-certified pinned canonical JavaScript projector. It makes a bounded,
private in-memory copy, rejects empty, over-1 MiB, BOM and malformed UTF-8,
hashes the exact bytes, requires the projector's reference SHA-256 to match,
then clears both byte buffers on every return, error or cancellation.
The projector must be the real canonical JS source, not a Kotlin schema fork.

This closes a digest-provenance gap at the transport boundary but does NOT
authenticate the source owner, certify Android JavaScript execution or make
an offline backup the current store. Both interfaces are unimplemented in
the preview. Synthetic contract tests verify success, tampered source bytes,
size/encoding rejection, cancellation cleanup and subscription teardown.
The result remains read-only structure, never prescriptions or log permissions.


### Shared browser-safe current saved-program projection (not connected)

The exact canonical saved-program structure mapping is now owned by
android-native/engine-bridge/current-program-projection.mjs. Node's
read-only-program-transport.mjs authenticates its SHA-256 reference using the
Node inspector and delegates to the SAME projection function. The bounded
Android bridge receives the exact current snapshot and requested stable ID,
and returns an envelope for the strict Kotlin codec. Kotlin and Java both
recompute SHA-256 of the supplied UTF-8 source. The existing
ExactCurrentSnapshotProjectionPort independently checks the authoritative
store owner's bytes against the returned reference; CurrentReadOnlyPlanGateway
then checks before/after evidence and deletion state.

The Android current store owner remains UNIMPLEMENTED and the source/projector
are NOT connected to MainActivity. The backup picker cannot supply this path.
No real Plan view, workout prescription, history writes, or release readiness
is implied by these wire tests.
# Finished native journal to canonical history (integration staging)

`workout-history-completion.mjs` is a pure Node integration boundary. It prepares
one canonical v13 history/store candidate from the exact original store bytes,
the current store bytes and a finished format-2 private journal. It performs no
file, network, Android or web-store writes. It is not a backup importer and is
not exposed through the read-only Android engine channel.

Run `node android-native/data-migration/workout-history-completion-test.mjs`
from the repository root. All inputs are invented. Kotlin consumes the same
finished-journal fixture, including out-of-order user logging, skipped set 2,
and unreported RIR. The test also follows generated Full Body, UL and ULPPL
programs through canonical history analysis and next-workout suggestions.

The native current-store owner, atomic canonical history publication, verified
program/session creation and actual user-facing logging are still pending.
Keep the originating snapshot until a history commit has been acknowledged;
never clear recovery just because preparation returned a candidate.
