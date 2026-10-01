# M219 — UI integration

Pursuit Iron 4.0.0 build 809 · Pursuit Engine 0.64.10.

Baseline: current M218 Phone Polish (`a278daac8c2879263a96b4f635522fd26917ba47`). The supplied AI archive was a separate M217-based build also labeled 808, so its changes were merged selectively rather than replacing the current app.

## Result

The current release-note repair, sticky week picker, selected-week scrolling, compact theme disclosure, workout target layout, typed entries, later-week intensifiers, saved phase lengths and interruption recovery remain intact.

The merge adds readable accent/category text, clearer option rings and Undo text, less fading of completed content, selected larger tap areas, clearer finish wording, smoother press release and corrected sheet exit animation. Removing blanket button compositor hints keeps promotion from being requested for every control. The static design-system export and duplicated icon inventory were not added to the production app.

The category-ink derivation now rechecks original and derived chip tints and keeps a small contrast margin. Remaining raw strength-level/pattern text and completed phase opacity were corrected. Chart stroke/fill colors remain separate from text inks.

Plan's overview cache now invalidates on rest-scale changes and same-count replacement of catalog entries, as well as program, week, cycle, history and saved-array changes. Unchanged inputs still reuse the expensive calculation. Tests demonstrate that longer rest changes the displayed duration without rewriting the saved prescription.

Closing sheets respect reduced motion at the final stylesheet cascade. The new browser gate reproduced a source-order conflict in an earlier draft; the correction is after the normal exit rule at matching specificity.

Workout chimes reuse a prewarmed context for the active session, retry interrupted audio on touch, disconnect finished tone nodes, and close the context when the workout view unmounts. A later resume can create a new context. Browser instrumentation verifies two actual native Web Audio oscillator starts from PR logging share one context, followed by close on Back and recreation on resume. This is not a claim of physical-device playback or a measured latency guarantee.

Screenshot review also found the inline exercise-list button's 44px hit target was clipped inside a 22px summary row. The summary now accommodates it and aligns its labels; the keyboard still collapses that secondary row. A browser check prevents this clipping from returning. The five effort choices now wrap as one group, avoiding a stranded “4+” choice at 320px; the browser gate checks their shared row and bounds.

## Validation

- Full release integrity: passed, including current hashes, 52 authored JS parse checks, 74 offline entries and included training/history/display regression suites.
- All current engine-contract groups: passed — generation (8 gates), adaptation (16 gates), quality (3 gates). The 58 non-App runtime/vendor files and their aggregate remain byte-identical to M218.
- Eight production browser gates: passed — update/restart, volume repair, workout prescriptions/variants, executable prescription synchronization, cycle duration, Android recovery, M218 phone polish, and the new M219 integration gate.
- New semantic gate: passed — memoization reuse; rest/catalog/history/week invalidation; valid current release-note icons; 4,480 theme/color cases against surfaces and both chip tint forms; audio reuse, interrupted-state unlock, tone-node cleanup and context release.
- New browser gate: passed — all 20 themes across five tabs at 320px without shell overflow; rest-length changes refresh Plan while preserving programs; reduced-motion and normal exit durations; native context lifecycle; unclipped workout summary; five effort choices together within the phone.
- Existing phone checks retain 320/360/390px and landscape entry checks, typed decimals, full rep ranges, final-set technique descriptions, focused mode, Back/resume, ten-week navigation, settings persistence and release-note replay.
- Generated Plan and Workout screenshots reviewed visually.

The new gates join the existing release/phone workflows. The M218 notes test reads the current milestone for dismissal assertions so a future release does not become incorrectly pinned to 218; its behavior checks remain intact.

Local browser verification used Chromium headless. Physical Android system font scale, keyboard, audio hardware and OEM background lifecycle certification remain separate from these browser checks.
