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

The `Native Compose UI compile (no APK)` workflow compiles source edits without making a distributable build. A new installable APK is still produced only by the existing explicit `PREVIEW_MILESTONE.md` gate. Next milestones must implement a schema-validated, revision-aware **read-only user program reference** and safe data import before promoting any personalized workout outline; workout logging remains disabled.
