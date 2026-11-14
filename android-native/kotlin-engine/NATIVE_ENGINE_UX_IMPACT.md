# Pursuit Iron — native UI and UX changes required by the Kotlin engine

**Status:** integration documentation only; audited 2026-11-03 against Compose foundation c42f90b0. This file changes no native UI, production web app or active training authority.

**Principle:** create a materially better Android experience than the web app. Kotlin owns training decisions; native-owned storage owns immutable actual observations; Compose owns fast readable presentation, explicit user choices, and reliable feedback. All 20 original themes must remain available under shared Material 3 Expressive tokens and responsive motion.

The binding product-level review is [Product cohesion and improvement](../PRODUCT_UI_DESIGN_CONTRACT.md#product-cohesion-and-improvement--binding-beta-requirement-2026-11-14).
Every carried feature needs a deliberate purpose, canonical location, coherent
interaction and complete task flow, with evidence of improvement over its web
experience. Functional parity and engine tests do not establish UX completion.

## Engine-to-screen impact table

| Native screen / surface | Required UI and engine-data changes | Starting Compose source | User-facing acceptance |
| --- | --- | --- | --- |
| Onboarding | Experience, hypertrophy/strength/powerbuilding goal, 3–6 day Full Body/UL/PPL/ULPPL, time buckets/hard cap, bodyweight policy, focus/reduced muscle, per-day barbell cap, supersets, equipment/units; save/reopen | NativeOnboardingScreen.kt; NativeScreens.kt; NativeRuntimeHost.kt | No invented defaults; review real generated program; cold restore |
| Gyms/equipment | Named gyms, per-exercise physical stock/increments, bar and Smith weights, plates, dumbbell per-hand, cable/machine variants, bench/rack/attachment availability | NativeGymInventoryEditor.kt; NativeVerifiedLoadOptions.kt; NativeVerifiedPlateBreakdown.kt | No 38.5/159.5 phantom weights; two independent machine loads stay separate |
| Plan builder | Whole week and day purpose, all actual sets/volume, time and recovery warnings; lock/rest/hard/long days; allow triceps on lower; review exact prescription, then confirm save | NativeProgramDraftEditor.kt; NativePlanPresentationHost.kt; WorkoutOutlinePanel.kt | Day, exercise, sets, rep ranges, rest and equipment shown before approving |
| Custom Plan | Create/duplicate/edit, reorder, pinned exercise, all per-set progression policies, attachment, supersets, myo/drop, reversible change summary | NativeProgramDraftEditor.kt | No silent lost settings; old authored details survive phase changes |
| Cycles | Hypertrophy -> strength -> peak/powerbuilding block overview, upcoming dose/frequency and focus, separate Adapt split and Adapt exercises toggles, diff/Undo | NativeScreens.kt; NativePlanPresentationHost.kt | Explain and flag disproportionate 30->11 collapse, preserve user custom and gym |
| Today | Separate Today's Game Plan from expanded workout outline; LOW/NORMAL/STRONG readiness; reported injury/restriction; 45/30/20 shortening; review why today differs; safe start | NativeScreens.kt; NativePreviewApp.kt | Approved adjustment changes real session; no hidden failure or fake progress |
| Workout preview | Exact issued warmups/working/extension rows; sets/reps/load basis/RIR/rest/time; status and source version; changes before confirming Start | NativePlanPresentationHost.kt; WorkoutOutlinePanel.kt | Preview matches pinned session; invalid equipment or missing policy blocks Start with next action |
| Train / one-page logger | Name left, Set 1 of 4 beneath, compact progression chip, right Skip/History/Info and non-wrapping Key Lift/Compound tags; weight/reps/RIR steppers + direct entry, Complete on same row | NativeWorkoutScreen.kt; NativeComponents.kt | No auto-overwrite, fewer taps than web, no duplicate RIR or cramped controls |
| Train / equipment controls | Per-exercise +/- valid physical increments, long-press + type-to-replace, last-set quick reuse, persist Last/Target switch, visual plate calculator with bar/Smith selector | NativeVerifiedPlateBreakdown.kt; NativeComponents.kt | No off-stock options or moving column layouts |
| Train / Focus/tools | Full logger, rest, notes, attachments, warmup, superset, swap, Last/Target and plate calc all functional in Focus; tablet/foldable tools dock | NativeWorkoutScreen.kt; NativeRestTimer.kt; NativeWarmupPanel.kt; NativeUpNextPanel.kt | No hidden/unreachable actions, one rest bar, keyboard and accessibility safe |
| Train / adaptations | Preview original vs changed set list, user report restriction, related muscles, safe shorter session, next-set option and explicit confirmation | NativePreviewApp.kt; NativeRuntimeHost.kt | No automatic changed load or silent history adjustment |
| Train / techniques | Typed warmups, working sets and extensions; correct myo/drop rest and counts, superset groups/order; safe re-prescription for exercise swap | NativeWorkoutScreen.kt; NativeWarmupPanel.kt; ACCESSORY_SUBSTITUTION_GATE.md | No zero-set substitution, no lost amplifier, no copied incompatible progressions |
| Completion and Resume | Confirm skip/finish, show last logged and remaining sets, durable acknowledgment, saved draft preservation, process-death retries, one history publish | NativeWorkoutScreen.kt; NativeRuntimeHost.kt | Rotation/kill/offline/disk-full recovery without duplicated PR or changed issued target |
| Insights | History and Last, corrected PRs, standards, 1000 lb club, volume/consistency by muscle and region, favorites, calendar, bodyweight/measurement trends | NativeHistoryPanel.kt; NativeHistoryCalendarPanel.kt; NativeScreens.kt | Old history edits correctly update feedback and later recommendations |
| Profile/settings | Full durable profile, actual preferences, gym, 20 themes, smooth native profile card and progress, accessibility/reduced motion | NativeDesignTokens.kt; NativeAppearanceSheet.kt; NativeScreens.kt | Theme and preferences survive cold restart, proper contrast and responsive layout |
| Backup/restore | Explicit export/import/merge review and Undo, conflict/tombstone warning, rollback, data validation, browser/native provenance | NativeBackupExportPanel.kt; NativeBackupMergeReviewPanel.kt | Failed import never mutates live data; accepted merge atomic and reviewable |

Referenced files are **integration locations**, not proof that the listed feature works today.

## Mandatory UI state and data contract

- **Observation:** actual reps/load/optional actual RIR or null, failed attempt, skipped, incomplete, note or correction. Show recorded fact only after durable acknowledgment; never treat issued RIR as actual RIR.
- **Unissued candidate:** generated program, alternate load, deload, short session, substitution or phase proposal. Display one concise reason, affected exercises, constraints/confidence and original-vs-proposed delta. Never pretend it is an issued target.
- **Issued target:** immutable engine revision, workout and set keys, movement variant/attachment, exact load unit/basis/verified stock, rep range, nullable RIR, rest, warmup and extension rows. Pin once, do not regenerate on recomposition.
- **Authorization:** user confirms scope (next set / today / future program / whole cycle). A proposed split or exercise adaptation requires the relevant toggle and explicit diff approval.
- **Stored receipt:** idempotent revision-aware acknowledgment; only then mark Complete, update History/Last/PR and advance next-session cursor.
- **Error states:** missing context, missing gym equipment, invalid increment, infeasible time/dose, unknown RIR, stale revision, offline/retryable write, conflict, lost acknowledgment, no safe candidate. Every error must offer a next action. Unknown does not mean zero.

## Shared Material 3 Expressive design requirements

1. One centralized token and component system for typography, spacing, surfaces, shape, elevation and theme color across Today / Plan / Train / Insights / Profile. No competing styles or grey placeholders; preserve all 20 original palettes and theme contrast.
2. Native, meaningful and smooth motion for expand/collapse, set completion, profile card, Focus and transitions, reduced-motion compliant. Avoid screen flash/jump and sticky gaps.
3. Workout first: single page, keyboard-safe number fields, bounded steppers, one reps-left indicator, completed sets muted, one rest timer, all tools reachable in Focus, no set-by-set explanation banners.
4. Keep right-side toolbar tags and Skip/History/Info aligned; concise coach explanation only at day or exercise level with optional Why this? expansion.
5. On phone use uncluttered bottom navigation and fast direct actions; on foldable/tablet use native rail and visible workout tool dock. Large font/TalkBack semantics and focus order must work.
6. Timer uses Android elapsed time and is not a training fact. Keep screen awake during actual workout only; restore previous flag afterward.
7. Any UI change to program/cycle/shorten/swap shows what changes and what remains unchanged, with clear Cancel/Confirm, review of stale revisions and no destructive optimistic UI.
8. Measure native vs web taps/time for comparable workflows, with real baseline; do not invent numbers or certify superiority from a visual mockup.

## Native acceptance checklist (not yet passed)

| ID | Scenario |
| --- | --- |
| U01 | Setup for all 3 goals, four splits, 3–6 days, exact gym, save then cold restore |
| U02 | Review complete advanced 60–90-minute plan, customize all key settings and confirm |
| U03 | Readiness LOW/STRONG and 45/30/20 shorter workout, compare and confirm actual changed sets |
| U04 | Log/correct set with direct steppers, Last/Target, Focus, rest and notes without navigation |
| U05 | Reject off-increment 38.5/159.5, missing attachment and wrong independent machine stock |
| U06 | Warmups, working/extension, myo/drop, superset and recovery timing all log exact facts |
| U07 | Exercise swap reissues valid targets without load/PR or old prescription transfer |
| U08 | Custom program across hypertrophy/strength/peak, optional split/exercise adaptation, same amplifiers |
| U09 | Rotate, background/force-close, disk-full and lost acknowledgment; resume/Finish exactly once |
| U10 | Correct old workout; Last, PR, charts and next coach decision all revise consistently |
| U11 | Export, preview import, conflict/merge, Undo, cold restart; no data loss |
| U12 | Test phone and foldable/tablet, all 20 themes, TalkBack, large text, IME and reduced motion |
| U13 | Paired measured web/native task times and tap counts plus experienced-coach review |

**Branch constraint:** the Kotlin-only engine and its integrated Compose app are developed and reviewed together on `architecture/kotlin-engine-rebuild`. Historical Compose filenames above are integration references, not a requirement to move this work to another native branch. Never auto-merge this branch into production web/main or the other native developer's branch. No APK until the full UI is finished.
