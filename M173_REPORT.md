# M173 integration + bootstrap resilience

App **3.223.0**, build **779**, Pursuit Engine **0.63.0**.

## Baseline audit

- History correction already normalizes editable set/load/rep/RIR data, locks structural provenance, refreshes mirrored performance/cache state, and makes Pursuit Engine re-read corrected evidence. It was left intact.
- Engine authority already covers generated prescription → shell cell → workout runtime → history → progression. It was left intact.

## New hardening

- Added one end-to-end torture gate across history correction, causal recovery evidence, next-block readiness, M170/M171/M172 phase transition generation, continuity, user avoidance, and final audit.
- Fixed stale startup boot-health accounting that still compared against build 774.
- Added PWA/mobile source resilience checks without another UI redesign.

## Validation

The focused M173 gates and the complete release-integrity suite must pass before this workflow publishes the milestone. Physical Android validation remains a separate device check.
