# Pursuit Iron Native — must surpass the web app

## Product decision

**The existing web app is the minimum functional feature baseline, never the
ceiling for training intelligence, interaction design, performance, reliability
or product polish.** The Android experience must be demonstrably better in
every meaningful user journey. The new Kotlin engine must correct known
training errors, not reproduce them as parity fixtures.

This is the *release acceptance standard*, not a statement that the current
Android implementation already passes. M5 is a source-tested development APK
that enables device evaluation; no Android-device results have been recorded
yet. This contract applies to both isolated branches:
`architecture/android-compose-foundation` and
`architecture/kotlin-engine-rebuild`. Neither branch may modify the
production web app or the other developer's native work.

## Coach-quality programming is an independent release gate

[COACHING_QUALITY_STANDARD.md](COACHING_QUALITY_STANDARD.md) defines the
non-negotiable programming standard: every microcycle, exercise prescription,
progression change, deload and phase transition must be defensible as something
a skilled coach would prescribe for the **whole individual**, not merely
numerically valid. The 14 scenario families and their rejection criteria
also apply to native UX: surface decisions concisely and keep active set
logging fast and free of repetitive banners.

A headless training-rule unit test is only *technical evidence*, never
a substitute for real-program quality, longitudinal review or device
acceptance. The current Kotlin coaching-review outputs remain provisional.

## Every-feature comparison is mandatory

For **each** of the 14 feature IDs in `FeatureCatalog.kt`, maintain:
1. Web baseline: what the latest actual web flow allows and its failure modes.
2. Native flow: real setup -> use -> failure -> recovery -> history/persistence.
3. Concrete improvement: simpler user task, more correct behavior, clearer
   feedback, better accessibility, or improved capability. Mere visual parity
   is **not** an improvement.
4. Evidence: scenario IDs, independent rule fixtures, Android recordings,
   performance measurements, and negative/recovery tests.
5. Status: Not started / Implemented (unverified) / Source verified /
   Device verified / Better than web verified. Never promote automatically.

### Capability-level improvement targets

| Feature ID | Required improvement over the web experience |
| --- | --- |
| adaptive-progression | Conservative earned progression, rep/RIR/fatigue evidence and verified equipment; no known double-progression weight jumps |
| workout-logging | Lower interaction cost, fast editing, reliable receipts, single-screen/focus continuity and interruption-safe resume |
| session-adjustments | Clear readiness, injury-related and 45/30/20-minute changes, with an explicit explanation of what changed |
| workout-tools | Correct warm-up/rest, plate calculator, supersets and myo/drop semantics, substitutions, pinned/session notes and reachable controls |
| program-builder | Accurate split, frequency, muscle exposure and time-range feasibility, with inspectable prescriptions rather than unexplained truncation |
| custom-programs | First-class authored programs that retain settings, amplifiers, movement identity and adaptation across phases |
| adaptive-cycles | Hypertrophy/strength/peak changes with justified frequency, split/exercise adaptation only when authorized, and no mysterious volume collapse |
| exercise-discovery | Equipment-aware variants, attachments, bodyweight policy and safe exercise swaps |
| history-and-prs | True completed observations, correct history edit, Last, PR eligibility and caches after revisions |
| volume-and-trends | Direct/indirect muscle accounting, consistency, standards and accessible, correction-safe charts |
| bodyweight | Correct measurements/milestones, progress identity and no fabricated trends or duplicated credit |
| backup-restore | Explicit review, non-destructive import/merge, conflict handling, rollback/Undo and reliable export/readback |
| gyms-and-equipment | Exact physical plates, Smith/bar weight, per-hand loads, cable/machine increments and gym-aware choices |
| profile | Retain all 20 original themes; improve preference persistence, user profile, accessibility and legibility |

If a web capability is missing, disconnected, simulated or worse on Android,
it remains an open **release blocker** until a documented replacement provides
at least the same user outcome and a measurable improvement. A deliberately
removed feature requires an explicit user-approved decision, not a quiet
simplification. Native architecture and visuals can change completely.

## Non-negotiable engine superiority

- **Independent specification, not web-output agreement:** requirements,
  hand-calculated cases, adversarial records and property invariants define
  expected behavior. JS parity is used only for legacy migration semantics.
- **No infeasible prescription:** exercise equipment, plates, implement
  weights, machine increments, units, per-hand/total semantics and deliberate
  user overrides must be explicit. No fabricated stock or silent rounding.
- **Earned progression:** distinguish missing, skipped, failed and completed
  sets, actual from prescribed RIR, intra-session fatigue and all-set load
  capacity. A last heavy set alone never proves the next workout's starting
  weight. Cover 200×3/205×3/210×2/215×2 at 5–8 and 215×2 near failure.
- **Programming quality:** all supported splits, 3–6 days, goal/time range,
  equipment, focus/reduced muscle choices; feasible volume must satisfy
  defined MEV/MAV/MRV and frequency constraints, with a clearly surfaced
  explanation whenever constraints cannot all be met.
- **Muscle granularity:** biceps vs brachialis; wrist flexion vs wrist
  extension; direct versus indirect exposure without double counting.
- **Cycle and history stability:** no unexplained phase underdosing; custom
  authoring survives. Corrections revise factual feedback but do not
  reinterpret old issued targets, overwrite logged loads or duplicate PRs.
- **Reproducibility:** deterministic clock/seed/engine version, traceable
  recommendation reasons and explicit migration with no silent fallback.

## Native UX and reliability superiority

- The real Today -> Train -> Plan -> Insights journey must be clearer and
  require **no more taps** than the equivalent web task. Target a strict
  improvement for frequent actions (log, correct, rest, resume, finish).
  Measure against the live web build using the same tasks and device.
- Users must be able to complete a set without navigating away; all required
  tools remain reachable in Focus mode. No explanatory banners per set,
  competing steppers, dark-on-dark cards, hidden actions or fake placeholders.
- Shared Jetpack Compose Material 3 Expressive tokens, native components,
  meaningful motion and full original theme palette; keyboard/insets,
  TalkBack, large font, reduced motion and phone/foldable layouts must work.
- No set is marked completed until durable acknowledgment; no repeated
  Finish publication, lost history correction, overwritten draft or
  unrequested training rule changes after rotation, backgrounding, process
  death, storage failures or retry.
- Run paired, repeatable performance scenarios on real devices: time to
  resume; time/taps to log or correct a set; startup; screen transitions;
  interaction responsiveness; battery/awake behavior. Define acceptance
  thresholds from **measured** web baseline plus a clear improvement
  margin—never manufacture baseline numbers.
- Critical data-loss, fabricated prescription, non-recoverable crash and
  unintentionally destructive import are automatic blockers, irrespective
  of visual quality.

## Phase gates

| Milestone | Evidence required | Can ship to users? |
| --- | --- | --- |
| M5 native technical test APK | Canonical source/JVM checks and compile; no device superiority claim | No |
| M5 device acceptance | Complete on-device onboarding, active sets, interruption, corrections, Finish and history, with defects documented | No |
| Independent Kotlin engine | New loading/evidence/progression/dose/cycle rules pass expected-outcome tests and adversarial cases; app engine switch is separately gated | No |
| Complete native feature set | All 14 feature outcomes operational and persistent; no feature silently abandoned | No |
| Better-than-web verification | Paired web/native user tasks, comparable performance, reliability and accessibility evidence plus independently certified engine decisions | Release candidate only |
| Release | All above passing, safe migration, no critical open issues, explicit approval | Yes |

**Design and engine work are both first-class workstreams.** Do not wait for
every screen to be visually finished before testing engine behavior, or
defer every feature until after engine code is complete. Improve the shared
UI and the headless engine in parallel behind stable data contracts; never
silently switch the training authority.

## Evidence ledger

Keep a dated comparison per capability using:
`feature ID | web behavior/build | native build | improvement | source tests |
device tests | regressions | status`.

Current verified facts (2026-11-02/03): M5 APK built from
`d7af9b3bc30fb5393ba3f63fcd3d655a8070d0b6`; source checks passed.
The Kotlin rebuild has begun separately. **None of the 14 capabilities
is yet certified as better than web on-device.** This is deliberate
honesty, not a lack of ambition.
