import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const gates = [
    ['program-creation-and-feasibility', 'm198-creation-audit.mjs'],
    ['prescription-to-log-serialization', 'm214-executable-prescription-sync-test.mjs'],
    ['logged-set-progression-and-resume', 'm220-day-progression-test.mjs'],
    ['exercise-swap-identity', 'm327-session-swap-provenance-test.mjs'],
    ['multi-block-history-and-recovery', 'm323-cycle-replay-test.mjs'],
    ['continuous-start-to-finish-journey', 'm340-engine-lifecycle-journey-test.mjs'],
    ['fatigue-manual-interruption-adversarial-journey', 'm341-adversarial-lifecycle-test.mjs'],
    ['seeded-multi-week-multi-equipment-stress', 'm342-seeded-longitudinal-test.mjs'],
    ['long-horizon-multi-cycle-recovery', 'm343-multicycle-recovery-test.mjs'],
    ['recovery-exit-and-plateau-proof', 'm344-recovery-exit-plateau-test.mjs'],
    ['method-stagnation-evidence-and-reset', 'm346-plateau-evidence-test.mjs'],
    ['stagnation-method-and-volume-decisions', 'm347-stagnation-decision-test.mjs'],
    ['volume-trial-observational-response', 'm348-observed-volume-response-test.mjs']
];
const root = path.resolve(new URL('..', import.meta.url).pathname);
const revisionResult = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' });
const revision = process.env.GITHUB_SHA || (revisionResult.status === 0
    ? revisionResult.stdout.trim() : 'unknown');
const reportFile = process.argv.includes('--report')
    ? process.argv[process.argv.indexOf('--report') + 1] : null;
if (process.argv.includes('--report') && (!reportFile || reportFile.startsWith('--')))
    throw new Error('Use --report path/to/lifecycle-audit.json');
const stages = [];
for (const [stage, file] of gates) {
    const start = performance.now();
    const ran = spawnSync(process.execPath, [
        '--no-warnings', '--experimental-loader', './verification/import-loader.mjs',
        'verification/' + file
    ], { cwd: root, stdio: 'inherit' });
    stages.push({ stage, gate: file, passed: ran.status === 0,
        seconds: Math.round((performance.now() - start) / 100) / 10,
        ...(ran.signal ? { signal: ran.signal } : {}),
        ...(ran.error ? { error: ran.error.message } : {}) });
    console.log('AUDIT_GATE ' + JSON.stringify(stages.at(-1)));
}
const report = { schemaVersion: 1, audit: 'pursuit-iron-engine-lifecycle',
    revision, checkedAt: new Date().toISOString(),
    status: stages.every(s => s.passed) ? 'pass' : 'fail', stages };
if (reportFile) {
    const resolved = path.resolve(reportFile);
    fs.mkdirSync(path.dirname(resolved), { recursive: true });
    fs.writeFileSync(resolved, JSON.stringify(report, null, 2) + '\n');
}
console.log('ENGINE_AUDIT_REPORT ' + JSON.stringify(report));
if (report.status !== 'pass')
    process.exitCode = 1;
