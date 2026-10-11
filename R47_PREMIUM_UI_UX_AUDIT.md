# Pursuit Iron — Premium UI/UX audit (R47)
**Scope:** current Build 831, Material 3 Expressive R46, on GitHub `main` at `812ba812441c944c1746420d1e52acfb57e23b0d`.
**Method:** actual app CSS, HTML, shared spring/navigation implementation, browser contract tests, GitHub Actions results, and current official Google/Android design guidance. **Not** a claim of physical Android or interactive screenshot inspection.

## The design standard
Material 3 Expressive is a **functional visual hierarchy**, not “add more large cards”: use one deliberately prominent action, quiet supporting information, semantic color, distinct but related shapes, and spring feedback that communicates cause and state. Prefer a restrained premium feel for training data over ornamental gradients. The chosen accent belongs to the active selection, goal, or Start action. User-set light/dark themes must survive.

Source references:
- Google Design — [Expressive design research](https://design.google/library/expressive-material-design-google-research) (clarity and user need before decoration)
- Google — [Material 3 Expressive rollout](https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/) (motion, dynamic color, responsive components, typography)
- Android accessibility — [Touch target size](https://support.google.com/accessibility/android/answer/7101858?hl=en&ref_type=adv) (recommended >=48dp)
- Android design — [Edge-to-edge](https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge) (updated 2026; safe inset and gesture regions)
- Android Developers — [Adaptive first in 2026](https://android-developers.googleblog.com/2026/05/android-adaptive-development-ecosystem.html) (foldables, keyboard/trackpad, window sizes)

## Assessment
| Area | State and evidence | Priority | Action |
| --- | --- | --- | --- |
| Design system / color | R41–R45 replaced saturated “weird grays” with page/quiet/raised/hero semantic tokens; screenshot-derived Android refinements exist; light/dark rendered gates and 20-theme checks exist | Maintain | Use one role per containment level; no gradients, no random hero colors |
| Touch target consistency | Selection tracks often 52px high but **real child buttons are 40/44px**. A rail does not make the smaller inner button a 48px hit target | P1 — fixing R47 | Increase main mode selectors and detail/filters to >=48px hit boxes and >=56px rails; keep compact workout input grid unchanged |
| Motion / test reliability | GitHub's R46 PR browser check passed, but **post-merge workflow 38124509265 / browser integration failed**: 44px `offsetHeight` was rendered as 43.416px mid 0.985->1 entrance scale. Not a user-state regression, but a false failure that degrades release confidence | P0 — fixing R47 | Measure CSS layout touch target independently of compositor transforms. Preserve measured spring animation and still check rendered visibility/focus |
| Main navigation | Five routes, selected pill geometry and label clearance were corrected R38/R40; R46 checks at 320,393,430px | Maintain | Retain 56px nav cells, coherent label/icon separation and visible focus |
| Workout logger | Rich target/previous, steppers, completed set fade, live state and rest controls are protected by numerous tests | P0 protection | **No** changes as part of decorative refactor; use standalone product/user-testing phase for workflow adjustments |
| Typography and rhythm | Numbers, labels and hierarchy use several separate overrides. Variable numeric width causes metrics to jump as values change | P2 — partial R47 | Enable tabular numeric rhythm on summary/records only; do not alter size or weight/reps fields |
| Accessibility | Strong reduced-motion, contrast, theme and focus test coverage; physical TalkBack and IME still unverified | P1 follow-up | Test screen reader focus order, Android font 130–200%, keyboard overlap, and color roles on device |
| Responsive/adaptive | 320,393,430px phone routes have integration coverage; foldable/tablet layout and pointer/keyboard interaction have narrower evidence | P2 follow-up | Separate width-class navigation rail and program/detail two-pane prototype after phone certification |
| CSS architecture | The current CSS has **5,673 lines (~239 KB), 2,114 `!important` declarations**, and overlapping R1–R46 patches. Source contains late specificity overrides such as triple `.wpb-home` to defeat older rules | P1 — planned, not blindly changed | Extract shared tokens/components to distinct layers, remove superseded selectors with screenshot/contract parity, establish selector specificity budget |
| Premium motion and delight | Existing shape morph, linked transitions and interruptible springs are a strength; indiscriminate animation could increase distraction in sets | Maintain | Reserve high expression for screen entry/selection, gentle feedback for taps, zero motion for numeric entry/completed set ledger |

## R47 implemented
1. Real 48px targets in Plan, Progress, Settings, Library filters and Exercise Detail's primary/range segmented choices. Rails use 56px to give containment and padding.
2. Keep gesture-friendly `touch-action: manipulation`, accessible 2px inward focus, animation ownership, semantic selection and measured layout.
3. Give summary-only values tabular numerals; training inputs stay unchanged.
4. Replace the flaky threshold `getBoundingClientRect().height>=43.5` with a real `offsetHeight>=48` check, while separately verifying rendered geometry is positive, focus style is present, and labels are not clipped.
5. Add 393px real element geometry audits at both 16px and 20px Android text scale, preserving the existing 320px/430px, theme, workout, program, engine, release and offline checks.

## Acceptance / remaining work
R47 can publish only after release-integrity, core contracts, independent engine audit, and all 4 phone/browser CI shards pass on the **same** PR SHA. Verify post-merge Pages deployment separately. CI screenshots are evidence for *manual* review only, not proof of subjective premium visual polish.

Next work should be R48: **consolidate the CSS cascade incrementally**, plus a controlled real-Android review of Home, Plan, Workout, Program, Library, Profile, Settings with OLED dark/light, 200% font, system gesture navigation, keyboard focus/IME and TalkBack. Introduce a foldable/tablet two-pane plan only once phone flow remains pristine.
