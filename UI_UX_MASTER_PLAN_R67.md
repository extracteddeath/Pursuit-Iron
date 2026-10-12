# Pursuit Iron — UI/UX Master Plan (R67 onward)

Updated: 2026-10-11. Source baseline: Build 846 candidate, preceding release Build 845. This document governs the next app-wide UX iteration; each slice is independently testable and reversible.

## Non-negotiable product behavior
1. Keep the training engine, progression logic, performance history, offline/live workout state and storage trustworthy. A UI release must not secretly rewrite prescriptions.
2. Do not turn the working phone logger into a poster-card UI. Retain the compact weight/reps grid, in-place steppers, long press, target/last-time switching, completion fading, timed rest, plate breakdown, amplifiers, supersets, note tint and restrained coaching. Preserve color-theme semantics across every screen, not generic gray.
3. Preserve **one navigation graph and one data owner** across phone, foldable, tablet and laptop. Responsive containers may reposition or reveal related information; they must not clone route/selection state.
4. Make transitions spatially coherent and restrained: state and layout changes may animate, but text/input selection and timers must not wait for animation. Honor reduced-motion and screen-reader focus.
5. Use the **available app window** rather than physical device type: Android split screen, foldables and laptop resizing can change size classes while the app is in use. No artificial full-screen or orientation assumptions.

## Source and convention
- Google Android Adaptive Apps documentation: NavigationSuiteScaffold (navigation bar vs rail), ListDetailPaneScaffold, SupportingPaneScaffold and retained state during configuration changes.
- Google Material responsive guidance: compact/medium/expanded plus 2025–26 large/extra-large window classes, flexible pane weights rather than blindly stretching a card.
- Pursuit Iron is a web/PWA, not Jetpack Compose. Apply the patterns and behavioral tests, not native API claims.

## Release train

### R65 / Build 844 — done
- Expanded primary shell (up to 1280px), responsive rail at 840px width *and* usable height, preserved compact bottom tabs, dimensional navigation-key behavior.
- Automated dark/light 393×852, 900×480, 900×1000 and 1280×850 checks.

### R66 — scoped bug repair, Build 845/846 — done or pending CI as tracked
- B845 repaired weight-stepper progression and individual Dynamic Double load advancement.
- B846 exposes independent empty-bar weight for each gym's barbell and Smith machine, with saved-unit semantics and correct plate calculations.
- No UI overhaul may preempt a training-blocking functional fix.

### R67 / Build 847 — first active adaptive surface slice
- On expanded *and tall* windows, convert only vetted interaction sheets (gym settings, program switcher, achievements) into centered contained surfaces. Maintain phone bottom sheets and interaction ownership.
- Align the Plan reading width with the expanded rail shell. Do not make a fake second Plan route or introduce a detached pane with stale week data.
- Add phone, landscape, laptop, theme and reduced-motion geometry assertions. Validate content scrolling and dismissal.
- Fit functional controls, clear focus rings, no overlapping fields, no gradient overlays or gray surfaces detached from theme.

### R68 — Plan as a real supporting-pane layout
- Desktop expanded: schedule is main pane; selected day summary / Next Session is a supporting pane. Medium: main pane with collapsible supporting section. Compact: existing single column.
- Single shared selected week and selected day source, synchronized with program week, Workout and Home Up Next. Unscheduled days and rest days stay explicit.
- Do not duplicate Week/Block state on viewport resize; pane reflow must retain scroll and selection.

### R69 — Exercise Library list/detail adaptation
- Expanded: search, filters and exercise list left; selected exercise detail, attachments, movement variants and history right. Medium: width-aware compact list/detail. Phone: familiar list→detail navigation.
- Each attachment and variant must remain eligible and usable; swap actions remain context-aware and preserve current exercise/sets.

### R70 — Progress information hierarchy
- Mobile summary→charts→history; large window supports a focused summary strip with charts and recent lifts beside it. Separate strength trend, measurements, volume and historical workouts.
- Axes/ticks and large-text layouts must never clip. Do not show engine implementation terminology.

### R71 — Program and cycle creation
- Responsive configuration form and preview pane. Content stays in one data model; a desktop preview is not a fork of creation logic.
- Intentional density and field widths; no empty-card walls. Review program details, blocks, week prescriptions and owner overrides before saving.

### R72 — Workout expanded mode
- Keep the phone logger untouched. Tablet landscape may use logging and upcoming/rest/notes as supporting panes, but never hide a working set or make the +/− less accessible.
- Timing, haptics, focus mode, keyboard editing, prev weight and completed-set fade must be identical across sizes; no guessed auto-completion or UI-driven weight changes.

### R73 — Motion and visual system finish
- Own token matrix for surfaces, on-surface ink, control stroke/fill, semantic warning, focus and pressed states in all light/dark themes.
- Shared spring durations and gesture rules; honor prefers-reduced-motion. Avoid gradients and unthemed gray; prevent active/input states disappearing on same-color fills.
- Fluid navigation selection and sheet detents; no bounce that delays recording a set.

### R74 — Accessibility and polish gate
- Keyboard: roving tab navigation, form focus return, Escape for dismissible surfaces, actionable controls with stable labels.
- Test ≥44px practical targets except intentionally compact steppers (maintain dedicated non-overlapping hit region), legible at 200% text size, and contrast against actual themed layers.
- Forced-colors support; visible focus; mobile safe-area and virtual keyboard; no horizontal document overflow.

### R75 — Device and release certification
- Contract matrix: 320×740, 393×852, 600×800, 900×480, 900×1000, 1280×850, 1600×900; light/dark + at least one low-contrast-prone palette; split-screen resize and resume.
- Exercise exact workflow: open workout, change weight, complete/Undo, log RIR, move exercise, plate calculator, minimize/resume and finish without data loss.
- Audit Home↔Plan↔Preview/Program↔Workout↔Log for a consistent next prescribed session, load unit, rep range, date and progression rationale.
- Enforce core engine, phone browser, offline/update and release integrity checks before merge, deploy and post-merge validation. Remove temporary CI workflows and keep the repository clear of unowned verification files.

## Release acceptance
Each slice ships only if: the four permanent GitHub workflows pass at the *final* PR SHA; no engine/history signature changes unless the slice explicitly owns them; phone logging interactions remain sound; no horizontal overflow or hidden primary buttons on any supported breakpoint; light/dark theme screens remain cohesive. The deployed merge commit then passes Pages + four post-merge checks.

## Primary risks
- **Fake adaptability:** widening the frame without changing relationships between components. Favor deliberate supporting/list-detail panes with shared state.
- **Route forks:** duplicated state and independent 'Today' vs 'Plan' predictions. Use the existing single graph and selected week data.
- **CSS cascade debt:** app.css has multiple generation-era overrides. Introduce narrowly scoped end-of-file rules in each slice and consolidate only after structural tests pass.
- **Functional drift:** screen changes must not modify weight, reps, effort, time or saved history. Use fixture comparisons before/after.
- **Parallel development:** do not merge another branch targeting outdated Build 844/845 state over this sequence; rebase or cherry-pick after reviewing every changed file.

## Delivery ledger — October 11, 2026
- Build 845: corrected dumbbell decrement bounds and independent Dynamic Double set progression; merged.
- Build 846: independent barbell and Smith empty-bar masses per gym, including counterbalanced zero; merged and Pages deployed.
- Build 847 / R67: expanded-window dialog containment and bounded Plan reading width; merged and Pages deployed. Preserves compact phone logging and includes workout overlay viewport anchoring.
- Build 848 / R68 (this branch): existing Plan tabs and schedule remain the single primary column, with the *same* Up Next and program overview DOM nodes in a supporting column at >=1060 x 700 available window size. One navigation/data owner; compact, short-landscape and medium windows retain their vertical stack.
- Acceptance before shipping R68: browser geometry across four window sizes and two themes, preserved selected-week and program data, no overflow or blank picker gap, all four permanent workflows green, followed by successful Pages and post-merge workflows.
- Next UI slices after R68: R69 Library list/detail adaptive surfaces, R70 Program/Wizard connected panes, R71 Progress and Profile graphs/typography, R72 Settings density and forms, R73 workout tablet supporting details, R74 motion/a11y parity, R75 full responsive certification. Preserve theme-color, non-gradient and compact workout standards throughout.

- Build 849 / R69 (working branch): Library detail reflows next to the existing search, filters and list at expanded tall viewport sizes; selection is shared across breakpoint changes. Phone keeps existing full-screen detail. R65 real-browser journey exercises real selection, resize and light/dark themes. Full gate and deployment verification required before release.

- Build 850 / R70: Sessions on expanded/tall windows get one full-width weekly glance plus consistency/calendar in a supporting lane beside actual history search, grouping and sessions. Load and recovery insights may use adjacent cards; exercises and weight entry stay focused single-column. The R65 browser matrix now includes a real history record, responsive geometry and Body panel width. Next: R71 program creation/inspection and deeper Body/chart quality checks, then R72 workout expanded-mode pilot. The release still requires all permanent checks at the final SHA and deployed Pages verification.

- Build 851 / R71: Program and Cycle generation now use the same original Wizard with a real read-only live setup supporting pane on >=1120 × 720 windows. The pane reflects selected config, cycle phases, available equipment and constraints; it never forks generation, writes data or invents an exercise schedule before build. Compact/medium remains the original single-column fieldset and footer. R65 now tests the actual Home→Wizard workflow, mode choice, naming, optional pane, resize persistence and saved-program nonmutation in both themes. Next: R72 tablet workout supplemental information, followed by R73 motion, R74 accessibility and R75 device certification. Four final-SHA workflow gates and Pages/post-merge verification required.
