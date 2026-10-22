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

Current enforced debt ceilings live in `verification/ui-ownership-test.mjs`; the milestone figures below are historical measurements. These are migration limits, not acceptance of their design quality. Every subsequent slice must lower a measured debt metric where migration removes an owner and leave affected browser/source contracts green. The active placement and delivery sequence is maintained in `NEXT_APP_UPDATE_PLAN.md`.

Sequence: (1) Program/cycle creation controls, (2) Settings and Profile cards, (3) Home and Plan composition, (4) Progress/History, (5) Workout/Focus separately after image-level phone validation, (6) overlays/dialogs and global cleanup, (7) full accessibility, offline, physical Android and Play release certification. Keep related styles near their canonical component owner; do not add a new end-of-file specificity patch.

### Cohesion 16 — Wizard input and choice ownership

The shared `OptionCard`, `Heading` and `Col` controls, along with the units and program-vs-cycle selectors and program-name input, now use class-owned presentation. Selection styling follows `aria-pressed`; the controlled name field exposes `data-has-value`. Source contracts reject a return to static inline style objects in these migrated owners. Preserve accessible disabled semantics, 44–48px touch targets, responsive grid reflow, forced colors, and reduced motion. The cycle template card and other StepBody sections retain legacy styling pending independent passes; do not assume the entire wizard is migrated.

### Cohesion 17 — Cycle creation cards

Cycle templates and the adaptation checkbox use shared card/stack/row primitives, Wizard typography and semantic palette roles. Their real `aria-pressed`/`aria-checked` values own selected paint. All template, custom-block and adaptation callbacks remain in the Wizard. The cycle heading now owns its wrapping without a separate high-specificity priority rule. Source guards also reject inline presentation in the cycle-type step. R74 covers keyboard template selection, preserved custom weeks, reversible adaptation, all step text/reflow and draft retention. Other StepBody sections still contain legacy inline presentation and require subsequent migration.

### Cohesion 18 — Equipment and constraint controls

Equipment presets, supersets and barbell limits reuse `UiAction` tonal selection. `EquipmentRestriction` shares the exclusion presentation used by the Wizard bodyweight option and `NoRackToggle` in both setup and the gym editor. Existing callbacks retain all equipment/configuration ownership. Static presentation lives beside Wizard controls and uses shared spacing, type, action and semantic warning tokens. Remove four obsolete priority declarations now that these controls no longer inject inline geometry or paint. Named constraint groups, keyboard selections, preset exclusivity, rack exclusion reversal and all existing enlarged-text/draft journeys remain browser gates. The legacy 42px non-workout form layer excludes `.wpb-ui-control`, so shared fields keep their canonical 48px dimensions and focus/paint roles. This repairs the custom-cycle touch-target failure exposed by R74. Focus and Workout have no changes. The Wizard shell, focus-point rows and split/session guidance still need migration.

### Cohesion 19 — Wizard shell, focus rows and guidance

Wizard uses `UiHeader` and shared native `UiAction` variants for Back, Next, cancel-build, discard and early finish. A single scoped CSS family owns chrome, scrolling, footer composition and responsive insets. Remove the superseded Wizard-only header/footer/progress layers from external CSS and the embedded StyleTag. The progress bar's calculated transform is the shell's only remaining inline presentation object. All `StepBody` questions and the setup-only `Stepper` have no inline presentation; real data attributes own muscle-focus emphasis and initial-step layout. Existing configuration, draft, feasibility, generation, cancellation and navigation callbacks retain ownership. R74 checks per-muscle and global point limits, reversal, keyboard input and enlarged text. Existing option-card legacy CSS remains part of later global priority cleanup; this closes static inline presentation for the Wizard owners, not the whole app.

Cohesion 19 short-window correction: the 200% text / 900×480 cycle-focus gate exposed an oversized footer. The named action region now uses at most 45% of the Wizard height and scrolls natively; R74 requires at least 44px of form space and checks keyboard focus reveals complete primary/early-finish actions. Scoped text-action typography overrides only the generic root button font reset, with no new priority declarations.

### Cohesion 20 — Settings groups and Profile club

All eight Settings row groups use explicit shared cards. Stable `SettingsRow` and `SettingsTitle` composition has no inline presentation. Copy and controls have named owners, wrap when space is limited and preserve native fields, switch callbacks and focus across edits. One Settings family owns row spacing, dividers, grouped corners and interaction paint; retire the old embedded and external card/row/title layers and unnamed-container selector. Shared accessibility and motion guards remain.

`ProfileStrengthClub` uses the shared card and tonal actions, including 48px measure and share controls. Only the snapshot-derived progress width remains inline. Real earned/projected classes and lift availability own state paint; lifted, estimated, unit, missing-lift and tier calculations are unchanged. The club has its own responsive text/value composition. Generic legacy Profile geometry excludes it with a zero-specificity `:where` condition, preserving the cascade for the other Profile sections. Keep highlighted Strength Score row alignment and share-card preferences.

R76 checks all Settings row parents, shared card paint, 48px row minimums and full enlarged-text copy/control containment. B907 checks native keyboard measure selection/reversal, distinct lifted/estimated totals, normal/200% club targets/labels and unchanged training history; phone captures cover dark and light themes. Settings choice controls, remaining Profile strength/achievement presentation and other screen families remain later passes.

### Cohesion 21 — switch contrast and Home preview

SettingsSwitch must not use the legacy `.wpb-toggle` direct-thumb selector. The nested track owns its 44×26px geometry inside a 48px action, with paired active colors and a contrasting inactive outline. HomeSessionOutline uses `.wpb-home-session-preview` as its only inset owner. Retire the superseded preview layer instead of stacking more overrides. Preserve the newer Settings group and Profile club owners.

### Cohesion 22 — compact navigation and Home title reflow

The canonical `TabBar` owns full and compact visual labels; its existing `aria-label` remains the unshortened destination name. CSS container queries show compact labels only if button width is narrow relative to its current font size, including enlarged-text testing and the tablet rail. Hidden duplicate visual labels must not become focusable or suppress the route's accessible name. R74 enforces single-line visible labels, no overlap, and accessible hit targets at narrow and wide dimensions. On phones below 360px, the non-interactive day orbit does not consume workout-title width; Up Next still states the week/day. Preserve 393px layouts, user themes, all five navigation routes and untouched Workout/Focus geometry.

### Focus numeric touch-selection timing

Focus weight and reps inputs share the canonical select-on-focus handler. After a real tap, allow one extra animation frame for the native caret to settle, then reselect only when the field remains active, editable and unchanged; do not clobber the first keystroke. Keep B825's real-touch selection contract and preserve the protected Workout/Focus layout.

### M270 — first implementation slice of the full tab audit

All five root destinations consume `.wpb-screen-title` and `--pi-type-screen-title` (1.625rem / 26px at default text size). This replaces their scattered pixel sizes while other heading roles remain unchanged. Empty Plan retains the same title shell and UiAction creation control.

TabBar has one visible full label per destination, matching its accessible name. This supersedes Cohesion 22's visual aliases. The bottom bar reflows without fragmenting labels when text grows; the expanded rail grows with its own text. Do not shrink enlarged text or abbreviate Progress/Settings into unrelated destinations. Existing selection-track and focus semantics remain authoritative.

UiNotice owns app and workout snackbar presentation; `modules/ui-layout.js` measures navigation, live docks and the actual workout footer, including rest/Focus height changes. It never reads/writes training state. It also exposes the measured rail width for the minimized workout dock. There is no guessed toast bottom offset. Production observers are disposed with BootCommit.

Debt ceilings after this slice: **3,171 inline objects**, **2,699 external priority declarations**, **1,313 embedded priority declarations**, and **24 repeated-priority instances**. These remain migration caps. R65 checks shared root titles and full visible names; R74 checks full names under actual text doubling; B896 checks notice/rest/footer clearance in normal and Focus. Phone, text reflow, lifecycle and release checks remain mandatory.

### M271 — one root app bar and destination hierarchy

`UiRootHeader` owns the header anatomy of Home, Plan (including empty Plan), Progress, Profile and Settings. It has one 48px title/action row, the same page inset and 12px/8px vertical padding, and one scalable h1 naming the destination. Root destinations have no Back action. Deep screens retain their existing Back semantics. Supplementary navigation and metadata appear below the title row; they never create a separate title origin.

Home keeps its full Pursuit Iron identity and date in a smoothly collapsing context region while its Home title and three actions remain stationary. Plan uses “Plan” as the root heading and a smaller h2 for the selected program; block navigation shares that program context. Profile's persistent app bar owns Edit, while identity metadata, the customizable share card and section arrangement live in the scrolling content. Progress tabs use the same inset as the other supporting controls. Scroll changes app-bar fill using the existing semantic palette.

Retire the old header/brand/title positioning rules in both stylesheets instead of adding priority layers. Reduced-motion transition durations are zero, preventing a transient old font size or collapsing-row height from surviving a text/layout change. The animation system and protected Workout/Focus geometry retain their existing owners.

Debt ceilings: **3,143 inline presentation objects**, **2,483 external priority declarations**, **1,290 embedded priority declarations**, **21 repeated-priority instances**. This removes 28 inline objects and 239 priority declarations from M270. R65 compares actual title coordinates across all five roots at seven window sizes and both themes, including 200% text on phone. R53 and B887 retain compact geometry, full-size targets, contextual navigation and continuous Home collapse checks; other screen and release gates remain required.

### M275 — connected sticky chrome and Program/Cycles information hierarchy

The Plan week picker owns a solid canvas, 0 external margin and a contained bottom rule while pinned. Scrolled deep-page headers never use partially transparent paint; Progress sticky group headings use internal padding, not external margins that expose content beneath. Preserve scrolling areas, focus and theme selection. Plan's single existing full-program action is now in the program-context heading, with a 48px native control and wrap at enlarged text. The current cycle's native block disclosure is open by default, user-collapsible and backed by the existing CycleBlockRows projection. Do not create a duplicate current-block card or a second navigation handler.


### M276 — Program/Cycles hierarchy, reference information and density

In Program, current training context and edit controls come before explanatory reference copy. The latter belongs in the existing Program Details sheet via a native, keyboard-accessible disclosure; never hide it behind truncated typography or duplicate it on the main program surface. The linked-cycle route uses the same measured scroll wrapper but class-owned semantic paint and 48px minimum height. Cycles uses the established spatial tokens for overview, block rows, and information cards; block routes retain at least 64px row height and the actual canonical sequence. Avoid parallel progress calculations or a second cycle-renderer owner.


### M277 — Progress/History section and session surfaces

Progress has one effective spatial owner for its By Cycle/By Month grouping, cycle/date sticky section heading and expandable history session: use shared `--pi-space-1/2/3/4` values rather than old per-widget pixel priorities. Header paint remains fully opaque and flush; the outer session card owns clipping, with inner details not imposing a second bottom radius. Keep the existing 48px edit/actions and real history/progress data. The CSS priority budget drops to 2,480; do not restore overwritten history spacing declarations or add a later override to undo this hierarchy.


### M278 — Profile share-card spacing and strength comparisons

The user's editable share card is the primary share surface, and its metrics use the same `--pi-space-1/2/3/4` tokens as the surrounding Profile sections. The established single-column strength score comparison (left labels, right values, first row theme tint) is the active design; do not restore overwritten early grid gaps, cell padding or rounded first/last tiles. At 320px and large text, keep value alignment, field labels and customizations readable without changing data order. Six shadowed priority declarations were retired and the CSS priority ceiling is now 2,474.


### M279 — Settings category rail and grouped controls

The primary Settings header remains in `UiRootHeader`. Immediately beneath it, `.wpb-settings-jump` is a full-width, opaque category rail with the shared `--pi-page-inset` and `--pi-space-2` bottom padding: the current R57 rule is authoritative, including narrow screens, not older independent pixel margins. The category select is a minimum 48px control with `--pi-shape-action`, and both category labels and preference guidance use `--pi-type-support` for readability. The eight existing `.wpb-settings-card` groups, their 48px rows, and native setting/navigation callbacks are unchanged. Do not reintroduce earlier narrower 44px select heights, duplicated sub-359px overrides, or superseded raised-surface CSS. CSS priority ceiling: 2,463.

At viewport widths up to 420px, the Settings select's adjacent visible “Jump to” span is suppressed because the native selector already carries `aria-label="Jump to Settings section"`. This protects long option names under enlarged text without duplicating interactive controls or altering focus/selection behavior. Its icon remains visible; above 420px the visible label remains.


### M280 — word-safe root title layout

All five destinations continue using the same `UiRootHeader` title row. Its fixed destination names use `min-width:min-content`, normal word breaking and the existing line-height/type token; toolbar actions may wrap to the next row under 200% text, rather than splitting “Profile” mid-word or overlapping Edit. Do not create a separate Profile header or adjust Workout/Focus layout. The R74 browser contract asserts intact Profile title and nonoverlapping actions at 320px / 200% enlargement.


### M281 — selected navigation is a tonal indicator, not an outlined chip

The one moving bottom-nav `[data-pi-m3-track]::before` indicator paints with `--pi-color-active` and never uses a persistent gray perimeter in normal light/dark themes. Remove obsolete R35 custom perimeter tokens at their original owner instead of adding a downstream negative-priority override. Keyboard focus continues to use a `:focus-visible` ring; Windows forced-colors selection continues to use an explicit Highlight-colored perimeter and underline. Keep the saved-theme icon and active-label colors, animated track measurements, and at least 48px tab hit targets unchanged.


### M282 — Settings explanations before controls under enlarged text

Use the existing `@container pi-settings-body (max-width:16em)` breakpoint for Settings' 200% enlarged-text cases, not a global viewport-only hack. Within it, the canonical `.wpb-settings-row` becomes a vertical layout; `.wpb-settings-row-copy` may use the full card width before `.wpb-settings-row-control` sits beneath. Its `flex:none` declaration prevents the original row flex basis from creating a huge blank block when the flex axis becomes vertical. Keep compact horizontal rows at ordinary text sizes, 48px control targets, the eight card groups, preference state, and accessible descriptions. No new style priority flags are introduced.


### M283 — responsive scroll content aligns to root header

Phone scroll panes should be flush with the same horizontal `--pi-page-inset` as `UiRootHeader`: 12px for widths below 360px, 16px until 599px, and 24px on larger reading layouts where applicable. Settings and Progress use a single token-based phone rule below 420px; Home already owns its token inset in the canonical shared reading-surface rule. Do not reintroduce the retired 14px two-sided hardcoded `!important` override. The Settings jump rail must sit directly under its opaque root header; the Plan sticky week picker continues to own its opaque background, zero margin and bottom border. Real-browser geometry asserts that these seams remain aligned without changing program data, workout geometry, or page scroll interaction.


### M284 — populated Sessions sidebar and reading lane

On wide (1120px+ with height 720px+) Progress Sessions, the overview spans both lanes; consistency and calendar form a supporting left lane, while filters, grouping and saved workout records occupy the primary right lane. The Training Calendar spans the grouping and first two history tracks (`grid-row:3 / span 3`), rather than consuming their heights. A two-track span fixed single-program alignment, but a short first month could still leave a large blank gap before the second month; the third track protects multi-month grouping. Both By program and By month must keep successive group surfaces closely spaced. Keep the existing min-content grid rows, scrolling data ownership, 48px actions, and content-aware history rendering. The 900px single-column view is intentionally unchanged. Real-browser tests use 16 persisted records in both palettes and inspect first-session placement relative to the calendar plus the scrolled header seam.


### M285 — mixed history grouping evidence

Maintain the existing two-lane Sessions layout at 1120px+/720px+ and the 900px single-column mode. Its browser contract now seeds two programs with 16 workouts over several months, switches between By month and By program, and verifies group bounding boxes remain within the right reading lane, month-group vertical gaps do not exceed 64px, and saved sets do not change. Capture both themes for review; do not create a parallel calendar or duplicate history list.


### M286 — short landscape / foldable reading width

The five root tabs keep one responsive shell: compact phones stay in the narrow layout; 600–839px wide windows at 640px+ height use the existing bounded 760px bottom-nav workspace; 840px+ wide windows shorter than 560px now **reuse that same bounded workspace** instead of being letterboxed to a 460px phone frame. At 840px+ width and 560px+ height the original navigation rail remains the owner. Do not create a second rail or modify Workout/Focus geometry. R65 measures actual shell and tab bounds, verifies bottom navigation and captures 900×480 in both themes. No additional styling priority declarations.


### M287 — canonical semantic tokens, no versioned aliases

Use the existing --pi-page-inset, --pi-space-* and --pi-color-* tokens as the one source for spacing and surfaces. The earlier R41 gap aliases (1 through 6), unused R41 surface-3, R51 page/surface/rail/selected/on-selected/on-rail/separator and R57 inset/header-gap/on-support declarations are retired. These were not consumed by stylesheet rules, and no replacement visual values are introduced. Static tests prohibit their use in app.css or App.js. Retain the active R41 surface-1/surface-2 references and earlier legacy variables that still feed real styles; do not delete tokens based only on their age.

### M288 — Profile lifetime-summary ownership

The maintained Expressive Profile family owns the effective lifetime-summary grid, metric cells, numeric wrapping, small-phone padding and large-text container reflow. Historical R17/R39/R40 summary rules and duplicate R77 reflow are retired. Keep the base non-Expressive fallbacks and shared forced-colors/typography owners. External priority ceiling is **2,432**; inline and embedded ceilings are unchanged.

The ownership scanner strips comments before reading at-rule contexts. Scoped repeats are **15**, across **14** existing allowed keys; the prior scanner reported 21 because some media/container rules were misclassified as unconditional. This corrects accounting rather than claiming additional CSS deletions. Comments before queries may neither invent cross-scope conflicts nor hide same-scope duplicates.

### M289 — Profile Strength comparison ownership

The maintained Expressive Profile family owns the effective Strength section, comparison rows, score typography, single-pattern sizing and next-milestone surface. Retire the historical tile/card and narrow-phone declarations instead of layering another override. Keep the featured row’s label and value aligned with ordinary rows, full-width single-pattern presentation and the 48px share action. Shared focus/forced-color rules and non-Expressive fallbacks retain their existing owners. External CSS priority ceiling is **2,389**, down 43; inline, embedded and repeated-priority budgets are unchanged.

### M290 — Profile level-card ownership

The existing Profile family owns the level card’s layout, level marker, title/meta and progress track/fill paint. Use published live theme roles; only the computed XP width stays inline. Preserve the canonical level/XP projection, progressbar semantics, current modern and fallback appearances and shared forced-color behavior. Embedded level-card rules and the superseded R17/R61 owners must not return. Budgets: **2,378 external priorities**, **1,280 embedded priorities**, **3,108 inline objects**, **15 repeated priorities**.

### M292 — Progress panes and history presentation

The main `HistoryView` uses the maintained Progress CSS family for fixed presentation. Reuse named card, support/caption, growth/alignment, field, row and history-action owners; do not return paint, spacing or typography to JSX. Native `aria-selected`, `aria-pressed`, `aria-current` and `disabled` state owns control paint. Data attributes describe existing presentation states; they must not become parallel filtering, history, progression or volume calculations. The five remaining inline objects carry only chart widths, range height and data-derived chart ink. Publish consumed palette roles once through `applyTheme`.

Retire a superseded declaration at its original owner. Same-selector declarations may be removed only when a later declaration in the same at-rule scope fully wins; preserve separate media/forced-color states. The existing inline-masked group-header font rule is retired. M292 removes 225 inline objects and ten embedded priorities: ceilings are 2,800 inline, 1,229 embedded, 2,360 external and 15 repeated priorities. Progress helper cards/charts retain their separate existing owners pending later migration.
