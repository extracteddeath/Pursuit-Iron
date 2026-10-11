# R42 — Settings tonal continuity and input hierarchy

R41 made Progress's primary/secondary navigation and expanded sessions cohesive. The Settings page still mixed older raised rows and tinted floating cards into the new neutral-dark scale.

## Implementation

- Header is a quiet solid surface, without gradients or shadow.
- Section groups each use one neutral low-elevation card; individual clickable rows remain semantic buttons with a subtle hairline between siblings rather than disconnected, elevated islands.
- The section picker and unit/effort segmented rails use the raised tonal role. The existing animated selected indicator remains the sole emphasis.
- Standard settings rows stay at least 48px high with compact 12px / 16px group rhythm.
- Narrow-phone and forced-colors behavior preserve accessibility.
- No React state, trainer engine, stored preferences, worker, modal routing or workout input geometry changes.

## Verification

R42 extends the real 320px / 430px Android-headless browser contract to measure opaque list/rail paints, a distinct two-level surface hierarchy, 48px Settings rows and controls, selected segmented state and absence of horizontal overflow. Existing R36 contrast audits continue to cover all 20 saved theme presets. Physical Android inspection, including TalkBack and large text, remains recommended after deployment.
