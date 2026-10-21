# Whole-app launch readiness

Updated October 21, 2026. This is the single current next-update plan. It supersedes the October 8 workout proposal: compact logging, coaching, session choices and program-change previews already exist. Preserve the user's current Workout and Focus experience.

## Current baseline and ownership

App 4.0.0 build 905 / Engine 0.65.9 / M234. `RELEASE_MANIFEST.json` is authoritative for shipped versions and hashes; `BUILD_PROFILE.json` records the current UI slice. Same-build UI repairs receive a new content-addressed service-worker cache. No new training-model identity is implied by this plan.

| Area | Maintained owner | Next work and acceptance |
|---|---|---|
| Shared UI | UiAction, UiChoice and semantic theme roles in `modules/App.js`; `app.css` | Continue migrating existing controls and removing provably superseded declarations. Preserve palette, focus, disabled, motion and forced-colors behavior; never solve conflicts by adding another override layer. |
| Home, Plan, Profile, Progress | Existing screen components and shared navigation state | Review empty/populated/error states, reading order, action hierarchy and 320px/200% text layouts. Profile and chart editors already share controls. Preserve saved selections and record content. |
| Settings and transfers | SettingsView preferences; BackupRestorePanel transfers and Undo; StorageStatusCard browser capability state | This slice separates backup ownership, shares action styling, reports transfer outcomes, prevents duplicate transfers and stale file reads, and distinguishes CSV from a full restore copy. Validate real restore/Undo and storage denial/grant. |
| Workout and Focus | Existing shared workout state, runtime and mobile lifecycle | Retain compact rows, direct input, steppers, target/last-time behavior, rest timers, effort provenance and Undo. Verify interruption, keyboard, safe areas and exact-once completion; no visual redesign. |
| Engine | `modules/engine-api.js`, canonical next-engine and training-domain modules | Continue existing independent, differential and replay gates. Change policy only for a reproduced defect with evidence and a regression. No duplicate engine, adapter or exporter. |
| Persistence and offline updates | Existing store migration/merge/queue and service worker | Corrupt/future saves must not be overwritten. Preview before restore, preserve drafts, require successful persistence before completing a workout, verify offline cache integrity and cancellation recovery. |
| Android / Play | `play/README.md`, final TWA package and Play Console | Resolve package identity and signing certificate association at the domain root, build the release bundle, validate actual permissions/SDKs and complete account-specific declarations/testing. Repository/browser checks cannot certify a signed Android package. |

## This slice

- A full backup marker changes only for full-backup transfer actions. CSV copy and cancelled/failed transfers do not suppress the backup reminder.
- Every transfer reports its outcome. Download messaging says a request was made and asks the user to verify the file, without claiming browser delivery proves a saved copy.
- File selection clears the old executable preview immediately. Oversized/unreadable files cannot leave an old restore ready to confirm; newer selection, typing, closing and unmounting invalidate pending reads.
- Transfer controls have one shared style owner, a minimum 48px target and wrapping action rows. Backup fields retain accessible names and native text selection.
- Storage protection displays only what the browser reports. Denial/error is visible, and a failed usage estimate cannot hide a successful persistence check.
- Undo stays in the restore panel until explicitly closed or Settings is left. No delayed automatic dismissal can close a later operation.

## Required release evidence

Use the existing contract registry and four workflows/ten checks on the exact candidate head. Run relevant real-browser cases before publication; verify Pages and deployed hashes after merging. Existing engine generation, adaptation, quality and integration gates remain required even for UI-only work. Record evidence in the PR and existing changelog/design plan, rather than creating competing audit documents.

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
