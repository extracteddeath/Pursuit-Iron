# M175 longitudinal adaptation memory

App **3.225.0**, build **781**, Pursuit Engine **0.63.2**.

M175 closes a multi-block adaptation-memory gap shared by standalone blocks and adaptive cycles. The immutable base request remains the source of stable program settings, but exercise avoidances now carry forward as longitudinal memory.

## Behavior

- User-banned exercises remain banned across later adapted blocks.
- Exercises diagnosed as poor fit remain avoided beyond only the next block.
- Newly diagnosed poor-fit exercises are added cumulatively.
- Adaptive cycle transitions receive explicit `replaceExerciseIds`, so continuity cannot preserve a diagnosed poor-fit movement.
- Temporary fatigue-driven capacity scaling is not persisted as permanent memory.

## Validation

Focused M175 persistence/parity tests plus the full production release suite must pass before publication. Physical Android interaction certification remains separate.
