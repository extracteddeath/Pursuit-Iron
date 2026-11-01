# Pursuit Iron — native Android experience preview

This is a **feature-first native design preview** on a separate development branch. It intentionally does not clone the web layouts. The existing app's features and canonical JS training engine remain the behavioral source of truth.

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

All interactive examples are **local UI-only state**. They do not generate training targets, log completed sets, modify web history or import user backups.

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

Normal Android commits run the fast foundation and canonical-engine checks, **not** a new APK build. An installable preview is built only when `android-native/PREVIEW_MILESTONE.md` is updated, the APK workflow changes, or a maintainer manually runs the workflow. This keeps experimental UI/engine work from filling Actions with redundant APKs. The most recent successful artifact remains available for 14 days.

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
