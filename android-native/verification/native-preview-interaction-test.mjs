import assert from 'node:assert/strict';
import fs from 'node:fs';

const root = 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const components = fs.readFileSync(root + 'NativeComponents.kt', 'utf8');
const screens = fs.readFileSync(root + 'NativeScreens.kt', 'utf8');
const shell = fs.readFileSync(root + 'NativePreviewApp.kt', 'utf8');

// A feature-first UI must not destroy working drafts when switching context.
assert.match(shell, /rememberSaveableStateHolder\(\)/);
assert.match(shell, /destinationStates\.SaveableStateProvider\(current\)/);
for (const name of ['sampleWeights', 'sampleReps', 'sampleRirs']) {
  assert.match(screens, new RegExp('var ' + name + ' by rememberSaveable'));
  assert.match(screens, new RegExp(name + '\\[exerciseIndex\\]'));
}
assert.match(screens, /values\.copyOf\(\)\.also/);
assert.doesNotMatch(screens, /weight = listOf\(185, 120, 185\)\[index\]/);

// Focus must never fork a logger or remove essential exercise/session controls.
assert.match(screens, /\/\/ Focus changes only the presentation; the session note remains available/);
assert.match(screens, /OutlinedTextField\(value = note/);
assert.match(screens, /Text\("Complete set · engine not connected"\)/);
assert.match(screens, /Text\("Generate real program · unavailable"\)/);

// Shared controls have understandable actions and enforce bounded demo inputs.
assert.match(components, /contentDescription = "Decrease \$title"/);
assert.match(components, /contentDescription = "Increase \$title"/);
assert.match(components, /minWidth = 48\.dp, minHeight = 48\.dp/);
assert.match(components, /enabled = canSubtract/);
assert.match(components, /enabled = canAdd/);
assert.match(screens, /canSubtract = reps > 1/);
assert.match(screens, /canSubtract = days > 3/);
assert.match(screens, /canAdd = days < 6/);
assert.match(screens, /Adapt exercises between blocks/);
assert.equal(screens.split('PreviewSettingSwitch(').length - 1, 2,
  'Plan adaptation switches must be composed from one shared native control');
assert.ok(components.includes('toggleable(value = checked, role = Role.Switch'),
  'The entire row, not only the switch thumb, owns the accessible action');
assert.ok(components.includes('Switch(checked = checked, onCheckedChange = null)'),
  'Avoid a second nested switch click owner');
assert.ok(components.includes('modifier = Modifier.weight(1f)'),
  'Section headings must yield room for narrow-screen trailing labels');
assert.ok(components.includes('overflow = TextOverflow.Ellipsis'),
  'Compact screen section metadata must not crowd the title');

const outline = fs.readFileSync(root + 'WorkoutOutlinePanel.kt', 'utf8');
const outlinePolicy = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/OutlinePresentation.kt',
  'utf8'
);
const packaged = fs.readFileSync(root + 'PackagedReadOnlySchemaVerifier.kt', 'utf8');
const activity = fs.readFileSync(root + 'MainActivity.kt', 'utf8');
assert.ok(packaged.includes('CanonicalJsSandboxProbe.verifyBundleBytes(binary, expected)') &&
  packaged.includes('SandboxBackupSchemaInspector(worker)') &&
  packaged.includes('openIfPackaged(context: Context)'),
  'Packaged read-only schema service must validate local JS bundle before any call');
assert.ok(packaged.includes('metadata.get("approvedForWorkoutExecution") === false') &&
  packaged.includes('metadata.get("androidSandboxParityVerified") === false'),
  'Bundle manifest may not claim training approval or sandbox parity certification');
assert.ok(!packaged.includes('previewAsync(') &&
  !packaged.includes('syntheticOutlineAsync(') &&
  !packaged.includes('lookupExercisesAsync(') &&
  !packaged.includes('openOutputStream(') &&
  !packaged.includes('takePersistableUriPermission('),
  'Read-only UI path must expose no prescriptions, writes or persistent grants');
assert.ok(activity.includes('readOnlyVerifier?.close()') &&
  activity.includes('PackagedReadOnlySchemaVerifier.openIfPackaged(this)'),
  'Native sandbox service must close with the activity lifecycle');

const screening = fs.readFileSync(root + 'BackupCopyScreeningPanel.kt', 'utf8');
const fingerprint = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ReadOnlyBackupScreening.kt',
  'utf8'
);
assert.ok(screens.includes('BackupCopyScreeningPanel(verifier = schemaVerifier)'),
  'Plan includes explicit, fingerprint-only file screening');
assert.ok(screening.includes('ActivityResultContracts.OpenDocument()'),
  'Android document picker requires user selection');
assert.ok(screening.includes('withContext(Dispatchers.IO)') &&
  screening.includes('ReadOnlyBackupScreening.fingerprint(source)'),
  'Fingerprint-only selection must remain available with no backend');
assert.ok(screening.includes('ReadOnlyBackupCandidateReader.screen(source)') &&
  screening.includes('screening.bytes.fill(0)') &&
  screening.includes('verifier!!.inspect('),
  'Explicit small-source schema selection must use bounded intake, no persistent bytes');
assert.ok(screening.includes('if (verifier != null)') &&
  screening.includes('Canonical file validation is not connected in this preview'),
  'Disconnected Compose preview cannot offer a fake schema validation');
assert.ok(screening.includes('is ReadOnlyDocumentScreening.FingerprintOnly') &&
  screening.includes('TooLargeForSchema'),
  'Oversized copies must never be silently truncated before canonical parsing');
assert.ok(screening.includes('var wantsSchema by remember') &&
  screening.includes('picker.launch(arrayOf("application/json", "text/plain"))'),
  'Canonical validation must require a separate explicit user picker action');

for (const forbidden of ['takePersistableUriPermission', 'SharedPreferences',
  'openOutputStream', 'rememberSaveable', 'parseStoredData(', 'SavedProgramOverviewPanel(']) {
  assert.ok(!screening.includes(forbidden),
    'Fingerprinted copies must not be persisted or grant data authority: ' + forbidden);
}
assert.ok(fingerprint.includes('MAX_COPY_BYTES = 50L * 1024 * 1024') &&
  fingerprint.includes('source.use { input ->') &&
  fingerprint.includes('MessageDigest.getInstance("SHA-256")'),
  'Screening streams are bounded, closed and fingerprinted');

const nativeUiWorkflow = fs.readFileSync(
  '.github/workflows/android-compose-ui-compile.yml', 'utf8'
);
assert.match(screens, /WorkoutOutlinePanel\(outline = null\)/,
  'Native Plan must default to unconnected read-only outline');
const savedPlan = fs.readFileSync(root + 'SavedProgramOverviewPanel.kt', 'utf8');
const planGate = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ReadOnlyProgramPresentation.kt',
  'utf8'
);
assert.ok(screens.includes('programSummary: ReadOnlyProgramSummary? = null') &&
  screens.includes('currentProgramReference: ReadOnlyProgramReference? = null'),
  'The native preview may not inject guessed personal program data');
assert.ok(screens.includes('ReadOnlyProgramPresentationPolicy.inspect(') &&
  screens.includes('programPresentation is ReadOnlyProgramPresentation.Structure'),
  'Saved-program UI must be behind the independent current-revision gate');
assert.ok(shell.includes('NativeDestination.PLAN -> PlanScreen(schemaVerifier = schemaVerifier)'),
  'The installable preview must not supply an unverified personal backup');
assert.ok(savedPlan.includes('presentation: ReadOnlyProgramPresentation.Structure'),
  'Shared saved-program composable must accept only policy-approved structure');
assert.ok(planGate.includes('ReadOnlyProgramReferenceGate.sameActiveRevision('),
  'Saved-program display must reject stale revisions and active tombstones');
assert.ok(!savedPlan.includes('generateNextProgramForShell(') &&
  !savedPlan.includes('prescribeSets(') && !savedPlan.includes('deleteHistory('),
  'Read-only summary UI may not invoke training or storage methods');
assert.ok(outline.includes('OutlinePresentationPolicy.inspect(outline)'),
  'Native outline must pass through the presentation gate');
assert.ok(outline.includes('ILLUSTRATIVE UI ONLY · NOT GENERATED OR SAVED'),
  'Layout-only data must not be mislabeled as canonical workout data');
assert.ok(outline.includes('SYNTHETIC ENGINE TEST · NOT YOUR PROGRAM'),
  'No synthetic engine session may be presented as a real user session');
assert.ok(outlinePolicy.includes('!outline.readOnly || !outline.syntheticOnly'),
  'Synthetic-only and read-only guards must both be required');
assert.ok(!outline.includes('deleteHistory(') &&
  !outline.includes('generateNextProgramForShell(') &&
  !outline.includes('previewAsync('),
  'Composable cannot directly mutate user data or invoke the training engine');
assert.match(nativeUiWorkflow, /:app:compileDebugKotlin/);
assert.doesNotMatch(nativeUiWorkflow, /assembleDebug/,
  'Routine Compose source commits must not assemble a preview APK');

console.log('PASS native preview context preservation, focus parity and touch accessibility source contracts');
