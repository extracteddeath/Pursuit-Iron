import assert from 'node:assert/strict';
import fs from 'node:fs';

// Source-only safety contract. Never reads the actual signing credentials.
const read=p=>fs.readFileSync(p,'utf8');
const workflow=read('.github/workflows/android-kotlin-lab-device.yml');
const app=read('android-native/app/build.gradle.kts');
const docs=read('android-native/KOTLIN_LAB_SIGNING_SETUP.md');
const marker=read('android-native/KOTLIN_LAB_DEVICE_TEST.md');

for(const secret of [
  'KOTLIN_LAB_KEYSTORE_B64',
  'KOTLIN_LAB_STORE_PASSWORD',
  'KOTLIN_LAB_KEY_ALIAS',
  'KOTLIN_LAB_KEY_PASSWORD'
])assert.ok(workflow.includes('secrets.'+secret),
  'APK workflow must depend on private stable signing secret '+secret);
assert.ok(workflow.includes('Require stable private Kotlin Lab signing') &&
  workflow.includes('if [[ -z "$KEYSTORE_BASE64"') &&
  workflow.includes('exit 1') &&
  workflow.includes('base64 --decode') &&
  workflow.includes('keytool -list') &&
  workflow.includes('KOTLIN_LAB_SIGNING_STORE_FILE='),
  'Missing keys must abort the APK before build, without emitting key material');
for(const v of [
  'KOTLIN_LAB_SIGNING_STORE_FILE',
  'KOTLIN_LAB_SIGNING_STORE_PASSWORD',
  'KOTLIN_LAB_SIGNING_KEY_ALIAS',
  'KOTLIN_LAB_SIGNING_KEY_PASSWORD'
])assert.ok(app.includes(v)||workflow.includes(v),
  'Stable signing must pass '+v+' into Android Gradle');
assert.ok(app.includes('getByName("debug")') &&
  app.includes('storeFile=file(keyFile)') &&
  app.includes('applicationIdSuffix = ".kotlinlab"'),
  'Only the separate Kotlin Lab debug signing identity may be customized');
assert.ok(!read('.github/workflows/android-kotlin-rebuild.yml')
  .includes('secrets.KOTLIN_LAB_KEYSTORE_B64'),
  'Source validation must never need private signing credentials');
assert.ok(docs.includes('ephemeral') && docs.includes('cannot upgrade') &&
  docs.includes('repo') && docs.includes('secret') &&
  docs.includes('never') &&
  marker.includes('GitHub runners generate'),
  'Safety docs must explain the original key incompatibility and private-key setup');
console.log('PASS no future Kotlin Lab APK may publish under an ephemeral signing key; source CI remains key-free');
