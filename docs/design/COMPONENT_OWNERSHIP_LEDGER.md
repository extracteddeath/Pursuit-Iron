# Pursuit Iron — UI component ownership ledger

**Baseline:** Build 1008. **Current slice:** one canonical Card owner and an executable design-debt inventory. This is not an app-wide visual or real Android acceptance certificate.

## System rules

- Components own semantic DOM tags, state contracts, shape/paint roles and focus; screens own content, callbacks and composition.
- Ordinary cards must render through UiCard. Do not recreate another surface with custom inline background/radius/padding.
- Static inline styles should migrate to CSS tokens. Only genuine computed widths, drag coordinates and measured motion may remain inline.
- Retire obsolete CSS in the same pass after measured DOM, theme, accessibility and responsive verification. Do not append late !important rules.
- Do not mechanically change the Workout/Focus logging controls, steppers, Target/Last, rest and engine behavior.

## Current shared component families

| Family | Canonical owner | Migrated in this pass | Next work |
| --- | --- | --- | --- |
| Card | UiCard and .wpb-ui-card | All 18 direct standard-card call sites now use one component; original div/section tags and feature classes preserved | One-off Home, Plan, Profile and Progress cards |
| Actions | UiAction | Existing shared owner unchanged | Review raw buttons, icon-only and dangerous controls |
| Choices | UiChoice / UiSegmented | Existing selected-state semantics unchanged | Keyboard and selected styling across every tab |
| Heading | UiRootHeader / UiSection | Existing owner unchanged | Single editorial type system for supporting text and section titles |
| Inputs | .wpb-ui-control / .wpb-ui-field | Existing owners unchanged | Static inline sizing and native field styles in Settings |
| Sheets | UiSheetClose and shared sheet shell | Existing owners unchanged | Dialog/backdrop owner consolidation |
| Feedback / metrics | UiStatus and theme roles | Existing owners unchanged | Profile, Progress, Home metric hierarchy |
| Workout / Focus | Specialized set-grid family | Intentionally unchanged | Dedicated Android parity and responsive acceptance |

## Measurement and gates

Run: node scripts/audit-ui-style-ownership.mjs --json

Reports canonical primitive adoption, inline-style objects by destination, static CSS class vocabulary, external and embedded !important usage. This is static source evidence, not computed CSS or design proof.

Measured after the Settings service and Diagnostics migrations: 831 App inline style objects (14 fewer than the previous pass), 2,024 external CSS priority declarations (unchanged), zero new standard-card bypasses, and 652,680 app.css characters. The stylesheet temporarily grew as inline declarations became a single named role family; this does not yet count as total CSS debt reduction. The next phase needs removal of superseded selectors and a lower combined-presentation budget.

The Build 1008 Profile spacing contract was out of date, and is reconciled with the maintained CSS. No training logic or storage behavior was modified.

## Migration queue

1. Settings: migrate support, About, metadata, action and form presentation to shared owners; preserve user data and real input semantics.
2. Home and Plan: rebuild hierarchy around one clear workout hero, task action priority and supporting surfaces.
3. Profile, Progress and History: unify metric type, surfaces and disclosure patterns with semantic selected states.
4. Program, Cycles, Library, onboarding and overlays: continue shared actions, cards, lists, fields, scroll/focus policy.
5. Workout/Focus last, as one specialized logged-set system, with Android browser and physical device verification.

Only lower style-debt ceilings when the corresponding code truly disappears; do not weaken gates merely to pass a visual change.

The Diagnostics slice retains its quick/full test handlers, result outcomes and progress width; the rest of its static presentation now belongs to class-owned CSS. No logger or engine logic was changed.

The Settings import sheet now has named presentation roles for the sheet, textarea, result and disabled submit. The existing drag/ref, textarea state writer and import callback remain unchanged. Total inline objects are lower, while the external CSS has grown temporarily; owner retirement still remains.

B1011 groups migrated Settings presentation in one bounded owner after B1010. Its separately counted CSS remains subject to a strict 8,412-character ceiling and unchanged priority rule counts; the legacy pre-B1009 source ceiling is not relaxed. Growth here does not count as net stylesheet debt reduction.

Presentation debt budgets now live in one verification/ui-style-budgets.mjs source of truth. At this checkpoint: 830 inline objects in App.js, six remaining in Settings, 652,934 CSS characters, 8,577 of them in the B1011 Settings family. Budgets are ceilings, not a completed consolidation claim.

Home's first utility reuse slice removes five fixed inline styles from the cycle name/week/day labels and backup microcopy, with no new CSS selectors, priority overrides, or runtime event changes. App inline objects: 830 → 825; Home: 36 → 31. Preserve dynamic cycle flex/phase colors for a separate stateful-owner pass. This is source validation, not screenshot certification.
