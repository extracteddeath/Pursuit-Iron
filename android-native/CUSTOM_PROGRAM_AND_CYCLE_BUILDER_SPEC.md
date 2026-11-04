# Pursuit Iron — native custom-program and cycle builder

## Product requirement

The Kotlin/Compose replacement must create genuinely custom programs and cycles
**better than the web version**. It must not make an athlete rebuild a generic
template after selecting “Custom,” and a cycle change must never discard chosen
exercises, sets, per-exercise RIR, attachments or amplifier settings.

An athlete can create from **Blank**, **Start from a split**, **Duplicate my
program**, or **Build a training cycle**. These are entry routes into ONE
first-class, versioned athlete-owned blueprint, not divergent legacy paths.

### Native authoring flow

1. **My program**: name, goal, custom schedule (1–7 chosen weekdays), optional
   named split or arbitrary user-defined split; choose ordinary program or
   multi-block cycle. A custom plan never requires choosing Full Body/UL/PPL.
2. **Week canvas**: swipe/reorder day cards with custom labels (Upper A / Pull /
   Day 1 / etc.), purpose and estimated duration. Drag exercises within or
   between sessions; preserve stable slot identity, not array index, and
   signal when a move affects recovery or muscle volume.
3. **Exercise picker**: searchable catalog filtered by **actual confirmed
   gym equipment**, permitted variants, facilities and attachments. Display
   verified load basis (per hand, machine stack, total barbell, Smith, etc.)
   and real attainable increments before exercise insertion. Unavailable
   exercises stay explainable rather than silently substituted.
4. **Set editor**: a compact card with editable individual warmup and working
   sets; per-set load, unit/basis, reps/range, RIR, rest and warmup. One action
   copies a set or repeats a scheme. A simple-first UI exposes advanced
   controls progressively: drop set, myo-reps, rest-pause, clusters, attachments,
   superset pairing, exercise notes, and **Pinned**. Amplifiers belong to the
   exact set, never float in a generic exercise drawer.
5. **Cycle timeline**: add named blocks (Foundation, Hypertrophy, Strength,
   Peak, Deload, Testing, or custom labels), set week lengths and day/focus
   structures independently. Duplicate previous block with the complete
   exercise/variant/set detail preserved, then edit only what changes.
   Any week can override one exercise slot without rewriting other weeks or
   deleting that block's template. Visual “this week only / all weeks in block /
   future blocks” scope is mandatory.
6. **Coach audit**: live but non-blocking warnings for gym feasibility,
   estimated duration, muscle-group MEV/MAV/MRV, heavy-pattern frequency,
   recovery, movement variety, rep targets and overload. No invented
   science-default thresholds. Author-supplied policy and verified athlete
   history govern interpretation; warnings distinguish *unavailable*,
   *conflicting*, *less optimal*, and *user-chosen*.
7. **Compare and approve**: present complete per-block/weekly diff before
   accepting any adaptive coach suggestion. Select which changes to accept;
   “Keep my exercise”, “Keep this weight/rep scheme”, and Undo must be
   granular. The author can intentionally override coach preferences, but
   the app cannot pretend unsafe/unavailable equipment is verified.

### Custom ≠ static: adaptive cycle control

The athlete owns **different permissions** for changes to goal, block schedule,
phase, split, exercise choice, pinned movement deletion, attachments, supersets,
working-set counts, loads, rep targets, RIR, rest, amplifiers, notes, and
week-level overrides. Every permission defaults OFF for coach suggestions.
“Adapt my exercises” must not imply permission to remove pinned movements;
“Adapt weights” must not rewrite sets or target RIR.

Coaching can propose improved progression and phase-specific exercise
frequencies (e.g. bench twice per week in strength) without silently
halving the entire hypertrophy volume or deleting custom amplifiers.
No proposal automatically changes an active issued workout, past history,
or completed evidence. Automatic athlete acceptance is a **separate,
future, authenticated policy**; this pass always returns review-only.

### Implemented Kotlin foundation on this branch

- `CustomTrainingCycleDomain`: fully authored, deterministic `CustomTrainingCycle`
  with blocks, freeform splits, ordered weekday sessions, stable exercise slots,
  exact per-set prescriptions, pinned exercises, gym attachments, superset
  membership, set-linked amplifier protocols, and per-block week overrides.
- `review()` checks id/bounds, unique slots and days, coherent weekly
  overrides, exact load/unit/basis attainability through the verified stock
  domain, availability of chosen exercise variants/attachments, amplifier
  compatibility and meaningful superset group sizes; returns an immutable
  review copy only when valid.
- `week()` projects the **exact** authored week and only its explicitly
  modified exercise slots. It never runs a searching composer to quietly
  replace exercise order or changed volume.
- `CustomCycleTransitionDomain`: granular field-level change review with
  separate athlete-origin and coach-origin semantics and explicit
  `CustomCycleCoachPermissions`. Every proposal remains review-only.

### Release gates not yet implemented

These source-domain constructs are NOT a completed native program workflow.
Before production authority, build and test:

- Native Compose **My Program / Week / Exercise / Set / Cycle / Compare** screens
  as reusable M3 Expressive components, persistent onboarding-to-program
  navigation, responsive phone/foldable layout, accessible drag alternatives,
  animation, draft auto-save, undo and crash recovery.
- A native durable, revision-checked custom blueprint repository; diff receipts,
  optimistic concurrency, migration/export, and a separate explicit author
  approval -> Kotlin workout issuance handoff. No JS object is an approval.
- Full catalog-backed variant/attachment filtering and per-gym physical stock,
  correct training-dose/recovery/periodization coaching, efficient cloning of
  all authored fields between blocks, athlete-approved adaptation,
  chronological prior-session evidence and real RIR.
- An audited importer for existing web custom programs AND cycle plans;
  never drop a previous program's custom exercise or volume amplifiers.
- Real-world end-to-end Playwright/Compose UI and Android device tests,
  power-loss recovery, backup, long-lived history, accessibility and
  coach decision audits. No release APK solely for this domain pass.

In particular, do not conflate `READY_FOR_COACH_REVIEW` with a saved
user-approved program or with successful issuance. Both result objects
explicitly report that they cannot issue workouts or publish changes.
