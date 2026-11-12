import assert from 'node:assert/strict';
import fs from 'node:fs';
import {CURATED_EXERCISES,EXERCISE_MAP} from '../../../modules/next-engine/exercise-db.js';

/**
 * Checks the INITIAL provisional Kotlin tranche against actual curated
 * source records. Never upgrades metadataConfidence or muscle coefficients
 * to independent exercise-science claims.
 */
const source=fs.readFileSync('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt','utf8');
const declared=[...source.matchAll(/\bentry\("([a-z0-9_-]+)"/g)].map(x=>x[1]);
const baseline=[
  ['barbell_bench','horizontal_press',[1,15],['barbell','bench','rack']],
  ['paused_bench','horizontal_press',[2,12],['barbell','bench','rack']],
  ['back_squat','squat',[1,12],['barbell','rack']],
  ['barbell_ohp','vertical_press',[1,12],['barbell','rack']],
  ['incline_db_press','horizontal_press',[5,20],['dumbbell','bench']],
  ['one_arm_db_row','horizontal_pull',[6,20],['dumbbell','bench']],
  ['db_lateral_raise','lateral_raise',[8,35],['dumbbell']],
  ['db_curl','elbow_flexion',[6,25],['dumbbell']],
  ['db_overhead_triceps','elbow_extension',[8,25],['dumbbell']],
  ['db_split_squat','squat',[6,20],['dumbbell']],
  ['db_rdl','hip_hinge',[5,20],['dumbbell']],
  ['db_calf_raise','calf',[8,30],['dumbbell']],
  ['bodyweight_crunch','core',[10,40],['bodyweight']],
  ['barbell_row','horizontal_pull',[3,15],['barbell']],
  ['rdl','hip_hinge',[3,15],['barbell']],
  ['conventional_deadlift','hip_hinge',[1,8],['barbell']],
  ['db_rear_delt_raise','rear_delt',[10,30],['dumbbell']],
  ['pullup','vertical_pull',[3,20],['pullup_bar','bodyweight']],
  ['leg_extension','knee_extension',[8,30],['machine']],
  ['seated_leg_curl','knee_flexion',[6,25],['machine']]
];
const indexed=new Map(CURATED_EXERCISES.map(x=>[x.id,x]));
assert.deepEqual(declared.slice(0,baseline.length),baseline.map(x=>x[0]),
  'The first 20 source identities must remain stable for existing records');
const appended=['wrist-curl','reverse-wrist-curl',
  'inc-bb-bench','incline_smith','machine_press','leg_press'];
assert.deepEqual(declared.slice(baseline.length),appended,
  'Appended reviewed identities must not be lost or silently reordered');
assert.equal(new Set(declared).size,declared.length);
for(const [id,family,reps,equipment] of baseline) {
  const record=indexed.get(id);
  assert.ok(record,'Missing curated source definition '+id);
  assert.equal(record.movementFamily,family,'Movement family drift: '+id);
  assert.deepEqual(record.practicalReps,reps,'Practical rep drift: '+id);
  assert.deepEqual(record.equipment,equipment,'Equipment requirement drift: '+id);
  assert.equal(EXERCISE_MAP.get(id),record,
    'Curated definition no longer wins canonical web catalog: '+id);
}
// Only validate the new source-record facts. Kotlin source-specific physical
// stock, facility and muscle-credit decisions are tested independently in
// StarterExerciseTrancheTest, not copied blindly from this web metadata.
for(const [id,family,reps,equipment] of [
  ['inc-bb-bench','horizontal_press',[6,10],['barbell','inclinebench','rack']],
  ['incline_smith','horizontal_press',[5,20],['smith','bench']],
  ['machine_press','horizontal_press',[5,25],['machine']],
  ['leg_press','leg_press',[6,25],['leg_press']]
]) {
  const definition=EXERCISE_MAP.get(id);
  assert.ok(definition,'Missing active web definition '+id);
  assert.equal(definition.movementFamily,family,'Source family drift for '+id);
  assert.deepEqual(definition.practicalReps,reps,'Source rep-band drift for '+id);
  assert.deepEqual(definition.equipment,equipment,'Source equipment drift for '+id);
}
for(const fragment of [
  'entry("inc-bb-bench","bench_press","incline-barbell"',
  'BAR,LoadBasis.BARBELL_TOTAL,setOf("incline-bench","rack"),6..10',
  'entry("incline_smith","bench_press","incline-smith"',
  '"incline-smith-station",LoadBasis.SMITH_TOTAL',
  'entry("machine_press","machine_chest_press","chest-press"',
  '"chest-press-machine",LoadBasis.EXERCISE_OVERRIDE',
  'entry("leg_press","leg_press","machine"',
  '"leg-press-machine",LoadBasis.EXERCISE_OVERRIDE'
])assert.ok(source.includes(fragment),'Missing independently authored stock rule '+fragment);

for(const id of ['one_arm_db_row','db_rdl','barbell_row','rdl','conventional_deadlift','db_rear_delt_raise','pullup']) {
  assert.ok(Object.hasOwn(indexed.get(id).muscles,'back'),
    'Broad-back source changed; explicit Kotlin credit remap must be reviewed');
}
assert.ok(Object.hasOwn(indexed.get('bodyweight_crunch').muscles,'core'),
  'Core label changed; Kotlin ABS mapping needs review');
assert.ok(source.includes('engineering-provisional-v1'));
assert.ok(source.includes('LEG_EXTENSION,LoadBasis.EXERCISE_OVERRIDE'));
assert.ok(source.includes('SEATED_LEG_CURL,LoadBasis.EXERCISE_OVERRIDE'));
assert.ok(source.includes('setOf("pullup-bar")'),
  'An unweighted pull-up needs an independently confirmed physical bar');
assert.ok(source.includes('direct(TrainingMuscle.GLUTES)),'), 'Conventional deadlift keeps direct glute credit; do not silently remap broad back');
assert.ok(source.includes('indirect(TrainingMuscle.REAR_DELTS,"0.25")'));
assert.ok(source.includes('mayTransferPreviousLoad:Boolean=false'));
console.log('PASS',declared.length,'source-linked provisional starter exercises; no automatic transfer or source credit certification');
