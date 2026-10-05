# M221 — engine input stability

Pursuit Iron 4.0.0 build 811 · Pursuit Engine 0.64.11.

The current interface remains intact. This release hardens equipment, history and workout-target inputs without rewriting saved programs or logged performance.

## Reproduced defects

- A null plate record or a string in an equipment-load list threw during normalization.
- An explicitly empty dumbbell or exact-load list silently became the default inventory, allowing advice for weights the athlete did not declare.
- A plate quantity of 100,000,000 stalled a simple next-load query; the pre-fix reproduction exceeded a two-second process deadline.
- A non-finite rep/RIR entry could produce a NaN strength estimate and prevent a later valid set from supplying the best estimate.
- A blank rep range became `[0, 0]` and produced three zero-rep working targets instead of the existing 8–12 default.

## Changes

Equipment normalization tolerates malformed records, preserves explicit empty lists and normalizes overrides before selecting a load. Missing fields retain existing defaults; malformed supplied loads cannot authorize a step. Increment calculations cannot return a non-finite load.

Barbell queries enumerate only reachable totals up to a query-specific ceiling. The next-load ceiling preserves the first feasible crossing above the current load. A 100,000-operation budget blocks pathological expansion without guessing a weight. Exhaustion returns no load recommendation, using the existing review path; it does not certify that the physical inventory has no heavier combination.

Strength estimates reject invalid numeric evidence and overflow while retaining valid estimates, missing-effort behavior and the high-rep safeguard. Blank or incomplete runtime ranges use the existing valid defaults, while legitimate zero RIR remains valid. Pending automatic rep reconciliation still protects completed/manual entries and their loads.

The compatibility window advances to engines 0.64.7–0.64.11, retaining the requested five-engine policy. Training-dose, rep-ceiling progression, cycle duration, custom techniques and day-specific history rules are unchanged.

## Validation

- New M221 gate reproduced the normalization failure before repair and passes afterward.
- 2,394 comparisons with independent exhaustive finite plate combinations passed across lb/metric-sized denominations and off-grid query loads.
- Huge plate quantities resolve directly; pathological fine-grained expansion and numeric overflow fail closed.
- Empty/malformed exact inventories, valid overrides, immutable inventory input, valid/invalid strength evidence and protected pending-set restoration passed.
- Full release integrity passed: 52 authored JavaScript modules parse and all 74 offline entries are present with current hashes.
- All 28 engine-contract gates passed: generation 8, adaptation 17, quality 3.
- The existing nine phone/offline gates are required on this release's pull request before merge. Their outcomes and GitHub Pages deployment are recorded in GitHub Actions.

Browser checks do not certify physical Android playback or device behavior.
