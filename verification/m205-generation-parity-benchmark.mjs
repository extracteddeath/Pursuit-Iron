import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';
import { EXERCISES } from '../modules/App.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { auditShellVolume } from '../modules/next-engine/volume-repair.js';

// Requires the M204 commit in local git history. This optional comparative benchmark is separate
// from release gates; Actions' shallow checkout does not need to download historical source.
const baselineCommit = '5fcb3f18fb035bb82a9010dbd59cdb5c1acdd8ff';
const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'pursuit-m204-parity-'));
try {
    const archive = execFileSync('git', ['archive', baselineCommit, 'modules'], { maxBuffer: 64 * 1024 * 1024 });
    execFileSync('tar', ['-x', '-C', temporary], { input: archive });
    const baselineFile = path.join(temporary, 'modules/App.js');
    const baselineSource = fs.readFileSync(baselineFile, 'utf8');
    // Reproduce M204's BROWSER import map in Node and expose its actual production entry point.
    assert.ok(baselineSource.includes('from "./next-engine/app-shell-adapter.js"'));
    fs.writeFileSync(baselineFile, baselineSource.replace('from "./next-engine/app-shell-adapter.js"',
        'from "./next-engine/app-shell-adapter-capacity.js"') + '\nexport { generateNextWithShadow as productionGenerate };\n');
    const { productionGenerate } = await import(pathToFileURL(baselineFile).href);
    const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','ezbar','pullup','dip','kettlebell','bands','legpress','hacksquat','legext','legcurl','calfmachine'];
    const base = { name: 'Parity probe', unit: 'lb', goal: 'both', experience: 'intermediate',
        split: 'full_body', days: 3, session: 's60', weeks: 6, equipment, focus: {}, reduce: [],
        barbellCap: 3, noBodyweight: false, noSupersets: false, deload: true, progression: 'auto' };
    const cases = [
        ['Full Body 3/s60', {}], ['Full Body 5/s90', { days: 5, session: 's90' }],
        ['Upper Lower 4/s90', { split: 'upper_lower', days: 4, session: 's90' }],
        ['ULPPL 5/s90', { split: 'ulppl', days: 5, session: 's90' }],
        ['PPL 5/s90', { split: 'ppl', days: 5, session: 's90' }],
        ['Patterns 4/s90', { split: 'full_body_patterns', days: 4, session: 's90' }],
        ['Strength FB', { split: 'strength_fb', goal: 'strength', session: 's90' }],
        ['Hypertrophy advanced', { split: 'ulppl', days: 5, goal: 'hypertrophy', experience: 'advanced', session: 's90' }],
        ['No supersets', { days: 4, split: 'upper_lower', noSupersets: true, session: 's90' }],
        ['No bodyweight', { noBodyweight: true }],
        ['Minimalist PPL', { split: 'ppl', days: 5, session: 's90', volumeApproach: 'minimalist' }],
        ['Dumbbell gym', { equipment: ['dumbbell','bench'], barbellCap: null }],
        ['Primary muscle focus', { days: 5, split: 'ulppl', session: 's90', focus: { chest: 1, forearms: 1 }, reduce: ['quads'] }]
    ];
    const canonical = value => JSON.parse(JSON.stringify(value, (key, v) =>
        ['createdAt','updatedAt','engineVersion','engineSourceVersion','version','shadow'].includes(key) ? undefined : v));
    const rows = [];
    for (let index = 0; index < cases.length; index++) {
        const [label, patch] = cases[index], config = { ...base, ...patch };
        const options = { config, legacyExercises: EXERCISES, seed: 20500 + index,
            makeId: () => `parity-${index}`, canaryResearch: { enabled: false } };
        const t0 = performance.now(), before = productionGenerate(options), t1 = performance.now();
        const after = generateNextProgramForShell(options), t2 = performance.now();
        assert.deepEqual(canonical(after.program), canonical(before.program), `Executable shell changed: ${label}`);
        assert.deepEqual(canonical(after.nextProgram), canonical(before.nextProgram), `Audited engine program changed: ${label}`);
        assert.equal(after.nextProgram.audit.result, 'pass');
        assert.equal(Object.hasOwn(after.program.nextEngine, 'shadow'), false);
        const guidance = auditShellVolume(after.program, EXERCISES);
        rows.push({ label, config, seed: options.seed, effectiveSeed: after.program.seed,
            baselineMs: +(t1 - t0).toFixed(2), currentMs: +(t2 - t1).toFixed(2),
            exercises: after.program.days.reduce((n, d) => n + d.exercises.length, 0), parity: 'exact',
            guidanceIssues: guidance.issues.map(i => ({ region: i.region, status: i.status,
                actual: i.v, minimum: i.mev, upper: i.mrv, week: i.week })) });
        console.log(`PASS ${label}: ${(t1 - t0).toFixed(1)} -> ${(t2 - t1).toFixed(1)} ms; ${guidance.issues.length} visible regional guidance finding(s).`);
    }
    const total = key => rows.reduce((n, r) => n + r[key], 0);
    const report = { baselineCommit, baseline: 'M204 build794 browser capacity adapter + unconditional shadow evaluation',
        candidate: 'M205 build795 canonical adapter without retired research',
        method: 'Same process, config, requested seed, shell catalog, and fixed IDs. Exact audited-program and executable-shell comparison excludes version/timestamp/research metadata. One paired timing sample per case, baseline first; not a physical-phone or controlled multi-run measurement. Regional guidance findings are reported, not treated as passing base-engine audits.',
        cases: rows.length, baselineTotalMs: +total('baselineMs').toFixed(2),
        currentTotalMs: +total('currentMs').toFixed(2), speedup: +(total('baselineMs') / total('currentMs')).toFixed(2), rows };
    fs.writeFileSync(process.argv[2] ?? 'verification/m205-generation-parity-results.json', JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify({ ...report, rows: undefined }));
} finally {
    fs.rmSync(temporary, { recursive: true, force: true });
}
