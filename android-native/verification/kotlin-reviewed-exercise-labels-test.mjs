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
const activeIds=new Set(EXERCISES.map(ex=>ex.id));
for(const [id,label] of mappings.map(m=>[m[1],m[2]])) {
  assert.ok(activeIds.has(id),'label must match an active source ID: '+id);
  assert.ok(label.length>=4 && !label.includes('${') && !label.startsWith('"'),
    'label must be a usable human-readable static Kotlin display name: '+id);
}
assert.ok(labels.includes('internal fun kotlinExerciseLabel(id:String)'),
  'fallback remains available only for truly unreviewed or custom identities');
console.log(`PASS complete source-matched display labels for ${ids.length} reviewed Kotlin exercise IDs`);
