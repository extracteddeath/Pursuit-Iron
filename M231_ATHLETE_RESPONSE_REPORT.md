# M231 — Owned athlete-response memory

History analysis now reports actual completed muscle dose, modeled workload, authored-day frequency, exercise success/failure, effort coverage and estimated strength progression velocity. Comparable progression evidence is indexed by program, authored day and exercise. Completed work remains an accounting fact even when an exposure is interrupted or otherwise unusable for progression.

An explicit logged prescription is required for evidence that changes capacity. Sparse histories, unknown outcomes, unrelated programs/days and current-target fallbacks cannot create confident personalized dose changes. Repeated negative evidence must span at least three workouts and two authored days; a response update changes capacity by at most five percentage points inside the existing 0.6–1.0 bounds. Restoration requires strong favorable evidence and normal recovery. Existing recovery-phase safeguards still apply.

Standalone and fixed-cycle next-block adaptation consume the same response, and retain the prior block's owned response record. The model does not infer individual physiological dose thresholds or raise volume from a single successful workout.

Validation: deterministic shuffled/serialized replay, duplicate identities, owner isolation, missing effort, limited exposures, separate actual-dose accounting, progression velocity and bounded reduction/restoration; full adaptation regression group including 13 unchanged Build 816 goldens.
