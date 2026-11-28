import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/engine-api.js';
import { shellConfigToNextRequest } from '../modules/next-engine/app-shell-adapter.js';
import { requestForBlock, generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { createStrengthClaims, normalizeRequest } from '../modules/next-engine/prescription.js';
import { generateProgram } from '../modules/next-engine/generate.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';

// Full commercial gym, real five-day ULPPL source fixture. Unlike unit-only
// scoring tests, this audits the actual built weekly exercise/phase outputs.
const fixtures = JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json', import.meta.url)));
const config = { ...fixtures.rows[3].config, goal: 'both', split: 'ulppl', days: 5,
    // The catalog distinguishes a flat bench from an adjustable/incline bench.
    equipment: [...new Set([...fixtures.rows[3].config.equipment, 'inclinebench'])] };
const original = shellConfigToNextRequest(config, [], EXERCISES, 34410);
const base = normalizeRequest(original);
const before = JSON.stringify({ config, base });
const phases = ['hypertrophy_accumulation', 'strength_accumulation', 'intensification', 'peak'];
const result = [];

for (const phase of phases) {
    const request = requestForBlock(base, config, { phase, weeks: 5, split: 'ulppl' });
    assert.equal(request.goal.type, phase === 'hypertrophy_accumulation' ? 'hypertrophy' : 'strength');
    if (phase === 'hypertrophy_accumulation') {
        assert.equal(request.preferences.powerbuildingInclineVolume, undefined);
        assert.ok(Object.keys(request.goal.liftFrequency ?? {}).length === 0);
        continue;
    }
    assert.deepEqual(request.goal.liftFrequency, { bench_press: 2, back_squat: 2, deadlift: 1 },
        'Both must create distinct bench and squat practice, with a single fatigue-managed deadlift visit');
    assert.equal(request.preferences.powerbuildingInclineVolume, true);
    assert.ok(!request.preferences.avoidedExercises.includes('incline_db_press'),
        'The positive fixture must provide an actual adjustable/incline bench');
    const claims = createStrengthClaims(request, phase);
    for (const [lift, expected] of Object.entries(request.goal.liftFrequency))
        assert.equal(claims.filter(c => c.lift === lift).length, expected,
            phase + ': the phase must have real strength-claim slots, not only a scoring target');
    // Retention is activated only when a REAL preceding high-variety
    // hypertrophy program exists. Ordinary/standalone Strength must not
    // silently gain optional arm volume.
    const { program, diagnostics } = generateProgram(request, {
        phase, blockWeeks: 5,
        preservePriorExerciseVariety: phase === 'strength_accumulation'
    });
    assert.equal(program.audit.result, 'pass', phase + ': ' + JSON.stringify(program.audit).slice(0, 1000));
    const catalog = createExerciseMap(request.customExercises);
    const directArms = Object.fromEntries([
        ['biceps','elbow_flexion'],['triceps','elbow_extension']
    ].map(([muscle,family])=>[muscle,program.sessions.reduce((sum,session)=>
        sum + session.exercises.reduce((n,e)=>n+
            (catalog.get(e.exerciseId)?.movementFamily===family?e.sets:0),0),0)]));
    if(phase==='strength_accumulation') {
        // The selected five-day gym already allows useful low-fatigue arm
        // work: direct elbow flexion and extension must be visible as real
        // prescribed, logged workout slots, not credited only from rows/presses.
        assert.ok(directArms.biceps>=2,
            'A long, five-day strength block with available cable/machine curls must not drop all direct biceps work: '+JSON.stringify({directArms,plan:program.sessions.map(s=>({name:s.name,slots:s.exercises.map(e=>e.exerciseId)})),repairs:diagnostics.lowFatigueArmMaintenance}));
        assert.ok(directArms.triceps>=2,
            'An attainable strength block must retain at least one independent, low-fatigue triceps exercise: '+JSON.stringify({directArms,repairs:diagnostics.lowFatigueArmMaintenance}));
        assert.ok(diagnostics.lowFatigueArmMaintenance.every(a=>a.sets===2),
            'Optional arm retention uses small, real logged work sets, not invisible fractional sets');
        assert.ok(program.sessions.every(s=>s.exercises.filter(e=>
            ['primary_strength','secondary_strength'].includes(e.role))
            .every(e=>e.prescription.reps[0]>=1 && e.sets>=1)),
            'Direct arm maintenance must not overwrite strength-specific loads, reps or sets');
    }
    if(phase==='peak')
        assert.equal(diagnostics.lowFatigueArmMaintenance.length,0,
            'Real Peak is a distinct low-fatigue taper, not mandatory arm-accessory expansion');
    const counts = Object.fromEntries(['bench_press','back_squat','deadlift'].map(lift => [lift,
        program.sessions.filter(s => s.exercises.some(e =>
            ['primary_strength', 'secondary_strength'].includes(e.role) &&
            (catalog.get(e.exerciseId)?.liftSpecificity?.[lift] ?? 0) > .45)).length]));
    assert.ok(counts.bench_press >= 2, phase + ' lost the second bench day');
    assert.ok(counts.back_squat >= 2, phase + ' lost the second squat-capable day');
    assert.ok(counts.deadlift >= 1, phase + ' lost the hinge practice');
    assert.ok(program.sessions.every(s => s.estimatedMinutes <= s.maxMinutes + .01),
        phase + ' exceeded an actual session time limit');
    const push = program.sessions.find(s => s.intent === 'push');
    assert.ok(push, phase + ' must retain its Push day');
    const incline = push.exercises.find(e => ['incline_db_press','incline_smith'].includes(e.exerciseId));
    assert.ok(incline && incline.role === 'secondary_strength',
        phase + ': an eligible incline press must be the secondary bench anchor on Push');
    result.push({ phase, counts, pushIncline: incline.exerciseId,
        minutes: program.sessions.map(s => Math.round(s.estimatedMinutes)),
        sets: program.sessions.reduce((sum, s) => sum + s.exercises.reduce((n, e) => n + e.sets, 0), 0) });
}
// A flat-only gym is not an incline gym. The secondary bench visit must
// remain real, but the generator must not invent an adjustable bench.
const flatConfig = { ...config, equipment: config.equipment.filter(id => id !== 'inclinebench') };
const flatBase = normalizeRequest(shellConfigToNextRequest(flatConfig, [], EXERCISES, 34411));
const flatRequest = requestForBlock(flatBase, flatConfig, { phase: 'strength_accumulation', weeks: 5, split: 'ulppl' });
assert.ok(flatRequest.preferences.avoidedExercises.includes('incline_db_press'));
const { program: flatProgram } = generateProgram(flatRequest, { phase: 'strength_accumulation', blockWeeks: 5 });
assert.equal(flatProgram.audit.result, 'pass');
const flatCatalog = createExerciseMap(flatRequest.customExercises);
const flatBenchDays = flatProgram.sessions.filter(s => s.exercises.some(e =>
    ['primary_strength', 'secondary_strength'].includes(e.role) &&
    (flatCatalog.get(e.exerciseId)?.liftSpecificity?.bench_press ?? 0) > .45));
assert.ok(flatBenchDays.length >= 2, 'Flat-only equipment still needs two bench-practice days');
assert.ok(!flatProgram.sessions.some(s => s.exercises.some(e =>
    ['incline_db_press', 'incline_smith'].includes(e.exerciseId))),
    'Flat-only equipment must not be assigned an incline bench exercise');

// A block which changes the five-day baseline to four days must not keep
// the five-day hybrid-specific frequency claims or incline bonus.
const resized = requestForBlock(base, config,
    { phase: 'strength_accumulation', weeks: 5, days: 4, split: 'upper_lower' }, EXERCISES);
assert.equal(resized.schedule.days.length, 4);
assert.equal(resized.preferences.powerbuildingInclineVolume, undefined);
assert.ok(Object.keys(resized.goal.liftFrequency ?? {}).length === 0);

// Athlete intent is stronger than inferred defaults, including a chosen
// one-exposure frequency and a specific OHP focus. Never silently overwrite.
const custom = structuredClone(base);
custom.goal.liftFrequency = { bench_press: 1 };
const explicit = requestForBlock(custom, config, { phase: 'strength_accumulation', weeks: 5, split: 'ulppl' });
assert.equal(explicit.goal.liftFrequency.bench_press, 1);
assert.equal(explicit.goal.liftFrequency.back_squat, 2);
const preferred = structuredClone(base);
preferred.preferences.preferredExercises = ['paused_bench'];
const preferredPhase = requestForBlock(preferred, config, { phase: 'strength_accumulation', weeks: 5, split: 'ulppl' });
assert.deepEqual(preferredPhase.preferences.preferredExercises, ['paused_bench']);
const preferredProgram = generateProgram(preferredPhase, { phase: 'strength_accumulation', blockWeeks: 5 }).program;
assert.equal(preferredProgram.audit.result, 'pass');
assert.ok(preferredProgram.sessions.find(s => s.intent === 'push')?.exercises.some(e =>
    e.exerciseId === 'paused_bench' && e.role === 'secondary_strength'),
    'An explicitly preferred eligible bench variation must beat the inferred incline default');


// Matrix: goal and topology isolation. Only eligible five-day hybrid Both
// strength phases earn the extra practice claims; no blanket rewrite of
// hypertrophy, pure-strength, four-day or six-day programs.
for (const variant of [
    { goal: 'both', split: 'pplul', days: 5, eligible: true },
    { goal: 'both', split: 'upper_lower', days: 4, eligible: false },
    { goal: 'both', split: 'ppl', days: 6, eligible: false },
    { goal: 'strength', split: 'ulppl', days: 5, eligible: false },
    { goal: 'hypertrophy', split: 'ulppl', days: 5, eligible: false }
]) {
    const cfg = { ...config, ...variant };
    const source = normalizeRequest(shellConfigToNextRequest(cfg, [], EXERCISES, 34420 + variant.days));
    for (const phase of ['strength_accumulation', 'intensification', 'peak']) {
        const req = requestForBlock(source, cfg, { phase, weeks: 5, split: variant.split });
        assert.equal(req.preferences.powerbuildingInclineVolume === true, variant.eligible,
            'Phase policy leaked across topology or athlete goal: ' + JSON.stringify(variant));
        if (variant.eligible)
            assert.equal(req.goal.liftFrequency.bench_press, 2);
        const actual = generateProgram(req, { phase, blockWeeks: 5 }).program;
        assert.equal(actual.audit.result, 'pass', phase + ' ' + JSON.stringify(variant));
        assert.ok(actual.sessions.every(s => s.estimatedMinutes <= s.maxMinutes + .01),
            'Over-budget session: ' + phase + ' ' + JSON.stringify(variant));
    }
}

// Preserve other training structures and all muscle-building phases. A
// 3-day Full Body is not allowed to invent five required lift exposures.
const threeDays = structuredClone(base);
threeDays.schedule.days = threeDays.schedule.days.slice(0, 3);
threeDays.preferences = { ...threeDays.preferences, lockedSplit: 'full_body', preferredSplit: 'full_body' };
const noChange = requestForBlock(threeDays, { ...config, split: 'full_body', days: 3 },
    { phase: 'strength_accumulation', weeks: 5, split: 'full_body' });
assert.ok(Object.keys(noChange.goal.liftFrequency ?? {}).length === 0);

// End-to-end cycle preview: report real exercise slots per day rather
// than equating an increased set total with restored movement variety.
const previewCycle = generateNextCycleForShell({
    templateId:'custom',
    config:{...config,name:'ULPPL continuity'},
    blocks:[
        {phase:'hypertrophy_accumulation',weeks:3},
        {phase:'strength_accumulation',weeks:3},
        {phase:'peak',weeks:2}
    ],
    legacyExercises:EXERCISES,seed:34410,
    makeId:(()=>{let n=0;return ()=>`m344-coverage-${++n}`})(),
    adaptBetweenBlocks:true,adaptTrainingSplit:false
});
const actualExerciseCounts=previewCycle.blocks.map(block=>({
    phase:block.nextEngine.program.phase,
    counts:block.nextEngine.program.sessions.map(session=>({
        intent:session.intent,exercises:session.exercises.length,
        minutes:Math.round(session.estimatedMinutes)
    }))
}));
assert.ok(actualExerciseCounts.every(block=>block.counts.length===5));
// This is a genuine source-preserving five-day cycle, not a fabricated
// eight-exercise toy. It must keep legitimate accessory variety alongside
// exact bench/squat/hinge practice, or refuse the phase before saving.
const priorUpper=actualExerciseCounts[0].counts.find(day=>day.intent==='upper');
const strengthUpper=actualExerciseCounts[1].counts.find(day=>day.intent==='upper');
assert.ok(priorUpper.exercises>=6,'The fixture must exercise a high-variety hypertrophy Upper day');
assert.ok(strengthUpper.exercises>=Math.ceil(priorUpper.exercises/2),
    'Same-split strength Upper must not lose over half its distinct exercise menu');
assert.ok(actualExerciseCounts[1].counts.every(day=>day.exercises>=2),
    'Real strength program must retain actual complementary movements across ULPPL');
const peakRows=previewCycle.blocks[2].nextEngine.program.sessions;
assert.ok(peakRows.every(day=>day.estimatedMinutes<=day.maxMinutes+.01),
    'Intentional Peak reduction must still obey the actual session time constraint');
console.log('M344 real five-day ULPPL exercise-count transition: '+JSON.stringify(actualExerciseCounts));
assert.equal(JSON.stringify({ config, base }), before, 'Phase generation must not mutate athlete settings or past blocks');
console.log('M344 actual Both / ULPPL phase training audit: ' + JSON.stringify(result));
console.log('PASS M344: ULPPL Both strength/peak requires repeated bench and squat with a real Push incline variant; explicit intent remains intact.');

// Explicit strength focus survives normalization and phase changes, while omitted
// competition lifts remain excluded from strength claims (not muscle training).
const selectedConfig = {...config, strengthPreferences: {
    bench_press: {priority:'primary', frequency:2, secondaryExerciseId:'paused_bench'},
    back_squat: {priority:'primary', frequency:2},
    deadlift: {priority:'excluded'}
}};
const chosen = normalizeRequest(shellConfigToNextRequest(selectedConfig, [], EXERCISES, 34461));
assert.equal(chosen.goal.liftPriorities.deadlift,'maintenance');
assert.equal(chosen.goal.liftPriorities.overhead_press,'maintenance');
assert.deepEqual(chosen.goal.liftFrequency,{bench_press:2,back_squat:2});
for(const phase of ['hypertrophy_accumulation','strength_accumulation','intensification','peak']){
    const req = normalizeRequest(requestForBlock(chosen,selectedConfig,{phase,weeks:5,split:'ulppl'}));
    const claims=createStrengthClaims(req,phase);
    assert.ok(claims.every(c=>c.lift==='bench_press'||c.lift==='back_squat'));
    assert.equal(claims.filter(c=>c.lift==='bench_press').length,2);
    assert.equal(claims.filter(c=>c.lift==='back_squat').length,2);
    assert.equal(req.preferences.preferredStrengthVariants.bench_press,'paused_bench');
}
const customStrength = normalizeRequest(requestForBlock(chosen,selectedConfig,{phase:'strength_accumulation',weeks:5,split:'ulppl'}));
const builtSelected=generateProgram(customStrength,{phase:'strength_accumulation',blockWeeks:5}).program;
assert.equal(builtSelected.audit.result,'pass',JSON.stringify(builtSelected.audit));
assert.ok(builtSelected.sessions.some(s=>s.exercises.some(e=>e.exerciseId==='paused_bench'&&e.role==='secondary_strength')));
const excludedAll = normalizeRequest(shellConfigToNextRequest({...config,strengthPreferences:{}},[],EXERCISES,34462));
assert.equal(createStrengthClaims(excludedAll,'strength_accumulation').length,0);
assert.throws(()=>normalizeRequest(shellConfigToNextRequest({...config,strengthPreferences:{
    bench_press:{priority:'primary',secondaryExerciseId:'not-a-lift'}
}},[],EXERCISES,34463)),/compatible lift/);
