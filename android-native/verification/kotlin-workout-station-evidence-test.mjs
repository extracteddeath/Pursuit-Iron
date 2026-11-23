import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const selector=readFileSync(root+'NativeKotlinWorkoutMachineSelector.kt','utf8');
const labels=readFileSync(root+'NativeKotlinWorkoutStationEvidence.kt','utf8');
const generated=readFileSync(root+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(root+'NativeKotlinCustomJournalScreen.kt','utf8');
const evidence=readFileSync(
    'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/WorkoutMachineLastPreviewDomain.kt','utf8');

assert.match(selector,/priorHistory:WorkoutLastReferenceResult\?/);
assert.match(selector,/priorWorkingKeys:List<WorkoutReferenceKey>/);
assert.match(selector,/onRequestHistory\?\.invoke\(\)/);
assert.match(selector,/workoutStationLastCaption\(/);
assert.match(selector,/Switching does not convert or lower another machine's load/);
const selection=readFileSync(
    'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/WorkoutLastReferenceDomain.kt','utf8');
assert.match(selector,/WorkoutLastReferenceDomain\.physicalStation\(previous,selectedMachineId\)/);
assert.doesNotMatch(selector,/previous\.machineSets\.isNotEmpty\(\)/);
assert.match(selection,/fun physicalStation\(/);
assert.match(selection,/selectedMachineId==null && history\.machineSets\.isEmpty\(\)/);
assert.match(selection,/history\.machineSets\.filter/);
assert.match(selection,/it\.actual\?\.machineInstanceId==null/);
assert.match(evidence,/history\.machineSets\[WorkoutMachineReferenceKey\(key,machineId\)\]/);
assert.match(evidence,/if\(actual!=null &&\s*actual\.machineInstanceId!=machineId\)/);
assert.doesNotMatch(evidence,/history\.sets\[|candidateWeight|proposedWeight|journalStore\.save/);

for(const workout of [generated,custom]) {
    assert.match(workout,/var stationHistoryRequested by rememberSaveable\(workoutId\)/);
    assert.match(workout,/priorWorkingKeys=/);
    assert.match(workout,/onRequestHistory=\{stationHistoryRequested=true\}/);
    assert.match(workout,/nativeSavedSetCaption\(/);
}
assert.match(generated,/issuedGymId=issuedGymId,machineRoster=machineRoster/);
assert.match(custom,/issuedGymId=current\.originalSnapshot\.stamp\.gymId/);
assert.match(labels,/NativeKotlinMachineStationLabels\.label\(/);
assert.match(labels,/fun nativeSavedSetCaption\(/);
assert.match(labels,/val observed=kotlinActualLoadLabel\(actual,issuedLoad\)/);
assert.match(labels,/"Load not reported" -> "load not reported"/);
assert.match(generated,/target\.load,target\.exerciseId,issuedGymId,machineRoster/);
assert.match(custom,/target,exerciseId,issuedGymId,machineRoster/);
assert.doesNotMatch(labels,/else "bodyweight"/);

assert.match(labels,/workoutPhysicalStationName\(/);
assert.match(labels,/parts\+=.*actual\.reps.*reps/);
assert.match(labels,/it\.gymId==gymId && it\.exerciseId==exerciseId/);
assert.match(labels,/\+ \(gymId to machineId\)/);
assert.doesNotMatch(generated,/" · machine \$it"/);
assert.doesNotMatch(labels,/machineId\.replace\(|return machineId/);
console.log('PASS native workout station evidence: exact same-day A/B previews and human labels; no IDs, history borrowing, or target mutation.');
