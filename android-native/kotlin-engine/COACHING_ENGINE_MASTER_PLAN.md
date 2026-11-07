# Pursuit Iron — coaching engine and adaptive-cycle master plan

**Status: architectural implementation contract, NOT an implemented or certified adaptive engine.**
**Scope:** isolated Kotlin training engine, native data authority and the eventual fully native Jetpack Compose product. No edit to the production JavaScript engine or the other developer's Android branch is authorized by this plan.

## 0. North star and hard boundaries

The finished engine is a coach that produces useful, individual, executable programs **before the first workout**, adapts them from authenticated performance and user feedback, and preserves every deliberate athlete choice. A “safe rejection” is necessary but is not the finished product: when evidence is sufficient, the engine must offer a specific, feasible next action. Fitness outcomes are individual and uncertain; “best” means the best **defensible option among checked candidates for this athlete**, not a globally optimal or clinically guaranteed program.

A successful program always has two linked levels:

1. **Structural coaching:** goal and phase -> cycle -> block -> week -> training day -> ordered exercise slots -> sets, reps, effort, loading, rest and techniques.
2. **Feedback coaching:** issued set -> actual receipt and corrections -> comparable slot history -> fatigue/adherence/readiness -> next-set/session/week/block decision -> independently audited proposed change -> athlete approval -> new immutable issued workout.

Do not invent a second week composer, progression system, journal writer or custom-cycle store. Extend the existing source-backed Kotlin policies, CoachAthleteProgramGenerator, CoachWeekComposer, CoachSessionPrescriptionDomain, CustomTrainingCycle/Builder/Transition/CoachAudit, LongitudinalCoachDomain, and native owner-fenced approval/issuance/history. One shared constraints/audit service and one explicit recommendation/authorization protocol should join them.

**Release invariants:**
- Kotlin is the only eventual Android training decision authority. No hidden JavaScript/WebView/network fallback.
- Native storage is the only authority for profile, gym, approved program, issued workout, actual sets, corrections and history. Decision functions are pure and reproducible.
- Never mutate completed sets, pinned issued targets, old program revisions or custom history. Corrections are separate authenticated events.
- Unreported actual RIR stays **unreported in history**. Ordinary progression may explicitly assume that set's original goal RIR; reported effort always wins. With neither source available, effort remains unknown. Unconfirmed equipment, slot mappings and coaching parameters are never guessed.
- User restrictions, equipment, hard session limit, split/day locks, recovery minimums, prescribed volume ceilings and explicit permissions outrank any optimization score.
- A candidate is NOT an accepted program. A permitted *suggestion* is NOT permission to apply it silently.

## 1. Product modes and athlete controls

**Coached program:** the engine may recommend a new program and subsequent cycle adaptations within user-confirmed scope; a proposal still needs explicit review. Offer a useful initial recommendation without requiring the athlete to author sixteen muscle targets or every lift-frequency rule.

**Custom / hybrid program:** keep stable block IDs, weekday IDs, slot IDs, exercise variants, set types, warmups, weights, RIR, notes, supersets and amplifiers; locked/pinned components stay intact. The athlete can opt into coaching on only specified dimensions. Invalid untouched custom work is surfaced as a review issue, not silently replaced.

**Core inputs:** goal (hypertrophy, strength, powerbuilding), experience and recent history, realistic training-day availability 3–6, permitted day-count range, locked training/rest days, per-day session duration bucket (40–60, 60–90, 120+) and optional hard stop, gym(s), exact implements and attachments, bodyweight allowed, constraints/injuries, focus and reduced muscles, priority lifts, exercise preferences, supersets, barbell-per-day cap, triceps-on-lower policy, recovery, actual effort confidence and expected cycle event/horizon where known. User settings are durable and revisioned; no undetectable mutable defaults.

**Separate adaptation permissions, checked at proposal AND persistence:**

| Toggle / permission | Precisely what it authorizes to **propose** | What remains prohibited |
| --- | --- | --- |
| Adapt blocks | Reorder/extend/shorten/add training, recovery or peak/test blocks and change declared phase goals within the permitted cycle | Erasing completed block history; surprise phase changes |
| Adapt training split | Change a future block among Full Body, UL, PPL, ULPPL or an explicit reviewed custom split | Renaming an unchanged day without verifying actual work |
| Adapt training-day count / schedule | Choose a different number of future sessions **within athlete-confirmed available days/range**, place sessions on available weekdays and revalidate locks | Treating “adaptive” as authorization to demand six days from a three-day schedule |
| Adapt exercises | Change exercises, variation, order and verified attachments for future unlocked slots; re-prescribe loads and preserve comparable history only when identities actually match | Removing pinned movements, transferring incompatible exercise history or selecting unavailable equipment |
| Adapt dose / sets | Change weekly set allocations, per-day set counts, direct frequency and exercise-level volume within reviewed goal/phase bounds | Unjustified 30->11 collapse, undetected overshoot above reviewed MRV or lost technique sets |
| Adapt reps, effort, loads, rest and techniques | Independently change those future prescriptions when authorized, evidence-backed and auditable | Copying one day's bench weight to another, inventing RIR/increments or destroying custom amplifiers |

Adapt-split and adapt-exercises must have independent toggles (the original product requirement). **Adapt day count** must be independent of adapt-split; adapting a split can preserve four days, and changing frequency can preserve the split when feasible. Default-deny for imported custom programs. Distinguish preferences (soft), physical/personal constraints (hard), pinned content (immutable unless explicitly unlocked) and one-time change approvals. A toggle permits recommendations; a before/after diff and explicit acceptance authorize mutation.

## 2. One canonical data and decision contract

Use stable, opaque identities:

- **Athlete/Gym/Catalog/Rules:** owner ID, revisions, evidence provenance, exact stock and equipment basis. A gym switch invalidates unverified prescriptions, not logged facts.
- **Cycle/Block/Week:** cycle ID/revision, block ID and phase, block purpose (training/recovery/test), planned duration/horizon, week occurrence and approved revision.
- **Training day:** stable logical day identity across ordinary schedule edits, actual weekday, purpose and focus; moving a day should not silently relabel another slot as the same day.
- **Exercise occurrence:** stable occurrence/slot ID plus movement/variant ID, training-day ID, session purpose and progression policy ID. Upper-bench and Push-bench are separate progression cells even when both are hypertrophy. If a split changes, explicit mapping decides which cells carry forward, fork, restart or require review.
- **Set target:** stable set identity/type/ordinal, exact load/unit/basis, rep band, optional target RIR, rest/active time, warmup and amplifier; never use index alone as long-term identity.
- **Observed evidence:** immutable issued reference, actual load/reps/RIR (nullable), difficulty/failure, corrections, timestamps/ordering, source revisions, completion and user feedback.
- **Coaching decision:** source and history watermarks, constraints, alternatives checked, confidence/unknowns, change scope, input facts, rejected alternatives and reason codes, candidate score, complete program diff and authorization required.
- **Outcome:** declined / more evidence needed / reviewed proposal / explicitly accepted / durably committed / issued; never conflate them.

Use the existing typed Kotlin and native-store models first. **Do not replace their authority with new parallel DTO universes**. Add fields with versioned codec migrations, cold-reopen tests, and independent stable-ID mapping tests.

## 3. Program generation: high-quality from day one

**A. Evidence-backed initial coaching presets.** Provide curated, **versioned, source-reviewed** starting policy families by goal, experience and phase: weekly direct/indirect muscle dose bounds, direct frequency, main-lift practice, exercise roles/variants, sensible rep/RIR/rest envelopes, session density and recovery. These are *initial heuristics*, not scientifically universal MEV/MAV/MRV claims. Adapt to actual athlete capacity and report low confidence. Expose a useful recommendation before historical calibration; if a critical fact is missing, surface a focused confirmation rather than fail all program creation.

**B. Feasible candidate enumeration.** Filter by training days, locked days, duration, gym, bodyweight, contraindications, split constraints and explicit permissions. Generate multiple options for eligible split, frequency, focus, purpose and primary-lift placement. Use the existing bounded generator and composer; do not return the first locally feasible week when materially better feasible ones are checked. Declare search budget and truncation openly.

**C. Actual exercise and volume design.** Choose compatible compound patterns and accessories, priority lifts, direct vs secondary credits and independent muscle channels (biceps vs brachialis, wrist flexion vs extension). Cover targeted muscles without unnecessary filler, duplicate high-fatigue patterns, hidden underdosing or excessive exercises. Pin primary visits, respect available attachments and choose appropriate per-day movement counts and sets. Ensure full-week MEV/MAV/MRV and direct-frequency checks after warmups and set techniques are counted correctly. Individual overreaching flags matter more than “fill the entire time bucket.”

**D. Full prescription.** Assign purpose- and **slot-specific** sets, rep bands, RIR targets, work/rest, verified physical loads, warmups and supported techniques. Periodized strength, hypertrophy and peak prescriptions must materially differ where justified. Re-audit real time including setup/transitions, unilateral work, rest and amplifier follow-ups. Never count post-finish rest; never round to impossible implements.

**E. Hard constraints before preferences.** Hierarchy: identity, safety, user schedule and pinned content -> availability, phase intent, hard time/dose/recovery/frequency -> completeness and honest prescription -> coach-quality tradeoffs. Only compare candidates passing all hard constraints. Score soft dimensions separately: phase/goal fit, stimulus-to-fatigue, direct-dose closeness to individual targets, lift and muscle recovery, exercise stability/variety, session balance and user preference. Use lexicographic/Pareto ranking or a documented calibrated policy, not a magic weighted scalar able to hide severe defects.

**F. Offer 1–3 useful choices when genuinely distinct:** recommended, more recovery/shorter, or more practice/volume, with changed-day/split/sets/duration and why. Show a concise infeasibility explanation and the smallest user-approved relaxation when no good candidate exists. Do not call a result “optimal” when the search budget was reached.

**Minimum output for each day:** training goal/purpose and focus, exact chronological exercise slots, role and verified variant, number of warmup and working sets, reps, intended RIR, verified load when historical/stock evidence permits, rest and extensions, dose contributions, estimated time and confidence. No null/zero placeholders shown as real work.

## 4. Truly adaptive cycle/block generation

**Cycle blueprint** is a complete prospective sequence of blocks, not a label glued to a single repeated week. Each block specifies phase (hypertrophy / strength / peak), purpose (train / recovery / performance test), expected duration and transition conditions, candidate split, available-day count, sessions/day roles, exercise-slot identities, main-lift practice frequency, weekly dose and frequency targets, full rep/RIR/load progression policy and planned recovery.

**Supported goal paths:**
- Hypertrophy: sustainable hypertrophy-focused blocks, stimulus variety without excessive novelty, fatigue management, progression and periodic review; strength practice only where beneficial.
- Strength: lift-specific repeated practice and technical specificity, enough supporting muscle volume, recovery and a justifiable ramp or reduced-volume taper/peak.
- Powerbuilding: intentionally coordinated hypertrophy -> strength -> optional peak/test with a goal-consistent dose and lift-frequency shift; never drop to a token accessory program merely because the phase name changes.
- Custom mixed cycles: arbitrary athlete-authored blocks/days/slots/techniques remain first-class and can opt into selected adaptive dimensions.

**Candidate transitions:** at a block boundary, consider at least (1) keep current split and days, adapt dose/loads; (2) keep days, propose a goal-better split; (3) change day count within the athlete's authorized range; (4) change exercises and frequency where permitted. For each, build and score the complete **next block** and representative full weeks, not a split name. Preserve historical/comparable slots and pinned settings; explicitly map or retire slot identities. No replacement from incompatible equipment or unauthorized movement swaps.

**Periodized continuity:** independently audit every generated week, each adjacent normal-training week and every normal-training block boundary. Preserve relevant direct-muscle credits and actual sets; enforce athlete-reviewed maximum rise/drop and direct-frequency policy. Recovery and performance-test blocks have distinct declared purposes and separate dose/taper review—not an excuse to mark an unplanned 30->11 collapse as “normal.” Substantial training-dose changes require an explicit explanation and possibly athlete confirmation even if below a generic cap.

**Transition triggers:** planned block completion alone is a review opportunity, not mandatory automatic advancement. Also review sustained progress/plateau, unresolved recovery demand, missed sessions, changed weekly availability, equipment changes, injury/restriction, user request and priority-lift changes. Return **continue, adjust current block, recover/deload, change next block, or request information** with the reason and revision-fenced proposal.

**Permission gates:** CustomCycleTransitionDomain already identifies BLOCKS/PHASE/SPLIT/SCHEDULE/EXERCISES/SETS/LOADS/RIR/REST/AMPLIFIERS etc. Use those exact kinds. Add distinct authorization for day-count change as a specialized SCHEDULE comparison; do not treat adaptBlocks or adaptSplit as implicit permission to change available training days. Recheck every permission after the final candidate is fully drafted and immediately before durable acceptance.

## 5. Progression: one coherent coaching policy

The prescribed progression belongs to **exercise occurrence/slot and training purpose**. Supported modes need explicit eligibility, updates, goals and regression tests:

| Mode | Trigger and suggested behavior | Never do |
| --- | --- | --- |
| Double progression | Improve reps within the approved band at stable verified loading; earn the smallest suitable load increase only when recent comparable set performance and RIR justify it; rebuild feasible rep targets | Promote an unearned 215 lb across all sets from 200/205/210/215 for 3/3/2/2 |
| Per-set rep progression | Change only earned working-set targets, using each set's observed reps/RIR and an intra-session fatigue curve; no blanket +3 rep jump; preserve source rep envelope and future continuity | Rewrite other sets or treat a new issued target as a historical actual |
| Dynamic load / autoregulation | Resolve the slot's actual verified implement and next load based on recent all-set ability, objective, rep reserve and recovery; allow an actual attainable repeat, increase or decrease | Assume a gym-wide increment, copy Upper bench to Push bench or treat unknown RIR as 0 |
| Strength/intensity progression | Manage repeated lift exposure, loading, practice and rep envelopes, with fatigue/skill balance and phase intent; specialize near a verified test/peak | Copy hypertrophy progression blindly into a strength day |
| Volume/frequency progression | Adjust sets and direct sessions inside individual MEV/MAV/MRV, approved recovery, schedule and cycle continuity; consider more frequent quality work before indiscriminate session stuffing | Drop 30->11 normal working sets without evidence and explicit review |
| Stalled/overloaded progression | Analyze comparable unsuccessful attempts and fatigue, propose repeat, verified lower load, lighter backoff, reduced volume or deload as independently justified | Retry a near-failure high load indefinitely or hide the latest failed workout |
| Recovery/deload | Respond to sustained strain, low readiness, recent restriction and planned block context; alter the next actual prescription and review recovery outcome | Merely display “deload recommended” while issuing unchanged workload |
| Technique/method progression | Handle warmups, drops, myo, rest-pause, cluster and supersets as typed workloads with appropriate rest, inventory and set counts | Drop amplifiers when transitioning cycles or count their extensions as arbitrary normal sets |

Always distinguish *conservative no increase*, *specific repeat*, *specific lower load*, *recovery substitution*, *insufficient evidence* and *unresolvable constraint*. When actual RIR is unreported, ordinary progression assumes the original issued goal RIR and identifies that assumption. It may earn the normal rep/load step when all other requirements pass. Neither source available means unknown effort, not an invented default. Next set/session/day/block decisions must use the SAME native authenticated receipts and revision watermarks.

**RIR fallback decision — 2026-11-07:** reported RIR, including zero, overrides the goal. A blank report can use only the original immutable goal for that same set, with `ASSUMED_GOAL` provenance and a clear review explanation. Never write the assumed value into actuals, count it as reported effort or infer fatigue/near-failure from the assumption alone. Missed reps, failed/skipped sets, recovery restrictions, physical loading checks and assistance switches retain precedence. The earlier benchmark's claimed advantage from withholding all progression on missing RIR is withdrawn.

## 6. Adaptive feedback loop and precedence

1. Native owner reconstructs **complete** contiguous journal history, corrections and current approved program/gym/profile/rules versions.
2. Classify the athlete's training status by exercise slot, region/muscle and week: adherence, earned progress, uncertain effort, missed rep floors, signs of overload, repeated plateau, explicit readiness/restriction.
3. Determine decision scope **before** recommending changes: next unstarted set; remainder of session; next session of a specific slot; future program week; next block/cycle. A set-level correction cannot silently restructure the cycle.
4. Generate feasible alternatives from existing canonical rule engines and policies, with concise reasons and exactly which evidence supports them. Use physical stock checks and target-vs-actual distinction.
5. Re-run the **entire prospective week and block audit** for structural changes, including whole-cycle volume continuity and existing custom slot semantics. If no option passes, show the blocking facts and smallest authorized change.
6. Present exact before/after (days, split, exercise slots, sets, load, reps, RIR, time, muscle credits); alternatives with tradeoffs and status (proven, provisional, needs input). User explicitly accepts scope.
7. Revalidate latest revisions under one owner fence, persist candidate/approved revision atomically, issue one immutable workout, and acknowledge only after durability.
8. After logging and corrections, update histories and subsequent decisions. Never retroactively mutate previous issued targets.

**Precedence:** immediate safety/restriction > authenticated observed evidence > source feasibility/hard constraints > explicit athlete permissions > phase-specific policy > soft preference/quality. A fatigue-driven change does not mean “more sets” elsewhere to compensate; total exposure and recovery must be recalculated.

## 7. Measurement and quality gates

Every recommendation should have a machine-inspectable scorecard:

- **Feasibility:** constraint and exact equipment passes; missing inputs; source revision checks; no unsafe issuance.
- **Program quality:** training days and roles, primary-lift frequency, whole-week/16-muscle dose, dose range, pattern diversity, per-day exercise/working-set counts, actual time distribution, circular recovery, hard/long-day intent, load/RIR validity.
- **Cycle quality:** every block goal matches the prescription; stable/custom slots; changes in split/day count are authorized; explicit dose-rise/drop and direct-muscle continuity; recovery/test purpose legitimacy; next-block transition confidence.
- **Progression quality:** comparable performance, actual vs target, correct slot, earned rep/load step, restraint after near failure, valid next-session targets and physical weight increments, revision continuity.
- **Athlete UX:** understandable proposal, exact diff and Undo, option to keep plan, no hidden auto-change, preview == issued == logged targets, successful cold restore.
- **Search limitations:** candidate counts, budget/time, policy provenance, why rejected, why recommended. A budget-limited search is NOT a global optimality proof.

**Objective acceptance suite, to implement and keep green:**
- Matrix across Full Body/UL/PPL/ULPPL; 3/4/5/6 allowed days where applicable; hypertrophy/strength/powerbuilding; hypertrophy/strength/peak; beginner/intermediate/advanced; varied gyms, bodyweight policy and 40–60/60–90/120+ sessions. Mark inapplicable combinations explicitly, never count them as passes.
- Multiweek deterministic simulations: new starter, steady progress, strong recovery, uneven set fatigue, plateau, repeated missed floors, missed week, injury, gym switch, schedule reduction/expansion, deload, strength transition and taper/peak. Each must demonstrate actual *prescription changes* and no retroactive history mutations.
- Paired same-exercise slots: Upper bench vs Push bench; same purpose with different slot IDs; same exercise on changed week weekdays; same exercise with different implements; planned block split change with mapping vs forked history.
- Adaptive permissions truth table: all toggles on/off separately, conflicting locks, no unauthorized split/day/exercise changes, preserved pinned custom techniques, explicit diff and CAS acceptance.
- Cycle benchmark: 5-day hypertrophy ULPPL -> optional 4-day strength UL -> 3–4-day peak only when permitted and prospectively audited; also test **unchanged split wins**, schedule not available, unavailable bench and dose-collapse rejection.
- **Adversarial known defects:** all L01–L26 in KNOWN_LEGACY_BUG_REGRESSIONS.md, especially 200/205/210/215, missing RIR, 38.5/159.5 phantom increment, 30->11 drop and lost amplifiers.
- Store durability and device: simulate corrections, retries, process death, offline restart, history growth, backup/restore and source conflicts; physical Android onboarding -> complete workout -> next issued session -> adaptive block proposal -> acceptance -> next block.
- Independent benchmark: execute actual pinned JavaScript vs Kotlin scenarios in verification/engine-superiority-cases.json, retain reproducible outputs in the empty evidence file only after paired execution, blind coach-quality review for complete programs/cycles, no regressions where JS passes, and explicit athlete acceptance.

**Hard release gates:** no failing critical scenario; no fabricated stock/load/effort; no unauthorized custom edit; no hidden JS authority; full 14-feature and E01–E41 ledgers satisfied; full real-device flow; all required measured comparison pairs and independent evaluation; user-approved cutover. Kotlin CI success alone is not release approval.

## 8. Implementation sequence: dependency-driven, not endless feature patches

Each milestone has a concrete artifact, targeted automated tests and a release-gate disposition. Work in the existing isolated branch, one authoritative domain per responsibility. Do not move to broad UI styling while coaching is still a placeholder.

| Gate | Build in existing architecture | Exit condition / acceptance |
| --- | --- | --- |
| **G0 — Freeze baseline and contract** | This plan; link to feature, legacy-bug, native-UX and superiority ledgers; pin source revisions; define athlete and phase policy provenance | One reviewed end-to-end model, stable IDs and no competing decision paths |
| **G1 — High-quality initial programs** | Curated goal/experience/phase dose and rep policies; extend existing generator, selector, composer and session prescriptions; candidate scoring and complete-week audit | Good complete 3–6-day first programs, usable without manual authoring of every dose; split/goal/availability matrix and independent coach review |
| **G2 — Adaptive cycle candidate orchestrator** | Compare future block layouts and candidate split/day counts/slot assignments/periodization; call existing custom transition diff and continuity audit | At least two meaningful complete block alternatives; one recommended with tradeoffs; independent adapt-block/split/days/exercises toggles; no unauthorized changes; no 30->11 normal block collapse |
| **G3 — Unified progress/recovery** | Native owner feeds same complete authenticated slot history into existing progression, fatigue, longitudinal and recovery policies; implement repeat/reduce/earned increase and deload updates | Multiweek changes occur in actual next issued sessions; same-day/same-purpose/different-slot isolation; corrections and low readiness change outcomes correctly |
| **G4 — Integrated cycle persistence and custom parity** | Native revision-fenced approval, exact structured diffs, mapping/fork of slot history, techniques and Undo; recovery after process death | Custom authored data and every amplifier preserved, day count and split changes survive cold reopen, old issued sessions immutable |
| **G5 — Real native coach UI and feature closure** | Compose cycle option chooser, Plan, Preview, Today, Train and History/Last with all required tools, accessible interactions and original themes | Onboarding -> program -> logged workout -> block adjustment -> next workout works fully offline on phone/foldable; all 14 capabilities and E01–E41 |
| **G6 — Demonstrate superiority and cut over** | Run paired JS/Kotlin evidence cases; blinded cycle/program review; scale owner journal, migration/backup, cold APK checks; explicit release decision | Independently measured improvement in at least three domains, zero critical regressions, complete replacement contract satisfied, deliberate Kotlin-only cutover |

**Immediate next engineering passes (specific order):**
1. Define a typed, permission-aware **cycle candidate request/result** using the existing custom-cycle model and native history facts. Add independently testable candidate types and explicit rejection reasons, not a second composer.
2. Add an adaptive **block-option evaluator** that delegates complete-week candidates to CoachAthleteProgramGenerator + CoachSessionPrescriptionDomain and compares transition continuity via CustomCycleCoachAuditDomain; use existing CustomCycleTransitionDomain for diff/permissions. Validate permitted split and day-count changes and a no-change baseline.
3. Build executable fixtures for powerbuilding multi-block and a custom/pinned cycle, with controlled gym and weekdays. Require specific counts, reps, effort, dose/frequency and justified recovery. Add earned progression, plateau, deload and unexpected availability-change variants.
4. Only after block candidates are proven, implement native ownership of proposed -> accepted -> persisted cycle revisions and immutable slot remapping, then connect it to actual issued workouts.
5. Expand the goal/phase/experience policy source library and complete the matrix; never inflate confidence from toy fixtures.

## 9. Current audited state — 2026-11-07

**G3/G4/G5 accepted calibration — 2026-11-07:** ordinary signed custom cycles
now have a durable one-workout starting-load approval through native
`NativeKotlinBlockCalibrationConsentStore`. Every new acceptance, preparation
and issuance recomputes complete corrected history and audits a virtual copy of
the whole cycle. Exact owner/source/consent/settings/permissions and per-set diff
are bound to the receipt; authored week exceptions and outstanding workouts are
protected. The original cycle stays unchanged. Candidate Compose supports
before/after review, separate acceptance/issuance confirmations, cold discovery,
opening the actual native logger, and confirmed authored reset with coaching
off. Re-enabling coaching cannot revive a reset calibration; issued targets
remain immutable. Native tests demonstrate authored 25 -> accepted/issued 20 lb
through actual logging and Finish, preserving null actual RIR. Local native
persistence tests: 405 passed. Existing pure engine rules are unchanged.

This closes the first-workout acceptance/issuance gap below, not the entire
continuity milestone. Next: subsequent same-slot workouts must review the new
issued baseline without an unearned jump back to the old authored weight.
Multiweek carry, activated/source/split transitions, paired transition-quality
measurement, backup/power-loss and physical-device verification remain open.

**G3 changed-recipe review — 2026-11-07:** `CustomBlockTransitionLoadDomain`
can build a conservative first-week phase/recipe calibration from the last
complete same-day exposure under an unchanged source and split. Weights remain
per-set and cannot exceed historical or authored loads; starting reps/reserve
must fit old observed reps plus reported or assumed ORIGINAL goal RIR. It shares
the existing factual exposure verifier, refuses new fatigue contexts, respects
assistance controls and preserves the immutable signed workout. Native readers
and the shared coaching UI expose this as a distinct read-only projection.
Pure tests cover original-vs-new goal effort, physical loading, rest, set order,
recovery, failed latest work and immutability; native fixtures exercise actual
strength-policy/gym audit, nine persisted completions, cold review and saved
near-failure correction. The ordinary-cycle changed-load acceptance, full
projected-cycle audit and actual issuance are now connected above; schedule/source
transitions and paired/device proof remain unfinished. This is no cutover or
superiority certificate.

**G3/G5 block-entry update — 2026-11-07:** the isolated Future Week coach
now displays actual native previous-block receipts and a conservative
`CustomBlockStartingLoadDomain` review. Exact same-context recipes can repeat
their per-set authored loads; phase/purpose changes, failed work, source changes,
near failure and recovery restrictions do not produce a carried load. Goal-RIR
assumptions remain calculation-only. The review is read-only and requires no
additional consent to retain already-authored weights. Ordinary signed authored
cycles now enter the same native coach through the existing consent-bound
preflight, without needing an activated schedule. Real native-file tests prove
same-source block-entry repeats, missing-RIR provenance, latest saved correction
invalidation, revoked consent, cold reads and the coaching off switch.
Source-changing schedule activation remains a distinct refusal. Different
same-source starting loads use the one-workout approval/issuance above;
new-context transition policies, paired block-transition results and
physical-device acceptance remain open.

**G3 continuity update — 2026-11-07:** the existing
`KotlinAdaptiveSetPrescriptionDomain` now materializes an explicit repeat of
authenticated personalized targets when complete, physically comparable work
does not earn another rep. It preserves the same day and purpose via the
existing native history owner, keeps goal-RIR assumptions separate from actual
effort, and retains all recovery/failed-work/source checks. Real native-file
tests cover another same-purpose day, explicit repeat acceptance, fresh disk
readers and exact next-session issuance/replay. The Coach preview identifies
a repeat without claiming earned progress. Canonical owner, journal format
and approval protocol are unchanged. This advances L26; paired continuity
and physical-device evidence remain unfilled release requirements.

**Present:** independently tested, review-only Kotlin week generation for 4 splits, 3–6 days; bounded feasible-candidate ranking and priority-lift recovery; authored phase/dose policy checks; full-session rep/RIR and equipment draft; purpose- and day-slot-specific progression paths with native owner-backed test issuance; custom-cycle authoring with stable slots/amplifiers; permissioned structural diffs; custom full-cycle dose continuity and longitudinal candidate review. Source: existing domain implementations and isolated green CI at commit ff7f0dc5c967d831c474700c4de167170e6475cd.

**Not yet implemented as a connected end-to-end adaptive cycle coach:** automatic research-grounded phase/dose presets for every goal/experience; complete block option enumeration and comparison; truly adaptive split and day-count selection; future-block exercise mapping and slot migrations; full fatigue/deload-driven next-workout issuance; finished custom/program UX; production Kotlin-only Android authority and real-device superiority certification.

**Existing required ledgers:** FULL_REPLACEMENT_CONTRACT.md (release/cutover); ENGINE_BASELINE_COVERAGE.md (14 features, E01–E41); NATIVE_ENGINE_UX_IMPACT.md (screens); KNOWN_LEGACY_BUG_REGRESSIONS.md (L01–L26); ENGINE_SUPERIORITY_ACCEPTANCE.md and verification/engine-superiority-*.json (paired objective proof); NEXT_LOAD_AND_CYCLE_CONTINUITY_GATE.md (existing load/cycle continuity integration).

**Working rule:** any new feature must name its gate, canonical owner, exact invariant, realistic adversarial test, storage/UX impact and regression ledger entry. If it cannot, do not add another domain class just to report “more work done.” Update this document's current-state section only when implementation and its end-to-end evidence actually change.
