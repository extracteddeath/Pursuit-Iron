# Personalized coaching: corrected objective and evidence requirements

Status: requirements and research basis, not a claim that the complete model is
implemented. This supersedes the assistant-imposed one-set/one-rep criterion.

## Correction of attribution and benchmark

The athlete did not request that only one set progress at a time. The assistant
introduced that conservative restriction, repeated it as an athlete requirement,
and then used conformance to it to claim superiority over the web engine.
That claim is withdrawn. The raw comparison stays available, but its rep-target
pair cannot count as a measured improvement or a completed quality proof.

The actual objective is better understood, maintainable code that generates
effective programs/cycles and realistic individual day-to-day prescriptions.
Evaluate attainable training at the intended effort, not the number of changed
sets. Several sets may improve, stay the same or decrease as their evidence
warrants. A single-set change can be useful, but is not a universal rule.

## Example: evaluate the fifteenth rep

If 14 reps were completed at reported 3 RIR and the next target is 2 RIR,
15 reps is a plausible starting estimate at the same load. Holding the first
set at 14 merely because a different set was selected is unjustified.
This estimate does not establish a calibrated probability: reported RIR has
error, and next-session capacity can change. Reassess later sets after the
first set's actual work and rest; an extra earlier rep is not free of fatigue.
Do not subtract an invented universal fraction of a rep from each later set.

## One model of the lifter across three time scales

| Time scale | Required evidence | Decision |
| --- | --- | --- |
| Program/cycle | Goal, experience, exercise access, schedule, session time, tolerated workload, prior adaptations, preferences and phase | Useful complete programs, exercise/frequency/volume distribution, block progression and recovery |
| Across sessions/weeks | Comparable same-day/exercise history, load/reps/effort trends, completed work, reported recovery and recent workload | Distinguish improving capacity, ordinary variation, plateaus and accumulating fatigue; adapt the relevant workload/phase |
| Within a workout | Current actual sets, earlier exercises, exercise order, set position, rest, load, reported RIR and recent decline at matched conditions | Update remaining set predictions and appropriate rest/load/rep options without changing logged facts |

Experience informs initial policies, not an automatic claim that an advanced
lifter reports RIR more accurately. Calibrate from the individual's observed
results where possible. Do not turn a usual within-workout rep decline into
proof of chronic fatigue, or diagnose a chronic fatigue state from one bad day.

Missing actual RIR may use that original set's goal as the athlete-requested
progression assumption. Mark it as assumed, preserve actuals as null and avoid
claiming measured recovery or precise capacity from it. Further history can
inform predictions, but must not be invented or borrowed from another day.

## Scientific basis and limits

- [Hughes et al., PMID 33337690](https://pubmed.ncbi.nlm.nih.gov/33337690/):
  RIR accuracy varied with load in trained men across four exercises. Treat
  reported effort as an uncertain estimate, not a measurement of exact capacity.
- [Accuracy of intraset RIR, PMID 37036795](https://pubmed.ncbi.nlm.nih.gov/37036795/):
  proximity to failure and set number influenced prediction accuracy; training
  experience was not a significant predictor in this study. Experience alone
  must not become an unvalidated confidence multiplier.
- [Willardson and Burkett, PMID 16686571](https://pubmed.ncbi.nlm.nih.gov/16686571/):
  repeated-set performance varied with rest conditions and declined across sets.
  A context-free copy of first-set capacity is not a complete workout forecast.
- [Mangine et al., PMID 34941608](https://pubmed.ncbi.nlm.nih.gov/34941608/):
  in trained men, stopping short of failure better sustained acute work across
  sets. Its recovery measures did not establish an equivalent universal
  multi-day fatigue penalty. Keep acute and longer-term fatigue distinct.
- [Individual RIR/velocity relationships, PMID 38418370](https://pubmed.ncbi.nlm.nih.gov/38418370/):
  individual relationships outperformed general relationships in the studied
  squat protocol. This supports individual calibration, not a claim that a
  rep-only app has measured bar velocity or achieved the paper's accuracy.
- [Plotkin et al., PMID 36199287](https://pubmed.ncbi.nlm.nih.gov/36199287/):
  both load and repetition progression were viable over an eight-week lower-body
  intervention. This does not identify one universally best progression rule.
- [Helms et al., PMID 29628895](https://pubmed.ncbi.nlm.nih.gov/29628895/):
  both RPE- and percentage-based programs improved outcomes; between-group
  differences were not statistically significant. Do not advertise a custom
  autoregulation algorithm as superior merely because it uses RIR.

These findings inform the model structure. They do not supply universal
coefficients, a per-set rep cap, a fixed number of successful sessions before
progression, or a guaranteed optimal dose for every muscle and athlete.
Published group means are starting information; they are not personal ground truth.

## Implementation and evaluation

Extend the current generator, prescription, fatigue and longitudinal domains.
Use one explicit context and decision explanation; do not add a parallel engine
or a second journal. Keep training decisions separate from ownership/consent.

The current `FatigueAwareRepTargetReviewDomain` implements a v2 starting estimate:
observed reps + resolved historical RIR - intended future RIR, bounded by the
rep ceiling. A required result below the floor asks for a load review. This is
an arithmetic interpretation of reserve, not a research-validated forecasting
algorithm. It can change several sets/reps and reduce a set whose reported
effort exceeded its goal. Assumed historical RIR always uses the original goal,
not a later goal chosen to manufacture reserve.

This estimate retains each set's own observed position/load rather than
copying the first set. It explicitly exposes uncalibrated RIR, single-exposure,
unobserved rest/prior-work and unmodeled chronic-fatigue limitations. Changed
early-set work adds a further later-capacity uncertainty flag. It does not yet
quantify those fatigue effects or individual response. The native preview
explains that the lifter should stop at target effort if fewer reps are available.

New reviews no longer enforce the one-set restriction. Frozen v1 is retained
solely for reproducing already accepted historical receipts; its distinct
identity cannot masquerade as v2. Issued workouts and actual history remain
immutable. Storage encoding v6 supports both old receipt replay and new v2
multi-set estimates. These compatibility checks are not a coaching milestone.

Before calling its replacement accurate:

1. Declare inputs actually available. If rest, workload, earlier exercise
   exposure or recovery are absent, expose the gap instead of making up values.
2. Distinguish observed capacity evidence, a forecast, and a selected target.
   Label uncertainty and avoid fabricated success probabilities.
3. Compare predictions against later held-out workouts for the same lifter,
   exercise/day and appropriate context. Track rep and effort error, systematic
   under-prescription/overshoot, coverage and calibration where probabilities exist.
4. Test several legitimate progressions, including improving multiple sets and
   the first set reaching 15 at target RIR. Reject unsupported increases and
   unnecessary holds; a conservative refusal is not automatically correct.
5. Exercise multiweek response, acute fatigue, plateau, recovery, goal/phase
   transitions and coaching-off behavior through the actual usable app loop.
6. Grade both engines against independently justified outcomes. Do not award a
   win for matching a rule that was invented to describe the Kotlin output.

Optional fatigue/coaching layers retain their off switches. The chosen core
progression method must remain usable when those suggestions are disabled.
