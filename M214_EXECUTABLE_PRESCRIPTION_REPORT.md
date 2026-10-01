# M214 — Executable prescription synchronization

Pursuit Iron 4.0.0 build 804 · Pursuit Engine 0.64.10.

Baseline: M213 build 803, commit `856344cc89bae8bff0b4f571491233aa8c2584b1`. The M207–M213 cleanup was already complete on main; this release continues from that source.

## Reproduced faults and resulting behavior

| Trigger | Before | After |
|---|---|---|
| Edit a generated slot to 2 sets at 10–15 reps and complete both at 10 | Workout showed the edit; history evaluation still required 5 sets at 8–12, reporting incomplete work | Runtime, immediate feedback and history evaluate 2 sets at 10–15; ordinary rep progression stays inside the range |
| Save a cycle-wide exercise swap | Future blocks displayed the replacement, but retained the old bound engine ID; the regional ledger counted the old movement | Bound identity follows the visible roster in both adaptive and locked phases, including previously saved stale metadata |
| Reorder lifts across later phases | Each later phase's cells and user metadata stayed at old slot numbers | Each surviving lift retains that phase's own cells, technique, user edits and explicit method at its new position |
| Edit a plan after logging a workout | Its historical target could be reconstructed from the newly edited plan | New logs retain an immutable per-exposure prescription; current suggestions can recalibrate for the new target without rewriting the old performance |
| Imported evidence includes unfinished rows, warmups or extensions | Those rows could enter working-set completion/progression | Only completed working sets count; partial top-range performance cannot earn an automatic load increase |
| Convert or complete an edited block | Continuation used the old immutable base roster, reviving removed movements or dropping a per-lift manual method | A temporary live base projection carries current identity and explicit method into the audited continuation |

## Shared boundaries

`getNextShellCell` owns current executable prescription resolution, including explicit `progStyle` selections. History evaluation now uses that same reader instead of bypassing ownership with raw week-cell reads. Response diagnoses include live swapped/added movements rather than only the original generator roster.

`resolveNextShellExerciseId` binds the visible shell exercise to the engine catalog. Volume, history and runtime loading use it. Cycle propagation remaps existing slot stores before updating identity; the receiving phase retains its own prescription rather than taking another phase's dose.

`snapshotNextShellPrescription` records the target at workout completion. Valid matching snapshots govern historical evaluation; malformed imported snapshots fall back to the current canonical projection. New logs distinguish reported effort from display fallback, even when the athlete reports exactly the prescribed effort. History correction preserves the prescription snapshot and marks edited effort as reported. Neither evaluation nor continuation rewrites historical sets or the stored immutable base.

`captureShellBaseProgram` projects the current roster for standalone conversion and real block advancement. It retains base sets for automatic slots, honors explicit counts, and recalculates events, the ledger, clock and audit. `continuationProgressionStyle` shares the rule for retaining user-selected methods between locked and adaptive continuations. Incoming explicit global choices remain authoritative; automatic methods still receive phase/evidence review.

## Cleanup and efficiency

Removed the historical root bundle, exactly 1,447,745 bytes. It had no production import, HTML load, precache entry, workflow dependency or verification caller. This reduces repository/deployment payload; it does not claim to speed startup, since the current entry never downloaded that bundle.

Removed the duplicate volume technique decoder; Workout, history and the executable audit share the existing protocol decoder. History now creates the legacy lookup once per planned session instead of once per unresolved slot. Transition projection reuses its captured snapshot/context; volume repair constructs its initial base sessions/events once. No runtime worker or measured timing improvement is claimed.

## Verification

The new behavioral release gate covers the reproduced set/rep mismatch, immediate/history advice, top-range versus partial completion, explicit/missing effort, logged-target stability after edits, malformed snapshots, all three final-set techniques and Off, cycle swaps/reorders, immutable inputs/base/history, previously saved stale identity, protected Auto-fix edits, locked/adaptive conversion and actual history-driven block advancement with per-lift manual methods.

The new browser gate mounts the real WorkoutSession at a 390-pixel viewport, saves a completed edited 2 × 10–15 workout, checks prescription and typed-row target/effort provenance in the actual completion payload, replays it through the engine, and verifies browser reload persistence. Existing browser gates continue to cover 390/360-pixel workout inputs, final-set techniques, exercise variations, canonical Auto-fix persistence and the service-worker update lifecycle.

Final local verification passed on October 1, 2026:

| Check | Result |
|---|---|
| Release/source integrity | Passed; 72 offline entries and 50 authored JavaScript files parse |
| Generation contracts | Passed all 8 gates, including 71 routes and 500 wizard feasibility checks |
| Adaptation contracts | Passed all 15 gates, including the new executable synchronization regression |
| Quality contracts | Passed all 3 gates, including 44 generated programs and 8 longitudinal simulations covering 99 weeks |
| Browser integrity | Passed all 4 gates: service-worker update, volume Auto-fix, workout prescription and actual edited-workout saving |

Browser checks use Chromium with phone-sized viewports, not physical Android/iOS certification.

## Remaining boundaries

Older logs lacking a prescription snapshot cannot reconstruct when a historical user edit occurred; their fallback uses the current canonical projection and retains conservative effort handling. Current plans and performed history remain readable. Difficult generation remains synchronous, and App remains a large module. Dose landmarks and clock estimates are models, not new physiological evidence. Constrained plans may still report honest partial/unable repairs. The release does not suppress those findings or re-enable a retired generator.
