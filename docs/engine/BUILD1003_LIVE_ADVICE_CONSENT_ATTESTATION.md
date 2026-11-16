# Build 1003 — Live load advice consent diagnostic gate

## Change and scope
Issue [#418](https://github.com/extracteddeath/Pursuit-Iron/issues/418), PR [#433](https://github.com/extracteddeath/Pursuit-Iron/pull/433).

A valid two-miss observation does **not** authorize the app to reduce an untouched, prescription-owned pending set. Before this change, the opt-in `auditWorkoutProgressionReplay` diagnostic exempted *all* pending rows with a live adjustment from its ordinary target/load parity check. An advisory-only `suggest_pending_load_reduction` could therefore mask a silently changed load if the original effort observations were genuine.

`modules/next-engine/workout-replay.js` now independently checks that the pending load is unchanged from its pre-advice snapshot, confirms the advice agrees with the source action, and flags legacy automatic `reduce_pending_load` actions on app-owned pending sets. Manual, user-owned reductions remain valid. The diagnostic reports violations; it does not overwrite or silently restore a potentially user-authored saved value.

`verification/m232-live-autoregulation-test.mjs` now covers the unchanged real suggestion, five adversarial mismatches/unauthorized load edits, valid manual consent via set ownership, and serialized-session replay. Neither the performance evaluator, the original workout prescription, nor the UI banners or steppers are modified.

## Evidence and release
- The exact-source draft branch passed the source integration contract and all **seven** Chromium browser groups.
- The monotonic release stage completed successfully and finalized **Build 1003** with app version 4.0.0 and the existing engine version 0.65.9, including `RELEASE_MANIFEST.json`, `BUILD_PROFILE.json`, `index.html`, `modules/App.js` and `sw.js`.
- Final independent release, engine, browser, hosted and post-merge checks must run on the finalized commit; passing the draft suite alone is not a publication approval.
- Full load-decision provenance, inventory/plate agreement, actual PR notification truth, undo/review lifecycle and physical Android accessibility/offline replay remain distinct launch gates under issue #418. **Do not close the issue or claim physical-device certification from Chromium.**
