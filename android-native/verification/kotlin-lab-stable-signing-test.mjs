import assert from 'node:assert/strict';
import fs from 'node:fs';

// Source-only safety contract. Never reads the actual signing credentials.
const read=p=>fs.readFileSync(p,'utf8');
const workflow=read('.github/workflows/android-kotlin-lab-device.yml');
const app=read('android-native/app/build.gradle.kts');
const docs=read('android-native/KOTLIN_LAB_SIGNING_SETUP.md');
const marker=read('android-native/KOTLIN_LAB_DEVICE_TEST.md');
const signing=read('android-native/tools/kotlin_lab_signing.py');

for(const secret of [
  'KOTLIN_LAB_KEYSTORE_B64',
  'KOTLIN_LAB_STORE_PASSWORD',
  'KOTLIN_LAB_KEY_ALIAS',
  'KOTLIN_LAB_KEY_PASSWORD'
])assert.ok(workflow.includes('secrets.'+secret),
  'APK workflow must depend on private stable signing secret '+secret);
assert.ok(workflow.includes('Require stable private Kotlin Lab signing') &&
  workflow.includes('kotlin_lab_signing.py prepare') &&
  signing.includes('any(not os.environ.get(name') &&
  signing.includes('base64.b64decode') &&
  signing.includes('KotlinLabSigningKeyCheck.java') &&
  read('android-native/tools/KotlinLabSigningKeyCheck.java').includes('store.getKey(args[1], keyPassword)') &&
  signing.includes('KOTLIN_LAB_SIGNING_STORE_FILE='),
  'Missing keys must abort the APK before build, without emitting key material');
assert.ok(workflow.includes('vars.KOTLIN_LAB_CERT_SHA256') &&
  workflow.includes('kotlin_lab_signing.py verify') &&
  workflow.includes('--source-commit "$GITHUB_SHA"') &&
  workflow.includes('kotlin-lab-build-receipt.json') &&
  workflow.includes('Remove temporary private signing key') &&
  signing.includes('"apksigner"') && signing.includes('"aapt2"'),
  'Final APK must verify its real certificate, package, version and launcher before upload');
assert.ok(workflow.indexOf('kotlin_lab_signing.py verify') <
  workflow.indexOf('uses: actions/upload-artifact@v4'),
  'An unverified APK cannot reach artifact upload');
for(const v of [
  'KOTLIN_LAB_SIGNING_STORE_FILE',
  'KOTLIN_LAB_SIGNING_STORE_PASSWORD',
  'KOTLIN_LAB_SIGNING_KEY_ALIAS',
  'KOTLIN_LAB_SIGNING_KEY_PASSWORD'
])assert.ok(app.includes(v)||workflow.includes(v),
  'Stable signing must pass '+v+' into Android Gradle');
assert.ok(app.includes('getByName("debug")') &&
  app.includes('storeFile=file(keyFile)') &&
  app.includes('applicationIdSuffix = ".kotlinlabstable"'),
  'Only the separate Kotlin Lab debug signing identity may be customized');
assert.ok(!read('.github/workflows/android-kotlin-rebuild.yml')
  .includes('secrets.KOTLIN_LAB_KEYSTORE_B64'),
  'Source validation must never need private signing credentials');
assert.ok(docs.includes('ephemeral') && docs.includes('cannot update') &&
  docs.includes('coexist') &&
  docs.includes('io.github.extracteddeath.pursuitiron.kotlinlabstable') &&
  docs.includes('repo') && docs.includes('secret') &&
  docs.toLowerCase().includes('never') &&
  marker.includes('GitHub runners generate'),
  'Safety docs must explain the original key incompatibility and private-key setup');
console.log('PASS no future Kotlin Lab APK may publish under an ephemeral signing key; source CI remains key-free');
