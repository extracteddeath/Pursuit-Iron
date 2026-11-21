# Pursuit Iron — Kotlin progression coverage contract

The model intentionally separates **how an exercise advances**, **how a set
is loaded**, and **how a program changes over time**. These are orthogonal
dimensions of one Kotlin training engine, not competing weight editors.
Every future program must carry an audited progression assignment for every
`blockId + weekday + slotId`; numeric changes still require authenticated
history, full-phase audits and explicit athlete approval.

## 1. Deterministic basic progression strategies

| Progression | Pure Kotlin authority | Trigger | Baseline safety |
|---|---|---|---|
| Manual / logbook only | `BasicProgressionPolicyDomain` | Never | Repeat existing approved weights; no coaching needed |
| Strict double progression | `BasicProgressionPolicyDomain` | All ordinary working sets reach rep ceiling | One adjacent exact physical increment to all sets, atomically |
| Per-set dynamic | `BasicProgressionPolicyDomain` | A particular set reaches its rep ceiling | Only that stable set ordinal earns a physical step; no heaviest-set promotion |
| Linear load | `BasicProgressionPolicyDomain` | All straight working sets meet at least the rep floor | One adjacent verified increment on all sets, atomically |
| Rep-target advancement | `BasicRepTargetStepDomain` via weighted policy | Actual reps meet approved exact target | +1 exact rep target per eligible set, never beyond rep band; weights unchanged |
| Rep ladder | `BasicProgressionPolicyDomain` + shared rep target helper | Build exact set targets; all sets reach ceiling | Only then earn one stock-verified uniform increment and reset exact targets to the lower rep bound |
| Explicit bodyweight reps | `BasicBodyweightRepProgressionDomain` (same rep-step helper) | Completed authenticated unweighted work meets approved exact target | No invented 0-lb load, no borrowed machine history |
| Assisted counterweight double | `BasicProgressionPolicyDomain` | All assisted sets meet ceiling | One exact **lower** machine counterweight, never mistaken for a load drop |
| Programmed percentage / authored wave | `BasicProgrammedPercentageLoadDomain` | Athlete confirms training max + all prescribed week percentages | Every week maps DOWN to verified inventory; not based on RIR or unverified max |

**Manual and rep-only modes must work with coaching switched off.** A missing
stock prevents recommending new weights, but should not prevent logging.

## 2. Program-generation routing

`BasicProgramProgressionPlanDomain` classifies *every actual exercise slot*
from the fully authored, reviewed cycle.

| Program/visit | Default route | Important exception |
|---|---|---|
| Ordinary isolation / barbell accessory | Strict double | Exact user-selected basic strategy may replace default |
| Source-reviewed quick-change compound (DB/machine/cable/Smith) | Per-set dynamic | Requires source catalog pattern and verified experience, not exercise-name guessing |
| Novice primary compound with reviewed catalog | Linear | Complete every set at the rep floor; cannot infer personal capacity |
| Strength-phase primary, ordinary straight loading | Linear | Authored periodization or a separately calibrated wave takes priority |
| Explicitly selected per-set weighted mode | Dynamic | Only compatible weighted working sets; no silently flattened backoffs |
| Machine counterweight | Decrease assistance at rep ceiling | Rep-only if explicitly chosen; never ordinary higher-load progression |
| Unweighted bodyweight | Bodyweight rep target | Weighted vest or assistance has distinct physical meaning |
| Peak or performance test | Peak/test hold | Requires authored planned percentages/weeks; not earned auto-load |
| Recovery/deload | Recovery hold | Must preserve its independently audited load/dose/time constraints |
| Explicit week overrides (DUP, wave, ramp) | Authored weekly plan | No duplicate automatic DP on a previously specified week |
| e1RM style | Specialist coach review | No e1RM is fabricated from uncalibrated strength/RIR data |
| Mixed working loads, top/backoff, different rep envelopes | Specialist/authored loading | No inferred averaging or silent equalization |
| Set amplifier attached to working set | Parent progression + separately attached technique | Mini-sets/drops are not independent ordinary working sets |
| Athlete selects manual | Manual | No progression writes; preserve the program and history |

## 3. Not interchangeable with a basic load increment

- **Periodization:** hypertrophy → strength → peak transitions, accumulation
  and intensification weeks, DUP, waves, percentage days and tapers are
  program-structure decisions. `CustomTrainingCycleDomain`,
  `ReviewedStrengthWeekPeriodizationDomain`,
  `CoachPrescribedBlockDomain` and exact authored weekly overrides own
  their multi-week integrity.
- **Volume progression:** MEV/MAV/MRV, frequency adaptation, set increases
  and deload reductions belong to the composer, muscle-dose and cycle
  audit. They must not be another mechanism for secretly editing set loads.
- **Autoregulation:** reported RIR/RPE, fatigue, readiness, plateau logic,
  overload recovery and model-based rep forecasts remain optional coach
  policy. These may propose a reviewed revision but cannot overwrite a
  basic progression rule or historical set receipt.
- **Set loading:** top set, last-set backoff, pyramid, drop sets, myo-reps,
  rest-pause, clusters and lengthened partials have their own parent/child
  relationship and equipment constraints. A technique round is not a new
  independent double-progression set.
- **Bodyweight added-load and resistance bands:** explicit equipment semantics
  and signed baselines are needed; never convert to a guessed external load.
- **Advanced intensity targets:** velocity, tempo, and density/rest changes
  are separate prescribed dimensions, not a generic load step.

## 4. Release-neutral acceptance gates

1. Pure strategies must not mutate history, issued prescriptions or future
   programs; missing evidence fails closed and no step exceeds confirmed stock.
2. Every actual generated slot has exactly one progression route, with source
   block/day/exercise identity and special loading preserved.
3. The first-program `CoachFirstProgramDraftConversionDomain` provides typed
   progression assignments only after the full cycle passed its independent
   inventory and structure review.
4. A **durable persisted** chosen strategy, consented revision handoff,
   backward-compatible custom-program migration and UI selector are still
   required for a user-visible feature. A preview DTO is not persistence.
5. Current first-program generation does **not** infer a personal training
   max; it cannot automatically select percentage loading just because an
   athlete selects strength or peak. Authored max and weekly percentages
   are prerequisites; refuse rather than invent a strength cycle.
6. CI, real program-generation sweeps, native history replay and device
   acceptance remain mandatory before parity/superiority claims and before
   any APK/AAB release.

No JavaScript fallback, no web-app mutation and no new APK in this pass.


## 5. Web progression style identity parity

The actual web progression-style contract exports
`auto, double, dynamic, ladder, linear, wave, e1rm`. The Kotlin model now
recognizes **all seven names** through `NativeProgressionStyle`:

- **Auto:** selects a safe method using reviewed physical basis, source
  movement pattern, phase, athlete experience and exercise role where those
  facts exist. Missing stability/loadability metadata cannot justify inventing
  advanced readiness/measurement reliability.
- **Double:** strict all-sets ceiling gate.
- **Dynamic:** independent earned load step for each exact ordinal.
- **Ladder:** increment an authenticated exact rep target per set, then take
  one verified load step and reset to the rep floor when all reach ceiling.
  Unweighted bodyweight holds at the cap pending a reviewed harder variation.
- **Linear:** a deliberately basic floor-completion one-step policy. The web
  evaluator's current safety gate may require all sets at their *ceiling*,
  even while its style description suggests in-range progression; this is an
  explicit behavior difference to test in matched engine comparison.
- **Wave:** complete source-authored weekly sequence; can be calculated from
  a separately confirmed training max and verified inventory with the
  `BasicProgrammedPercentageLoadDomain`, not guessed from current reps.
- **e1RM:** separately reviewed effort-based coaching only. Preserving its
  style name does NOT claim validated e1RM prediction or an auto-load grant.

These are typed *program candidate assignments*, not proof that the durable
on-device progression selection and runtime adaptation have been migrated.
The parity/superiority acceptance gate must compare exact per-style outputs,
including this noted linear threshold difference, before any claim of parity.
