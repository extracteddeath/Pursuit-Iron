# M204 — Prescription ownership hardening

Pursuit Iron 4.0.0, build 794. Pursuit Engine 0.64.4.

Baseline: M203 build 793, commit `f1eca56f0a189ebcbdbb729a099fd4a7309c2978`.

## Why this change exists

Several older reliability defects shared the same architectural cause: more than one persistence layer could appear to own the same value. A generated week cell, an `overrides` entry, the live workout snapshot, and a view-local program copy could each contain a plausible set count, rep target, role, progression style, or edited value. Most of the time those copies agreed. When migration, block transitions, volume repair, restore, or a user edit changed only one copy, the app could later resurrect stale data.

M204 turns those implicit conventions into explicit ownership boundaries rather than adding another special-case fix.

## Ownership contract

- **Generated automatic prescriptions:** `nextWeekPrescriptions` is the executable shell projection and the audited `nextEngine.program` remains the engine source. Generated sets, reps, RIR, rest, technique, role, and progression style are not owned by ordinary metadata in `overrides`.
- **Explicit user prescription edits:** per-field `prescriptionOwners` marks only the edited fields as user-owned. User ownership wins for that field and can be cleared back to Engine ownership without disturbing unrelated metadata.
- **Manual progression mode:** sets, reps, RIR, and rest are seeded as explicitly user-owned so manual plans keep the behavior the athlete selected.
- **Legacy compatibility:** pre-M204 rest and technique edits remain user-owned because those controls already existed. Legacy manual-mode prescription values also retain ownership.
- **Live workout values:** new pending rows store `valueOwner: "prescription"`; typing/stepping a value transfers it to `valueOwner: "user"`. The old `auto` flag remains a compatibility fallback for existing live snapshots, not the primary authority marker.
- **Program persistence:** `commitProgram` resolves one program value and writes that same object to the open view and saved library. It no longer performs a saved-state update from inside a React state-updater callback.

## Removed duplicate writers

New engine-created slot overrides now contain slot identity only (`nextEngine`, `nextExerciseId`, and `legacyExerciseId`). Generated role, progression style, progression-selection metadata, and other week prescription fields are no longer mirrored there.

Volume repair refuses to alter an explicitly user-owned set count and no longer mirrors engine-generated set changes into `overrides`. Repairs write the executable week cells/audited engine state instead of creating a second set owner.

User-added engine slots similarly keep identity/ownership metadata in `overrides` while their generated prescription lives in the week-cell store.

## Verification

`verification/m204-prescription-ownership-test.mjs` proves:

- stale auto-mode override values cannot freeze sets, reps, RIR, role, or progression style;
- explicit user ownership wins field-by-field and clears cleanly back to Engine ownership;
- manual-mode ownership is explicit;
- legacy rest/technique edits remain compatible;
- live rep reconciliation honors `valueOwner` before the old `auto` flag;
- volume repair cannot mirror generated set counts into overrides or overwrite user-owned sets;
- the persistent program commit boundary does not side-effect one React state store from inside another updater.

The change also passes the M201 workout-prescription regression, engine authority, M199 volume repair, M200 regional dose, M202 locked-cycle dose, custom progression safety, history editing, M171 transaction context, M173 integration torture, M176 restore hardening, M194 simulation/progression parity, all 71 M198 creation routes, and 500 wizard-feasibility checks.
