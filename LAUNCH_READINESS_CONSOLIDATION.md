# Pursuit Iron — Consolidated launch-readiness program

Verified production baseline at the start of B995: main efd7a581c4b5 (Build 988; engine 0.65.9). This is a working checklist, not a declaration that the app is launch-ready.

## Delivery policy
- Batch related source changes on focused branches; avoid bumping the public build for every styling edit.
- Keep a passing main branch and use PR checks before integration. Do not alter the engine for presentation-only work.
- At a release checkpoint, regenerate the app/build identifiers, manifest hashes, service-worker revision and changelog together using the canonical release finalizer.
- Validate the *merged* commit and the hosted site's actual revision, not just the branch. No launch-ready claim until the final gate is complete.

## Styling and layout workstream
- Measure CSS size, !important declarations, dead selectors and fixed inline-style objects at each checkpoint; budgets may decrease, never silently increase.
- Consolidate by component owner, starting with Workout (timer, controls, set editor, rest), Program (cycle/preview and controls), then Home, Library, History, and dialogs.
- Prefer shared M3 tokens and semantic state/data attributes to fixed React style objects; retain genuinely dynamic geometry and progress.
- Verify dark/light/forced colors, 320–foldable widths, 200% text, safe-area insets, keyboard resize, touch targets, focus order and reduced-motion behavior.
- Avoid new overrides to hide underlying ownership conflicts; audit CSS cascade and remove unreachable rules only after reference checks.

## Engine lifecycle workstream
- Creation -> schedule -> exercise prescription -> set logging -> complete -> history -> progression -> next session -> cycle adaptation -> undo.
- Check equipment compatibility, machine/plate increments, fatigue-aware double progression, PR events only on actual records, RIR, amplifier timing, and program-volume constraints.
- Maintain deterministic seeded fixtures and browser-backed persistence/refresh tests. No hidden or automatic weight reductions.

## Final release gate
- Canonical release integrity and independent engine audit.
- Core contracts plus all seven browser shards, including workout, program, lifecycle, data, accessibility, motion and integration.
- Hosted app and service-worker cache hashes match main; no stale PWA update or build-number mismatch.
- Physical Android acceptance: rotation, IME, TalkBack, offline/resume, installed PWA upgrade, interruption and back gestures. Record device-tested versus simulated coverage explicitly.
- Ship only after high-priority regressions are resolved; record any remaining nonblocking issues and a rollback point.

## Current checkpoint — 2026-10-29
- **Released main:** Build 988, commit `efd7a581c4b55aeb936b10ff5357b564cc9178ef`, Engine 0.65.9. GitHub Actions release integrity, core contracts, hosted-webapp, offline/phone, independent engine audit and Pages deployment are green on this commit.
- **CSS debt (B994 published budgets):** app stylesheet 643,592 characters, `!important` occurrences at most 2,060, `modules/App.js` fixed inline `style: {` objects at most 1,101. These are ceilings, **not** claims that presentation debt is finished.
- **B995 source-only candidate:** PR #414 on a draft branch; resolves stale Program schedule IDs before calculating training/rest counts, corrects spoken schedule action names, and improves accessible settings, 1RM, and clipboard statuses. Adds B995 source contract and real Program schedule Chromium 320px/200% check. No engine/persistence algorithm change. This is **not** an integrated release.
- **B996 large UI/UX consolidation in the same draft:** shared Program Save Review/History correction focus owner, two-stage destructive Exercise Options Escape and focus restoration, MotionReveal ResizeObserver fallback, semantically accessible major/sub-muscle meters and expanded contributor regions, improved Progression search/results, and keyboard-viewport sizing for Program Schedule/Reference/Progression/Save Review. No prescription, persisted history, or progression algorithm change.
- **B996 styling debt:** 643,206 CSS characters (down 386 from Build 988; includes shortened historical CSS commentary and changed viewport owner rules); still 2,060 `!important` occurrences, with **no new** priority overrides. No claim that all static App styles are retired.
- **B992/B995/B996 source verification:** 75 in-memory assertions passed across three targeted contracts; 11 changed modules passed syntax checks. New B995/B996 Chromium fixtures were authored and syntax checked but **not yet confirmed passing** in GitHub Actions. A draft-only source/browser workflow was added to test the Program and accessibility shards without bumping the public build.
- **B997 follow-through in this same draft:** first draft CI identified a real keyboard-focus loss when Clear Schedule removed its own focused button; corrected by focusing the stable Close action before parent state updates. Updated the legacy Exercise Options ownership contract for the intentional confirmation-first Escape behavior. The shared Program focus hook now also owns SessionChoiceDialog, preserving protected Escape semantics while removing **1,841 characters** of duplicated dialog focus/trap code. Reworked M3-sized Program search/filter/review/confirmation controls and removed exact redundant Workout completion CSS declarations.
- **Current stylesheet:** **643,157 characters**, 435 fewer than published Build 988; **2,060 `!important`** occurrences, no net new priority overrides. B959/B992/B996 targeted source validations are passing in an in-memory harness; the new exact-head GitHub Chromium/CI gate remains authoritative and may still reveal issues.
- **Draft CI failures and remediation:** initial source gate reported an outdated `ui-ownership-test` assumption; initial Program browser gate timed out after Clear Schedule removed focus. Both are now corrected and the B995/B996 browser tests check focus transfer and reduced-motion viewport behavior. Do not declare those tests passing until GitHub confirms.
- **B998–B1002 consolidated candidate:** Home outline uses unique `useId` IDs; Plan review banner and training-max actions announce live state; Workout set controls have bounded steppers, 48px navigation targets and true 200%-scale text; Program dialog focus is shared across screens, including confirmation-safe Escape/backdrop handling. Seven browser shards exposed older tests with fixed accessible-label/ID assumptions; those tests now follow the real semantic controls.
- **B1002 CSS consolidation:** merged nine adjacent identical M3 declaration-rule pairs and three equivalent forced-colors blocks. Retired seven Exercise Detail properties identically shadowed by later canonical selectors. **642,573 CSS characters** and **2,035 `!important` occurrences** (Build 988: 643,592 and 2,060). Updated B983 budget ratchet; no engine or prescription code modified. This is a real but intentionally conservative reduction, not a declaration that remaining CSS debt is solved.
- **Guarded release preparation:** top-of-file Build 989 changelog entry staged; draft-only Actions job will run canonical one-build finalizer **only if** all seven Chromium shards and integration source contracts pass at an exact PR head. The generated release must also pass the repository's release verifier; any failure leaves the PR unmerged. Ready-for-review Core, engine audit, hosted-webapp, release-integrity, and device checks remain separate gates.
- **Release next:** finish any compatible cleanups in the draft, then finalize **one** monotonic build through `scripts/finalize-release.mjs --next-build`, run exact-head CI and all seven Chromium shards, merge only once checks pass, and certify hosted SHA/asset/cache identity.
- **Not yet device-certified:** Android TalkBack, IME and orientation, interruption/offline/PWA upgrade on a physical phone. Main branch protection remains unprotected; treat this as a separate release-governance issue.
- **No launch-ready claim** while these acceptance gates remain open.
