import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root='android-native/';
const gradle=readFileSync(root+'app/build.gradle.kts','utf8');
assert.ok(gradle.includes('"**/NativeStrengthVolumeRepairReview.kt"'),
  'A new native candidate source MUST be explicitly staged in the Kotlin-only app');
const pkg=root+'app/src/main/java/io/github/extracteddeath/pursuitiron/';
const host=readFileSync(pkg+'NativeKotlinCandidateHost.kt','utf8');
const model=readFileSync(pkg+'NativeStrengthVolumeRepairReview.kt','utf8');
const controller=readFileSync(pkg+'NativeKotlinCustomCycleController.kt','utf8');
const plan=readFileSync(pkg+'NativeKotlinCustomCycleCandidateScreen.kt','utf8');
const comparison=readFileSync(pkg+'NativeKotlinCycleBlockComparisonPanel.kt','utf8');
const engine=readFileSync(root+'kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/AdaptiveStrengthVolumeRepairDomain.kt','utf8');
const tests=readFileSync(root+'kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/TrainingBlockRecoveryContinuityTest.kt','utf8');

for(const name of ['NativeStrengthVolumeRepairRequest','NativeStrengthVolumeRepairReady',
  'NativeStrengthVolumeRepairSlot','NativeStrengthVolumeRepairPreview',
  'NativeStrengthVolumeRepairDecision'])
  assert.ok(model.includes(name),'Missing native review data type: '+name);
assert.match(model,/mayApplyAutomatically:Boolean get\(\)=false/);
assert.match(model,/mayIssueWorkout:Boolean get\(\)=false/);
assert.match(model,/mayAlterHistory:Boolean get\(\)=false/);
assert.match(model,/slot\.sets\.count\{it\.type==DraftSetType\.WORKING\}/);
assert.match(model,/proposed\.sets\.count\{it\.type==DraftSetType\.WORKING\}/);

for(const required of [
  'fun previewStrengthVolumeRepair(',
  'fun acceptStrengthVolumeRepair(',
  'revisionFence.withOwnerTransaction(owner.ownerKey)',
  'customDraftStore.read(owner.ownerKey',
  'authoringSourceStore.read(owner.ownerKey)',
  'adaptiveCyclePermissions.read(owner.ownerKey',
  'CustomCycleChangeKind.SET_COUNT !in rights.allowedKinds',
  'coachSettings.read(owner.ownerKey)',
  '!assistance.policy.coachingSuggestionsEnabled',
  'original.revision!=request.expectedCycleRevision',
  'source.revision!=request.expectedSourceRevision',
  'AdaptiveStrengthVolumeRepairDomain.propose(',
  'AdaptiveCycleOptionRequest(',
  'CustomCycleCoachPermissions(adaptSetCount=true)',
  'fresh.receipt!=reviewed',
  'reviewedStrengthDose(reviewed.request)',
  'reviewed.request.expectedCycleRevision',
  'customDraftStore.save(owner.ownerKey',
])assert.ok(host.includes(required),'Missing owner-fenced repair contract: '+required);
const acceptance=host.slice(host.indexOf('fun acceptStrengthVolumeRepair('),
  host.indexOf('fun readAdaptiveCyclePermissions('));
assert.ok(acceptance.indexOf('reviewedStrengthDose(reviewed.request)')>=0);
assert.ok(acceptance.indexOf('fresh.receipt!=reviewed') <
  acceptance.indexOf('customDraftStore.save(owner.ownerKey'));
assert.doesNotMatch(acceptance,/issueWorkout\(|recordWorkingSet\(|updateHistory\(|journalStore\.save\(/);

for(const required of [
  'strengthRepairReview:NativeStrengthVolumeRepairReady?=null',
  'fun reviewStrengthVolumeRepair(blockId:String,maxSetsPerSlot:Int)',
  'shown.auditSourceRevision!=sourceRevision',
  'audited.auditedCycleRevision!=draft.revision',
  'host.previewStrengthVolumeRepair(',
  'fun acceptStrengthVolumeRepair(athleteConfirmed:Boolean)',
  'host.acceptStrengthVolumeRepair(',
  'CustomCycleDraftReceipt(',
  'applyRead(prior.cycleId,disk)',
  'strengthRepairReview=null',
  'canUndo=true',
  'host.saveReviewedCustomCycleDraft(',
  'NativeCustomDraftRefusal.STALE_REVISION'
])assert.ok(controller.includes(required),'Missing controller review/save: '+required);
// Both revisioned Strength repairs must take the source-fenced Undo path.
assert.match(controller,/if\(receipt\.operation in setOf\("strength-dose-repair",\s*"strength-menu-recovery"\)\)/,
  'Dose and menu recovery Undo must use a current reviewed gym source');
for(const required of [
  'repairReview=state.strengthRepairReview',
  'state.coachingSuggestionsEnabled',
  'onRequestStrengthRepair=if(permissionHost!=null)',
  'controller.reviewStrengthVolumeRepair(',
  'onAcceptStrengthRepair=if(permissionHost!=null)',
  'controller.acceptStrengthVolumeRepair(true)'
])assert.ok(plan.includes(required),'Missing signed-in native Plan callback: '+required);
for(const required of [
  'Review proposed strength adjustments',
  'onRequestStrengthRepair(next.blockId,maximumSets)',
  'verified.changedSlots.forEach',
  'I reviewed these working-set changes',
  'athleteConfirmed',
  'enabled=enabled && athleteConfirmed',
  'onAcceptStrengthRepair?.invoke()',
  'Save reviewed strength adjustments',
  'require renewed program approval'
])assert.ok(comparison.includes(required),'Missing athlete-controlled Plan consent: '+required);

for(const required of [
  'initial.sourceRepairRequired',
  'input.review.permissions.adaptSetCount',
  'cycle.weekOverrides[block.blockId].orEmpty().isNotEmpty()',
  'work.any {it.amplifier!=null || it.targetRir==null}',
  'AdaptiveCycleOptionReviewDomain.review(',
  'AdaptiveStrengthVolumeRepairStatus.CANDIDATE_READY_FOR_REVIEW'
])assert.ok(engine.includes(required),'Missing bounded Kotlin generator gate: '+required);
const maskedDayTests=readFileSync(root+'kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/AdaptiveStrengthDayDoseRepairTest.kt','utf8');
for(const required of [
  'StrengthDoseBoundaryGuardDomain.review(',
  'val affectedDays=dayBoundary?.affectedDays.orEmpty()',
  'val dayFloor=affectedDays.associate',
  'val dayWorking=block.days.associate',
  'val localGap=positive(',
  'compareByDescending<Placement> {it.dayBenefit}',
  'val repairedBoundary=repairedStructure?.let',
  'if(affectedDays.isEmpty())'
])assert.ok(engine.includes(required),
  'Strength dose repair must reconcile real source-backed weekday losses: '+required);
for(const required of [
  'StrengthDoseBoundaryGuardDomain.review(it,id)',
  'val affectedStrengthDays=strengthDayDose?.affectedDays.orEmpty()',
  'if(affectedStrengthDays.isNotEmpty())',
  'affectedStrengthDays.isNotEmpty() ||',
  'onRequestStrengthRepair(next.blockId,maximumSets)'
])assert.ok(comparison.includes(required),
  'Native Plan must explain the exact lost day and offer reviewed repair: '+required);
for(const name of [
  'reconstructsDayDoseDespiteUnchangedWeeklyTotalWithoutMutatingSource',
  'consentAndUnattainableSetCapacityRefuseWithoutWriting'
])assert.ok(maskedDayTests.includes(name),
  'Need positive and refusal tests for hidden per-day Strength loss');
for(const name of [
  'generatedStrengthRepairActuallyProducesAudited24SetWeek',
  'strengthRepairDoesNotRecreateMusclesAbsentFromTheSavedTargetScope',
  'generatedRepairCoversEveryWeekOfAnUnchangedStrengthTemplate'
])assert.ok(tests.includes(name),'Missing full-cycle JVM fixture '+name);
console.log('PASS native strength repair: athlete preview, same-owner proposal replay, revision-CAS approval, independent Kotlin dose audit and no history writes.');
