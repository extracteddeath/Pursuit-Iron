# Pursuit Iron: evidence-led program and cycle decision contract (draft v1)

Status: **design and acceptance contract, not a released algorithm or evidence of superiority**. Applies to new native Kotlin program/cycle decisions after explicit review. Existing issued prescriptions, historical data, and saved legacy policy versions are immutable. Web parity or merge requires its own checks.

## Scientific baseline (reviewed 2026-11-11)

1. **2026 ACSM position stand**, Currier et al., *Medicine & Science in Sports & Exercise*, DOI [10.1249/MSS.0000000000003897](https://doi.org/10.1249/MSS.0000000000003897). Overview of 137 systematic reviews: strength commonly benefits from heavier loading (>=80% 1RM), full ROM, ~2–3 sets per exercise, early placement, >=2 sessions weekly; hypertrophy benefits from increasing volume and is often enhanced at >=10 weekly sets per muscle; power uses 30–70% 1RM with fast intended concentric and modest volume. **These are population-level associations, not universally required cutoffs or automated set/weight prescriptions.** The review did not find consistent superiority for failure or periodization in general outcomes.
2. **Pelland et al.**, *The Resistance Training Dose Response: Meta-Regressions Exploring the Effects of Weekly Volume and Frequency on Muscle Hypertrophy and Strength Gains*, [PubMed 41343037](https://pubmed.ncbi.nlm.nih.gov/41343037/). Increasing weekly set dose shows diminishing returns; additional frequency has a clearer association for strength than hypertrophy when relevant set credit is considered. Direct and indirect set contributions should not be treated as equivalent. Associations are not individual guarantees.
3. Individual movement skill, tested performance, preferences and adherence matter. No definitive evidence validates universal per-muscle MEV/MAV/MRV numbers, a mandatory hypertrophy→strength→peak sequence, universal 2x/week exercise frequency, or always-training-to-failure. Treat each as an **editable coaching heuristic** rather than scientific law.

## Decision precedence for the Kotlin generator

1. **Safety / truth**: authenticated athlete, immutable issued-gym identity, physically verified load increments, actual exercises, exclusions/injuries, load units/bases, physical machine IDs and consent. No inferred load transfer among barbell/dumbbell/physical machine A/B.
2. **User hard constraints**: days, time ceilings when truly hard, equipment, locked days, unavailable exercises, barbell cap and supersets disallowed. Do not fabricate work to pass a dose check.
3. **Goal-appropriate effective stimulus**: task-specific strength practice and intensity; adequate recoverable hypertrophy dose and muscle distribution; realistic power/peak exposure only when a peak/test date or performance objective exists.
4. **Recovery and adherence**: place recoverable stress; conserve suitable exercise continuity while adapting dose, schedule and exercise choices.
5. **Preferences/soft constraints**: favored split, focus, exercises and optional advanced techniques. If impossible, report explicit unresolved tradeoffs, not a misleading success.
6. **Bounded optimization** across valid candidates, with an explanation and observable objective for each chosen change. Never early-return a merely feasible candidate as if it were the scientifically optimal candidate.

## Goal-specific defaults: *starting priors*, not hard rules

| Goal / block | Primary emphasis | Weekly practice | Dose and loading | Do not infer |
| --- | --- | --- | --- | --- |
| Hypertrophy | Sufficient hard sets per muscle and movement coverage with recoverability | Frequency chosen to distribute productive sets without lowering quality; no arbitrary per-exercise 2x mandate | Start near evidence-informed ~10 direct-equivalent sets/muscle/week *when feasible*, individualize up/down from training and recovery; broad loading works when sets are sufficiently challenging | No fixed 10-set minimum for every muscle/athlete or automatic training to failure |
| Strength | Specific heavy practice for chosen strength objectives without dropping all muscle-building support | Prefer repeated practice of **named, feasible target lifts** if skill and recovery benefit; distinguish movement family and actual exercise | Include suitably heavy work when calibrated and safe; heavy exposure early, typically moderate set counts plus individually recoverable assistance | No universal frequency rule; 2 bench sessions are not a substitute for expressly chosen incline DB; OHP is not equivalent to incline |
| Peak / test preparation | Optimize performance on explicitly named test lifts, reduce interference and fatigue | Maintain technically relevant practice but choose exposure based on proximity to test and recovery | Shift emphasis to specificity; optionally taper *total stress* before the known test, while preserving enough intensity to retain readiness | A generic `PEAK` tag does not justify guessing a meet date, 1RM or universal taper length |
| Powerbuilding / “both” | Joint strength and hypertrophy objectives | Explicit primary-skill exposures plus meaningful accessories | Preserve enough hypertrophy support across strength and peak blocks, barring a deliberate test taper | “Strength” must not mean every other muscle receives near-zero work |

**Volume accounting**: Maintain separate direct and fractional indirect credits; account for biceps versus brachialis and wrist flexion versus extension independently. Expose both raw sets and credited sets. Do not count warmups, skipped sets, or non-equivalent amplifier followups as normal hard sets. Any physiological weight or fraction is versioned and explicitly documented.

**Intensity and effort**: Use exercise-specific calibrated working weights, rep ranges and a target RIR. Missing logged RIR may use target RIR **as a tagged assumption** for coaching, never as a fabricated observation. Avoid forcing an increase based on one easy set or forcing a drop from one difficult set; assess actual first-set attainability, subsequent intra-session fatigue, chronic workload and experience. Opt-out of coaching and fatigue adjustments must remain functional. Recommendations never overwrite user-entered targets or actual logs.

**Personalization**: Start conservatively when history is limited; update based on multi-session measured responses, completion quality, reported RIR, progression, soreness/readiness (when provided), and fatigue indicators. Do not infer hormonal state, pain etiology, injury risk or readiness from load changes alone. Use conservative uncertainty bounds and allow user overrides.

## Cycle generator contract

1. Accept goal (hypertrophy, strength, powerbuilding), experience, days available, split preference, hard session limits, equipment/gym, exercise preferences, movement goals, and **optional** target test date.
2. Decide whether multi-block periodization has a defensible purpose. If the user wants ongoing hypertrophy without a test, provide a continuous train-and-adjust plan; do not force a peaking block. If powerbuilding with a planned test, propose a goal-relevant accumulation, intensification and optional taper/test, with editable durations. Clearly distinguish **coaching convention** from demonstrated superiority.
3. Build multiple feasible candidate *whole cycles* rather than optimizing only week one. Evaluate every week and every block boundary for session feasibility, direct-equivalent muscle dose, named-lift frequency, recoverable spacing, schedule conflicts, equipment feasibility and specific accessory continuity.
4. Allow split/day-count and exercise adaptation **between blocks** only when enabled. Preserve named exercise identities/calibrations and their independently verified machine-specific histories. Clearly mark a change that needs recalibration.
5. Prefer minimal disruption when objective scores are comparable. Avoid OHP displacing a user-calibrated incline variant merely because they share a broad press category. Conversely do not require every user-calibrated movement in every week regardless of available time: show a tradeoff and seek an explicit change when hard constraints conflict.
6. Specify planned volume/load ranges and progression rules for each block. A deload or taper is a response to fatigue or an upcoming event, **not an automatic calendar reset with fabricated fatigue data**. Include weekly change safeguards and support sessions shortened to 45/30/20 minutes with documented cut order.
7. Evaluate and report candidate ranking: goal specificity, estimated productive dose, anticipated recoverability, adherence burden, personal history confidence, continuity, constraints, and uncertainty. No claim of "optimal" without evidence and competitive comparison.

## Mandatory acceptance tests before calling the engine better

- **Frequency / split matrix**: 3–6 days; Full Body, UL, ULPPL, PPL, and supported custom splits; hypertrophy, strength, both, and optional test peak. Compare practice on actual exercise IDs and day roles, not only broad movement-family labels.
- **Five-day ULPPL both**: with calibrated barbell bench, squat, incline DB and optional OHP, verify named bench/squat practice when feasible *and* at least one meaningful incline exposure if constraints permit. Test an infeasible variant that returns an honest unresolved outcome without inventing sets.
- **No forced two-exposure rule**: three-day full-body athlete with constrained time must not be rejected because two exposures cannot be supplied to every named lift. Less frequent options may legitimately win when stress or availability is limiting.
- **Complete cycle**: verify all weeks and transitions (not only the starter) and ensure muscle dose and support exercise choices remain rational after strength and peak transitions.
- **Gym A/B**: physical machine identities keep independent calibration, working loads, progression and PR contexts across switching, cold restart and multiple gyms. Stock must be proven on the selected machine before suggesting a load.
- **Volume**: direct/indirect credit, distinct small muscles, sets counted once, per-muscle recoverability and missing-volume explanation. Avoid claiming a magic MEV/MRV threshold.
- **Fatigue/progression**: no RIR tagged assumption, high/low readiness scenarios, strong and struggling lifters, mid-workout performance decline, day-specific independent history, configurable coaching-off behavior.
- **Comparative quality**: same athlete, gym and available catalog against frozen web baseline; score verified feasibility, strength-specific practice, muscle-dose completeness, full-cycle continuity, safe executable loads, suggestion accuracy against held-out logs, and time adherence. Publish failures and uncertainty. No cherry-picked “better” label.

## Current implementation boundary (2026-11-11)

- Native v6 first-week repeated-primary-lift rule is merged in PR #491; it is **not** evidence that a complete adaptive cycle performs well.
- PR #492 is an unmerged draft and its latest headless Kotlin CI failed 1 of 855 tests (`StarterProgramReleaseMatrixTest.calibratedInclineVariantRemainsInUlpplStrengthAndPeak`, assertion line 166). Compose contracts passed. Investigate the candidate's actual failure and constraints; **do not weaken tests or waive required dose merely to make a green check**.
- This document is a decision/QA contract, not code behavior, verified comparative performance, or release approval.

## Change control

Before integrating any new prior: identify the source and quality, mark empirical result vs coaching judgment, provide bounds and an explanation, add matched tests and version the policy. Preserve archived plans and logged facts. No APK/AAB until the full native experience and release gates have passed.
