# Pursuit Iron — complete engine scope and acceptance ledger

**Current progress reference — 2026-11-08:** the dated implementation notes
below are the original scope baseline, not current feature certification.
The isolated Kotlin Lab now has native-owned issuance and training/restart
flows. See [release readiness](../RELEASE_READINESS.md) for current evidence
and gaps; all 14 capabilities and E01–E41 acceptance requirements below remain.

**2026-11-03 source audit. Planning document, not certification.**
Inspected the independent Kotlin rebuild at fa2f2168 and the separate native Compose foundation at c42f90b0. The new engine is not connected to the native app. None of the 14 FeatureCatalog capabilities is certified better than web on a physical device.

**Status key:** R = existing *headless Kotlin review-only* code plus tests; N = evidence of a source-level native/JS path on the Compose branch, not device certification; P = planned or not fully implemented; G = blocked by another safety/ownership gate. R never means executable live engine. Every row below remains subject to independent human-coach quality, migration and real-device acceptance.

## All 14 product feature capabilities

| FeatureCatalog ID | Existing bounded evidence | Required unfinished engine and/or product behavior |
| --- | --- | --- |
| adaptive-progression | R: set facts, conservative double-progression reviews, preliminary fatigue and coach decisions; N: old engine-issued targets | P: actual DP/linear/strength/e1RM policies, intra-set fatigue, earned changes, plateau response, equipment-bound load prescriptions, history feedback |
| workout-logging | R: factual outcome and non-issuing prescription drafts; N: working/extension journal, correction, finish/retry | P: Kotlin issued targets, complete warmup/working/amplifier and superset logging, real corrections, pinned session version, idempotent interrupted execution |
| session-adjustments | R: low-readiness/restriction review; N: shortening and affected-muscle adjustment source flows | P: readiness LOW/NORMAL/STRONG, 45/30/20-minute options, safe recovery substitutions, injury report and authorization |
| workout-tools | R: load inspection and set review; N: rest, Focus, notes, load options, warmup/amplifier paths | P: graphical plate calculator, all rest/amplifier semantics, full supersets, substitutions, equipment-verified variants, every tool in Focus |
| program-builder | R: four-split 3–6-day composer, time/dose feasibility, local repair; N: program preview/save | P: goal/experience/gym-aware full program, credible time distribution, exercise/volume selection and enforceable hard constraints |
| custom-programs | R: pinned appearances and adaptive permission review; N: saved plan revision/editor paths | P: first-class create/clone/edit, stable slot settings, complete custom cycle and technique preservation |
| adaptive-cycles | R: longitudinal, recovery/phase and prescribed block review candidates | P: full hypertrophy, strength, peak, powerbuilding policy; proper frequency/volume; adaptive split/exercises toggles and explicit approvals |
| exercise-discovery | R: 20 provisional exact exercise IDs, source rep bands, equipment and recovery identity; N: read-only exercise catalog | P: complete reviewed library, variants/attachments, bodyweight/assisted forms, safe per-variant rep and load re-prescription |
| history-and-prs | R: immutable evidence inputs; N: history, corrections, Last and calendar source paths | P: deterministic Kotlin historical replay, correction-driven PR, Last and next-session refresh without duplicate records |
| volume-and-trends | R: sixteen-muscle direct/indirect working-set and weekly dose audits | P: correction-safe charts, consistency, volume, records, strength standards, favorites and meaningful progress |
| bodyweight | P: no Kotlin measurement-trend implementation; N: limited UI groundwork | P: dated bodyweight and measurement records, milestone calculation and accurate graphs |
| backup-restore | G: outside core rule engine; N: export, comparison, private merge groundwork | P: validated import, conflict/tombstone handling, explicit merge, Undo, migration and safe rollback |
| gyms-and-equipment | R: exact BigDecimal load feasibility and per-exercise gym checks; N: gym inventory editor | P: multiple gyms, actual stock/attachments, units, bar and Smith settings, per-exercise machine/cable increments and unavailable-equipment recovery |
| profile | P: no complete athlete/preference model; N: profile and theme tokens | P: training history/experience settings, 20 original themes, preserved preferences, accessibility, profile identity and goals |

## Mandatory BASIC engine implementation checklist

Each numbered requirement needs independently specified outcomes, property/regression tests, persistence evidence and the relevant Android UI flow. Unspecified policy is **not** silently given a guessed default.

### A. Profile, programming inputs and gym (P0)
- E01 **R (partial)** Versioned athlete setup input review now validates hypertrophy/strength/powerbuilding, experience, 3–6 weekday choices, all four splits (Full Body, Upper-Lower, PPL, ULPPL), timezone and revision. **P** Training-history calibration, durable profile ownership and full generator integration still required.
- E02 **R (partial)** Explicit 40–60, 60–90 and 120+ preferences are distinct from an approved hard time cap; contradictory cap fails closed. **P** Real plan-level validated time estimates for warmups, rest, unilateral sides and setup.
- E03 **R (partial)** Named gym setup confirms unit, stock, available fixtures and attachment IDs, with independent physical inventory validation and immutable snapshot. **P** Multi-gym management, UI editing and complete implement/plate/Smith options.
- E04 **P** Bodyweight enabled/disabled; assisted/weighted bodyweight distinct identities; minimum loads/increments per exercise, not one global 5 lb rule.
- E05 **R (partial)** Setup validator confirms all focus/reduced, exclusion IDs, triceps-on-lower, barbell/day limit, supersets and rest/hard/long/locked-day controls and detects conflicts. **P** Actual authoring, constraint-driven generator and Compose integration.
- E06 **R (partial)** Setup review gives actionable missing/conflicting fields, stable revision and defensively frozen candidate input. **P** Persist/save/edit/restore and explicit profile ownership still required.

### B. Exercise and real equipment intelligence (P0)
- E07 **R** Exact BigDecimal, plate pairing and physical stock inspection; **P** publish a complete verified loadability and override contract without 38.5/159.5 phantom loads.
- E08 **R** 20 provisional source-linked IDs, credited muscles, allowed focus, rep provenance and equipment fixtures; **P** complete independently reviewed attachment/variant/unilateral/machine catalog.
- E09 **P** Substitution engine: compatible stimulus and intent, facility, recovery, attachment and load basis; new prescriptions for replacement, no transferred old per-exercise weights, progression cells or PRs. The opt-in source-verified selector now filters gym-incompatible offers and preserves required/custom movements, but catalog breadth and independent exercise effects remain unresolved.
- E10 **P** Exercise setup hints and attachment imagery/choices; distinct rope/straight/V/dual rope and lateral/curl variants where actually supported by gym stock.

### C. Program and week composer (P0)
- E11 **R (partial)** Calendar-aware generation now creates split-day focus, coach-authored phase-purpose distribution and verified prioritized primary-lift placement for all four splits and 3–6 training days, including circular recovery. Actual exercise/sets composition, equipment verification, dose/time audit, barbell caps and direct-triceps/lower constraints remain separately checked. **P** Evidence-grounded goal-specific muscle dose presets, autonomous exercise selection, hard/long day coaching policy, multiweek recovery and real program issuance.
- E12 **R (partial)** Authored goal/experience/phase policy provenance now validates all 16 muscle dose and direct-day targets (including separate biceps/brachialis and wrist-flexion/extension channels) against the specific athlete profile and checks actual focus/reduced changes, without inventing universal MEV/MAV/MRV presets. **P** Independently validated athlete-specific dose targets and true adaptive calibration.
- E13 **R (partial)** Goal/phase policy now audits actual drafted-week dose, day purpose, session time and explicitly authored primary-lift day frequencies (including strength/peak named skill lifts), not merely matching labels. **P** Independently justified presets, generation of purposeful lift frequency and balanced movement patterns, rotation and longitudinal recovery.
- E14 **P** Realistic session-duration allocation for 40–60/60–90/120+; no junk/filler sets to satisfy minimum time, no lower-bound hugging with underdosed advanced training.
- E15 **P** Recover infeasible program constraints with transparent reason and alternatives; explicit user decisions before relaxing locks, rest, recovery, volume or equipment requirements.
- E16 **P** First-class custom programs: add/remove/reorder exercise and day, specify sets/reps/RIR/rest/loads/techniques, stable slot IDs, verified diff and Undo.

### D. Exercise prescription and execution (P0)
- E17 **R** Headless reviewed straight/backoff candidate, verified warmups/reps/rest/RIR; **P** executable correct loads for all working, warmup, technique, unilateral and alternate variants.
- E18 **P** Distinct DP, linear, strength, percentage/e1RM and conservative uncalibrated policies; no fictitious 1RM and no auto increase after one heavy top set.
- E19 **P** Fatigue-aware across-set loads and rep targets; fatigue/rest/near-failure evidence; optional actual RIR distinct from issued RIR, never persist an invented constant 2 RIR.
- E20 **P** Myo-reps, drop sets and related amplifiers: correct rests, working/extension credit, exercise suitability and reliable transfer across weeks and cycle phases.
- E21 **P** Superset ordering/timing, movement interaction, allowed toggle; safe shortening and longer training when appropriate.
- E22 **P** Immutable issued target set keys, movement identity, source/engine version, actual gym/load basis, warmup steps, first/last sets, and total expected session effort.
- E23 **P** Atomic and offline-safe issued workout -> set logging -> interruption/resume -> finish and exactly-once history receipt; source version pinned until completion.

### E. In-workout decisions, adaptability and coaching (P0)
- E24 **R** Separate actual complete/skipped/failed/missing evidence; preliminary exercise-level and next-set advice; **P** validated decision and action policy (not a suggestion-only demo).
- E25 **P** Readiness LOW/NORMAL/STRONG affects the actual session; detect context limitations; recovery workouts are never mislabeled as failed efforts.
- E26 **P** User-reported injury/limitation during workout, related-muscle reduction or substitute, non-diagnostic language, and clear preview of changed sets.
- E27 **P** Shorter-session actions 45/30/20 min with safe trim priority and explicit confirmation, preserving main lifts and minimum vital dose when physically feasible.
- E28 **P** Mid-workout authorized changes, equipment-verified +/- increments, valid plate calculator, exercise changes, rest/time and notes with no overwrites of user-entered facts.
- E29 **P** Coach message at day/exercise level only; concise plan explanation, evidence/confidence on request, avoid per-set banners, excessive jargon and unrequested weight-lowering prompts.

### F. Progression, cycles and longitudinal policy (P0)
- E30 **R** Independent DP evidence, two-session conservative review, optional-RIR confidence, longitudinal and deload candidate audits; **P** full automatic policy and versioned authorization.
- E31 **P** Actual completed set history, recovery, trend, plateau and fatigue accumulation inform rep-building, load increases, volume changes, deload and sustain decisions.
- E32 **P** Hypertrophy -> strength -> peak and powerbuilding cycle: appropriate phase rep/RIR/rest, frequency, movement specificity and per-muscle volume; prevent 30-to-11 unexplained custom collapse.
- E33 **P** Explicit Adapt split and Adapt exercises toggles; original custom amendments, favorite lifts, amplifiers, equipment settings and exercise identity survive unless reviewed and approved.
- E34 **P** Automatic, evidence-triggered deload review and recovery strategy; no unjustified all-set weight promotion, repeated unearned rep jumps or disappearing working sets.
- E35 **P** Proper week-boundary scheduling (Sunday/Monday), combined movement recovery, future plan previews and deterministic multi-week simulation.

### G. History, insights, data and release quality (P0/P1)
- E36 **P** Distinct issued/actual record, nullable RIR, zero-weight semantics, skip vs fail vs missing, manual correction and stable edit history; no mutations of old targets.
- E37 **P** History correction replays PR eligibility, Last, progression and all charts even for edits to older workouts; never delete or alter later work.
- E38 **P** Strength/volume/consistency and personal record charts, favorites, strength standards, thousand-pound milestone; bodyweight, measurements and safe dated trends.
- E39 **P** Backup/export/import/merge/Undo, conflicting revision/tombstone safety, tested cold restoration, legacy web migration, and idempotent journal recoveries.
- E40 **P** Profile preferences, 20 color themes, data-protection and accessibility settings, all exercised on phone and foldable.
- E41 **P** Explainable immutable rule version, independent acceptance corpus, user-approved engine switch and tested rollback; web parity is not proof of coaching quality.

## Required planned DOMAIN interfaces (not yet implemented as production APIs)

AthleteContext + ProfileRevision; GymInventorySnapshot + EquipmentProfile; ExerciseCatalogRevision; ProgramRequest + CustomProgramRevision; CoachWeekCandidate + FeasibilityIssues + CoachQualityScore; ImmutableIssuedSession with ordered sets; ObservedSetReceipt + CorrectionReceipt + CompletionReceipt; HistorySnapshot + ProgressionDecision; AdaptationProposal + Authorization; CycleState + NextBlockCandidate; MigrationEnvelope + IdempotentTransaction.

The headless engine computes decisions, native storage owns immutable fact receipts, and Compose displays/requests changes. A review-only result cannot issue a target or write history.

## Delivery order and explicit certification

| Stage | Required evidence |
| --- | --- |
| S0: scope | This ledger plus [native UI/UX impact specification](NATIVE_ENGINE_UX_IMPACT.md) references all 14 features and E01–E41 |
| S1: input and catalog | All profile, exercise, attachment, gym and load data can be verified; safe unavailable states |
| S2: genuine program | Full 3–6-day plans with correct goals, 4 splits, complete volume/frequency, time, recovery and custom settings; C01/C06/C07/C10 coach review |
| S3: working set rules | Exercise-specific physical loading and intra-workout targets; DP/RIR/fatigue/rest/warmups/superset/amplifier regression suite |
| S4: actual workout lifecycle | Versioned issue -> log -> resume -> finish -> correct -> next session across interruption and conflict |
| S5: multiweek coach | Cycles/deload/adaptations/custom continuity proven against C02–C13 and adversarial athlete histories |
| S6: native integration | All 14 feature outcomes, explicit permissions, complete offline/durable flows, readable errors and accessible control surfaces |
| S7: superiority | Actual device and paired-web tests, all themes, measurable better UX, independent high-quality coaching, no critical bugs and release approval |

**Not a completed feature checklist.** Unresolved requirements are not silently waived or postponed beyond release; an APK or passing headless CI cannot promote any row to product acceptance. Existing JS-issued workouts remain pinned and must finish with original targets during any eventual migration.
