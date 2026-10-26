# R90 — Android-style interrupted interaction certification

## Current release acceptance — Build 923 candidate, October 26, 2026

R90 below is historical Build 871 browser evidence. It does not certify a physical Android device. The current delivery scope is the hosted web/PWA; Android package signing and Play publication are separate work.

Automated coverage now includes the full source/engine contracts, the independent exported engine and lifecycle audit, seven production-browser groups, content-addressed service-worker installation/update/offline recovery, backup restore/Undo, 200% text and narrow-screen layouts, and actual app-window storage ownership. M318 additionally exercises a 240-workout full App startup and five Resume/Back cycles at native and simulated 4x CPU, preserving manual input and recovery identity. Record final-commit CI, hosted-byte verification and public online/offline results in the release handoff before assigning a software readiness verdict.

Physical-device acceptance remains unexecuted. Use a disposable program or a verified backup; record the visible build, device, Android/browser versions and observed outcome for each row. Browser emulation is supporting evidence only.

| Device check | Concrete acceptance | Status |
|---|---|---|
| Install and offline cold start | Open the installed PWA, finish caching, enable airplane mode, close/reopen, and reach the same program without a blank screen. | Requires physical device |
| Keyboard and enlarged text | At 200% text, edit load/reps/RIR and notes; current inputs and Save/Next controls stay reachable above the keyboard in portrait and landscape. | Requires physical device |
| Interruption and recovery | Enter a distinct manual load, complete one set, pause, lock/background, force-close/relaunch, then resume the same session and elapsed paused time without duplicated or lost sets. | Requires physical device |
| Back and navigation | Use Android gesture Back from detail, sheet, Focus and active Workout; dismiss the top layer or present the existing exit protection without losing training. | Requires physical device |
| TalkBack | Reach named tabs, form labels, current/target/last values, completion state, error messages and dialog actions in a sensible order; focus returns after dismissal. | Requires physical device |
| Safe areas and rest | Inspect status/navigation bars, short landscape/split-screen, rest countdown and screen-awake behavior during a real session. | Requires physical device |
| Installed update and backup | Retain a manually edited session through an installed update/restart; export, review/restore and Undo a backup without changing completed facts. | Requires physical device |

Physical failures that lose training, prevent logging/recovery, trap focus or hide required actions block the affected device release. Do not mark these rows passed from CI or infer a Play-ready release from a deployed web build.

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
