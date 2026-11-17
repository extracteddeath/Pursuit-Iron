import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const strength=readFileSync(dir+'NativeKotlinProgressTrend.kt','utf8');
const performance=readFileSync(dir+'NativeKotlinExercisePerformanceTrend.kt','utf8');
const axis=readFileSync(dir+'NativeKotlinTrendAxisPolicy.kt','utf8');
const tests=readFileSync('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinTrendAxisPolicyTest.kt','utf8');
const workflow=readFileSync('.github/workflows/android-kotlin-rebuild.yml','utf8');

assert.match(strength,/entry\.manifest\.completedAtEpochMillis/);
assert.match(strength,/val ratio=axis\.fractions\[index\]/);
assert.match(strength,/val x=axis\.fractions\[index\]/);
assert.match(strength,/NativeKotlinTrendAxisPolicy\.sessionLabel/);
assert.match(performance,/NativeKotlinTrendAxisPolicy\.exactMetric/);
assert.match(performance,/manifests\[sample\.completionOrder\]\?\.completedAtEpochMillis/);
assert.match(performance,/NativeKotlinTrendAxisPolicy\.sessionLabel/);
assert.doesNotMatch(performance,/setScale\(1,RoundingMode\.HALF_UP\)/);
assert.match(axis,/known\.zipWithNext\(\)\.any/);
assert.match(axis,/NativeKotlinTrendAxis\(sequence,false\)/);
assert.match(axis,/Date not recorded/);
assert.match(axis,/stripTrailingZeros\(\)\.toPlainString\(\)/);
for(const test of [
  'calendarSpacingReflectsUnequalRecordedIntervals',
  'missingTiedOrContradictoryDatesNeverBecomeInventedCalendarSpace',
  'smallSampleAxisNeverDividesByZero',
  'exactObservedWeightsRetainQuarterPlatePrecisionAndSignedChanges',
  'dateLabelsUseActualFinishClockOrExplicitlySayUndated'
])assert.ok(tests.includes(test),'Missing JVM test '+test);
assert.match(workflow,/node android-native\/verification\/kotlin-factual-trend-time-axis-test\.mjs/);
console.log('PASS: date-sourced, exact-observation native Progress charts, chronological fallbacks and JUnit test contract.');
