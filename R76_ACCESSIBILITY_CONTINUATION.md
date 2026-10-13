# R76 — Profile, Progress and Settings accessibility continuation

Baseline: Build 860, merged PR #144 at `806ecb990b958a4568cc5be1f05f2bf240f0f3ee` (Engine 0.65.7).

## Scope

The remaining R74/R75 acceptance work extends beyond the completed Library, Wizard, Gym/Goal, Settings labels and next-workout preview gates. This slice adds a permanent real-browser check of Profile, Progress and Settings at twice the rendered text size, on 320, 393, 900 and 1280 px windows in dark Rose and light Rosewater. It measures document and current-panel horizontal overflow; practical target geometry of Profile edit, Progress destination tabs and Settings jump selector; browser exceptions; and unchanged saved program after typography inspection. Reduced motion stays enabled. Screenshots are uploaded by the existing phone/offline workflow.

This is deliberately a verification-only candidate. It does not modify the training engine, saved data model, app stylesheet, runtime, version, service-worker cache or shipping UX. The original 21+ browser gates, 77 source contracts, 36 independent engine suites and release-integrity requirements remain unchanged. If a new assertion reproduces a defect, repair the owning surface without loosening the assertion before merge.

## Still required

Physical Android IME, TalkBack, safe-area and touch/trackpad acceptance; remaining app-wide forms and controls; a comparative light/dark visual audit; stylesheet owner consolidation. Keep one navigation graph, the compact phone logger, themed materials, no broad gray/gradient overrides and interruptible/reduced motion. Do not call the roadmap complete from browser emulation alone.
