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
