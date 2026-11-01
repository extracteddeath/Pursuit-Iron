# Pursuit Iron — native Android experience preview

This is a **feature-first native design preview** on a separate development branch. It intentionally does not clone the web layouts. The existing app's features and canonical JS training engine remain the behavioral source of truth.

## APK freeze until the full workout journey is finished

**No new native Android APKs will be built for the remainder of the
architecture, onboarding, workout runtime, persistence and completion work.**
Both installable-preview and instrumentation-device workflows are explicitly
job-disabled; even manual dispatch cannot generate an APK. Routine verification
is limited to Kotlin/Compose compilation, JVM tests, canonical engine
regression suites and Java instrumentation-source compilation.

Unblock APK production only in a deliberate later change, after the same
native revision has end-to-end evidence for: onboarding -> program creation ->
verified personal workout -> set logging -> interruption/resume -> workout
completion -> saved history and correct next-workout/progression decisions.
The source implementation now covers the first standard working-set journey.
Device acceptance and the remaining advanced-session behavior are still pending;
the build freeze remains active.

The instructions below describe how **older previously produced** artifacts
could be installed. They do not authorize new builds before the release gate.

## Install and view

Open the most recent successful **Native Android preview APK** run at
https://github.com/extracteddeath/Pursuit-Iron/actions/workflows/android-native-preview.yml
and download the artifact `pursuit-iron-native-preview`. Extract the ZIP and install `app-debug.apk` on Android. It's debug signed with a different certificate on each CI run; to replace an old native preview, uninstall the old **Pursuit Iron Native Preview** first. The package has a separate `.preview` suffix, not the future production identity.

Alternatively, open `android-native/` in Android Studio, provision JDK 17, Android SDK 37 and Gradle 9.3.1, and run the debug `app` module.

## New app UX direction

- **Today** is one training decision center: next action, readiness and temporary session adjustments.
- **Train** is the focused single-session workspace: exercise switching, weight/reps/RIR steppers, last/target, rest, notes and contextual tools. Focus mode changes display only.
- **Plan** is the complete programming journey rather than a collection of old settings screens: demo goal, days and split-adaptation controls.
- **Insights** will present correction-safe personal records, training trends, volume and milestones after verified data migration.
- The top-right feature map displays the **capability contract**, not a checklist of completed features.
- Phones use bottom navigation; wider screens use a navigation rail. Cards and motion are shared Compose primitives, not CSS.

Native onboarding can prepare and explicitly save a new program through the
packaged canonical engine. Its saved program can start a real next workout,
record working sets, resume and publish native training history. Other
interactive examples remain local UI-only state. Web history and backup import
are separate from this native store.

## Engine-issued native working-set journey

Today and Train now open the saved program's actual next day/week through
`nextSessionCursor`, then use the web logger's `buildLifterModel`,
`calibratedAnchorPerf`, `sessionSuggestion` and `prescribeSets` path. Exact
issued rows, including decision traces, are atomically pinned with their source
snapshot before the journal opens. Restart restores those bytes and committed
observations; it never calculates replacement targets for the pending workout.

Complete and Skip acknowledge journal writes. Blank load cannot become zero,
and optional actual RIR starts empty rather than inheriting target RIR. Finish
requires every working slot to be resolved, publishes canonical history before
recovery cleanup, and preserves the targets actually issued alongside manual
observations. A durably finished workout can retry after restart without a
duplicate history entry; the next session uses the advanced canonical cursor.

Warm-up targets are displayed as references. Advanced myo/drop sub-sets and
one-shot deload consumption reject session creation instead of silently dropping
engine instructions. These features, richer logging tools and device acceptance
remain incomplete. The full-loop proof is currently actual-bundle Node/JVM
integration plus Compose/source compilation. It does not certify an Android
device, and no APK build is enabled by this change.

## Implementation boundaries

- `domain-contract/FeatureCatalog.kt`: feature outcomes and certification gates.
- `FEATURE_FIRST_ARCHITECTURE.md`: source-backed parity inventory and end-to-end acceptance scenarios.
- `app/NativeComponents.kt`: native shared presentation primitives.
- `app/NativeScreens.kt`: screen journeys with only demo UI state.
- `app/WorkoutOutlinePanel.kt`: shared native Material 3 exercise-outline presentation, showing explicitly illustrative layout examples until a verified stored user program exists.
- `domain-contract/OutlinePresentation.kt`: fail-closed synthetic/read-only presentation gate; it never accepts an unverified personal program.
- `app/NativePreviewApp.kt`: adaptive shell, contextual sheets and explicit unconnected engine state.
- `modules/engine-api.js`: unchanged JavaScript canonical training owner.
- `engine-bridge/`: a verified, bounded read-only JS protocol running in an AndroidX sandbox for synthetic progression, exercise-catalog and audited workout-outline tests. The canonical engine and web app remain unchanged.
- `data-migration/`: read-only canonical backup validation, not native import.

## Android preview build cadence

Normal Android commits run the fast foundation and canonical-engine checks, **not** a new APK build. The preview and instrumentation APK jobs are hard-disabled until the complete onboarding-to-workout-completion milestone is certified; edits and manual dispatch cannot build them. This keeps experimental UI/engine work from filling Actions with redundant APKs. The most recent successful artifact remains available for 14 days.

## Verification gates

GitHub Actions must pass the native APK build and JS protocol, backup and feature-contract tests. A successful APK build is not evidence of real-device visual, interaction, accessibility or interruption testing. Full training functionality remains disabled until canonical engine parity, storage round-trip, revision identity, and Android device certification are complete.

## Toolchain

The installable preview uses pinned **AGP 9.1.1**, **Gradle 9.3.1**, **Android SDK 37**, **Compose compiler plugin 2.4.21**, **Compose BOM 2026.09.00**, **Material 3 1.5.0-beta02 (native preview only)**, **Activity Compose 1.11.0**. The preview explicitly opts into the published expressive MotionScheme and theme-aware navigation motion. This beta dependency is isolated from web production. The required SDK 37 / AGP 9.1 migration is in this branch and must compile successfully before a native milestone is considered ready. Native compile, real-device motion and accessibility acceptance are separate requirements.

## Verified read-only Android foundation (not yet real workouts)

Milestone six's isolated AndroidX runtime can execute the canonical generator on a fixed synthetic profile, project audited exercise order/sets/rep bounds/RIR/rest/duration, and decode through Kotlin. The native **Plan** view now has an accessible, expandable read-only layout example. It is intentionally illustrative: `WorkoutOutlinePanel(outline = null)` means the installable UI does not access private workouts or claim to show the user's real program. `OutlinePresentationPolicy` independently requires correct canonical source, synthetic-only/read-only provenance and finite bounded values for any future test output.

The separate data-migration contract can now derive a bounded saved-program
structure envelope from an exact canonical v13 snapshot and active reference.
Its strict Kotlin decoder and shared synthetic fixture establish the
language-neutral wire shape, not a native importer or trusted personal plan.
The Plan screen now has a shared read-only Compose structure panel guarded by
an independent current-reference/tombstone check. The installable preview
passes no program or reference, keeps `outline = null`, and neither reads nor
logs any user backup.

The `Native Compose UI compile (no APK)` workflow compiles source edits without making a distributable build. A new installable APK is still produced only by the existing explicit `PREVIEW_MILESTONE.md` gate. Next milestones must implement a schema-validated, revision-aware **read-only user program reference** and safe data import before promoting any personalized workout outline; workout logging remains disabled.

### User-selected backup screening (not an import)

The native Plan preview can select a backup copy through Android's document
picker, stream it off the UI thread, enforce 50 MiB, and display a temporary
byte count/fingerprint. It does **not** validate JSON, import data, grant
persisted access, populate Plan with real workouts or enable training.
The current-revision program gate stays disconnected from this feature.

### Explicit, bounded read-only schema interaction

The document picker now has a separate, injection-gated read-only validation
path. When a certified canonical verifier becomes available, a deliberate
new picker action reads one complete copy, captures only up to 128 KiB and
drops those bytes after canonical schema checking. Full-sized files stay
fingerprint-only; the UI cannot imply that large or truncated files passed.
The ordinary preview remains unconnected.

### Opt-in, packaged read-only canonical verifier for native preview

The app now depends on the isolated `engine-runtime` **only through**
`PackagedReadOnlySchemaVerifier`, which exposes the schema-count inspector,
not an engine gateway. `UnconnectedEngineGateway` still owns training and
all workout actions remain disabled.

An ordinary source checkout has **no canonical bundle assets** and therefore
`openIfPackaged` yields null; there is no network loading or implicit
user-file access. The opt-in preview APK **milestone workflow only** exports
the exact canonical snapshot, checks parity, compiles the allowlisted
offline IIFE and checks the actual bundle exports. It then packages the
local bundle and matching signed-app asset manifest *only for debug builds*.
Android verifies the bundle checksum and fail-closed certification flags
before creating the isolated worker. An explicit user-selected small JSON
copy can then return **collection counts only**; app lifecycle closes the
worker without blocking the UI.

No full-backup import or private workout history access is enabled. Large
backups are fingerprint-only, and no current-snapshot program reference
is obtained from this metadata-only source. A device-tested preview APK is
still a future milestone; no APK is built by ordinary source commits.

### Canonical saved-program inventory in a backup copy

The same optional, checksum-pinned debug-only verifier now provides a second
explicitly chosen document action, **View saved programs in backup**. For
whole sources within the small-copy bound, the Android JavaScript sandbox
uses the canonical v13 parser to enumerate at most 24 active saved entries,
honors equal/newer deletion clocks, rejects duplicate program IDs, and
returns only names, training-day counts and exercise-slot counts. Those
programs are clearly identified as **contents of the selected backup**, not
as active or current Plan data. No exercises, workout weights, RIR,
history, editing, import, or persistence are exposed.

Unlike the live `SavedProgramOverviewPanel` path, this backup catalog
does not assert that the copy is the most recent store revision. It is
separately typed and must never be forwarded to active Plan presentation.

### Native shared backup-only program cards

`BackupSavedProgramInventoryPanel` presents the saved-program inventory
with shared M3 surface/shape/typography tokens and a prominent backup-only
label. It is separate from the active `SavedProgramOverviewPanel` gate:
no card is clickable, and no saved backup row can activate a training
program. The actual API-36 emulator gate tests the canonical projection
with an equal-time deletion and synthetic private history.

### Cold-start scheduling and Activity lifecycle

The optional embedded read-only canonical JavaScript bundle can be several
megabytes. Native startup now composes the first frame **before** the app
opens its assets, reads the manifest, verifies SHA-256 or creates its
sandbox service. A bounded local IO coroutine performs those checks, and
the UI shows a neutral verifying state until it completes. No network or
user-file reads are involved. Even if the Activity is destroyed during
asset loading, the noncancellable Main-thread handoff closes the newly
constructed verifier rather than installing it into a stale Activity.
Missing/invalid assets remain fail-closed; training functionality remains
unconnected. Routine changes require only Compose compilation, not an APK.

### Canonical workout store transaction foundation

The unconnected native storage owner now retains one canonical snapshot,
the pending session's exact source snapshot and a durable history receipt in
one atomic file. It consumes canonical JS completion candidates, rechecks
the current store after engine execution, and clears recovery only after
confirmed history publication. Restart/fault tests use the actual canonical
JS golden history result shared with Kotlin, including simultaneous finish
attempts and crashes during cleanup. App-private Android writes explicitly
sync and verify their committed bytes.

The process runtime instantiates this owner for native first-program creation
and engine-issued working-set sessions. APK generation remains frozen.

### Packaged canonical completion runtime

`SandboxCanonicalWorkoutStoreEngine` connects the checksum-pinned JavaScript
worker to native store initialization and completion preparation. Its dedicated
bounded channel is separate from ordinary engine diagnostics and backup-only
metadata; it has no file handles and never writes history itself. Kotlin binds
responses to the exact request ID, source/current snapshots, journal and candidate
hashes. Cancellation discards late responses before native publication.

Completion and reference validation now share environment-neutral JS between Node
and Android. UTF-8/SHA-256 identity is compared to independent native-crypto
vectors and Unicode cases; training calculations remain in `modules/engine-api.js`.
One allowlisted source manifest owns bundle packaging for all workflows.

The source-only runtime workflow builds the actual pinned IIFE, runs Kotlin
store/journal integration through that bundle in a restricted Node VM, and
compiles the Android sandbox and instrumentation sources without an APK. That
host proof covers lost commit acknowledgements, canonical history, retry and
cancellation; it is not Android device certification. Native session execution
is limited to the issued working-set flow described above.

### Native onboarding and first-program save

The app now opens a three-step setup, equipment and generated-program review
flow. Goals, experience, units, split, days, session range, equipment, bodyweight,
supersets and barbell-per-day limits are carried to the real canonical generator.
Split/day choices come from its compatibility authority. Review has no writes;
Save initializes a new private store and exact retries acknowledge the same
creation. Existing, corrupt or incompatible data is never replaced as fresh setup.

One Application-owned runtime survives Activity recreation and owns the pinned
worker and store. Saved first-program identity is restored through canonical
inspection; Plan reads current structure off the main thread. Late/cancelled
generation results cannot replace a newer action. The issued-session flow now
adds working-set logging and completion; no new APK has been generated.
