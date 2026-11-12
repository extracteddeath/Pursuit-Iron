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
  'inc-bb-bench','incline_smith','machine_press','leg_press',
  'hack_squat','hip_thrust','cable_lateral_raise',
  'lat-pulldown','rope-pushdown',
  'cable_fly','chest_supported_row','neutral_pulldown',
  'rear_delt_fly','machine_ohp','preacher_curl',
  'cable_curl','cable_triceps','overhead_cable_triceps',
  'standing_calf','cable_crunch',
  // Preserve the original reviewed 42 in order. New source-linked
  // identities must be appended, not spliced into old persisted IDs.
  'db-bench','db-fly','db-shoulder','inc-curl','hammer',
  'face-pull','pec-deck'];
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
  ['leg_press','leg_press',[6,25],['leg_press']],
  ['hack_squat','squat',[5,20],['machine']],
  ['hip_thrust','hip_extension',[5,20],['barbell','bench']],
  ['cable_lateral_raise','lateral_raise',[8,35],['cable']],
  ['lat-pulldown','vertical_pull',[8,12],['cable']],
  ['rope-pushdown','elbow_extension',[10,15],['cable']],
  // Exact current curated web source definitions; no JSON-only/renamed aliases.
  ['cable_fly','chest_adduction',[8,30],['cable']],
  ['chest_supported_row','horizontal_pull',[5,20],['dumbbell','bench']],
  ['neutral_pulldown','vertical_pull',[5,20],['cable']],
  ['rear_delt_fly','rear_delt',[8,30],['machine']],
  ['machine_ohp','vertical_press',[5,20],['machine']],
  ['preacher_curl','elbow_flexion',[6,25],['machine']],
  ['cable_curl','elbow_flexion',[8,30],['cable']],
  ['cable_triceps','elbow_extension',[8,30],['cable']],
  ['overhead_cable_triceps','elbow_extension',[8,30],['cable']],
  ['standing_calf','calf',[6,25],['machine']],
  ['cable_crunch','core',[8,30],['cable']]
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
  '"leg-press-machine",LoadBasis.EXERCISE_OVERRIDE',
  'entry("hack_squat","squat","hack-machine"',
  '"hack-squat-machine",LoadBasis.EXERCISE_OVERRIDE',
  'entry("hip_thrust","hip_extension","barbell"',
  'BAR,LoadBasis.BARBELL_TOTAL,setOf("flat-bench"),5..20',
  'entry("cable_lateral_raise","lateral_raise","cable-handle"',
  'direct(TrainingMuscle.SIDE_DELTS),attachmentId="single-handle"',
  'entry("lat-pulldown","vertical_pull","machine-lat"',
  '"lat-pulldown-machine",LoadBasis.EXERCISE_OVERRIDE',
  'entry("rope-pushdown","elbow_extension","cable-rope"',
  'direct(TrainingMuscle.TRICEPS),attachmentId="rope"',
  'entry("cable_fly","chest_adduction","dual-handle"',
  'direct(TrainingMuscle.CHEST),attachmentId="dual-handles"',
  'entry("chest_supported_row","chest_supported_row","incline-dumbbell"',
  'DBS,LoadBasis.DUMBBELL_PER_HAND,setOf("incline-bench"),5..20',
  'entry("neutral_pulldown","vertical_pull","neutral-cable"',
  'attachmentId="neutral-grip-bar"',
  'entry("rear_delt_fly","rear_delt_fly","reverse-pec-deck"',
  '"reverse-pec-deck-machine",LoadBasis.EXERCISE_OVERRIDE',
  'entry("machine_ohp","overhead_press","shoulder-machine"',
  '"shoulder-press-machine",LoadBasis.EXERCISE_OVERRIDE',
  'entry("preacher_curl","elbow_flexion","preacher-machine"',
  '"preacher-curl-machine",LoadBasis.EXERCISE_OVERRIDE',
  'entry("cable_curl","elbow_flexion","cable-straight-bar"',
  'entry("cable_triceps","elbow_extension","cable-straight-bar"',
  'direct(TrainingMuscle.BICEPS),attachmentId="straight-bar"',
  'entry("overhead_cable_triceps","elbow_extension","overhead-cable-rope"',
  'entry("standing_calf","calf_raise","standing-machine"',
  '"standing-calf-machine",LoadBasis.EXERCISE_OVERRIDE',
  'entry("cable_crunch","trunk_flexion","cable-rope"',
  'direct(TrainingMuscle.ABS),attachmentId="rope"'
])assert.ok(source.includes(fragment),'Missing independently authored stock rule '+fragment);

// Exact active web extended IDs, practical rep ranges and source equipment
// are checked against the live canonical map, not trusted because the Kotlin
// author merely typed their names. No transfer of web fatigue coefficients.
for(const [id,family,reps,equipment] of [
  ['db-bench','horizontal_press',[8,12],['dumbbell','bench']],
  ['db-fly','chest_adduction',[12,18],['dumbbell','bench']],
  ['db-shoulder','vertical_press',[8,12],['dumbbell']],
  ['inc-curl','elbow_flexion',[8,12],['dumbbell','inclinebench']],
  ['hammer','elbow_flexion',[10,15],['dumbbell']],
  ['face-pull','rear_delt',[12,20],['cable']],
  ['pec-deck','chest_adduction',[12,20],['pecdeck']]
]) {
  const web=EXERCISE_MAP.get(id);
  assert.ok(web,'Missing actual live web source definition '+id);
  assert.equal(web.movementFamily,family,'Live movement family drift '+id);
  assert.deepEqual(web.practicalReps,reps,'Live practical reps drift '+id);
  assert.deepEqual(web.equipment,equipment,'Live gym equipment drift '+id);
  assert.equal(web.source,'legacy_v661','Missing original web source provenance '+id);
  const kotlin=source.match(new RegExp('\\bentry\\("'+id+'","[^"]+","[^"]+"'));
  assert.ok(kotlin,'Kotlin identity absent '+id);
  assert.ok(source.includes(','+reps[0]+'..'+reps[1]+',\n            "Active web'),
    'Kotlin practical rep range changed '+id);
}
assert.ok(source.includes('direct(TrainingMuscle.BRACHIALIS)'),
  'Source brachialis subregion must remain explicitly distinct from biceps and generic forearms');
assert.ok(source.includes('direct(TrainingMuscle.BICEPS)'),
  'Biceps work remains independently tracked from brachialis');

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
