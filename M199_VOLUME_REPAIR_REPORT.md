# M199 — Volume repair and Auto-fix integrity

Pursuit Iron 4.0.0, build 789. Engine 0.64.0.

Baseline: current `main` at `fa850476976b4f1ca34865b4550ef6e3bd21a23c`, including the newer M199 wizard fixes. M198 capacity and split preservation remain intact.

## Fixed behavior

- Auto-fix previously wrote legacy `slotBias`; the Next engine ignored it, leaving the prescribed sets unchanged. Repair now edits the active engine program and its canonical weekly cells.
- Auto-fix previously changed only the open draft. Verified changes now use the shared program commit, updating the saved plan and the open view together.
- Program warnings used generic intermediate landmarks and only the final week. Audit now uses the engine's experience, phase, priority, and capacity targets and checks every working week; deload is excluded from growth-volume warnings.
- Lat-biased rows previously received full upper-back credit from their generic movement family. Catalog muscle intent now takes precedence: these rows receive 0.4 upper-back credit, while upper-back rows receive full credit. Pullovers remain lat work.
- Volume bars, per-day contributors, and warnings now read one live snapshot, using the engine's muscle ledger and public regional credits. Biceps/brachialis and wrist flexion/extension remain distinct in arm coverage.

## Repair order and verification

1. Increase useful sets on existing movements.
2. Reallocate accessory sets when sessions are full; consolidate redundant setups before introducing another movement.
3. Add a compatible movement only after existing safe set changes are exhausted.
4. Check all working-week rounding, exercise set limits, session minutes, equipment/bodyweight/barbell restrictions, recovery, split identity, and the full engine audit.
5. Persist only accepted changes. Re-read the returned artifact and report success only when its volume warnings are cleared and the engine audit passes. Partial or infeasible repairs explain what remains.

Reps, RIR, rest, progression metadata, and user overrides remain attached to their correct slots when a movement is inserted or a redundant setup is removed. New movements are placed before isolation work to avoid local pre-fatigue.

## Validation

- PASS: new volume-repair regression, including the old no-op reproduction, two existing rows absorbing the deficit without a third exercise, capacity reallocation, honest failure, serialized reload, identical UI accounting, separate arm buckets, and Full Body with Hypertrophy/Strength/Both.
- PASS: M199 wizard feasibility — 500 inexpensive checks and real Full Body generation across every time band.
- PASS: M198 creation audit — 71 routes, zero enabled-to-build mismatches, five capacity ladders, including standalone programs and cycles.
- PASS: full release integrity — existing progression/history/prescription/set-display/engine/coach-quality gates; 62 authored modules parse and 85 offline-shell entries are present.
- PASS: real phone-size browser click/save/reload — 25 stable exercise slots; weekly prescribed sets changed from 92/90/88/88/86/83 to 94/92/90/89/88/85, persisted to the canonical saved program, and remained repaired after reload.
- PASS: production Release integrity, PWA lifecycle integrity, volume repair integrity, and Pages deployment for commit `725219098cf883fca8d464feb2528fc1a0336bb3`.
- Browser certification: https://github.com/extracteddeath/Pursuit-Iron/actions/runs/36760832393

The service-worker cache rotates for build 789 so installed PWAs can receive the repair through their normal update flow.
