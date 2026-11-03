import assert from 'node:assert/strict';
import fs from 'node:fs';

const read=path=>fs.readFileSync(path,'utf8');
const settings=read('android-native/settings.gradle.kts');
const app=read('android-native/app/build.gradle.kts');
const domain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/EquipmentLoadDomain.kt');
const workflow=read('.github/workflows/android-kotlin-rebuild.yml');
assert.ok(settings.includes('":kotlin-engine"'));
assert.ok(!app.includes('project(":kotlin-engine")'),
  'New training rules are not authorized to issue or alter active workout targets');
assert.ok(domain.includes('java.math.BigDecimal'));
assert.ok(!domain.includes('org.json') && !domain.includes('android.') &&
  !domain.includes('CanonicalJs') && !domain.includes('engine-api.js'));
assert.ok(workflow.includes('architecture/kotlin-engine-rebuild') &&
  workflow.includes(':kotlin-engine:testDebugUnitTest') && !workflow.includes(':app:assemble'));
console.log('PASS Kotlin engine laboratory is isolated from native UI, active journals and the legacy training authority');
