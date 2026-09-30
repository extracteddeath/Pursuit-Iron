# M202 — Locked-cycle phase volume reconciliation

Pursuit Iron 4.0.0, build 792. Pursuit Engine 0.64.3.

## Problem

Static cycles intentionally preserve the same exercise roster across blocks. The phase retargeter correctly changed reps, RIR, rest, progression, and base set counts, then stopped once the core engine audit passed. The shell later expanded those base prescriptions into exact working-week cells. In some powerbuilding strength blocks, that week-by-week realization could leave accessory regional volume above the phase-specific ceiling displayed by the app (the audit finding that opened this pass was calf volume reaching 12 effective sets against a 9.6-set displayed upper boundary).

## Change

Locked blocks now run the existing verified shell-volume transaction after their weekly cells are built. A new preserve-roster mode forbids exercise additions and removals and permits a one-set accessory when that is the safe way to retain the locked movement. The transaction still excludes protected strength roles, rejects any proposal that worsens the engine audit, honors time limits and regional floors, and rechecks every working week. If a locked block still has a displayed-region overflow after legal set-count repairs, cycle creation fails closed instead of presenting an internally inconsistent plan.

## Regression contract

The M202 regression builds multiple 5-day PPL powerbuilding cycles with exercise adaptation disabled. It requires at least one previously overflowing block to be reproduced, then verifies that the repair actually changes set counts, every later block preserves the first block's exercise IDs exactly, all core engine audits pass, all sessions fit their time cap, and no working week exceeds the public phase-specific regional ceiling.
