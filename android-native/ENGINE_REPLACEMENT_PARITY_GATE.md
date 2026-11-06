# Pursuit Iron native replacement: parity → superiority gate

**Purpose.** The end state is a fully native, athlete-safe Kotlin/Jetpack Compose
app that **replaces the JavaScript engine**. A successful Kotlin unit-test suite
alone is not parity; a non-issuable candidate preview alone is not a working
program. This document is the acceptance contract for future engineering passes.

## Release criteria (all mandatory)

1. **Feature parity:** onboarding → gym inventory → program/cycle selection
   → editable generated/custom calendar → day preview → workout issuance
   → real set logging/editing, rest and amplifier execution → completion
   → immutable history and corrections → next-session progression
   → coach adaptation/deload/phase transition, all without JavaScript.
2. **Better coach decisions:** measurable comparisons against old failure
   fixtures for every progression path, including double progression,
   individualized intra-session fatigue, readiness, deload, recovery, phase,
   gym increments, per-day/purpose load isolation and volume constraints.
3. **Safety and persistence:** no fabricated equipment/load, no silent
   removal of an athlete's sets, no cross-owner facts, no history mutation,
   durable replay after process death, explicit review of changed coaching,
   stable session/slot/set IDs, correct per-block policy provenance.
4. **Native experience:** cohesive Material 3 Expressive Compose UX,
   theme parity, correct phone/foldable layouts, timers, note controls,
   focus mode, accessibility and fast, predictable navigation.
5. **Real acceptance proof:** installable APK through the full scenario,
   repeat on cold restart, and controlled comparison with the same scenarios
   in the existing web engine. **No cutover from a passing compile alone.**

## Evidence states

- **Contract tested**: a Kotlin domain unit test covers a specific rule.
  It does **not** prove the Android workflow is connected.
- **Integrated tested**: an owner-scoped native store + host flow passes an
  isolated integration or persistence test. It does **not** prove user UX.
- **App E2E passed**: device/emulator scenario verifies what an athlete
  can actually do. None of the areas below is claimed here yet.
- **Unverified**: no current passing evidence for required parity.
  A file, method, preview, old JS feature or UI mockup is insufficient.

## Current verified evidence / remaining release blockers

| Capability | Strongest current evidence | Next required proof |
|---|---|---|
| Native onboarding, stock, restrictions, session duration | Contract tested: `CoachAthleteSetupDomainTest`, `EquipmentLoadDomainTest` | Persist and edit setup in Compose; demonstrate gym changes propagating safely |
| Split/day-frequency/cycle authoring | Contract tested: `CustomCycleBuilderDomainTest`, `CustomTrainingCycleDomainTest`, `GeneratedAdaptiveBlockSafetyTest` | UI creation/editing of 3–6-day generated and unrestricted custom cycles |
| Volume MEV/MAV/MRV, frequency, phase continuity | Contract tested: `CustomCycleCoachAuditDomainTest`, `CustomCycleTransitionDomainTest` | Test actual generated cycles for hypertrophy, strength, peak, recovery with realistic equipment and time |
| Physically achievable, equipment-aware load | Contract tested: `EquipmentLoadDomainTest` | Full program → set picker/steppers → logged loads, including custom machines and increments |
| Double progression and individualized reps | Contract tested: `ProgressionEvidenceDomainTest`, `KotlinAdaptiveSetPrescriptionDomainTest` | Complete exercise-scoped native history → per-set next target → issued next workout → completed feedback |
| Independent load between program days | Contract implemented for exercise/purpose/day through `CoachNextProgramLoadReviewDomain` | Verified two-day bench scenario: progressing Upper must not alter Push |
| Custom sets, RIR, rest, amplifiers, override preservation | Contract tested in custom authoring/transition/coach audit suite | Real Android preview, workout logger, rest timer, amplification and next-block retention |
| Workout preparation, issuance, logging, completion | Integrated native journal/history tests; journal and store keep original prescriptions | Single real onboarding-to-completed-workout APK flow and cold restore |
| Future adaptive block schedule and athlete consent | Integrated draft + future context + two consent checkpoints; activation review uses verified history and outstanding inventories | **Durable, re-audited source/policy activation transaction, with exact target future week and history-safe recovery** |
| History/progression corrections/recovery | Integrated journal, history and offline recovery test suite | Android edit-history UI, corrected next targets, zero phantom progression |
| Readiness, illness/injury, shorter sessions, recovery | Partial domain contracts; not asserted as native E2E | Functional workout adjustment and native injury/time-shortening UI |
| M3E workout, plan, progress and profile UX | Candidate Compose screens/host exist; not certified E2E | Complete usable navigation/controls, accessibility/animation review and device testing |
| Migration and parity with live JS app | No complete equivalence result | Repeat deterministic old defect fixtures on both engines; produce pass/fail evidence scorecard |

## No-regression fixtures (must remain green)

- **200×3, 205×3, 210×2, 215×2, target 5–8:** never
  prescribe 215 for all four next sets. A 215×2 near-failure repeat is
  not an increase signal. Evidence unit tests already assert these facts.
- **Unattainable loads (e.g. 38.5 lb with 5-lb stock steps):**
  no invented weight; exact gym attainability governs suggestions.
- **Upper-day bench vs Push-day bench:** separate purpose/day working
  loads and history. A change to one may never silently rewrite the other.
- **Custom hypertrophy → strength:** preserve every authored change,
  stable IDs, amplifiers and volume intent; any reduction must be
  explicitly audited against athlete/phase volume and continuity policy.
- **RIR:** actual RIR is unknown unless logged; no target-RIR invention.
  Near-failure performance must not trigger a heavier all-set target.
- **Prospective block:** first/active block cannot be recast as a future
  schedule. Only a later block with a separately audited future profile
  may be saved, approved and eventually activated.
- **Cold restart/account switch:** no release of unconfirmed training,
  no stale consent, no cross-athlete journal or mutable history.

## Implementation order: avoid safety-gate-only development

1. Finish the **source + per-block policy activation transaction** and
   replay/rollback recovery, including the issued/grant boundary. Then
   verify athlete-approved future change on real, completed first-block history.
2. Wire full native **onboarding → train → log → complete → history →
   individualized progression**; build a test APK only when this vertical
   path is actually usable. Complete repeat/correction flows in this step.
3. Audit **program quality** from shared realistic test profiles across
   3–6 days, hypertrophy/strength/powerbuilding, available gym stock,
   frequency, time buckets, readiness and recovery. Fix the prescription
   algorithm, not just adapters and previews.
4. Finish **feature-parity UX**: custom editing, phase controls, notes,
   amplifiers, set controls, rest/focus, progress, profile, injury/short
   sessions and M3 Expressive theme/motion/accessibility.
5. Execute end-to-end and regression matrix; compare with JS on the
   same seed, constraints, logs and expected outcomes. Publish an objective
   **better/same/worse/unverified** scorecard. No engine cutover if any
   release-critical journey is unverified or worse.

**Branch policy:** only `architecture/kotlin-engine-rebuild`. Do not
merge into the active web app, other native branches or main without an
explicit, independently verified cutover decision. This scorecard is a
living gate; amend statuses only with named executable evidence.
