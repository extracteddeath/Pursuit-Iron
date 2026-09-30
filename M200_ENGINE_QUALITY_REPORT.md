# M200 — Regional dose preservation

Pursuit Iron 4.0.0, build 790. Engine 0.64.1.

Baseline: deployed M199 build 789, commit `725219098cf883fca8d464feb2528fc1a0336bb3`.

## Problem and change

The realizer satisfied lat and upper-back accumulation floors, then the final recoverable-dose cleanup removed the same work using the coarse back ledger. The seed-199 ULPPL example reached the shell with only 4.6 upper-back and 7.2 lat effective sets in its lowest working week, while the engine audit passed.

Cleanup now preserves the achieved regional reserve before accepting a subtractive change. Each required region retains the smaller of its current dose and the existing public base target, so a pre-existing deficit cannot deepen. Strength work is protected by the existing role rules. Excess collateral volume can still be reduced when the regional floors are preserved, and the ordinary final engine audit remains authoritative.

The cycle regression also exposed an equipment mismatch: generic machine eligibility could select Seated Calf Raise when its specific calf-machine toggle was off. Standalone and cycle adapters now share the shell's equipment registration, and the request excludes engine identities whose mapped shell exercise cannot be performed. This keeps selection, audit, and display on the same available exercise instead of failing during catalog mapping.

## Evidence

A comparison using the actual 357-exercise shell catalog covered 37 standalone routes. All 14 standard high-capacity accumulation routes retained passing engine audits and cleared their regional warnings: 29 warnings before, 0 after. All 37 final plans built, and every sampled working week fit its session maximum. The seed-199 ULPPL example retained 24 exercise slots while clearing both back-region warnings.

The new regression covers 18 generation cases, four- and eight-week blocks, sessions without supersets, useful overflow cleanup, immutable input, strength-anchor preservation, preservation of an already-underfilled region, and locked/adaptive powerbuilding and hypertrophy cycles.

Additional locked/adaptive powerbuilding cases use a real shell inventory without a calf machine and require every displayed exercise to use only checked equipment. The 71-route creation matrix passes with this equipment guard enabled.

Detailed comparison: `verification/m200-generation-quality-audit.json`. The existing 71-route creation matrix, M199 Auto-fix persistence browser check, full release integrity, and production PWA checks gate publication.

## Next findings from the audit

| Area | Observed behavior | Next improvement |
|---|---|---|
| Short sessions | Several s60 plans pass the coarse audit but show phase-adjusted regional warnings. | Make working-week guidance account for actual capacity and consolidation. |
| Minimalist intent | Reduced-dose PPL still faces the high-capacity public target contract. | Align minimalist generation and display targets. |
| Locked cycle carryover | Existing PPL powerbuilding strength blocks can retain 12 calf sets against a displayed 9.6-set upper boundary. | Reconcile phase-specific dose while retaining the locked exercise roster. |

These findings remain visible in the audit and are separate follow-up work.
