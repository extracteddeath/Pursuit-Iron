# Trial 2 Android emulator startup smoke

Run a one-time emulator installation/lifecycle test using the ALREADY
BUILT immutable source commit bf977ff049d5c16b13d9862cbd1386e1cd3e8c89
and signed APK artifact from GitHub Actions run 42481026405.

Verify the original receipt and APK hash before installation. On Android
API 35: install the unique trial package, launch its Kotlin activity,
background/resume, force-stop and cold-launch, then capture real emulator
screenshots and AndroidRuntime logs. This proves only basic package/startup
behavior. It does NOT prove workout progression quality, accessibility,
backup/migration, full device acceptance or production readiness.
