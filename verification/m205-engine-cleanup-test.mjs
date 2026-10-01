import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES, runSelfTest, mergeStores, migrateStore, STORE_VERSION } from '../modules/App.js';
import { generateNextProgramForShell, getNextShellCell } from '../modules/next-engine/app-shell-adapter.js';
import { ENGINE_VERSION, ENGINE_COMPATIBLE_VERSIONS } from '../modules/next-engine/config.js';
import { preserveRetiredTrialData, preserveRetiredRolloutData } from '../modules/legacy-research-data.js';

const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
const index = fs.readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const coachRegression = fs.readFileSync(new URL('../modules/next-engine/coach-regression.js', import.meta.url), 'utf8');
for (const retired of ['function generateLayout(', 'function generateProgramOnce(', 'const GEN_PIPELINE',
    'generateNextWithShadow(', 'runShadowProgramForShell(', 'runShadowTransitionForShell(',
    'assignCanaryTrial(', 'runCanaryBehaviorForShell(', 'runSelectivePromotionForShell(', 'applyControlledCanaryProgram'])
    assert.equal(app.includes(retired), false, `retired execution path must stay removed: ${retired}`);
for (const retired of ['proposeCoachRegressionFixture', 'approveCoachRegressionFixture', 'evaluateCoachRegressionFixture', 'function suggestedCheck(', 'function expectedCode('])
    assert.equal(coachRegression.includes(retired), false, `retired coach-review wrapper must stay removed: ${retired}`);
assert.ok(coachRegression.includes('export function evaluateObjectiveCoachGuardrails'), 'objective production guardrails must remain');
for (const retiredProp of ['canarySummaryData', 'canaryAnalysisData', 'canaryGovernanceData', 'selectivePromotionStatusData', 'onExportCanaryDossier', 'onToggleCanary'])
    assert.equal(app.includes(retiredProp), false, `retired Settings prop must stay removed: ${retiredProp}`);
const retiredDeadExports = {
    'modules/next-engine/exercise-db.js': ['getExerciseDefinition'],
    'modules/next-engine/exercise-economy.js': ['avoidableCompoundOverlap', 'redundantCompoundPairs', 'compoundEconomyCluster'],
    'modules/next-engine/exercise-selection-intelligence.js': ['exerciseSelectionPenalty'],
    'modules/next-engine/extended-exercise-catalog.js': ['LEGACY_V661_EQUIPMENT_IDS'],
    'modules/next-engine/focus-intent.js': ['enforceExplicitFocusIntents'],
    'modules/next-engine/history.js': ['buildStrengthTrend'],
    'modules/next-engine/ledgers.js': ['ZERO_FATIGUE'],
    'modules/next-engine/loading.js': ['createLoadingInventory', 'summarizeLoadingInventory'],
    'modules/next-engine/progression-style.js': ['resolveProgressionStyle'],
    'modules/next-engine/response.js': ['diagnoseSessionResponses'],
    'modules/next-engine/setup-economy.js': ['setupZoneRevisits'],
    'modules/next-engine/simulation.js': ['defaultPowerbuildingBlocks']
};
for (const [file, symbols] of Object.entries(retiredDeadExports)) {
    const source = fs.readFileSync(new URL('../' + file, import.meta.url), 'utf8');
    for (const symbol of symbols) assert.equal(source.includes(symbol), false, `unreferenced runtime export must stay removed: ${symbol}`);
}
const retiredUnusedBindings = {
    'modules/next-engine/allocator.js': ['const byMuscle = new Map'],
    'modules/next-engine/cycle-runtime-adapter.js': ["import { generateProgram } from './generate.js'", 'bestDelta =', 'bestPenalty =', 'function blockRequest(baseRequest, phase)'],
    'modules/next-engine/phase-transition.js': ["import { generateProgram } from './generate.js'", 'function equipmentEligible('],
    'modules/next-engine/realizer.js': ['const intentMuscles = {'],
    'modules/next-engine/recovery-realization.js': ['const priority = request.goal.musclePriorities[primary]']
};
for (const [file, snippets] of Object.entries(retiredUnusedBindings)) {
    const source = fs.readFileSync(new URL('../' + file, import.meta.url), 'utf8');
    for (const snippet of snippets) assert.equal(source.includes(snippet), false, `unused engine binding must stay removed: ${snippet}`);
}
for (const retired of ['nextProgramToShellProgram', 'generateNextBlockFromShellHistory', 'advanceNextCycleForShell', 'SCALE_OK', 'PATTERN_SET', 'LOADABLE_EQUIP'])
    assert.equal(app.includes(retired), false, `proven-dead App shell binding must stay removed: ${retired}`);
const retiredPureAppLocals = [
    'const TYPE = {', 'const WEIGHT = {', 'const SPACE = {', 'const RADIUS = {',
    'const eng = typeof src === "number" ? src : 0;',
    'const { program, day, ex, slot, perf, history, weekIndex, unit } = o || {};',
    'const retroKey = hasRetro ?', 'const angArm = (sh, hOpen, hClose, sign, t) =>', 'const [id, sessions]',
    'const swapTRIR = parseRIRNum(swapCell.rir);', 'const cellTRIR = parseRIRNum(cell.rir);',
    'const anyChange = nextPlan.some(p => p.dir !== "hold");',
    'const roundTo = (v) => { const inc = unit === "lb" ? 5 : 2.5;', 'inThousand } = snap;',
    'const xOf = (d, i) =>', 'const totalOf = (b) =>', 'const doneWeeks = P.doneWeeks, pct = P.pct;'
];
for (const snippet of retiredPureAppLocals)
    assert.equal(app.includes(snippet), false, `pure dead App local must stay removed: ${snippet}`);
const equipmentStep = app.slice(app.indexOf('if (stepKey === "equipment")'), app.indexOf('if (stepKey === "constraints")'));
assert.equal(equipmentStep.includes('const hasBarbell = isBarLike(config.equipment);'), false, 'equipment-step dead hasBarbell read must stay removed');
assert.doesNotMatch(index, /app-shell-adapter/, 'Node and browser must resolve the same canonical adapter');
assert.equal(fs.existsSync(new URL('../modules/shadow-engine', import.meta.url)), false);
assert.equal(fs.existsSync(new URL('../modules/next-engine/app-shell-adapter-capacity.js', import.meta.url)), false);
for (const file of ['domain.js', 'comparison.js', 'coach-review.js', 'coach-quality-oracle.js'])
    assert.equal(fs.existsSync(new URL('../modules/next-engine/' + file, import.meta.url)), false,
        'unused review helpers and verification-only code must not ship in the runtime');
assert.equal(new Set(ENGINE_COMPATIBLE_VERSIONS).size, 5);
assert.ok(ENGINE_COMPATIBLE_VERSIONS.includes(ENGINE_VERSION), 'diagnostics must inspect the current release');
assert.ok(app.includes('new Set(ENGINE_COMPATIBLE_VERSIONS)'));

// Retiring computation must not delete evidence or immutable prescriptions from old backups.
const trials = { schemaVersion: 1, enabled: true, enrollmentId: 'old-enrollment',
    trials: [{ trialId: 'old-control', behavior: 'time_reallocation', arm: 'control', sessions: [{ historyId: 'workout-1' }] }] };
const rollouts = { schemaVersion: 1, releaseId: 'old-reviewed-release', rollbackLatched: true,
    rollbackReason: 'historical stop', programs: [{ programId: 'saved-1', arm: 'fallback', sessions: [] }] };
assert.deepEqual(preserveRetiredTrialData(trials).trials, trials.trials);
assert.equal(preserveRetiredRolloutData(rollouts).releaseId, rollouts.releaseId, 'archive must not be reset to a new rollout release');
assert.deepEqual(preserveRetiredRolloutData(rollouts).programs, rollouts.programs);
const stored = { v: STORE_VERSION, saved: [], cycles: [], history: [], canaryResearch: trials, selectivePromotionRuntime: rollouts };
assert.deepEqual(migrateStore(structuredClone(stored)).canaryResearch, trials);
const merged = mergeStores(stored, { ...stored, savedAt: 2,
    canaryResearch: { ...trials, trials: [{ trialId: 'old-treatment', behavior: 'phase_transition', arm: 'treatment', sessions: [] }] } }).data;
assert.deepEqual(new Set(merged.canaryResearch.trials.map(t => t.trialId)), new Set(['old-control', 'old-treatment']));

// Execute the app's own diagnostic, not just an assertion about its version-list source.
const config = { name: 'Current diagnostic fixture', unit: 'lb', goal: 'both', experience: 'intermediate',
    split: 'full_body', days: 3, session: 's60', weeks: 4, progression: 'auto', deload: false,
    equipment: ['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'],
    focus: {}, reduce: [], barbellCap: 3, noBodyweight: false, noSupersets: false };
const current = generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 205, makeId: () => 'current-diagnostic' }).program;
const frozen = structuredClone(current);
const result = await runSelfTest([current, { id: 'archived', engineSource: 'pursuit-next', engineSourceVersion: '0.60.0', days: [] }], [], {}, { scope: 'quick' });
assert.equal(result.skippedArchived, 1, 'only the archived fixture may be skipped; the current saved program must be checked');
assert.deepEqual(result.failures, []);
assert.ok(result.programs > 0 && result.cells > 0);
assert.deepEqual(current, frozen, 'self-test must not rewrite the saved program');
assert.equal(getNextShellCell(current, current.days[0], 0, 1).ownership.sets, 'engine');
console.log(`PASS M205 cleanup: no retired generator/research/adapter aliases; historical backup evidence preserved; current saved plan included in ${result.programs} diagnostic programs / ${result.cells} cells, zero failures.`);
