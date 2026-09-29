# M165 set display & prescription-boundary integrity

App **3.215.0**, build **771**, Pursuit Engine **0.62.5**. Based on the verified M164 release.

## Reported issues addressed

1. The workout SET / TARGET-LAST reference columns could feel cramped on phone widths; LAST also attempted to repeat RIR inside the narrow reference cell.
2. A malformed/persisted generated-program set count could be array-shaped (for example `[3,3]`). Different JavaScript consumers could render or coerce that shape differently, making the same prescription appear multiplied/repeated on Home and Program and potentially disagree with Plan/Preview/Workout.
3. The next-cycle-block opening dose summary looked up week 0 even though Next prescription stores are 1-based.

## Shared set-count fix

M165 canonicalizes working-set counts at the Pursuit Next → shell boundary rather than patching individual screens. A valid scalar stays unchanged; repeated arrays collapse to one value; ambiguous/corrupt values recover from the immutable engine-side program snapshot when it is available. A bounded fallback prevents corrupt storage from creating an extreme set count. New engine-to-shell prescriptions are also stored canonically.

This matters because Home, Program, Plan, Preview and Workout all consume the same `computeCell` / `getNextShellCell` route. The repair therefore follows the prescription, not a particular UI. Custom/user-authored programs also normalize their editable set-count field before it reaches shared display/runtime consumers.

The existing M163 distinct-day week-cursor fix is retained, so duplicate history rows still cannot falsely jump a finite program to a later lower-volume week.

## SET / TARGET-LAST layout

The protected phone layout remains active through **520 CSS px**. M165 gives SET and TARGET-LAST slightly more width and separation while preserving the single-line Load/Reps controls. LAST now shows the previous matching set as **load × reps only**. RIR remains available in the session suggestion and effort surfaces, avoiding duplicated information in the narrow reference cell.

## Cycle opening dose

`blockChangeSummary` now reads prescription week 1 rather than nonexistent week 0, so next-block opening set summaries use the real engine-owned first week.

## Engine authority

Pursuit Engine remains **0.62.5**. M165 does not change allocator, prescription, progression, or volume math. The authority audit additionally corrupts a persisted shell set shape and verifies that `getNextShellCell` recovers the immutable engine-authored set count before workout runtime realizes it.

## Verification

Portable automated verification covers progression safety, custom 10–15 double progression, editable-history integrity, distinct-day week advancement, engine authority, all engine/shadow imports, Programs interactions, and the new set-display-integrity suite. Real Android/PWA layout interaction remains device-dependent and is not claimed by this report.
