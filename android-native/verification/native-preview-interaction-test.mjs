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
  assert.ok(screens.includes('previewValueAt(' + name + ', valueIndex)'));
}
assert.match(screens, /12 -> values\.copyOf\(\)/);
assert.match(screens, /3 -> IntArray\(12\) \{ i -> values\[i \/ 4\] \}/);
assert.match(screens, /3 -> values\[index \/ 4\]/);
assert.match(screens, /expanded\[index\] = value/);
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
assert.match(screens, /title = "Reps", value = reps, min = 1, max = 50, step = 1/);
assert.match(screens, /var setIndex by rememberSaveable/);
assert.match(screens, /val valueIndex = exerciseIndex \* 4 \+ setIndex/);
assert.match(screens, /Text\("Next sample"\)/);
assert.match(screens, /Text\("Previous sample"\)/);
assert.ok(components.includes('internal fun PreviewNumericStepper(') &&
  components.includes('OutlinedTextField(') &&
  components.includes('keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)') &&
  components.includes('it in min..max && (it - min) % step == 0') &&
  components.includes('enabled = validInput'),
  'Reusable direct numeric set editor must reject invalid or unaligned sample values');
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
  shell.includes('selectedTrainingDay?.reference?.programId == activeProgramId') &&
  shell.includes('storeChangeKey = canonicalStoreChangeKey') &&
  shell.includes('currentPlan = visiblePlan') &&
  shell.includes('else ReadOnlyProgramPresentation.Unavailable'),
  'Plan navigation must invalidate old content without giving backups authority');

const syntheticReader = fs.readFileSync(root + 'CanonicalBackupSchemaVerifier.kt', 'utf8');
const syntheticPanel = fs.readFileSync(root + 'SyntheticCanonicalWorkoutPanel.kt', 'utf8');
assert.ok(syntheticReader.includes('internal interface CanonicalSyntheticOutlineReader') &&
  packaged.includes('SandboxWorkoutOutline(worker)') &&
  packaged.includes('outlines.inspect(split, dayIndex)') &&
  shell.includes('syntheticOutlineReader = syntheticOutlineReader') &&
  activity.includes('syntheticOutlineReader = readOnlyVerifier') &&
  screens.includes('SyntheticCanonicalWorkoutPanel(reader = syntheticOutlineReader)') &&
  syntheticPanel.includes('LaunchedEffect(reader, request)') &&
  syntheticPanel.includes('withContext(Dispatchers.IO)') &&
  syntheticPanel.includes('OutlinePresentationPolicy.inspect(it) is OutlinePresentation.SyntheticOnly') &&
  syntheticPanel.includes('WorkoutOutlinePanel(outline = outline)') &&
  syntheticPanel.includes('enabled = reader != null && !loading') &&
  syntheticPanel.includes('request = null') &&
  syntheticPanel.includes('if (isActive)') &&
  !syntheticPanel.includes('SharedPreferences') &&
  !syntheticPanel.includes('Complete set') &&
  !syntheticPanel.includes('onCompleteSet'),
  'Canonical synthetic Train explorer must use only the existing pinned worker, gate displays and never enable a workout');
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
  screens.includes('SavedProgramOverviewPanel(currentPlan) { dayId ->'),
  'Native Plan accepts only approved read-only structure');
assert.ok(shell.includes('NativeDestination.PLAN -> PlanScreen(') &&
  shell.includes('schemaVerifierLoading = schemaVerifierLoading'),
  'The installable preview must not supply an unverified personal backup');
const dayJourney = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ReadOnlyTrainingDayJourney.kt', 'utf8'
);
const selectedDayUi = fs.readFileSync(root + 'ReadOnlyTrainingDayPanel.kt','utf8');
assert.ok(dayJourney.includes('selection.reference != program.reference') &&
  dayJourney.includes('ReadOnlyProgramPresentation.Structure') &&
  dayJourney.includes('ReadOnlyTrainingDayView.Unavailable') &&
  screens.includes('ReadOnlyTrainingDayJourney.select(currentPlan, dayId)') &&
  screens.includes('ReadOnlyTrainingDayJourney.inspect(currentPlan, selectedDay)') &&
  shell.includes('var selectedTrainingDay by remember {') &&
  !shell.includes('rememberSaveable { mutableStateOf<ReadOnlyTrainingDaySelection') &&
  selectedDayUi.includes('ReadOnlyTrainingDayView.Structure') &&
  selectedDayUi.includes('Workout execution is not connected') &&
  selectedDayUi.includes('ReadOnlyMovementIdentityRow(') &&
  components.includes('internal fun ReadOnlyMovementIdentityRow(') &&
  !selectedDayUi.includes('exerciseCount = 0') &&
  !selectedDayUi.includes('onCompleteSet') &&
  !selectedDayUi.includes('logSet('),
  'Read-only Plan to Train journey must require the same approved revision and disable workout logging');
assert.ok(savedPlan.includes('presentation: ReadOnlyProgramPresentation.Structure'),
  'Shared saved-program composable must accept only policy-approved structure');
const sourceContract = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/CurrentReadOnlyPlanGateway.kt', 'utf8'
);
const canonicalAdapter = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/CanonicalCurrentPlanSourceAdapter.kt', 'utf8'
);
const bytePort = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ExactCurrentSnapshotProjectionPort.kt', 'utf8'
);
assert.ok(bytePort.includes('MessageDigest.getInstance("SHA-256")') &&
  bytePort.includes('CodingErrorAction.REPORT') &&
  bytePort.includes('ReadOnlyBackupLimits.MAX_SOURCE_BYTES') &&
  bytePort.includes('candidate?.fill(0)') &&
  bytePort.includes('source.fill(0)') &&
  !shell.includes('ExactCurrentSnapshotProjectionPort('),
  'Current snapshot SHA gate must be bounded, strict UTF-8, wiping, and remain unconnected');
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
const setReviewNav = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/SyntheticSetReviewNavigation.kt', 'utf8'
);
assert.ok(components.includes('internal fun TrainingSetHeader(') &&
  screens.includes('TrainingSetHeader(') &&
  outline.includes('TrainingSetHeader(') &&
  components.includes('LinearProgressIndicator(') &&
  components.includes('numerator.toFloat() / denominator'),
  'The demo and canonical workout preview must share one Material 3 set header');
assert.ok(setReviewNav.includes('approved.outline.exercises') &&
  setReviewNav.includes('fun next(') &&
  setReviewNav.includes('fun previous(') &&
  setReviewNav.includes('fun jumpToExercise(') &&
  setReviewNav.includes('totalWorkoutSets = exercises.sumOf { it.sets }') &&
  outline.includes('SyntheticSetReviewNavigation.inspect(presentation, cursor)') &&
  outline.includes('SyntheticSetReviewPanel(') &&
  outline.includes('Browse all exercises') &&
  outline.includes('Review these sets') &&
  outline.includes('Complete set · personal engine not connected') &&
  !outline.includes('saveWorkout(') &&
  !outline.includes('logSet('),
  'Engine-derived set-by-set review must be navigation only and never grant workout logging');
assert.ok(outlinePolicy.includes('Collections.unmodifiableList(ArrayList(outline.exercises))'),
  'Synthetic outline must not retain mutable source-list aliases');
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
