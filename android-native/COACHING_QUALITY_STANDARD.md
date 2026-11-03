# Pursuit Iron — coach-quality training system

## North star

A workout or training block is not successful merely because it is internally
consistent, mathematically valid, or technically compatible with the old web
app. **It must make sense as a complete plan an excellent experienced coach
would genuinely write for this athlete and this point in their training.**

The product should outperform the old engine through better individual
programming decisions, meaningful feedback, consistency across weeks, fewer
needless taps, and safer data ownership. This is an acceptance criterion,
**not** a claim that the current Kotlin laboratory or M5 APK already
achieves it. Human coaching judgments are not perfectly objective, so no
single formula or fabricated research-derived universal constant can certify
quality. Use transparent assumptions, independent scenarios, multiple
training contexts, and adversarial review.

## Required layers of an actual coaching decision

1. **Athlete context:** training history, ability and goal, split and
   scheduling, equipment and verified load increments, actual session time,
   movement restrictions, preferred exercises, current phase, fatigue and
   recovery observations. Missing context stays visibly unknown.
2. **Block design:** a stated goal for hypertrophy, strength or powerbuilding;
   coherent progression of training stress and specificity through base,
   strength and peak; frequency and movement selection; deliberate deloads
   and recovery. Do not blindly cut 30 sets to 11 across phases or copy a
   percentage based on an unverified maximum.
3. **Weekly microcycle:** every day has a purpose, nonempty realistic
   session, appropriate intensity and stimulus, manageable overlap and
   recovery, and volume/frequency that satisfy user-specific constraints
   or transparently explain a tradeoff. A 75-minute advanced workout
   should not be arbitrarily underfilled with four movements.
4. **Exercise prescription:** correct implements and stable movement
   variants; actual working sets vs warm-ups/extensions; sensible target
   reps, loading, RIR, rest, attachments and time. Consider performance
   variation and fatigue across sets instead of prescribing identical
   load/reps irrespective of the recorded pattern.
5. **Workout execution:** save exactly what occurred. User-entered load
   and reps must not be overwritten. Focus and rest features must stay
   reachable; skipped/failed sets, shortened sessions, restrictions and
   interruptions remain distinct from genuine effort failure.
6. **Adaptive feedback:** interpret the whole exercise and recent week,
   not just the last heaviest set. Distinguish under-target reps, a good
   but hard session, progressing at constant load, repeated rep ceilings,
   uncertain RIR, changed equipment, an injury-related restriction,
   persistent fatigue and a true plateau. A good coach can *maintain*,
   *build reps*, *review an increase*, *substitute*, *adjust volume* or
   *deload*, and can say when evidence is insufficient.
7. **Explanation:** one concise statement of *what changed and why* in
   program review or the game plan; deeper reasoning available on demand.
   Avoid per-set coaching banners or engine jargon.
8. **Longitudinal integrity:** a block progresses toward the user's goal,
   respecting custom authorship, cycle choices and user overrides. No
   isolated workout optimization at the expense of the rest of the week.

## Design and engineering boundaries

- A **headless Kotlin domain engine** computes versioned, deterministic
  decisions with stable exercise/slot IDs; persistence and Compose do not
  generate their own training prescriptions.
- **Feasibility is a gate, not a coaching verdict:** a plan can meet MEV/MAV/MRV
  and still be poorly organized, excessively monotonous, too short, too hard
  to recover from, or misaligned with the block goal. A plan can exceed a
  preferred time range without violating a real hard user limit.
- Equipment availability is physical and exercise-specific: verified
  dumbbells-per-hand, bar/Smith totals, exact plate pairs, machine/cable
  stacks, units and user's increments. Never suggest impossible loads.
- Training dose must distinguish direct/indirect muscle credit and
  biceps/brachialis, wrist flexion/extension, as well as working sets,
  warm-ups and amplifiers. Coefficients and phase targets need independent
  domain validation, not invented physiological guarantees.
- **Optional RIR means optional:** do not infer missing actual RIR from an
  issued target or freeze all progress. Performance-only signals can still
  justify a *human-reviewed* next step, with lower confidence and questions
  for context. Near-failure recorded RIR must be respected.
- A suggestion does not authorize a write. Explicitly distinguish
  `observed fact`, `review option`, `issued prescription`,
  `user-authorized adjustment`, and `durable completion`.
- Old web outputs are useful for import compatibility and past failure
  examples, not an authoritative correctness oracle.
- **No active engine swap** until independent scenario tests, real-program
  generation, versioned migration, correct journal integration, real-device
  acceptance and safe rollback are established.

## Mandatory independent coaching scenarios

| ID | Context and signal | Coach-quality outcome to prove |
| --- | --- | --- |
| C01 | Advanced lifter, four days, 60–90 min, available equipment | Coherent complete week, sensible weekly volume and frequency, sessions reasonably fill preferred duration rather than consistently hugging lower bound |
| C02 | Four sets 200×3 / 205×3 / 210×2 / 215×2 against 5–8 target | Treat entire result as below target; never auto-promote all future sets to 215 |
| C03 | Repeated 215×2 near failure against 5–8 | Review load and recovery, do not mistake effort for a double-progression success |
| C04 | Two comparable 8/8/8 sessions, actual RIR missing | Do not invent RIR; permit a low-confidence, equipment-valid *review option*, not an automatic rewrite |
| C05 | Dumbbells 25, 30, 35; or bar with exact plate pairs | No 38.5/159.5 phantom increment, no erroneous per-hand vs total interpretation |
| C06 | Custom hypertrophy 30 sets → strength 11 sets | Explain phase-specific changes against separate dose/frequency requirements; flag unexplained underdosing and preserve authored program settings |
| C07 | Bench priority in strength phase | Evaluate appropriate frequency and lift specificity; authorize split/exercise adaptation only with user consent |
| C08 | Low readiness, illness/restriction, shorter session | Propose safe day-level changes without treating recovery as training failure or modifying recorded facts |
| C09 | No RIR, mixed loads or a skipped set | Interpret the actual observation; never fabricate a single repeatable working load or a completed set |
| C10 | Three-to-six-day split with bodyweight, no barbell, or limited gym | Feasible equipment, nonempty days, realistic duration, balanced recovery and explicit unresolved compromises |
| C11 | Repeated plateau or accumulating poor reps | Review volume, rest, session distribution, technique context and recovery; do not endlessly increase or decrease load based on one set |
| C12 | Custom-cycle settings, supersets, myo/drop, exercise variants | Preserve authorship and valid rest/volume behavior through block transitions and exercise replacements |
| C13 | Actual workout corrected after Finish or resumed after process death | New coaching uses corrected facts, old issued targets remain immutable, no duplicate PRs or lost sets |
| C14 | Material 3 Expressive training journey in all original themes | Compare taps, screen response, legibility, one-page set logging, Focus, interruptions and explanation burden with the web app |

Each scenario needs explicit inputs, a defensible human-coach-style
expected range of outcomes, rejection criteria, reproducible execution
and observed native results. Avoid hard-coding one arbitrary answer
for a case that has several acceptable coaching choices. A result may
be **unresolved**; it must not be called certified simply because a
test asserts a convenient answer.

## Training-engine build order

1. Independent facts: loading, honest effort and fatigue observations,
   volume/frequency/time feasibility, immutable user history.
2. Contextual coaching review: optional-RIR confidence, repeated
   performance, readiness/restriction handling and non-destructive
   review options. This has begun in the isolated Kotlin branch.
3. Exercise and movement-pattern catalog with validated equipment,
   per-muscle effects, variant/attachment identity and transparent
   substitution rules.
4. Real *plan composer*: build candidate weeks for all four splits,
   validate constraints, rank by coherent block purpose, recoverability,
   adherence and duration. Repair infeasibility and explain tradeoffs;
   don't only reject candidates.
5. Session prescriber and fatigue-aware set-level loads, RIR/reps/rest,
   realistic intra-session progression and deload/autoregulation.
6. Longitudinal cycle policy, custom authoring parity, evidence-grounded
   progression, robust adversarial tests and human-coach-style audits.
7. Independently certify ownership switch, data migration and device
   behavior, then work toward full better-than-web release acceptance.

The native app should use this reasoning to feel purposeful *without*
showing unnecessary coaching text on each set. The user should experience
a better coach and a cleaner workout interface at the same time.
