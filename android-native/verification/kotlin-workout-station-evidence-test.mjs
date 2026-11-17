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
assert.match(evidence,/history\.machineSets\[WorkoutMachineReferenceKey\(key,machineId\)\]/);
assert.match(evidence,/if\(actual!=null &&\s*actual\.machineInstanceId!=machineId\)/);
assert.doesNotMatch(evidence,/history\.sets\[|candidateWeight|proposedWeight|journalStore\.save/);

for(const workout of [generated,custom]) {
    assert.match(workout,/var stationHistoryRequested by rememberSaveable\(workoutId\)/);
    assert.match(workout,/priorWorkingKeys=/);
    assert.match(workout,/onRequestHistory=\{stationHistoryRequested=true\}/);
    assert.match(workout,/workoutPhysicalStationName\(/);
}
assert.match(generated,/issuedGymId=issuedGymId,machineRoster=machineRoster/);
assert.match(custom,/issuedGymId=current\.originalSnapshot\.stamp\.gymId/);
assert.match(labels,/NativeKotlinMachineStationLabels\.label\(/);
assert.match(labels,/it\.gymId==gymId && it\.exerciseId==exerciseId/);
assert.match(labels,/\+ \(gymId to machineId\)/);
assert.doesNotMatch(generated,/" · machine \$it"/);
assert.doesNotMatch(labels,/machineId\.replace\(|return machineId/);
console.log('PASS native workout station evidence: exact same-day A/B previews and human labels; no IDs, history borrowing, or target mutation.');
