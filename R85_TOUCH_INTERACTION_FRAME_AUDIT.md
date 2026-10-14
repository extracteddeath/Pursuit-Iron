# Build 868 / R85 — No forced layout on repeated touch feedback

## Baseline and scope

Build 867 / R83 introduced frame-consistent springs and quiet theme-native feedback on logged calendar dates. This follow-up changes only the shared `modules/ui-motion.js` state-layer controller, its corresponding original `app.css` pseudo-element, browser contracts, release metadata and changelog. It also retains the preceding R84 diagnostic browser contract for responsive navigation.

### Defect

The second consecutive press of the same button synchronously removed the active ink attribute, read `el.offsetWidth` to force layout, then reattached the attribute. That forced the browser to re-evaluate layout during a very time-sensitive pointer interaction, potentially delaying visual response on slower Android devices. Restarts also needed clear ownership so a superseded animation completion could not clear the latest ink.

### Implemented

- Alternate `data-pi-ink="a"` and `data-pi-ink="b"` on successive presses. The two matching CSS keyframe definitions have **identical colors, opacity, duration, scale and easing**. Their different names let CSS restart the paint-only pseudo-element without a forced offset-width read or cloned React children.
- `animationend` clears feedback only when its name matches the button's **currently active** pulse. Older superseded pulses cannot remove a newer one.
- On ordinary one-time presses, touches and visual feedback remain the same; no extra height/width, DOM child, tap target, React click owner, navigation state or stored workout data.
- Existing `prefers-reduced-motion`, forced-colors behavior, browser blur cleanup, pointer cancellation, protected set controls, steppers, RIR and workout-specific inputs are unchanged.

### Verification

The permanent B831 browser contract checks rapid repeated real pointer events, alternating computed CSS animation names, absence of synchronous `offsetWidth` reads on the test button, and both stale and current completion behavior. It retains the prior R83 frame cadence/calendar geometry check and all other B831 motion/keyboard/theme tests. R84 browser diagnostic tests still require real zoomed navigation hit geometry without autoclick retries.

Release manifest certifies all 93 runtime files and their aggregate plus seven UI files. Service worker cache revision covers the exact 102-file offline shell. Do not merge until all four final-head CI workflows and every browser shard succeed; after merge verify Pages deployment and main workflows. A physical-device INP/Android gesture review remains a separate acceptance item.
