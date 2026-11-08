# Trial 7 Android emulator startup smoke

Run a one-time emulator installation/lifecycle test using the ALREADY
BUILT immutable source commit fe5237d85d1b45c6e34a7ab745f71958655605fc
and signed APK artifact from GitHub Actions run 42495295715. This is the
APK-only Amethyst/Target/Last milestone; no AAB is downloaded or produced.

Verify the original receipt and APK hash before installation. On Android
API 35: install the unique trial package, launch its Kotlin activity,
background/resume, force-stop and cold-launch, then capture real emulator
screenshots and AndroidRuntime logs. This proves only basic package/startup
behavior. It does NOT prove workout progression quality, accessibility,
backup/migration, full device acceptance or production readiness.
