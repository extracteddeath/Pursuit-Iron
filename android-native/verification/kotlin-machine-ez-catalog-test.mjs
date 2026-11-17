import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const base='android-native/kotlin-engine/';
const manifest=JSON.parse(readFileSync(base+'verification/reviewed-catalog-expansion.json','utf8'));
const starter=readFileSync(base+'src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt','utf8');
const labels=readFileSync('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseLabels.kt','utf8');
const tests=readFileSync(base+'src/test/java/io/github/extracteddeath/pursuitiron/rebuild/ReviewedMachineAndEzCatalogTest.kt','utf8');
const names=['chest-row','preacher','leg-press'];
assert.deepEqual(manifest.records.slice(-3).map(x=>x.id),names);
assert.match(starter,/require\(definitions\.size==333\)/);
for(const row of manifest.records.slice(-3)) {
 assert.ok(starter.includes('entry("'+row.id+'"'));
 assert.ok(labels.includes('"'+row.id+'" to '));
 assert.ok(row.review.note.length>=45);
 assert.equal(row.review.attachment,null);
}
assert.deepEqual(manifest.records.slice(-3).map(x=>x.review.basis),
 ['EXERCISE_OVERRIDE','BARBELL_TOTAL','EXERCISE_OVERRIDE']);
assert.deepEqual(manifest.records.at(-2).review.fixtures,['preacher-bench']);
assert.match(starter,/mayTransferPreviousLoad:Boolean=false/);
assert.match(tests,/newIdsAppendAfterAllExistingDefinitionsWithoutLoadHistoryAliasing/);
assert.match(tests,/preacherUsesEzBarAndRealBenchNeverMachineStack/);
assert.match(readFileSync('.github/workflows/android-kotlin-rebuild.yml','utf8'),
 /node android-native\/verification\/kotlin-machine-ez-catalog-test\.mjs/);
console.log('PASS exact Kotlin-only stock-backed exercise ids, manifest and UI names');
