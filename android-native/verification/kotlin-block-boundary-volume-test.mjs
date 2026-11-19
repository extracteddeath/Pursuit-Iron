import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='android-native/';
const domain=readFileSync(root+
  'kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomCycleBlockComparisonDomain.kt','utf8');
const sourceArmDomain=readFileSync(root+
  'kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StrengthSourceArmContinuityDomain.kt','utf8');
const sourceArmSelection=readFileSync(root+
  'kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CoachAthleteExerciseSelectionDomain.kt','utf8');
const tests=readFileSync(root+
  'kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/CustomCycleBlockComparisonDomainTest.kt','utf8');
const ui=readFileSync(root+
  'app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCycleBlockComparisonPanel.kt','utf8');

// The previous block is measured at EXIT, the next block at ENTRY.
// Never compare week-one-to-week-one or silently omit later weak strength weeks.
assert.match(domain,/prior\.auditedWeeklyWorkingSets\?\.lastOrNull\(\)/);
assert.match(domain,/next\.auditedWeeklyWorkingSets\?\.firstOrNull\(\)/);
assert.match(domain,/next\.auditedWeeklyWorkingSets\?\.minOrNull\(\)/);
assert.match(domain,/prior\.lastAuditedWeekDirectSets/);
assert.match(domain,/val strengthHalfVolumeReviewRequired:Boolean/);
assert.match(domain,/data class CustomBlockDayExerciseChange/);
assert.match(domain,/val strengthExerciseCollapseReviewRequired:Boolean/);
assert.match(domain,/val strengthDirectArmLosses:Set<TrainingMuscle>\?/);
assert.match(domain,/prior\.lastAuditedWeekDirectSets \?:return null/);
assert.match(domain,/next\.firstAuditedWeekDirectSets \?:return null/);
assert.match(domain,/before\.signum\(\)>0/);
assert.match(domain,/after\.signum\(\)==0/);
assert.match(domain,/day\.priorExerciseCount>=6/);
assert.match(domain,/day\.nextExerciseCount\*2<day\.priorExerciseCount/);
assert.match(domain,/prior\.split==next\.split/);
assert.match(domain,/prior\.weekdays\.toSet\(\)==next\.weekdays\.toSet\(\)/);
assert.match(domain,/CustomTrainingCycleDomain\.week\(it,priorExit\)/);
assert.match(domain,/CustomTrainingCycleDomain\.week\(it,nextEntry\)/);
assert.match(domain,/val auditedDayRosters=matchedTraining/);
assert.match(domain,/next\.phase!=TrainingPhase\.STRENGTH/);
assert.match(domain,/strengthHalfVolumeUsesAuditedWeeks/);
assert.match(domain,/audit\.structuralReview\?\.verifiedDraft==cycle/);
assert.match(domain,/weeks\.map\{it\.absoluteWeek\}==expectedWeeks/);
assert.match(domain,/slot\.sets\.count\{it\.type==DraftSetType\.WORKING\}/);
assert.doesNotMatch(domain,/firstAuditedWeekDelta:Int\? get\(\)=\s*prior\.auditedWeeklyWorkingSets\?\.firstOrNull/);

// Render the exact exit/entry and every next week; never label a
// descriptive warning as an approved program or issue a workout.
for(const text of [
  'Previous exit week','Next entry week','Entry change',
  'Next block by week:', 'Lowest week retains',
  'Strength working-set drop needs review',
  'No loads, sets, training history or program approval were changed.',
  'Final prior week → first next week'
])assert.ok(ui.includes(text),'Missing visible native volume context: '+text);
assert.match(ui,/comparison\.strengthHalfVolumeReviewRequired/);
assert.match(ui,/comparison\.strengthExerciseCollapseReviewRequired/);
assert.match(ui,/comparison\.strengthDirectArmLosses\?\.takeIf/);
assert.match(ui,/Direct arm work disappeared/);
assert.match(ui,/Review strength arm exercises/);
assert.match(ui,/Indirect assistance from bench presses and rows/);
assert.match(ui,/comparison\.exerciseRosterUsesReviewedWeeks/);
assert.match(ui,/Review or restore strength exercises/);
assert.match(ui,/onEditBlock\(next\.blockId\)/);
assert.match(ui,/Set-count repair alone cannot restore an omitted exercise/);
assert.match(ui,/comparison\.strengthHalfVolumeUsesAuditedWeeks/);
assert.match(ui,/comparison\.matchingAuditStatus==/);
assert.doesNotMatch(ui,/\.save\(|\.record\(|\.issueWorkout\(|\.finish\(/);

for(const name of [
  'fullyMeasuredUnresolvedAuditIsVisibleButNeverCalledApproval',
  'aLaterStrengthWeekCliffIsNotHiddenByItsSafeEntryWeek',
  'eightToThreeSameSplitStrengthUpperIsFlaggedByExerciseNotSetCount',
  'measuredRostersStillRequireSourceMatchedAuditAndNoFakePeakFloor',
  'zeroDirectArmsAreFlaggedEvenWithoutEightToThreeExerciseLoss',
  'anExplicitPeakBlockIsNotMisreportedAsStrengthVolumeCliff',
  'smallWeeksDoNotReceiveAnInventedUniversalVolumeFloor',
  'rejectsStaleIncompleteOrMismatchedAuditMeasurements'
])assert.ok(tests.includes(name),'Missing native volume regression: '+name);
assert.ok(tests.includes('assertEquals(-16,result.boundaryWorkingSetDelta)'));
assert.ok(tests.includes('assertEquals(37,result.lowestNextWeekRetentionPercent)'));
console.log('PASS native cycle boundary: final→first and weakest strength week, factual warnings, immutable workout history.');

// Strength source continuity must match all five independently credited arm/
// forearm functions flagged by the last-Hypertrophy → first-Strength audit.
assert.match(sourceArmDomain,/TrainingMuscle\.WRIST_FLEXORS,TrainingMuscle\.WRIST_EXTENSORS/);
assert.match(sourceArmSelection,/TrainingMuscle\.WRIST_EXTENSORS/);
assert.match(sourceArmSelection,/source\.candidates\.size<=5/);

const menuDomain=readFileSync(root+
  'kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StrengthExerciseMenuRecoveryDomain.kt','utf8');
const menuTests=readFileSync(root+
  'kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/StrengthExerciseMenuRecoveryDomainTest.kt','utf8');
const host=readFileSync(root+
  'app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt','utf8');
const controller=readFileSync(root+
  'app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleController.kt','utf8');
const plan=readFileSync(root+
  'app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleCandidateScreen.kt','utf8');

// A real 8→3 menu needs EXERCISE continuity, not just more sets.
assert.match(menuDomain,/StrengthRosterBoundaryGuardDomain\\.review/);
assert.match(menuDomain,/boundary\\.affectedDays/);
assert.doesNotMatch(menuDomain,/retainedAnywhere/,
  'A movement present on Wednesday must not hide a lost Monday exposure');
assert.match(menuDomain,/CustomTrainingCycleDomain\.week/);
assert.match(menuDomain,/AdaptiveCycleOptionReviewDomain\.review/);
assert.match(menuDomain,/permissions\.adaptExercises/);
assert.match(menuDomain,/StrengthMenuRecoveryStatus\.READY_FOR_ATHLETE_REVIEW/);
assert.match(menuDomain,/mayAlterHistory:Boolean get\(\)=false/);
for(const test of [
  'realExitWeekMenuRestoresFiveDistinctAccessoriesWithoutMutatingSource',
  'capacityAndConstrainedSourceNeverPretendTheyRestoredEverything',
  'fullProposeNeverTreatsInjectedStructuralAuditAsAuthority'
])assert.ok(menuTests.includes(test),'Missing Strength menu JVM test: '+test);
assert.match(host,/fun previewStrengthMenuRecovery\(/);
assert.match(host,/fun acceptStrengthMenuRecovery\(/);
assert.match(host,/CustomCycleChangeKind\.EXERCISES !in rights\.allowedKinds/);
assert.match(host,/fresh\.receipt!=reviewed/);
assert.match(host,/customDraftStore\.save\(owner\.ownerKey/);
assert.match(controller,/fun reviewStrengthExerciseMenu\(/);
assert.match(controller,/fun acceptStrengthExerciseMenu\(/);
assert.match(controller,/"strength-menu-recovery"/);
assert.match(ui,/Preview source exercise recovery/);
assert.match(ui,/Confirm and save recovered exercises/);
assert.match(ui,/StrengthRosterBoundaryGuardDomain\\.review\\(it,id\\)/);
assert.match(ui,/val affectedStrengthMenus=strengthRoster\\?\\.affectedDays\\.orEmpty\\(\\)/);
assert.match(ui,/if\\(strengthCoverageReviewRequired\\)/);
assert.match(ui,/if\\(strengthCoverageReviewRequired &&/);
assert.match(ui,/Intervening recovery or performance-test weeks are not/);
for(const name of [
  'recoveryAndTestBridgesPreserveActualHypertrophyExitForStrength'
]) {
  const test=readFileSync(root+
    'kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/StrengthRosterBoundaryGuardDomainTest.kt','utf8');
  assert.ok(test.includes(name),'Missing H→deload→Strength guard regression');
}
assert.ok(menuTests.includes('recoveryBridgeCannotHideEightToThreeSourceMenuLoss'),
  'Missing source-backed H→deload→Strength exercise recovery fixture');
assert.match(plan,/menuRecoveryReview=state\.strengthMenuRecoveryReview/);
console.log('PASS source-audited Strength menu recovery: real draft → owner-fenced Plan preview/consent → CAS/undo; no automatic issue.');
