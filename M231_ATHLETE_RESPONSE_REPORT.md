# M231 — Athlete response model

Engine 0.65.3 / Build 820 integration candidate.

History analysis now derives a versioned, observational athlete-response snapshot from active-program completed workouts. It records muscle dose tolerance, failure pressure, frequency, exercise success/fit, progression velocity, recovery spacing and confidence. Progression trends stay separate by authored session and exercise. Duplicate revisions, stale/future records, unrelated programs, ambiguous slots, edited/interrupted/readiness-limited exposures, incomplete work and unreviewed custom semantics are excluded with reasons. Loads normalize to the declared unit; missing effort remains unknown.

Estimates shrink toward the prior and use at most twelve observations per slot across ninety days. Automatic dose changes require six comparable exposures spanning fourteen days with at least 75% reported effort. Preferred and upper regions move by at most -10% or +5%; explicit priorities, authored schedules and minimum/public floors remain intact. Progressing work retains its dose. Repeated broad comparable failures can reduce capacity by at most 5%. Frequency changes remain review information because observational spacing cannot establish an athlete's causal optimum.

Actual shell logs feed the model. Next-block and cycle request adaptation carry it into canonical muscle prescriptions, while tolerated successful movements receive a bounded preference that cannot override avoidance/equipment constraints. The existing per-workout progression evaluator remains the authority for earned load/rep changes.

Validation covers deterministic historical replay, program/day ownership, duplicate/recency/context/effort safeguards, unit parity, sparse-evidence restraint, schema refusal, actual shell-history integration and bounded dosage application. Existing baseline generation remains unchanged without response evidence. This model reports uncertainty rather than claiming individually measured physiological thresholds.
