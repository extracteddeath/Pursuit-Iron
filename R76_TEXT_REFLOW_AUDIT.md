# R76 — Profile and Progress large-text readability

Baseline: R76 browser-verification milestone merged through PR #145 at `d3fce0a970f9991b561f7abbd93275a1664a6ed9`, following Build 860.

The 200%-text screenshot review exposed a presentation problem on 320px phones: the Profile summary's three metrics split whole words, and the Progress primary four-way tabs split their captions despite the rest of the app passing overflow and target-size checks.

This release keeps the existing compact ordinary-scale layout and adds named, font-relative container queries only to Profile summary and Progress tabs. At enlarged text, Profile metrics reflow as a single readable summary column; Progress choices reflow as a 2×2 grid. The source data, tab button identities, selection handlers, selected theme paint and spring motion owner stay intact. This does not touch the phone workout logger, input sizing, engine, program prescriptions or history.

The permanent R76 browser gate additionally asserts both original ordinary-scale columns and new enlarged-text columns, together with no horizontal scroll, practical primary-action target sizes and nonmutation of the saved program in two palettes/four widths. Existing 22 prior browser gates and 77 source contracts remain.

The locally prepared stylesheet, manifest, service-worker cache and build profile were finalized using `scripts/finalize-release.mjs` and passed `scripts/verify-release.mjs` on the preserved deployed Build 860 artifact. The uploaded Git blob hashes match that finalized artifact exactly. A green final-head GitHub CI run, merge, Pages deployment, and postmerge workflows are still publication prerequisites.

Physical Android keyboard/TalkBack/touch and full app-wide visual/style consolidation remain separate milestones.
