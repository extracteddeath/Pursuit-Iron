import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';

// Source integration sentinel; Kotlin JVM tests prove the ranking model.
// No workout-only or changed-week UI may invent direct training frequency.
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const read=(file)=>readFileSync(join(root,file),'utf8');
const domain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomExerciseSwapRankingDomain.kt');
const sheet=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomExerciseSheet.kt');
const plan=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleCandidateScreen.kt');
const live=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt');
assert.match(domain,/otherWeekdayExerciseIds:List<List<String>>\?=null/);
assert.match(domain,/weekKnown=dayCoverageKnown &&/);
assert.match(domain,/day\.all\{it in byId\}/);
assert.match(domain,/thenByDescending \{it\.weeklyRetentionPriority\}/);
assert.match(sheet,/otherWeekdayExerciseIds=otherWeekdayExerciseIds/);
assert.match(sheet,/not proof of equal volume/);
assert.match(plan,/otherWeekdayExerciseIds=current\.blocks/);
assert.match(plan,/current\.weekOverrides\[blockId\]\.orEmpty\(\)\.isEmpty\(\)/);
assert.match(plan,/block\.days\.map\{it\.weekday\}\.distinct\(\)\.size/);
assert.match(plan,/it\.weekday!=weekday/);
assert.match(plan,/slot\.sets\.any\{set->set\.type==DraftSetType\.WORKING\}/);
assert.doesNotMatch(live,/otherWeekdayExerciseIds=/);
console.log('PASS only verified Plan block days inform weekly swap ordering; workout and overrides remain day-only');
