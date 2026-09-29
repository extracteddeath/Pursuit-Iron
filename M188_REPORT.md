# Pursuit Iron M188 — Production Torture + Certification

## Purpose

M188 is the final broad production-candidate stress milestone after M187. Its job is not to add another feature layer; it is to prove that the current engine/app remains correct under a wider matrix and across long time horizons.

The critical regression M188 is designed to catch is **late degeneration**: a program that looks correct in week 1 but becomes invalid, empty, underdosed, structurally broken, or contradictory by week 9 or after a block transition.

## Automated certification scope

### Broad generation matrix

`verification/m188-production-torture-test.mjs` covers:

- novice / intermediate / advanced;
- hypertrophy / strength / mixed goals;
- 2, 3, 4, 5 and 6 training days;
- full body, upper/lower, ULPPL, PPL, PPLUL and PHUL topologies;
- 45–90 minute sessions;
- full gym, machine/dumbbell-only, and home-dumbbell equipment;
- bodyweight exclusion;
- supersets disabled;
- one-barbell-movement-per-day restriction;
- high SBD priorities;
- arm/delt hypertrophy priorities.

Every generated program must pass both the engine audit and the pass-only Coach Quality Oracle. Every session must remain non-empty, inside its time budget, and contain valid sets/reps/RIR.

### Longitudinal torture

The same gate runs adaptive multi-block simulations for:

- Powerbuilding;
- Strength Peak;
- Beginner Foundation;
- Hypertrophy Specialization;
- steady response;
- fast response;
- mixed/inconsistent response;
- fatigue-prone response.

The simulations run 10–14 weeks and verify:

- week 9 is actually exercised;
- second/third blocks remain non-empty and audit-clean;
- phase identity matches the cycle contract;
- prior training history carries into later blocks;
- stalled exercises do not immediately resurrect through adaptive continuity;
- weekly session counts and time limits remain valid;
- deterministic replay produces the same fingerprint and block metrics.

### Cross-product reliability gates

The M188 workflow additionally reruns:

- M173 integration torture and history-correction replay;
- M174 causal cycle-state logic;
- M175 longitudinal adaptation memory;
- M176 interrupted-workout restore and malformed-state handling;
- M177 editable-history merge integrity;
- history volume and history edit integrity;
- set display, cycle overview, and swap-sheet UI regressions;
- M180–M186 engine-quality contracts;
- M187 premium UX and release/offline integrity;
- production release verification;
- a real Chrome service-worker update lifecycle test.

## Device certification

Physical Android certification is tracked separately in `M188_DEVICE_CERTIFICATION.md`. Automated CI does not claim physical touch/keyboard/system-interruption certification.

## Exit criteria

M188 is complete only when:

1. the automated `M188 production torture certification` workflow passes on the M188 branch head;
2. any defects exposed by the broader/longitudinal matrix are fixed and the complete matrix is rerun clean;
3. all 20 required physical Android cases are passed with concrete evidence.

Until item 3 is satisfied, the branch can be called **automated production candidate**, not fully device-certified production.
