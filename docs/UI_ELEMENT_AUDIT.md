# Pursuit Iron — Whole-app element ownership audit (B1024)

## Scope

The earlier inline-style tally was insufficient. The new `node scripts/audit-ui-elements.mjs` inventory scans **every compiled native JSX element and shared Ui* call** in first-party modules/*.js, including App.js and extracted presentation modules. An element is counted whether it has ordinary stylesheet classes, inline styles, dynamic classes, or no class.

The report groups by screen/module and inventories native interactive controls, surface candidates, shared component calls, classes reused across different owners, static class references absent from CSS, repeated CSS selectors, and duplicated declaration families. Run `node scripts/audit-ui-elements.mjs --json` for the complete data. Dynamic class names and JSX expressions are explicitly classified as unresolved.

This is a **source-level audit, not a rendered-DOM or computed-style certificate**. CSS selectors not seen in static JSX may be used by dynamic states; do not delete them from this report alone.

## Owner matrix

| Family | Shared system responsibility | Screen or engine responsibility |
| --- | --- | --- |
| Buttons and icon actions | variant, size, states, focus, disabled, motion, colors | action, label, callback |
| Chips, choices, segmented | selection semantics and paint, density, keyboard | selected value, persistence |
| Cards and tiles | surface, border, radius, padding, elevation, tone | content, grids, intentional hero exceptions |
| Metrics | value/label anatomy, responsive typography | data, units, calculations |
| Fields, toggles, steppers | native access, touch, focus, validation states | data validation and updates |
| Dialogs, sheets, menus | header, close, scrolling, backdrop, focus, insets | content, confirmation, undo |
| Headers, tabs, navigation | type, spacing, active/current/scroll visuals | routing and navigation state |
| Disclosure and rows | separators, shape, chevrons, motion, semantics | expand state, workout/program history |
| Workout, chart, timer | shared labels, shell patterns where safe | live logs, engine, calculated geometry |

## Required migration process

1. Audit **all** candidate roles by source and mounted browser screen, not by inline style count. Keep necessary one-off structural containers local.
2. Recompose the same patterns around a canonical shared component and existing design tokens, preserving HTML semantics, ARIA, state writers and callbacks.
3. Capture computed-style parity at 320/393/834/1280 px, 200% text, dark/light/user palettes, forced colors, reduced motion, and selected/disabled/error states. Retire old CSS **only after confirming equal or intentionally improved rendering**.
4. Measure separately **shared adoption and duplicate owners removed**, **actual CSS bytes/priority selectors removed**, and **regressions**. Moving CSS around is not debt retirement.
5. Treat compact Workout/Focus inputs as separately certified high-risk surfaces; protect logging, plate math, storage, timer and Android resume.

This first pass creates a complete source-based review queue; it does not imply every runtime screen or state has already passed a visual audit.
