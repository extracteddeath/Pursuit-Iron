# R46 — Android responsive and focus-visible certification

## Intent
After R41–R45 connected the full app's Material 3 Expressive palette, R46
examines the remaining input affordance and middle-width Android layout edge
cases. It does not claim physical device testing.

## Concrete UI refinement
The global R45 keyboard ring paints 2px outside all controls. Moving selection
rails for Plan, Progress, Settings, Library, chart ranges and Exercise Detail
need the same legible color but with the ring inside the active real button:
`outline-offset:-2px`. A focused button sits above the decorative animated
track, so the track never obscures keyboard focus. Standalone controls
keep the exterior ring. Bottom navigation uses the inward ring to avoid
clipping at the edge of the screen. Forced-colors mode uses native Highlight.

## New real-phone-size verification
Existing 320px and 430px Android browser verification is supplemented by
393x852px, at both default 16px and enlarged 20px root font scale. Each of
the five real routes must have the correct selected tab, unobstructed
icon/label spacing, no clipped labels, usable tap geometry and no horizontal
overflow. The genuine Plan and Progress segmented controls must expose
2px solid, visible, inward keyboard focus, without stealing ownership of
their existing moving indicator. Images of actual 393px Home and Settings
screens are captured as CI artifacts for manual inspection.

Every width-and-focus check verifies saved prescriptions remain unchanged;
no engine, workouts, logger fields, selected states, storage, page routing or
screen reader tab semantics are altered. Preexisting release, core contract,
phone/browser, R36 contrast, R38 nav geometry, R40 enlarged text, R41–R45
visual hierarchy and independent engine audits remain gates.

## Remaining manual device certification
A real Android OLED phone should still be inspected for TalkBack, 1.3–2x
Android text scaling, IME keyboard occlusion, refresh hitches, haptics and
touch gestures. Browser assertions alone cannot establish those qualities.
