# Native migration checkpoint — 2026-11-01

Branch: `architecture/android-compose-foundation`. APK production stays frozen.

The source journey covers onboarding, generated review and explicit program
save, engine-issued working sets and myo/drop extensions, durable logging,
interruption recovery, active-set correction, canonical completion, one-shot
deload consumption and saved-history corrections. History browsing includes
name/date filters, local-calendar shortcuts and a complete month calendar linked
to saved-workout review and correction. This is source verification, not device
acceptance.

## History browsing

Insights now browses owned native completions in pages of 12, newest first.
Each row carries its workout identity, original date, day/program labels and
canonical set/volume summaries. Native tombstones remain hidden, duplicate
identities fail closed, and earlier journal-only entries expose a summary when
editable set observations are unavailable. Web history is not imported here.

A page cursor is bound to the exact source snapshot. The pure canonical JS
reader and Kotlin codec check its identity, bounds, order and continuation.
`NativeHistoryReadPort` rechecks the owned bytes after asynchronous engine
projection and wipes its read buffers. The runtime also checks the store-change
version before installing read results. A read overlapping completion is dropped;
it cannot replace newer history with a late response.

Opening an older workout selects its own detail and correction context without
replacing the latest completion receipt. Older corrections use the existing
atomic canonical owner and refresh the page at the same chronological offset.
Changing a weight cannot move an old workout to the top, replace a newer Last
result or advance the workout rotation. All-time working-set records refresh
from canonical history. Pages/details may be read during an active workout;
saved-history mutation still waits until Finish. Canonical store writes and
history reads are serialized, with a visible loading label when Finish is
temporarily waiting.

History pages and workout details have distinct scroll contexts. Navigation
opens at the correct heading while rotating within the same context preserves
its saved scroll state. Insights now presents real history instead of repeating
unconnected feature-description cards.

## Find saved workouts

The history browser searches program and day names with bounded, case-insensitive
literal terms. Date ranges include both selected local calendar days. The native
date picker uses UTC day labels, which are converted into device-local start and
exclusive end instants; a day can therefore cover 23 or 25 hours. Search and date
criteria are applied together by the canonical JS reader before pagination.

Every page echoes the exact filter, filtered count and total saved count. Kotlin
checks the requested filter and date bounds in addition to the source snapshot,
page identity, order and continuation. The read port also rejects a typed result
with a different filter. Empty search results expose the saved count without
claiming the device has no history.

Quick date chips apply a read-only, name-combinable filter immediately. Their
local calendar windows include 23- and 25-hour daylight-saving days, and
This month includes its whole final day. Search/date drafts survive rotation. Apply, Use dates and Clear filters issue
reads; typing does not issue requests. Applied filters persist through paging,
detail review, reload, correction and the next workout's completion. A new query
starts on the first page with its own scroll context. This is read-only query
state; it does not change training data, progression or the latest receipt.

## Active and saved-set correction owners

Active corrections append receipts naming the exact prior operation. The
completed-slot projection resolves each slot once; stale replacements and
malformed or repeated durable events fail closed. Unedited journals retain
format 2; edited format-3 journals retain the full original audit. Before an edit,
the journal reserves enough bytes for all remaining observations and Finish.
Canonical completion publishes latest observations plus `nativeSetRevisions`
before retiring recovery. Targets and working-set positions remain pinned.

Saved-history corrections retain original targets, extensions, skips, omitted
zero-load observations and completion identity. `normalizeEditedHistoryEntry`,
`perfAfterHistoryReplace` and `exRecords` remain canonical owners. The native
writer publishes corrected history and the completion receipt's new snapshot
hash atomically, with exact operation/request retry identity. Both correction
surfaces share `NativeSetEditSheet` and numeric inputs, keyboard scrolling and
dismissal protection while awaiting a save. No training formulas moved to Kotlin.

## Verified locally for this pass

- 110 domain JVM tests and 21 packaged-runtime JVM tests passed, with no skips.
- Compose Kotlin and instrumentation Java source compilation passed.
- Native filtered history-page/correction, active-edit, protocol, canonical
  fixture parity, sandbox isolation and UI source gates passed.
- Canonical export, independent source parity, snapshot identity and the actual
  packaged IIFE proof passed. Canonical training modules and the web release
  package remain unchanged (source candidate 1010).
- APK-freeze checks passed; no APK was produced.

The JVM journey exercises four real native completions, filtered multi-page
history, empty results, legacy summaries, forged filter rejection, read-only
access during a pending workout, correction of an older false high, retained
newer Last/receipt/cursor state, a delayed filtered page overlapping completion,
cancelled reads and reopening the saved store. Date tests cover spring/fall
daylight-saving changes, leap day, opposite time-zone offsets and local midnight.

Required commands include
`node android-native/engine-bridge/native-history-page-test.mjs`, the existing
native-history/workout/protocol suites and the four source-only Gradle tasks:
`:domain-contract:testDebugUnitTest`, `:engine-runtime:testDebugUnitTest`,
`:app:compileDebugKotlin`, and
`:engine-runtime:compileDebugAndroidTestJavaWithJavac`.

## Publication boundary

The user explicitly approved publishing the correction commits to
`extracteddeath/Pursuit-Iron`, branch `architecture/android-compose-foundation`,
on the condition that the other programmer's native app and the web app stay
unchanged. Publication is limited to this development branch. Preserve every
other branch, including `main`, `native-app` and `native-smoke`; do not merge,
deploy or produce an APK. The live GitHub Pages deployment is sourced from
`main`. Branch heads and live index/service-worker/app/records bytes are checked
before and after publication. The verification results above are local results;
GitHub CI results must be checked independently for the pushed revision.

## Follow-up: calendar-day shortcuts

Four Material 3 filter chips now use local calendar periods, converted through
the existing exclusive-end canonical history query. The canonical reader still
owns matching, pagination and source-revision checks; there are no changes to
history, workouts or training progression. Added JVM regression coverage for
both DST changes, a leap month and a skipped date-line calendar day. This
follow-up requires fresh CI source-only verification; the APK freeze remains.

## Follow-up: confirmed skip of remaining exercise sets

The real native Train workspace now exposes one explicitly confirmed action
to skip the unfinished sets of the selected exercise. It does not overwrite
already logged or corrected sets, issue replacement targets, alter the
exercise roster, or change the next workout. It uses the current canonical
session journal and appends one acknowledged SKIPPED receipt per unresolved
slot, including engine-issued myo/drop extension slots. The UI reports only
persisted receipts, blocks competing actions while writing, and recovers
partial progress after restart or a failed write. A second invocation skips
only still-unresolved slots. The existing canonical completion path interprets
each skip as a skip, not as performed repetitions.

This is a manual interruption workflow, not automatic time trimming or
injury-aware engine adaptation. The separate session-adjustment controls
remain demonstrative until canonical policy integration is certified.
JVM and interaction regression cases were added; verification depends on
the new commit's CI run. No APK, web, or other native branch changes.

## Follow-up: canonical shortened-session previews

Native Today now offers read-only 45-, 30- and 20-minute previews for the
next workout of a genuinely saved native program. The JS bridge calls the
existing `modules/engine-api.js:planShorterSession` policy, which protects
strength protocols, preserves set/rest priorities and reports whether the
requested time budget fits. The worker receives only a checksummed owned
snapshot. Kotlin decodes a strict result, rejects forged identity/bounds,
and the process runtime drops results after a store revision change.
Cut rows and any fit warning are displayed with an explicit **preview
only, not applied** statement. Starting Train still issues the full session;
history and progression are unchanged. Real application requires separately
certified pinned issued targets and time-limited progression provenance.
The packaged-runtime JVM test also checks all three preview choices through
the actual worker and rejects a different program; a stale response cannot
appear while the next full workout is opening.
No APK or production web code is changed.

## Follow-up: shortened issuance and canonical history provenance

The isolated Android workout-store bridge now prepares source-issued shortened
sessions using the original canonical `planShorterSession` policy, with a versioned
issued record. Full sessions retain the original version-1 schema and byte
handling. Shortened issued workouts carry the full canonical length plan,
only the retained exercise slots and exact prescribed warmups, loads and
extensions. The pending owner pins this issued JSON to the same atomic
pre-history journal, and recovery reuses those bytes rather than re-prescribing.

Before completing a shortened session the source bridge revalidates its
shortening plan against the pinned original canonical snapshot, including
eligible exercise roster, set counts and extension protocols. Canonical
history uses the original engine performance summarizer with the shortened
day's dose and publishes `sessionLengthPlan`, `prescriptionEdited` and
`timeLimited` from that approved plan. The source web engine and existing
history are not modified. This prepares execution without yet exposing
shortened-session starting in native UI until integration tests are green.
The actual Today flow now offers a separate Start shortened workout action
only for a feasible preview with real reductions. The runtime rejects starts
from an expired preview by rechecking the current owned snapshot hash and
saved program before issuing the format-2 plan. Train displays its selected
time and provenance. No UI-side set calculations or source web changes.
The packaged integration suite also exercises shorten → journal → Finish →
canonical history and retry; device certification remains required. No APK built.

## Follow-up: non-mutating affected-muscle review

Train now offers an explicit "Review muscle involvement" action for the active
engine-issued workout. The canonical JS worker resolves each exercise's primary
and secondary muscle involvement using `EX_BY_ID` and `secondaryOf`, bound to
the exact retained issued plan and owned source SHA. The verified transport and
Kotlin decoder reject altered workout IDs, slot rosters, muscle lists and
unexpected write instructions. Compose shows selectable muscle chips and
potentially related exercises. Selecting an exercise only navigates to its
existing, explicitly confirmed skip action; the review never skips sets,
changes targets, asserts an injury diagnosis or changes the saved program.
This is a safe initial native injury-awareness layer, not an automatic injury
adaptation engine.

## Follow-up: confirmed related-muscle skips

After reviewing primary/secondary involvement, Train can now explicitly
confirm skipping all remaining sets related to one selected muscle. The
confirmation names affected exercises and the exact outstanding set count.
One shared runtime writer services both single-exercise and muscle-wide
skips using journal CAS receipts; completed and corrected work is preserved.
For muscle-wide selection the Kotlin boundary checks the canonical review's
workout ID, reviewed exercise IDs/indices, and every pinned journal slot.
An interruption can persist only a subset of skips; recovery and retry
select only unresolved slots. Every saved skip remains SKIPPED in canonical
history, not a completed working set or an invented failure.
No injury diagnosis, persistent injury-report reason, automatic session
reshaping or future-plan change is claimed in this pass.

## Follow-up: deterministic, confirmed reduced work for affected muscles

Users can now choose a **reduce planned work** action for a muscle identified
by the canonical issued-workout review. Selection keeps the first half
(rounded up) of *originally issued* sets in every related exercise and offers
to skip only the unresolved late-set tail. It is not half of a changing
pending count: crash/retry cannot cause repeated 50% reductions. If a set
in the tail was previously logged, that original completion is retained.
All reduction skips use the same ordered, atomic CAS journal writer as
per-exercise skips, and the UI previews exactly the policy-selected count
before an explicit second confirmation.

This step is a session-scoped, user-controlled dose reduction. It does NOT
yet record a durable injury reason, auto-diagnose symptoms, adjust new loads,
or mark the original canonical prescription as modified. Such metadata
needs a versioned, recovery-safe journal/history protocol instead of
silently inventing annotations in Kotlin.

## Follow-up: versioned, durable adjustment provenance

The native journal now supports **format 4** alongside the unchanged format-2
and format-3 representations. A user-confirmed muscle-wide skip or reduced
workout records a bounded `{action,part}` adjustment on each SKIPPED receipt.
Plain exercise skips and user-completed sets remain unannotated. The private
journal's SHA/revision CAS, lost-ack retry and correction chain preserve these
annotations without inventing diagnoses or changing the program.

The dedicated canonical completion bridge independently verifies every
annotation against the originally issued exercise's primary/secondary muscle
involvement, and publishes `nativeSetAdjustments` for effective tagged skips
and a `nativeSetRevisions` audit record. The transactional Kotlin owner
validates BOTH the canonical annotation list and audit against its own
durably recovered journal before allowing history to be committed.
Older journal formats and nativeSetObservations remain unchanged.
It is session-scoped user-selected context, not a longitudinal medical log.
The web app and global engine stay untouched.

## Follow-up: repeated-exercise history and original Last positions

New issued completions publish version-1 `nativeSetLocations`: each retained
canonical performance row links to its exact original exercise/set slot. The
canonical logger still owns row retention, zero-load omission, summaries and
progression. The Kotlin atomic owner independently verifies locations against
the pinned issued targets and durable observations before publishing history.
Identical myo/drop values cannot silently move between repeated exercises.

Saved-history review now groups repeated exercises by their workout position.
Working and extension sets can be corrected independently, including merged
performance indices beyond 19, within the existing 480-slot workout bound.
Corrections preserve original locations, targets, adjustment/audit records and
the engine's duplicate-exposure comparability flags. Existing ambiguous legacy
repeated workouts remain summary-only. A completion committed before location
metadata existed can still acknowledge its exact handoff after restart without
rewriting the saved entry or snapshot bytes.

The related native Last projection now carries original working-set positions.
Skipped and omitted sets leave gaps rather than borrowing the following set.
Repeated exercises select their own program/day/slot observations. Extensions
remain outside Last, and old pinned issued arrays still recover unchanged.
No prescription or progression formulas moved into Kotlin.

Local verification passes all 40 native/canonical source suites plus canonical
export, independent source parity and the real Android IIFE. The history suite
also runs against that actual bundle in source-only CI. Added JVM coverage for
large repeated-lift corrections, forged location rejection before publication,
post-commit crash/retry, a full rotation back to correct Last positions, sparse
Last transport and legacy issued recovery. Fresh GitHub Kotlin/Compose/runtime
checks must be read for the published revision; this is not device acceptance.
No APK or production web release is produced.

## Follow-up: restoring and skipping saved observations

New issued completions retain version-1 `nativeHistoryBasis` evidence for every
original exercise occurrence. Its per-exercise metadata comes from the canonical
logger; only skipped or omitted rows need additional set templates. Completed
rows reuse their retained targets, avoiding a second copy of every decision
trace. Template preparation strips placeholder actuals and applied values before
publication. The Kotlin atomic owner verifies each missing template against the
pinned issued target and requires complete, unique original-slot coverage.

Saved history can now restore an originally skipped set, correct a completed
zero-load row omitted from the performance summary, or explicitly mark a
completed working/myo/drop set as skipped. The native editor shares the existing
numeric controls and busy/dismissal protection. Skipped sets start with blank
actual-value fields. A lost acknowledgement retries the identical command and
timestamp. Marking completed work as skipped requires confirmation. The last
working set cannot silently remove completed extensions.

Format-2 history correction commands identify original journal slots, independent
of changing performance-array indices. Canonical `normalizeEditedHistoryEntry`,
`perfAfterHistoryReplace` and `exRecords` still own summaries, Last and records.
Original dates, completion identity, targets, rotation, saved programs and the
active-workout audit stay intact. `nativeHistoryRevisions` appends bounded
before/after receipts. Restoring an adjusted skip clears its effective adjustment
while retaining the original adjustment audit; a later plain skip does not
reactivate an earlier reason. The Kotlin owner checks requested observations,
unchanged targets/audit/other store owners and exact retry identity before commit.

Legacy completions without this basis retain their prior value-only editor.
They are never assigned retrospective targets. A completion already committed
before the new annotation can still acknowledge its exact handoff without
rewriting bytes. Zero-rep failed-attempt history editing remains separate.
Existing failed-attempt completion evidence remains preserved.

Local verification passes all 41 native/canonical source suites and the actual
Android IIFE, including independent canonical source/export parity. New source
cases cover fully skipped workouts, omitted zero-load rows, working/myo/drop and
repeated occurrences, full exercise removal/restoration, older edits with newer
Last/PR results, audit retention and malformed requests. JVM tests add disk-full,
post-commit crash/restart/retry, active-workout write rejection and coherent
transport tampering of targets, observations, audits and saved programs. Read
fresh Kotlin/Compose/runtime CI for the pushed revision. No APK is produced.

## User direction: integrated native system, then full Kotlin rebuild

The user explicitly wants the whole engine rebuilt after the native system meets
fully integrated acceptance standards. The current engine has reported training
and programming defects. JS parity is integration/compatibility evidence only;
it must not be used as the sole correctness oracle for the replacement.
`ENGINE_REBUILD_PLAN.md` records the integration gate, complete domain scope,
independent regression requirements, data migration boundaries and rebuild order.
The full engine replacement has not started. Integration and Android acceptance
remain incomplete; required features cannot be counted complete from demo UI.

## Follow-up: factual zero-rep attempts through the native journey

Fresh GZCLP-issued working sets carry an explicit failed-attempt capability.
Issuance decoding checks it against the owned source program. Live entry, active
correction and the runtime writer all enforce the pinned per-slot capability and
a positive load. Extensions and ordinary programs do not acquire it. A zero-rep
entry is labeled as a failed attempt, with zero successful repetitions.

New version-2 `nativeHistoryBasis` annotations pin the same source capability at
completion. The atomic owner checks it against the original source snapshot;
format-1 bases and previously issued bytes remain readable. An old committed
completion still acknowledges its original handoff without a rewrite.

Native history now reads and corrects failed attempts, including changes among
failed, successful and skipped observations. Original working positions, targets,
active audit, correction receipts and completion identity remain intact. Failed
attempts contribute no invented successful rep, tonnage or working-set PR.
Last displays the zero-rep attempt with its original position. Summary counts use
"recorded" to include resolved attempts without calling them successful work.

The existing canonical positive-rep edit helper drops zero-rep rows. A bounded
native evidence normalizer preserves verified failed facts around that helper;
positive summaries remain canonical, and an all-failed summary retains zero reps
and the heaviest actual failed load. It makes no prescription/progression choices.
The web engine modules and release package are unchanged. This data adapter is
part of integration, not the planned Kotlin engine rebuild or certification of
the current engine's training rules.

Local verification passes 44 native/canonical and targeted regression suites,
plus the actual Android IIFE with the canonical source digest unchanged.
Source/IIFE coverage checks kg/lb, mixed and all-failed work, extensions, repeated
occurrences, original targets, legacy acknowledgement, positive/skip/failure edits,
invalid permissions/zero loads and next-session replay. JVM coverage adds issued
permission checks, active logging/correction, restart, lost completion/edit
acknowledgements, saved corrections, no phantom PR and a full rotation back to
the failed lift. Read fresh CI for the published revision. No APK is produced.

## Follow-up: complete local-month history calendar

Insights now switches between the existing filtered list and a Material 3 month
calendar. Available dates contain saved workouts; selecting a date shows all of
its workouts through the same summary cards and revision-bound review action.
Program/day-name search applies to the visible month. Its drafts and the selected
day survive rotation and a trip through workout detail/correction. Calendar
search and list filters keep their own browsing context.

The existing canonical page reader supplies every matching row before any day
is exposed. A month can span more than one 12-workout page. Kotlin groups factual
dates for presentation only; it introduces no new JS bridge method, history
writer or training rule. Every page must agree on revision, exact local-month
filter, count, offset, ordering and continuation. Missing, duplicated, malformed
or conflicting pages reject the whole calendar. The owned snapshot is checked
again after the final page, and detached read buffers are wiped.

Local date boundaries use the established UTC-picker-label conversion, including
23/25-hour days, leap years and opposite zone offsets. Repeated local clock hours
remain on the same calendar day. Corrections and completion invalidate old
calendar data and reload the pinned query; a late result overlapping a store
change is dropped. Opening an older day does not replace the latest completion
receipt or advance workout rotation. Legacy summaries stay explicitly summary-only.

All 31 native source suites pass locally. Added JVM cases cover complete
multi-page and 2,000-workout months, empty/search
results, local-day grouping, changed source bytes, cancellation and forged page
metadata/content. The real packaged-runtime journey also browses all 29 saved
workouts, edits an older workout, checks refreshed dates/counts/volume, rejects a
calendar overlapping Finish and reopens the resulting 30-workout month. Read
fresh CI for the published revision; Android interaction acceptance remains open.
No APK is produced, and the current canonical training source is unchanged.

## Follow-up: validated saved-data backup export

The native Plan screen now offers Save backup through Android's CreateDocument
Save As flow. The system selects a new JSON destination; no selected import copy
is overwritten or promoted into the current store. Picker cancellation writes
nothing. A process-owned job continues across rotation after a destination is
returned. Only status messages, never source bytes or document URIs, enter UI state.

The exporter reads detached owned v13 snapshot bytes, checks UTF-8/bounds and the
existing pinned canonical parser, then rechecks ownership before opening output.
It writes exact bytes in bounded chunks, closes the stream, reopens the chosen
document and compares every byte plus its complete length. It rechecks the owned
source again before success. A changed revision, write/flush/close/readback error
or cancellation cannot report a saved backup. Detached/readback buffers are wiped;
an unsuccessful export attempts deletion of the new incomplete document. A
provider can refuse cleanup, so the UI tells the user to discard an unconfirmed copy.

An active/blocked workout prevents export. Starting a workout and editing saved
history wait during export; the existing history interaction gate also protects
an open correction sheet. Export includes the complete canonical saved-data
snapshot, including native observations, original targets and correction audits.
It excludes the private unfinished journal/receipt envelope and does not write
the canonical store or implement import, restore, merge or Undo.

All 31 native source suites pass locally. Added JVM cases cover exact
Unicode/chunked bytes, invalid or rejected sources,
source changes, corrupt/truncated/appended provider results, output/read failures,
cancellation, cleanup failure and buffer wiping. The actual packaged-engine
failed-attempt journey exports its corrected owned snapshot, checks canonical
validation and history identity/observations, and verifies zero additional store
writes. Read fresh CI for the published revision. Android document-provider and
interaction acceptance remain open. No APK is produced.

The rewrite plan now groups the remaining work into data migration/recovery,
programming, live training, insights, personal settings and final Android
acceptance. It separates baseline integration from the independent training-rule
correctness tests required of the rebuilt engine. Existing engine bugs remain
tracked rebuild requirements rather than a prerequisite to fixing that engine
before it can be replaced.

## Next work

Safe restore/merge/Undo and existing web-data migration, custom programming and
cycles, full workout tools/readiness, real-data analytics/bodyweight, and complete
gym/profile preferences remain integration workstreams. Physical-device
interaction, interruption and accessibility
acceptance is still required for the complete agreed journey; preserve the APK
freeze until its release gate has the required evidence.
The full Kotlin engine rebuild follows the integrated-system gate in
`ENGINE_REBUILD_PLAN.md`; current parity success does not close that gate.

## Follow-up: redacted canonical backup merge preflight (no import)

The isolated Android bridge now exposes `previewBackupMerge` solely on the
private workout-store preparation channel. It accepts **two separately bounded
exact v13 snapshots** (the owned current store and an explicitly selected
backup), verifies the existing canonical parser, rejects duplicate/invalid
record IDs and deletion clocks, and evaluates the unmodified canonical
`mergeStores` policy in memory. It returns only source fingerprints, counts,
hypothetical size, conflict count and explicit history-cap/capacity warnings.
It never returns proposed merged records or writable snapshot bytes.

The Kotlin boundary independently checks both full UTF-8 source hashes,
protocol/version, strict result keys, bounded counts and candidate size,
risk flags and the permanent `approvedForImport=false` markers. The sandbox
method is **not wired to a document picker, Compose, or a canonical store writer**.
No import, merge, Undo, migration of old web backups or recovery of an active
journal occurs in this pass. Current JS data ownership and both other apps
remain unchanged.

The Node test covers correction clocks, tombstones and resurrection, real
2,000-workout history-cap loss, source bounds, private-channel isolation and
redacted results. Kotlin tests cover forged approval, fingerprints, flags,
counts and request bounds. Remote CI and Android device acceptance must be
checked separately at the exact final head before calling this stage certified.

**Next actual integration step:** hold chosen import bytes in a process-scoped
owner, read and fingerprint a provider copy, run canonical v13 inspection and
the two-snapshot preflight against an immutable current-store revision, show
conflicts and risks for user confirmation, and explicitly refuse import until
an atomic Undo/rollback and revision/tombstone-safe publication path has its
own crash/retry tests. Never imply this diagnostic is safe to publish merely
because its risk list is empty.

## Follow-up: backed-up program comparison visible in native Plan

The native Plan screen now offers an explicit **Compare backup with saved data ·
preview only** document picker once a saved program and the packaged canonical
runtime are present. The selected document is read through the existing bounded
read-only provider stream; no picked URI, raw backup data, or merged snapshot
is retained in Compose. Selected bytes are cleared on completion or failure.

The process-owned runtime reads the exact active store, fingerprints both
sources, submits the two snapshots through the typed private canonical
`mergeStores` preflight, checks for an intervening owned-store revision, and
reads the current bytes again before releasing only a redacted summary.
An active workout or competing history/export task refuses the comparison.
The view labels hypothetical program/workout counts, revision conflicts,
history cap loss and native capacity violations. It is not an import decision,
does not preview every scalar settings conflict and cannot restore records.

A new source interaction test requires Activity -> PreviewApp -> Plan ->
document picker -> process owner -> strict decoder wiring and source wiping.
The packaged real-runtime test also checks no canonical store writes.
Physical document-provider and lifecycle acceptance, atomic merge/Undo and
web-backup migration are **still open**. The latest CI head needs confirmation,
and APK freeze remains active.

## Follow-up: no-loss candidate preparation and private atomic Undo transaction

The previous two-snapshot Plan comparison now reports **existing records
removed, existing records replaced and personal settings changed**, separately
from the canonical merge's generic collision count and history-cap losses.
A deletion tombstone can otherwise remove an original workout with no ordinary
record conflict. These new risk indicators remain redacted; no training loads,
records or selected source bytes enter Compose state.

A separate `prepareBackupMerge` method is now allowlisted **only** on the
private pinned workout-store channel. It binds two exact canonical v13 sources
and an expected candidate digest; any loss, overwrite, settings change, conflict,
oversized result or changed digest fails closed. The strict Kotlin result decoder
checks the whole candidate, hashes, version, byte count and non-writing flags.
Neither the bridge nor this decoder can publish a candidate.

The existing single-process `CanonicalWorkoutStorePersistence` now supports a
backward-compatible **format-2 atomic envelope** that can store a prepared
additive candidate and the exact prior snapshot/completion receipt for Undo in
ONE atomic file write. Existing non-restore writes continue using format 1.
The private transaction independently rechecks the canonical preview and
preparation, explicit authorization evidence, active/pending ownership and
the exact pre-await current snapshot before publication. Undo is permitted
only while the installed snapshot remains unchanged. Committed restore and
Undo retries are idempotent after lost acknowledgements, and starting a
new workout revokes pending Undo rights. Corruption cannot silently bootstrap
an empty store.

The private `installApprovedBackupMerge` and `undoApprovedBackupMerge`
methods are intentionally **not called by Compose, Plan, Activity or the native
runtime host**. No actual user-selected backup is imported. JVM synthetic
fault tests cover before/after-write crashes, restart, duplicate operations,
owner revisions, workout start and corrupt envelopes; packaged JavaScript
candidate preparation is separately verified through the pinned transport.
Read the exact-head CI reports for results.

**Still blocking activation:** a documented explicit user consent sheet,
revision- and provider-bound source ownership, recovery/Undo on real Android
storage and process death, realistic migrated web histories with 1-MiB limits,
detailed conflict-resolution controls (including deletions, settings and
custom cycles), full workflow/device accessibility acceptance. Preserve the
APK freeze. A passing backend contract is not enough to describe restore or
the Kotlin engine rebuild as production ready.

## Follow-up: restore operation replay protection and shared M3 review

The private restore transaction now records a bounded ledger of consumed
operation IDs in envelope format 3. It remains readable alongside older
format-1 canonical snapshots and format-2 Undo envelopes. A duplicate
approval cannot silently run again after Undo or a subsequent workout;
a new explicit operation ID can be evaluated against the restored current
snapshot. The ledger persists through new workout starts, history corrections
and saved completions, and fails closed at its 512-operation limit rather
than evicting older authorization history.

Both live Undo state and its durable acknowledgement are decoded from
formats 2 **and** 3. Crash/restart tests also verify the exact serialized
envelope, replay refusals, fresh approved transaction after Undo, tampered
ledger refusal and backward-format compatibility. The API is still internal
and cannot be invoked from Plan, Activity or the native runtime host.

The Plan backup comparison now uses a reusable Material 3 review component.
It displays current/selected/hypothetical counts for workouts, programs,
custom programs, cycles and gyms, with explicit risk descriptions for removal,
replacement, history-cap loss, conflicts, setting changes and capacity.
It never offers an Import or Undo action. An earlier result is discarded when
the process-owned canonical store revision changes; the owner separately
rejects selected sources that become stale during comparison.

**Activation remains blocked** pending a real explicit-consent interaction,
provider-bound repeat selection and exact-source revalidation, Android
interruption/recovery tests, conflict-resolution policy, legacy web backup
migration and whole-journey device acceptance. The installed training engine
has not been rewritten and the APK freeze remains in effect.

## Follow-up: exact repeated backup selection before any restore consent

A second, **read-only** OpenDocument selection is available after the initial
canonical comparison in Plan. The user must choose the same physical backup
copy again. A process-owned `NativeBackupReviewTicket` retains only the
first selected source byte count, source fingerprint and canonical redacted
preview. It does not retain the selected URI or raw records, and is never a
`NativeBackupRestoreApproval`.

The second selection is read completely through the bounded provider reader,
then the native runtime independently re-fingerprints the bytes, reruns the
pinned canonical merge comparison against the current owned snapshot and
requires the same source fingerprints, hypothetical merged digest, counts and
risk flags. The runtime re-reads the exact current canonical source after
the worker await. A new comparison or committed store change invalidates
the process-scoped ticket; closing the runtime invalidates it as well.
Cancellation and provider failures never allow a verified result. The
second selection's detached bytes are erased when the picker finishes.

The UI distinguishes an exact second match from a mismatched, changed or
unreadable copy, and says explicitly that **verification does not import,
save, approve or enable restoration**. The regular read-only preview,
export, onboarding, workouts and history still use their existing owners.

Domain tests cover first-source mismatch, invalid/oversized hashes, stale
current-store revisions, selected-byte length mismatch, changed merge
prediction and risky-but-reidentifiable inputs. A source-level Android
interaction contract traces Plan -> picker -> runtime -> selected-byte wiping
while ensuring neither the UI nor runtime calls the private import writer.

Remaining activation blockers: a real consent and conflict-resolution sheet,
provider re-opening/recovery acceptance on actual Android devices, no-loss
legacy backup migration, process-death and storage capacity acceptance, and
integration of the rest of the native training workflow. Do not build an APK
or describe this as a user-operable restore.

## Follow-up: complete silent-mutation warning coverage for private merges

The canonical web merge maintains a latest-performance mirror (`perf`) and
deletion clocks (`tombs`) outside the five ID-based saved collections.
A backup can therefore change an existing exercise's latest-performance
summary, or introduce a deletion marker for a record not currently on this
device, even with zero generic conflicts and zero immediately removed rows.
Those risks were omitted from the initial review summary.

The isolated native bridge now explicitly counts `performanceChanged` (existing
exercise mirror rows altered or removed) and `deletionClocksChanged`
(introduced or advanced future deletion clocks). Both produce named risk
flags in a strictly checked order. Kotlin rejects missing, forged or
unflagged risk counts. The shared Material 3 review shows the warnings,
and the private candidate preparation independently rejects these changes
before returning a candidate. The source branch is protected by synthetic
redaction/negative tests and the exact-head Android contract workflows.

These are **safety gates**, not a second Kotlin merge implementation or a
complete conflict-resolution policy. Canonical JavaScript remains the
temporary merge authority. It is still impossible to import, merge, restore
or Undo a selected document from the app UI. Physical provider and
process-death acceptance is outstanding; maintain the APK freeze.

## Follow-up: single-use native backup review lifecycle

The first two-selection backup comparison was read-only but kept its original
fingerprint ticket in the process after a successful or failed re-selection.
That left a stale review available to an unrelated later picker action and
allowed a canceled old verification to revoke an unrelated newer comparison.
The review owner is now an explicit, process-only `NativeBackupReviewGate`.

A first comparison creates a revision-bound ticket; a second selection claims
and **consumes** that ticket before the canonical worker call. The result can
be confirmed once. An old worker result, canceled input, failed provider read,
dismissed Plan view, newly selected backup, committed workout change or
runtime shutdown cannot resurrect it. Cancelling a stale job invalidates only
its own claimed attempt, not a newer review. No saved source bytes, URI,
candidate store or approval evidence are retained by this owner.

Native Plan and Activity wire explicit revocation when the picker is canceled,
another inspection mode is selected, a result is cleared or the backup panel
is disposed. JVM unit tests cover one-shot success, mismatch, cancellation,
concurrent revisions, stale job abandonment, tampering and shutdown.
Source interaction contracts ensure this never invokes the private
`installApprovedBackupMerge` or an Android document writer.

This remains **read-only lifecycle safety**, not user-authorized restoration.
Provider and process-death testing on a physical Android device, a full
consent/conflict policy, web backup migration and complete app integration
must be accepted before enabling import. Preserve the APK freeze.

## Follow-up: native muscle emphasis and reduced-volume program authoring

The native onboarding workflow now exposes a shared Material 3 muscle-priority
picker sourced from the **canonical JavaScript engine's supported choices**.
A user can request up to three muscle priorities (focus or specialization)
and three reduced muscles, with an explicit no-overlap rule and a Balanced
reset for each group. Choices are included in the typed native profile,
passed through the pinned canonical `generateNextProgramForShell` request,
shown in the complete program review and saved into the canonical program
creation snapshot. The generator, not Compose or Kotlin, remains responsible
for exercise selection, real training dose and the existing audit.

`NativeProgramSetupCodec` accepts both the original exact 11-field saved
onboarding profile and the new exact 13-field setup with `focus` and
`reduced`. New fields have strict finite domains, numeric focus points
(1 or 2), up to three selected muscles, no duplicate/reduced overlaps,
deterministic source serialization, and no arbitrary private settings.
Old saved native programs can still be restored without rewriting their data.
Canonical bridge and Kotlin tests exercise valid requests, incorrect shapes,
missing paired fields, duplicate IDs, unsupported muscles, preservation across
inspect/restart and no public diagnostic write authority.

The existing engine maps lats and upper back onto one general `back`
priority. Native onboarding now advertises that **combined back group**
rather than incorrectly promising two independently controlled groups.
Similarly, the current engine does not yet provide independent brachialis,
wrist-flexor and wrist-extensor training credits: those remain explicit
Kotlin engine rebuild requirements, not native feature-completion claims.
The picker warns that reduced muscles may still receive compound work.

**Important boundaries:** this pass enhances initial *generated program*
authoring, not custom day/exercise authoring or editing an already saved
program. There is still no first-class custom editor, no complete adaptive
cycle builder, no restoration import button and no APK. Complete all native
workflows and Android device acceptance before switching training authority
to the independent Kotlin engine.

## Follow-up: verified native saved-program editing draft (not yet persistable)

The Plan screen now opens a revision-pinned Material 3 editing workspace for
the authenticated current saved program. The user can rename the program,
rename existing training days, reorder existing movements within their
original day, reset the draft or close it. The workspace uses an immutable
`NativeProgramEditDraft` with private construction, frozen nested movement
arrays, exact original-day and snapshot reference checks, and no write
authority. It refuses invalid indexes and labels, and clears/recreates its
state for each verified saved-program revision. Editing is disabled during
an active/in-flight workout.

The draft's movement names are resolved via the app's existing **packaged
canonical exercise catalog**, not guessed from exercise IDs or duplicated in
Compose. The process-owned runtime rechecks the exact current-store fingerprint
both before and after bounded catalog lookups. Missing, stale or inaccessible
results remain identified only by their original stable exercise IDs. The
read-only catalog cannot issue targets, authorize a custom record, update a
program, alter history or access a user-selected backup.

**No program edits are saved yet.** The editor makes this explicit with a
disabled save action. Renaming/reordering an existing program must first be
certified by the canonical program editor and converted into an atomic
expected-revision update, while preserving movement-specific overrides,
primary lift identity, slot references, custom amplifiers, superset pairings
and associated cycle settings. Source/JVM tests establish immutable draft
behavior and current-source identity, not training correctness or Android
device acceptance. Adding/removing/swapping exercises, genuinely authored
custom programs and adaptive-cycle editing remain future integration work.

This is an incremental native editing foundation, not completion of the
"custom programs" or "program builder" feature catalog gates. No APK
or training-engine replacement is authorized by this milestone.


## Follow-up — 2026-11-02: saved program organization is connected

The native Plan editor now reviews and saves program/day renames, whole-day
ordering and within-day movement ordering. Save requires explicit confirmation
and an exact current snapshot/program revision. The packaged JavaScript bridge
uses the existing canonical `programSavePlan` with scope `program`; it never
propagates these changes to another cycle block. The single native store owner
checks the source again after the worker returns, commits atomically, refreshes
the latest completion receipt's snapshot identity, and reconciles an exact
committed file after a lost acknowledgement. An active workout prevents editing.

Every movement carries its original slot number through a draft. Duplicate
exercise IDs therefore retain independent overrides, next-week prescriptions,
progression settings, dose biases, alternate-week data and amplifiers. The
original primary lift follows its slot. Superset links and round counts travel
with intact, ordered groups; a save that would split/reverse a group is rejected.
The editor does not yet offer dedicated group-moving or pairing authoring tools.

History, performance/PR caches, tombstones, equipment/settings, creation
provenance, custom definitions, cycle settings and other saved blocks are retained.
Reordering days with ambiguous legacy position-only history is refused rather
than silently assigning that history to another day. SavedState retains the
draft's day/slot order and partially typed labels across rotation and tab changes;
a mismatched source revision cannot restore a draft as current. Closing the
editor requires confirmation before discarding unsaved changes.

Source tests cover rich per-slot data, duplicate movements, paired groups, stale
and malformed requests, exact retry identity, channel isolation and canonical
issuance from the edited program. Kotlin integration scenarios cover a real
packaged-engine save/restart/start/log/finish journey, retained completion/history,
disk-full refusal, lost acknowledgement reconciliation, and a delayed edit racing
a newer save. Android interaction contracts trace the confirmed Save through
Activity, Compose, runtime, canonical editor and atomic owner. These checks do
not substitute for an Android device journey.

This closes the disabled-save gap for organizing an existing saved program.
Adding/removing/substituting exercises, authored custom plans, advanced cycle
controls, the rest of live training/tools/settings/insights, restore activation,
and Android interaction acceptance remain integration work. APK readiness and
the independent Kotlin training-engine rewrite have not been declared complete.

Validation for this follow-up: 185 domain JVM tests and 32 packaged-runtime JVM
tests passed with zero failures/skips; Compose Kotlin and Android instrumentation
Java compilation passed. The full `Android Compose foundation contracts` source
suite and a freshly built/verified canonical IIFE proof passed. No APK was built.


## Follow-up — 2026-11-02: durable session notes through the real workout journey

The real Train logger now has an accessible session-note icon in its controls,
including Focus Mode. The shared `NativeEditSheet` owns keyboard scrolling and
save/dismiss protection for both set corrections and notes. The note editor keeps
its draft through SavedState, confirms unsaved discard, and closes after a save
only when the exact stored text is acknowledged. Clearing text requires saving
that change. Notes are limited to 2,000 characters, preserve Unicode/newlines,
and never appear as coaching above the exercise name.

Session notes are stored in the atomic workout journal, with a separate operation
and replacement identity. They do not consume a working-set slot, advance set
revisions, alter issued targets or affect progression. Note writes reserve enough
journal space for every unresolved observation and Finish. Existing journal formats
2–4 remain readable and unchanged when a note has never been recorded; format 5
adds a bounded note and retains all working/correction/adjustment events. A finish
cannot predate its saved note, and a finished workout's note cannot be mutated.

The canonical completion handoff validates and retains the exact note as
`nativeSessionNote` in saved history, in the same atomic history transaction.
The native history reader displays it, later set corrections preserve it, and a
validated export contains it. A new workout starts with no session note. This is
a session note, not a pinned per-exercise note/setup or completed-note editor.
Those separate tools remain integration work.

Verification: 188 domain JVM tests and 33 actual packaged-runtime JVM tests pass
with no failures/skips, including disk-full/lost-ack note saves, restarted journals,
working-set and adjusted-skip preservation, active corrections, completion, saved
corrections, export and next-workout isolation. Compose and instrumentation-source
compilation, the complete source-contract suite and a freshly verified Android
IIFE proof pass. Notes do not change set counts, volume, strength records or
prescription data. No APK has been produced and device acceptance remains open.


## Follow-up — 2026-11-02: warm-up logging, original targets and saved corrections

The real native Train workspace now records warm-ups through the same atomic
recovery journal as working sets and session notes. Issued warm-up targets have
independent positions, including repeated exercise IDs in different workout
slots. Users can also add consecutive warm-ups when no initial load estimate
exists, log actual load/reps/optional RIR, explicitly skip a target, and replace
an earlier warm-up observation. Blank input is never treated as zero; explicit
zero-load work and positive-load zero-rep attempts remain distinct factual rows.

The shared keyboard-aware edit sheet and numeric controls are used in Train,
Focus Mode and saved history. Each warm-up draft and retry identity is saveable.
A lost acknowledgement recovers the exact recorded operation, and a stale edit
cannot replace a newer fact. Warm-up writes reserve the space needed for all
unresolved working observations and Finish. The finish review identifies any
unrecorded targets; those targets are not silently marked completed or skipped.

New issued workouts carry `warmupLogging: true`; their format-6 journals preserve
the original target roster even if no warm-up is recorded. Existing issued
workouts and journal formats 2–5 retain their exact old identity and remain
readable. Recording warm-ups during an older active workout adds the new facts
without regenerating that workout's prescriptions. Set revisions, working-set
positions and completion counters remain independent of warm-up receipts.

Canonical completion retains the exact original target objects, exercise roster
and full warm-up observation/replacement audit in `nativeWarmups`. The native
store independently checks those facts against the pinned issued workout before
publishing history or retiring recovery. Saved warm-ups can be corrected through
the existing expected-revision history owner. These corrections retain other
history, notes, working observations/targets, records, progression mirrors and
program/cycle data. Later working-set edits retain the warm-up evidence. Validated
exports include both original targets and corrected observations; the next
workout starts with no previously logged warm-up facts.

Warm-ups intentionally do not contribute to working-set completion, recorded
training volume, strength records or progression. Tests compare next-workout
issued rows before/after a false high warm-up correction and require exact
agreement. This integrates factual logging; it does not rewrite warm-up loading
or any training formula in the existing engine.

Verification: 193 domain JVM tests and 35 actual packaged-runtime JVM tests pass
with no failures/skips. Scenarios cover real history-derived targets, added
warm-ups, repeated lifts, unrecorded/skipped/zero-load/zero-rep rows, active and
saved corrections, disk-full/lost-ack recovery, a forged backend target, protected
Finish capacity, export and next-workout isolation. Compose Kotlin,
instrumentation-source compilation, the complete source-contract suite and a
freshly verified Android IIFE proof pass. No APK was built. Custom/cycle
programming, pinned notes/setup, loading/substitution/superset/readiness tools,
insights/preferences, restore activation and Android device acceptance remain
integration work before the engine rewrite.


## 2026-11-02 — Pinned exercise notes and physical setup

The actual Train and Focus Mode controls now include a pin icon, saved detail
card and shared keyboard-aware note/setup editor below the exercise heading.
Drafts and retry identities survive rotation; saving disables competing actions,
closing protects unsaved changes and only an acknowledged operation dismisses
the editor. Equipment fields and labels come from canonical
`setupFieldsWithStored`; Kotlin does not infer exercise equipment. Stored orphan
settings remain editable and an explicit clear carries into future workouts.

New issued rows retain the exact original note/setup context. Unsupported legacy
values and old rows without that context remain readable and cannot be silently
overwritten through an empty editor. Note edits are per exercise ID, so repeated
slots share the same pinned details while retaining independent set identities.

The private format-7 journal retains ordered, per-exercise replacement operations
with bounded text/setup and independent clocks. Working revisions, target values,
completion counters, warm-ups and session notes remain separate. Exact retries
recover the prior operation; stale revisions, replacement IDs, collisions,
invalid text/fields and edits after Finish are rejected. Edits reserve enough
bytes to log every remaining set and Finish. Formats 2–6 remain readable.

Finish applies the recorded note/setup edits to canonical `exNotes`/`exSetup` in
the same publication as workout history. The completed entry retains the full
`nativeExerciseNotes` audit. The native owner independently checks the base facts,
exact audit and complete resulting maps, including unchanged unrelated exercise
entries, before committing or retiring recovery. A lost completion acknowledgement
recovers the one existing publication. Saved history corrections retain the
audit and pinned details; validated export and the next workout retain the
final saved values. Active journal edits are exported after completion, not as
part of a saved-data-only backup during a workout.

Verification: 198 domain JVM tests and 37 actual packaged-runtime JVM tests pass
with zero failures or skips. Source and freshly built Android IIFE scenarios
cover repeated lifts, legacy fields, clear operations, exact retries, wrong
contexts, corrections and unchanged training facts. Runtime scenarios cover
restart, lost save/completion acknowledgements, unauthorized candidate changes,
backup and the next issued workout. The complete source-contract suite, Compose
Kotlin compilation and instrumentation-source compilation pass. No APK was built
and no training formulas were rewritten. Remaining integration includes advanced
custom/cycle programming, loading/substitution/superset/readiness tools,
insights/preferences, restore activation and Android device acceptance.


## Follow-up — 2026-11-02: guarded movement removal in saved native plans

The native Plan editor now supports explicit, confirmed removal of one movement
slot from an existing saved program. A repeated exercise ID is not treated as
one shared slot: retained occurrences keep their independent original positions
and prescription metadata. Removal cannot empty a training day, delete its
designated primary movement or silently split a linked superset. Unavailable
group and primary authoring tools are not implied by the removal control.

The revision-pinned organization request remains format 1 for reorder/rename
only; format 2 allows a strict nonempty subset of original exercise positions.
The canonical JavaScript editor still owns the actual program modification and
scopes it to the selected saved block. Original slot-keyed overrides, week
prescriptions, progression and amplifier data move only with retained slots.
The Kotlin decoder independently reconstructs those maps and refuses any
unauthorized mutation of history, performance, cycles, sibling programs,
settings, identity clocks or deletion records. Draft restoration preserves
the selected original slots. The atomic store owner still handles stale
revisions, write failures, exact retries, interrupted acknowledgements and
the active-session lock.

Verification includes JS source contracts, a freshly verified canonical
Android JavaScript bundle, the Android sandbox JVM suite with a new real
save/restart/issued-workout test, Kotlin domain contracts and the Compose UI
compilation carried by this branch. The source contract suite passed at
[41419837876](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41419837876)
and the packaged sandbox runtime/JVM verification passed at
[41419837893](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41419837893).
An earlier integration-test write-count failure was a test expectation error:
starting an issued session legitimately records its own reservation. The
corrected test measures the blocked program edit independently and confirms
history remains unchanged.

This finishes **removing** existing movements, not adding or substituting
new ones, writing full custom programs, adaptive cycle changes, or Android
interaction/device acceptance. It does not authorize an APK or the Kotlin
training-engine replacement. Other native app/web branches remain untouched.


## 2026-11-02 — Accessory substitution deliberately held behind prescription gate

A separate research branch,
`feature/native-accessory-swap-prescription-gate`, retains the attempted
Compose accessory picker, catalog-only alternative query, version-3 program
edit and regression harness. It is **not released or accepted** into the
verified Android migration. The stable migration branch retains tested
movement removal and the earlier native workout journey.

The blocker is a domain invariant, not a missing button: Pursuit Next stores
week-by-week engine-owned targets in `nextWeekPrescriptions` by original
exercise slot. Deleting that cell makes `computeCell` return an unavailable
zero-set workout, while carrying it blindly to another exercise risks
incompatible rep windows, techniques and exercise-specific progression.
A generated native program did not offer even one alternative satisfying
the overly strict equal-rep/technique constraints. Tests correctly refused
to certify a substitution that cannot issue a valid workout.

Next implementation requires the canonical engine to **re-prescribe the
new exercise for every affected week/phase**, using actual exercise identity,
equipment, loading convention, role, fatigue, set dose and available increments,
without transferring recorded loads, user-owned prescriptions, prior PRs or
exercise-specific amplifiers. The native save owner must independently verify
the entire candidate snapshot; subsequent issued workouts must contain
nonzero, valid sets for the replacement. Repeat with matching and differing
rep-range alternatives, primary/paired protections, explicit user overrides,
multiple phases, custom programs, zero-availability, retries, stale revisions,
restart, history integrity, and fail-closed recovery. The M3E picker can then
be enabled on the migration branch with end-to-end/device acceptance.

No APK was built, the working web app and another developer's Android
branch were not modified, and the scheduled independent Kotlin engine rebuild
remains gated behind native integration. Failed experimental CI is intentionally
not treated as evidence that the stable migration is broken.


## 2026-11-02 — Receipt-confirmed rest timing in native Train

Rest timing is now connected to the already-persisted canonical workout
journal, not a speculative "Complete set" click. The pure Kotlin
`NativeRestCuePolicy` selects only the most recently acknowledged, retained,
non-correction completed working-set receipt in the selected exercise, with a
remaining set in the actual workout. Issued rest targets, including the rest
before myo/drop extensions, are used without training calculations on Android.
Skipped or corrected sets, finished workouts, other exercise tabs and grouped
supersets cannot auto-trigger this individual-exercise timer.

The Compose `NativeRestTimer` starts after the store becomes available again.
It saves the last observed receipt key to prevent re-triggering on ordinary
rotation, navigation or workout recovery. Monotonic elapsed-time deadlines
continue through backgrounding; wall-clock/boot changes fail closed. Tapping
restarts the current issued target, holding clears it, and an M3 progress
indicator makes the interval visible. This is a presentation capability,
not a journal event, engine prescription or notification service.

Three pure domain JVM tests cover persisted acknowledgements, extension
timing, skips, corrections, other exercises, superset suppression and end-of-
workout behavior. A packaged-engine JVM test now generates a real program,
issues a real workout, persists one working set, recovers the journal, and
verifies the cue and unchanged canonical store; a skip and a later correction
are confirmed not to restart rest. The latest source-contract check passed at
[41427843063](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41427843063),
Compose UI compilation passed at
[41427128436](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41427128436),
Kotlin domain tests passed at
[41426868076](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41426868076),
and the packaged sandbox runtime/JVM plus Android instrumentation-source
compilation passed at
[41427842402](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41427842402).
No APK was generated. Device/background behavior still needs real Android
acceptance testing before the integrated APK gate.

The unsafe accessory replacement attempt remains isolated at
`feature/native-accessory-swap-prescription-gate`. No JS training formulas
were changed, and the live web/other programmer's Android branch remain
untouched.


## 2026-11-02 — Actual issued Up Next navigation and Focus parity

Native Train now presents a shared M3 **Up Next** card based exclusively on
`NativeNextSetPolicy.inspect`, the issued `NativeWorkoutPlan` and the
acknowledged private `WorkoutJournalState`. The policy refuses mismatched
session identities or ordered slots. It does not calculate target load, rep
ranges, RIR or next-workout progression; the text reproduces the precise
engine-issued set targets and unit for each original slot.

The card handles an unfinished set on the selected exercise, subsequent sets
on another exercise, completing the selected exercise, wrapping back to
unfinished earlier work, repeated instances of the same exercise ID, logged
corrections and saved skips. It exposes an actual exercise-position navigation
action only when needed. Focus continues to use the identical journal and
preserves unsent per-set drafts; no second workout logger or hidden training
mutation was introduced. When a single set remains, it is shown as the final
set instead of suggesting a nonexistent next step.

Synthetic Kotlin fixtures test issuance order, repeated movement IDs,
skips, corrections, wraparound and refusal of mismatched session/slot
identities. A packaged-runtime JVM integration test generates a real plan,
issues and saves a workout, records a set, skips a subsequent set, corrects
the original observation, recovers the journal and proves that Up Next always
uses the remaining actual set identities without altering the canonical
saved store.

Verified checks: Kotlin domain
[41429301722](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41429301722),
Compose compilation
[41429301698](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41429301698),
Android source contracts
[41429415526](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41429415526),
and packaged sandbox JVM tests/instrumentation-source compilation
[41429415636](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41429415636)
all passed. No APK was built, no training engine formulas changed, and
neither the live web app nor the other native branch was touched.

Device accessibility, animated transitions and true background/resume behavior
remain a later real-device acceptance gate. The experimental exercise
substitution implementation remains quarantined until canonical fresh
prescription generation is available.


## 2026-11-02 — Focus receipt handoff and contextual discomfort controls

The native Train surface now advances an existing Focus session between
exercises **only after a new, durably acknowledged final set** resolves all
slots on the selected exercise. A pure `NativeNextSetPolicy.advanceAfterAcknowledged`
checks canonical session, current exercise position, the original
exercise-indexed slot and the latest retained journal receipt. It refuses to
advance after same-receipt replay, saved-set corrections, unrelated exercise
saves, a still-pending set, mismatched journal data or the workout's final set.
Focus does not create any training targets or write to history. The
last-observed receipt ID is saveable across normal Android rotation and
navigation; an already-present receipt at first composition is the baseline,
not a new completion. Busy saves delay navigation until the store settles.
User-selected exercise tabs and unsaved per-set drafts remain intact.

The full affected-muscle control surface is now behind a compact M3
`Adjust for discomfort` card above the live workout. Expanding restores
all canonical muscle inspection, affected exercise navigation, explicit
reduce/skip confirmations and existing journal adjustment annotations.
Collapsing the card cannot silently skip a set, change an exercise or apply
an injury diagnosis. This avoids placing a large secondary tool before every
working set by default.

Pure Kotlin tests exercise fresh and repeated receipts, corrections,
skips, cross-exercise identity and end-of-session behavior. A packaged
canonical-engine runtime integration test issues a real multi-exercise
workout, commits all sets of its first exercise, proves Focus does not jump
until the last acknowledged receipt and verifies restored journal identity
and unchanged canonical saved-store writes.

Verified CI: source contracts
[41430484122](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41430484122),
Kotlin domain tests
[41430484259](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41430484259),
Compose compilation without APK
[41430484226](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41430484226)
and packaged engine-runtime JVM/instrumentation-source checks
[41430484159](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41430484159)
all passed. Device-level accessibility, background/rotation and real UX
acceptance still require Android hardware; compile/runtime tests do not
replace them.

No APK was generated. Existing JavaScript training formulas, the live web
application and the other programmer's Android branch remain unchanged.
Unsafe accessory substitution is still quarantined behind canonical
re-prescription. The planned Kotlin training-engine rewrite has not begun.


## 2026-11-02 — One shared numeric set-entry experience across Train and Insights

The Android UI uses the same `NativeSetValueFields` for issued workout
logging, corrections to completed or skipped active sets, and later historical
observation corrections. The formerly separate numeric validators now call
the pure `NativeSetEntryPolicy.accepts` in all three flows. It preserves
the canonical observational bounds (nonnegative load with at most three
fractional decimals, 1–100 normal reps, actual RIR 0–10 or omitted) and
the **issued GZCLP-only failed-attempt exception**: zero reps require the
engine-authorized slot and positive observed load. This input policy does
not alter engine training prescriptions or independent journal validation.

Shared M3 filled-tonal plus/minus controls give 48 dp minimum touch targets
beside keyboard-editable rep and actual-RIR fields. Rep steps are exactly one
observed rep and respect the zero-rep capability. The empty RIR field means
*unknown*, not an assumed RIR of zero: pressing plus starts at zero, pressing
minus from zero clears it again, and the 0–10 bounds are enforced. Invalid
or incomplete drafts cannot be silently rounded to a value. `Load` retains
direct manual entry; no barbell, dumbbell, Smith, cable or machine increment
has been invented by Kotlin. Actual equipment-specific loading steps remain
gated behind canonical provenance and a separate integration test.

A follow-up fixes a lost-ack retry edge case in the active correction sheet.
An already-issued **skip correction** contains no numeric values, and its
existing idempotent operation can now be retried even when load/reps inputs
are blank or invalid. An ordinary completed-set correction still requires
valid actual values before it can be submitted. The retry retains the
original operation identity; it does not create an optimistic skipped set.

Verification: the Kotlin domain contract suite (including three new bounded
input/stepper scenarios) passed at
[41431513823](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41431513823).
The shared source-contract suite passed at
[41431514063](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41431514063)
and after the retry fix at
[41432136852](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41432136852).
Compose compilation passed at
[41431513971](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41431513971)
and again after the retry fix at
[41432136903](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41432136903).
The packaged canonical runtime JVM/instrumentation-source test suite passed at
[41431514097](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41431514097).
These are integration and compiler checks, not physical-device UX acceptance.

No APK was generated; no live web, main, other native branch, canonical
engine algorithm or user-owned workout data was modified. Remaining gating
features include canonical equipment increments/attachments, full advanced
custom and adaptive-cycle behavior, verified safe exercise replacement,
backup-restore activation and Android device accessibility/resume acceptance
before the planned Kotlin training-engine rebuild.


## 2026-11-02 — Equipment-aware load inspection with explicit physical confirmation

Native Train now offers an optional **Check equipment-aware weights** action
for one unfinished, engine-issued working-set slot and its literal edited
load. This is strictly a read-only source-pinned *adjacent selectable load*
inspection, not a progression decision or a change to actual performance.
The original manual input and journal-confirmed Complete action remain the
only way to log the set. The review is valid only while workout ID, original
exercise slot, display unit, entered load and owned canonical snapshot
revision match; subsequent edits invalidate the displayed review.

The new isolated `native-equipment-loading.mjs` bridge delegates physical
availability to the existing canonical `nextAvailableLoad` and
`availableLoadAtOrBelow` solvers. It does **not** treat factory loading
defaults as verified physical gym inventory. A saved explicit same-unit
inventory is required. It can confirm user-owned exercise-specific racks
and increments; exact dumbbell loads **per hand**; explicit cable/machine
stacks; and total barbell loads achievable using a known empty bar and
physically owned paired plates. Generic Smith stack assumptions are
**not enough to certify the effective empty Smith bar**: only an explicit
list of selectable Smith *total loads* enables its corresponding controls.
Unknown or specialized bars, machine configurations not supported by the
actual native catalog metadata, missing plate pairs, ambiguous load carriers,
unit-mismatched stock and absent inventory fail closed to manual entry.
Where the current manually entered load is not selectable, the UI says so
without silently overwriting the user's actual log.

The audit discovered an important preexisting integration defect:
`modules/next-engine/loading.js` looks up its default exercise mode through
the newer `EXERCISE_MAP` (underscore exercise IDs), whereas actual native
issued workouts use `EX_BY_ID` (hyphen exercise IDs, e.g. `back-squat`).
Blindly using that mode had marked actual barbell exercises
`external_load`. The **native-only bridge adapter** now reads authoritative
`EX_BY_ID[eid].equip`, authenticates one unambiguous supported loading
carrier against the program equipment, and passes that mode as a temporary
read-only override to the canonical physical solver. No web engine or
historical workout formulas were modified. Unknown/specialized equipment
still requires a genuinely configured exercise-specific load profile.

A new `NativeEquipmentLoadingCodec` in Kotlin independently validates the
worker's method/result, issued-workout and current-snapshot SHA-256 hashes,
source, active workout ID, original exercise/slot identity, unit, exact input,
typed family, optional load numbers and strictly decreasing/increasing
candidate bounds. The Java sandbox request allowlist is updated separately,
and the JS bridge is included in the checksum-pinned IIFE bundle. The native
runtime re-reads the owned snapshot for every inspection and rejects stale
snapshot revisions or already completed sets before publishing the display.
No workout or gym store mutation is available through this capability.

Verification includes:
- JS canonical source tests for no default-inventory confirmation, legacy
  unit conversion ambiguity, 159.5 lb → next 160 lb with an explicit 5 lb
  exercise step, 45 lb bar with real paired plates, absent plate inventories,
  forged issued targets and isolated channels;
- a **real packaged runtime JVM integration test** verifying actual program
  issuance, exact input and unit, unknown default inventory refusal,
  confirmed user override, malicious non-increasing response rejection and
  mismatch of saved source;
- Kotlin contract tests and Compose compilation in addition to the native
  source and manifest-integrity checks.

The final source-contract workflow passed at
[41435161541](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41435161541);
the canonical JS bundle proof passed at
[41435161393](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41435161393);
the packaged sandbox runtime and JVM integration suite passed at
[41435161387](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41435161387).
The Compose UI previously compiled at
[41434096325](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41434096325)
for the identical Compose code. The Kotlin domain contracts passed at
[41433908752](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41433908752).
No APK was generated.

**Critical remaining scope:** native onboarding currently creates programs
without a user-authored `config.loadingInventory`, so verified weight
buttons stay disabled on those newly created programs until explicit
equipment/rack/bar/plate inventories can be configured and transactionally
persisted. A first-class native gym/equipment editor, personalized gym
selection and trustworthy cross-unit and exercise-specific equipment
migration are still required. More importantly, inspecting selectable
hardware is **not** the planned correction of the existing adaptive training
engine's flawed progression formulas. The full Kotlin engine rebuild remains
scheduled only after the native feature journey is integrated and accepted.
The live web application, production main, another developer's Android branch
and the quarantined substitution experiment were not changed.


## 2026-11-02 — First-program physical gym stock, explicitly authored

Native onboarding now has an optional M3 physical inventory form with *blank*
unconfirmed defaults. It captures a checked empty bar and actual matched
plate-pair counts; exact dumbbell loads per hand; verified cable and machine
minimum/increment/maximum ranges; and **selectable Smith total loads**, not
an assumed effective Smith bar. The form never inserts sample or default
stock into saved workouts. Changing units clears the loading form rather
than auto-converting hardware. Invalid partial entries block review.

The native Kotlin profile wire supports the optional `loadingInventory` object,
while original saved v1 profiles and priority-only profiles continue to
decode without it. Kotlin validates bounded numeric precision, uniqueness,
family shapes, actual plate counts, unit identity and strict allowed keys.
The isolated native JS creation bridge **independently revalidates** the
same authored inventory before adding it to the canonical generated program
configuration and atomically prepared first-store snapshot. No Kotlin
prescription or new progression formula has been introduced.

Regression coverage checks retained authored stock, old no-inventory
behavior, duplicate/nonphysical plates or loads, conflicting units,
unrecognized families and undeclared per-exercise overrides. Loading
inspection still independently re-reads the exact pinned snapshot and
remains read-only: editing equipment never changes a logged set. This does
**not** mean the current adaptive weight progression is corrected.

**Scope still open:** there is no post-save gym inventory editor, no
exercise-specific override authoring, no multi-gym selection, and no safe
stock migration when a user changes units. Existing saved users must not
be given factory loads as if they owned them. Full custom/adaptive-cycle
flows, safe substitution with canonical re-prescription, native backup
restore, physical Android accessibility/interruption acceptance, and the
later Kotlin engine rewrite are separate milestones. No APK is generated;
the production web app and other native developer branch remain untouched.

Verification for this checkpoint is complete only when the latest
`architecture/android-compose-foundation` head passes the foundation,
domain JVM, Compose compilation and packaged runtime workflows; an Actions
success alone is not device acceptance.


## 2026-11-02 — Individually verified cable / machine load profiles

An isolated, revision-bound extension to the native saved-gym editor now
supports **physical settings per individual cable/machine movement**. Candidate
IDs, names and carrier modes originate from the current canonical
`EX_BY_ID` catalog and the actual saved program's exercise slots; they
are not supplied as arbitrary user text. Both explicit selectable loads
(`exact: 17.5, 22.5, 27.5`) and witnessed ranges
(`range: 5, 7.5, 200`) use the canonical existing load solver.
The shared M3 UI keeps this optional section collapsed until opened.

The native first-program setup still does **not** accept user-created
exercise overrides. Only the current, saved, revision-authenticated Plan
editor offers them. Kotlin permits only listed candidate IDs and
well-formed bounded profiles; the canonical JS editor separately checks
every candidate against the original program and source catalog, disallows
user-chosen carrier modes and preserves all noninventory history/plan
fields. The independently validated atomic commit must find an unchanged
store revision and no pending workout. After restart, native inspection
retrieves the exact saved profile, not stale onboarding settings.

A separate exercise-loading test runs real canonical program issuance,
then confirms that the observed `159.5` weight shows verified
`155` / `162.5` adjacent settings on a known 7.5-lb cable profile,
and exercises an exact 17.5/22.5/27.5 list. Both the source dispatch and
the checksum-pinned packaged bundle test reject wrong units, unknown or
unowned IDs, forged modes, duplicate or fractional-precision-drifted loads,
and stale save revisions. The shared loading inspector now reauthenticates
the entire physically authored inventory before enabling any control;
malformed legacy or injected overrides fail closed rather than borrowing
factory inventory. Kotlin and JS restrict physical configuration to two
decimal places because the unchanged canonical solver rounds to hundredths.

**Not solved:** plate-loaded, selectorized specialty and ambiguous
machines still need explicitly modeled equipment carriers and attachment
variants. Per-exercise physical settings are informational and do not
correct the existing adaptive weight/progression decisions. Removing an
exercise that has a stored physical override requires explicit
reconciliation in a future program-editor pass; the current loader refuses
stale equipment metadata. Multiple named gyms and cross-unit migration
are not enabled. Keep the no-APK gate until the full journey and physical
device tests pass. No live web files, training formulas or other native
developer branches were changed.


## 2026-11-02 — Specialty-machine logged-weight accuracy gate

In `native-loading-catalog.mjs`, a saved plan can now expose its exact
specialty machine movements (leg extension/curl, leg press, hack squat, calf
machines and recognized independent specialty machine families) as
`external_load`. Each movement must be a real issued-domain `EX_BY_ID`
exercise, owned by a current program day, and explicitly supported by the
saved gym's equipment list. These are **not** automatic machine-stack or
barbell/plate modes. Unknown machines, unsupported gear and different
resistance mechanisms do not inherit generic factory equipment.

Only **exact selectable weights measured in the same unit and meaning as
the native set logger** may be saved on a specialty movement. For example,
`exact: 12.5, 17.5, 22.5`. Unlike an explicitly verified cable or
standard stack, `range: minimum, increment, maximum` is intentionally
blocked for specialty movements because the step and mechanical advantage
cannot be safely inferred. Values are explicit user-supplied logged loads;
they do NOT establish a sled base weight, paired plates, per-side plate
math, counterbalance or measured effective resistance. Users of those
methods must continue manual set entry until the recording basis can be
made explicit and validated separately.

Kotlin form/transaction validation and canonical JS edit/inspection are
kept separate. New tests reject fake modes, missing equipment, disallowed
candidate IDs, ambiguous machine ranges and invented increments while
retaining the same atomic revision pin and original workout history.
The training engine and deployed web app remain untouched; no APK is built.


## 2026-11-02 — Exercise removal may not orphan physical stock

Saved program organization now explicitly refuses to delete the final occurrence
of a movement while the native plan still retains a verified, per-exercise
physical load profile for it. A repeat of the same movement on another day is
not an orphan: its verified equipment values are retained. The canonical
program edit JS and independent Kotlin response verifier each enforce that
rule, preserving exact existing program configuration, historical workouts
and atomic revision checks. The removal confirmation explains how to
clear equipment settings first. No automatic inventory deletion or guessed
remapping to a replacement exercise is authorized.

## 2026-11-02 — Direct, discard-safe gym weight editing from Plan

The native saved-program editor now offers a direct route to verified gym
settings. It is a navigation-only action: a dirty program draft requires
explicit discard confirmation before the equipment editor opens. The equipment
form opens ready for inspection; saving still uses its own canonical, revision-
authenticated transaction and confirmation. A changed snapshot returns to the
current Plan rather than silently replaying the old draft. No workout or
training formula is changed by navigation.

The exercise-removal regression now also covers a repeated movement retained
on a different training day. The same verified loading profile must survive
that legitimate removal. Full Compose and source-contract verification is
required on the published revision; no APK or web changes.

## 2026-11-02 — Protect physical stock before removing a movement

The authenticated current Plan editor now disables removing the last
occurrence of any movement with a verified, exercise-specific loading profile,
with an explanation and the direct gym settings route. Repeated movements
elsewhere in the current draft remain removable; the final one requires first
clearing its saved setting. The independent canonical save contract still
enforces this invariant.

Cancel in the physical inventory editor now discards the uncommitted draft.
Leaving for Plan with unsaved edits explicitly asks whether to discard them,
rather than silently losing settings. No invented equipment, formulas, history
writes, APK, or changes to production web and other native branches.

## 2026-11-02 — Contextual equipment-profile reconciliation

A protected exercise in the saved native Plan editor can now open exactly its
verified per-exercise machine weight profile, instead of sending the user into
an undifferentiated gym form. The original current-store revision determines
which movement may be targeted; a dirty program draft must be explicitly
discarded to proceed. The target profile appears first, its normal row is
deduplicated, and a Clear button empties only this particular *draft* value.
Nothing is deleted until the user reviews and confirms the existing atomic
gym-inventory save; other verified machine profiles, barbell plates and racks
remain unchanged.

The native Kotlin regression validates clearing one specialty profile while
preserving a separate cable profile and shared cable hardware. Specialty
machine range guesses remain disallowed. Interaction gates require focus
propagation, explicit clearing and absence of duplicated profile fields.
This is targeted equipment editing, not a progression formula rewrite.
No APK, production web release or other native branch is touched.

## 2026-11-02 — Honest specialty-machine loading units

Native physical inventory and in-workout weight inspection now explicitly label
specialty-machine settings as **verified logged values**, not estimated
mechanical resistance, sled base weights or plate-per-side math. The logger
continues to use the same user-verified basis; no conversion or progression
formula is added. Source interaction checks cover the contextual explanation.

## 2026-11-02 — Primary/paired exercise removal preflight

The canonical JS inspector now projects a bounded read-only map of primary
and paired original slot positions from the exact saved program, not generic
exercise IDs. Kotlin validates every entry against the currently projected
day and source slot. The Compose editor blocks the removal button for key
lifts and both members of linked pairs, with an explanation. The immutable
program draft resolves original source positions after moves and deletions,
so repeated exercise IDs never inherit the wrong slot's protection.

The canonical editor remains authoritative at Save, and no exercise
substitution, training prescription, workout history or physical inventory
is automatically altered. Source and Kotlin tests cover paired source slots,
repeated movements and modified display order. No APK or live deployment.

## 2026-11-02 — Linked pair move preflight

A protected source slot may now be classified as both primary and paired.
The Kotlin domain and saved-program verifier recognize that exact combination,
so a key lift that anchors a superset cannot be accidentally moved alone.
In the native editor, Up and Down are enabled for ordinary standalone
movements, but disabled whenever an individual move would swap across a
source-pinned member of a linked group. This is intentionally conservative:
proper atomic group reordering can be introduced separately, only with
canonical writer parity. The existing editor still independently rejects
split pairing writes. Kotlin + source interaction regressions cover both
pair members, neighboring movements, standalone primary slots, invalid
indices, and state-preserving local reorder cases. No native APK.

## 2026-11-02 — Safe atomic linked-group moves in saved Plan

The native program draft now supports moving the full contiguous block of
linked exercise slots up or down past a standalone neighbor. The block carries
original source positions and corresponding exercise IDs in the same order.
If adjacent linked pair groups cannot be distinguished, they move together
as one contiguous block: no canonical pair is silently separated. The UI
shows group controls only at the first row of a linked block. Individual
Up/Down swaps across linked slots remain disabled.

The Kotlin regression exercises the move, boundary conditions, round-trip
back to original positions, immutable drafts, and serialized canonical
slot indices. Source interaction checks require wired group controls. The
canonical JS save planner still owns the final verification; this pass does
not alter the existing training engine, exercise prescriptions or log data.
No APK is generated; no live web or other developer branch is modified.

## 2026-11-02 — User-authorized installable smoke milestone

The explicit APK threshold changed to onboarding -> saved program -> real
workout screen. An isolated preview workflow is allowed to assemble one
debug-signed `.preview` package with verified canonical source assets after
rerunning onboarding, workout and UI tests. This does not imply physical
Android-device success, completion acceptance, engine rewrite or production
release. Normal source CI remains no-APK and the instrumentation-device
workflow remains disabled. The existing web and other native branches are
untouched.

## 2026-11-02 — Post-device-feedback usability pass and APK re-freeze

The first native tester APK built successfully, but it remains an early
technical preview. The next APK is held until onboarding, actual set logging,
completion, resume, history and coherent Material 3 Expressive interactions
meet a real acceptance pass. The dedicated preview workflow is disabled
again. The current source-only pass moves day detail into Plan; Train always
shows actual logging for a saved program. Authenticated day previews may
fetch canonical exercise names. Saved-user chrome no longer displays the
prototype capability catalog or preview banner; backup tools are collapsed
under a real user action, and the current workout defaults to one actionable
set while retaining Show all. No training formulas, history, production web
or other developer's Android branch are modified.

## 2026-11-02 — Native Train-first layout and Compile recovery

Follow-up to the post-APK UX pass fixes a missing
`ReadOnlyTrainingDayJourney` import detected by Android Compose CI;
the earlier source-only Kotlin/domain and sandbox jobs were green.
The real Train screen now prioritizes its current actionable working-set
logger over its optional injury/muscle panel, timer and skip actions.
Prescribed warm-ups stay available through an explicit expandable
control, and can still add or edit confirmed entries using the same
journal writer. A rest countdown remains session/receipt-bound but is
positioned after the current working-set controls. The saved-progress
label explicitly distinguishes logged, skipped and unresolved set slots.
Workout starts in Focus while retaining Show all, full controls, Last,
notes, skip confirmation, exercise switching, and canonical completion.
No new training calculation, history conversion or device APK was made.

Source interaction contracts verify single-set first presentation,
optional warm-ups and continuing visibility of real recovery and safety
controls. The full native Kotlin/Compose validation must pass before
calling this revision integrated.

## 2026-11-02 — Today-first training decision

On a verified saved program, Today now places the actual Start/Resume
workout action before View program, and presents a compact coherent
session summary. The existing source-backed shorter-session comparison
is preserved behind an explicit expandable action rather than taking
over the default Today view. Expansion is saveable per program identity.
Canonical engine issuance, preview safety, held revisions and workout
state/journal are unchanged. Source interaction tests verify CTA ordering
and guarded opt-in preview. No APK is built.

## 2026-11-02 — Completion-to-history navigation and revised Today assertion

After a canonical journal completion has been durably published and exposed
as `state.completion`, the native Train landing screen now offers a
primary **View saved workout** action that opens the existing Insights
history reader, while starting the next session remains secondary.
This never manufactures or caches a completion in the UI. It simply routes
to the existing authenticated, source-owned history already loaded by
`NativeRuntimeHost.finishWorkout`, with normal manual reload available
if the detail could not be read.

The unchanged canonical short-session policy is protected by a source
assertion using the new, more explicit user-facing copy. No new
training engine assumptions or history writes; no new APK.

## 2026-11-02 — Compact issued Up Next and stronger editor busy gating

The real Train Focus screen now renders a compact, touch-friendly Up Next
summary. It retains the actual canonical-issued exercise, set kind, load
and unit, target reps and optional RIR, and offers an accessible Open
action only when the next unresolved exercise differs. The existing full
Up Next presentation remains when Show all is selected; navigation is
still driven by the acknowledged journal and verified
`NativeNextSetPolicy` rather than optimistic taps. All derived targets
remain display-only; no training or persistence math was introduced.

The live working-set editors are disabled during history and backup
operations as well as workout writes and already-finished sessions, rather
than showing tappable controls that the runtime will reject. Existing
draft preservation, manual load entry, truly verified equipment-adjusted
load choices, skip confirmation and receipt-based Focus advancement
are retained. Interaction contracts cover these invariants. Source-only
Android builds remain required; no APK.

## 2026-11-02 — Explicit real exercise/set position

In the authenticated, active Train session, the selected exercise now
identifies its original issued position out of the full exercise list
("Exercise X of Y"). The active working set shows "Set N of M"
using that exact exercise's issued set count, rather than leaving
the user to infer the total from navigation chips. Myo/drop extensions
retain their distinct display names, rest policies, and independently
journaled slot identities; this pass introduces no new set numbering,
training prescription, or logging calculation. Shared source contracts
verify the source of both counts. All normal CI stays no-APK.

## 2026-11-02 — Source-backed target load visibility

The saved-workout Train set card now shows the exact engine-issued load
and unit before target repetitions and RIR in the Targets view. The
Last-time toggle remains journal/history-backed, and actual entered
weight remains independently editable and unmodified by changing the
presentation. This prevents an editable actual value from being the
only visible indication of the originally issued weight. No equipment
weight guesses, progression rules or storage writes were introduced.
The source interaction check pins this ordering; no APK.

## 2026-11-02 — Compact, shared verified load controls in real Train

The live working-set screen now delegates equipment-aware load options to
`NativeVerifiedLoadOptions`, a reusable native Compose presentation
component. The entry field is still manually editable, and no barbell,
dumbbell, Smith, machine or cable increment is ever guessed. The exact
canonical confirmation is checked against current workout ID, immutable
set slot, entered load and unit before offering any adjacent load. Only
available verified previous/next values become buttons; explanatory text
is collapsed behind About these loads. Unconfirmed equipment explicitly
offers no increments; a failed inspection leaves manual logging available.
When a verified load is chosen, only the user's unsaved actual field is
updated, and the previously verified result is hidden until rechecked.
Canonical engine, persistence and progression math are unchanged.

The shared source assertions now inspect the dedicated component rather
than requiring loading-detail presentation code inside the workout screen.
No APK is produced; the entire journey must pass before another device build.

## 2026-11-02 — Narrow-screen workout toolbar and machine-load semantics

The live workout note, pinned exercise setup and Focus mode controls now
share a non-scrolling row; Last/Targets controls use their own visible row.
This removes a previously scrollable weighted Spacer that could obscure
Focus on phones. The same saveable state and the same durable set editor
remain active.

The shared verified-equipment component distinguishes source-confirmed
machine logging scale values from effective mechanical resistance and
keeps the detailed note available on demand. Source contracts were updated
to follow the new shared component rather than incorrectly reading its
copy from the old monolithic workout screen. No APK is authorized.

## 2026-11-02 — Decimal equipment lookup input regression caught

Audit of the extracted load-control component found an over-escaped
decimal separator in the *presentation-side* preflight regex.
This was corrected to the same source-level two-backslash decimal
escape as the canonical load-input contract, so decimal weights such
as 38.5 are eligible for a real engine-verified inspection. The
actual adjustment values are still read exclusively from source-
authenticated canonical inventory, not computed in the UI.
A source assertion now guards against accidental over-escaping.
This changed no issued prescriptions, logged sets or historical values.

## 2026-11-02 — Shared responsive set-entry controls

The single Compose `NativeSetValueFields` now uses a responsive layout for
Reps and optional actual RIR, sharing the existing bounded input validation,
48dp step touch targets and the same policy-backed increments in every
live set, saved correction, warm-up and history editor. The two counters
can share a row where there is enough width and automatically stack
on smaller screens without clipping. Typed load stays a separate field;
equipment-dependent load steps are *not* inferred. A short contextual
bodyweight hint appears only when actual load is zero, reducing repeated
instructional text on normal weighted sets. Draft ownership, engine
values and storage remain unchanged. No APK until end-to-end acceptance.

## 2026-11-02 — Cross-module nullable verified load compile correction

The no-APK Compose compile found that public `previous` and `next`
properties of the typed verified-loading model cannot be smart-cast
across the domain module boundary inside Compose button callbacks.
The load presentation now captures these source-confirmed nullable
values in local immutable variables and offers a button only for a
non-null value. Canonical verification, manual drafts, units, and
no-inferred-increment rules are unchanged. The interaction fixture
verifies the local value gating. This is a compilation integration
repair, not an engine change.

## 2026-11-02 — Finish-first transition after acknowledged final set

Train no longer leaves a previously saved set and its rest timer in the
foreground after all originally issued working-set slots have durable
completion/skip receipts. Once the journal reports zero unresolved
slots, a highlighted Review and finish action appears immediately below
the real completed-set progress indicator, ahead of movement tabs,
optional notes and adjustment tools. In Focus, the stale saved set
is hidden; Show all still gives access to exact saved-set corrections.
No rest timer is shown with no remaining sets.

The final publication action still goes through the original explicit
Finish dialog, including the warm-up summary; an already durably
finished journal still exposes the exact retry path rather than
manufacturing a new finish. Runtime-owned history publication,
saved-set receipts, recovery and progression are unchanged.
Verification contracts check source ordering, zero-unresolved
gates and the original review/retry requirements. No APK.

## 2026-11-02 — Readable native counter sizing

Responsive shared reps/RIR counters now require at least 560dp of
available width before using adjacent columns, allowing each column
room for its field and two 48dp M3 touch targets. Ordinary phones
stack the controls instead of forcing tiny input fields. This adjusts
layout only: direct numeric editing, policy-backed rep/RIR steppers,
optional RIR, draft retention and journal writes are unchanged.

## 2026-11-02 — Direct saved-set review before Finish

When the acknowledged journal has no unresolved slots, the highlighted
Finish card also exposes a secondary Review saved sets action while
Focus mode is active. This switches to the identical real journal
logger/correction view (Show all) without recording any operation or
publishing history. The same confirmation remains required to finish.
A durably finished journal does not display an edit shortcut, but can
still retry its history publication. The interaction contract checks
the ordering and absence of an optimistic completion path.

## 2026-11-02 — Reveal Finish after the final acknowledged set

The native destination scroll state is intentionally retained while
logging. To avoid hiding the newly elevated Review and finish action
after the final set, Train now animates its page to the top on the
specific transition to an active workout with zero unresolved journal
slots. The effect is keyed to the authenticated session workout ID
and destination, not page-scroll position or ordinary recomposition:
manual Show all review and corrections do not cause repeated scrolling.
The final state is still read from canonical journal receipts, not
a button tap or an optimistic set count. No APK generated.

## 2026-11-02 — Verified latest-completion history handoff

Train's post-completion **View saved workout** no longer merely switches
tabs, which could reveal a previously browsed older history detail or
a filtered/empty list. A new runtime-only `openLatestCompletedWorkout`
operation uses the durable latest `CanonicalWorkoutHistoryReceipt`
session ID, clears unrelated history-selection state, and fetches the
exact workout through the existing canonical read-only history port.
The returned history ID and currently saved revision/completion identity
must still match before the UI publishes its result. A source-bound
unfiltered history page is refreshed for navigation if available;
detail itself remains readable if the list refresh fails. Interrupted
or stale requests fail visibly instead of presenting old workout details.
Train's link is disabled while the relevant runtime is busy.

No new history writer, cache, or training progression logic is added,
and the active workout and any unsaved set drafts remain untouched.
Source interaction tests verify the receipt identity, tab/action wiring,
revision checks and fallback error path. No APK.

## 2026-11-02 — Home shortcut to exact latest saved history

Saved-program Today now exposes **Review last workout** when no
workout is active and a durable completion receipt exists. It uses
the very same `openLatestCompletedWorkout` canonical history read
as Train's post-completion action, rather than trusting the
last-visited Insights detail. Start/Resume remains the primary
CTA, and review is disabled while state reads/writes are busy.
The Today workout CTA was also aligned with runtime busy/blocked
guards to avoid taps that the canonical host would reject.

The source contract was corrected for the already-added busy-state
guard on Train's history button. All changes remain isolated to the
native Compose branch, source-only and no APK.

## 2026-11-02 — Compact, accessible saved-history set rows

Saved workout details now use a single reusable native M3 `ListItem`
row for recorded working sets, warm-ups, and skipped/other observations.
Set kind/index and actual load/reps/RIR are separate readable lines,
with a distinct accessible Edit or Record action rather than a long
sentence-shaped full-width button. The existing exact observation slot,
legacy set correction and warm-up correction callbacks are unchanged.
Correction controls remain disabled during an active workout or busy
history write. Training history remains the canonical read-only source;
all edits still flow through the original revision-verified writer.
Source checks cover shared row use and original correction identity.
No APK.

## 2026-11-02 — History correction busy-state parity

The shared saved-history correction sheets now observe the same
runtime busy conditions as the canonical history writer: active
workout, workout I/O, backup export, history I/O, or broader
store work. The visible Edit/Record actions and all three editor
variants (working, observation/extension, and warm-up) use one
`historyEditorBusy` value. If another tab starts a new active
workout, any history correction sheet previously open is closed
rather than leaving a stale editable form. This avoids no-op
Save actions that the canonical owner correctly rejects, while
preserving exact retry payloads, entry revisions, and all durable
history. There is no new writer, and no APK.

## 2026-11-02 — Completed-exercise Focus navigation

When a user manually selects a fully resolved exercise while other issued
working sets remain, Focus now shows a resolved-state explanation rather than
reopening the previously logged last set as if it were actionable. It
preserves the issued next-set preview and its explicit Continue/Open
navigation; no automatic crossing between exercises occurs without a new
durable receipt. The workout-scoped rest timer remains
mounted when manually switching to a fully resolved exercise, so an active
countdown is not inadvertently reset. Show all continues to expose the
original journal-backed set corrections. Source assertions guard against
the completed-set fallback while retaining session-scoped rest continuity.
This is a presentation-only fix: no prescriptions, logged facts, or data
store writes change; no APK.

## 2026-11-02 — Train landing action guard parity

The Train landing actions now use the canonical runtime's general busy and
blocked conditions as well as workout, history, backup and recovery guards.
Starting the next workout or opening the exact latest saved completion cannot
look enabled while another owned program/store operation makes the runtime
reject that tap. The latest-history action still uses the durable receipt and
reader, not local screen state. Source regression contracts pin both disabled
states. This changes no saved data, engine calculations or navigation targets;
no APK.

## 2026-11-02 — Canonical workout write-gate parity

The native runtime now uses one shared fail-closed gate across all live
journal mutation entry points: recording/correcting working sets, recording
warm-ups, saving session or pinned exercise notes, skipping/reducing issued
slots, and finishing. It rejects writes when initialization is incomplete,
recovery is blocked, program/store operations are active, history I/O or
backup export is busy, or a workout write is already pending. A journal with
an acknowledged Finish may still retry its original canonical publication;
it cannot accept new set or note writes. Saved-history correction and latest-
completion reading also honor broad blocked/store states. This protects
against stale UI callbacks and interleaved owned writes without changing
engine prescriptions, journal formats, existing facts, or finish recovery.
Source contract coverage was extended. No APK or production merge.

## 2026-11-02 — Train control parity with the runtime writer

Train now derives one common operation-busy state and one mutation-busy
state, incorporating blocked recovery, program/store activity, history I/O,
backup export, workout I/O, and the journal's terminal state. Working
sets, warm-ups, session/pinned notes, skip/reduction confirmations, and
Finish bind to the writer's source of availability. Finished journals still
allow only the original explicit publication retry. This is UI integration
and source contract coverage, with no replacement writer or engine rule.

## 2026-11-02 — Short-session preview availability parity

Today’s canonical shorter-session choices and explicit shortened Start
share one disabled-state predicate for blocked recovery, program/store
operations, history I/O, backup export and a pending workout write.
The native runtime independently rejects the same invalid previews and
rejects starting a workout when the store is blocked. An approved short
session is still source-matched against the canonical engine before
issuance. No UI training calculations, persisted data changes or APK.

## 2026-11-02 — Saved-history edit gating after recovery

History detail now disables its existing Edit/Record and all three
shared correction sheets during unready or blocked recovery states, as
well as the original active workout, backup, history I/O and other busy
conditions. This matches the tighter canonical correction writer and
avoids a Save button that can only be rejected. Current corrected
observations, original targets, exact retry identity and saved data
remain unchanged. Source-only validation and Compose compile required.

## 2026-11-02 — History browser/Today shortcut availability parity

The canonical read-only history page, calendar and detail entry points
now share a fail-closed guard against unready or blocked recovery, store
work, history I/O and backup export. The Insights list, filters, date
picker, pagination and Today latest-workout shortcut reflect the same
availability. Read-only history remains usable during an active workout,
while corrections still require finishing it. No saved facts, journal,
training engine calculation or backup files are changed. No APK.

## 2026-11-02 — Auto-rest follows the acknowledged set across Focus navigation

Fixed a real lifecycle/UX gap: Focus may switch to the next exercise after
an acknowledged final set, but the rest cue previously inspected only
the newly selected exercise and lost the logged lift's rest. The cue is
now derived from the last durable, uncorrected working-set receipt and
that exercise's issued rest (including extension rest). It never starts
for skipped/corrected sets, supersets with no paired rest protocol, or
finished workouts. A compatibility API retains explicitly selected-tab
semantics for other callers. Added Kotlin regression coverage for a
bench-to-row Focus transition and source assertions. Timer state remains
session-scoped, nonpersisting and user-resettable. No prescriptions or
stored workout bytes changed; no APK.

## 2026-11-02 — Accurate physical load entry guidance

Improved the native gym inventory editor's exercise-specific input
instructions. The parser already required exact: or range: prefixes, but
several individual field hints omitted them, leading to rejected input.
Every applicable field now explains the valid syntax; specialty/externally
loaded machines explicitly require exact verified logged totals, while
checked cable/standard stacks may supply exact values or real increments.
Bodyweight's unconfirmed loading hint no longer suggests that the exercise
itself has unconfirmed equipment or invented steps. No example is
autofilled and no default load is prescribed; users still enter their
actual physical inventory. The engine and persisted values are untouched.

## 2026-11-02 — Native verified barbell plate breakdown

Added an exact, read-only plate calculator to live working-set loading
options. It appears ONLY after the canonical engine confirms the selected
issued set is a barbell-plates family and the entered weight is an exact
selectable total. It independently validates the persisted physical bar
and matching plate pairs, computes a symmetric per-side layout without
rounding, and chooses a minimal-pair setup with larger denominations
preferred on ties. A bounded solver fails closed on impossible/large
cases; Smith, specialty machines, bodyweight and unknown equipment never
borrow a barbell setup. Its Material 3 confirmation dialog displays the
bar weight, exact total and verified plates per side. It does not alter
the set draft, training prescription, journal, history or inventory.
Kotlin tests cover actual combinations, decimals, impossible totals,
missing stock, wrong units and other equipment families. No APK.

## 2026-11-02 — Two-sided Material plate schematic

The verified barbell read-only dialog now renders two equal-width,
Material-styled LEFT/RIGHT plate columns around a labeled BAR, each using
the exact same validated per-side plate counts. The original numeric
bar/total summary and no-write warning remain visible. Compact layout
does not claim a mechanical force estimate or change the workout.

## 2026-11-02 — Plate calculator dialog overflow protection

The two-sided physical plate breakdown now caps its content area at 360 dp
and scrolls through long verified inventories within the existing Material
3 confirmation sheet. This keeps the Done action accessible on compact
phones and larger text sizes without truncating denominations or guessing
missing plate stock. Source-only UI change; no APK.

## 2026-11-02 — Nonblocking verified plate layout

The read-only physical plate viewer now calculates potentially expensive
bounded plate-pair combinations on a worker dispatcher rather than blocking
the Compose UI thread. Loading is an explicit state. Each lookup is keyed to
the exact saved physical inventory, issued workout ID, unit, selected load,
verified exercise IDs and exactness; changing any input creates a fresh
unavailable/loading state and cannot briefly show a prior plate setup. The
confirmation dialog state also resets when stock or source verification
changes. This is display-only, with no changes to engine-issued targets,
journal, physical inventory, progression or saved history. Source contract
and Compose compilation are required before acceptance. No APK.

## 2026-11-02 — Rest across zero-target Focus and cold-start recovery safety

Fixed two native interruption cases without altering the engine or journal.
An acknowledged previous exercise's rest remains visible as Focus advances
to a zero-rest exercise. The zero-target view does not offer a misleading
restart duration; it provides an explicit Clear action while the active
elapsed timer remains workout-scoped, and receipt effects remain mounted.
An exception opening the packaged engine now exits the startup loading
state with a blocked, non-writing recovery message instead of leaving a
perpetual spinner. Cancellation still propagates. Source regression gates
cover both cases; Compose and domain checks required. No APK.

## 2026-11-02 — Owned resume summary on Today

Today now distinguishes the actual native journal's recorded, skipped and
unresolved sets after navigation, rotation or process recovery. It also
separately labels a zero-unresolved session awaiting explicit Finish, and
a durably acknowledged Finish awaiting canonical history publication.
All counts and labels are sourced from the runtime-owned, persisted journal;
no optimistic set completion, alternative writer, new app state or program
mutation was added. The CTA still routes to the real Train confirmation and
publication workflow. Source contracts and Compose compile gate this pass.
No APK and no production web changes.

## 2026-11-02 — Cross-restart workout receipt acceptance case

Added a deterministic domain JVM regression covering three distinct native
journal owner lifetimes: two original saved working sets, an interruption,
a replacement correction to the first set, an explicitly skipped final set,
and a fully acknowledged Finish. The final cold-start reader checks the
exact corrected values, preserved second set, original slot ordering, one
skipped set, four ordered events, zero unresolved slots, and a single
finished identity. Lost-ack Finish replay performs no additional write;
late set writes and a competing workout begin remain rejected. This is
fault-isolated domain verification, not an Android device test or an APK
acceptance result. No engine or production changes.

## 2026-11-02 — Android Back protection for real Train

The native host now intercepts Android system Back only on the Train
destination while a genuine issued native workout exists and no app-level
sheet is open. It offers explicit Keep training or Go to Today actions,
with an accurate warning that confirmed sets/notes stay in the private
journal while unsubmitted values are not logged. The latter simply changes
the destination; there is no silent Finish, discarded recovery file,
new prescription, or history write. Standard Dialog/ModalBottomSheet Back
remains owned by the top layer. The confirmation resets if workout identity
changes or Train closes. Source contract and Compose compiler gates apply;
physical predictive Back and gesture behavior remain unverified. No APK.

## 2026-11-02 — Dedicated cold-start restoration state

The native root now shows a read-only Material 3 initialization screen
while the canonical worker, saved program and private workout journal are
being opened. It no longer briefly presents an editable new-program
onboarding wizard to athletes who already have a saved program or pending
session, nor exposes the demo navigation during that same transient phase.
Once initialization finishes it routes to existing onboarding or the actual
saved-program destinations; blocked source errors still remain visible in
the existing onboarding recovery surface. No new persistence/engine API,
new training data or source-web change. Source and Compose checks required;
physical launch acceptance remains outstanding. No APK.

## 2026-11-02 — Hierarchical system Back for native tabs

Expanded the workout-aware system Back handler to honor Android's internal
navigation hierarchy. Train with no active workout returns to Today; with
an active issued workout it retains the explicit confirmation before leaving.
Plan day detail returns to the Plan overview, and an idle saved-history detail
returns to its list through the existing history navigation callback. An
app-level modal sheet keeps its own Back handling and no history write is
issued. At Today, Plan overview and Insights list, default Android Back
remains available; no global interception traps the user in the app. Physical
predictive Back gestures still need device testing. No APK.

## 2026-11-02 — Protect physical inventory on weight unit changes

Onboarding now asks for an explicit confirmation before clearing any
nonblank verified bar, plate-pair, dumbbell, cable, Smith or machine
inventory entry when toggling lb/kg. Cancel and system Back preserve the
exact original unit and the full draft. Confirmation switches units and
atomically resets old-unit loading entries, avoiding silent data loss and
unsafe invented unit conversions. Empty drafts can switch without an
unnecessary dialog. No changes to the engine's physical load verification,
completed training data or persisted program. Source and Compose checks
required; no APK.

## 2026-11-02 — Exact-source equipment loading messages

The read-only equipment load inspection now retains a typed request
identity (workout ID, issued set slot and the literal entered load). The
checking label and failure message render only beside that matching
set/load, never on other exercises or after typing a different value.
Asynchronous results and failures are rejected when a different request,
workout or canonical snapshot is current. Canonical store changes clear
the in-flight display identity, preventing obsolete spinner and error
propagation into a subsequent workout. The engine remains the only source
of confirmed adjacent load values; this pass adds no inferred increments,
training writes or new persistence. Source and Compose checks required,
no APK.

## 2026-11-02 — Reveal the next actual exercise after Focus auto-advance

Focus now requests scroll-into-view of the next issued exercise header
only when a freshly acknowledged, completed set moves the selection to
a different exercise. This uses Compose's standard relocation requester
inside the existing scroll container, without a hard-coded pixel offset,
new horizontal layout, or global scroll reset. Manual exercise selection,
restored sessions, prior receipts and saved corrections do not trigger
an automatic scroll. The request is transient UI state; no journal,
engine-issued target, persistence or history behavior changed. Source
and Compose compile are required; device scroll/keyboard acceptance
remains outstanding. No APK.

## 2026-11-02 — Hierarchical Android Back during program onboarding

The first-time native setup wizard now handles system Back for its
second equipment step and the generated-program review. Equipment Back
returns to preferences without losing the draft. Review Back invokes the
existing explicit proposal-edit action and returns to the initial step.
If generation is still running, Back uses the same supported cancellation
as the visible Cancel control. During an actual canonical program save,
Back is consumed rather than cancelling the worker after an uncertain
durable write. The weight-unit confirmation dialog owns its own Back
handling and preserves user-entered verified stock. A stale source-test
assertion for the Focus auto-scroll refactor was also updated to the
new acknowledged-navigation block. No APK or engine rewrite.

## 2026-11-02 — Shared read-only workout inspection gate

The runtime now has one fail-closed gate for the physical loading and
muscle-involvement inspection flows: no requests while the source is not
ready, canonical writes or history reads are in progress, backup export
is active, recovery is blocked, or the journal has reached Finish. Both
readers also check this gate before installing asynchronous results or
errors. On canonical store changes, the cached muscle/weight inspection
and in-flight busy/error labels are invalidated together. Stale inspect
failures cannot paint errors on a subsequent workout. Existing confirmed
journal writes, exercise prescriptions and actual logged values are
unchanged. Source and Compose checks required; no APK.

## 2026-11-02 — Native design-system foundation (graphite + one expressive accent)

Replaced the wallpaper-driven/default color/typography combination with a
shared native-only light/dark token layer. Graphite/neutral semantic
Material 3 surfaces use one violet accent and readable on-color roles,
while respecting the Android light/dark setting. The existing M3 Expressive
motion scheme and asymmetric shape family remain intact. Centralized
headline/title/body/label typography and a consistent spacing scale; the
shared PageHeading, SectionLabel and PursuitCard now consume those tokens.
Every real Today, Train, Plan and Insights surface using these components
adopts the system without a competing per-screen style or training-write
change. No new engine logic, preset loads, web styling or APK. Compose and
source checks must pass before accepting the foundation.

## 2026-11-02 — Shared session metrics and high-priority action

The real Today and Train surfaces now show the same compact, accessible
three-column logged/skipped/remaining summary sourced directly from the
acknowledged workout journal, not another optimistic counter. A reusable
52dp primary action presents Start/Resume from Today. The shared hero card
uses a neutral M3 tonal container with a restrained violet outline rather
than a full-screen colored block; its content remains semantic onSurface.
No training decision or set mutation was moved into these components.
Source tests pin the shared tokens and use on both journeys. The app still
needs physical-device visual validation and user feedback before an APK
is treated as accepted.

## 2026-11-02 — Shared actual workout primary actions

The real native Train screen now uses the same 52dp, Material 3 shaped
primary action as Today for its confirmation-ready Finish and for the
issued working-set logging control. The button remains disabled on invalid
entered values and during canonical busy conditions, and zero-rep failed
attempts and independently issued myo/drop extensions retain their exact
labels. Finish still opens its existing explicit confirmation, and the
Journal remains the only writer. This removes inconsistent primary button
sizing without altering prescription, set validation, recovery or history.
No APK.

## 2026-11-02 — Restore the complete original theme identity

Reverted the newly invented graphite/violet theme color scheme. Native
Material 3 now derives from all **20 literal Pursuit Iron themes** in the
production web source `modules/App.js` (Lime, Ember, Ice, Rose, Forest,
Mint, Clay, Graphite, Amethyst, Stone, Patrol, Slate, Lilac, Emerald,
Gold, Sage, Ochre, Rosewater, Ocean and Citrus). Each preset carries its
original background, tinted background, both card surfaces, text, muted
inks, accent, on-accent, warning and danger. Readable muted/accent/danger
inks were precomputed with the exact original `TOK` contrast algorithm.
The native M3 tonal roles retain each original surface and filled-button
accent, rather than substituting an OS-generated or designer-invented
palette. Patrol separately retains its contrast-corrected accent ink.
The first-install default is the original Lime; any of the 20 themes is
selectable through the native Appearance sheet in onboarding, startup and
the top app bar. Native selection persists in a private *appearance-only*
preference, without altering the canonical training snapshot or claiming
existing web theme settings have been imported. Shared M3 Expressive
components, motion, typography and layouts are retained. An explicit
source-parity test compares all 20 original palette inputs by exact hex
and guards the picker and saved choice. No APK, production web change or
engine rewrite.

## 2026-11-02 — Use the original contrast-safe accent ink in native UI

The shared PageHeading, outlined hero card and Today/Train journal metrics
now use the original theme's `TOK`-derived accentInk role when drawing
foreground lines/text. This preserves Patrol's duty-blue action fill while
keeping its lighter blue readable on the dark card; all 20 established
palettes retain their own surface colors. The Appearance picker has a
bounded, consistent swatch width for compact phones. The older foundation
source assertion no longer expects a fabricated two-theme palette;
a dedicated exact-hex comparison covers all 20 original inputs in CI.
No training writes or engine changes, and no APK.

## 2026-11-02 — System-bar contrast follows the selected original theme

Native edge-to-edge status and navigation icon lightness now follow the
user-selected original Pursuit theme rather than the phone's OS light/dark
setting. This matters for a light Mint, Clay, Stone, Slate, Lilac, Sage,
Ochre, Rosewater, Ocean or Citrus theme selected while Android is in dark
mode, and vice versa. Uses AndroidX's window inset controller and Compose's
SideEffect (no status-bar color hacks and no lifecycle leaks). The 20-theme
parity gate also asserts the original 10 dark / 10 light split. The
appearance setting remains independent from canonical training data. No APK.

## 2026-11-02 — Preserve original theme contrast throughout core journeys

Theme-sensitive on-surface copy in the real Today shorter-session preview,
program-save feedback, active Train shortened-workout status, saved-workout
record highlight and session-note affordance now uses the original
contrast-derived `accentInk` rather than the filled-button `primary`
accent. This is particularly important for Patrol's #2563EB fill,
whose native text ink is lighter by design. No training, progression,
program or history logic changed. Original theme inputs and UI choice
persistence stay untouched; no APK.

## 2026-11-02 — Native onboarding grouping and explicit Kotlin-engine milestone

First-time program setup is reorganized into reusable Material 3 form
sections (identity, training week, equipment, verified loading, and
preferences), retaining every real option, validated gym inventory,
recovery and authorizations. Continue, canonical program review and
explicit Save use the shared 52dp primary action, like real workout
logging. This changes presentation only, not generated prescriptions or
persisted training facts. The engine roadmap now explicitly tracks the
agreed sequence: coherent source-checked core -> developer APK and real
device feedback -> independent Kotlin training-engine redesign ->
advanced feature completion -> full release certification. Source-only
CI is not treated as physical-device acceptance.

## 2026-11-02 — One shared fact strip for active and saved workouts

Today, real Train, paged Insights history summaries and saved-workout detail
now share one compact Material 3 metric-strip surface and typography. Values
are projected only from their respective immutable journal/history facts;
no eager logging or recalculation was introduced. Saved history distinguishes
`Recorded`, `Other sets` and `Volume` without claiming that unperformed
or skipped target positions were all explicit skips. Historical correction
sheets now use the same 52dp primary action as active logging while
retaining exact retry-id operations and validation gates. All 20 original
themes apply through semantic containers and the existing accent-ink role.
No engine rewrite or APK in this pass.

## 2026-11-02 — One fail-closed Plan editing authority

The real Plan workspace now uses a single guard for saved-program entry,
program editing, gym-only editing, and opening the gym from an exercise.
This guard requires the exact owned saved program matching the current
read-only Plan revision, ready/recovered runtime, and no active workout,
canonical mutation, history read, backup export, shortened-session preview
or blocked recovery. The old Edit button could remain enabled while some
of these conditions were false; the editor would reject writes later but
mislead the user. The common gate blocks such entry and disables existing
open edit controls when runtime ownership changes. This is native UI
authorization only; canonical write validation and every old target remain
intact. It also establishes a stable UI/engine boundary for the Kotlin
replacement, rather than trusting a stale presentation as an owner.
No APK or training engine rewrite.

## 2026-11-02 — Exact history editor selection and original-theme picker contrast

The saved-workout correction UI now selects observation rows by an explicit
exercise-index/set-index key instead of encoding both as an integer with an
implicit 20-set width. Legacy merged history selection additionally binds to
its originating exercise occurrence, while preserving the original canonical
correction protocol, revision checks, confirmed changes and observed values.
The current observation protocol already bounds individual occurrence slots;
this is defensive UI identity hardening for custom-program evolution and
prevents the presentation from relying on that implicit width. A source
regression now asserts distinct keys, stable repeated-exercise selection and
the absence of packed-index matching. No existing logged data were modified.

The Appearance selector's chosen-theme label now uses the exact original
contrast-safe accent ink rather than the solid action-button accent. This
preserves readable selection feedback across the 20 existing palettes,
including Patrol. The original theme values, saved appearance choice, native
M3 Expressive components and canonical program/workout logic are unchanged.
The isolated source-contract job passed on the history fix; final checks and
Compose compilation remain separately verified CI gates. No APK built and no
Kotlin training engine rewritten in this pass.

## 2026-11-02 — Compact native set-entry controls on phone widths

The shared Reps/RIR editor now uses two balanced fields with their matching
48dp minimum hit-target minus/plus actions directly underneath. A normal
phone width (340dp of available content) keeps both independent values on
one row; very narrow layouts use the previous stacked fallback. The load
field, optional actual RIR, failed-attempt rules, user-owned values and
canonical quantization/verification remain untouched. This shared component
also updates warm-up logging, active corrections and saved-history edits
without adding any separate per-screen styling or app-owned progression.
Source assertions cover the new compact breakpoint, equal-width touch
actions, fallback and retained validated set-entry flow. Compose compilation
and Android visual/large-text acceptance remain outstanding. No APK or
training engine rewrite.

## 2026-11-02 — Adaptive shared metrics for Android large text

The single three-fact native metric strip shared by Today, active Train,
history summaries and saved-workout review now has an accessibility layout:
it keeps three compact columns at ordinary font scales and switches to
readable label/value rows when Android font scale is at least 1.35 or the
available width falls below 320dp. Values remain the precise caller-owned
journal/history snapshot, not recomputed metrics or alternative counters.
The original theme's contrast-safe first-value ink survives both layouts;
there is no new mutable state, training rule, or data write. Source checks
protect the shared adaptive rendering, with actual phone/large-font visual
acceptance and latest Kotlin compilation still required. No APK.

## 2026-11-02 — Exercise-specific zero-load copy

The shared set-entry field no longer labels all entered zero loads as
"bodyweight". The prior generic explanation was inaccurate for machines,
assistance and other verified equipment contexts. The replacement explicitly
warns that zero load does not automatically imply bodyweight and leaves
exercise-specific interpretation to the authoritative engine/loading
contract. Existing actual load values, validation, warm-up rules and journal
receipts are unchanged. The shared wording applies consistently to active
sets and corrections. No APK or engine rewrite.


## 2026-11-09 — Source-verified per-exercise Progress trends and direct History navigation

Completed after the earlier custom-work preservation/adjacent-block comparison work. The native Progress screen now has a collapsed-by-default, explicitly selected **View trend** for pinned logged exercises. Unlike the four existing published Strength Standards barbell comparisons, this is a factual completed-main-working-set metric for ANY movement ID present in the current authenticated mixed native workout history. The pure `NativeExercisePerformanceTrendDomain` groups by exact exercise ID, physical loading basis and performed rep window (1–5, 6–10, 11–15, 16–30, 31–100), converts verified lb/kg without changing journal units, and excludes warm-up, skipped, failed, amplifier follow-up, missing/invalid weight and mismatched-unit data. Unweighted bodyweight has its own *completed reps* series and never borrows assisted/weighted values.

A shared semantic Material chart, readable session-by-session value list and owner-correction-aware complete History drilldown are now available. History exercise search can open a specific trend directly **without pinning**, and Android Back returns to History; Profile favorite deep links return to Profile. All deep links recheck exercise eligibility against the current owner-verified history. Device presentation uses existing Material 3 theme tokens; no additional strength ratings or coaching prescriptions have been invented.

Pure JVM tests cover conversions, exclusion gates, physical basis and rep-band separation, custom corrections, per-session selection and bodyweight reps. A first GitHub CI run exposed a new-file Kotlin parser tokenization issue, which was corrected. The **final combined run 42570247170 passed all 33 workflow steps**, including native history recovery and Android Compose compilation. Design contract documents the remaining scope. The last validated code commit was `eebd29caa91d2226ad6a75fc75d136599efc991b`; subsequent commits only record the result and checkpoint. No APK or AAB was built.

**Next design priorities:** compact, prominent Strength Standards hierarchy; rich exercise-specific progress and Profile presentation on narrow/foldable layouts; Android accessibility and 200%-font verification; then the separately gated source-backed adaptive cycle alternative producer and exact full-cycle review/acceptance flow. **Never equate factual load/rep charts with scientific performance prediction or an engine-vs-web superiority result.** Keep native Coach/Plan/Workout sharing Material 3 components and preserve the user's explicit no-APK-until-design-complete decision.


## 2026-11-09 — Strength Standards first-screen redesign; green source verification

Strength Standards now opens with a four-lift, source-backed **Strength at a glance** chooser for bench, squat, deadlift and standing barbell press. All four cards use one best-per-lift selection from the same authenticated, corrected, BARBELL_TOTAL working-set facts used by the detailed reference view, and the canonical 1–10-rep Epley estimate. No missed or unsupported exercise is represented as zero or given an invented strength level. Cards select the native verified strength trend immediately underneath.

The reusable `NativeKotlinStrengthOverview` chooses one column on narrow/large-type screens, two on normal phones and four on wider screens; its selected semantics and background are Material 3 theme-derived. Community comparison is a collapsed, accessible disclosure. Valid explicitly entered group/bodyweight settings show a broad reference level only, without turning them into training prescriptions. A new shared `PursuitResponsiveActionPair` stacks Save/Clear at narrow widths or 135%+ text scale. The full published reference cards and methodology are a separate collapsed disclosure directly under comparison, before unrelated exercise favorites. All existing general exercise progress, navigation, theme palettes, source verification, workout journaling and coaching decisions are unchanged.

The navigation source contract checks the overview-before-settings order, single evidence calculation, disclosed details and large-text layout. CI **run 42572780090 passed 33 of 33 steps**, including headless Kotlin, web-vs-Kotlin comparisons, journal recovery and Android Compose compilation. No APK/AAB was produced.

**Outstanding:** realistic phone/tablet/foldable screen and TalkBack visual acceptance; finish cross-app shared UI and animations; complete the independently audited adaptive cycle-alternative producer and explicit review/acceptance; prove training-engine superiority using outcome-based scenarios. No APK until the complete official UI design is done.
