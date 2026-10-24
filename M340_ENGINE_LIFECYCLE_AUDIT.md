# M340 — Full engine lifecycle audit

This audit checks cross-boundary consistency with one saved program and real workout serialization rather than only disconnected calculation fixtures.

## Run it

From the repository root (Node 22+):

    node scripts/audit-training-lifecycle.mjs --report /tmp/pursuit-iron-lifecycle-audit.json

The command exits nonzero on any failed stage. It prints AUDIT_GATE and ENGINE_AUDIT_REPORT machine-readable JSON records including the exact tested Git revision. The six stages reuse established creation, executable-prescription, set-progress, swap, multi-cycle and recovery contracts.

The new verification/m340-engine-lifecycle-journey-test.mjs is also registered in the existing integration CI gate. It creates an adaptive two-block cycle; realizes actual prescriptions into live working rows; rechecks untouched sets for drift; protects manual and completed rows on resume; logs completed sets using production serialization; compares preview dose with recorded working sets; checks the exact persisted LAST evidence; round-trips storage; corrects history without double-counting; adapts to the reserved preview block; and completes the cycle.

## Coverage ownership

| Boundary | Authority | Gate |
| --- | --- | --- |
| Creation and wizard buildability | Canonical generator, split feasibility | M198, M340 |
| Saved program to preview and week cells | Saved executable engine prescriptions | M214, M323, M340 |
| Workout start, live editing, resume | Per-set targets and explicit user ownership | M201, M220, M340 |
| Completion and raw history | loggedWorkoutPerformance and per-set snapshots | M214, M327, M340 |
| Next advice and progress calculations | Revision-normalized comparable completed work | M220, M323, M340 |
| Corrected history and recovery | Newest valid historical revision | M214, M323, M340 |
| Cycle transition and final completion | Cycle adapter consuming the same saved history | M323, M340 |
| UI/mobile/offline interruptions | Browser shards and physical Android certification | Existing browser gates, R90 |

## Regression standard

A new unexpected target, logging or adaptation result should receive a minimal reproducible saved-program + workout-row + history fixture. Check creation, preview, live display, serialized log, next prescription and block change together; do not add another local estimator or silent normalization to make one screen look correct. Working sets, warmups, added work, substitutions, edited prescriptions, explicit effort, history revisions, equipment rounding and manual control must preserve provenance.

A green M340 result is necessary but does not certify every Android interruption, accessibility issue, animation, or visual discrepancy. Those are separately verified with browser/device checks. Every report must identify revision, passed/failed stages, the first discrepancy, reproduction data, and its owning layer.
