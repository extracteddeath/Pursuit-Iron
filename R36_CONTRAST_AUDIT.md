# R36 — Rendered light/dark contrast audit

## Scope and standards
Material 3 Expressive keeps theme-native tonal surfaces and distinct color roles. The
automated target is WCAG AA **4.5:1** for non-large active text and **3:1** for
meaningful non-text indicators, as reflected in Android's accessibility guidance.
Inactive/disabled controls are excluded from WCAG's text contrast requirement;
they must still be recognizable by their actual disabled semantics.

## Findings from the baseline measurement
The 20-theme × five-main-tab audit sampled 180 visible text-to-painted-surface
pairs at Android phone width. Its initial diagnostic reported 20 low-contrast
instances, all concentrated in the following labels:
- Small Home and Plan accent kicker text in Light, Clay, Slate, Lilac, Sage,
  Ochre, Rosewater, Ocean, and Citrus palettes measured around 4.04–4.14:1.
- Stone Plan current-week and Progress Sessions summary labels measured 4.39:1.

R36 maps these specific small labels to two fully opaque, theme-derived
foreground roles. Accent text uses the theme primary toned toward black
on **light themes**; secondary metric labels use a near-neutral, subtly
theme-tinted dark ink instead of a bright fallback foreground. The existing dark action emphasis is retained.
This does not alter typography geometry, set completion fading, workout data,
or other established branded color roles.

## Deep components at two phone widths
Baseline dark-theme browser observations at 320/430px:
- Numeric workout weight and reps input text: **15.66:1**.
- Workout toolbar text: **14.65:1**.
- Rest countdown text: **9.41:1**.
- Contextual menu item: **13.27:1**.
- Exercise action sheet: **14.65:1**; sheet close text: **5.9:1**.
- Chart axis and SVG sample labels: **5.35:1**.
- Disabled program row (active selection): **13.92:1**; available row:
  **14.59:1**. These are sampled semantic containers, not an audit of all
  disabled descendants. Disabled rows must remain disabled and visually
  distinct rather than be promoted to enabled styling.

R36 turns the sampled enabled workout, sheet, menu, and chart text checks into
repeatable 4.5:1 CI assertions, and adds a separate light-theme phone fixture
checking the real Your Gyms sheet and numeric workout entry fields. The
existing 20-theme navigation/state audit and logging integrity checks remain.

## Exclusions and device follow-up
This is a targeted, browser-rendered contrast certification for sampled
components, **not** a claim that every pixel and every enabled control meets
WCAG. Semantic chart series, translucent icons above photography, visual
accessibility at extreme brightness, OLED rendering, TalkBack reading order,
and real Android touch latency require separate device checks. On a phone,
check Light, Amethyst, Lime, Stone, and Slate at 320–430 CSS-pixel viewports,
low/high brightness, enlarged text, both orientation modes, and reduced
motion. Verify no numeric field clipping, obscured bottom action, or unreadable
sheet hint; the touch areas, History, and RIR must retain functionality.

No training engine, plan generation, persisted program data, or workout
set-entry interactions are changed by the contrast correction.
