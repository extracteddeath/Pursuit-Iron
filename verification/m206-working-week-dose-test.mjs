import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES, computeCell, plannedWeek, weeklyVolume } from '../modules/App.js';
import { generateNextProgramForShell, nextProgramToShellProgram, getNextShellCell, markUserPrescriptionOverride } from '../modules/next-engine/app-shell-adapter.js';
import { generateNextCycleForShell, convertProgramToNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { auditShellVolume, captureShellVolumeSnapshot, reconcileShellWeekDose, repairShellVolume } from '../modules/next-engine/volume-repair.js';
import { createMusclePrescriptions, normalizeRequest } from '../modules/next-engine/prescription.js';
import { publicMevContractApplies, PUBLIC_REGION_MUSCLE } from '../modules/next-engine/public-mev.js';
import { prescriptionForSimulationWeek } from '../modules/next-engine/simulation.js';

const fixtures = JSON.parse(fs.readFileSync('verification/m205-generation-parity-results.json', 'utf8')).rows;
const clone = x => structuredClone(x);
const protectedRole = role => ['primary_strength', 'secondary_strength', 'strength_support'].includes(role);
const summary = audit => audit.issues.map(i => ({ region: i.region, status: i.status, actual: i.v, minimum: i.mev, upper: i.mrv, week: i.week }));
const rawProjection = built => {
    const raw = nextProgramToShellProgram(built.nextProgram, built.program.config, EXERCISES, () => built.program.id);
    raw.nextEngine = { ...built.program.nextEngine, ...raw.nextEngine, request: clone(built.request), program: clone(built.nextProgram) };
    delete raw.nextEngine.regionalDose;
    return raw;
};
const assertClock = audit => assert.ok(!audit.missing && audit.weeks.every(w => w.sessions.every(s =>
    Number.isFinite(s.estimatedMinutes) && s.estimatedMinutes <= s.maxMinutes)), 'exact executable weeks must fit the session caps');
const assertOnlySetChanges = (before, after) => {
    assert.deepEqual(after.days, before.days, 'rounding repair must preserve the exercise roster and slot identity');
    assert.deepEqual(after.overrides, before.overrides, 'no second prescription writer');
    assert.deepEqual(after.nextEngine.program, before.nextEngine.program, 'base engine prescription stays immutable');
    for (const [key, weeks] of Object.entries(before.nextWeekPrescriptions)) for (const [week, cell] of Object.entries(weeks)) {
        const next = after.nextWeekPrescriptions[key][week];
        assert.deepEqual({ ...next, sets: cell.sets }, cell, 'rep/RIR/rest/method/technique metadata stays intact');
        if (protectedRole(cell.role) || +week > before.config.weeks)
            assert.deepEqual(next, cell, 'strength and recovery-week prescriptions are protected');
    }
};
const rows = [];
let reproduced = 0;
for (const fixture of fixtures) {
    const built = generateNextProgramForShell({ config: fixture.config, legacyExercises: EXERCISES, seed: fixture.seed,
        makeId: () => `m206-${fixture.seed}` });
    assert.equal(built.nextProgram.audit.result, 'pass', fixture.label);
    const raw = rawProjection(built), input = JSON.stringify(raw);
    const before = auditShellVolume(raw, EXERCISES);
    const repaired = reconcileShellWeekDose(raw, EXERCISES);
    assert.equal(JSON.stringify(raw), input, 'repair never mutates the original program');
    assertOnlySetChanges(raw, repaired.program);
    const after = auditShellVolume(built.program, EXERCISES);
    assert.deepEqual(after.issues, [], fixture.label + ': retain actual regional floors and ceilings');
    assertClock(after);
    assert.deepEqual(repaired.after.issues, [], fixture.label + ': the direct transaction must match generation');
    assert.deepEqual(repaired.program.nextWeekPrescriptions, built.program.nextWeekPrescriptions);
    const rerun = reconcileShellWeekDose(repaired.program, EXERCISES);
    assert.equal(rerun.changed, false, 'repair is idempotent');
    const savedFix = repairShellVolume(raw, EXERCISES);
    assert.ok(['success', 'unchanged'].includes(savedFix.status), savedFix.message);
    assert.deepEqual(auditShellVolume(JSON.parse(JSON.stringify(savedFix.program)), EXERCISES).issues, []);
    for (let week = 1; week <= fixture.config.weeks; week++) {
        const snapshot = captureShellVolumeSnapshot(built.program, week, EXERCISES);
        assert.deepEqual(weeklyVolume(built.program, week), snapshot.volume, 'volume guidance uses the executable cells');
        const total = built.program.days.reduce((sum, d) => sum + d.exercises.reduce((n, id, slot) =>
            n + computeCell(built.program, d, id, slot, week).sets, 0), 0);
        assert.equal(plannedWeek(built.program, week).sets, total, 'Home and Program consume identical prescriptions');
    }
    if (before.issues.length) { reproduced++; assert.ok(repaired.changed); }
    rows.push({ label: fixture.label, config: fixture.config, seed: fixture.seed, effectiveSeed: built.program.seed,
        rawIssues: summary(before), finalIssues: summary(after), changes: repaired.changes.length,
        status: built.program.nextEngine.regionalDose.status });
}
assert.ok(reproduced >= 3, 'regression must reproduce rounded-week failures rather than test only already-valid programs');

// Minimalist generation and guidance share the existing approach-aware muscle model. Standard
// five-day floors remain active for the ordinary approach; they are not lowered to match a plan.
const miniFixture = fixtures.find(f => f.config.volumeApproach === 'minimalist');
const mini = generateNextProgramForShell({ config: miniFixture.config, legacyExercises: EXERCISES, seed: miniFixture.seed });
assert.equal(publicMevContractApplies(mini.request, mini.nextProgram.phase), false);
const prescriptions = createMusclePrescriptions(normalizeRequest(mini.request), mini.nextProgram.phase);
for (const t of auditShellVolume(mini.program, EXERCISES).targets) {
    const p = prescriptions.find(p => p.muscle === PUBLIC_REGION_MUSCLE[t.region]);
    assert.equal(t.mev, p.minimum * (p.muscle === 'back' ? .5 : 1));
}
assert.ok(publicMevContractApplies({ ...mini.request, preferences: { ...mini.request.preferences, volumeApproach: 'standard' } }, mini.nextProgram.phase));
for (const phase of ['hypertrophy_accumulation', 'mixed_accumulation', 'strength_accumulation', 'intensification', 'peak', 'recovery'])
    for (let week = 1; week <= 6; week++) for (const s of mini.nextProgram.sessions)
        assert.ok(prescriptionForSimulationWeek(s, phase, week, 6).exercises.every(e => e.sets <= 3), 'weekly modulation respects minimalist set caps');
for (const adaptBetweenBlocks of [false, true]) {
    const cycle = generateNextCycleForShell({ templateId: 'powerbuilding', config: miniFixture.config, legacyExercises: EXERCISES,
        seed: miniFixture.seed, adaptBetweenBlocks });
    for (const p of cycle.blocks) {
        assertClock(auditShellVolume(p, EXERCISES));
        assert.ok(p.nextEngine.regionalDose, 'cycle blocks use the same finishing boundary');
        for (const w of auditShellVolume(p, EXERCISES).weeks) for (const s of w.sessions)
            assert.ok(s.exercises.every(e => e.sets <= 3), 'minimalist caps survive static/adaptive block transitions');
    }
}
const olderMini = clone(mini.program);
for (const s of olderMini.nextEngine.program.sessions) for (const e of s.exercises) delete e.workingSetCap;
const converted = convertProgramToNextCycleForShell({ program: olderMini, templateId: 'powerbuilding', legacyExercises: EXERCISES,
    seed: miniFixture.seed, adaptBetweenBlocks: false });
assert.ok(converted.blocks.slice(1).every(p => p.nextEngine.regionalDose), 'standalone conversion finalizes later blocks too');
for (const p of converted.blocks.slice(1)) for (const w of auditShellVolume(p, EXERCISES).weeks)
    assert.ok(w.sessions.every(s => s.exercises.every(e => e.sets <= 3)), 'older minimalist snapshots gain the cap at static retargeting');

// Explicit manual sets are preserved even when they prevent full repair. Impossible clocks or
// incomplete projection data must remain visible and must never be reported as success.
const built = generateNextProgramForShell({ config: fixtures[0].config, legacyExercises: EXERCISES, seed: fixtures[0].seed });
const edited = clone(built.program), editDay = edited.days[1], editSlot = editDay.exercises.length - 1;
const editKey = `${editDay.id}:${editSlot}`;
edited.overrides[editKey] = markUserPrescriptionOverride(edited.overrides[editKey], 'reps', '12-15');
edited.overrides[editKey] = markUserPrescriptionOverride(edited.overrides[editKey], 'rir', '0-1');
edited.overrides[editKey] = markUserPrescriptionOverride(edited.overrides[editKey], 'tech', 'drop set');
let editedSnapshot = captureShellVolumeSnapshot(edited, 1, EXERCISES);
let editedExercise = editedSnapshot.sessions[1].exercises[editSlot];
assert.deepEqual(editedExercise.prescription.reps, [12, 15], 'audits receive numeric rep ranges from the executable cells');
assert.deepEqual(editedExercise.prescription.rir, [0, 1], 'audits receive numeric effort ranges');
assert.equal(editedExercise.advancedTechnique.type, 'drop_set', 'clock model uses the selected technique');
edited.overrides[editKey] = markUserPrescriptionOverride(edited.overrides[editKey], 'tech', 'off');
editedExercise = captureShellVolumeSnapshot(edited, 1, EXERCISES).sessions[1].exercises[editSlot];
assert.equal(editedExercise.advancedTechnique, undefined, 'explicit Off has no technique clock charge');
const owned = rawProjection(built);
for (const d of owned.days) d.exercises.forEach((_, slot) => {
    const key = `${d.id}:${slot}`;
    owned.overrides[key] = markUserPrescriptionOverride(owned.overrides[key], 'sets', getNextShellCell(owned, d, slot, 1).sets);
    owned.overrides[key].manualGuardTag = key;
});
const ownedInput = JSON.stringify(owned), unable = reconcileShellWeekDose(owned, EXERCISES);
assert.equal(unable.changed, false);
assert.equal(unable.status, 'unable');
assert.equal(JSON.stringify(owned), ownedInput);
assert.deepEqual(unable.program.nextWeekPrescriptions, owned.nextWeekPrescriptions);
const ownedAutoFix = repairShellVolume(owned, EXERCISES);
assert.equal(JSON.stringify(owned), ownedInput, 'saved-plan repair also keeps input immutable');
for (const [oldKey, oldOverride] of Object.entries(owned.overrides)) {
    const preserved = Object.values(ownedAutoFix.program.overrides).find(o => o.manualGuardTag === oldKey);
    assert.ok(preserved, 'fallback repair cannot delete an explicitly owned exercise');
    assert.equal(preserved.sets, oldOverride.sets);
    assert.equal(preserved.nextExerciseId, oldOverride.nextExerciseId);
    assert.equal(preserved.prescriptionOwners.sets, 'user');
}
const impossible = clone(owned);
impossible.nextEngine.program.sessions.forEach(s => { s.maxMinutes = 1; });
const timed = reconcileShellWeekDose(impossible, EXERCISES);
assert.equal(timed.status, 'unable');
assert.ok(timed.timeIssues.length > 0);
const incomplete = clone(owned);
delete incomplete.nextWeekPrescriptions;
assert.equal(reconcileShellWeekDose(incomplete, EXERCISES).status, 'unable');
assert.equal(reconcileShellWeekDose({ days: [] }, EXERCISES).status, 'unable');

fs.writeFileSync('verification/m206-working-week-dose-results.json', JSON.stringify({
    cases: rows.length, reproducedRoundingCases: reproduced, rows,
    invariants: ['immutable input/base', 'roster/strength/recovery-week protection', 'manual ownership',
        'exact working-week clock', 'no false success', 'shared UI/Auto-fix accounting', 'minimalist caps and cycle entry']
}, null, 2) + '\n');
console.log(`PASS M206 working-week dose: ${rows.length} routes; ${reproduced} rounding failures reproduced and repaired; caps, cycles, manual ownership, persistence, and honest failure verified.`);
