# M235 — Engine Verification 2.0

M235 verifies the M234 / Build 823 / Pursuit Engine 0.65.6 candidate. It does not change training behavior, engine version, app version, cache identity, or build number.

## Verification layers

1. **Deterministic property matrix** — the established 13 generation scenarios and one alternate seed per scenario are checked for finite numeric state, valid prescriptions, positive bounded set counts, unique session/slot identities, supported phases, equipment feasibility, barbell limits, session time limits, immutable caller configuration, passing audits, and same-seed determinism.
2. **Version differential** — the original 13 Build 816 / Engine 0.64.16 projection hashes remain the compatibility baseline. Current M234 output must match every certified sessions + weekly prescriptions + cells + volume + week-plan hash unless an intentional behavior change explicitly refreshes the baseline.
3. **Historical replay** — a six-exposure owned history is replayed in forward order, reverse order, JSON round-trip form, and equivalent kg units. Unrelated-program history must remain excluded. The replay pins comparable evidence, success count, dose behavior, frequency, recovery state, capacity scale, and positive progression velocity.
4. **Mutation kills** — the test creates ephemeral source mutants beside the real modules, imports them, proves they compile, and requires the verification assertions to kill them. The first mutant removes candidate hard-constraint filtering. The second removes active-program history scoping. Temporary mutant files are deleted in the same run.
5. **Performance budgets** — the 13 certified baseline generations are measured with a 30,000 ms aggregate ceiling and an 8,000 ms per-scenario ceiling. These are regression tripwires, not phone-performance claims; the historical M205 comparison measured the same 13-case family at roughly 2.37 s total on its original environment.

## Verified evidence

Behavior-bearing M235 verification commit: `94377683c45d8449d1eec5488d76664fcb781822`.

- Verification 2.0: **26/26 deterministic property cases**, **13/13 Build 816 differentials**, stable historical replay, and **2/2 semantic mutation kills**.
- Measured generation benchmark on the GitHub Ubuntu runner: **1,852.2 ms aggregate** across the 13 certified baseline scenarios; **479.2 ms slowest scenario**.
- Permanent adaptation group: **30 gates passed**, including the complete M224–M235 chain.
- Engine contracts: generation, adaptation, and quality groups passed.
- Release integrity passed.
- Phone/offline integrity passed all 12 browser gates, including the M232 live-autoregulation gate and the longer PWA/update and Android-lifecycle gates.
- Independent engine audit passed export, standalone parity, source-identity checks, independent regressions, and artifact publication.

## Version policy

The historical differential line is Build 816 / Engine 0.64.16 and the current verified line is Build 823 / Engine 0.65.6. A 0.66.x engine does not exist in this repository yet, so M235 does not fabricate a 0.66 baseline. The next engine-minor line must add its own explicit differential/replay baseline before replacing the M235 current-line assertion.

## Promotion rule

M235 is complete only when the permanent engine-contract runner, release integrity, phone/offline integrity, and independent engine audit pass on the finalized M235 head. `main` remains unchanged until the verified engineering candidate is deliberately promoted.
