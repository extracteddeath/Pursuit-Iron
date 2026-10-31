# B1011 — Material 3 Expressive foundation, screenshot-driven

Baseline: Build 1007 main, October 31, 2026. Evidence: user Home, Settings and Profile screenshots. This is a first **source-level** shared design pass, not a claim that all remaining pages have been redesigned or that the result has passed physical Android testing.

## Observed defects
- Multiple layers of similarly colored rounded containers, with few hierarchy cues.
- Settings controls compete with labels for attention.
- Profile's primary strength and club metrics carry nearly the same emphasis as secondary details.
- Large header labels, segmented controls, Home's outline and update status do not yet form a single system.
- Legacy hard-coded selectors still override the shared Material-inspired tokens.

## This pass (14 edits within existing owner blocks)
- One purpose-based shape scale: featured 30px, cards 24px, actions 18px, pill and identity roles.
- All root destinations use a fluid title and readable navigation text token.
- Shared cards and tonal actions use quieter borders and common radius roles.
- Settings groups, section labels and segments use a coherent hierarchy.
- Home's session inset blends with the featured container; adjustment notices stay visually secondary.
- Profile's top two metrics gain hierarchy; secondary metrics lose busy divider lines and reflow to one column at 22em.
- Preserve the user's theme selection, data, state, event handlers, workout set grid and training engine.
- No extra `!important` or new end-of-file CSS override was introduced.

## Not completed by this pass
Home needs genuine layout composition changes, Settings requires conversion of remaining native controls, Profile needs identity/achievement refinement, Plan/Progress/Program/Library/Onboarding require full surface migrations, and Workout/Focus must be redesigned separately after precise functional verification.

## Acceptance criteria
Compare screenshots, behavior and accessibility at 320/393/834/1280 widths, 200% font scale, all saved palettes, dark/light and forced colors, reduced motion, navigation and touch focus. Verify engine, storage, offline/release parity and physical Android/TalkBack before declaring a full redesign or shipping a release.
