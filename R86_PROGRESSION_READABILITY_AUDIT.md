# Build 869 · R86 — phone-readable progression explanations

## Problem
Plan Info → Lift progression → “Why these methods?” already presents the canonical engine-owned method, retained/changed comparison and explanatory reason. It is a disclosure, not an engine configuration. On narrow phones, the rationale header had a small touch area and the exercise title used inline one-line clipping with ellipsis; method names could crowd it.

## Implementation
Only app-owned **presentation CSS** changed. The existing native summary now has a 48px minimum height and keyboard-visible focus outline. The display rows for `[data-progression-reason]` permit wrapping of the actual full exercise and progression-method names, retaining original type, text, button semantics, themes and content. No new panels, magic labels, alternate engine choice, history analytics, timers, sounds or haptics were added.

### Unchanged contracts
- Engine 0.65.7; original progression-plan record and reason, changed/kept state, prescription values and adaptation policy
- The existing `<details>` open/close behavior and keyboard activation
- Workout logging inputs/stepper grid, persisted workout/history state and program generation
- All current themes, offline PWA/runtime module graph and reduced-motion policy

## Verification
Permanent B831 phone integration contract now creates an isolated rationale view matching the real DOM and the real inline ellipsis style. At 320px and 430px it requires a >=44px disclosure, full wrapping labels, zero horizontal overflow, and native expansion/collapse. All four pre-merge CI workflows and all four phone-browser shards must pass; then verify Pages and main post-merge checks. Physical Android screen-reader inspection remains an open device task.

This is a narrowly scoped, reversible extraction of one useful suggestion in the user-supplied Native App/Next-Engine v3.0 specification; wholesale solver, arbitrary e1RM recalibration, forced glass dock, and fixed palette were intentionally rejected.
