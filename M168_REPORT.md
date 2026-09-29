# M168 cycle overview + phase semantics

App **3.218.0**, build **774**, Pursuit Engine **0.62.5**.

## Fixed

- Cycle Detail no longer stacks an overall progress bar and an unlabeled segmented progress bar.
- The overview stat row no longer repeats block/week counts already stated in the header; it shows days/week and clearly labels completed history as sessions logged.
- Cycle phase display is now derived from the block phase, not the block training goal. Powerbuilding therefore displays **Hypertrophy → Strength → Peak** while the Peak block can still use the strength-goal prescription internally.
- The phase sequence is explicitly labeled **Cycle path**.

## Regression protection

- Added `verification/cycle-overview-ui-test.mjs` and wired it into release verification.
- Engine generation, progression, set prescriptions, and saved cycle data are unchanged.
