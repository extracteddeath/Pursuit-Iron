# Pursuit Iron — UI/UX Master Plan (R67 onward)

Updated: 2026-10-20. Build 858 is published through PR #142 at `2e2489a2c2c249f14b9c226230e0b08a1d6d9149`; all four final-head workflows, Pages, four post-merge workflows and 106 deployed asset hashes passed. Build 859 continues the Settings form review. Each slice remains independently testable and reversible.

## Current design cohesion work

Cohesion 06 continues the merged share-card pass (#211). Profile and chart editors reuse UiAction's shared tonal variant; scoped layout stays with the screen. Removed 86 actual important declarations, 3,103 to 3,017, without changing the workout or engine owners. Dead-rule removal was checked in the same mounted DOM across 24 phone/tablet light/dark scenarios (5,718 elements, zero computed-value changes). Shared buttons intentionally standardize editor borders, typography and state colors, with 48px targets and existing motion/focus/forced-colors handling. Broader cascade cleanup remains ongoing; legitimate accessibility overrides are retained.


Foundation, Profile hierarchy/style ownership, and Progress/History CSS ownership are merged through PR #209. This pass adds the requested editable share card at the top of Profile, separate from arranging the Profile sections, plus aligned Strength Score insets and shared theme roles for editor controls. The same metric model drives preview and image export. Previously saved section preferences and share selections are preserved.

Validation for this slice covers populated 320/393/834px light/dark Profile layouts, saved name/favorite lifts, preview/export content, section reordering/visibility, equal score alignment and the established compact logger regression. CI remains the release gate. Broader single-owner CSS cleanup is still ongoing; this does not certify every screen or physical Android/TalkBack behavior.

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

- Build 852 / R72: merged PR #135 at 3551f6c; GitHub Pages deployment succeeded. The actual session route has a wide/tall themed exercise lineup driven by the existing WorkoutSession and goEx owner. Phone, medium, short landscape and focused logger remain unchanged. Four permanent pre-merge workflows, R72 browser contract and Pages succeeded. Post-merge checks remain independently tracked in Actions.

- Build 853 / R73 (first isolated slice): unify focus and color-only selected-state transitions for the expanded Library and Workout companion rows. Match active fill/stroke to live theme roles, avoid generic gray and geometry-affecting animation, and preserve reduced-motion and forced-colors behavior. Extend the existing R72 real-browser paired-theme test to assert active/idle separation and bounded motion. This is a draft incremental slice only; do not publish before broader R73 visual token audit and final CI certification.

- Build 853 / R73: paired-theme selected/focus tokens now cover the expanded Library and live Workout lineup, with semantic color-only selection transitions added to existing Plan, Progress, Settings, Library and exercise-detail segmented controls. R65 and R72 browser contracts check color-only motion, reduced-motion suppression, and themed selection without changing geometry or stored prescriptions. This is R73 **slice 1**, not completion of the visual-system milestone. Remaining R73: further actual-screen token/contrast audit and motion unification, then R74 accessibility and R75 whole-device certification. Candidate must be finalized and gated before merge.

- Build 853 / R73 slice 1: merged PR #136 at f1602c2 and Pages deployed; four permanent premerge/postmerge workflows and all browser shards passed. Theme-native active and keyboard focus colors in Library/Workout companion and restrained color motion for shared segments are now live.
- Build 854 / R73 slice 2 (branch): extend the existing browser matrix to measure actual rendered selected/idle foreground contrast, moving-indicator state distinction and nongradient tab paint across all 20 saved themes in Plan and Progress. Give idle segmented controls a saved-theme feature tint on hover and an inset tint on press, while preserving selected indicator ownership, red/gray avoidance, phone workout grid and reduced-motion. Paired-theme viewport tests inspect media-aware hover/press paint and no geometry transitions; R73 must also retain the older B831 moving-indicator contract when buttons transition from pressed to selected. Still requires exact release hash finalization, four premerge/postmerge checks and Pages. R74 accessibility and R75 whole-device certification remain separate gates.

- Build 855 / R74 accessibility checkpoint: begin with a scoped keyboard-focus and forced-colors audit on the existing segmented navigation and adaptive side panes. Test keyboard tab reachability and Escape semantics on real screens before changing interaction behavior. Preserve Build 854 moving-indicator selection, pointer tints, workout logging, saved-theme palettes and reduced-motion behavior. Keep this branch draft until exact release hashes and full CI pass.

- Build 855 / R74 first implementation slice: cover keyboard-visible Library detail Back, Library search and Wizard form controls with existing theme focus role; in Windows forced-colors give semantic selected nav/segmented controls a secondary, non-color underline. Expand the R65 paired-theme browser matrix with 44px navigation targets, real forced-colors Plan/Progress selected checks and keyboard-focus visibility on Library detail. This does not close R74: Escape/dismissal focus return, 200% text, mobile virtual keyboard/safe-area and full touch target certification remain pending. Keep R75 whole-device certification separate.

- Build 855 / R74: merged PR #138 as `f1f83de91c274e90817f69fa416d0c5f38a2f250`. Pages and all four permanent post-merge workflows passed. This closes the first keyboard-focus/forced-colors slice, not every accessibility/device acceptance item.
- Build 856 / R74 continuation and R75 browser certification: repair four reproduced defects—200% navigation text clipped by fixed leading; expanded Plan supporting cards overlapping at enlarged text; gym form positioned behind an overlaid keyboard; nested autofocus Goal form losing the real opener on Escape. Keep one focus/dismissal stack and the existing visual viewport signal. Add explicit Gym/Goal dialog titles and gym form labels. Register the R74 real-browser gate in the permanent integration shard; expand R72 to the seven R75 windows and three palettes for a full logging/Undo/effort/plates/minimize/resume/finish journey. The candidate must pass final-SHA permanent CI, Pages and post-merge validation before publication.
- Remaining acceptance after this candidate: physical Android/TalkBack and actual keyboard/safe-area behavior; wider 200% form/Library/Wizard and touch-target inspection beyond the measured navigation/forms; final app-wide visual review and stylesheet consolidation; the separately tracked Home/Plan duration discrepancy. Automated responsive coverage does not substitute for these checks. No complete-roadmap claim is made.

- Build 856: publication explicitly authorized, merged PR #140 as `b4892045c441046c8cde4c02a1ed62e7656aed8f`. All four permanent workflows and ten checks passed at final head `47da0c4d54ea0856e10ee1f375f8ccf750703d8a`; Pages and all four main workflows passed. All 106 checked deployed assets match the merge. The earlier local approval checkpoint is closed.
- Build 857 / R74 candidate: preserve the checked PR #139 Library focus/zoom work on Build 856, and repair reproduced large-text defects in Library filters/detail tabs and creation chrome. Existing modal ownership excludes the expanded Library supporting pane and refreshes on resize; visible list/search remain keyboard-accessible, while compact detail retains the same Back/Escape owner. Restore focus by selected exercise identity after Back/Escape. Add named Program/Cycle fields, 44px Library controls and whole-label reflow. R74 now checks all filtered rows after actual scrolling (content-visibility defers offscreen geometry), modal/nonmodal behavior, 200% text and zoom-equivalent Library/Wizard resize with search/draft retention. Final-SHA permanent CI and deployed identity remain required.
- Remaining after the Build 857 slice: physical Android keyboard/TalkBack/safe areas/touch/trackpad; 200% text across every Wizard step and further app-wide forms/controls; final visual review and stylesheet consolidation; the separate Home/Plan duration discrepancy. Initial name-entry, Library list/detail and measured targets are covered, not every control in the app. No whole-roadmap completion claim is made.

- Build 857 final local gates: all 21 browser gates have passing runs, all 76 source contracts passed on Node 22, exact canonical export/application parity and 36 independent semantic suites passed, and release integrity passed. The existing R44 browser gate caught the custom-action height rule also matching result rows; the rule was narrowed to the custom action, and R44 passed with its original assertion. No release gate or tolerance was relaxed. Next required step: four permanent workflows at the final PR SHA, merge, Pages/post-merge checks and deployed byte verification.


## October 13 continuation — Build 858

Build 857 is published through PR #141 as `91941890a4da4ad787f3d8723420870fc7cf561b`. Its four permanent final-head workflows, Pages, all four post-merge workflows and all 106 deployed asset hashes passed. The Library/name-entry slice is closed. The historical Home/Plan duration item is also closed by the existing R50 / M219 calibrated-estimate source and browser contracts; it is not a new remaining task.

Build 858 extends the current R74 gate through all first-use Program/Cycle steps. Reproduced defects: long split names clip within the inline option columns at 200% text; cycle duration metadata crowds long names; units and constraint labels exceed their fixed columns; muscle-focus buttons are 34px and the optional finish action lacks a 44px minimum. The repair keeps labels whole, reflows choices by available text space, grows setup targets, and names each setup group. Bodyweight preference exposes its selected state. Compact live logging retains its existing controls.

Acceptance for this slice: 15 Program and 13 Cycle steps at seven windows in Rose/Rosewater (392 step visits), normal and 200% text, whole option words, 44px named controls, keyboard muscle-focus/bodyweight selection, Back/Next retention, and enlarged desktop zoom-equivalent layout viewports. The exported production Wizard is mounted to include Units/Experience/Equipment, which established profiles skip; the ordinary app loads its theme first. The component fixture never generates or saves a plan. Original app-route, Library, form focus, workout and offline gates remain required.

Engine 0.65.6 and all 93 non-App runtime hashes stay unchanged. Candidate publication still requires all four final-head workflows, Pages, all four post-merge workflows and deployed byte identity.

Remaining roadmap acceptance: physical Android keyboard/TalkBack/safe-area and touch/trackpad evidence; app-wide forms and targets beyond the checked Library, Gym/Goal and Wizard surfaces; final app-wide visual/usability review and stylesheet consolidation. The Wizard browser matrix does not close these separate items or certify the whole roadmap.


## Build 859 — Settings form continuation

The form review reproduced three numeric fields announced by placeholders (bodyweight as “—”, calculator inputs as “100” and “5”), unnamed filled export text, clipped 200% experience labels and a clipped narrow-screen Reset label. Give six fields stable purpose names, including units for weight; tie the existing experience container queries to their own text size and let the Reset label wrap. Keep its two-tap action unchanged.

The R74 continuation reads the browser accessibility tree, checks populated/read-only export and unsaved restore/program fields, preserves restore draft on text reflow and checks whole experience/reset labels at every seven-window paired-theme scenario. The earlier R53 normal/125% four/two/one-column assertions remain unchanged. Original switch tracks already have independent 44px pseudo-element hit areas; the compact track is retained.

Release acceptance still requires all four final-head workflows, Pages, all four post-merge workflows and deployed byte identity. Physical Android/TalkBack/input evidence, remaining app-wide forms, final visual/usability review and stylesheet consolidation remain separate acceptance items.


## Build 860 — Selective alternative integration

The owner authorized implementing selected improvements from the reviewed Claude archive. Adapt units/PR/history correctness, dose/time accounting, transition capacity, cue/exposure consistency and generation-scoped feasibility into the current owners. Add clearer day headings, working-set/week preview facts, enlarged-text preview reflow and a restrained dark feature role. Keep the existing logger, user-selected menu colors, responsive/motion owners and inventory safeguards.

The preview was already present in Build 859; this release improves it without another route. The legacy M230 consolidation stage is deliberately corrected with an adversarial dose-conservation gate; other historical stage fingerprints stay unchanged. Engine 0.65.7 retains the existing property/golden/standalone verification and last-five compatibility.

See `B860_SELECTED_IMPROVEMENTS_AUDIT.md` for scope and validation. Publication requires final-head CI, Pages, post-merge checks and deployed identity. Remaining physical-device and whole-roadmap acceptance stays open.
