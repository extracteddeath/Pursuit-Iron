import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const read=file=>fs.readFileSync(file,'utf8');
const nativeSettings=read('android-native/settings.gradle.kts');
const app=read('android-native/app/build.gradle.kts');
const workflow=read('.github/workflows/android-kotlin-rebuild.yml');
const root='android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild';
const kotlinFiles=fs.readdirSync(root).filter(filename=>filename.endsWith('.kt'));
assert.ok(nativeSettings.includes('":kotlin-engine"'));
assert.ok(!app.includes('project(":kotlin-engine")'),
  'New independent engine cannot issue or alter an active native workout');
assert.ok(kotlinFiles.length>=6,'Require all current independent Kotlin rule domains');

for(const name of kotlinFiles) {
  const source=read(path.join(root,name));
  assert.match(source,/^package io\.github\.extracteddeath\.pursuitiron\.rebuild\s*$/m,
    name+' must stay in the isolated engine namespace');
  assert.doesNotMatch(source,/\bimport\s+(?:android\.|androidx\.|org\.json\.|io\.github\.extracteddeath\.pursuitiron\.domain\.)/,
    name+' cannot import Android, legacy training, storage or UI types');
  assert.doesNotMatch(source,/\b(?:SharedPreferences|ContentResolver|SQLiteDatabase|CanonicalJsSandboxProbe|WorkoutJournalPersistence|WorkManager|WebView)\b/,
    name+' cannot own storage, a background worker, or a JS runtime');
}
const equipment=read(path.join(root,'EquipmentLoadDomain.kt'));
assert.ok(equipment.includes('java.math.BigDecimal'),
  'Equipment computations require exact decimal arithmetic');
assert.ok(workflow.includes('architecture/kotlin-engine-rebuild') &&
  workflow.includes(':kotlin-engine:testDebugUnitTest') &&
  !workflow.includes(':app:assemble'));
console.log('PASS '+kotlinFiles.length+
  ' Kotlin rule domains are headless, isolated from native UI/active journals and tested without an APK');
