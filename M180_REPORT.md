# M180 Coach Quality Oracle 2.0

Candidate target: **app 3.229.0 / build 785 / Pursuit Engine 0.63.3**.

## Goal

Move Pursuit Iron beyond “the program generated and passed structural validation” toward an independent release-quality contract that asks whether the prescription remains defensible as a coach-built program.

## Coach Quality Oracle

M180 adds a domain-gated oracle above the existing engine audit, objective coach guardrails, and sampled blind human-review framework. It deliberately does **not** calculate one blended quality score. A strong result in one category cannot average away a major defect in another.

Independent domains:

- safety and restrictions
- dose and priority fulfillment
- session execution
- exercise selection
- fatigue and recovery
- split and phase identity
- prescription quality

Critical/major findings reject the candidate. Softer findings require review. Human-review evidence also has minimum sample and per-dimension floors, and repeated major consensus defects remain independently release-blocking.

## Engine defect found and fixed

The first real generated-program audit surfaced a `LOWER_BACK_CLUSTER` in a 4-day mixed Upper/Lower plan with high squat, bench, and deadlift priorities. The lower session contained both a 3-set secondary Back Squat and a 4-set primary Conventional Deadlift even though the split provided two lower sessions.

The cause was strength-claim capacity selection: generic strength plans already refused optional lower-strength volume when it required squat/deadlift stacking, but mixed/powerbuilding plans did not. M180 extends that recovery protection to generic mixed plans. Required heavy squat/deadlift anchors remain inviolable; only optional second exposures are declined when they cannot fit the canonical split without lower-region strength stacking. Named strength systems retain their structure-specific behavior.

## Regression coverage

The focused oracle suite verifies that:

- hard execution defects cannot be averaged away by excellent human scores;
- a clean program is release-eligible;
- soft defects cause review rather than being mislabeled as hard failures;
- one weak human-review dimension cannot hide behind stronger dimensions;
- repeated major underdosing consensus rejects the candidate;
- insufficient blind-review evidence cannot falsely certify a program;
- mixed Upper/Lower preserves required squat/deadlift anchors while refusing optional lower-strength stacking that would create the recovered defect.

The representative quality matrix is now **pass-only** and covers 19 setups spanning novice/intermediate/advanced, hypertrophy/mixed/strength, 2–6 training days, Full Body, Upper/Lower, PHUL, ULPPL, PPLUL, PPL, Bro Split, Torso/Limbs, short and long sessions, supersets disabled, machine/dumbbell constraints, bodyweight exclusion, and home dumbbells.

## Release rule

M180 is not allowed to ship if any matrix case is `review`, `reject`, or fails to generate. The complete existing production release suite must also remain green.

Physical Android installed-PWA certification remains tracked separately in M179 and is not claimed by this milestone.
