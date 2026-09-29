# Pursuit Iron M187 — Premium UX Audit + Cross-Screen Refactor

## Release identity

- Milestone: **M187**
- Branch: `m187-premium-ux`
- Runtime engine: **Pursuit Engine 0.63.3**
- Source baseline: **M186 explainable block review**
- Product scope: **Home, Program, Workout, Progress, Settings, Onboarding**
- PWA cache: `pursuit-iron-production-m187-premium-ux`

## What M187 changes

M187 is a product-surface milestone. It deliberately preserves the M186 engine, progression, dose, exercise-selection, longitudinal-evidence, phase-specialization, and block-review behavior while applying one shared premium interaction contract across the app.

The shared contract adds:

- consistent heading hierarchy, text wrapping, radii, and motion tokens;
- tactile pressed/disabled/hover behavior for buttons and role-buttons;
- stable tabular numerals for weights, reps, metrics, tables, and grids;
- native numeric-input behavior without browser spinner controls;
- contained dialog/sheet scrolling and hidden inner-app desktop-style scrollbars;
- explicit selected/current hierarchy for tabs and navigation;
- high-contrast focus/disabled behavior and reduced-motion support;
- no blanket 44 px button rule, preserving the compact workout weight/reps steppers.

## Product-facing audit

### Home

- Inherits the same heading, metric-number, button, pressed-state, focus, navigation, and scrolling contract as the rest of the app.
- Generated prescription display remains on the existing canonical engine-to-shell boundary; M187 does not introduce a second Home-specific prescription path.

### Program

- Inherits the shared heading/control hierarchy and stable numeric presentation.
- Existing set-display regression coverage protects the shared generated prescription path and prevents malformed persisted set shapes from multiplying visible sets.
- Existing cycle-overview coverage preserves the simplified, labeled phase hierarchy and correct `Peak` semantics.

### Workout

- Weight/reps numbers use tabular numerals and native numeric-input behavior.
- Compact steppers are explicitly protected from a global 44 px height regression.
- Existing set-display coverage protects the compact `SET / TARGET-LAST` row and prevents duplicate RIR in the LAST reference.
- Existing swap-sheet coverage protects one native vertical scroll owner with touch momentum and no nested vertical scroller.

### Progress

- Tables/grids/metrics inherit stable tabular numerals and selected-tab hierarchy.
- Existing editable-history integrity coverage protects corrected workout evidence and its derived progression/readiness state.

### Settings

- Form controls inherit the common typography, focus, disabled, contrast, dialog, and reduced-motion behavior.
- M187 does not expose engine-internal diagnostics or change training decisions.

### Onboarding

- Headings, legends, form controls, buttons, focus, contrast, reduced motion, and text wrapping inherit the same shared contract as the signed-in product surfaces.
- No onboarding answer semantics or program-generation behavior are changed.

## Regression boundary

M187's dedicated workflow must pass all of the following before the branch is considered complete:

1. M187 shared premium UX contract.
2. M186 explainable Block Review contract.
3. Product-facing UI regressions for shared set display, workout swap-sheet scrolling, cycle-overview hierarchy, and editable history integrity.
4. Preserved M185/M184/M181/M180 engine and program contracts plus causal-cycle, longitudinal-memory, prescription-integrity, program, and import gates.

## Not changed

- No new training math is introduced by M187.
- No progression rule is loosened.
- No dose or exercise-selection rule is changed.
- No phase-transition or Block Review behavior is changed.
- No compact workout control is intentionally enlarged.

## Completion standard

M187 is complete when its GitHub Actions workflow passes at the M187 branch head with the product-facing audit included. A physical Android pixel-by-pixel visual certification is a separate device QA step and is not claimed by this automated milestone.
