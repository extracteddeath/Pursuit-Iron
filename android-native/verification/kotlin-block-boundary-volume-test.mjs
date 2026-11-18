import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='android-native/';
const domain=readFileSync(root+
  'kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomCycleBlockComparisonDomain.kt','utf8');
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
assert.match(ui,/comparison\.strengthHalfVolumeUsesAuditedWeeks/);
assert.match(ui,/comparison\.matchingAuditStatus==/);
assert.doesNotMatch(ui,/\.save\(|\.record\(|\.issueWorkout\(|\.finish\(/);

for(const name of [
  'fullyMeasuredUnresolvedAuditIsVisibleButNeverCalledApproval',
  'aLaterStrengthWeekCliffIsNotHiddenByItsSafeEntryWeek',
  'anExplicitPeakBlockIsNotMisreportedAsStrengthVolumeCliff',
  'smallWeeksDoNotReceiveAnInventedUniversalVolumeFloor',
  'rejectsStaleIncompleteOrMismatchedAuditMeasurements'
])assert.ok(tests.includes(name),'Missing native volume regression: '+name);
assert.ok(tests.includes('assertEquals(-16,result.boundaryWorkingSetDelta)'));
assert.ok(tests.includes('assertEquals(37,result.lowestNextWeekRetentionPercent)'));
console.log('PASS native cycle boundary: final→first and weakest strength week, factual warnings, immutable workout history.');
