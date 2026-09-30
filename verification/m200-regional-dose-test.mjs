import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter-capacity.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { reconcileRecoverableDose } from '../modules/next-engine/dose-reconciliation.js';
import { publicMevBaseTarget, publicMevContractApplies, publicMevLedger, publicMevRequired, PUBLIC_MEV_REGIONS } from '../modules/next-engine/public-mev.js';
import { auditShellVolume } from '../modules/next-engine/volume-repair.js';

const clone = x => JSON.parse(JSON.stringify(x));
const map = createExerciseMap([]);
const base = { name: 'M200 regional dose', split: 'ulppl', days: 5, session: 's90', goal: 'both',
    experience: 'intermediate', weeks: 6, deload: true, barbellCap: 3,
    equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'smith', 'leg_press', 'pullup_bar', 'bodyweight'] };
const count = p => p.days.reduce((n, d) => n + d.exercises.length, 0);
const matrix = [
    ['Full Body 5', { split: 'full_body', days: 5 }, 19805],
    ['Full Body 6', { split: 'full_body', days: 6 }, 19806],
    ['Upper/Lower 6', { split: 'upper_lower', days: 6 }, 19809],
    ['PPL 5', { split: 'ppl', days: 5 }, 19811],
    ['PPL 6', { split: 'ppl', days: 6 }, 19812],
    ['ULPPL', {}, 19813], ['PPLUL', { split: 'pplul' }, 19814],
    ['Hybrid', { split: 'hybrid' }, 19815], ['PHAT', { split: 'phat' }, 19817],
    ['Bro', { split: 'bro' }, 19818], ['Arnold', { split: 'arnold', days: 6 }, 19819],
    ['ULA', { split: 'ula' }, 19823], ['Hypertrophy', { goal: 'hypertrophy' }, 19826],
    ['Barbell cap 1', { barbellCap: 1 }, 19832],
    ['Original seed 199', {}, 199],
    ['Four-week block', { weeks: 4 }, 199],
    ['Eight-week block', { weeks: 8 }, 199],
    ['No supersets', { noSupersets: true }, 199]
];
const rows = [];
for (const [label, patch, seed] of matrix) {
    const built = generateNextProgramForShell({ config: { ...base, ...patch }, legacyExercises: EXERCISES, seed });
    assert.equal(built.nextProgram.audit.result, 'pass', label);
    assert.ok(publicMevContractApplies(built.request, built.nextProgram.phase), label);
    const audit = auditShellVolume(built.program, EXERCISES);
    assert.deepEqual(audit.issues, [], label + ': final prescriptions must retain all public regional floors');
    assert.equal(audit.missing, false);
    assert.ok(audit.weeks.every(w => w.sessions.every(s => s.estimatedMinutes <= s.maxMinutes)), label + ': all working weeks must fit');
    rows.push({ label, slots: count(built.program), regions: Object.fromEntries(PUBLIC_MEV_REGIONS.map(r => [r, Math.min(...audit.weeks.map(w => w.regions[r]))])) });
}

// Cleanup must still remove real collateral excess and preserve strength anchors and input data.
const built = generateNextProgramForShell({ config: base, legacyExercises: EXERCISES, seed: 199 });
const request = normalizeRequest(built.request);
const sessions = clone(built.nextProgram.sessions);
const curl = sessions.flatMap(s => s.exercises).find(e => e.role === 'hypertrophy_isolation' && map.get(e.exerciseId).muscles.biceps?.credit === 1);
assert.ok(curl); curl.sets += 10;
const input = JSON.stringify(sessions);
const before = publicMevLedger(sessions, map);
const cleaned = reconcileRecoverableDose(sessions, request, built.nextProgram.phase);
const after = publicMevLedger(cleaned.sessions, map);
assert.equal(JSON.stringify(sessions), input, 'reconciliation must not mutate the input');
assert.ok(cleaned.adjustments.length > 0, 'public floor protection must not disable excess cleanup');
assert.ok(after.biceps < before.biceps, 'useful cleanup must remain active');
for (const region of PUBLIC_MEV_REGIONS.filter(r => publicMevRequired(built.request, r)))
    assert.ok(after[region] + .001 >= Math.min(before[region], publicMevBaseTarget(built.request, region)), region + ': preserve the achieved reserve');
const anchors = ss => ss.flatMap(s => s.exercises.filter(e => ['primary_strength', 'secondary_strength', 'strength_support'].includes(e.role)).map(e => [s.day, e.exerciseId, e.sets, e.prescription]));
assert.deepEqual(anchors(cleaned.sessions), anchors(sessions), 'cleanup may not modify strength prescriptions');

// A reserve not yet achieved cannot be sacrificed while cleaning up a different muscle.
const shortfall = clone(sessions);
for (const s of shortfall) for (const e of s.exercises)
    if (map.get(e.exerciseId).movementFamily === 'horizontal_pull') e.sets = 2;
const shortBefore = publicMevLedger(shortfall, map);
const shortAfter = publicMevLedger(reconcileRecoverableDose(shortfall, request, built.nextProgram.phase).sessions, map);
assert.ok(shortBefore.upper_back < publicMevBaseTarget(built.request, 'upper_back'));
assert.ok(shortAfter.upper_back + .001 >= shortBefore.upper_back, 'do not deepen an existing regional shortfall');

// Generation and cycle entry share the same final dose cleanup. Locked and adaptive blocks must build.
for (const adaptBetweenBlocks of [false, true]) {
    for (const [templateId, goal] of [['powerbuilding', 'both'], ['hypertrophy_spec', 'hypertrophy']]) {
        let n = 0;
        const cycle = generateNextCycleForShell({ templateId, config: { ...base, goal }, legacyExercises: EXERCISES,
            seed: 19908, adaptBetweenBlocks, makeId: () => 'm200-' + templateId + '-' + adaptBetweenBlocks + '-' + (++n) });
        assert.ok(cycle.blocks.every(b => b.nextEngine.program.audit.result === 'pass'));
        const growthBlocks = cycle.blocks.filter(b => publicMevContractApplies(b.nextEngine.request, b.nextEngine.program.phase));
        assert.ok(growthBlocks.length);
        for (const block of growthBlocks) assert.deepEqual(auditShellVolume(block, EXERCISES).issues, [], templateId + ': growth block must retain its regional floors');
    }
}
fs.writeFileSync('verification/m200-regional-dose-results.json', JSON.stringify({ cases: rows, collateralCleanup: { before, after, adjustments: cleaned.adjustments.length } }, null, 2) + '\n');
console.log('PASS M200 regional dose: ' + rows.length + ' creation cases; all working-week regional floors; real overflow cleanup; immutable input; strength anchors; pre-existing shortfalls; locked/adaptive cycles.');
