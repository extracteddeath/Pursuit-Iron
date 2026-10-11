# R48 — Android screenshot-led UI/UX refinement (2026-10-11)

## Evidence
Four real-device screenshots provided by the app owner at a 690 × 1536 image resolution: Home, the SekuFit Program/Plan screen, Progress/Sessions and Profile. They show the live selected theme and actual nonfixture program data, unlike synthetic CI scenarios.

## What the screenshots reveal
1. **Home:** The Up Next card owns most of the first viewport. The workout sequence and primary Start button are meaningful, but there is expensive vertical space in the title/orbit area, borders and nested preview. The permanent Create Program/Quick toolbar and bottom tabs leave Strength Score partially behind chrome; do **not** eliminate these real controls or reduce them below a 48px tap target.
2. **Plan:** Hero metrics and active next session are clear, but week rows and the week strip are saturated purple relative to Home/Profile's neutral charcoal. Past and future weeks should be low-emphasis; the current selection and next actionable workout receive the tint.
3. **Progress:** Top navigation sits in a large plum-toned header unlike the rest of the pages. The consistency heatmap and searchable sessions are meaningful. Preserve the four true tabs, heatmap, filters, disclosure, data and access to historical cycles.
4. **Profile:** The level and Strength Score are strong information features; repetitive outlined tiles can be quieter, while supporting values remain readable. Preserve all four real strength subscores, trend visualization, strength projection and Share/Edit actions.
5. **Cross-screen conflict requiring an explicit data audit:** Home displays Upper approximately **91 min** for SekuFit Week 5, while Plan's Up Next shows **80 min** for Upper Week 5. These are user-observed numbers; confirm whether one is a different duration model, rest estimate or stale preview before changing display. A second related check: week total **6h35m** vs session counts and declared daily durations. This pass does not invent a reconciled number or modify training prescriptions.

## Current incremental changes
- Keep existing Material 3 Expressive animated navigation, type hierarchy and dynamic accent; remove needless ornamental height in the Home hero and preview, while retaining the action targets.
- Render Progress's header on the same neutral quiet role as other destination chrome, instead of the old plum-tinted surface.
- Demote non-current program day cards and week choices to neutral, maintaining action/selected emphasis.
- Keep Profile's four measurements, but make supporting score tiles plain quiet cards rather than multiple competing accents.
- Add 320px/430px browser assertions for the actual rendered Home hierarchy, neutral Progress background, gradient-free Program cards and unchanged navigation/data. Previous R36–R47 contrast, phone, offline, engine, and workout checks remain.

## Specific design principles
- **Material 3 Expressive:** shape, material, hierarchy, and motion should identify the next action and affordances, not coat every row in the theme hue.
- **Premium Android:** neutral layered surfaces, clear typographic hierarchy, concise spacing and purposeful animated selection; solid opaque dark theme, no gradients.
- **Accessibility:** maintain 48dp real hit targets on primary selection controls, 125% Android text, TalkBack and system high-contrast compatibility.
- **Training-state integrity:** never alter the live logger, recorded sets, numeric text entry, Target/Previous, rest timer, adaptive generator, week data or history as part of the visual pass.

## Validation boundary
The screenshots are genuine observed app visuals. Our CI browser checks are *simulations*, not a substitute for an owner-side screenshot review after deployment. It is possible for screen-level overrides or CSS tokens to differ on a real Android PWA; follow-up device screenshots should be checked in both light and dark modes.

## Next task
Reconcile Home/Plan/Preview duration estimates using **one declared model and consistent rest policy**, then pursue measured reduction of the ~6k-line accumulated CSS overrides without changing rendered snapshots.
