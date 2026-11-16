import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const pre='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const profile=readFileSync(pre+'NativeKotlinProfileScreen.kt','utf8');
const model=readFileSync(pre+'NativeKotlinProfileMilestones.kt','utf8');
const panel=readFileSync(pre+'NativeKotlinProfileMilestonesPanel.kt','utf8');
const summary=readFileSync('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinHistoryPerformanceSummaryDomain.kt','utf8');
const workflow=readFileSync('.github/workflows/android-kotlin-rebuild.yml','utf8');

assert.match(profile,/NativeKotlinMixedHistoryReadResult\.Review/);
assert.match(profile,/it\.athleteId==current\?\.athleteProfileId/);
assert.match(profile,/KotlinHistoryPerformanceSummaryDomain\.fromComposer/);
assert.match(profile,/KotlinHistoryPerformanceSummaryDomain\.fromCustom/);
assert.match(summary,/it\.part == CustomWorkoutVisitPart\.MAIN && it\.setType == DraftSetType\.WORKING/);
assert.match(summary,/it\.type == DraftSetType\.WORKING/);
assert.match(summary,/it\.outcome == KotlinJournalSetOutcome\.COMPLETED/);
assert.match(profile,/NativeKotlinProfileMilestones\.fromVerifiedCounts/);
assert.match(profile,/NativeKotlinProfileLevelBar\(profileProgress\)/);
assert.match(profile,/NativeKotlinProfileMilestonesPanel\(profileProgress,current\?\.athleteProfileId\)/);
assert.match(model,/XP_PER_COMPLETED_SESSION = 100L/);
assert.match(model,/XP_PER_COMPLETED_WORKING_SET = 10L/);
assert.match(model,/MAX_LEVEL = 100/);
assert.match(panel,/rememberSaveable\(athleteId\)/);
assert.match(panel,/never influence training weights, reps, RIR or coaching/);
assert.doesNotMatch(model+panel,/\.issue\(|\.publish\(|\.record\(|\.approve\(|\.saveWorkout\(|KotlinNextProgramLoad|CoachNextProgramLoad/);
assert.match(workflow,/node android-native\/verification\/kotlin-profile-milestones-test\.mjs/);
console.log('PASS Kotlin Profile level and participation milestones use verified corrected history only');
