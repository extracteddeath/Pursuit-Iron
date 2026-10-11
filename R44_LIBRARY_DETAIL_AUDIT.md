# R44 — Material 3 Expressive Library-to-Detail cohesion

## Problem addressed

The R41 surface system already neutralized most Home, Plan and Progress areas, but
exercise discovery and the full-screen detail had independently evolved surface
definitions. Dark themes could show a vivid, base-colored detail header next to
gray list results, while search/filter instruments and the exercise detail tabs
did not clearly share a surface hierarchy.

## Implemented

- Library page uses the same quiet near-charcoal canvas as the main destinations
  (with the existing saved light-theme base).
- Library header and exercise result rows use the low tonal surface; search and
  the four real filter tabs use the raised tonal surface. The filter track and
  selected-state animation stay intact.
- Real exercise rows retain their variable heights, distinctive group corners,
  full content, semantic selection and linked detail action. Margins become a
  tighter 4px rhythm; no new fake rows or decorative overlays.
- Full-screen detail has a solid page canvas (no ghosted Library beneath it).
  Title header and media frame share the quiet surface; the four primary detail
  tabs and chart time-range selectors use the raised role.
- Preserve figure/art content, form-video link, chart values, metric tabs,
  record history, linked title/figure entrance, reduced-motion and keyboard focus.
- Do not change workout logging, exercise database, swaps, program creation,
  persisted user data, UI state handlers or engine code.

## Rendered gates

The existing B831 Android-headless integration contract now checks both 320px
and 430px: actual opaque painted pixels for the header/search/filter/result and
full-screen header/media/tabs, tonal distinctions, gradient-free surfaces, four
filter hit targets, four detail tabs, 48px Back control, selected semantics,
and no body horizontal overflow.

Retained gates cover Library search focus morph, long exercise titles, R12 linked
motion, R36 all-theme contrast, R40 enlarged Android text, chart accessibility,
exercise history integrity, release integrity and independent engine audit.

Device-only validation remaining: TalkBack ordering, long-press feel, OLED
contrast, Android keyboard resizing and animation quality on the physical phone.
