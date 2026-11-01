# Pursuit Iron — Material 3 Expressive reference baseline

**Checked:** 2026-10-31. **Platform:** responsive web/PWA with Android wrapper; not a Jetpack Compose application.

## Authoritative current specification

- Android Compose Material 3 release notes (updated October 21, 2026): stable **1.4.0**, newest beta **1.5.0-beta02**. https://developer.android.com/jetpack/androidx/releases/compose-material3
- Material 3 Expressive system, including color roles, dynamic palettes, typography, components, adaptive navigation, tonal elevation and accessibility: https://developer.android.com/develop/ui/compose/designsystems/material3
- Material 3 **MotionScheme** (1.5.0-beta02): standard for routine interactions; expressive for prominent hero moments. Exposes fast/default/slow spatial and effects specs. https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme
- Android guidance for interruptible spring animations and reducing composition work: https://developer.android.com/develop/ui/compose/animation/quick-guide

**These are reference principles, not a guarantee that 1.5 beta Compose classes exist in a browser.** The browser uses CSS transitions for effects and the existing interruptible spring() engine in `modules/ui-motion.js` for spatial motion. Do not copy Compose code into the web view or imply Android's platform ripple, wallpaper dynamic color, overscroll stretch, edge-to-edge system bars and predictive back are automatically provided by CSS.

## Current design-system rules

1. Color is semantic and paired: `--pi-color-card`, `--pi-color-control`, `--pi-color-feature`, corresponding foreground colors, tinted surfaces, correct light/dark/user theme and contrast. No generic grayscale overlay that ignores the selected palette.
2. Shared shapes: `--pi-shape-card` regular; `--pi-shape-action` controls; `--pi-shape-identity` selectively on intentionally featured cards. Avoid turning the dense Workout logger into large hero tiles.
3. Type uses rem-backed `--pi-type-*`, scalable labels and explicit hierarchy. Respect 200% font scaling and 320px width.
4. Shared native buttons/choices use state-layer feedback on hover and press with native focus, disabled and ARIA states. No click delay or synthetic tap. Screen labels and action callbacks remain in their feature owner.
5. Separate **effects** (color/opacity with short CSS transitions) from **spatial** (position, size, morph using interruptible JS springs). Standard motion for repeated set entry, expressive motion only for navigation, prominent disclosure or hero transitions. Do not animate changing weight/reps input values.
6. Reduced motion disables decorative transitions and leaves all state changes immediate; forced colors remove decorative state layers and retain native focus/selection contrast.
7. Migrate one actual cross-screen family at a time; retire its superseded CSS and track stylesheet bytes, !important count, adoption, browser snapshots and interaction tests separately.

## Implemented reference slice

`UiCard` accepts `tone="featured"` with `data-surface`, preserving caller DOM and other style variants; Home/Plan game plan uses this shape. `UiIconAction` standardizes accessible icon-only actions; the shared sheet close delegates its variant branch. `UiAction` and `UiChoice` use one non-blocking state-layer owner with reduced-motion and forced-colors exceptions. Old skins are not presumed certified simply because they now have a shared class.

## Keeping the guidance current

Before each major design release, compare the **official** Release Notes, Material 3 Expressive theme/components documentation and MotionScheme API with this baseline. Record publication/update dates, affected components, web-equivalent implementation, accessibility and motion changes, test evidence, and migration decisions in this document or a linked change log. Check stable and preview releases independently. Preview APIs should inform design but never be represented as shipped stable Android behavior. Revisit monthly and whenever Google announces a major Material update.

The audit is not complete until all rooted/deep screens, sheets, states and Android wrapper behavior pass real-browser and device review. Do not claim full M3E certification from the source contract alone.
