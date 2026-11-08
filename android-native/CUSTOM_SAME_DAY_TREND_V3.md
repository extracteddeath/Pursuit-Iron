# Pursuit Iron Kotlin — V3 reported-effort performance guard

## Why this exists
The existing **V2** custom rep proposal estimates each set's reps at its
original goal RIR using the actual reps and reported RIR from the latest
completed comparable workout. Its own warning is correct: that one workout
does not estimate chronic fatigue, residual fatigue within the next workout,
sleep, or rest differences. It can therefore suggest an increase after a
markedly worse same-day exposure when reported RIR still appears high.

**V3 is a strictly opt-in, versioned policy**, for newly reviewed rep targets.
It retains the per-set V2 formula and the possibility of more than one earned
rep and more than one raised set. It adds one evidence-backed *hold* check,
not a fixed one-rep or one-set progression cap.

## Decision rule
The ordinary native owner reopens and validates all issued, finished workout
snapshots and saved corrections. The pure V3 reviewer then compares only the
two most recent appearances of the exact same cycle, block, weekday, exercise
slot, phase, session purpose, source revision, loading and exercise variant.

A trend comparison requires **at least two working sets** in each session,
identical approved rep bands, goal RIR, actual per-set load and warmup
prescriptions; all working sets must be completed with independently
**reported** actual RIR in both sessions. An RIR field left blank can still
use the approved goal RIR for ordinary rep forecasting, but **cannot**
authorize a fatigue/performance-regression conclusion.

For each matching working-set ordinal, compute the *proxy*:

`observed reps + independently reported RIR`

With identical loads and goals, compare the proxy across the two workouts.
Only if **every comparable working set drops by at least two estimated reps**
does V3 return `HOLD_MARKED_SAME_DAY_CAPACITY_DROP`, show the ordinal
differences, and refuse both rep increases and a load-increase proposal.
The approved workout is **not** changed automatically. The athlete may
review readiness, rest, or a planned deload separately.

The two-rep threshold is a **conservative engineering heuristic**, not a
validated physiological fatigue cut point. The result does **not** diagnose
chronic fatigue, illness, overtraining, or the cause of the drop.

If a session is incomplete, load changes, the source is revised, RIR is
missing, there is only one comparable working set, or only one set declines,
V3 **does not invent a decline**. The existing verified target-RIR and
physical-equipment progression checks remain in force.

## Athlete opt-out and independent verification

The saved coach preferences choose the policy **on each new review**:
- **Standard:** V3 may examine the same-day reported-effort trend.
- **Progression Only:** V2's regular target-RIR progression remains available,
  but no V3 performance/fatigue guard is run.
- **Manual:** Neither proposal is made; the authored workout and completed
  history remain accessible.

A native owner-fenced review independently re-reads these settings. A
previously accepted V3 receipt cannot silently replay after fatigue coaching
is switched off; the athlete must obtain a fresh policy-matched review.
V1 and V2 receipts remain separately versioned and replay their own rules.

## What is kept stable
- V1's byte-for-byte approved receipts and frozen one-rep rule.
- V2's target-RIR projection, even on a pattern that V3 would hold.
- V2 and V3 version-2 receipts carry different policy names in both the
  fingerprint and signed envelope; reading either restores its exact rule.
- Individual training days never borrow each other's exposure. A Push-day
  bench cannot train the Upper-day bench model.
- No changed issued workout, exercise weight, completed set, or saved
  history without an explicit athlete-approved and current-source-bound
  transaction.

## Explicit uncertainty
Reported RIR is subjective. Accuracy varies with exercise, load and
proximity to failure. Representative papers:
- Refalo et al., J Strength Cond Res (2024), PMID 37967832, on
  intraset RIR estimates in resistance-trained lifters.
- Hughes et al., J Strength Cond Res (2021), PMID 33337690, on
  exercise/load differences in RIR estimation.
- Factors influencing RIR accuracy, systematic review (2025),
  DOI 10.1080/10833196.2025.2564026.

The current application has **no measured sleep, recovery, between-set rest
adherence, cardiovascular condition, velocity, or long-term individual
calibration model**. V3 never fabricates those facts.

## Release-grade evaluation still required
Before declaring V3 more accurate than V2 or the legacy JavaScript engine:
1. Use de-identified authentic, consented session pairs across exercises,
   set ordinals, loading schemes, trained days and lifter experience.
2. Score RIR-adjusted subsequent-session rep prediction error (MAE,
   signed error and overprediction), separately from the *hold* classifier.
3. Measure false holds, missed meaningful regressions, and the fraction of
   sessions with enough actual-RIR reports to support a trend decision.
4. Compare V1, V2, V3 and the legacy engine on the **same recorded sessions**
   and initial program constraints. No source-comparison fixture alone
   proves user outcomes or physiological superiority.
5. Confirm on Android that V3 review/accept/prepare/issue/Finish/restart
   and versioned older receipts survive cold restarts.

This is a bounded, read-only decision improvement, not a validated
predictive fatigue model or completion of the overall engine replacement.
