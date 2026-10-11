# R45 — App-wide tonal harmony, selection, and keyboard focus

## Goal
A screen-to-screen consistency check after R41–R44, with special attention
to dark-theme excess tint, Android icon/label clearance, theme-derived color,
light/dark accessibility, and unclipped controls at 320px / 430px.

## Implementation
- Reconciled Home's older R38 charcoal/plum surface family with R41's neutral
  page/quiet/raised/hero ladder now shared by Plan, Program, Progress, Profile,
  Settings, Library and Exercise Detail. No decorative gradients.
- Home header, metric and action tiles are quiet; workout preview and Quick
  tool use the raised surface, while the primary workout hero remains a
  restrained accent-tinted hero. This preserves the existing action hierarchy.
- Bottom navigation's moving indicator is reduced from the older high-accent
  dark pill to a more balanced dark surface/primary mix. The 27px indicator
  height, icon/label clearance, five tab semantics and routing springs
  are unchanged.
- Added one theme-native 2px visible keyboard focus ring with correct
  forced-colors/high-contrast fallback across major non-workout interactive
  controls. This paints outside the control without altering layout or touch
  targets.
- No changes to the user-selected theme, app or exercise data, charts,
  live set input grid, Target/Previous, steppers, completed sets, RIR,
  service-worker logic, or training engine.

## Acceptance
- Real B831 browser fixture visits all five destinations at both 320px and
  430px. It asserts actual fully opaque, non-gradient page and navigation
  surfaces, selected navigation semantics, stable tap geometry and no
  horizontal overflow.
- A dedicated Home assertion checks that actual rendered header/metric/hero
  surfaces resolve precisely to the app-wide theme roles in the active
  scheme. Existing R10 Start Workout contrast, R36 theme/AA checks,
  R38 navigation label clearance, R40 enlarged-text, R41–R44 page audits
  and the full phone, engine and release gates still apply.
- Light/dark role fidelity is covered by the R16 two-scheme role probe.
  Physical Android verification remains recommended for OLED, TalkBack,
  viewport resizing, long content and subjective motion polish.

## Deliberate regression boundary
No global button-height changes, blanket text colors, typography changes
to the live logger or new decorative/interactive overlays.
