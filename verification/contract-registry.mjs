import fs from 'node:fs';
import path from 'node:path';

export const contractGroups = Object.freeze({
    generation: Object.freeze([
        'm196-capacity-band-regression-test.mjs',
        'm197-capacity-monotonic-test.mjs',
        'm198-creation-audit.mjs',
        'm199-wizard-feasibility-test.mjs',
        'm199-volume-repair-test.mjs',
        'm200-regional-dose-test.mjs',
        'm202-locked-cycle-dose-test.mjs',
        'm206-working-week-dose-test.mjs'
    ]),
    adaptation: Object.freeze([
        'm235-engine-verification-test.mjs',
        'm234-candidate-optimization-test.mjs',
        'm233-semantic-exercise-graph-test.mjs',
        'm232-live-autoregulation-test.mjs',
        'm231-athlete-response-test.mjs',
        'm230-realizer-decomposition-test.mjs',
        'm229-domain-contracts-test.mjs',
        'm228-canonical-boundary-test.mjs',
        'm224-engine-hardening-test.mjs',
        'm225-history-provenance-test.mjs',
        'm226-engine-boundary-integrity-test.mjs',
        'm227-request-canonicalization-test.mjs',
        'm222-custom-program-parity-test.mjs',
        'm221-engine-input-stability-test.mjs',
        'm181-dose-reconciliation-test.mjs',
        'm182-exercise-selection-intelligence-test.mjs',
        'm182-selection-matrix.mjs',
        'm183-progression-safeguards-test.mjs',
        'm184-longitudinal-evidence-test.mjs',
        'm185-phase-specialization-test.mjs',
        'm186-explainable-block-review-test.mjs',
        'm189-adaptive-progression-selection-test.mjs',
        'm189-integration-wiring-test.mjs',
        'm190-progression-lifecycle-test.mjs',
        'm191-progression-explainability-ui-test.mjs',
        'm192-progression-transition-deltas-test.mjs',
        'm193-cycle-progression-context-test.mjs',
        'm194-simulation-progression-parity-test.mjs',
        'm214-executable-prescription-sync-test.mjs',
        'm215-cycle-duration-test.mjs'
    ]),
    quality: Object.freeze([
        'm180-coach-quality-oracle-test.mjs',
        'm180-coach-quality-matrix.mjs',
        'm187-premium-ux-test.mjs',
        'm188-novice-strength-fragmentation-test.mjs',
        'm188-production-torture-test.mjs'
    ]),
    integration: Object.freeze([
        'm265-worker-generation-test.mjs',
        'm237-ci-supply-chain-test.mjs',
        'm236-verification-topology-test.mjs',
        'm236-runtime-reachability-test.mjs',
        'custom-progression-safety-test.mjs',
        'cycle-overview-ui-test.mjs',
        'engine-authority-test.mjs',
        'full-engine-import-test.mjs',
        'history-edit-integrity-test.mjs',
        'history-volume-integrity-test.mjs',
        'm167-legacy-dose-cycle-test.mjs',
        'm170-engine-correctness-test.mjs',
        'm171-transaction-context-test.mjs',
        'm172-actual-strength-baseline-test.mjs',
        'm173-integration-torture-test.mjs',
        'm173-pwa-resilience-test.mjs',
        'm174-causal-cycle-state-test.mjs',
        'm175-longitudinal-adaptation-memory-test.mjs',
        'm176-workout-restore-hardening-test.mjs',
        'm177-history-merge-integrity-test.mjs',
        'm201-workout-prescription-test.mjs',
        'm204-prescription-ownership-test.mjs',
        'm205-engine-cleanup-test.mjs',
        'm217-android-lifecycle-test.mjs',
        'm219-ui-integration-test.mjs',
        'm220-day-progression-test.mjs',
        'prescription-integrity-test.mjs',
        'programs-test.mjs',
        'progression-safety-test.mjs',
        'set-display-integrity-test.mjs',
        'shell-progression-safety-test.mjs',
        'swap-scroll-integrity-test.mjs'
    ])
});

export const browserShards = Object.freeze({
    lifecycle: Object.freeze([
        'm178-pwa-update-browser-test.mjs',
        'm217-android-lifecycle-browser-test.mjs',
        'm218-phone-polish-browser-test.mjs'
    ]),
    workout: Object.freeze([
        'm201-workout-prescription-browser-test.mjs',
        'm214-executable-prescription-sync-browser-test.mjs',
        'm232-live-autoregulation-browser-test.mjs'
    ]),
    program: Object.freeze([
        'm199-volume-repair-browser-test.mjs',
        'm215-cycle-duration-browser-test.mjs',
        'm220-day-progression-browser-test.mjs'
    ]),
    integration: Object.freeze([
        'm265-worker-generation-browser-test.mjs',
        'm219-ui-integration-browser-test.mjs',
        'm222-custom-program-parity-browser-test.mjs',
        'm225-percentage-workout-browser-test.mjs'
    ])
});

export const releaseTests = Object.freeze([
    'm223-icon-update-test.mjs'
]);

const specialExecutableFiles = new Set([
    'm180-coach-quality-matrix.mjs',
    'm182-selection-matrix.mjs',
    'm198-creation-audit.mjs'
]);

export function verifyContractRegistry(root = process.cwd()) {
    const verificationDir = path.join(root, 'verification');
    const owned = new Map();
    const register = (owner, file) => {
        if (owned.has(file))
            throw new Error(`Verification gate ${file} is owned by both ${owned.get(file)} and ${owner}.`);
        if (!fs.existsSync(path.join(verificationDir, file)))
            throw new Error(`Registered verification gate is missing: ${file}.`);
        owned.set(file, owner);
    };

    for (const [group, files] of Object.entries(contractGroups))
        for (const file of files) register(`contract:${group}`, file);
    for (const [shard, files] of Object.entries(browserShards))
        for (const file of files) register(`browser:${shard}`, file);
    for (const file of releaseTests)
        register('release', file);

    const executable = fs.readdirSync(verificationDir)
        .filter(file => file.endsWith('-test.mjs') || specialExecutableFiles.has(file))
        .sort();
    const unowned = executable.filter(file => !owned.has(file));
    if (unowned.length)
        throw new Error(`Executable verification files without an owner: ${unowned.join(', ')}.`);

    return Object.freeze({
        ownedCount: owned.size,
        contractCount: Object.values(contractGroups).flat().length,
        browserCount: Object.values(browserShards).flat().length,
        releaseCount: releaseTests.length
    });
}
