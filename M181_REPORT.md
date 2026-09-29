# M181 Volume & Dose Intelligence

Baseline: **M180 Coach Quality Oracle**.

## Purpose

M181 hardens the relationship between available session time and productive training dose. Session-duration selections are treated as capacity bands, not quotas: the engine should use additional time when it can create useful work, but it should not manufacture redundant sets solely to make a long session look full.

The validation matrix covers 16 combinations:

- experience: intermediate, advanced
- goal: hypertrophy, mixed
- duration bands: 40–60, 60–90, 90–120, 120+ minutes

## What the baseline exposed

The allocator itself respected each muscle claim's modeled upper region, but the final realized program could materially exceed those regions. Realization and later capacity-fill passes selected exercises for one target muscle whose secondary credits also trained other muscles, while direct work for those secondary muscles was still realized in full.

Representative pre-fix examples included:

- advanced mixed 60–90: back 30.3/15, rear delts 18.3/11, biceps 21.3/12, calves 14/11, core 11/8
- advanced hypertrophy 60–90: back 28/16, rear delts 18.3/12, biceps 18.8/12
- intermediate hypertrophy 60–90: back 21/14, rear delts 13.5/10, biceps 13.5/11

Contributor tracing confirmed the problem was not simply one bad compound pair: several 4–5-set direct movements could coexist with substantial compound secondary stimulus, so the final ledger could be much higher than the allocation claims suggested.

## M181 reconciliation pass

`modules/next-engine/dose-reconciliation.js` adds a final, subtractive reconciliation step after all additive generation and repair passes are complete.

The reconciler:

- derives actual fractional and direct dose from the final realized exercises;
- uses the same muscle-prescription landmarks already used by allocation and audit;
- never reduces primary-strength, secondary-strength, or strength-support work;
- preserves preferred total dose when it was already achieved;
- otherwise preserves the modeled minimum when it was already achieved;
- never worsens a pre-existing total-dose shortfall;
- applies the same preservation logic to direct preferred/minimum dose;
- normally reduces optional non-strength work one set at a time without creating one-set fragments;
- may remove an entire optional two-set movement when keeping it would leave material overflow, the session retains at least three other movements, and all dose floors remain protected;
- is deterministic and emits its adjustments and any remaining overflow in generation diagnostics.

After reconciliation, sessions are re-finalized, training-set events and the muscle ledger are rebuilt, and the normal program arbiter runs again. M181 does not bypass or downgrade any existing safety or quality audit.

## Recoverable-dose ceiling

The M181 machine-checkable material-overflow ceiling is:

```text
modeled upper + max(1 set-equivalent, 8% of modeled upper)
```

The small allowance handles discrete exercise/set realization without accepting the 1.5–2.0× overages found by the original baseline.

The matrix initially used a looser 1.2× diagnostic gate. Tightening CI to the exact reconciler ceiling exposed one final advanced-hypertrophy edge case: back dose remained approximately 17.3 against a 16-set modeled upper, just above the 17.28 reconciliation ceiling. The cause was an optional two-set movement that could not be reduced further under the original no-one-set-fragment rule. Allowing safe whole-movement removal closed that gap while preserving all modeled dose floors.

## Long-session behavior

M181 deliberately does not require every long session to reach the lower edge of its selected time band. If every session in a 60+ minute profile finishes below the lower edge, the matrix requires at least 90% average attainment of modeled upper recoverable dose. This separates genuine unused productive capacity from appropriate saturation and prevents adding junk volume simply to occupy time.

Across increasing time bands, the matrix also rejects material regression in total realized productive dose or upper-region attainment.

## Verification

Authoritative verified engine/test head:

- branch: `m181-volume-dose-intelligence`
- commit: `f9241f6c281b85b7241c36f07aea932706c9f2e1`
- GitHub Actions run: `36578367902`
- result: **success**

That run passed:

1. the focused M181 dose-reconciliation regression;
2. the exact-ceiling 16-case duration/experience/goal matrix;
3. all affected engine regressions:
   - M167 legacy dose / cycle behavior
   - M170 engine correctness
   - M173 integration torture
   - M174 causal cycle state
   - M175 longitudinal adaptation memory
   - prescription integrity
   - progression safety
   - custom progression safety
   - full engine import

The focused reconciliation fixture additionally verifies deterministic output, protected strength-specific sets, no one-set fragments, preservation of total/direct dose floors, and no worsening of an unrelated pre-existing shortfall.

## Status

**M181 engine and automated verification work is complete on the feature branch.**

The branch remains separate from `main`; this milestone does not claim or replace the pending M179 physical Android installed-PWA certification.
