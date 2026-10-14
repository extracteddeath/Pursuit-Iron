# Build 867 / R83 — Motion quality at real frame rates

Baseline: merged Build 866 / R82. Motion belongs to the existing `modules/ui-motion.js` owner; training engine 0.65.7, React app state, logger, values, route ownership, saved theme tokens and persisted history are unchanged.

## Problem and implementation

The former integrator subdivided each animation frame into exactly three physics steps, but clamped **all** elapsed time beyond 32 ms. A janky 15–20 Hz interval thus advanced only half the real elapsed time, giving the impression of slowed interactions and a laggy indicator after a dropped frame.

The revised numerical integrator processes up to 64 ms per frame, subdividing elapsed time into steps of no more than 8 ms. This allows springs to preserve roughly comparable wall-clock progress and stable damping across fast-refresh phones, 60 Hz screens, and temporarily slow frames. It retains velocity-preserving retargeting, interrupted/reversed exits, detached-element cleanup and instant settlement for reduced-motion or backgrounded content. It does not add an extra animation system.

Recorded workout dates in Progress use the existing restrained pointer-origin ink animation and native button press response. It is applied only to a `.wpb-calendar-date[data-workouts]` button. Empty dates and workout logger inputs are excluded; selection semantics and accessible hit geometry remain unchanged.

## Regression and rollout

The existing permanent B831 motion browser contract now samples the actual imported production spring module under synthetic **60 Hz and 15 Hz** callback cadences over the same 200 ms window. It requires nearly equivalent progress and tests an actual recorded calendar button for named class targeting, press-feedback data attribute and unchanged 44px-plus hit width. Previous R28–R31 lifecycle/interrupt, route, theme, phone and reduced-motion checks remain intact.

No CSS/React layout, data model, storage, third-party library or engine changes. Production JS runtime hash, offline cache revision and release manifest must exactly match this Build 867 candidate before merging. Merge only after all four CI workflows and all four phone-browser shards pass, then verify GitHub Pages and main post-merge CI. A physical Android frame-time/TalkBack acceptance pass is still required for final polish.
