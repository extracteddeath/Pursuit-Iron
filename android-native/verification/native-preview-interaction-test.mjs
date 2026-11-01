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
const currentPlanHost = fs.readFileSync(root + 'NativePlanPresentationHost.kt', 'utf8');
assert.ok(currentPlanHost.includes('LocalLifecycleOwner.current') &&
  currentPlanHost.includes('Lifecycle.Event.ON_STOP') &&
  currentPlanHost.includes('session.onBackground()') &&
  currentPlanHost.includes('LaunchedEffect(session, requestVersion, sourceObserved)') &&
  currentPlanHost.includes('source.subscribeCanonicalChanges') &&
  currentPlanHost.includes('DisposableEffect(source, session, selectedProgramId)') &&
  currentPlanHost.includes('if (selectedProgramId == null) null else runCatching') &&
  currentPlanHost.includes('session.current() === presentation') &&
  currentPlanHost.includes('session.onCanonicalStoreChanged()') &&
  currentPlanHost.includes('Channel<Unit>(Channel.CONFLATED)') &&
  currentPlanHost.includes('if (!sourceObserved) return@LaunchedEffect') &&
  currentPlanHost.includes('remember(session, selectedProgramId, storeChangeKey)') &&
  !currentPlanHost.includes('rememberSaveable') &&
  !currentPlanHost.includes('BackupSavedProgramInventory'),
  'Personal Plan structure must be ephemeral and cleared after lifecycle changes');
assert.ok(shell.includes('UnconnectedCurrentReadOnlyPlanSource') &&
  shell.includes('selectedProgramId = if (section == NativeDestination.PLAN.ordinal)') &&
  shell.includes('storeChangeKey = canonicalStoreChangeKey') &&
  shell.includes('currentPlan = visiblePlan') &&
  shell.includes('else ReadOnlyProgramPresentation.Unavailable'),
  'Plan navigation must invalidate old content without giving backups authority');

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
  activity.includes('PackagedReadOnlySchemaVerifier.openIfPackaged(') &&
  activity.includes('assetScope.launch') &&
  activity.includes('CoroutineScope(SupervisorJob() + Dispatchers.IO)') &&
  activity.includes('withContext(NonCancellable + Dispatchers.Main.immediate)') &&
  activity.indexOf('setContent {') < activity.indexOf('assetScope.launch') &&
  activity.includes('if (released) loaded?.close()'),
  'Read-only bundle must load off the first frame, close on destruction, and never leak cancelled results');

const backupInventoryUi = fs.readFileSync(root + 'BackupSavedProgramInventoryPanel.kt','utf8');
const approvedProgramUi = fs.readFileSync(root + 'SavedProgramOverviewPanel.kt','utf8');
assert.ok(components.includes('internal fun ReadOnlyTrainingDayRow(') &&
  components.includes('MaterialTheme.shapes.small') &&
  backupInventoryUi.includes('ReadOnlyTrainingDayRow(') &&
  approvedProgramUi.includes('ReadOnlyTrainingDayRow(') &&
  !components.includes('ReadOnlyProgramTransportCodec') &&
  !components.includes('CurrentReadOnlyPlanSource'),
  'Current and backup Plan rows must reuse one M3 presentation without merging authority');

assert.ok(backupInventoryUi.includes('BACKUP COPY · NOT YOUR ACTIVE PLAN') &&
  backupInventoryUi.includes('MaterialTheme.shapes.large') &&
  backupInventoryUi.includes('surfaceContainerHigh') &&
  backupInventoryUi.includes('remember(inventory) { mutableStateOf<String?>(null) }') &&
  backupInventoryUi.includes('TextButton(') &&
  backupInventoryUi.includes('View backup days') &&
  backupInventoryUi.includes('entry.days.forEachIndexed') &&
  !backupInventoryUi.includes('SavedProgramOverviewPanel(') &&
  !backupInventoryUi.includes('deleteHistory(') &&
  !backupInventoryUi.includes('openTrain('),
  'M3E backup list must look native and only expand read-only source metadata');
const screening = fs.readFileSync(root + 'BackupCopyScreeningPanel.kt', 'utf8');
const fingerprint = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ReadOnlyBackupScreening.kt',
  'utf8'
);
assert.ok(screens.includes('BackupCopyScreeningPanel(verifier = schemaVerifier, verifierLoading = schemaVerifierLoading)'),
  'Plan includes an asynchronous verifier gate and explicit file screening');
assert.ok(screening.includes('ActivityResultContracts.OpenDocument()'),
  'Android document picker requires user selection');
assert.ok(screening.includes('withContext(Dispatchers.IO)') &&
  screening.includes('ReadOnlyBackupScreening.fingerprint(') &&
  screening.includes('checkActive = { job.ensureActive() }'),
  'Fingerprint-only selection must remain available and cancellable with no backend');
assert.ok(screening.includes('ReadOnlyBackupCandidateReader.screen(') &&
  screening.split('checkActive = { job.ensureActive() }').length === 3 &&
  screening.includes('screening.bytes.fill(0)') &&
  screening.includes('verifier!!.inspect('),
  'Explicit small-source schema selection must use bounded intake, no persistent bytes');
assert.ok(screening.includes('if (verifier != null)') &&
  screening.includes('if (verifierLoading)') &&
  screening.includes('Checking local read-only engine compatibility') &&
  screening.includes('Canonical file validation is not connected in this preview'),
  'Disconnected Compose preview cannot offer a fake schema validation');
assert.ok(screening.includes('1 MiB read-only schema-check limit'),
  'Oversized file copy text must match transport limits');
assert.ok(screening.includes('is ReadOnlyDocumentScreening.FingerprintOnly') &&
  screening.includes('TooLargeForSchema'),
  'Oversized copies must never be silently truncated before canonical parsing');
assert.ok(screening.includes('var wantsPrograms by remember') &&
  screening.includes('verifier!!.inspectPrograms(') &&
  screening.includes('BackupScreeningStage.ProgramInventory(') &&
  screening.includes('View saved programs in backup') &&
  screening.includes('BackupSavedProgramInventoryPanel(current.inventory)'),
  'Program inventory must require explicit selection and be clearly backup-only');
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
assert.ok(screens.includes('currentPlan: ReadOnlyProgramPresentation = ReadOnlyProgramPresentation.Unavailable') &&
  !screens.includes('ReadOnlyProgramPresentationPolicy.inspect(') &&
  !screens.includes('currentProgramReference:'),
  'The native preview cannot self-authorize guessed program data');
assert.ok(screens.includes('currentPlan is ReadOnlyProgramPresentation.Structure') &&
  screens.includes('SavedProgramOverviewPanel(currentPlan)'),
  'Native Plan accepts only approved read-only structure');
assert.ok(shell.includes('NativeDestination.PLAN -> PlanScreen(') &&
  shell.includes('schemaVerifierLoading = schemaVerifierLoading'),
  'The installable preview must not supply an unverified personal backup');
assert.ok(savedPlan.includes('presentation: ReadOnlyProgramPresentation.Structure'),
  'Shared saved-program composable must accept only policy-approved structure');
const sourceContract = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/CurrentReadOnlyPlanGateway.kt', 'utf8'
);
const canonicalAdapter = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/CanonicalCurrentPlanSourceAdapter.kt', 'utf8'
);
assert.ok(canonicalAdapter.includes('ReadOnlyProgramTransportCodec.decode(') &&
  canonicalAdapter.includes('ReadOnlyProgramPresentationPolicy.inspect(') &&
  canonicalAdapter.includes('readCurrentProjection(') &&
  canonicalAdapter.includes('subscribeCommittedChanges(') &&
  !canonicalAdapter.includes('SharedPreferences') &&
  !canonicalAdapter.includes('openOutputStream') &&
  !canonicalAdapter.includes('parseStoredData(') &&
  !shell.includes('CanonicalCurrentPlanSourceAdapter('),
  'Current canonical projection adapter must remain separate from offline backups and the shipping preview');
assert.ok(sourceContract.includes('fun subscribeCanonicalChanges(onChanged: () -> Unit): AutoCloseable') &&
  sourceContract.includes('override fun subscribeCanonicalChanges('),
  'Current store adapter requires explicit, disposable canonical change monitoring');
assert.ok(planGate.includes('ReadOnlyProgramReferenceGate.sameActiveRevision(') &&
  planGate.includes('CurrentProgramDeletionEvidence.CheckedAbsent') &&
  planGate.includes('class Structure internal constructor(') &&
  !planGate.includes('data class Structure') &&
  planGate.includes('Collections.unmodifiableList('),
  'Plan structure requires explicit deletion evidence and restricted constructor');
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
