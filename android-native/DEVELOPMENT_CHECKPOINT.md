# Native migration checkpoint — 2026-11-01

Branch: `architecture/android-compose-foundation`. APK production stays frozen.

The source journey covers onboarding, generated review and explicit program
save, engine-issued working sets and myo/drop extensions, durable logging,
interruption recovery, active-set correction, canonical completion, one-shot
deload consumption and saved-history corrections. This pass adds paged history
browsing and older-workout correction. This is source verification, not device
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

- 106 domain JVM tests and 21 packaged-runtime JVM tests passed, with no skips.
- Compose Kotlin and instrumentation Java source compilation passed.
- Native history-page/correction, active-edit, workout, extension, deload,
  protocol, completion, history edit/volume/merge and UI source gates passed.
- Canonical export, independent source parity, snapshot identity and the actual
  packaged IIFE proof passed. Canonical training modules and the web release
  package remain unchanged (source candidate 1010).
- APK-freeze checks passed; no APK was produced.

The new JVM journey exercises four real native completions, multiple history
pages, legacy summaries, read-only access during a pending workout, correction
of an older false high, retained newer Last/receipt/cursor state, a delayed page
response overlapping completion, cancelled reads and reopening the saved store.

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

## Next work

Calendar/search and richer training analytics, saved-history correction of
skipped/omitted rows or failed attempts, duplicate exercise-slot history editing,
native session-adjustment controls and imported web history remain separate
milestones. Physical-device interaction, interruption and accessibility
acceptance is still required for the complete agreed journey; preserve the APK
freeze until its release gate has the required evidence.
