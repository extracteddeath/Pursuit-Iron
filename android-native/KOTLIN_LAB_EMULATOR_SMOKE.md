# Trial 8 Android emulator startup smoke

Run a one-time emulator installation/lifecycle test using the ALREADY
BUILT immutable source commit 1f2276c3f04c43ade653195d017ed68bd38277f8
and signed APK artifact from GitHub Actions run 42496653330. This includes
the fresh-onboarding and system-bar corrections found in Trial 7's actual
screenshots. No AAB is downloaded or produced.

Verify the original receipt and APK hash before installation. On Android
API 35: install the unique trial package, launch its Kotlin activity,
background/resume, force-stop and cold-launch, then capture real emulator
screenshots and AndroidRuntime logs. This proves only basic package/startup
behavior. It does NOT prove workout progression quality, accessibility,
backup/migration, full device acceptance or production readiness.

The startup check now rejects the false equipment-failure banner. Visually
verify that Amethyst paints the status/navigation bar backgrounds and that
the system icons remain readable after cold restart.
