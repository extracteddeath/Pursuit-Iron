# Pursuit Iron design-system contract

Owner: `app.css` provides semantic theme roles and shared component styles. `modules/App.js` owns data, behavior, state and composition. The theme must stay user-selected and preserve tinted dark surfaces.

## Canonical tokens

- **Theme paint:** `--pi-color-canvas`, `--pi-color-card`, `--pi-color-control`, `--pi-color-inset`, `--pi-color-feature`, `--pi-color-divider`, `--pi-color-on-control`, `--pi-color-on-muted`, `--pi-color-active`, `--pi-color-on-active`. Do not invent screen-specific hex colors.
- **Space:** `--pi-space-0/1/2/3/4/5/6/8/10` = 0/4/8/12/16/20/24/32/40 px. Layout gaps use the scale. Independent child margins should be removed gradually in favor of owner-controlled parent `gap`.
- **Type:** `--pi-type-caption/label/support/body/control/section/heading` = 11/12/13/15/16/16/22 CSS px (using rem). `--pi-weight-action` sets reusable action emphasis. Text scaling and readable lengths override fixed pixel typography.
- **Shapes:** `--pi-shape-action` for controls, `--pi-shape-card` for cards, `--pi-shape-feature` for intentional hero exceptions; `--pi-radius-control` is a legacy alias until all consumers migrate.
- **Elevation:** `--pi-shadow-card` (currently none) and `--pi-shadow-overlay`, used only when elevation communicates layering. Avoid arbitrary per-screen shadows.
- **Control dimensions:** `--pi-control-min` is 48px, `--pi-primary-min` 52px; `--pi-control-padding-inline` and `--pi-field-label-gap` belong to shared controls. Don't shrink essential touch targets for visual density.

## Shared primitives and variants

- `UiAction` / `.wpb-ui-action`: button semantics and variants `primary`, `secondary`, `text` (ghost), `icon`, `tonal`; shared native disabled and focus states. Some historical `row` variants retain screen-specific composition until they are migrated.
- `.wpb-ui-card`: shared background, border, radius, padding, text and no-elevation default; screens provide only needed placement and intentional hero exceptions.
- `.wpb-ui-field` and `.wpb-ui-control`: shared labels, text/select fields, min height, inset, surface, focus and disabled behavior. Use native controls and keyboard semantics.
- `UiChoice` / `.wpb-ui-choice`, `SettingsSwitch`: existing shared selection and switch semantics. Preserve `aria-pressed`/`aria-checked` and existing state ownership.
- `.wpb-ui-row` and `.wpb-ui-stack`: horizontal wrapping row and vertical grid with scale-based gap. Screen-specific responsive Grid/Flex layouts should use these roles where applicable.

## Inline style and CSS review policy

1. No **new static** React `style: { ... }`, JSX `style={{...}}`, DOM `style="..."`, or embedded `StyleTag` presentation declarations. Use a CSS class and token.
2. Permitted exceptions must be **data-driven or measured at runtime**, such as a progress percentage, calculated chart position, drag transform or user-derived dimension; they must not include unrelated static paint/layout values. When practical, set only a computed custom property and let CSS render the component.
3. Do not grow the baseline `!important` or inline-style budgets in `verification/ui-ownership-test.mjs`; lower them whenever verified style migration removes debt. Do not create new duplicate important selector/property owners.
4. Preserve the user-selected theme, narrow-phone scrolling, 200% text, keyboard/forced-color affordances, Focus/Workout separation, offline cache identity, and training/engine data semantics.

## Migration sequence and acceptance gates

Current baseline after Cohesion 19 is **3,233 inline `style: {` objects** (down from 3,331), **2,858 external CSS priority declarations**, **1,376 embedded CSS priority declarations**, with a historical repeated-priority ceiling of 30. These are legacy-debt caps, **not acceptance of their design quality**. The first slice migrates the shared action's static palette and the Plan header, Profile share-card editors and custom-cycle form controls. Every subsequent slice must lower at least one measured debt metric and leave the affected screen's browser and source contracts green.

Sequence: (1) Program/cycle creation controls, (2) Settings and Profile cards, (3) Home and Plan composition, (4) Progress/History, (5) Workout/Focus separately after image-level phone validation, (6) overlays/dialogs and global cleanup, (7) full accessibility, offline, physical Android and Play release certification. Keep related styles near their canonical component owner; do not add a new end-of-file specificity patch.

### Cohesion 16 — Wizard input and choice ownership

The shared `OptionCard`, `Heading` and `Col` controls, along with the units and program-vs-cycle selectors and program-name input, now use class-owned presentation. Selection styling follows `aria-pressed`; the controlled name field exposes `data-has-value`. Source contracts reject a return to static inline style objects in these migrated owners. Preserve accessible disabled semantics, 44–48px touch targets, responsive grid reflow, forced colors, and reduced motion. The cycle template card and other StepBody sections retain legacy styling pending independent passes; do not assume the entire wizard is migrated.

### Cohesion 17 — Cycle creation cards

Cycle templates and the adaptation checkbox use shared card/stack/row primitives, Wizard typography and semantic palette roles. Their real `aria-pressed`/`aria-checked` values own selected paint. All template, custom-block and adaptation callbacks remain in the Wizard. The cycle heading now owns its wrapping without a separate high-specificity priority rule. Source guards also reject inline presentation in the cycle-type step. R74 covers keyboard template selection, preserved custom weeks, reversible adaptation, all step text/reflow and draft retention. Other StepBody sections still contain legacy inline presentation and require subsequent migration.

### Cohesion 18 — Equipment and constraint controls

Equipment presets, supersets and barbell limits reuse `UiAction` tonal selection. `EquipmentRestriction` shares the exclusion presentation used by the Wizard bodyweight option and `NoRackToggle` in both setup and the gym editor. Existing callbacks retain all equipment/configuration ownership. Static presentation lives beside Wizard controls and uses shared spacing, type, action and semantic warning tokens. Remove four obsolete priority declarations now that these controls no longer inject inline geometry or paint. Named constraint groups, keyboard selections, preset exclusivity, rack exclusion reversal and all existing enlarged-text/draft journeys remain browser gates. The legacy 42px non-workout form layer excludes `.wpb-ui-control`, so shared fields keep their canonical 48px dimensions and focus/paint roles. This repairs the custom-cycle touch-target failure exposed by R74. Focus and Workout have no changes. The Wizard shell, focus-point rows and split/session guidance still need migration.

### Cohesion 19 — Wizard shell, focus rows and guidance

Wizard uses `UiHeader` and shared native `UiAction` variants for Back, Next, cancel-build, discard and early finish. A single scoped CSS family owns chrome, scrolling, footer composition and responsive insets. Remove the superseded Wizard-only header/footer/progress layers from external CSS and the embedded StyleTag. The progress bar's calculated transform is the shell's only remaining inline presentation object. All `StepBody` questions and the setup-only `Stepper` have no inline presentation; real data attributes own muscle-focus emphasis and initial-step layout. Existing configuration, draft, feasibility, generation, cancellation and navigation callbacks retain ownership. R74 checks per-muscle and global point limits, reversal, keyboard input and enlarged text. Existing option-card legacy CSS remains part of later global priority cleanup; this closes static inline presentation for the Wizard owners, not the whole app.
