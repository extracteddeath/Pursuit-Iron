# R53 — Material 3 Expressive Plan semantic component architecture

## Motivation / screenshot audit

R51 normalized most shared controls. R52 correctly made Settings Theme, Units, and Your Gyms use the same role. Yet the user's SekuFit Plan screenshot retained uniform plum *week cards*: these actually come from `PlanView`'s anonymous `data-plan-week` wrapper with inline `background: C.card`, not the similarly-named full Program `.wpb-day-card`. R52 targeted the latter. Later investigation of `modules/App.js` confirmed the nested Plan session buttons also supplied their own legacy `C.bg2` and `C.accentDim` surfaces. Merely stacking another global CSS override wouldn't fix ownership.

## Source-level design fix

- Promote Plan's week picker, week chips, phase group, week card, nested session action, and full-program action to explicit semantic classes in the single maintained `modules/App.js`. Keep its persisted data, durations, sets, labels, scroll behavior, callback handlers and animation lifecycle unchanged.
- Delete the anonymous week-card, session-row, phase-icon and Program phase explanation *inline paint*, retaining inline placement and geometry properties. CSS owns those materials.
- Use R52 canonical roles `--pi-color-canvas` / `--pi-color-card` / `--pi-color-control` / `--pi-color-feature` / `--pi-color-divider`, instead of painting each week according to a saved theme's old card palette. Keep one current-week border cue and restrained NEXT state; the theme hue is still present in progress and action highlights.
- Give `WeekPill` a correct `aria-pressed` state, and make that program selection visually traceable. This is more reliable than inferring selection from border color alone.
- Keep the actual four-tab Plan selector, Settings/Progress, and compact workout logging layout untouched.

## Six real paired Android views

`verification/r53-semantic-plan-browser-test.mjs` creates **a true side-by-side image (393px each)** for the same seeded program in Rose/Dark (left) and Rosewater/Light (right), on each of Home, Plan, Progress, Profile, Settings, and Program. It also verifies that:

- The appropriate navigation tab is active and there is no 393px horizontal overflow.
- Plan's week cards and Settings' settings cards resolve to the same computed neutral material role, not differently tinted theme hexes.
- Plan has exactly one current week, real nested session actions, and an exposed selected week.
- Full Program has exactly one `aria-pressed` week button and no overflow.
- Both theme choices persist correctly and do not generate runtime JS errors.

Screenshots are GitHub Actions artifacts named `r53-*-phone.png`, owned by the **integration** browser shard. Retain the existing R36 20-theme contrast audit, R47 enlarged text, phone-workout test and all source integrity gates.

## Acceptance/limitations

Check both contrast ratios and visual results. A passing computed-style test does not imply every hierarchy decision is subjectively perfect. The design baseline should be evaluated visually before extending the system to other small legacy panels. Do not change exercise history, prescription engine logic, sessions, gestures, steppers, or rest behavior. Any failure in verification or the offline PWA release signature blocks merging.

R53 increments UI build to 832 and regenerates the PWA shell cache revision by the canonical finalizer. All CI workflows already in the repo remain as designed; use and remove a one-shot finalizer only for release fingerprint calculation.

Certification workflow is temporary and must be deleted before PR merge.
