# R43 — Plan and Program hierarchy

## Objective

Continue the October 2026 screenshot-driven Material 3 Expressive cleanup by
making the Plan summary and the full Program accordion feel like parts of one
app rather than two different generations of card styling.

## Changes

- **Plan summary:** keep the current/featured week as the primary measure;
  secondary facts use one neutral low surface, a subtle border and shared 20px
  corners. The active workout remains a gentle hero with just one accent cue.
- **Plan navigation:** four real view buttons sit in a raised, connected
  segmented rail. Their existing moving selected indicator and all four
  callbacks remain authoritative. At least 44px button targets.
- **Program:** page background uses the same charcoal canvas in dark themes
  and the saved light base in light themes. Week choices are quiet neutral
  containers; semantic active states can be tinted without decorative
  gradients. Day accordions are quiet unless genuinely next/open.
- **Expanded day:** its detail area is transparent on the card surface
  with a minimal separator, avoiding an unintended brighter gray card
  nested inside a card. Day titles wrap and day action targets stay usable.
- **Motion:** existing disclosure spring and measured corner radius still
  own open/closed state. The existing 27px navigation indicator, card
  transition system, and reduced-motion support remain.
- **Out of scope:** live workout logger, completed-set fading, set/weight/reps
  inputs, Target/Previous, engine, history storage, program generation,
  and router.

## Acceptance checks

Real Android-headless layout snapshots at 320px and 430px verify opaque Plan
surfaces, separated hero/quiet/raised backgrounds, gradient-free panels,
four view controls at >=44px, summary height, Program page/card contrast,
day accordion touch size, week controls and horizontal overflow.

Existing R36 all-theme contrast, R38 nav geometry, R40 large-text behavior,
R41 Progress hierarchy, R42 Settings controls, and engine/phone/release
integrity tests still apply. Physical Android inspection is required for
screen-reader order, gesture animation feel and OLED contrast.
