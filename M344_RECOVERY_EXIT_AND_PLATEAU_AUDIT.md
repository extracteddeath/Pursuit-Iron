# M344 — Verified recovery exit and progression plateau journey

M343 proved early fatigue causes an inserted recovery block, but stopped before proving a **successful exit**. M344 exercises actual workout logging to supply favorable recovery evidence, then invokes the production cycle advancement API and checks all resulting contracts.

For both the 4-day upper/lower and 3-day equipment-restricted full-body profiles, it checks: original fatigue evidence enters a real recovery block; the recovery programme has its own saved workout identities; the engine waits for repeated favorable recovery exposures; the recovered athlete resumes the reserved strength phase with the original preview ID; recovery-specific capacity reduction does not silently carry into normal training; and the plan remains internally executable and within session budget.

A second scenario records three comparable plateau exposures below the rep ceiling, ensuring no unearned load increase. It then changes progression method from double to dynamic and checks that prior training history remains immutable and the new suggestion does not falsely reward past incomplete progression.

Each new test is part of required integration contracts and the one-command lifecycle audit. The deterministic replay command is:

    M344_SEED=34411 M344_REPRO_DIR=/tmp/engine-repros node verification/m344-recovery-exit-plateau-test.mjs

The complete CI report uses:

    node scripts/audit-training-lifecycle.mjs --report verification/engine-lifecycle-audit.json

These are algorithmic and storage simulation contracts; separate browser/physical Android checks remain necessary for real-process interruption certification.
