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

`verification/ui-ownership-test.mjs` runs in the existing verification integration group. Its measured non-growing ceilings are **3,018** `!important` declarations, **33** repeated important properties at equal selector/at-rule scope (30 remaining collision keys), and **3,410** JSX inline `style: {` objects in `App.js`. It also blocks *new* colliding selector/property owners and duplicate shared component definitions. These are technical debt counters, **not** a verdict that every existing inline/dynamic value or override is incorrect. Lower the ceiling only with verified deletions. Never raise budgets or soften the test just to get a visual patch accepted. New presentation should use the established owner, not a fresh end-of-file specificity layer.

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

## Subsequent slices

1. Continue cross-screen form/action ownership, prioritizing visible inconsistencies and removing redundant CSS only after computed-style or interaction proof.
2. Review navigation, dialogs, loading/refusal and recovery states across compact/expanded windows and enlarged text, retaining one navigation/data owner.
3. Fix reproduced engine or persistence defects against the existing replay/differential baselines. Keep all prescribed and observed effort, authored-day/revision ownership and manual input protections intact.
4. Complete signed Android packaging, store listing/declarations and physical-device checks before claiming launch readiness.
