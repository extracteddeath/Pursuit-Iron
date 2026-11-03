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
// The app MAY now compile the isolated Kotlin candidate, but its LIVE
// runtime and Compose screens must still own only the unchanged JS-backed
// canonical store until the explicit full-runtime cutover gate is met.
assert.ok(app.includes('project(":kotlin-engine")') &&
  app.includes('project(":native-coach-store")'),
  'The isolated Android Kotlin candidate must compile against native source');
const appRoot='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const mainActivity=read(appRoot+'MainActivity.kt');
const legacyRuntime=read(appRoot+'NativeRuntimeHost.kt');
const application=read(appRoot+'PursuitIronApplication.kt');
const candidate=read(appRoot+'NativeKotlinCandidateHost.kt');
const localAthlete=read(appRoot+'AppPrivateKotlinLocalAthlete.kt');
const candidateScreen=read(appRoot+'NativeKotlinWorkoutCandidateScreen.kt');
const sessionController=read(appRoot+'NativeKotlinSessionController.kt');
assert.ok(application.includes('by lazy { NativeKotlinCandidateHost(applicationContext) }'),
  'Kotlin candidate must not start or enroll anyone at Application startup');
assert.doesNotMatch(mainActivity,
  /KotlinCandidateHost|kotlinCandidate|kotlinWorkoutSession|NativeKotlinWorkoutCandidateScreen|kotlin-engine|nativecoachstore/,
  'No Compose screen may switch workout ownership before native cutover');
assert.doesNotMatch(legacyRuntime, /KotlinCandidateHost|kotlinCandidate|nativecoachstore/,
  'The active legacy workout runtime must not change its owner or journal');
assert.ok(legacyRuntime.includes('CanonicalWorkoutStorePersistence') &&
  legacyRuntime.includes('WorkoutJournalPersistence'),
  'Active runtime must keep existing canonical storage while Kotlin is isolated');
assert.doesNotMatch(candidate+localAthlete,
  /CanonicalJsSandboxProbe|PackagedReadOnlySchemaVerifier|engine-runtime|io\\.github\\.extracteddeath\\.pursuitiron\\.domain/,
  'Isolated Kotlin-only host cannot depend on the JS runtime or canonical bridge');
assert.ok(candidate.includes('CoachAthleteSetupDomain.review') &&
  candidate.includes('NativeKotlinApprovalFileAuthority') &&
  candidate.includes('NativeKotlinWorkoutCoordinator') &&
  localAthlete.includes('explicitlyActivated=false'),
  'Native onboarding must require explicit reviewed local athlete activation');
assert.doesNotMatch(candidateScreen+sessionController,
  /CanonicalJsSandboxProbe|PackagedReadOnlySchemaVerifier|io\\.github\\.extracteddeath\\.pursuitiron\\.domain/,
  'Kotlin workout screen/controller must not import the live JS-backed runtime');
assert.ok(candidateScreen.includes('PursuitCard') &&
  candidateScreen.includes('PursuitPrimaryAction') &&
  candidateScreen.includes('KotlinWorkoutEntryDomain.parse') &&
  sessionController.includes('KotlinWorkoutPresentationDomain.inspect') &&
  sessionController.includes('NativeJournalFileResult.Durable'),
  'Kotlin workout candidate must share M3E controls and render only acknowledged native receipts');
assert.ok(!legacyRuntime.includes('NativeKotlinSessionController') &&
  !mainActivity.includes('NativeKotlinSessionController'),
  'Independent Kotlin session controller must not switch live workout ownership');
const storeBuild=read('android-native/native-coach-store/build.gradle.kts');
assert.ok(nativeSettings.includes('":native-coach-store"') &&
  storeBuild.includes('project(":kotlin-engine")') &&
  !storeBuild.includes('project(":engine-runtime")'),
  'Native journal storage must use Kotlin engine receipts with no JS runtime');
const storeSrc=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/KotlinNativeJournalFileStore.kt');
assert.doesNotMatch(storeSrc, /\b(?:WebView|CanonicalJsSandboxProbe|engine-runtime|androidx\.compose)\b/,
  'Storage layer must remain native, JS-free and independent of Compose UI');
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
