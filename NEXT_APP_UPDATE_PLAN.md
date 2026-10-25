## October 25 — M366 Program Settings ownership (build 914 candidate)

Extract the Program Settings dialog from App.js to `modules/program-settings-sheet.js`, retaining the existing state owner, staged save/cancel transaction, program and cycle length validity rules, and the swipe dismissal callback. Move 10 static block-duration JSX styles into dedicated theme CSS with disabled and invalid states plus forced-colors keyboard focus treatment. Count the new module in the source debt guard and reduce the ceiling 2738 → 2728. Do not change engine, saved program data, history or schema. Require exact-head release, core, independent engine, browser and offline verification before merge; validate hosted Build 914 afterward.

## October 25 — M365 Program weekly schedule ownership (build 913 candidate)

Extract the Program weekly-schedule presentation to modules/program-schedule-sheet.js while leaving the existing ProgramView reducer as the only persisted schedule writer. The existing day cycling, clearing, today marker, workout assignments and swipe-dismiss ref are retained. Retire 15 JSX style objects in favor of one set of semantic theme-backed CSS roles with keyboard focus, reduced-motion and forced-colors coverage. The style source contract includes the new module so debt cannot be hidden by extraction. Engine 0.65.9 and saved training data remain unchanged. Merge only after exact-head source, engine, browser and offline/release checks, then verify public Build 913 bytes.

## October 25 — M363 Program review ownership and CSS cleanup (build 911)

A bounded second UI ownership slice moves ProgramRevisionReview, ProgramLayoutHistory,
and ProgramSaveReview from the 2.42 MB application owner to
`modules/program-review-surfaces.js`. App composes them without duplicating state,
saved-data migrations, or training policy. The review surface consumes the existing
canonical engine API and program revision transaction. No change to Engine 0.65.9.

The save-review dialog's 19 inline style objects and the shared icon button's
inline style call are replaced by 20 named CSS roles in the existing stylesheet.
Every role consumes live `--wpb-theme-*` variables; disabled buttons still respect
conflicts and zero-diff saves, and cycle-only versus linked-block radio scope is
unchanged. CSS uses no additional `!important` and includes forced-colors support.
The source contract counts the extracted module alongside App.js so moved inline
debt cannot disappear from accounting; its ceiling becomes 2,753 (from 2,772).

Acceptance requires exact-head release, engine and browser checks; after merge,
verify Pages deployment, published bytes and offline cache with visible Build 911.
This pass makes the Program review family reusable but does not yet decompose
ProgramView's large editing and week rendering functions. Next: a similarly bounded
ProgramView section, only after observing behavior in real browser layouts.

## October 25 — M363 cohesive session presentation owners (build 910 candidate)

Extract the existing Home/Plan session outline, coaching highlights, plan-change review and
its load-formatting helpers into `modules/plan-session-surfaces.js`. Extract the already
shared keyboard-contained SessionChoiceDialog into `modules/session-choice-dialog.js`.
These two modules keep the exact JSX, callbacks and ARIA contracts; App remains the
composition owner and the canonical engine API remains the only computation writer.
No appearance, CSS selector, theme, workout, storage or engine-model change is intended.
Original function declarations are deleted instead of retained as forwarding shims.
This is one bounded ownership step, not completion of M363 or launch certification.

Gate this move with existing source, browser, offline and standalone engine checks; bump
the visible build and verify the published bytes and offline cache after merging.
Continue by co-locating the adjacent Program review family with the existing dialog
owner, then retire static style properties at source without adding CSS overrides.

# Whole-app launch readiness

## October 25 — M362 prelaunch code cleanup (build 909)

The app has not launched; its only current user is its owner. Prioritize a clean,
maintainable launch baseline over compatibility for hypothetical external code
consumers. Preserve actual saved programs, workout history and backup migrations.
This section supersedes the earlier instruction to prioritize another prescription
audit: reproduced defects still get fixed, but structural cleanup is now the main work.

First pass: retire App.js's duplicate computation export surface (256 exports),
move 49 verification consumers to the canonical engine API, remove 280 unused import
bindings, the unused CycleTimeline renderer and two unused constants. Consolidate
App's canonical API imports. The standalone exporter copies semantic tests without
rewriting UI imports; parity compares against engine-api.js without loading React.
The boundary regression retains 13 independent golden scenarios and rejects engine
exports or computation-test imports through App. No training policy or data schema changes.

This is a completed boundary cleanup, **not a launch-clean codebase**. App.js is
still about 2.44 MB. The maintained style audit counts 2,772 inline style objects;
2,344 stylesheet and 1,199 embedded priority declarations remain. Tighten the
existing budget after removal; never hide debt by moving it between files.

| Next pass | Purpose and acceptance |
|---|---|
| Cohesive feature ownership | Extract UI features with their state and dependencies at clear boundaries. Avoid duplicate state, calculators and passive forwarding layers; preserve the current Workout and Focus flows. |
| Shared presentation | Move static presentation to the existing shared primitives and semantic CSS. Delete superseded rules at their original owners, preserving themes, focus, disabled states, reduced motion and narrow screens. |
| Obsolete paths and verification | Prove reachability before deleting code. Keep source invariants, independent engine evidence and meaningful browser/device journeys; remove redundant compatibility scaffolding only when no real saved-data contract depends on it. |
| Reproducible production output | Keep one maintained authoring source with reproducible browser output. Evaluate packaging when ownership is clearer; do not introduce edited generated copies or a second implementation. |

Each published slice increments the build, passes exact-head source/engine/browser
checks, and verifies public hosted bytes plus visible/offline build identity. Keep
physical Android launch certification separate from Chromium emulation. Continue
feature and styling cleanup before declaring launch readiness.

## October 25 — M361 final-target explanation authority (build 908)

Baseline 907 was deployed and verified. Reproduced an equipment-boundary mismatch:
a generated 105 lb common-load estimate became four executable 100 lb sets after
the selected increment was applied, while the reason still said “Use 105 lb”.
The Why sheet separately bypassed that final boundary and also showed 105 lb.

The existing prescription adapter now reconciles prescribed “Use/to” loads after
display-unit conversion and equipment rounding, preserving observed evidence.
The existing explanation projection consumes `sessionSuggestion` rather than
calling the lower-level generator bridge. Its evidence follows the same raw LAST
exposure, converts once, respects reported-effort provenance and counts corrected
history once. A stronger/global anchor or another-day starting reference cannot
be relabeled as this day’s LAST. Completed history and saved plans are read-only.

M224 source regression failed before the fix; it now covers rounded/unrounded
loads, converted decimal estimates, earned increases, corrections and evidence
identity/provenance. B882’s real-browser Repeat flow covers four set rows,
expanded reason, Why, Focus and reload in lb/kg. No new engine or UI calculator,
schema, CSS or workout geometry. Publish only after exact-head gates, then verify
Pages bytes and the visible/offline build. Continue the cross-screen prescription
and progression audit after this bounded repair; physical Android remains separate.

## October 25 — M360 numbered webapp release policy

The M359 server files were current, but the visible build remained 906 through
M321–M359. Build 907 collects those deployed fixes under a new visible release.
Every subsequent change to shipped runtime/UI/offline bytes must increment the
canonical manifest build using `node scripts/finalize-release.mjs --next-build`.
Ordinary finalization remains idempotent. Keep engine-version changes tied to
actual engine model identity, independently of the always-increasing app build.

Release CI compares against the exact PR base or pre-push manifest and rejects
unchanged/regressed build numbers for changed shipped files. The existing PWA
browser journey checks the displayed build before/after Restart and after an
offline reload, preserving local training storage. Published-byte verification
must pass for the exact merged release. This supersedes older guidance permitting
same-build publication; local assembly still rotates cache identities safely.

After publishing and verifying 907, continue the progression/load and prescription
audit. Preserve user-owned entries, deliberate set effort and completed history.

## October 25 continuation — M359 authored set intent and live fatigue

Baseline: `528a857` (M358). The existing 13-stage lifecycle audit passed, but a new
reproduction exposed a live intent gap: two session-local **To failure** sets at
100 × 8 @ 0 RIR were compared with the exercise's default 1–2 RIR and reduced the
next ordinary set to 95. Set-type changes retain automatic numeric ownership;
that flag alone does not establish permission to reinterpret authored effort.

The existing `live-autoregulation.js` owner now shares one authored-intent guard
across load reductions, rep caps and restore validation. Explicit set types,
failure, AMRAP and percentage targets cannot authorize or receive these automatic
changes. Resume removes an old automatic adjustment whose sources carry authored
intent, preserving completed observations and user-owned/changed rows. Ordinary
two-miss reductions, observed rep caps and earned future progression retain their
existing owners. No UI layout, styling, stored-history/schema or program policy
changes; static style/priority counts are unchanged.

The existing M232 source regression failed before the fix (100 → 95) and now
covers source/pending protocol protection, immutable input and idempotent resume.
Its registered browser journey exercises the real set-number menu, deliberate
failure logging, subsequent target protection and reload. Acceptance requires
all exact-head CI checks, the lifecycle audit and post-merge hosted-byte parity;
physical Android certification remains open. Next: continue the progression/load
matrix and cross-screen prescription audit, prioritizing reproduced defects.

Updated October 22, 2026. This is the single current next-update plan. It supersedes the October 8 workout proposal: compact logging, coaching, session choices and program-change previews already exist. Preserve the user's current Workout and Focus experience.

## Current baseline and ownership

App 4.0.0 build 909 / Engine 0.65.9 / M234. `RELEASE_MANIFEST.json` is authoritative for shipped versions and hashes; `BUILD_PROFILE.json` records the current UI slice. Every changed published release increments its app build; content-addressed offline caches certify the exact bytes. No new training-model identity is implied by the app build.

| Area | Maintained owner | Next work and acceptance |
|---|---|---|
| Shared UI | UiRootHeader, UiAction, UiChoice and semantic theme roles in `modules/App.js`; `app.css` | Continue migrating existing controls and removing provably superseded declarations. Preserve palette, focus, disabled, motion and forced-colors behavior; never solve conflicts by adding another override layer. |
| Home, Plan, Profile, Progress | Existing screen components and shared navigation state | Review empty/populated/error states, reading order, action hierarchy and 320px/200% text layouts. Profile and chart editors already share controls. Preserve saved selections and record content. |
| Settings and transfers | SettingsView preferences; BackupRestorePanel transfers and Undo; StorageStatusCard browser capability state | Backup/restore ownership is consolidated. Continue category placement and shared selection controls while preserving preview, safe merge, Undo and truthful transfer/storage outcomes. |
| Workout and Focus | Existing shared workout state, runtime and mobile lifecycle | Retain compact rows, direct input, steppers, target/last-time behavior, rest timers, effort provenance and Undo. Verify interruption, keyboard, safe areas and exact-once completion; no visual redesign. |
| Engine | `modules/engine-api.js`, canonical next-engine and training-domain modules | Continue existing independent, differential and replay gates. Change policy only for a reproduced defect with evidence and a regression. No duplicate engine, adapter or exporter. |
| Persistence and offline updates | Existing store migration/merge/queue and service worker | Corrupt/future saves must not be overwritten. Preview before restore, preserve drafts, require successful persistence before completing a workout, verify offline cache integrity and cancellation recovery. |
| Android / Play | `play/README.md`, final TWA package and Play Console | Resolve package identity and signing certificate association at the domain root, build the release bundle, validate actual permissions/SDKs and complete account-specific declarations/testing. Repository/browser checks cannot certify a signed Android package. |



## M292 — one Progress and History presentation family

- Move the main Progress view's 225 fixed inline style objects into maintained class owners, sharing card, caption, field, row and action presentation across Sessions, Lifts, Volume and Body. Action/field families remove 32 repeated fixed CSS declarations. Keep five chart objects for calculated widths/heights and data-derived ink. Spacing values on the existing scale use its canonical tokens.
- Existing native `aria-selected`, `aria-pressed`, `aria-current` and `disabled` values own selection/control paint. Display-state attributes own expanded/reached/change presentation; publish only consumed live palette roles. Retire the old masked group-header font and 14 exact-selector embedded declarations that are fully superseded in the same CSS scope.
- Inline ceiling: **3,025 → 2,800**. Embedded priorities: **1,239 → 1,229**. External/repeated ceilings remain **2,360 / 15**. Preserve the canonical filtering, history grouping, charts, body logs, scores, callbacks and Workout/Focus experience; do not add priority patches or raise budgets.
- Screenshot review reproduced a 9px heading/number overlap when the six-week range bars reached full height. Keep the chart's 46px minimum and let its container fit labels plus bars. The native browser gate must use sufficiently populated weeks and assert heading separation and chart containment at normal/enlarged text.
- The extended B911 browser journey covers 320/393/834/1280px, two palettes, normal/200% text, native keyboard controls, full session notes/sets, filters, all panes, explicit body logs, same-day replacement and reload persistence. Require migration appearance equality for empty, single, populated, stalled, rising and flat data across modern/fallback presentation and forced colors; independently verify the intentional range-chart height correction. Require screenshot review, all exact-head CI and deployed-file identity before publication.
- Next: continue separately proven presentation cleanup in the remaining Progress helper cards/charts and shared overlays; main-view ownership does not imply those helpers are migrated. Physical Android certification remains open.

## M291 — complete Profile presentation batch

- Move 83 fixed JSX style objects across Profile, achievement details, Strength trends and shared Sparkline into the existing CSS families. Leave only calculated progress widths, empty-chart height and data-derived chart ink inline. Publish the live faint/border-soft palette roles once; preserve existing theme values.
- Retire all embedded Profile selectors and their superseded external counterparts. External priorities: **2,378 → 2,360**; embedded priorities: **1,280 → 1,239**; inline objects: **3,108 → 3,025**. Repeated-priority ceiling remains 15. No new priority patch layer or increased debt budgets.
- Create the section element map once per render instead of recreating all sections for each visible section. Preserve saved ordering/visibility, earned status, history, score calculations, share export and Workout/Focus behavior. Name achievement dialogs through the existing backdrop accessibility owner; expose heading/progress semantics and verify keyboard containment, dismissal and restored focus.
- Require before/after computed-style equality for empty, missing-bodyweight and populated Profile states, collapsed/expanded achievements and locked/earned details across five windows, two palettes, normal/200% text, ordinary/forced colors and Expressive/fallback presentation. Retain the eight-window Profile browser journey, screenshot review, final-head CI and deployed source identity before publication.
- Next: continue separately proven non-Workout presentation cleanup across the remaining screens and shared controls. Physical Android certification remains open.

## M290 — class-owned Profile level card

- Move the level card’s eight fixed JSX style objects and embedded fallback rules into the existing Profile CSS family, using live theme roles. Only the calculated XP fill width remains inline; canonical level, XP and history calculations are unchanged.
- Retire the superseded R17/R61 card/mark rules. External priorities: **2,389 → 2,378**; embedded priorities: **1,290 → 1,280**; inline objects: **3,116 → 3,108**. Repeated-priority budget remains 15. Keep both the modern and fallback appearances, shared forced colors and current Workout/Focus.
- Existing Profile browser coverage checks complete level/next-level copy and agreement between accessible XP percentage and actual fill width at all eight windows and both text sizes/palettes, alongside customization/export/history checks. Require before/after computed-style equality, screenshot review, final-head CI and deployed source identity before publication.
- Next: continue separately proven Profile achievement and remaining cross-screen presentation cleanup. Physical Android certification remains open.

## M289 — one Profile Strength comparison owner

- Consolidate the effective Strength section, comparison grid, row typography, single-pattern sizing and next-milestone presentation in the maintained Expressive Profile family. Retire the obsolete tile/card/narrow-phone declarations at their original owners. Preserve shared forced-colors, focus and non-Expressive fallbacks.
- External CSS priority ceiling drops from 2,432 to **2,389** (43 fewer). Inline, embedded and repeated-priority budgets are unchanged.
- Extend the existing populated Profile browser journey to check complete labels, matching featured/ordinary row insets and 48px share controls under normal/200% text at phone, tablet, rail and short-landscape windows. Retain customization, PNG export, saved history and preference persistence checks.
- Publication requires before/after computed-style equality for empty, single-pattern and populated Profiles in both palettes, ordinary/forced colors and normal/200% text, screenshot review and all final-head CI. Continue with separately proven non-Workout presentation cleanup; physical Android certification remains open.

## M288 — one Profile lifetime-summary style owner

- Move the effective layout and numeric wrapping into the maintained Profile summary family; retire the obsolete R17/R39/R40 normal rules, duplicate small-phone rules and duplicate R77 container reflow. External CSS priority ceiling: 2,432 (30 fewer).
- Fix comments erasing media/container scope in the existing ownership guard; retain only the 14 actual baseline collision keys, with a scoped repeat cap of 15. This is an accounting correction, not a claim of six further CSS removals.
- Existing populated Profile browser coverage now includes eight phone/tablet/desktop and short-landscape windows in both palettes, normal/200% text, complete summary labels, share-card customization/export and unchanged history. Require before/after computed-style parity, screenshot review and final-head CI before publication.
- Next: continue proving and retiring superseded non-Workout presentation owners. Physical Android keyboard/TalkBack/foldable certification remains separate from browser checks.

## M287 — prune genuinely unused semantic aliases

- Audit confirmed unused R41 spacing aliases, R41 surface-3, R51 intermediate palette aliases, and R57 inset/header labels in app.css. Remove 17 inert definitions from their original owners; use --pi-page-inset, --pi-space-* and semantic --pi-color-* instead of maintaining alternate token ladders.
- UI ownership regression guards ensure the retired names do not reappear in CSS or App.js; source layout and interactions are deliberately unchanged. The stylesheet priority ceiling stays at 2,462.
- Next: continue targeted ownership cleanup only with proven nonconsumers; certify physical Android foldables and tablets separately from browser geometry and motion checks.

## M286 — foldable and short landscape reading width

- Actual 900×480 R65 screenshot showed a 460px centered phone frame with excessive unused width. Extend the existing R88 medium-tab-shell CSS owner to cover short 840px+ landscape windows, retaining bottom nav and a maximum 760px reading surface; do not affect Workout/Focus or taller rail layouts.
- R65 screenshot/geometry regression checks now expect 750–760px at 900×480, five tappable tabs, no viewport overflow, and continuous root titles across all five destinations. CSS priority count unchanged.
- Remaining Android launch work requires a real-device short-landscape/foldable inspection, plus continued component/declaration retirement only when a superseded owner is demonstrated. Do not infer physical device parity from browser screenshots alone.

## M285 — multi-program and multi-month history continuity

- Expanded populated tablet fixtures to two saved programs, 16 sessions and multiple months in both palette families and both 900px/1280px widths. Verify grouping buttons, record completeness, scroll containment and header continuity without modifying history.
- Found a calendar-induced gap between short month groups in the wide Sessions lane. Keep the calendar as the same shared sidebar but span the grouping and two subsequent record tracks. Guard group-to-group gaps at 64px maximum in a rendered browser.
- No new `!important` styles, data-engine changes, or Workout/Focus restyling. Follow with actual Android foldable/tablet checks and harder saved-history imports, not speculative redesigns.

## M284 — populated tablet history certification

- Real 16-session browser fixtures revealed the 1280px Sessions history column began *below* the full calendar because its grid row shared the calendar's intrinsic height. The calendar now spans the grouping and first history row, letting workout records begin beside it. 900px remains a single-column reading layout.
- Cross-palette, 900/1280px top/scrolled screenshot capture and browser assertions now enforce bounded records, stationary opaque root chrome, the first-record/calendar relationship, and unchanged session data except expected migration timestamps. Keep this test fixture in CI to prevent the empty-state-only coverage gap from reopening.
- After exact-head CI, continue reviewing long multi-program/month groupings and actual Android tablet/foldable usage; avoid reworking data generation or Workout/Focus.

## M283 — cross-tab phone scroll insets and header seams

- Settings/Progress scroll bodies had a legacy 14px mobile padding rule, different from the shared header 12px at 320px and 16px at 393px. Replace this with `--pi-page-inset` and keep Home's existing canonical scroll owner. No new CSS layer or navigation changes.
- Browser regression checks on both themes verify left/right root header ↔ Settings/Progress scroll inset equality, opaque header chrome, and zero visible gap at the Settings jump rail. Plan's scroll picker stays guarded by its prior R62 sticky/opaque/margin tests.
- Static style-priority ceiling drops to 2,462. Next: use screenshot evidence to review tablet header/scroll surface transitions and narrow-phone text overflow; fix reproduced issues only, not a blanket redesign.

## M282 — accessible Settings row descriptions at 200% text

- Real-browser screenshot at 320px/200% text showed narrow Settings copy breaking a single word (“suggestions”) around the inline kg/lb control. The existing Settings body 16em container breakpoint now stacks the copy above its control rather than narrowing the description to one-third of the card.
- Regression guards inspect 320px/393px text-only enlargement, requiring full-width copy and controls positioned below. No additional `!important`; the normal-size compact row design and saved settings remain untouched.
- Next verify the real screenshot, bottom-navigation phone/tablet selected state, and sticky chrome after scroll. Do not change Workout/Focus or engine without a reproduced regression.

## M281 — navigation indicator and shared selected state

- Removed the persistent gray R35 perimeter from the shared moving selected-tab pill. Continue to express selection using the saved-theme tonal fill, icon and label; retain a focus-visible keyboard ring and a distinct forced-colors border.
- Real-browser and static contract tests guard against ordinary-theme pseudo-element outline/border returning across all five primary tabs. No duplicate tab renderer, animated-track change, navigation state change or `!important` increase.
- Next cross-tab priorities: enlarged-text Settings descriptions and category affordances, scroll/sticky bar compositing at 320/393px, and real-device sampling across light/dark theme and tablet sizes.

## M280 — cross-tab root title accessibility

- Browser screenshots revealed a one-word title split (“Profil” / “e”) at 320px and 200% text alongside Profile's Edit action. Fix in the existing shared title owner rather than creating a Profile-specific header.
- Root destination names now retain whole-word intrinsic width; existing flex-wrap moves action buttons instead of clipping title text. R74's real-browser inspection asserts the entire Profile word and no action overlap at 320px / 200% text.
- Next audit: 200% text within Settings row descriptions, cross-tab sticky header scroll positioning, phone navigation, and the tablet layout. Keep snapshots/keyboard/motion tests, actual-device evidence and release certification distinct.

## M279 — Settings control and category hierarchy

- One effective Settings category rail uses shared root alignment, page inset, themed canvas and 48px choice target; 11 superseded high-priority declarations and duplicate narrow viewport overrides have been retired.
- Category headings, explanatory text and selection shape use shared typography/shape tokens; all eight saved Settings groups, toggle behavior, category jump, transfer workflows and preferences remain canonical. For phone widths at or below 420px, hide the redundant visible jump label while preserving the native select's accessible label and section names.
- Static ownership and 320px, 393px, 900px and 1280px enlarged-text browser assertions now protect the category rail, focus/touch geometry and card layout. Priority ceiling: 2,463. Next work: cross-tab device screenshot review and targeted consolidation of repeated old button/section rules before launch; do not blanket-redesign Workout/Focus.

## M278 — Profile share-card cohesion

- Share-card heading, metrics and editable favorites use the shared spatial tokens with fewer bespoke gaps; six previously superseded strength/statistics priority declarations are removed without changing the current single-column score comparison.
- User-chosen card content, saved preferences and share actions remain untouched. The production CSS priority ceiling drops to 2,474. Exact-head browser accessibility, 320px typography and screenshots remain mandatory before merge.
- Next review Settings category hierarchy and input/control consistency, then a cross-tab device-only regression pass for sticky chrome, typography and scrolling.

## M277 — Progress/History cohesion

- History grouping and section headers now use the shared spacing scale; expanded sessions inherit outer-card clipping rather than creating nested corners. Two redundant high-priority legacy rules were removed. History data, date grouping, chart owners, filters, actions and Workout/Focus remain unchanged.
- The static CSS priority ceiling is now 2,480. New verification guards reject history spacing regressions; visual device review and exact-head CI remain required before merge.
- Next cohesion targets: Profile share-card editing scope and section density, then Settings category controls; do not layer additional priority patches onto the completed Program/Cycles or Progress hierarchy.

## M276 — Program/Cycles reading flow and density

- The Program's first scroll content now prioritizes current cycle context and controls; edit scope is available from Details. Cycle-link presentation moved from static per-element styles to shared semantic roles.
- Cycles overview, block list, rows, and support cards now share the existing spacing/shape scale without denser touch targets or changed training data. The open sequence is fully accessible, with concise list summaries.
- Inline debt cap drops from 3,127 to 3,116. The current Workout/Focus composition, the Plan program action, cycle navigation, and offline/storage behavior are preserved. Next verify real device screenshots, then move to Progress/History and Profile hierarchy; full Play certification remains separate.

## M275 — scrolling chrome and Program/Cycles follow-up

- Plan: the Full program action sits in the program heading, and the old footer action was removed. Selected-week memory, read-only block routing and all four panes remain intact.
- Plan's sticky week rail is fully opaque and flush at its bottom edge; deep-screen headers and Progress sticky history rows no longer leave translucent/margin bands while scrolling. No new important CSS declarations.
- Active Cycles disclose their complete block list by default but remain user-collapsible; the existing canonical cycle data, callbacks, progress and saved state are unchanged.
- Continue the audit with deeper Program day/action density, Cycles expanded layouts, then Progress/History, Profile and Settings. Require browser certification, release integrity and device screenshots before calling the entire app complete.

## This slice — M274 Program presentation consolidation

- The deep Program header's title row, name, collapsible metadata and saved-state indicator now use CSS classes and shared tokens rather than eight static React layout objects. This retains the custom program rename, save/review states, cycle navigation, motion refs and training data unchanged.
- The inline-object budget is lowered to 3,127; source gates reject return to per-instance layout owners. Protected Workout and Focus geometry is unchanged.
- Next priority remains populated Progress/History reading order, Profile share-card edit scopes and Settings category/control consistency.

## Previous slice — M273 selected-week-first Plan

- The selected week's existing session card appears directly under the week picker and week focus; remaining weeks stay in their original phase order. No extra session renderer, per-tab prescription projection or duplicated week data is introduced.
- Plan's three overview tile presentation objects have been migrated to semantic CSS classes; inline debt decreases from 3,138 to 3,135 in the actual production source, with a lowered non-growing gate. The existing browser suite additionally verifies the selected week has a single card even after navigation.
- Home/Workout/Focus/training engine behavior stays untouched. Next cohesion priority: Program overview and deep-cycle density, then Progress/History, Profile and Settings.

## Previous slice — M271 root app-bar hierarchy

- All five roots now share UiRootHeader, one actual title origin, semantic h1 destination labels and the same action row/insets. Root Plan has no Back button. The selected program is a subordinate h2 above its block/schedule context.
- Profile's header/Edit stay outside the scrolling share-card content; metadata and personalization retain their existing data owners. Progress's section navigation is inset below its shared title row. Home keeps its continuous context collapse with a stationary Home title and actions.
- Retired per-screen header geometry, brand and title overrides from both stylesheets. Reduced-motion CSS uses zero transition duration to avoid stale font/row measurements.
- Existing R65 now asserts title coordinates as well as typography; R53/B887/B831 follow the new root anatomy while retaining their interaction, touch, reflow and contrast checks. No parallel suite or tracker was added.
- Remaining placement work: Home/Preview relevance, current-week-first Plan flow, Progress records/filter hierarchy, Profile edit scopes, Settings categories, then overlay/device certification. This root-bar pass does not complete those batches.

## Previous slice — M270 shared shell

- Root destinations share one scalable title role and full visible navigation labels. The no-program Plan state retains the same title and shared creation action.
- Bottom navigation reflows at enlarged text; the expanded rail grows with labels and publishes its measured inset to the minimized workout dock.
- Progress summary columns depend on the actual text size, preserving whole readable words instead of splitting them into narrow fragments.
- UiNotice replaces duplicated app/workout snackbar presentation. A shared layout owner measures actual bottom barriers; route and rest/Focus size changes update clearance immediately. Layout transitions cannot carry Undo across a newly returned Resume control.
- Static presentation moves into named shared CSS owners. Root typography, navigation aliases and old notice offsets are retired instead of covered with new priority layers. Logging geometry, training data and policy are unchanged.

## Required release evidence

Use the existing contract registry and four workflows/thirteen jobs on the exact candidate head. Run relevant real-browser cases before publication; verify Pages and deployed hashes after merging. Existing engine generation, adaptation, quality and integration gates remain required even for UI-only work. Record evidence in the PR and existing changelog/design plan, rather than creating competing audit documents.

Actual Android certification remains open: TalkBack navigation, soft-keyboard resize, gesture Back, safe areas, offline cold start/update, process interruption/recovery, long-session timing, notifications and file sharing through the final signed wrapper. Test the package distributed by Play internal testing; a local upload-key build may use a different signing certificate. Do not describe browser emulation as device certification.


## Cohesion 09 — permanent ownership rules and whole-app sequence

Cohesion 08 (PR #214) merged five Settings switches and reminder-day selection. Cohesion 09 begins the app-wide **anti-duplication** process: remove two dead earlier important CSS declarations that are unconditionally superseded at the same selector/property, establish a CI debt ceiling, and progress through every screen in isolated passes. This is not a claim that the whole app, Android bundle or engine has been recertified. Do not disturb the compact Workout and Focus layouts.

### Required single sources

| Concern | Maintained authority | Prohibited alternative |
|---|---|---|
| Theme, paint, shape, surface, touch | Existing semantic roles in `app.css`; `UiAction`, `UiChoice`, `UiStatus`, `UiSection` | Per-screen palette, duplicate button/shadow/token set, new priority override stacking |
| Motion, focus, navigation | `modules/ui-motion.js`, existing overlay and route state owners | Animation that controls data, flash/remount workarounds, independent navigation copies |
| Next workout, day, week, cycle | Authored program/plan session projections and `program-duration.js` | Separate Today/Plan/Program/Cycle calculations or display values |
| Progression, exercise identity, volume, recovery | `modules/engine-api.js`, `next-engine/`, `training-domain/` | UI-specific training formulas, hidden fallback engines, replicated exercise catalog |
| Workout history, PRs, strength, charts | Canonical persisted revision/provenance and shared analytics | Counting deleted/superseded records twice; separate score or PR math per view |
| Backup, recovery, offline | `BackupRestorePanel`, transaction/recovery owner, `sw.js` | Multiple import previews, duplicate persistence, claiming canceled transfers succeeded |

**One source of truth does not mean one oversized component.** Share behavior, data projection and theme primitives where semantics truly match. Keep small presentation adapters for layouts or workflows with different needs. Screens may show the same canonical value in different forms, but may not independently recompute or persist it.

### CI anti-regression budget

`verification/ui-ownership-test.mjs` runs in the existing verification integration group. Its measured non-growing ceilings are **2,931** `!important` declarations, **32** repeated important properties at equal selector/at-rule scope (29 remaining collision keys), and **3,331** JSX inline `style: {` objects in `App.js`. It also blocks *new* colliding selector/property owners and duplicate shared component definitions. These are technical debt counters, **not** a verdict that every existing inline/dynamic value or override is incorrect. Lower the ceiling only with verified deletions. Never raise budgets or soften the test just to get a visual patch accepted. New presentation should use the established owner, not a fresh end-of-file specificity layer.

### Screen passes — follow this order

| Pass | Screens / owner | Simplification and acceptance |
|---|---|---|
| 0 · Foundation **started** | App shell, navigation, semantic roles, shared controls, CSS | Identify source for action/choice/surface/disclosure/motion; remove provably dead declarations and record counts; no unexpected computed-style differences |
| 1 · Home | `Home`, `HomeSessionOutline`, `SessionGamePlan`, resume and injury entry | One primary action, Game Plan in its own location, no card pile/flash, Next matches Plan and resumes directly; first-run/offline/recovery states |
| 2 · Plan / Program / creation / Cycles | `PlanView`, `ProgramView`, `Wizard`, `CustomCycleComposer`, `CycleDetail` / `CyclesView` | One current week/phase/duration/prescription/change-reason source; reduce duplicate section bars and option styles; keep editable custom cycle order and changes/Undo |
| 3 · Profile / Progress / History | `ProfileView`, `ProfileShareCard`, `ProgressDetail`, `HistoryView` | Share-card-first editing separate from page layout; aligned score, favorites, charts and history derive from canonical revisions; no fake empty data |
| 4 · Settings / Gyms / Library | `SettingsView`, `BackupRestorePanel`, `GymsSheet`, `LibraryView`, exercise/swap detail sheets | One controls/fields/toggles/segments system, consistent selected states, clear labels, equipment identity, safe import/Undo and responsive search |
| 5 · Workout / Focus **protected** | `WorkoutSession`, `WorkoutExerciseNav`, Focus, plates, rest, amplifier and injury overlays | **Do not redesign logger.** Consolidate only duplicate data/state owners with regression proof; retain steppers, active rows, target/last/actual RIR, sets, notes, rest, offline resume |
| 6 · Cross-screen / release | All sheets, dialogs, loading, empty and error states, adaptive shell, installed PWA and TWA | Reuse modal/focus/scroll ownership, certify small/expanded layouts, OS accessibility and signed-package Play tests |

Each pass starts with a concrete ownership inventory: actual function, state origin, CSS selectors, duplicated conditionals and dynamic inputs. Review task hierarchy (one primary action), typography, density, meaningful motion, error/empty/loading, and navigation. Prefer removing redundant wrappers and rules to introducing new classes. Do not create a second tracker: update **this** plan and `UI_INTERACTIONS.md` with what actually passed.

### Separate engine simplification track

Inventory and verify one canonical request route, exercise ID/catalog, allocation and time budget, volume dose, loading and RIR, shortened-session pruning, live auto-regulation, plan-change reasons/Undo, history revisions and records, strength/PR/analytics, import/export, and interruption recovery. Changes to engine policy require a reproduced defect or differential evidence proving equivalence, new replay/golden regression, and integration verification. UI-only changes must keep runtime engine files byte-identical. Avoid merging engine and visual changes into the same PR without a demonstrated coupling.

**Release definition of done:** each slice has a tiny diff, before/after debt meter, rendered populated/empty scenarios (320, 393, 834, 1280px), light/dark plus impacted saved themes, 200% text, keyboard/disabled/focus, reduced motion and forced colors where relevant, and target browser + all ten exact-head CI jobs green. Do not change test expectations to excuse a mismatch. Review physical Android/TalkBack, notification/keyboard/safe-area, offline interruption, signing, TWA domain association and Play internal testing separately; browser CI is not Android certification.

## Cohesion 10 — Home entry hierarchy (scoped first slice)

**Implemented for review:** the first-run Home no longer offers three parallel creation affordances (large “Build my program” card, empty-state “Build a program” button, and pinned “Create Program”). The pinned Compose dock remains the single primary creation action when no session or next workout is active. Templates, Custom split and Cycles remain available as secondary discovery routes. An existing program still keeps its start/preview/session outline, program switcher, injury entry, separated Game Plan, history and recovery surfaces. No engine, schedule, logging, storage or React navigation owner changed.

Home's discovery tiles now use `UiAction` for behavior and one scoped `app.css` owner for layout, text and semantic theme roles, rather than copying inline layout/color objects per tile. Their keys derive from immutable route IDs rather than an index that shifts with the program state. Measured `App.js` inline style-object occurrences decrease **3,410 → 3,393** (17 removed); the CI ceiling decreases to 3,393. CSS priority/duplicate limits remain 3,018 / 33. Existing Home scroll/masthead and Game Plan tests remain authoritative; the new `home-entry-hierarchy-test.mjs` and real-browser `home-entry-hierarchy-browser-test.mjs` additionally verify empty/populated states, primary-action count, discovery navigation and mobile/medium layout.

**Still open in Home:** dense populated dashboards, cycle progress duplication with Program, responsive disclosure control hierarchy, and initial Home/Plan projection equivalence over resumed and interrupted workouts. Those need individually proven behavior changes, not speculative style overrides. Next isolated pass: Program/Plan week/phase presentation ownership, or remaining Home sections where measured density warrants it. No claim of complete screen certification until the full queue is finished.

## Cohesion 11 — remove superseded Home color owner (first cascade pass)

The Home create action had an early `background-color:light-dark(...--pi-m3e-dark-support)` declaration and a later same-selector unconditional `background-color:light-dark(...--pi-r38-surface-1)` declaration, both `!important`. Remove only the earlier losing declaration. This does not touch the later effective declaration, hover/focus styling, layout, JS, state, or engine. Lower CI ceilings to **3,017** important declarations and **32** repeated important properties. This is a narrowly scoped cascade reduction, **not** completion of the Plan/Program screen pass. Continue with Plan/Program state and selector inventory next; avoid speculative layout changes.

## Subsequent slices

1. Continue cross-screen form/action ownership, prioritizing visible inconsistencies and removing redundant CSS only after computed-style or interaction proof.
2. Review navigation, dialogs, loading/refusal and recovery states across compact/expanded windows and enlarged text, retaining one navigation/data owner.
3. Fix reproduced engine or persistence defects against the existing replay/differential baselines. Keep all prescribed and observed effort, authored-day/revision ownership and manual input protections intact.
4. Complete signed Android packaging, store listing/declarations and physical-device checks before claiming launch readiness.

## Cohesion 12 — Plan week and pane ownership (published)

Seven Plan JSX layout/paint objects have been rehomed into one CSS declaration family with theme roles. Preserve four `UiChoice` panes, the week collapse/remembered selection, keyboard focus, and engine data. Extended R53 browser checks assert 44px targets and switch both directions. Inline style budget **3,393 → 3,386**; no new `!important`. Program week pill is the next separately proven pass.

The first CI run caught Plan labels inheriting the page font after removing inline typography: the global `#root button` reset outranked the new unscoped class. Keep the migrated tab rule within the existing expressive Plan owner specificity, so its font size and selected weight survive the reset without adding priority declarations. The unchanged B831 R47 text-fragment/48px target assertion remains the acceptance gate; no test tolerance is relaxed. PR #219 merged as `decf1f5`; all ten candidate and post-merge checks, Pages, 115 deployed-file hashes and the direct live phone smoke passed.


## Cohesion 13 — shared week and cycle-block choices (published)

`UiChoice` now owns a labelled/captioned `summary` variant for Plan weeks, Program weeks and cycle block navigation. `WeekPill` is removed. The existing selected state, keyboard/native disabled behavior, semantic theme roles, focus, forced colors and motion remain with the shared primitive. A single `.wpb-choice-rail` owns spacing and focus-ring clearance. Removed obsolete Program/Plan paint and shape overrides instead of adding another priority layer: **3,017 → 2,983** priority declarations and **3,386 → 3,378** inline style objects. Repeated-priority ceiling stays 32; tighten the other CI ceilings to the new actual counts.

State/data owners are unchanged: Plan uses `openWeek`/`pickWeek` and its existing plan projections; Program uses `weekIndex`/`pickWeek` and `plannedWeek`; cycle browsing uses `cycleBlockMetadata` and its existing `onSelect`/missing-block guard. The shared primitive formats presentation only. Manual-program choices remain selectable and readable when inactive; the redundant opacity fade is removed. Labels and captions scale in rem and wrap instead of clipping. Deload remains explicitly labelled and uses the same selected-state system.

Existing R54 coverage now exercises real manual/deload choices at 320/393/834/1280px in dark/light, 100/200% text, keyboard activation, forced-color selection and immutable saved programs/history. B890 adds actual 200% cycle-block labels and missing-block disabled selection without advancement. Keep the original R53 20-theme contrast and B831 motion/logger regressions unchanged. Publication requires all ten exact-head checks, Pages, post-merge checks and deployed-file identity; see the PR for final evidence.

Next: Cycle detail/list action and layout ownership, including expanded facts and read-only previews. This slice does not close the whole Program/Cycle screen queue or physical Android/Play certification.


## Cohesion 14 — Cycle list, detail and read-only preview ownership

One scoped Cycle CSS family now owns the list/detail headers, overview, block rows, facts, progress tracks, disclosures and preview content. `UiHeader` and `UiAction` retain shared header/action semantics. Remove the older embedded Cycle stylesheet and the separate later row/card patches; maintain the current card hierarchy and active/current/completed/preview distinctions. Normal-text block-row geometry and computed paint/typography were compared with the published baseline at 320/390/900px and match. The only remaining progress presentation objects supply the canonical percentage width; the pre-existing options Delete tone remains for the shared action-tone track.

The empty Cycle fallback has one creation action. The detail footer now declares the shared primary action instead of supplying ineffective inline accent paint beneath the secondary variant. Existing navigation, icon choice, advancement/deletion confirmations, `cycleProgress`, `cycleBlockMetadata` and `computeCell` retain ownership. Expanded next-block changes and schedule facts remain visible. Browsing/previewing does not advance a block or alter saved programs/history.

Typography uses relative sizes; narrow enlarged-text rows stack their index/chevron above the reading content when the card lacks space. Normal-text card layout stays the same. Headers wrap complete labels, preview controls have 48px targets and day disclosures retain native keyboard behavior. Active/future/completed states, keyboard focus, forced colors and reduced motion remain in the same family, with no new priority rules.

Measured debt: CSS priority declarations **2,983 → 2,931**; App inline style objects **3,378 → 3,331**; repeated-priority ceiling remains **32**. Embedded App CSS also loses 58 priority occurrences (**1,475 → 1,417**); add a non-growing 1,417 App priority ceiling and an executable escalation check so embedded styles cannot bypass the CSS budget. Tighten the executable ceilings to the new actual values. Keep engine/runtime files outside App byte-identical and preserve Workout/Focus.

Extend the existing B890 browser gate at 320/390/393/834/900/1280px: real all-cycles and detail navigation, 200% text, read-only prescriptions and week selection, keyboard preview, forced-color focus/state, saved data preservation, plus real exported-component empty/completed/missing-block fallback cases. Source contracts now assert the semantic Cycle content owner instead of the retired positional selector, and distinguish `transform` from `text-transform` when banning geometry animation. Require all ten candidate/main checks, Pages and deployed identity; record final evidence in the PR.

Next: Program/cycle creation controls (`Wizard` and custom block composer), followed by remaining block-review presentation. Whole-app and physical Android/Play certification remain open.


## Cohesion 16 and 17 — creation controls

Cohesion 16 (PR #225) merged as `b20ec6d` after all ten candidate checks passed: shared Wizard choices, heading/options layout, units, program/cycle mode and controlled name input are CSS-owned. The candidate also contains the browser-proven deferred Focus tap-selection correction. All unrelated Workout layout and engine behavior are preserved.

Cohesion 17 migrates cycle-template cards and the adaptation checkbox into the existing Wizard family, reusing shared cards/stacks/rows and type/spacing/theme roles. Selection follows the real ARIA state. Move cycle-heading reflow into its single class owner and retire the redundant priority rule. No training, generation, progression, storage or logger changes. Debt: 3,303 → 3,289 inline style objects; 2,923 → 2,922 external priorities; 32 → 30 repeated priority declarations (27 remaining collision keys); embedded priorities stay 1,417. Tighten all affected CI ceilings. R74 now exercises each template by keyboard, custom-week retention across template switches, reversible adaptation, and the existing enlarged text and Back/Next journeys. Candidate publication requires all ten final-head checks, Pages and post-merge evidence.

Next: remaining Wizard equipment/constraint and review presentation, then Settings/Profile cards. Full application migration and physical Android/Play certification remain open.

## Cohesion 18 — equipment and constraint ownership

Consolidate setup presets and constraint choices with shared tonal actions. Share equipment exclusion rendering between bodyweight and rack restrictions, including the existing gym-editor adapter. Move static labels, warning and horizontal-preset layout into the existing Wizard CSS owner; extend the live palette with its warning role. Debt: inline style objects 3,289 → 3,266; external priorities 2,922 → 2,918; embedded and repeated priorities unchanged. R74 covers preset selection, reversible rack exclusions and each constraint value by keyboard in addition to text/reflow/draft coverage. The extended Cohesion 17 browser gate exposed legacy 42px styling overriding shared custom-cycle fields. Exclude shared controls from that legacy form owner so the canonical 48px field and focus treatment apply. Cohesion 17 and 18 ship together through PR #226 only after all ten final-head checks and deployed identity pass.

Next: finish remaining Wizard shell, focus-point rows and split/session guidance; then Settings/Profile cards. The read-only review pane already uses class-owned presentation. Physical Android and Play certification remain open.

## Cohesion 19 — Wizard shell and focus ownership

Cohesion 17–18 merged in PR #226 as `12d6ff7`; all ten candidate/main checks passed, Pages deployed, and all 109 published runtime/UI hashes matched. Cohesion 19 completes the remaining Wizard shell, muscle-focus rows, shared setup steppers and split/session guidance. Use existing header/action/card primitives and theme/type/spacing roles. Keep only the calculated progress transform inline. Remove superseded Wizard-only CSS layers instead of appending priority patches. Preserve all configuration, generation and persistence behavior.

Measured debt: 3,266 → 3,233 inline objects; 2,918 → 2,858 external priorities; 1,417 → 1,376 embedded priorities; repeated-priority ceiling remains 30. Tighten existing budgets and guard the complete StepBody and setup stepper. R74 adds exact five-point/per-muscle limit and reversal checks plus normal/enlarged phone captures. Require all ten candidate/main checks, visual review, Pages and deployed identity before completion.

Next: remaining Settings/Profile card owners, followed by Home/Plan and Progress/History. Legacy option-card and global priority cleanup remains tracked; physical Android/Play certification is open.

Cohesion 19 short-window correction: the 200% text / 900×480 cycle-focus gate exposed an oversized footer. The named action region now uses at most 45% of the Wizard height and scrolls natively; R74 requires at least 44px of form space and checks keyboard focus reveals complete primary/early-finish actions. Scoped text-action typography overrides only the generic root button font reset, with no new priority declarations.

## Cohesion 20 — Settings groups and Profile strength club

Give all eight Settings row groups explicit shared cards. Migrate stable row/title and gym-navigation presentation into the Settings owner; remove legacy card/row/title layers and the unnamed parent selector. Preserve fields, settings callbacks, switch ownership and category navigation. Migrate the Profile club to shared cards and 48px tonal actions; retain lifted versus estimated snapshots, tiers, missing-lift guidance, units, share callbacks and progress semantics. Only the computed club progress width remains inline. Preserve all other Profile cascade priorities by using zero-specificity exclusions for the migrated club.

Measured debt: inline objects 3,233 → 3,195; external priorities 2,858 → 2,812; embedded priorities 1,376 → 1,325. Repeated-priority ceiling remains 30 (27 collision keys). Tighten source budgets and add Settings-group and club-progress ownership guards. R76 checks grouped card ownership/paint and 200% copy/control containment; B907 adds keyboard measure reversal, distinct totals, normal/enlarged club targets and preserved history. Require all ten candidate/main checks, image review, Pages and 109-file deployed identity.

Next: remaining Settings choice controls and Profile Strength/Achievements card presentation, followed by Home/Plan and Progress/History. This scoped pass does not close the whole Settings/Profile queue or physical Android/Play certification.

## October 21 visual/source tab audit — phone, portrait and landscape reference handoff

**Evidence and limits:** Compared the supplied phone/tablet reference ZIPs with the current production-source component hierarchy and app CSS; inspected GitHub Actions phone captures from the exact approved Cohesion 21 candidate, including 393px and 320px/enlarged-text states. The references are design inspiration, not a source-of-truth replacement. The approved switch/Home fix was merged in PR #230 after all ten candidate checks passed. This is a *finding log*, not a claim that every state or physical Android device is visually certified. Existing tests, app engine, Workout and Focus behavior are not changed by this audit.

| Surface | Confirmed hierarchy / strengths | Remaining gap and owner | Next acceptance gate |
|---|---|---|---|
| Shared five-tab navigation | Explicit Home, Plan, Progress, Profile, Settings routes; active page has `aria-current`, tab buttons retain accessible labels. Normal 393px presentation is coherent. | **P1**: 320px/enlarged-text captures visibly wrap bottom-tab labels within words (notably Progress/Settings), and 1280px/enlarged-text captures likewise split labels in the side rail. The 320px Home/Plan headings also crowd and break. Check `TabBar` and the existing R40 `.wpb-tabbar .wpb-tab>span` CSS owner. Do not fix by clipping, shrinking essential touch targets, or hiding accessible names. | Real rendered 320/393/834/1280px, normal and 200% text in dark/light; visible unbroken or deliberately legible nav labels, no cross-axis overflow, stable active indicator, focus/order and 48px targets. |
| Home | At 393px one clear Start workout action, Preview, an expanded session outline and a separate short Game Plan. Cohesion 21 removes stacked outline padding and restores the correct switch contrast elsewhere. | **P1**: The 320px enlarged-text Hero headline breaks awkwardly; revisit the responsive heading and summary/orbit composition, not training data. **P2**: Home header/hero have multiple legacy cascade owners. | Empty/populated/resume/injury/plan-change states; preview content matches Plan; no increased gap above first exercise, no tab-bar overlap, enlarged-text reflow. |
| Plan | At 393px Program name leads, Up Next follows, then the four Schedule/Volume/Progression/Blueprint modes and selectable weeks. | **P1/P2**: At 320px enlarged text the heading and pane/week rails become cramped. Plan title typography has multiple scattered specificity owners; migrate only after measured computed-style equivalence. | Current and previewed weeks, long manual program names, keyboard pane switching, 200% text, week labels/summary and Home↔Plan prescription parity. |
| Progress / History | Four meaningful panes (Sessions, Lifts, Volume, Body); glance tiles, filters, chart sections and empty states have understandable roles. | **P2**: Complex view retains ~237 static/dynamic inline presentation objects in `HistoryView`; dense filter/chart ownership risks mismatched typography and row spacing. The reviewed 393px shot has empty data, **not** a populated history sign-off. | Empty and populated history with long exercise names, measurement editor, charts, favorites, filters and grouping at 320/393/834/1280 and 200% text; verify no horizontal scroll/overlays. |
| Profile | At 393px the customizable share card is first, followed by Arrange Profile, level, summary stats, strength and achievements. The shared strength-club card and actions are now class owned. | **P2**: The page-title has multiple old font-size declarations; strength/achievement cards still use significant one-off inline styling. Keep share-card customization distinct from page-section arrangement; avoid merging unlike behaviors. | 320/393 and tablet, long name, absent/populated scores, emphasized push/pull/legs alignment, share export, 200% text and toggled sections. |
| Settings | Eight explicit shared cards, recognizable section jump and legible 393px control rows; Cohesion 21 restores switch track/thumb contrast with a 48px hit target. | **P2**: Four-option experience selector is visually compact; the jump control and remaining segmented choices still have legacy inline sizing. Review 320px and enlarged text without squeezing labels or adopting a second control dialect. | 320/393, normal/200%, dark/light and all saved themes; on/off switch containment, long translated labels, keyboard select, focus, settings persistence, forced-color states. |
| Program / Cycles / creation | Existing week/cycle selection and creation flows already share UiChoice and recent class-owned components; `CyclesView` has near-zero inline presentation. | **P2**: `ProgramView` remains one of the largest inline-layout owners (~456 style objects). Avoid cosmetic redesign of workout prescriptions or duplicating engine projections. | Current/next/completed cycle, 4–10 week custom order, read-only previews, session prescription, configuration forms, keyboard and enlarged text. |
| Workout / Focus | Protected compact logger with steppers, Target/Previous/Actual RIR, notes, rest timer, injury and offline recovery; existing candidate browser CI passed. | **Protected:** no speculative visual refactor in this audit. Physical Android and signed Play/TWA certification remain outstanding. | Preserve logger geometry and exact persistence; future changes require separately reviewed real phone/Focus captures and interaction replays. |

**Audited legacy ownership baseline (Cohesion 21):** 3,193 React inline style objects, 2,793 external CSS priorities and 1,325 embedded priorities. These numbers describe technical debt, not a score of visual quality. Do not raise the budgets or add an end-of-file CSS override. Canonical spacing/type/paint and shared UI components are specified in `DESIGN_SYSTEM.md`.

**Next isolated, testable passes:** (1) navigation and 320px/200% heading reflow, (2) Plan title/pane and Profile heading ownership, (3) populated Progress/History hierarchy and Settings choice controls, (4) Program overview density; retain the protected Workout/Focus design. A pass is complete only when exact-head ten-job CI, phone/tablet rendered screenshots, browser interactions and cache integrity pass. The Android physical-device checklist remains separate.

Source capture reference: [candidate phone/offline integrity run](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/39685915651) (screenshots including R53/R74/R76); compare against the attached Phone, Tablet portrait and Tablet landscape design handoffs.

## October 22 full tab audit — implementation ledger

The source/rendered-screen audit at main `f1725a3` defines a task-based placement plan: Home prepares today; Plan manages training; Progress reviews history/results; Profile leads with the customizable share card; Settings configures equipment/preferences/data. Preserve all capabilities and the current Workout/Focus geometry.

1. **M270 shared shell — implemented; verification tracked in PR.** One scalable root title, full navigation names, reflowing summaries and measured shared notices. The expanded rail and minimized workout inset grow together. Existing motion stays authoritative.
2. **Home and preview — next.** Keep session outline visible; keep Game Plan concise/expandable; put time available and exercise inspection ahead of detailed preview coaching; use one safe Start/Resume action region.
3. **Plan and Program — pending.** Selected/current-week sessions directly below the week control; clear management/edit scope. Reproduce and resolve the audit fixture's phase/duration projection difference before changing labels.
4. **Progress — pending.** Show recent session records before the full calendar/dashboard; retain explicit grouping, calendar and filters; favorite-ordered Lifts and clear Volume/Body interpretation.
5. **Profile — pending.** Separate Edit details, Edit card and page layout customization; keep aligned strength/club rows and saved choices.
6. **Settings/tools — pending.** Categories by purpose, shared controls and clear global/gym/program scopes; retain every existing preference/tool and safe restore/Undo.
7. **Workout/overlays — pending certification.** Surrounding consistency only; verify keyboard/rest/active-set visibility, notes/plates/amplifiers, completion and recovery without reworking the ledger.
8. **Whole-app/device sign-off — pending.** Compare populated/empty states together, large text, themes, motion interruption, offline and physical Android. Passing CSS debt budgets alone does not certify deliberate design.
