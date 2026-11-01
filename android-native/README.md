# Pursuit Iron — native Android experience preview

This is a **feature-first native design preview** on a separate development branch. It intentionally does not clone the web layouts. The existing app's features and canonical JS training engine remain the behavioral source of truth.

## Install and view

Open the most recent successful **Native Android preview APK** run at
https://github.com/extracteddeath/Pursuit-Iron/actions/workflows/android-native-preview.yml
and download the artifact `pursuit-iron-native-preview`. Extract the ZIP and install `app-debug.apk` on Android. It's debug signed with a different certificate on each CI run; to replace an old native preview, uninstall the old **Pursuit Iron Native Preview** first. The package has a separate `.preview` suffix, not the future production identity.

Alternatively, open `android-native/` in Android Studio, provision JDK 17, Android SDK 36 and Gradle 8.13, and run the debug `app` module.

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
- `app/NativePreviewApp.kt`: adaptive shell, contextual sheets and explicit unconnected engine state.
- `modules/engine-api.js`: unchanged JavaScript canonical training owner.
- `engine-bridge/`: read-only protocol; it has not yet been executed inside an Android JS runtime.
- `data-migration/`: read-only canonical backup validation, not native import.

## Verification gates

GitHub Actions must pass the native APK build and JS protocol, backup and feature-contract tests. A successful APK build is not evidence of real-device visual, interaction, accessibility or interruption testing. Full training functionality remains disabled until canonical engine parity, storage round-trip, revision identity, and Android device certification are complete.

## Toolchain

The installable preview uses pinned **AGP 8.13.2**, **Gradle 8.13**, **Android SDK 36**, **Kotlin 2.2.21**, **Compose BOM 2025.10.01**, **Activity Compose 1.11.0**. Later Material 3 Expressive APIs requiring API 37/AGP 9.x are deferred until the compatible SDK is available and builds pass. The native design system can evolve independently of these versions.
