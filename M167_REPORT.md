# M167 legacy dose & standalone cycle recovery

App **3.217.0**, build **773**, Pursuit Engine **0.62.5**.

## Fixed

- SekuFit-style legacy custom programs once again read their designed role baseline and their persisted `slotBias` / `autoBias` deltas. The real Lower fixture resolves to **5 / 3 / 4 / 5 / 5 / 4 / 5 = 31 sets**, matching the saved plan/history instead of seven uniform 3-set rows.
- New custom programs without legacy bias maps still use neutral custom defaults, and M166 authored week schedules remain intact.
- Fixed standalone plans once again expose **Turn into training cycle**. A legacy/custom plan remains byte-for-byte prescription-owned as current Block 1; the current engine generates only the future blocks.
- For a bridged legacy first block, completion activates the already-audited first future Pursuit Next block. From that point forward the normal history-driven Next cycle transition path owns adaptation.

## Safety

No legacy current plan is silently regenerated merely to satisfy cycle conversion. If future Next blocks cannot be generated safely, conversion fails closed and the standalone plan remains unchanged.
