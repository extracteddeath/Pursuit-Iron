import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {EXERCISES} from '../../modules/next-engine/exercise-db.js';

// CI-only source comparison. The installable Kotlin app includes static names,
// not the legacy JavaScript exercise DB or a JS training runtime.
const source=readFileSync(
  'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt','utf8');
const labels=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseLabels.kt','utf8');
const ids=[...source.matchAll(/\bentry\("([a-z0-9_-]+)"/g)].map(m=>m[1]);
const mappings=[...labels.matchAll(/"([a-z0-9_-]+)" to "([^"]+)"/g)];
const labelIds=mappings.map(m=>m[1]);
assert.ok(ids.length>=208,'Kotlin must preserve at least the verified wave-six prefix');
const declared=Number(source.match(/require\(definitions\.size==(\d+)\)/)?.[1]);
assert.equal(ids.length,declared,'Kotlin reviewed source count and generated IDs disagree');
assert.equal(labelIds.length,ids.length,'every reviewed Kotlin exercise needs a real name');
assert.equal(new Set(labelIds).size,labelIds.length,'duplicate native labels');
assert.deepEqual([...new Set(labelIds)].sort(),[...new Set(ids)].sort(),
  'athlete-facing names must cover exact reviewed Kotlin IDs, not unrelated legacy ones');
const reviewedRequestFixture=JSON.parse(readFileSync(
  'android-native/kotlin-engine/verification/requested-web-variants-20261117.json','utf8'));
assert.equal(reviewedRequestFixture.sourcePullRequest,671);
const activeIds=new Set([
  ...EXERCISES.map(ex=>ex.id),
  ...reviewedRequestFixture.records.map(ex=>ex.id)
]);
// A historical shell collision is not a current-web exact ID. Require
// the frozen source row and its independent native added-load identity.
const historicalShell=JSON.parse(readFileSync(
  'android-native/STAGE1_WEB_SHELL_INVENTORY.json','utf8'));
const weighted=historicalShell.shellRows.find(row=>row.id==='SHELL-pullup');
assert.equal(weighted?.name,'Weighted Pull-Up');
assert.equal(weighted?.candidateWebId,'pullup');
assert.equal(EXERCISES.find(ex=>ex.id==='pullup')?.name,'Pull-Up');
assert.match(source,/entry\("weighted-pullup","vertical_pull","added-load-overhand",\s*ComposerPattern\.VERTICAL_PULL,ComposerRole\.PRIMARY,pull,\s*"weighted-pullup-resistance",LoadBasis\.EXERCISE_OVERRIDE/);
assert.match(labels,/"weighted-pullup" to "Weighted Pull-Up \(added load\)"/);
assert.match(labels,/"pullup" to "Pull-Up"/);
const legacyCatalog=readFileSync('modules/training-domain/catalog.js','utf8');
for(const [shellId,candidate,nativeId,rawEquipment,label] of [
  ['chest-row','chest_supported_row','chest-supported-machine-row','["machinerow"]','Chest-Supported Machine Row'],
  ['preacher','preacher_curl','ez-bar-preacher-curl','["ezbar", "preacher"]','EZ-Bar Preacher Curl']
]) {
  const row=historicalShell.shellRows.find(x=>x.shellId===shellId);
  assert.equal(row?.candidateWebId,candidate);
  assert.equal(row?.match,'explicit-web-alias');
  const line=legacyCatalog.split('\n')[row.line-1];
  assert.ok(line.includes('"'+shellId+'"')&&line.includes(rawEquipment),
    'Historical physical source drift: '+shellId);
  assert.ok(labels.includes('"'+nativeId+'" to "'+label+'"'));
}
const historicalNativeIds=new Set(['weighted-pullup','chest-supported-machine-row','ez-bar-preacher-curl']);
assert.ok([...historicalNativeIds].every(id=>!activeIds.has(id)),
  'Separate native identity must not inflate current-web exact matches');
for(const [id,label] of mappings.map(m=>[m[1],m[2]])) {
  assert.ok(activeIds.has(id)||historicalNativeIds.has(id),
    'label must match a reviewed current or historical source identity: '+id);
  assert.ok(label.length>=4 && !label.includes('${') && !label.startsWith('"'),
    'label must be a usable human-readable static Kotlin display name: '+id);
}
// A band-supported squat remains unweighted. Assisted machines now have an
// explicit inverse counterweight basis, independently audited provenance and
// station identity; timed holds, carries and hybrid banded bodyweight exercise
// still need separate measurement models.
const fixtureUi=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymFacilityChoices.kt','utf8');
assert.ok(ids.includes('spanish-squat'));
assert.match(labels,/"spanish-squat" to "Spanish Squat \(band-supported, reps\)"/);
assert.match(fixtureUi,/"spanish-squat-support" to "Anchored Spanish squat support band"/);
for(const id of ['assisted-dip','assisted-chinup','assisted-pullup']) {
  assert.ok(ids.includes(id),'Verified assisted counterweight source missing: '+id);
  assert.ok(labels.includes('"'+id+'" to "'),
    'Physical assisted machine must have its own visible label: '+id);
}
// Neck source equipment is misleading: all four are weighted movements.
// A provisional V5 anatomical credit and verified real-world resistance
// plus a named supported setup are mandatory before any native issuance.
for(const [id,name,fixture] of [
  ['neck-extension','Weighted Neck Extension','supported-neck-extension'],
  ['neck-curl','Weighted Neck Curl','supported-neck-flexion'],
  ['neck-harness','Neck Harness Raise','fitted-neck-harness'],
  ['neck-lateral','Lateral Neck Raise','supported-neck-lateral']
]) {
  assert.ok(ids.includes(id),'Reviewed neck source missing: '+id);
  assert.ok(labels.includes('"'+id+'" to "'+name+'"'),
    'Neck exercise must keep canonical athlete-readable name: '+id);
  assert.ok(fixtureUi.includes('"'+fixture+'" to "'),
    'Neck movement requires an explicitly selectable confirmed support: '+id);
}
// Real neck weight settings are discrete by default in the Gym editor.
// A missing inventory never becomes a guessed continuous machine stack.
const gymEditor=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymSourceCandidateScreen.kt','utf8');
const stockLabels=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinPhysicalStockDraft.kt','utf8');
assert.match(fixtureUi,/"weighted-pullup-setup" to "Secure added-weight pull-up setup"/);
for(const id of ['neck-extension-device','neck-flexion-device',
  'neck-harness-device','neck-lateral-device','weighted-pullup-resistance']) {
  assert.ok(gymEditor.includes('"'+id+'"'),
    'Loaded neck movement must choose athlete-verified exact stock: '+id);
  assert.ok(stockLabels.includes('"'+id+'" -> "'),
    'Gym must name the physical implement, not invent a generic stack: '+id);
}
for(const id of ['band-pushup','plank',
  'dead-hang','farmers','weighted-plank']) {
  assert.ok(!ids.includes(id),
    'Unmodeled timed/carry/hybrid loading must not be admitted: '+id);
}
// Every native load-advice entry must know that increasing machine
// COUNTERWEIGHT is LESS demanding. Snapshot/History still own the actual.
const kotlinRoot='android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/';
const guardFiles=[
  ['InWorkoutCoachDomain.kt','if(!planned.basis.higherSettingIncreasesDifficulty)'],
  ['ConservativeProgressionReview.kt','ASSISTANCE_NEEDS_DECREASE_REVIEW'],
  ['CoachNextProgramLoadReviewDomain.kt','stock.basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT'],
  ['CustomSameBlockLoadContinuityDomain.kt','ASSISTANCE_NEEDS_SEPARATE_RECOVERY_REVIEW'],
  ['ReviewedFutureWeekProgressionDomain.kt','exercise.expectedLoadBasis==LoadBasis.ASSISTANCE_COUNTERWEIGHT'],
  ['CoachSessionPrescriptionDomain.kt','if(assistance && p.scheme==SetLoadScheme.LAST_SET_BACKOFF)return null'],
  ['CoachFirstProgramStartDomain.kt','val warmupLoad=if(assisted)inspected?.nextAbove']
];
for(const [file,needle] of guardFiles) {
  const implementation=readFileSync(kotlinRoot+file,'utf8');
  assert.ok(implementation.includes(needle),
    'Assisted counterweight could silently invert load effort in '+file);
}
assert.ok(labels.includes('internal fun kotlinExerciseLabel(id:String)'),
  'fallback remains available only for truly unreviewed or custom identities');
console.log(`PASS complete source-matched display labels for ${ids.length} reviewed Kotlin exercise IDs`);
