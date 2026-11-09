import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const read=(name)=>readFileSync(root+name,'utf8');
const shared=read('NativeComponents.kt');
const generated=read('NativeKotlinWorkoutCandidateScreen.kt');
const custom=read('NativeKotlinCustomJournalScreen.kt');

// Every native workout uses the same bounded keyboard-editable fields, with
// 48dp rep/RIR steppers and responsive layout at narrow phone widths.
assert.match(shared,/internal fun NativeSetValueFields\(/);
assert.match(shared,/NativeSetEntryPolicy\.stepReps\(reps,-1,allowFailedAttempt\)/);
assert.match(shared,/NativeSetEntryPolicy\.stepReps\(reps,1,allowFailedAttempt\)/);
assert.match(shared,/NativeSetEntryPolicy\.stepRir\(rir,-1\)/);
assert.match(shared,/NativeSetEntryPolicy\.stepRir\(rir,1\)/);
assert.match(shared,/sizeIn\(minWidth=48\.dp,minHeight=48\.dp\)/);
assert.match(shared,/if\(maxWidth>=340\.dp\)/);
assert.match(shared,/if\(showLoad\)/);
assert.match(shared,/Text\(if\(showLoad && load=="0"\)/);
assert.match(shared,/isError=loadInvalid/);
assert.match(shared,/invalid=repsInvalid/);
assert.match(shared,/invalid=rirInvalid/);

// Bodyweight exercises must not ask athletes for nonexistent external load.
assert.match(generated,/NativeSetValueFields\([\s\S]*?showLoad=verifiedLoad!=null/);
assert.match(custom,/NativeSetValueFields\([\s\S]*?showLoad=verified!=null/);
// Working-set Complete is still gated by native domain validation.
assert.match(generated,/enabled=!busy && actual!=null/);
assert.match(generated,/KotlinWorkoutEntryDomain\.parse\(target,weight,reps,effort\)/);
assert.match(generated,/existing\?\.reps\?\.toString\(\)/);
assert.doesNotMatch(generated,/existing\?\.reps\?\.takeIf\{it>0\}/);
assert.match(custom,/val effort=if\(actualRir\.isBlank\(\)\)null else/);
assert.match(custom,/enabled=loadValid&&reps!=null&&effortValid/);
// These controls never save a skip or failure without its original flow.
assert.match(generated,/onSkip=\{pendingSkip=set\.target\.setKey\}/);
assert.match(generated,/onFail=\{attempt->pendingFail=set\.target\.setKey to attempt\}/);
assert.match(custom,/pendingExceptionalOutcome=KotlinJournalSetOutcome\.SKIPPED/);
assert.match(custom,/pendingExceptionalOutcome=KotlinJournalSetOutcome\.FAILED_ATTEMPT/);
// Neither renderer changes the target or applies the coach's proposed weight.
const controlBody=shared.slice(shared.indexOf('internal fun NativeSetValueFields('),
  shared.indexOf('/** Matching accessible touch targets'));
assert.doesNotMatch(controlBody,/recordWorkoutOperation|proposeLoad|changePrescription|issueWorkout/);
console.log('PASS shared native set fields: responsive steps, bodyweight, validation, and explicit journal actions');
