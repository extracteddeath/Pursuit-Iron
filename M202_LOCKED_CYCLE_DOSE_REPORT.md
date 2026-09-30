# M202 — Locked-cycle phase volume reconciliation

Pursuit Iron 4.0.0, build 792. Pursuit Engine 0.64.3.

## Problem

Static cycles deliberately keep the same exercise skeleton across blocks. Phase retargeting happens before the shell expands base prescriptions into exact work-week set counts, so whole-session rounding could leave a later strength block above the phase-specific regional ceiling shown by the app even though the immutable engine program passed. The reproduced case was 12 effective lower-leg sets against a 9.6-set strength-block ceiling.

## Fix

M202 uses two separate transactions. First, the existing shell-volume repair may change accessory base-set counts while a preserve-roster mode forbids exercise additions/removals, permits a one-set accessory floor, and continues to protect all strength roles and re-audit the engine. Second, after base repair has stopped, a locked-cycle finishing pass may reduce generated work-week accessory cells only. That second pass is intentionally separate because rebuilding a base session regenerates its weekly cells. It protects exercise identity and strength work, never increases session time, and never lets another public region leave its prior safe envelope.

Tied worst weeks are handled explicitly: a trim can improve the current offending week while the same block-wide maximum moves to the next tied week. Such a plateau step is accepted only when total deficit does not worsen and the exact offending week/region strictly improves. The pass continues until the displayed ceiling is satisfied or no safe trim remains; unresolved overage fails cycle creation closed.

## Regression contract

The M202 regression builds multiple 5-day PPL powerbuilding cycles with exercise adaptation disabled. It must reproduce at least one pre-repair locked-cycle overflow, perform a real repair, preserve every exercise ID across all blocks, keep the core engine audit passing, keep every session within its time cap, and leave zero phase-specific displayed-region overages. M200 regional-dose, M199 volume-repair, and M201 workout-prescription regressions are also required to pass.
