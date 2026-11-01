# Pursuit Iron — Android-first Compose preview

This is an **isolated native Android design preview**, not a replacement for the live web app. It uses Jetpack Compose, Material 3 dynamic color, Android native navigation and demo-only Workout controls. The canonical JS engine is deliberately disconnected. No existing program, history or backup is read, written or synced.

## View the Android app on a phone

1. Open [GitHub Actions → Native Android preview APK](https://github.com/extracteddeath/Pursuit-Iron/actions/workflows/android-native-preview.yml).
2. Open the **latest successful** run for the `architecture/android-compose-foundation` branch.
3. Under **Artifacts**, download `pursuit-iron-native-preview` (a ZIP).
4. Extract the ZIP on your Android device or computer, then install `app-debug.apk`. Android may ask you to allow installation from the Files app or browser temporarily. Only install artifacts from your own repository's verified workflow.
5. Launch **Pursuit Iron Native Preview**. Navigate Today, Plan, Workout and Progress. The demo steppers change temporary UI state only; no workouts can be started.

Debug APKs use CI-generated test signing keys. A newer preview may fail to install over an older one; uninstall the old **Native Preview** first and then install the new APK. It uses a separate `.preview` package ID and must not replace a future Play Store version.

**If no successful run or artifact is present, there is no installable APK yet.** The branch alone is source code, not a runnable app. You can also open `android-native/` in Android Studio with JDK 17 / Android SDK 36, provision Gradle 8.13 and run the `app` debug configuration.

## Architecture

- `app`: Android Activity, Compose Material 3 theme, native preview navigation and sample Workout controls.
- `domain-contract`: Kotlin-only engine gateway contract, explicitly unconnected in this preview.
- `engine-bridge/protocol.mjs`: allowlisted, read-only protocol referencing canonical `modules/engine-api.js`. Handshake does not certify engine parity.
- `data-migration/`: read-only structural and canonical-parser verification of explicitly supplied backup copies; native import is not enabled.
- Production training logic remains in `modules/engine-api.js`; `modules/next-engine/` and `modules/training-domain/`.

## Release gates not met

Actual Android runtime engine execution; fixture equivalence for load rounding, double progression, RIR and cycles; native backup import/round-trip; real-device accessibility, IME, text scaling and interruption certification. Debug APK is **not** a production Play Store release.
