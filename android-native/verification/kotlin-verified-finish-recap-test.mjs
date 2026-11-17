import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const domain=readFileSync(source+'NativeKotlinWorkoutRecapDomain.kt','utf8');
const screen=readFileSync(source+'NativeKotlinVerifiedFinishRecap.kt','utf8');
const generated=readFileSync(source+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(source+'NativeKotlinCustomJournalScreen.kt','utf8');
const tests=readFileSync('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutRecapDomainTest.kt','utf8');
const ci=readFileSync('.github/workflows/android-kotlin-rebuild.yml','utf8');

assert.match(screen,/host\.reviewActiveAthleteSetup\(\)/);
assert.match(screen,/host\.currentHistoryExportOwner\(\)/);
assert.match(screen,/host\.reviewVerifiedKotlinHistory\(\)/);
assert.match(screen,/ownerBefore!=ownerAfter/);
assert.match(screen,/timeline\?\.athleteId!=active/);
assert.match(screen,/\.singleOrNull \{/);
assert.match(screen,/is NativeKotlinMixedHistoryCompletion\.Composer -> !isCustom/);
assert.match(screen,/is NativeKotlinMixedHistoryCompletion\.Custom -> isCustom/);
assert.match(screen,/NativeKotlinWorkoutRecapDomain/);
assert.match(screen,/Refresh saved recap/);
assert.match(screen,/No estimated PRs or load changes/);
assert.doesNotMatch(screen,/\.issue\(|\.record\(|\.approve\(|\.publish\(|\.mutate\(/);
assert.match(domain,/completion\.evidence\.sets\.map/);
assert.match(domain,/completion\.evidence\.visits\.map/);
assert.match(domain,/it\.part==CustomWorkoutVisitPart\.FOLLOW_UP/);
assert.match(domain,/!row\.followUp && row\.type==DraftSetType\.WORKING/);
assert.match(domain,/skippedOther=/);
assert.match(domain,/failedOther=/);
assert.match(generated,/NativeKotlinVerifiedFinishRecap\([\s\S]{0,180}isCustom=false/);
assert.match(custom,/NativeKotlinVerifiedFinishRecap\([\s\S]{0,180}isCustom=true/);
assert.match(ci,/node android-native\/verification\/kotlin-verified-finish-recap-test\.mjs/);
for(const name of [
  'genuineWorkingSetsDoNotBorrowWarmupsOrAmplifierCounts',
  'failedAndSkippedWarmupsAndFollowUpsAreNotSilentlyDiscarded',
  'allSkippedWorkoutDoesNotInventCompletedSetsOrPrs',
  'outOfBoundsOrInvalidEvidenceFailsClosed'
])assert.ok(tests.includes(name),'Missing JVM recap test: '+name);
console.log('PASS native Kotlin verified Finish recap: owner-fenced, generated+custom, factual corrections, clear outcome counts.');
