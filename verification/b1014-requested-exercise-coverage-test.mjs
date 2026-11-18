import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { EXERCISES, EX_BY_ID, EQUIPMENT } from '../modules/training-domain/catalog.js';
import { EXERCISE_MAP, createExerciseCatalog, createAutomaticExerciseCatalog } from '../modules/next-engine/exercise-db.js';
import { createEngineContext } from '../modules/next-engine/engine-context.js';
import { exerciseBenchLevel, exerciseAttachmentFacet } from '../modules/exercise-variant-browse.js';

// Regression for specifically requested real-world training setups; an
// exercise name alone must not fabricate equipment, progression or ROM parity.
assert.equal(EQUIPMENT.length,40,'retain all 40 individually selectable gym gear IDs');
for (const equipment of [
  'dumbbell','barbell','ezbar','trapbar','safetybar','landmine',
  'bench','inclinebench','declinebench','preacher','pullup','dip',
  'cable','smith','bands','kettlebell','machinepress','machinelatraise',
  'machineshoulder','machinecurl','machineext','hacksquat','legpress',
  'beltsquat','hipthrustmachine','kickback','abduction','adduction',
  'pecdeck','machinepullover','machinerow','assisted','ghd','reversehyper'
]) {
  assert.ok(EQUIPMENT.some(e=>e.id===equipment),'missing selectable equipment '+equipment);
}

const expected=[
  ['high-inc-db-press','High-Incline Dumbbell Press',['dumbbell','inclinebench'],'chest'],
  ['bottom-half-db-fly','Bottom-Half Dumbbell Fly',['dumbbell','bench'],'chest'],
  ['deficit-pendlay-row','Deficit Pendlay Row',['barbell'],'upper_back'],
  ['low-to-high-face-pull','Low-to-High Face Pull',['cable'],'shoulders'],
  ['leaning-cable-ohext','Leaning Cable Overhead Triceps Extension',['cable'],'triceps'],
  ['ad-press-barbell','AD Press (Barbell)',['barbell','inclinebench','rack'],'shoulders'],
  ['ad-press-smith','AD Press (Smith Machine)',['smith','inclinebench'],'shoulders']
];
for (const [id,name,equipment,part] of expected) {
  const web=EX_BY_ID[id],engine=EXERCISE_MAP.get(id);
  assert.ok(web && engine,'both web UI and independent engine must know '+id);
  assert.equal(web.name,name);
  assert.equal(web.part,part);
  assert.deepEqual(web.equip,equipment);
  assert.equal(engine.name,name);
  assert.deepEqual(engine.equipment,equipment);
  assert.equal(engine.metadataConfidence,'provisional',
    'new exercise stimulus claims cannot be presented as independently validated');
  assert.equal(engine.equipmentAlternatives,undefined,
    'requested setup must not gain an implicit substitute');
}

// Distinct exercise IDs own separate load/progression history. Machine A/B is
// a different physical station WITHIN an exercise and is never an exercise ID.
for(const id of [
 'low-inc-bb-bench','inc-bb-bench','high-inc-bb-bench',
 'low-incline-db','inc-db-press','high-inc-db-press',
 'decline-bench','decline-db-press','smith-bench','smith-incline',
 'rope-pushdown','dual-rope-pushdown','v-bar-pushdown',
 'straight-bar-pushdown','ez-pushdown','single-pushdown','oh-cable-ext',
 'seated-db-lat-raise','seated-cable-lat-raise','cuff-cable-lat-raise',
 'single-cable-raise','leaning-lat-raise','machine-lat-raise',
 'chest-supported-lat-raise','face-pull','wide-pulldown',
 'close-pulldown','bayesian-curl','smith-ohp','bottom-half-db-fly',
 'deficit-pendlay-row','low-to-high-face-pull','leaning-cable-ohext',
 'ad-press-barbell','ad-press-smith'
]){
  assert.ok(EX_BY_ID[id],'missing user-requested web exercise '+id);
}
assert.equal(new Set(EXERCISES.map(e=>e.id)).size,EXERCISES.length,
  'the user-visible picker cannot silently collapse variant IDs');

// Incline is a BENCH or press configuration, not cable travel or body lean.
assert.equal(exerciseBenchLevel(EX_BY_ID['low-incline-db']),'low-incline');
assert.equal(exerciseBenchLevel(EX_BY_ID['inc-db-press']),'incline');
assert.equal(exerciseBenchLevel(EX_BY_ID['high-inc-db-press']),'high-incline');
assert.equal(exerciseBenchLevel(EX_BY_ID['db-bench']),'flat');
assert.equal(exerciseBenchLevel(EX_BY_ID['inc-cable-fly']),null);
assert.equal(exerciseBenchLevel(EX_BY_ID['low-to-high-face-pull']),null);
assert.equal(exerciseBenchLevel(EX_BY_ID['incline-pushup']),null);
for(const [id,attachment] of [
  ['rope-pushdown','rope'],['dual-rope-pushdown','dual-rope'],
  ['v-bar-pushdown','v-bar'],['straight-bar-pushdown','straight-bar'],
  ['ez-pushdown','ez-bar'],['single-pushdown','single-handle'],
  ['oh-cable-ext','rope']
]) assert.equal(exerciseAttachmentFacet(EX_BY_ID[id]),attachment);

// Default generated plans must remain byte-for-byte compatible with frozen
// engine snapshots. Manual Add/Swap can still reach all reviewed IDs.
const manual = createExerciseCatalog();
const automatic = createAutomaticExerciseCatalog();
for(const [id] of expected.map(([id])=>[id])) {
  assert.ok(manual.some(ex=>ex.id===id),'manual catalog lost '+id);
  assert.ok(!automatic.some(ex=>ex.id===id),'new provisional variant was auto-generated: '+id);
}
assert.equal(manual.length-automatic.length,expected.length,
  'only the seven new provisional variants may be excluded from automatic generation');
const sourceContext=createEngineContext({
  athlete:{experience:'advanced'},goal:{type:'hypertrophy'},
  schedule:{days:[{day:'monday'}]},
  equipment:{available:['dumbbell','bench','inclinebench','barbell','rack','smith','cable'],bodyweight:'allow'},
  restrictions:{maxBarbellMovementsPerDay:2},
  preferences:{avoidedExercises:[]}
});
for(const [id] of expected.map(([id])=>[id])) {
  assert.equal(sourceContext.exerciseById(id)?.id,id,
    'provisional identity must remain addressable for manual session history');
  assert.ok(!sourceContext.automaticCatalog.some(ex=>ex.id===id),
    'unreviewed variant leaked into generated planning candidates');
}
assert.equal(sourceContext.exerciseCatalog.length,manual.length,
  'full engine source retains every manually reachable built-in');


// Picker must search all source IDs, with options visible in program and
// in-workout swaps. Physical machine ID is never an exercise swap.
const app=readFileSync(new URL('../modules/App.js',import.meta.url),'utf8');
const picker=readFileSync(new URL('../modules/exercise-picker-sheet.js',import.meta.url),'utf8');
const machine=readFileSync(new URL('../modules/next-engine/machine-instances.js',import.meta.url),'utf8');
assert.match(app,/exercises: EXERCISES/);
assert.match(app,/matchesVariantFacets\(a, \{ level, attachment \}\)/);
assert.match(picker,/const matches=useMemo\(\(\)=>pool\.filter/);
assert.match(picker,/matches\.slice\(0,visibleCount\)/);
assert.doesNotMatch(picker,/\.slice\(0,100\)/);
assert.match(machine,/machineInstanceId/);
assert.match(app,/"ad-press-barbell": \{ key: "ad-press-bar"/);
assert.match(app,/"ad-press-smith": \{ key: "ad-press-smith"/);
assert.match(app,/visual === "ad-press-bar"/);
assert.match(app,/visual === "ad-press-smith"/);
assert.match(app,/visual === "chest-db-partial"/);
assert.match(app,/visual === "overhead-lean"/);
console.log('PASS web exercise request inventory, distinct setups, no false bench facets, program/workout picker authority');
