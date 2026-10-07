import assert from 'node:assert/strict';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { initialPhaseForGoal, phaseLabel, phasePolicyFor, SUPPORTED_PHASES } from '../modules/next-engine/phase-policy.js';
import { shellConfigToNextRequest, NextShellAdapterError } from '../modules/next-engine/app-shell-adapter.js';
import { generateNextCycleForShell, convertProgramToNextCycleForShell, advanceNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';

const request = {
    athlete: { experience: 'intermediate', trainingAgeMonths: 24 },
    goal: {
        type: 'mixed',
        musclePriorities: { chest: 'high' },
        liftPriorities: { bench_press: 'normal' }
    },
    schedule: {
        days: [
            { day: 'monday', minMinutes: 40, maxMinutes: 60, targetExercises: 5 },
            { day: 'friday', minMinutes: 40, maxMinutes: 60, targetExercises: 5 }
        ]
    },
    equipment: { available: ['dumbbell', 'bench'], bodyweight: 'allow' },
    restrictions: { maxBarbellMovementsPerDay: 2, allowSupersets: true },
    preferences: { progressionStyle: 'double', volumeApproach: 'standard' },
    customExercises: [],
    seed: 17
};

const before = structuredClone(request);
const normalized = normalizeRequest(request);
assert.equal(normalized.goal.type, 'mixed');
assert.equal(normalized.athlete.experience, 'intermediate');
assert.equal(normalized.goal.musclePriorities.chest, 'high');
assert.equal(normalized.goal.liftPriorities.bench_press, 'normal');
assert.deepEqual(request, before, 'request normalization must remain immutable');

const alias = structuredClone(request);
alias.goal.type = ' BOTH ';
alias.athlete.experience = ' Beginner ';
alias.goal.musclePriorities.chest = ' HIGH ';
alias.goal.liftPriorities.bench_press = ' NORMAL ';
alias.schedule.days[0].day = ' Monday ';
alias.preferences.progressionStyle = ' DOUBLE ';
const normalizedAlias = normalizeRequest(alias);
assert.equal(normalizedAlias.goal.type, 'mixed');
assert.equal(normalizedAlias.athlete.experience, 'novice');
assert.equal(normalizedAlias.schedule.days[0].day, 'monday');
assert.equal(normalizedAlias.goal.musclePriorities.chest, 'high');
assert.equal(normalizedAlias.preferences.progressionStyle, 'double');

const requestError = (mutate, pattern) => {
    const candidate = structuredClone(request);
    mutate(candidate);
    assert.throws(() => normalizeRequest(candidate), pattern);
};
requestError(x => { x.goal.type = 'power'; }, /Unsupported training goal/);
requestError(x => { x.athlete.experience = 'expert'; }, /Unsupported training experience/);
requestError(x => { x.schedule.days[0].day = 'funday'; }, /Unsupported training day/);
requestError(x => { x.goal.musclePriorities.chest = 'extreme'; }, /Unsupported priority/);
requestError(x => { x.goal.musclePriorities.pecs = 'high'; }, /Unsupported muscle priority target/);
requestError(x => { x.goal.liftPriorities.clean = 'high'; }, /Unsupported lift priority target/);
requestError(x => { x.preferences.progressionStyle = 'guess'; }, /Unsupported progression style/);
requestError(x => { x.equipment.available = 'dumbbell'; }, /equipment availability array/);
requestError(x => { x.schedule.days[0].equipmentOverride = 'dumbbell'; }, /Equipment override/);
requestError(x => { x.customExercises = {}; }, /Custom exercises must be an array/);
console.log('PASS boundary request: valid aliases normalize deliberately; unsupported goal/experience/day/priority/style and malformed arrays fail closed.');

assert.ok(SUPPORTED_PHASES.includes('peak'));
assert.equal(initialPhaseForGoal('mixed'), 'mixed_accumulation');
assert.equal(phasePolicyFor('recovery').phase, 'recovery');
assert.equal(phaseLabel('strength_accumulation'), 'Strength Accumulation');
assert.throws(() => initialPhaseForGoal('both'), /Unsupported training goal/);
assert.throws(() => phasePolicyFor('not_a_phase'), /Unsupported training phase/);
assert.throws(() => phaseLabel('not_a_phase'), /Unsupported training phase/);
console.log('PASS boundary phase: internal goals/phases have one explicit vocabulary and invalid values cannot fall through to mixed/undefined behavior.');

const shellBase = {
    days: 4,
    session: 's60',
    goal: 'both',
    experience: 'intermediate',
    split: 'full_body',
    equipment: ['dumbbell', 'bench'],
    progressionStyle: 'auto'
};
const shell = shellConfigToNextRequest(shellBase, [], [], 123);
assert.equal(shell.goal.type, 'mixed');
assert.equal(shell.athlete.experience, 'intermediate');
assert.equal(shell.schedule.days.length, 4);
assert.equal(shell.preferences.lockedSplit, 'full_body');
assert.equal(shell.preferences.progressionStyle, 'auto');

const legacyDefaults = shellConfigToNextRequest({ equipment: ['dumbbell'] }, [], [], 123);
assert.equal(legacyDefaults.goal.type, 'hypertrophy');
assert.equal(legacyDefaults.athlete.experience, 'intermediate');
assert.equal(legacyDefaults.schedule.days.length, 4);
assert.equal(legacyDefaults.preferences.lockedSplit, 'full_body');

const shellCode = (patch, code) => {
    assert.throws(
        () => shellConfigToNextRequest({ ...shellBase, ...patch }, [], [], 123),
        error => error instanceof NextShellAdapterError && error.code === code
    );
};
shellCode({ goal: 'power' }, 'NEXT_CONFIG_GOAL_INVALID');
shellCode({ experience: 'expert' }, 'NEXT_CONFIG_EXPERIENCE_INVALID');
shellCode({ split: 'mystery' }, 'NEXT_CONFIG_SPLIT_INVALID');
shellCode({ days: 1 }, 'NEXT_CONFIG_DAYS_INVALID');
shellCode({ days: 4.5 }, 'NEXT_CONFIG_DAYS_INVALID');
shellCode({ session: 'forever' }, 'NEXT_CONFIG_SESSION_INVALID');
shellCode({ progressionStyle: 'guess' }, 'NEXT_CONFIG_PROGRESSION_INVALID');
assert.throws(() => shellConfigToNextRequest(null, [], [], 1), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CONFIG_INVALID');
assert.throws(() => shellConfigToNextRequest(shellBase, 'bench', [], 1), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CONFIG_BANNED_INVALID');
assert.throws(() => shellConfigToNextRequest(shellBase, [], {}, 1), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CONFIG_EXERCISES_INVALID');
console.log('PASS boundary shell: backward-compatible omissions keep deliberate defaults; explicit invalid config no longer silently changes the requested program.');

assert.throws(() => generateNextCycleForShell(null), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CYCLE_OPTIONS_INVALID');
assert.throws(() => generateNextCycleForShell({ templateId: 'powerbuilding' }), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CYCLE_CONFIG_INVALID');
assert.throws(() => generateNextCycleForShell({ templateId: 'powerbuilding', config: shellBase, legacyExercises: {} }), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CYCLE_EXERCISES_INVALID');
assert.throws(() => convertProgramToNextCycleForShell(null), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CYCLE_CONVERSION_OPTIONS_INVALID');
assert.throws(() => convertProgramToNextCycleForShell({ legacyExercises: [] }), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CYCLE_CONVERSION_UNSUPPORTED');
assert.throws(() => advanceNextCycleForShell({}), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CYCLE_HISTORY_INVALID');
assert.throws(() => advanceNextCycleForShell({ history: [], legacyExercises: {} }), e => e instanceof NextShellAdapterError && e.code === 'NEXT_CYCLE_EXERCISES_INVALID');
console.log('PASS boundary cycles: malformed public adapter inputs fail at the boundary with stable error codes instead of incidental runtime exceptions.');
