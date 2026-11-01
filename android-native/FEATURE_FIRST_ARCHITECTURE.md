# Pursuit Iron Native — feature-first redesign contract

## Product decision

The current Pursuit Iron web application is a **behavioral reference and data authority**, not a pixel, component or navigation specification. Native Android is allowed to reorganize, combine, simplify or completely replace screen layouts where doing so materially improves training. No screen-by-screen cloning mandate. The live web app continues independently.

**A feature is preserved when an actual end-to-end user scenario and its invariants pass**: input, calculation, displayed prescription, user ownership, persistence, resume, correction, accessibility and history interpretation. Screenshots alone do not prove preservation.

## Native information architecture (proposed, not a copy)

- **Today** — training decision and next meaningful action. Readiness, next session, and situational adjustments belong near the decision, not spread across pages.
- **Train** — single active-session workspace with focused set controls, exercise context, rest, warm-ups, substitutions, tools and safe interruption/recovery. "Focus" is an alternate presentation of the same state, not a separate engine.
- **Plan** — program structure, upcoming sessions, goal/phase, split, equipment, schedule, volume, custom-program edits and adaptive-cycle preferences.
- **Insights** — history, PRs, progress charts, consistency, volume, bodyweight, strength standards and revision-aware comparisons.
- **Contextual sheets and settings** — exercise library, exercise info/attachments, injury constraints, session length, program management, backup/restore, gyms, preferences and profile. Not every feature deserves a persistent top-level tab.

The debug preview can demonstrate interactions with explicitly labeled ephemeral examples. **Never treat those examples as engine suggestions, set completions, storage writes or backup imports.**

## Non-negotiable engine and data invariants

| Capability | Canonical owner / starting reference | Native parity requirement |
| --- | --- | --- |
| Program construction and split/equipment constraints | `modules/engine-api.js`, `modules/next-engine/generate.js`, `modules/training-domain/programs.js` | Same valid options, exercise compatibility, day structure and stable IDs |
| Adaptive cycles and block decisions | `modules/next-engine/adaptive-cycle-upgrade.js`, `modules/next-engine/cycle-runtime-adapter.js` | Custom programs first-class; adapt split/exercises when authorized; stable cycle/phase identity |
| Double progression and observed-set feedback | `modules/next-engine/performance.js`, `modules/next-engine/workout-runtime.js` | Weight does not automatically jump after poor reps; respect set fatigue and user-entered weight |
| RIR and load increments | `modules/next-engine/loading.js`, `modules/training-domain/prescriptions.js` | Rep/RIR policies; plates and machine-specific increments; no impossible fractional loads |
| Workout runtime, history, edits and resumes | `modules/next-engine/workout-history-adapter.js`, `modules/next-engine/history-contract.js`, `modules/workout-session-controls.js` | Confirmed set completion, last/target, ordering, history revisions, idempotent resume |
| Readiness and injury handling | `modules/workout-readiness.js`, `modules/injury-tools.js` | Session-only and user-authored changes; never silently overwrite logged/user-owned values |
| Safe session trimming | `modules/next-engine/session-shortening.js` | Full/45/30/20; preserve warmup/rest/intensity integrity; no unintended program edit |
| History, PRs, analytics | `modules/training-domain/analytics.js`, `modules/workout-pr.js` | No duplicate PR or historical volume from superseded revisions |
| Backup/restore and conflict resolution | `modules/training-domain/records.js`, `modules/backup-restore-panel.js`, App callbacks | v13 canonical parser, edit clocks/tombstones, preview and Undo, never destructive overwrite |
| User preferences and equipment | `modules/program-settings-sheet.js`, `modules/gym-load-fields.js` | Same settings semantics, units, equipment and available increment rules |

Additional feature families to retain or improve: exercise discovery, attachments and substitutions; custom notes and pinned notes; warm-up support; rest timer; plate calculator; supersets and last-set amplifiers; quick workout adjustment; progress milestones and thousand-pound club; visual share cards; custom gym and bar weight; consistent first-class history edit; offline and Android interruption recovery; bodyweight and strength standards.

**Not invariants:** current React component hierarchy, existing tab order, CSS class names, card/banners proliferation, duplicated explanation copy, current modal hierarchy, pixel positions or exact animations. Those may be removed.

## Feature gate definitions

- **Native preview:** UI behavior can be explored using explicitly labeled transient samples; no persisted training data.
- **Canonical engine:** Kotlin may display real engine outputs only after an isolated, versioned, pinned JS runtime passes independent fixtures for the feature.
- **Verified storage:** anything involving user history, program mutations or backup import also requires schema/merge/round-trip tests and Android interruption testing.
- **Device integration:** timers, foreground/background, keyboard, vibration, accessibility and screen-awake controls must be device-verified.

Never substitute native hardcoded training formulas as a temporary workaround. Never imply a feature is complete solely because its UI is listed in this roadmap.

## Workflow-level acceptance before production

1. **Onboard → customize → generate:** valid equipment, 3–6 days, goal, time range, schedule, muscle focus/reduction and split; inspect full generated workouts.
2. **Open session → adjust → log → resume → finish:** readiness and injury, Full/45/30/20 trim, working sets, increments, RIR, notes, warm-ups, rest, focus, interruption and recovery; compare all engine outputs with JS.
3. **Finish → review → next session:** progressive overload decisions, unchanged user-owned loads, real-only PRs, history revision identity and Undo.
4. **Customize → cycle transition:** preserve custom programming and user-approved split/exercise adaptation, equipment constraints, MEV/MAV/MRV semantics.
5. **Backup → validate → preview → import → edit → export:** canonical v13 store, preserves timestamps/IDs, revision/tombstone policy, rollback on failure, no data loss.
6. **Accessibility:** narrow/wide, text 200%, TalkBack, reduced motion, edge-to-edge keyboard, contrast, portrait/landscape and interruption on real devices.

## Execution order

A. Compose information architecture and shared components, native responsive layouts and meaningful preview interactions — **no engine writes**.
B. Isolated JS runtime bundle with immutable canonical export manifest, handshake and deterministic fixture parity.
C. Read-only data bridge with canonical historical projections and safe preview import.
D. Real Workout vertical slice with transactional set logging, failure recovery and device tests.
E. Program authoring, insights, library and full-feature acceptance; then release readiness.

## Future Kotlin engine without an all-at-once rewrite

The `domain-contract` package now defines typed `ProgressionPreviewInput`, `ProgressionPreviewResult` and `ParityGatedProgressionRouter` contracts. Read-only fixed JS golden fixtures in `engine-contract/` are regression-checked in source CI. Certified Kotlin backends may eventually replace JS per feature when they exactly match protocol version, pinned source digest and independent fixture digest. No Kotlin training formulas are in production; feature routing currently has no certified runtime backend.

## Next data milestone: canonical v13 saved-program reference (not yet enabled)

The native read-only `ReadOnlyProgramReference` gate is now a **contract**, not a
program loader. The canonical v13 store keeps authored programs under
`saved[]`; individual records have stable `id` and `updatedAt` clocks,
`savedAt` is a store-level legacy/equal-revision tie breaker, and a
`tombs[programId]` deletion timestamp at or after `updatedAt` hides the
record. These facts are owned by `modules/training-domain/records.js` and must
not be rewritten into a second, diverging merge algorithm on Android.

Before any native screen displays a **real** program:

1. Validate a read-only copy with `parseStoredData` inside the trusted,
   pinned canonical JavaScript runtime; reject unsupported future versions,
   malformed lists and any untrusted program structure.
2. Select a record only by its exact canonical program ID, not list position
   or a guessed recent date. Preserve custom authored programs and all advanced
   slot settings without collapsing them into generic exercises.
3. Derive and pin its `updatedAt`, overall store `savedAt`, and SHA-256 of
   the exact source snapshot. Reject if the snapshot changes, its version is
   unsupported, the program clock differs or a deletion tombstone supersedes it.
4. For an actual workout use canonical schedule/cycle/phase and session
   prescription code to select the correct week. The existing **synthetic**
   `workoutOutline` route must NOT be reused as if it were personalized.
5. Compare a source-backed, read-only outline with a canonical web projection
   across custom plans, cycle transitions, user overrides, equipment changes and
   undo/edit revisions. Until this passes, keep Compose in the explicit
   unconnected state and all workout logging disabled.

`ReadOnlyProgramReferenceGate` alone cannot authenticate the snapshot digest,
parse a backup, recover stale data or authorize writes. Only a future trusted
source adapter and device/interruption test can do that.


## Current-program day browsing: first read-only Plan to Train vertical slice

A program inspected through the existing canonical current-Plan gateway can
now select a specific training day and navigate to Train. The transient
ReadOnlyTrainingDaySelection holds only the exact revision reference and
stable day ID, not the program, workout or history. Train re-resolves the
selection against the still-approved current Plan presentation, rejects
changed source SHA/revision, missing or deleted days and detached navigation,
and shows only the canonical day name and exercise IDs. It does NOT invent
exercise names, weights, sets, RIR, warm-up or progression suggestions.

This is intentionally an end-to-end read-only navigation path, NOT a working
logger; the old explicitly labeled demo remains separate for users with
no verified current program. No active store is connected in the installable
preview, so synthetic demo UI is unaffected and no program is saved.
