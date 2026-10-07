import { execFileSync } from 'node:child_process';

// Semantic regression suites survive milestone retirement. CI reads source and verifies it;
// none of these checks rewrites release identity or pushes a historical patch.
const groups = {
    generation: [
        'engine-production-fuzz', 'm196-capacity-band-regression', 'm197-capacity-monotonic', 'm198-creation-audit',
        'm199-wizard-feasibility', 'm199-volume-repair', 'm200-regional-dose', 'm202-locked-cycle-dose',
        'm206-working-week-dose'
    ],
    adaptation: [
        'm224-engine-hardening', 'm225-history-provenance', 'm226-progression-identity', 'engine-boundary-hardening', 'm222-custom-program-parity', 'm221-engine-input-stability', 'm181-dose-reconciliation', 'm182-exercise-selection-intelligence', 'm182-selection-matrix',
        'm183-progression-safeguards', 'm184-longitudinal-evidence', 'm185-phase-specialization',
        'm186-explainable-block-review', 'm189-adaptive-progression-selection', 'm189-integration-wiring', 'm234-candidate-optimization', 'm235-engine-verification',
        'm190-progression-lifecycle', 'm191-progression-explainability-ui', 'm192-progression-transition-deltas',
        'm193-cycle-progression-context', 'm194-simulation-progression-parity', 'm214-executable-prescription-sync', 'm215-cycle-duration'
    ],
    quality: [
        'm187-premium-ux',
        'm188-novice-strength-fragmentation', 'm188-production-torture'
    ]
};
const requested = process.argv.slice(2);
for (const group of requested.length ? requested : Object.keys(groups)) {
    if (!groups[group]) throw new Error(`Unknown engine verification group: ${group}`);
    for (const name of groups[group]) {
        // Two historical matrices intentionally use a matrix filename instead of a test suffix.
        const file = ['m198-creation-audit','m182-selection-matrix'].includes(name)
            ? `verification/${name}.mjs` : `verification/${name}-test.mjs`;
        execFileSync(process.execPath, ['--no-warnings','--experimental-loader','./verification/import-loader.mjs',file], { stdio: 'inherit' });
    }
    console.log(`PASS current engine contracts: ${group} (${groups[group].length} gates).`);
}
