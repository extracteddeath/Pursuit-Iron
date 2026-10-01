# M215 — Cycle duration consistency

Pursuit Iron 4.0.0 build 805 · Pursuit Engine 0.64.10.

Baseline: live M214 build 804, commit `85ec7924649f60b8f758e61fbfa680d203b79967`. Engine prescription policy remains unchanged.

## Reproduced faults

Converting an existing ten-week custom routine retained its days, prescriptions and `config.weeks: 10`, but copied the generated powerbuilding template's six-week entry duration into `cycle.blockMeta`. Cycle views preferred that stale metadata, while the real program and its late-block intensifier schedule used ten weeks. Existing settings offered only 4, 5, 6, 8 and 10 weeks, omitting the generated three-week peak and other valid existing durations.

The new behavioral regression failed on M214 with `[6, 5, 3]` where the preserved custom routine required `[10, 5, 3]`.

## Resulting behavior

`modules/program-duration.js` resolves working-week duration from the actual saved program, matching by program id. Metadata remains a fallback when the referenced program is unavailable. Cycle list/detail, Home block rows, Plan phases and progress/calendar totals share that resolution. Old saved conversions display correctly without rewriting programs or history. Deload adds a separate calendar week; ten working weeks plus a deload, then five and three working weeks, span 19 calendar weeks.

New custom-to-cycle conversion records the ten-week entry duration in both block metadata and the planned block specification. Future phase lengths stay five and three weeks. Explicit duration edits in the saved custom routine remain authoritative after reload.

Settings offer three-week blocks and retain uncommon existing lengths such as one, two, seven, nine or twelve weeks. Generated cycle blocks show the actual phase length with a clear cycle-owned explanation, avoiding a control that the existing cycle generation contract cannot apply. Custom routines retain editable duration choices.

The user confirmed the intensifiers were present at a later week. The schedule is preserved; this release does not move intensifiers earlier or overwrite explicit technique choices.

## Verification

The behavioral gate checks conversion, stale metadata, id-based lookup, cumulative ranges and deload, immutable inputs, settings durations and preservation of the ten-week custom intensifier schedule. The phone browser gate mounts the real production cycle list/detail, Home and settings components; verifies ten/five/three-week displays; saves a custom duration change; reloads it; and checks the controls at 360 pixels.

Final local checks passed on October 1, 2026:

| Check | Result |
|---|---|
| Release integrity | Passed; 73 offline entries and 51 authored JavaScript files parse |
| Engine contracts | Passed 8 generation, 16 adaptation and 3 quality gates |
| Browser integrity | Passed all five gates, including cycle duration, edited-workout saving, intensifiers, Auto-fix and service-worker update/reload |

Browser checks use phone-sized Chromium viewports. Existing custom intensifier, prescription ownership, immutable history and cycle advancement regressions remain green.
