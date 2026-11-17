import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const pre='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const domain=readFileSync(pre+'NativeKotlinWorkoutRecapDomain.kt','utf8');
const finish=readFileSync(pre+'NativeKotlinVerifiedFinishRecap.kt','utf8');
const history=readFileSync(pre+'NativeKotlinCandidateHistoryScreen.kt','utf8');
const shared=readFileSync(pre+'NativeKotlinPerformanceEvidenceLines.kt','utf8');
const tests=readFileSync('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutRecapDomainTest.kt','utf8');
const ci=readFileSync('.github/workflows/android-kotlin-rebuild.yml','utf8');

for(const expr of [/KotlinHistoryPerformanceSummaryDomain\.fromComposer\(/,
  /KotlinHistoryPerformanceSummaryDomain\.fromCustom\(/,
  /attachVerifiedPerformance\(it,performance\)/,
  /canonical\.completedWorkingSets!=summary\.completedMain/,
  /canonical\.completedFollowUpVisits!=summary\.completedAmplifiers/,
  /return summary\.copy\(performance=canonical\)/])assert.match(domain,expr);
assert.match(finish,/summary\.performance\?\.let \{ verified ->/);
assert.match(finish,/NativeKotlinPerformanceEvidenceLines\(verified\)/);
assert.match(history,/NativeKotlinPerformanceEvidenceLines\(performance\)/);
assert.match(shared,/evidence\.repsWithinRange/);
assert.match(shared,/evidence\.repsBelowMinimum/);
assert.match(shared,/evidence\.actualRirReportedWorkingSets/);
assert.doesNotMatch(shared,/issueWorkout|reviewCurrentApprovedLoad|setRir\(/);
assert.match(tests,/canonicalHistoryPerformanceIsAttachedOnlyWhenCountsReconcile/);
assert.match(tests,/contradictoryHistoryMetricsRefusePublishingARecap/);
assert.match(ci,/node android-native\/verification\/kotlin-canonical-finish-feedback-test\.mjs/);
console.log('PASS Kotlin History + Finish share factual performance projection without second progression authority');
