# Native migration checkpoint — 2026-11-01

Branch: `architecture/android-compose-foundation`. APK production stays frozen.

The source journey covers onboarding, generated review and explicit program
save, engine-issued working sets and myo/drop extensions, durable logging,
interruption recovery, active-set correction, canonical completion, one-shot
deload consumption and saved-history corrections. This pass adds name and date
filters to paged history browsing. A follow-up adds Today, 7 days, 30 days
and This month shortcuts using local calendar boundaries. This is source verification, not device
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

## Next work

Calendar views and richer training analytics, saved-history correction of
skipped/omitted rows or failed attempts, duplicate exercise-slot history editing,
native session-adjustment controls and imported web history remain separate
milestones. Physical-device interaction, interruption and accessibility
acceptance is still required for the complete agreed journey; preserve the APK
freeze until its release gate has the required evidence.
