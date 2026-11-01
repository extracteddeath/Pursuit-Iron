# Pursuit Iron — Android-first Compose foundation

This is a new, isolated Android presentation layer. It does **not** replace the hosted web app, change user data, alter the canonical JavaScript training engine, or claim engine parity.

## Build prerequisites

Android Studio with Android SDK 36 and JDK 17. Open `android-native/` as a Gradle project. The Gradle wrapper JAR and Android SDK are not checked into this initial scaffold; generate the wrapper in a trusted local Android Studio environment and pin/verify its distribution before using CI. The versions below are **bootstrap pins**, not a claim that they are the newest Google releases. Verify compatibility against official Android documentation before promoting to release.

## Architecture

- `app`: Compose Material 3 theme, native Android Activity, accessible app shell and a static workout preview.
- `domain-contract`: Kotlin-only interface and explicit engine readiness state. **No fake training suggestions.**
- `modules/engine-api.js` remains the source of truth in the web app. No engine files are copied or rewritten in this scaffold.
- Future `engine-bridge`: a strictly isolated JS runtime adapter with canonical export parity tests; Kotlin engine migration can follow only after golden-fixture equivalence.
- Future `data`: versioned, non-destructive JSON import from a user-exported web backup; never overwrite existing backups or silently initialize a new program.

## Gates before any release

1. Compile on Android SDK 36, verify Material3 dependencies and theme with Android Studio.
2. Confirm dark/light, system dynamic color on Android 12+, text scaling, TalkBack, 48dp minimum targets, reduced-motion and edge-to-edge/insets on real hardware.
3. Build a deterministic, JSON-safe engine contract; replay real program/history fixtures against the canonical JS engine and bridge, checking weight increments, DP, RIR, cycle transitions, injuries, time trims and revision ownership.
4. Import a **copy** of a real backup; verify schema/version/hash and round-trip without loss. No writes to web storage or Play release until approved.
5. Rebuild one complete Workout vertical slice and exercise interruptions/background/resume before migrating other screens.

**Current status:** source scaffold only; not compiled, not device-tested, not Play-ready.
