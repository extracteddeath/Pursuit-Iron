# M217 — Android workout recovery

Pursuit Iron 4.0.0 build 807 · Pursuit Engine 0.64.10.

Baseline: M216 build 806, commit `634353b1d5903b4029f8c9fa61378a059e787bdd`.

## What changed

Completing a workout now saves its completed history, performance values, and consumed one-shot deload in one store write before changing screens. The live recovery snapshot is removed after that write succeeds. A rejected save leaves the workout and its recovery copy intact, displays the existing retry action, and does not announce success. The finish action shows its pending state and blocks repeated taps during the write.

Each new or resumed workout receives a stable session ID. The same ID travels into completed history. If an interruption prevents removal of the recovery snapshot after history commits, startup and resume recognize the already-logged session and cannot offer it as a second workout. Older live snapshots acquire their ID when first saved by this release; their existing sets, notes, paused state, and timing remain readable.

Minimize and the Android Back bridge now require a successful live snapshot save before closing the workout. The existing update prompt remains deferred during workouts and while the resume dock is visible. An explicit update restart also waits for the shared store writer to save the current programs, drafts, settings, and history; failure leaves the current app open.

Screen-awake ownership is tied to the workout lifecycle. Concurrent foreground events share one pending request. Backgrounding releases an active lock, foreground/page restoration can reacquire it, and requests that resolve after closing the workout immediately release their result. Unsupported or denied requests do not interrupt training.

The installation manifest permits landscape and split-screen use, removes the stale engine version from its description, and is now included in release hashes. Build 807 precaches the new lifecycle module as part of 74 offline entries.

## Verification

The new browser gate mounts the complete production app with its real persistence, navigation and service-worker code. Its fixtures use a ten-week custom program and a partially completed workout with typed values, a note, and paused workout/rest timers.

| Check | Evidence |
|---|---|
| Regression reproduced on M216 | The new gate fails because system Back closes a workout after a rejected `wpb:live` save |
| Failed minimize and completion | Injected storage failures keep the workout open; a failed completion preserves the snapshot and writes no history |
| Actual successful completion | The real App store contains one completed session with the same stable ID and performed-set data |
| Interrupted cleanup | Deliberately blocked snapshot deletion cannot show a resume dock or create a duplicate after reload |
| Android Back ownership | Browser-history Back closes the workout sheet before minimizing; successful minimize/resume retains typed values and paused timers |
| Numeric entry | Load → Reps → Done focus flow preserves typed values and does not mark sets complete |
| Offline recovery | A real Chromium renderer crash followed by a new offline page restores the same workout, values and paused rest timer through the installed service worker |
| Geometry | Production workout bounds fit 320 × 740, 390 × 844 and 844 × 390 CSS-pixel viewports |
| Screen-awake races | Deferred platform responses, background/foreground, page restoration, denial, duplicate events and unmount cleanup pass the lifecycle unit gate |
| Recent fixes | Existing browser gates retain custom partials/myo-reps, manual and blank rep ownership, eight variation previews, compact duration editing and cycle duration consistency |
| Store/engine agreement | Existing Auto-fix and executable-prescription browser gates retain canonical save/reload behavior and performed-set provenance |
| Release/update integrity | Full release checks and the real waiting-worker → Restart → reload/cache-rotation browser gate |

The lifecycle unit gate is part of release verification. The production browser gate is part of the permanent Phone and offline integrity workflow.

## Scope and remaining device evidence

The training engine and its prescription policy remain at 0.64.10. M215/M216 phase-duration ownership and recent late-week custom intensifier repairs carry forward.

Browser Back exercises the app's Android bridge, and renderer termination exercises restoration without React memory. These checks do not certify a physical phone's system gesture, Android force-stop, OEM battery restrictions, soft-keyboard geometry, 200% system font scale, or notification delivery. M188's physical Android checklist remains the place to record those results; this release does not mark its unrun cases as passed. This is an installed PWA release, not an APK or Play Store package.
