# Build 875 — Contextual dock and injury safeguards

The opt-in Workout Options → Preview set-aware dock now follows the actual first unfinished row, including warm-ups and prescribed drop/myo extensions. Completed exercises show completion. Matching working-set history uses the original working-set index. Suggested reps use the engine-owned `target.prefillReps`; a typed/manual load is never presented as a new recommendation. Plate launch uses the actual current row's load.

A prescribed amplifier appears only on the final ordinary working set, with a compact technique disclosure using the existing explanation. Affected injury-adjusted rows suppress prescribed amplifiers. Reducing work retains completed sets and effort, keeps roughly half of pending ordinary work, and removes pending failure-technique extensions. Logged extensions remain recorded. Explicit injury reports, Undo, resume and permanent program identity retain their existing owners.

The existing shared motion module owns interruptible height, opacity and corner springs. The dock reads the existing rest state and has no independent clock. It measures its own height and the actual footer, reserves corresponding scroll space, and reveals the current set after a transition. The existing keyboard and Focus owners take precedence; the dock returns after those states end. Large content can scroll inside the bounded panel.

Evidence: permanent B875 source fixtures cover warm-up/work/drop/myo/completion and injury preservation/immutability. The real-browser gate covers 320px dark, 430px light with reduced motion, 800px and 1280px; real rest-to-set container identity, exact suggested reps, typed-load plate launch, unobscured current-set completion, measured footer clearance, keyboard interruption, injury reduction/Undo/resume and unchanged saved programs. Existing B831 browser and full release/source/CI gates remain mandatory.

The dock stays opt-in. This is Chromium rendering and interaction evidence, not physical Android keyboard, TalkBack or animation-feel certification. Engine 0.65.7 and future prescriptions are unchanged.

## User feedback revision — October 14

- Show only one side of the barbell in the calculator, in inner-to-outer order. The explanatory text still specifies loading the same plates on both sides. Configured bar/Smith weights and per-side arithmetic remain the owners.
- Remove the dock's secondary rep range. Prefer the engine's exact prefill over range-shaped suggestions. Preserve AMRAP/failure protocol intent; legacy/manual fallback retains an exact entered count rather than inventing a target from a range.
- The dock and regular rest bar share `WorkoutRestActions` and the existing wall-clock callbacks for −15/+15, restart, pause/resume and skip. Only one is visible while previewing.
- Dock weight and reps support direct numeric entry and the same hold-repeat steppers as the logger. Enter moves weight → reps → dismiss; edits preserve manual ownership. Log uses the existing completion handler and progression/rest/effort behavior.
- Add/remove working-set count acts only on the live session. Removal takes the last unlogged ordinary set, preserves at least one ordinary set and all completed work, and removes orphaned pending extensions. Add uses the established logger callback. Saved program prescriptions remain intact.
- The dock remains mounted when its own input opens the keyboard, using a compact editor layout. It steps aside for ordinary-row entry and Focus mode.
- The prior CI failure was a reduced-motion transition sampling the old scroll reserve after the panel had grown. Reserve writes now follow height changes synchronously, with the existing ResizeObserver retained for other geometry changes. The browser assertion additionally waits for measured layout before comparing geometry.

Local validation: all 35 integration source contracts passed, including session restore/manual ownership and set-count/rep-suggestion fixtures. Full browser and release evidence is required at the final updated PR head; do not merge on the prior candidate's results. The resumed compact revision below has local Chromium evidence; final updated PR-head CI remains the publication gate.

## Compact logging revision — October 14

The default dock now performs the next-set task: editable actual weight and reps with hold-repeat controls, Log through the original completion/effort owner, a compact progression suggestion and matching prior result, and a 44px plate shortcut. Repeated reference cards, the decorative title/icon, and the full-width plate launch are removed. Tap the exercise/set heading to reveal session-only add/remove-set controls. The final-set amplifier stays contextual and its explanation remains available.

Minimize/expand preserves manual values and the existing wall-clock timer; the minimized dock reserves its actual smaller height. Rest shows the time, next-set preview and all five shared controls. Completed exercises use a compact header with set options available. The same spring/ResizeObserver owners handle these changes; reduced motion, ordinary-input keyboard priority and dock-input retention are preserved.

Measured Chromium acceptance at 320/430/800/1280px: ordinary actionable dock must settle at <=205px, minimized at <=72px, without horizontal overflow or obscured active-set controls. Tests exercise count disclosure, shared row updates, direct entry, plate load, log completion, rest actions, minimize/expand value preservation, keyboard interruption and injury Undo/resume. All 35 source integration contracts pass. Screenshots are reviewed after animation settles. Physical Android/TalkBack feel remains unverified.
