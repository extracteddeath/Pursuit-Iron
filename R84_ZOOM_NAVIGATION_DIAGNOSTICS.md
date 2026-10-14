# R84 — Responsive primary navigation reliability at 200% zoom

## Evidence

Build 866 final pre-merge phone/browser checks were green. A **post-merge** browser integration run then timed out at `verification/r65-adaptive-shell-browser-test.mjs:515`: after a wide screen was resized to a 200%-zoom-equivalent CSS viewport, the synthetic user tapped Plan but `[data-view-frame="plan"]` never appeared within 30 seconds. Re-running only the affected integration job on the same commit passed. Neither a confirmed production navigation defect nor harmless CI timing can be claimed from this result alone. Tracking issue: [#155](https://github.com/extracteddeath/Pursuit-Iron/issues/155).

## Diagnostic ownership

This R84 slice changes only the **permanent real-browser integration test**, not the React route handler or saved data.

- Wait for **actual pointer hit geometry** (button width/height and `document.elementFromPoint`) following rail-to-compact viewport changes, not a fixed sleep.
- Capture a real browser-trusted click on the target native tab and immediately correlate selected `aria-current=page` with the route mounted in `data-view-frame`. Neither route is inferred solely from button color.
- On a missed tap or failed route commit, include viewport/device scale, button dimensions, DOM hit stack, overlays, previous/current route, and click delivery in the failing assertion; save a browser screenshot.
- Keep original 200%-zoom overflow/44px target checks, full five-screen navigation, keyboard arrows, exercise library focus recovery, workout history/calendar and unchanged saved program.
- **Never auto-retry clicks to hide a real bug**. The 6-second targeted check is shorter and more informative than a generic 30-second selector timeout. Testing can distinguish a blocked hit target from a delivered click that did not change React state.

This establishes a reproducible diagnostic boundary before changing app navigation or motion. It is not physical Android, TalkBack, IME or INP certification; those still require real-device review. The next production change, if evidence warrants it, should address the identified target, not introduce a second router or global pointer gesture.
