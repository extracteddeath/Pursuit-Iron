# R90 — Android-style interrupted interaction certification

## Observed coverage gap
The permanent B831 suite already verifies rapid route navigation, keyboard tab focus, touch ripples, sheet drag, input retention, blur recovery and reduced motion. It did not independently check cancellation of an active navigation press by `pointercancel`, release after a move crossing the scrolling threshold, or a held Space press interrupted by app blur.

## Changes
This is a **verification-only** extension to the real Chromium mobile-emulated, production-app B831 test at 320px and 430px.
- Press the actual selected Settings bottom tab, wait until the shared spring is underway, dispatch `pointercancel`, and require scale to settle to exactly 1 and ripple to end.
- Press again, move by 24px in each axis (past the existing 12px motion cancellation threshold), then release. No navigation route may change, and the original five touch targets retain >=56px layout height and live hit testing.
- Hold a real keyboard Space event against the selected nav item, interrupt with window blur, release it and prove the scale settles and the Settings route is preserved.

All three cases use the existing production event ownership. They do not install another animation system or render mock buttons. The test does not claim physical Pixel, Android predictive back, keyboard IME, TalkBack, or frame-pacing certification.

## Release scope
No UI, engine, saved state, CSS, service worker, release manifest, cache, version or build marker changes. Keep Build 871. Require all four final-commit PR workflows, especially B831 in browser integration, before merge; then check main CI and Pages independently.
