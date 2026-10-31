# Shared-control pilot — Settings, Gallery and Program settings

**Baseline:** Build 1000 on main. **Source:** whole-app audit in docs/design/WHOLE_APP_UI_UX_COMPONENT_AUDIT_BUILD1000.md (separate draft PR #424). **Release status:** candidate only; do not claim production/physical Android parity.

## Problem addressed

The production app has shared actions/choices in modules/ui-primitives.js but some of the most common settings links, Gallery chips and Program settings footer actions still use screen-specific raw buttons and repeated inline style objects. Several selections have screen-owned paint rather than shared semantic selected/focus/disabled states.

## Implemented pilot

- **Settings navigation:** five destination rows, including Gyms, share UiAction (tonal, start-aligned) with one feature layout owner. Removes the four duplicated inline card objects and the legacy Gyms card wrapper, while retaining all original navigation callbacks and a consistent leading icon family.
- **Settings gym plate selection:** a UiChoice chip replaces a dynamically colored raw button. The selected state is now exposed with native button aria-pressed, each plate has an accessible name with its unit, and the plate collection is a named group. Existing setPlates updater, ordering and unit scope remain unchanged. The plate selector container adopts the existing ui-card surface.
- **Gallery filters:** use UiChoice chip; no separate --selected paint override. Split, days and goal have distinct accessible group names. Back/Retry/Import use UiAction icon/tonal/primary roles. Import also reuses the existing shared confirmation-CTA paint; retry and import callbacks and duplicate-save disabling remain unchanged.
- **Program settings:** Cancel and Save use UiAction tonal/primary respectively; staged numeric draft, disabled save, validation, Back/escape, focus trap and apply callback remain feature-owned. Numeric +/- steppers stay specialized, not forced into an inappropriate generic action.
- **CSS:** Gallery-selected theme paint now has a single owner in the shared choice. Settings links reuse the pre-existing Settings navigation anatomy; Gallery's Retry and Import no longer own redundant button paint. App.css is 644,295 characters versus the 644,317-character Build 1000 baseline (22 fewer). App.js is 2,021,497 characters versus 2,022,099 (602 fewer); the Program Settings module is 4 characters smaller. Six App.js inline style objects were removed. The external CSS still contains exactly 2,024 !important declarations. No new priorities.

## Design acceptance boundary

Source checks guard the actual shared class/callback/selection structures, the inline debt ceiling and existing 2,024 external priorities. Chromium fixture exercises selection, focus and program draft validation at 393/320px, including 200% actual text. A separate routed-screen test exercises the real Settings and Gallery surfaces across 320/393/834/1280px, screenshots the 320/834px views in both themes, and probes Gallery at 320px with 200% root text. GitHub Actions saves screenshots as seven-day artifacts for human review.

The source and browser fixtures cannot prove subjective visual polish or real-device accessibility. Before this pilot can be called 14/14, review the captured routed screenshots, additionally cover short landscape, and compare visible hierarchy, card color and touch targets. Also complete Android keyboard and TalkBack checks. The engine issue #418 remains independent.

## Decision gates for more work

1. Confirm exact-head source and browser suites, including existing Gallery/Program settings checks.
2. Inspect real composed screens for button/card harmony, strong first-viewport hierarchy and no horizontal overflow. Fix root causes, not more append-only CSS patches.
3. If shared controls prove correct, migrate the *next cohesive family*: Settings numeric fields and switch/segmented groups, then Gallery cards, then secondary Program action bars. Preserve workout specialized set-controls.
4. Reduce legacy StyleTag and external CSS overlap only after the exact computed theme/color parity is proven. Do not count individual CSS edits as separate design passes.

**Scope:** app and UI semantics only, no changes to engine prescription, logging, history, gym inventory data, offline formats or release manifest.
