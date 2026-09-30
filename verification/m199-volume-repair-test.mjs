import assert from 'node:assert/strict';
import fs from 'node:fs';
import { performance } from 'node:perf_hooks';
import { EXERCISES, computeCell, weeklyVolume, weeklySubVolume, volumeAudit, dayMuscleBreakdown, plannedWeek, distributeVolBias } from '../modules/App.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter-capacity.js';
import { nextProgramToShellProgram } from '../modules/next-engine/app-shell-adapter.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { createTrainingSetEvents } from '../modules/next-engine/events.js';
import { deriveMuscleLedger } from '../modules/next-engine/ledgers.js';
import { estimateSessionMinutes } from '../modules/next-engine/realizer.js';
import { auditProgram } from '../modules/next-engine/arbiter.js';
import { publicRegionContribution, directlyTargetsPublicRegion } from '../modules/next-engine/public-mev.js';
import { captureShellVolumeSnapshot, auditShellVolume, repairShellVolume } from '../modules/next-engine/volume-repair.js';

const clone = x => JSON.parse(JSON.stringify(x));
const map = createExerciseMap([]);
const config = { name: 'M199 volume repair', split: 'ulppl', days: 5, session: 's90', goal: 'both', experience: 'intermediate', weeks: 6,
    deload: true, equipment: ['barbell', 'rack', 'dumbbell', 'bench', 'cable', 'machine', 'smith', 'pullup', 'legpress'], barbellCap: 3 };
const generated = generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 199 }).program;
const count = p => p.days.reduce((n, d) => n + d.exercises.length, 0);

// The regression is functional: the old button's chosen adjustment is invisible to the Next cell.
const old = clone(generated);
const rowDay = old.days.find(d => d.exercises.some((_, slot) => {
    const id = old.overrides[`${d.id}:${slot}`].nextExerciseId;
    return publicRegionContribution(map.get(id), 'upper_back') === 1;
}));
const rowSlot = rowDay.exercises.findIndex((_, slot) => publicRegionContribution(map.get(old.overrides[`${rowDay.id}:${slot}`].nextExerciseId), 'upper_back') === 1);
const oldSets = computeCell(old, rowDay, rowDay.exercises[rowSlot], rowSlot, 6).sets;
distributeVolBias(old, { upper_back: 2 });
assert.equal(computeCell(old, rowDay, rowDay.exercises[rowSlot], rowSlot, 6).sets, oldSets);

const latRow = [...map.values()].find(d => d.legacyPart === 'lats' && d.movementFamily === 'horizontal_pull');
assert.ok(latRow);
assert.equal(publicRegionContribution(latRow, 'lats'), 1);
assert.equal(publicRegionContribution(latRow, 'upper_back'), .4, 'lat-biased rows cannot receive full upper-back credit');
assert.equal(directlyTargetsPublicRegion(latRow, 'upper_back'), false);

function bridge(engine, template = generated) {
    const request = clone(template.nextEngine.request);
    engine.events = createTrainingSetEvents(engine.sessions, request.customExercises);
    engine.muscleLedger = deriveMuscleLedger(engine.events);
    engine.sessions.forEach(s => { s.estimatedMinutes = estimateSessionMinutes(s.exercises); });
    engine.audit = auditProgram(engine, request);
    assert.equal(engine.audit.result, 'pass', JSON.stringify(engine.audit.findings));
    const p = nextProgramToShellProgram(engine, template.config, EXERCISES, () => template.id);
    p.nextEngine = { ...template.nextEngine, ...p.nextEngine, program: engine, request };
    return p;
}

// Two existing upper-back movements with useful headroom: repair must not create a third setup.
const twoEngine = clone(generated.nextEngine.program);
const horizontal = twoEngine.sessions.flatMap(s => s.exercises.map(e => ({ s, e }))).filter(x => map.get(x.e.exerciseId).movementFamily === 'horizontal_pull');
if (horizontal.length === 1) {
    const source = horizontal[0];
    const destination = twoEngine.sessions.find(s => s !== source.s && ['upper', 'full', 'strength_full', 'pull'].includes(s.intent));
    assert.ok(destination);
    const extra = clone(source.e);
    const firstIsolation = destination.exercises.findIndex(e => !map.get(e.exerciseId).flags.compound);
    destination.exercises.splice(firstIsolation < 0 ? destination.exercises.length : firstIsolation, 0, extra);
    horizontal.push({ s: destination, e: extra });
}
assert.ok(horizontal.length >= 2);
// Keep the two rows on separate days. A newer selection may place two
// complementary rows on Pull; turning both into the same row there is invalid.
if (horizontal[0].s === horizontal[1].s) {
    const item = horizontal[1];
    const destination = twoEngine.sessions.find(s => s !== item.s && ['upper', 'full', 'strength_full', 'pull'].includes(s.intent));
    assert.ok(destination);
    item.s.exercises.splice(item.s.exercises.indexOf(item.e), 1);
    destination.exercises.push(item.e); item.s = destination;
}
for (const { e } of horizontal.slice(0, 2)) {
    e.exerciseId = 'cs-db-row'; e.name = 'Chest-Supported Dumbbell Row'; e.sets = 2;
}
const two = bridge(twoEngine);
assert.ok(auditShellVolume(two, EXERCISES).issues.some(i => i.region === 'upper_back'), 'fixture must start below MEV');
const frozen = JSON.stringify(two);
const start = performance.now();
const repaired = repairShellVolume(two, EXERCISES);
assert.equal(repaired.status, 'success', repaired.message);
assert.equal(repaired.after.issues.length, 0);
assert.equal(count(repaired.program), count(two), 'existing rows must absorb the shortfall');
assert.equal(JSON.stringify(two), frozen, 'repair may not mutate its input or generation snapshot');
assert.ok(performance.now() - start < 3000, 'ordinary repair must stay responsive');

// Full sessions have high-dose chest accessory work that can donate useful clock time.
const tightEngine = clone(twoEngine);
const targetSession = tightEngine.sessions.find(s => s.exercises.some(e => e.exerciseId === 'cs-db-row'));
const donor = clone(generated.nextEngine.program.sessions.flatMap(s => s.exercises).find(e => map.get(e.exerciseId).movementFamily === 'chest_adduction'));
assert.ok(donor, 'fixture needs an accessory donor');
const existingDonor = targetSession.exercises.find(e => map.get(e.exerciseId).movementFamily === 'chest_adduction');
if (existingDonor) existingDonor.sets = 7;
else { donor.sets = 7; targetSession.exercises.push(donor); }
const tight = bridge(tightEngine);
const tightWeeks = auditShellVolume(tight, EXERCISES).weeks;
tight.nextEngine.program.sessions.forEach((s, i) => { s.maxMinutes = Math.max(estimateSessionMinutes(s.exercises), ...tightWeeks.map(w => w.sessions[i].estimatedMinutes)); });
const constrained = repairShellVolume(tight, EXERCISES);
assert.equal(constrained.status, 'success', constrained.message);
assert.ok(count(constrained.program) <= count(tight) + 1, 'capacity repair may add at most one movement after exhausting valid set transfers');
assert.ok(constrained.program.nextEngine.volumeRepair.changes.some(c => c.sets.some(s => s.delta < 0 || s.remove)), 'capacity fixture must actually reallocate work');
assert.ok(constrained.after.weeks.every(w => w.sessions.every(s => s.estimatedMinutes <= s.maxMinutes)), 'every repaired week must remain inside its clock cap');

const impossible = clone(two);
impossible.nextEngine.program.sessions.forEach(s => { s.maxMinutes = 1; });
const impossibleBefore = JSON.stringify(impossible);
const unable = repairShellVolume(impossible, EXERCISES);
assert.notEqual(unable.status, 'success');
assert.match(unable.message, /Couldn’t fully repair/);
assert.equal(JSON.stringify(impossible), impossibleBefore);

// Reload and every displayed view consume the same canonical cells and attribution model.
const reloaded = clone(repaired.program);
assert.equal(volumeAudit(reloaded).issues.length, 0);
for (let week = 1; week <= 7; week++) {
    const snapshot = captureShellVolumeSnapshot(reloaded, week, EXERCISES);
    assert.deepEqual(weeklyVolume(reloaded, week), snapshot.volume);
    assert.deepEqual(weeklySubVolume(reloaded, week), snapshot.subVolume);
    const total = reloaded.days.reduce((n, d) => n + d.exercises.reduce((sum, id, slot) => sum + computeCell(reloaded, d, id, slot, week).sets, 0), 0);
    assert.equal(plannedWeek(reloaded, week).sets, total);
    const upper = reloaded.days.flatMap(d => dayMuscleBreakdown(reloaded, d, week)).filter(r => r.part === 'upper_back').reduce((n, r) => n + r.sets, 0);
    assert.ok(Math.abs(upper - snapshot.regions.upper_back) < .001);
    assert.ok('biceps_brachii' in snapshot.armCoverage.weightedSets && 'brachialis' in snapshot.armCoverage.weightedSets);
    assert.ok('wrist_flexors' in snapshot.armCoverage.weightedSets && 'wrist_extensors' in snapshot.armCoverage.weightedSets);
}
assert.equal(repairShellVolume(reloaded, EXERCISES).changed, false, 'a resolved repair must be idempotent');

for (const goal of ['hypertrophy', 'strength', 'both']) {
    const p = generateNextProgramForShell({ config: { ...config, split: 'full_body', days: 3, goal }, legacyExercises: EXERCISES, seed: 199 }).program;
    const result = repairShellVolume(p, EXERCISES);
    assert.equal(result.program.nextEngine.audit.result, 'pass', `${goal}: full engine audit must remain passing`);
    for (const s of result.program.nextEngine.program.sessions) {
        const families = s.exercises.map(e => map.get(e.exerciseId).movementFamily);
        assert.ok(families.some(f => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(f)));
        assert.ok(families.some(f => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(f)));
        assert.ok(families.some(f => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(f)));
    }
}

const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
assert.match(app, /\(onCommitProgram \|\| setProgram\)\(result.program\)/, 'Auto-fix must use canonical commit, not only the draft setter');
assert.match(app, /data-volume-repair-status/, 'failed/partial repairs need visible feedback');
console.log('PASS M199 volume repair: ignored legacy bias reproduced; row credits corrected; existing rows first; capacity reallocation; honest failure; reload/accounting consistency; arm buckets; Full Body across three goals.');
