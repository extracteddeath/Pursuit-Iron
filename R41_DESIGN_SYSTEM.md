# R41 — Material 3 Expressive design-system consolidation

## Reference audit

The October 11 Android screenshot of Pursuit Iron Progress shows a crowded
primary tab bar over the secondary "By cycle" control, a maroon page with a
lighter gray expanded session detail, four isolated purple action tiles,
and prominent dark set chips. MacroFactor, Google Fit and Gmail screenshots
provide the directional references: consistent tonal separation, restrained
selected-state color, connected list items, useful density, stable navigation.

This milestone implements the underlying surface roles and restructures
Progress presentation **without touching stored set data, history actions,
program generation or workout logger geometry**.

## Design tokens

The dark mode palette uses one near-charcoal canvas, one low-elevation card,
and two neutral-richer raised states, with 90% neutral / 10% theme-base
blending to prevent the rose/purple base from staining every gray card.
All 20 saved theme accents still supply selection and primary emphasis.

| Role | Nominal dark reference | Typical use |
| --- | --- | --- |
| Canvas | #111317 | Page scrollers / content background |
| Surface 1 | #1A1D22 | Home, Plan, Profile, History cards |
| Surface 2 | #24272D | Selected/raised elements and controls |
| Surface 3 | #2C3037 | Reserved for scarce high-elevation UI |
| Separator | ~10% on-surface | Related rows; not nested thick outlines |

Light themes continue to use existing M3 Expressive surface roles. Exact
painted values are composited with the saved theme: the table lists neutral
targets rather than a forced per-theme hex constant. Avoid gradients.

## Spacing and shape rules

The baseline is a 4px grid. Gaps of 8/12px structure related elements,
16px is the default horizontal page inset, 20–24px separates major groups.
General cards get 20–22px radii; nested selection tracks get 11–15px
radii. Action touch targets remain at least 48px where space allows.
The workout logger's compact +/- buttons, weight/reps inputs, completed
set fading, timer bar, amplifier rows and Target/Previous controls are
explicitly outside this milestone.

## Screen acceptance map

- **Progress — implemented R41:** Progress header is one opaque resting
  surface with a 16px inset. Primary Sessions/Lifts/Volume/Body tabs are the
  largest segmented row. By cycle/By month is a narrower, secondary control
  with a dedicated vertical gap and smaller corners. Group headers sit on
  a clean separator, not an elevated tile. An expanded history session uses
  the same base surface as its parent card; small data divisions use faint
  rules rather than a second gray panel. Share/duplicate/edit/delete retain
  all semantics and have 48px hit areas.
- **Home — shared roles R41:** Header, next session, strength summaries
  and action dock inherit consistent quiet/raised/hero colors. Only Start
  Workout and legitimate status use prominent brand accents. No oversized
  new decorative components.
- **Plan — shared roles R41:** Next session has one softly tinted hero;
  other week cards are neutral. Long workout names, selection indicators
  and week navigation keep the previous responsive behavior.
- **Profile — shared roles R41:** Connected 3-way metric strip and singleton
  strength tile from R39 remain; text hierarchy is preserved while gray
  surfaces come from the same system tokens.
- **Settings — shared roles R41:** Existing settings groups/segmented
  options inherit the quiet surface scale. No new toggles or controls.
- **Workout — explicitly frozen:** Do not apply palette or spacing resets
  to live rows. Future native-device review may propose changes separately,
  with a clear old/new screenshot and full feature parity.

## Validation

The R41 browser contract measures real rendered Progress tab/group surface
difference, secondary group width relative to primary, top-level spacing,
single-surface expanded history, action hit targets and overflow at 320px
and 430px. Existing R36 checks measure secondary text contrast across
20 themes on all five destinations. All engine, workout, lifecycle and
release integrity checks must pass before merge.

Remaining physical device review: Test real Android Rose and Amethyst themes
at 100%/125% text scale, with Progress scrolled and session expanded; check
4-button action wrap, TalkBack order, OLED contrast and safe-area.
