# M343 — Multi-cycle and recovery lifecycle audit

M340 checks real program creation through a two-block cycle; M341 checks adversarial live-set behavior; M342 checks varied multi-week programs. M343 follows actual production workout serialization through **four consecutive adaptive blocks**, across upper/lower and equipment-limited full-body profiles, and independently forces early fatigue that inserts a recovery block.

Each block exercises its own user-selected progression style (double, dynamic, ladder, e1rm). The suite verifies that the exact historical prescription survives logging; current-block analytics do not accidentally consume previous-block history; corrected logs do not duplicate workouts or volume; the saved phase plan and prebuilt preview ID stay intact; and adapted exercise programs retain passing generation audits and session-time constraints.

The forced recovery route tests historical fatigue evidence, one inserted recovery week, exact recovery lineage and resume index, protected reserved preview, and a refusal to exit recovery on insufficient favorable workouts. It does **not** assert that every fatigue scenario should deload; causal recovery rules remain owned by the engine and the M323 contracts.

## Run

```sh
node scripts/audit-training-lifecycle.mjs --report verification/engine-lifecycle-audit.json
```

For an individually reproducible program and saved-state failure:

```sh
M343_SEED=34311 M343_REPRO_DIR=/tmp/engine-repros node verification/m343-multicycle-recovery-test.mjs
```

M343 is part of the integration registry, eight-gate predecessor audit plus the new ninth gate, and the revision-scoped CI report. As always, a full browser/device pass remains independent from the engine-only property replay.
