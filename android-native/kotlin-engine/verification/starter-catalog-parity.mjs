import assert from 'node:assert/strict';
import fs from 'node:fs';
import {CURATED_EXERCISES,EXERCISE_MAP} from '../../../modules/next-engine/exercise-db.js';

/**
 * Checks the INITIAL provisional Kotlin tranche against actual curated
 * source records. Never upgrades metadataConfidence or muscle coefficients
 * to independent exercise-science claims.
 */
const source=fs.readFileSync('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt','utf8');
const declared=[...source.matchAll(/\bentry\("([a-z0-9_]+)"/g)].map(x=>x[1]);
const baseline=[
  ['barbell_bench','horizontal_press',[1,15],['barbell','bench','rack']],
  ['paused_bench','horizontal_press',[2,12],['barbell','bench','rack']],
  ['back_squat','squat',[1,12],['barbell','rack']],
  ['barbell_ohp','vertical_press',[1,12],['barbell','rack']],
  ['incline_db_press','horizontal_press',[5,20],['dumbbell','bench']],
  ['one_arm_db_row','horizontal_pull',[5,20],['dumbbell','bench']],
  ['db_lateral_raise','lateral_raise',[8,35],['dumbbell']],
  ['db_curl','elbow_flexion',[6,25],['dumbbell']],
  ['db_overhead_triceps','elbow_extension',[8,25],['dumbbell']],
  ['db_split_squat','squat',[6,20],['dumbbell']],
  ['db_rdl','hip_hinge',[5,20],['dumbbell']],
  ['db_calf_raise','calf',[8,30],['dumbbell']],
  ['bodyweight_crunch','core',[10,40],['bodyweight']]
];
const indexed=new Map(CURATED_EXERCISES.map(x=>[x.id,x]));
assert.deepEqual(declared,baseline.map(x=>x[0]),
  'Kotlin provisional tranche IDs must exactly match this review version');
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
for(const id of ['one_arm_db_row','db_rdl']) {
  assert.ok(Object.hasOwn(indexed.get(id).muscles,'back'),
    'Broad-back source changed; explicit Kotlin credit remap must be reviewed');
}
assert.ok(Object.hasOwn(indexed.get('bodyweight_crunch').muscles,'core'),
  'Core label changed; Kotlin ABS mapping needs review');
assert.ok(source.includes('engineering-provisional-v1'));
assert.ok(source.includes('indirect(TrainingMuscle.REAR_DELTS,"0.25")'));
assert.ok(source.includes('mayTransferPreviousLoad:Boolean=false'));
console.log('PASS',declared.length,'source-linked provisional starter exercises; no automatic transfer or source credit certification');
