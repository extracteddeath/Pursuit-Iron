import assert from 'node:assert/strict';
import fs from 'node:fs';
const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const ui=fs.readFileSync(root+'NativeOnboardingScreen.kt','utf8');
const screens=fs.readFileSync(root+'NativeScreens.kt','utf8');
const host=fs.readFileSync(root+'NativeRuntimeHost.kt','utf8');
const activity=fs.readFileSync(root+'MainActivity.kt','utf8');
const shell=fs.readFileSync(root+'NativePreviewApp.kt','utf8');
const train=fs.readFileSync(root+'NativeWorkoutScreen.kt','utf8');
for (const field of ['name','goal','experience','unit','split','days','session','equipment','bodyweight','supersets','barbellCap'])
  assert.match(ui,new RegExp('var '+field+' by rememberSaveable'));
assert.ok(ui.includes('state.options?.splits') && ui.includes('selected.days.forEach') &&
  ui.includes('if(days !in option.days)days=option.days.first()'),
  'Split/day compatibility must be canonical option data, not a second Kotlin topology engine');
assert.ok(ui.includes('Review generated plan') && ui.includes('Save program') && ui.includes('Edit choices') &&
  ui.includes('if (proposal!=null)') && ui.includes('ReadOnlyTrainingDayRow('));
assert.ok(!ui.includes('.initialize(') && !ui.includes('generateNextProgramForShell') &&
  !ui.includes('commitAtomically') && !ui.includes('Complete set'));
assert.ok(host.includes('owner.initialize(proposal.snapshot)') &&
  host.includes('saved.creationId==proposal.creationId') &&
  host.includes('finally { bytes.fill(0) }') && host.includes('blocked=true') &&
  host.includes('actionVersion.get()==version'));
assert.ok(activity.includes('onReviewProgram=runtime::review') && activity.includes('onSaveProgram=runtime::save') &&
  activity.includes('activeProgramId=state.saved?.program?.id') && shell.includes('onboarding.saved==null'));
assert.ok(shell.includes('UnconnectedEngineGateway().status'),
  'First-program persistence must not authorize workout logging');
// Injury-area review uses source-backed muscle involvement and never mutates
// a prescription. Navigation to the already-confirmed skip action is explicit.
assert.ok(host.includes('fun inspectWorkoutMuscles()') &&
  host.includes('engine.inspectWorkoutMuscles(source,plan.issuedJson)') &&
  host.includes('NativeWorkoutMuscleReviewCodec.matchesOrigin(source,plan)') &&
  activity.includes('onInspectMuscles=runtime::inspectWorkoutMuscles') &&
  shell.includes('onCorrectSet,onSkipExercise,onInspectMuscles'),
  'Muscle review must come from pinned canonical issue, not screen-side training guesses');
// Related-muscle bulk skips share the exact journal receipts with exercise
// skips. The user must confirm the selected muscle and count first.
assert.ok(host.includes('fun skipAffectedMuscle(part: String)') &&
  host.includes('NativeSessionSkipPolicy.remainingAffected(it,review,part)') &&
  host.includes('private fun skipSelectedIssuedSlots(') &&
  host.includes('for(slot in select(active.journal))') &&
  activity.includes('onSkipAffectedMuscle=runtime::skipAffectedMuscle') &&
  shell.includes('onSkipExercise,onInspectMuscles,onSkipAffectedMuscle') &&
  train.includes('confirmAffectedPart') &&
  train.includes('Confirm related skips') &&
  train.includes('onSkipAffectedMuscle(confirmedPart)') &&
  train.includes('Completed work is preserved.') &&
  train.includes('it.exerciseIndex in relatedIndices && it !in committedSlots'),
  'Muscle-wide skipping must be explicitly confirmed and only save unresolved issued slots');
// An injury-related dose reduction has a stable original-issued-set boundary.
assert.ok(host.includes('fun reduceAffectedMuscle(part: String)') &&
  host.includes('NativeSessionSkipPolicy.reduceAffected(it,review,part)') &&
  activity.includes('onReduceAffectedMuscle=runtime::reduceAffectedMuscle') &&
  shell.includes('onSkipAffectedMuscle,onReduceAffectedMuscle') &&
  train.includes('NativeSessionSkipPolicy.reduceAffected(journal,review,affectedPart)') &&
  train.includes('Confirm reduced work') &&
  train.includes('onReduceAffectedMuscle(approved)') &&
  train.includes('The first half of each original prescription remains available'),
  'Reduced planned work must be confirmed and originate from stable canonical issued slots');
// User-selected muscle adjustments must persist as versioned, bounded
// SKIPPED receipts instead of a UI-only "reason" that vanishes on restart.
assert.ok(host.includes('JournalSetAdjustment("skip",part)') &&
  host.includes('JournalSetAdjustment("reduce",part)') &&
  host.includes('JournalSetDisposition.SKIPPED,null,adjustment=adjustment'),
  'Confirmed muscle adjustments must use the persisted journal annotation contract');
// Short-session previews come only from the canonical engine and cannot
// masquerade as an applied session or a UI-computed training prescription.
assert.ok(screens.includes('SectionLabel("Shorter session", "Canonical engine preview")') &&
  screens.includes('previewShorterSession(minutes)') &&
  screens.includes('Review first. No workout is shortened until you choose Start.') &&
  screens.includes('if(showShorterSession) {') &&
  activity.includes('onPreviewShortSession=runtime::previewShorterSession') &&
  shell.includes('previewShorterSession=onPreviewShortSession') &&
  shell.includes('startShortWorkout=onStartShortWorkout') &&
  activity.includes('onStartShortWorkout={runtime.startWorkout(it)}') &&
  screens.includes('startShortWorkout(preview.minutes)') &&
  screens.includes('if(preview.fits && preview.changed)') &&
  host.includes('NativeShortSessionPreviewCodec.matchesSource(') &&
  train.includes('Your logged dose will be marked time-limited') &&
  host.includes('engine.previewShorterSession(snapshot,programId,minutes)') &&
  host.includes('shortPreviewBusy=true,shortPreview=null') &&
  host.includes('latest.storeChangeKey==current.storeChangeKey') &&
  host.includes('!latest.workoutBusy') &&
  !screens.includes('planShorterSession('),
  'Shorter session UI must show a revision-checked canonical preview without changing the workout');
// Real Train controls are journal-driven; UI must never manufacture a persisted
// set or publish a workout directly from an unconfirmed tap.
const controls=fs.readFileSync(root+'NativeComponents.kt','utf8');
assert.ok(train.includes('val committedSlots=journal.completed.map { it.intent.slot }.toSet()') &&
  train.includes('val nextPreview=NativeNextSetPolicy.inspect(progress,exerciseIndex)') &&
  train.includes('NativeUpNextPanel(preview,exerciseIndex,') &&
  train.includes('onOpenExercise={ position ->exerciseIndex=position }') &&
  !train.includes('Go to next unfinished exercise') &&
  fs.readFileSync(root+'NativeUpNextPanel.kt','utf8').includes('preview.upNext'),
  'Train next-up navigation must use the exact issued slot and committed journal, not optimistic taps or exercise ID matches');
assert.ok(train.includes('showSkipConfirmation') &&
  train.includes('showSkipConfirmation=false') &&
  train.includes('onRecord(set.slot,JournalSetDisposition.SKIPPED,null)') &&
  train.includes('Skip set') &&
  train.includes('Keep set'),
  'Skipping requires an explicit confirmation; a tap must not silently discard a working set');
assert.ok(train.includes('showFinishReview') &&
  train.includes('journal.unresolvedCount==0') &&
  train.includes('journal.completed.count { it.intent.disposition==JournalSetDisposition.SKIPPED }') &&
  train.includes('Finish workout') &&
  train.includes('Keep training'),
  'Finishing requires all durable slots and a review of completed and skipped observations');
assert.ok(train.includes('var showLast by rememberSaveable(plan.session.workoutId)') &&
  train.includes('FilterChip(selected=!showLast') && train.includes('FilterChip(selected=showLast') &&
  train.includes('if(showLast)Text(set.last?.let') &&
  train.includes('LinearProgressIndicator(progress={journal.completed.size.toFloat()/plan.slots.size}'),
  'Last/target is a single session-scoped presentation choice and progress counts acknowledged sets only');
assert.ok(train.includes('val setDraftStates=rememberSaveableStateHolder()') &&
  train.includes('setDraftStates.SaveableStateProvider(') &&
  train.includes('${plan.session.workoutId}:${set.slot.exerciseIndex}:${set.slot.setIndex}') &&
  train.includes('NativeWorkingSet(plan.session,set,commit'),
  'Exercise navigation must not destroy another set’s unsaved load/reps/RIR draft');
assert.ok(train.includes('NativeSetValueFields(session.unit,load,reps,rir,!busy') &&
  controls.includes('input.length<=10 && input.all {it.isDigit() || it==\'.\'}') &&
  controls.includes('input.length<=3 && input.all(Char::isDigit)') &&
  controls.includes('input.length<=2 && input.all(Char::isDigit)') &&
  train.includes('NativeSetEntryPolicy.accepts(load,reps,rir,set.allowFailedAttempt)') &&
  controls.includes('NativeSetEntryPolicy.stepReps(reps,-1,allowFailedAttempt)') &&
  controls.includes('NativeSetEntryPolicy.stepRir(rir,1)') &&
  controls.includes('minWidth=48.dp,minHeight=48.dp') &&
  !controls.includes('stepLoad('),
  'All set editors share bounded keyboard-and-stepper entry, touch targets and no made-up equipment increments');
const verifiedLoadUi=fs.readFileSync(root+'NativeVerifiedLoadOptions.kt','utf8');
assert.ok(verifiedLoadUi.includes(String.raw`(\\.[0-9]{1,3})?`) &&
  !verifiedLoadUi.includes(String.raw`(\\\\.[0-9]{1,3})?`),
  'Equipment load lookup must accept true fractional weights without permitting a literal-backslash input');
assert.ok(train.includes('onInspectEquipmentLoad:(JournalSetSlot,String)->Unit') &&
  train.includes('NativeVerifiedLoadOptions(') &&
  train.includes('onLoad={load=it},onInspect=onInspectEquipmentLoad)') &&
  verifiedLoadUi.includes('it.workoutId==session.workoutId && it.slot==set.slot') &&
  verifiedLoadUi.includes('it.unit==session.unit && it.inputLoad==load') &&
  verifiedLoadUi.includes('if(review?.confirmed==true)') &&
  verifiedLoadUi.includes('val previous=review.previous') &&
  verifiedLoadUi.includes('if(previous!=null)') &&
  verifiedLoadUi.includes('val next=review.next') &&
  verifiedLoadUi.includes('if(next!=null)') &&
  verifiedLoadUi.includes('No assumed load increments are offered') &&
  verifiedLoadUi.includes('if(!review.exact)') &&
  verifiedLoadUi.includes('About these loads') &&
  verifiedLoadUi.includes('onLoad(previous)') &&
  verifiedLoadUi.includes('onLoad(next)') &&
  !verifiedLoadUi.includes('stepLoad(') &&
  !verifiedLoadUi.includes('commitAtomically') &&
  host.includes('NativeEquipmentLoadingCodec.matchesOrigin(snapshot,progress.plan)') &&
  host.includes('engine.inspectEquipmentLoad(snapshot,progress.plan.issuedJson,slot,input)') &&
  host.includes('latest.storeChangeKey==initial.storeChangeKey') &&
  activity.includes('onInspectEquipmentLoad=runtime::inspectEquipmentLoad'),
  'Compact equipment options must use an exact canonical verified result for the current set, typed load and source revision; no inferred increments or writes');
assert.ok(train.includes('var focusMode by rememberSaveable(plan.session.workoutId)') &&
  train.includes('val visibleSets=if(focusMode && (journal.unresolvedCount==0 || exercisePending==0))') &&
  train.includes('else if(focusMode)listOfNotNull(exercise.sets.firstOrNull') &&
  train.includes('it.slot !in committedSlots') &&
  train.includes('visibleSets.forEach { set->') &&
  train.includes('NativeWorkingSet(plan.session,set,commit,') &&
  train.includes('val writeBlocked=operationBlocked || journal.isFinished') &&
  train.includes('NativeWorkingSet(plan.session,set,commit,writeBlocked,') &&
  train.includes('onCorrect,state.workoutError,state,onInspectEquipmentLoad,') &&
  train.includes('FilterChip(selected=focusMode,onClick={focusMode=!focusMode}') &&
  train.includes('val setDraftStates=rememberSaveableStateHolder()'),
  'Focus mode must project the identical receipt-based set logger and preserve unsent drafts');
assert.ok(train.includes('var observedFocusReceipt by rememberSaveable(plan.session.workoutId)') &&
  train.includes('LaunchedEffect(lastSetReceiptId,focusMode,state.workoutBusy,state.historyBusy,exerciseIndex)') &&
  train.includes('NativeNextSetPolicy.advanceAfterAcknowledged(') &&
  train.includes('if(destination!=null)exerciseIndex=destination') &&
  train.includes('if(!focusMode || journal.isFinished)observedFocusReceipt=lastSetReceiptId'),
  'Focus may cross exercise boundaries only after a fresh persisted receipt and must suppress replay after recovery');
assert.ok(train.includes('var showAffectedTools by rememberSaveable(plan.session.workoutId)') &&
  train.includes('TextButton(onClick={showAffectedTools=true}') &&
  train.includes('Text("Adjust workout for discomfort")') &&
  train.includes('TextButton(onClick={showAffectedTools=false})') &&
  train.includes('NativeSessionSkipPolicy.reduceAffected(journal,review,affectedPart)') &&
  train.includes('Confirm reduced work'),
  'Compact discomfort tools must retain the real confirmation and journal-backed reductions');
assert.ok(train.includes('val view=LocalView.current') &&
  train.includes('DisposableEffect(view,progress?.plan?.session?.workoutId)') &&
  train.includes('if(progress!=null)view.keepScreenOn=true') &&
  train.includes('onDispose { view.keepScreenOn=prior }'),
  'Keep screen awake only while a real workout is rendered and restore the original view setting');
const rest=fs.readFileSync(root+'NativeRestTimer.kt','utf8');
assert.ok(train.includes('NativeRestTimer(plan.session.workoutId, activeExtension ?: exercise.restSeconds,') &&
  rest.includes('rememberSaveable(workoutId)') &&
  rest.includes('SystemClock.elapsedRealtime()') &&
  rest.includes('LaunchedEffect(deadline, startedElapsed, startedWall)') &&
  rest.includes('delay(250L)') &&
  rest.includes('onLongClickLabel = "Reset rest timer"') &&
  rest.includes('onLongClick = {') && rest.includes('deadline = 0L') &&
  rest.includes('enabled = enabled') &&
  !rest.includes('onRecord(') && !rest.includes('store.') && !rest.includes('journal.'),
  'The rest countdown must be a session-scoped manual M3 control, not an unacknowledged set write or engine re-prescription');
assert.ok(rest.includes('var startedElapsed by rememberSaveable(workoutId)') &&
  rest.includes('var startedWall by rememberSaveable(workoutId)') &&
  rest.includes('var activeSeconds by rememberSaveable(workoutId)') &&
  rest.includes('elapsed < startedElapsed || wall < startedWall') &&
  rest.includes('abs(wallAge - monotonicAge) <= 60_000L') &&
  rest.includes('activeSeconds=seconds') &&
  rest.includes('activeSeconds = 0') &&
  rest.includes('var observedEvent by rememberSaveable(workoutId)') &&
  rest.includes('LaunchedEffect(latestReceiptOperationId,enabled)') &&
  rest.includes('latestReceiptOperationId!=observedEvent') &&
  rest.includes('autoCue?.operationId==latestReceiptOperationId') &&
  rest.includes('begin(autoCue.seconds)') &&
  train.includes('autoCue=NativeRestCuePolicy.latest(progress,exercise.index)') &&
  train.includes('latestReceiptOperationId=journal.events.lastOrNull()?.intent?.operationId'),
  'Rest countdown must use the issued duration after a new durable set receipt, not a pending save, and must not replay after recovery');

assert.ok(train.includes('set.isExtension') &&
  train.includes('set.extensionRestSeconds?.let') &&
  train.includes('if(set.isExtension)"Complete extension"') && train.includes('else "Complete set"') &&
  train.includes('val activeExtension=if(focusMode)exercise.sets.firstOrNull {') &&
  train.includes('activeExtension ?: exercise.restSeconds'),
  'Myo/drop extensions must remain independently visible, loggable and rest-aware');
assert.ok(train.includes('JournalSetValues(load,session.unit,checkNotNull(repCount),effort)') &&
  train.includes('var rir by rememberSaveable { mutableStateOf("") }') &&
  train.includes('if(journal.isFinished)onFinish() else showFinishReview=true'),
  'The review must not synthesize actual RIR or block an exact retry of a durably finished session');
// Exercise-level bulk skip is an explicit confirmation and serialized durable
// journal loop, not multiple optimistic UI taps or a training re-prescription.
const skipPolicy=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeSessionSkipPolicy.kt','utf8');
assert.ok(train.includes('var showSkipExercise by rememberSaveable(plan.session.workoutId)') &&
  train.includes('Text("Skip remaining ${exercisePending} sets for this exercise")') &&
  train.includes('onSkipExercise(exercise.index)') &&
  train.includes('Completed sets stay unchanged') &&
  train.includes('Skip remaining sets') &&
  activity.includes('onSkipExercise=runtime::skipRemainingExercise') &&
  shell.includes('onSkipExercise: (Int)->Unit = {}') &&
  shell.includes('onCorrectSet,onSkipExercise') &&
  host.includes('fun skipRemainingExercise(exerciseIndex: Int)') &&
  host.includes('owner.recoverIssuedSession(recovery)') &&
  host.includes('NativeSessionSkipPolicy.remaining(it,exerciseIndex)') &&
  host.includes('private fun skipSelectedIssuedSlots(') &&
  host.includes('for(slot in select(active.journal))') &&
  host.includes('recovery.recordSet(active.plan.session,active.journal.revision,intent)') &&
  host.includes('JournalSetDisposition.SKIPPED,null') &&
  host.includes('it.copy(workout=active,workoutBusy=true)') &&
  skipPolicy.includes('journal.completed.map { it.intent.slot }.toSet()') &&
  skipPolicy.includes('journal.slots.filter { it.exerciseIndex==exerciseIndex }'),
  'Confirmed per-exercise skip saves only unresolved canonical journal slots, one acknowledgement at a time');
// History navigation comes only from the owned, revision-bound page reader.
const historyUi=fs.readFileSync(root+'NativeHistoryPanel.kt','utf8');
const historyFilters=fs.readFileSync(root+'NativeHistoryFilters.kt','utf8');
assert.ok(shell.includes('NativeDestination.INSIGHTS -> InsightsScreen(onboarding,onCorrectHistory,onReloadHistory,') &&
  screens.includes('internal fun InsightsScreen(state: NativeRuntimeState?,') &&
  screens.includes('if(state?.saved!=null)NativeHistoryPanel(') && screens.includes('NO SAVED WORKOUTS') &&
  historyUi.includes('val page=state.historyPage') && historyUi.includes('page.entries.forEach { summary ->') &&
  historyUi.includes('onOpen(summary.historyId)') && historyUi.includes('if(summary.detailsAvailable)') &&
  activity.includes('onOpenHistory=runtime::openHistory') && activity.includes('onHistoryPage=runtime::loadHistoryPage') &&
  host.includes('Browsing an older workout never replaces the latest completion receipt.') &&
  host.includes('if(it.storeChangeKey==current.storeChangeKey)') &&
  shell.includes('val pageScroll=key(scrollContext) { rememberScrollState() }'),
  'Insights must browse owned saved history, keep completion separate and reject late stale reads');
assert.ok(historyUi.includes('NativeHistoryFilters(state.historyFilter,') &&
  historyUi.includes('NO MATCHING WORKOUTS') && historyUi.includes('page.unfilteredTotal') &&
  historyFilters.includes('rememberSaveable(applied.search)') &&
  historyFilters.includes('NativeHistoryDates.range(search.trim(),') &&
  historyFilters.includes('DateRangePicker(state=picker,') &&
  historyFilters.includes('selectableDates=dates') &&
  historyFilters.includes('DialogProperties(usePlatformDefaultWidth=false)') &&
  historyFilters.includes('ImeAction.Search') && historyFilters.includes('Clear filters') &&
  activity.includes('onFilterHistory=runtime::filterHistory') &&
  host.includes('optionalHistoryPage(current.historyPage?.offset ?: 0,history.snapshotSha256,current.historyFilter)') &&
  host.includes('prior.filter!=filter') && shell.includes('onboarding?.historyFilter}'),
  'Search/date drafts use native controls; applied filters persist through paging, corrections and scroll contexts');
assert.ok(host.includes('val completion=owner.lastCompletionReceipt()') &&
  host.includes('Starting another workout cannot erase the last durable completion receipt.') &&
  !host.includes('workoutError=null,completion=null'),
  'Starting the next workout must not hide the previous confirmed history receipt');
const activeEdit=fs.readFileSync(root+'NativeActiveSetCorrection.kt','utf8');
assert.ok(controls.includes('internal fun NativeSetEditSheet(') &&
  controls.includes('confirmValueChange={it!=SheetValue.Hidden || !currentBusy && currentCanDismiss()}') &&
  controls.includes('NativeEditSheet(busy,onDismiss,content=content)') &&
  controls.includes('imePadding().verticalScroll(rememberScrollState())') &&
  historyUi.includes('NativeSetEditSheet(historyEditorBusy,') && activeEdit.includes('NativeSetEditSheet(busy,'),
  'Both correction sheets share keyboard layout and prevent drag-dismissal during an unacknowledged save');
assert.ok(train.includes('NativeActiveSetCorrection(session,set,commit,busy,error,onCorrect)') &&
  activeEdit.includes('rememberSaveable(commit.intent.operationId)') &&
  activeEdit.includes('NativeSetValueFields(session.unit,load,reps,rir,!busy') &&
  activeEdit.includes('onCorrect(JournalSetIntent(id,set.slot,disposition,values,commit.intent.operationId))') &&
  activeEdit.includes('pendingDisposition==JournalSetDisposition.SKIPPED.name') &&
  activeEdit.includes('val retryingSkip=operation!=null && pendingDisposition==JournalSetDisposition.SKIPPED.name') &&
  activeEdit.includes('enabled=!busy && (retryingSkip || valid && (changed || operation!=null))') &&
  activeEdit.includes('Text(if(busy)"Saving…" else if(retryingSkip)"Retry skip correction"') &&
  activeEdit.includes('Mark this set as skipped?') && activity.includes('onCorrectSet=runtime::correctSet') &&
  host.includes('previous?.receipt?.fromRevision ?: progress.journal.revision,intent)') &&
  !activeEdit.includes('recordSet(') && !activeEdit.includes('commitAtomically'),
  'Active edits share controls, bind the prior receipt, preserve exact retry intent and wait for durable acknowledgement');
assert.ok(historyUi.includes('NativeSetValueFields(history.unit,load,reps,rir,!busy') &&
  historyUi.includes('val canEdit=!historyEditorBusy') &&
  historyUi.includes('val exact=command ?: NativeHistoryCodec.correctionJson(') &&
  historyUi.includes('System.currentTimeMillis(),listOf(NativeHistoryChange(') &&
  historyUi.includes('onCorrect(exact)') && activity.includes('onCorrectHistory=runtime::correctHistory') &&
  host.includes('owner.correctHistory(correctionJson)') && !historyUi.includes('commitAtomically'),
  'History edits share input controls, retry exact operations and go through the owned canonical transaction');
console.log('PASS native onboarding draft retention, canonical options, review/save separation, single-owner restore and logging gate, plus real-workout safe navigation, timer and confirmed history');
const observationEditor=fs.readFileSync(root+'NativeHistoryObservationEditor.kt','utf8');
assert.ok(historyUi.includes('NativeHistoryObservationEditor(history,observation,') &&
  historyUi.includes('Skipped and other recorded sets') && historyUi.includes('history.editRows.orEmpty()') &&
  observationEditor.includes('mutableStateOf(actual?.load ?: "")') &&
  observationEditor.includes('mutableStateOf(actual?.reps?.toString() ?: "")') &&
  observationEditor.includes('NativeSetValueFields(history.unit,load,reps,rir,!busy') &&
  observationEditor.includes('NativeHistoryObservationCodec.correctionJson(') &&
  observationEditor.includes('command?.let(onCorrect) ?: submit(') &&
  observationEditor.includes('Mark this saved set as skipped?') &&
  !observationEditor.includes('commitAtomically') && !observationEditor.includes('set.target'),
  'Saved skip/zero edits use explicit actuals, original slots, shared controls and exact atomic-owner retries');
assert.ok(train.includes('"Record failed attempt"') && train.includes('set.allowFailedAttempt') &&
  host.includes('NativeWorkoutCodec.acceptsValues(progress.plan,intent.slot,values)') &&
  activeEdit.includes('set.allowFailedAttempt') && observationEditor.includes('row.allowFailedAttempt') &&
  observationEditor.includes('0 reps records a failed attempt.'),
  'Zero-rep entry, active edits and saved edits must use issued eligibility and label the attempt explicitly');
const calendarUi=fs.readFileSync(root+'NativeHistoryCalendarPanel.kt','utf8');
assert.ok(activity.includes('onHistoryCalendar=runtime::loadHistoryCalendar') &&
  screens.includes('onFilterHistory,onHistoryCalendar)') &&
  shell.includes('onboarding?.historyCalendarQuery?.let') &&
  historyUi.includes('val browserState=rememberSaveableStateHolder()') &&
  historyUi.includes('browserState.SaveableStateProvider("calendar")') &&
  historyUi.includes('NativeHistorySummaryCard(summary,enabled,onOpen)') &&
  calendarUi.includes('DatePicker(state=picker,showModeToggle=false') &&
  calendarUi.includes('remember(calendar,enabled)') && calendarUi.includes('calendar?.workoutsOn(utcTimeMillis)') &&
  calendarUi.includes('workouts.forEach { NativeHistorySummaryCard(it,enabled,onOpen) }') &&
  host.includes('reader.calendar(query)') &&
  host.includes('loadHistoryWorkout(historyId,calendar.snapshotSha256)') &&
  host.includes('optionalHistoryCalendar(current.historyCalendarQuery,history.snapshotSha256)'),
  'Complete-month calendars must preserve day selection, share saved review cards, bind reads to the owned revision and refresh after corrections');
const backupUi=fs.readFileSync(root+'NativeBackupExportPanel.kt','utf8');
const backupExport=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeBackupExport.kt','utf8');
assert.ok(backupUi.includes('ActivityResultContracts.CreateDocument("application/json")') &&
  backupUi.includes('if(uri==null)pickerMessage="Backup canceled."') &&
  backupUi.includes('resolver.openOutputStream(uri,"wt")') && backupUi.includes('DocumentsContract.deleteDocument(resolver,uri)') &&
  !backupUi.includes('mutableStateOf<ByteArray') && !backupUi.includes('commitAtomically') &&
  screens.includes('NativeBackupExportPanel(runtimeState,onExportBackup,onResetBackupExport)') &&
  activity.includes('onExportBackup=runtime::exportBackup') &&
  host.includes('current.historyBusy || current.backupExportBusy') &&
  host.includes('NativeBackupExporter(owner,loaded.programEngine).export(document)') &&
  host.includes('it.copy(backupExportBusy=true,historyBusy=true') &&
  host.includes('current.historyBusy || current.workoutBusy || current.backupExportBusy || current.workout!=null') &&
  host.includes('current.workoutBusy || current.historyBusy || current.backupExportBusy || current.workoutBlocked') &&
  backupExport.includes('engine.validateInitialSnapshot(text)') && backupExport.includes('document.openInput().use') &&
  backupExport.includes('checkActive();checkCurrent(bytes);checkActive()') &&
  backupExport.includes('source?.fill(0)') && backupExport.includes('if(!saved)runCatching { document.discard() }') &&
  !backupExport.includes('commitAtomically') && !backupExport.includes('.initialize('),
  'Backup export uses a new user-selected destination, exact canonical-owned data, readback verification and cleanup without import or store writes');

const backupScreening=fs.readFileSync(root+'BackupCopyScreeningPanel.kt','utf8');
const mergeCodec=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeBackupMergePreview.kt','utf8');
const mergePolicy=fs.readFileSync('android-native/engine-bridge/backup-merge-preview.mjs','utf8');
const privateWire=fs.readFileSync('android-native/engine-bridge/workout-store-protocol.mjs','utf8');
assert.ok(activity.includes('onPreviewBackupMerge=runtime::previewBackupMerge') &&
  shell.includes('onPreviewBackupMerge=onPreviewBackupMerge') &&
  screens.includes('onPreviewBackupMerge = if (hasSavedProgram) onPreviewBackupMerge else null') &&
  backupScreening.includes('ActivityResultContracts.OpenDocument()') &&
  backupScreening.includes('Compare backup with saved data · preview only') &&
  backupScreening.includes('ReadOnlyBackupCandidateReader.screen(') &&
  backupScreening.includes('screening.bytes.fill(0)') &&
  backupScreening.includes('BackupScreeningStage.MergePreview') &&
  host.includes('suspend fun previewBackupMerge(selected: ByteArray)') &&
  host.includes('source.contentEquals(fresh)') &&
  host.includes('first?.fill(0);latest?.fill(0)') &&
  mergePolicy.includes("import { mergeStores, parseStoredData }") &&
  privateWire.includes("request.method==='previewBackupMerge'") &&
  mergeCodec.includes('result.get("approvedForImport") == false') &&
  !mergePolicy.includes('commitAtomically') && !mergePolicy.includes('publishSnapshot') &&
  !backupScreening.includes('commitAtomically'),
  'Backup comparison requires explicit selected source, bounded in-memory canonical merge, stale-owner refusal, redacted results, and no import authority');

const restoreOwner=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/CanonicalWorkoutStorePersistence.kt','utf8');
const restorePort=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeBackupMergePreparation.kt','utf8');
const restorePreparation=fs.readFileSync('android-native/engine-bridge/backup-merge-preparation.mjs','utf8');
assert.ok(restoreOwner.includes('private data class RestoreUndo(') &&
  restoreOwner.includes('val extended=state.undo!=null || state.undoAck!=null') &&
  restoreOwner.includes('if(extended)3 else 1') &&
  restoreOwner.includes('consumedRestoreIds') &&
  restoreOwner.includes('internal suspend fun installApprovedBackupMerge(') &&
  restoreOwner.includes('internal fun undoApprovedBackupMerge(') &&
  restoreOwner.includes('preview.flags.isNotEmpty()') &&
  restoreOwner.includes('current.snapshot!=initial.snapshot') &&
  restoreOwner.includes('undo=null,undoAck=null') &&
  restorePreparation.includes('approvedForImport:false') &&
  restorePreparation.includes('requiresAtomicUndo:true') &&
  restorePort.includes('class NativeBackupRestoreApproval internal constructor(') &&
  !activity.includes('installApprovedBackupMerge') &&
  !activity.includes('undoApprovedBackupMerge') &&
  !shell.includes('installApprovedBackupMerge') &&
  !screens.includes('installApprovedBackupMerge') &&
  !backupScreening.includes('installApprovedBackupMerge'),
  'Atomic undo transaction remains private and unconnected until real consent, provider/recovery and Android acceptance');

const restoreReviewUi=fs.readFileSync(root+'NativeBackupMergeReviewPanel.kt','utf8');
assert.ok(backupScreening.includes('NativeBackupMergeReviewPanel(current.preview)') &&
  restoreReviewUi.includes('BackupMergeCountRow("Workouts"') &&
  restoreReviewUi.includes('BackupMergeCountRow("Programs"') &&
  restoreReviewUi.includes('BackupMergeCountRow("Custom programs"') &&
  restoreReviewUi.includes('BackupMergeCountRow("Training cycles"') &&
  restoreReviewUi.includes('BackupMergeCountRow("Gyms"') &&
  restoreReviewUi.includes('preview.ownedRecordsRemoved') &&
  restoreReviewUi.includes('preview.ownedRecordsReplaced') &&
  restoreReviewUi.includes('preview.settingsChanged') &&
  restoreReviewUi.includes('Restore is not available yet') &&
  restoreReviewUi.includes('A future restore will require a new source check') &&
  !restoreReviewUi.includes('installApprovedBackupMerge') &&
  !restoreReviewUi.includes('commitAtomically') &&
  !restoreReviewUi.includes('CreateDocument('),
  'Native M3 backup differences must cover every collection and preserve explicit non-import status');

assert.ok(screens.includes('currentStoreChangeKey = runtimeState?.storeChangeKey ?: 0L') &&
  backupScreening.includes('remember(currentStoreChangeKey) { mutableStateOf<BackupScreeningStage>') &&
  backupScreening.includes('remember(currentStoreChangeKey) { mutableStateOf(false) }') &&
  host.includes('now.storeChangeKey==before.storeChangeKey') &&
  host.includes('source.contentEquals(fresh)'),
  'Backup review must be revision-bound in Compose and reject stale source during comparison');

const backupReviewGate=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeBackupReviewVerification.kt','utf8');
assert.ok(activity.includes('onVerifyBackupMerge=runtime::verifyBackupMergeSelection') &&
  shell.includes('onVerifyBackupMerge=onVerifyBackupMerge') &&
  screens.includes('onVerifyBackupMerge = if (hasSavedProgram) onVerifyBackupMerge else null') &&
  backupScreening.includes('Select same backup again · verify only') &&
  backupScreening.includes('ReselectionVerified') &&
  backupScreening.includes('ReselectionMismatch') &&
  backupScreening.includes('screening.bytes.fill(0)') &&
  host.includes('private val backupReviewGate=NativeBackupReviewGate()') &&
  host.includes('backupReviewGate.claimReselection()') &&
  host.includes('backupReviewGate.abandonReselection(claimed)') &&
  host.includes('backupReviewGate.completeReselection(claimed,fresh,second)') &&
  backupReviewGate.includes('freshPreview==preview') &&
  backupReviewGate.includes('freshSelection.sha256Hex==preview.incomingSha256') &&
  backupReviewGate.includes('A preview by itself cannot bind') &&
  !backupReviewGate.includes('commitAtomically') &&
  !host.includes('installApprovedBackupMerge') &&
  !backupScreening.includes('installApprovedBackupMerge'),
  'Second selection must fully recheck current owner and exact selected bytes without import authority');

const mergePreflightSource=fs.readFileSync('android-native/engine-bridge/backup-merge-preview.mjs','utf8');
const mergePreparationSource=fs.readFileSync('android-native/engine-bridge/backup-merge-preparation.mjs','utf8');
assert.ok(mergePreflightSource.includes('deletionClocksChanged') &&
  mergePreflightSource.includes('performanceChanged') &&
  mergePreflightSource.includes("'DELETION_CLOCKS_CHANGED'") &&
  mergePreflightSource.includes("'PERFORMANCE_CHANGED'") &&
  mergePreparationSource.includes('Deletion clocks would be changed') &&
  mergePreparationSource.includes('Existing performance mirror would change') &&
  mergeCodec.includes('"performanceChanged","deletionClocksChanged","flags"') &&
  restoreReviewUi.includes('preview.performanceChanged') &&
  restoreReviewUi.includes('preview.deletionClocksChanged') &&
  restoreReviewUi.includes('"PERFORMANCE_CHANGED"') &&
  restoreReviewUi.includes('"DELETION_CLOCKS_CHANGED"') &&
  !restoreReviewUi.includes('commitAtomically'),
  'Privileged merge may not hide performance cache changes or future deletion markers');

const reviewOwnerSource=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeBackupReviewGate.kt','utf8');
assert.ok(
  activity.includes('onDiscardBackupReview=runtime::discardBackupReview') &&
  shell.includes('onDiscardBackupReview=onDiscardBackupReview') &&
  screens.includes('onDiscardBackupReview = onDiscardBackupReview') &&
  backupScreening.includes('DisposableEffect(Unit)') &&
  backupScreening.includes('onDiscardBackupReview()') &&
  reviewOwnerSource.includes('fun claimReselection()') &&
  reviewOwnerSource.includes('fun abandonReselection(') &&
  reviewOwnerSource.includes('fun completeReselection(') &&
  reviewOwnerSource.includes('pending === claimed'),
  'Backup re-selection identity must be single-use and screen-scoped'
);

assert.ok(backupScreening.includes('if(inspectReselection) onDiscardBackupReview()') &&
  backupScreening.includes('is ReadOnlyDocumentScreening.FingerprintOnly -> {') &&
  backupScreening.includes('BackupScreeningStage.Rejected') &&
  backupScreening.includes('selectedUri == null'),
  'Failed or canceled second-source reads must revoke the old one-shot review');

const nativePriorityUi=fs.readFileSync(root+'NativeMusclePrioritySelector.kt','utf8');
const priorityContract=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeProgramSetup.kt','utf8');
const canonicalBuilder=fs.readFileSync('android-native/engine-bridge/native-onboarding.mjs','utf8');
assert.ok(
  ui.includes('var focus by rememberSaveable') &&
  ui.includes('var reduced by rememberSaveable') &&
  ui.includes('NativeMusclePrioritySelector(') &&
  ui.includes('focus.toMap(),reduced.toList()') &&
  ui.includes('proposal.profile.focus') &&
  ui.includes('proposal.profile.reduced') &&
  nativePriorityUi.includes('muscles: List<NativeProgramMuscle>') &&
  nativePriorityUi.includes('onChange: (String,Int)->Unit') &&
  nativePriorityUi.includes('focus.size<3') &&
  nativePriorityUi.includes('reduced.size<3') &&
  priorityContract.includes('val focus: Map<String,Int>') &&
  priorityContract.includes('val reduced: List<String>') &&
  priorityContract.includes('if(hasPriorities)wire.exact(') &&
  canonicalBuilder.includes('MUSCLE_IDS.has(part)') &&
  canonicalBuilder.includes('focus:{...(p.focus||{})}') &&
  canonicalBuilder.includes('reduce:[...(p.reduced||[])]') &&
  !ui.includes('generateNextProgramForShell(') &&
  !nativePriorityUi.includes('commitAtomically'),
  'Native authored priorities must be bounded and preserved by the canonical generator, not a UI-only rule'
);

const nativeDraftContract=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeProgramEditDraft.kt','utf8');
const nativeDraftUi=fs.readFileSync(root+'NativeProgramDraftEditor.kt','utf8');
assert.ok(
  screens.includes('NativeProgramDraftEditor(') &&
  screens.includes('rememberSaveable(currentPlan.program.reference)') &&
  screens.includes('Edit saved program') &&
  screens.includes('runtimeState?.workout==null') &&
  nativeDraftContract.includes('fun from(verified: ReadOnlyProgramPresentation.Structure)') &&
  nativeDraftContract.includes('fun matchesCurrent(') &&
  nativeDraftContract.includes('Collections.unmodifiableList(') &&
  nativeDraftContract.includes('fun moveExercise(') &&
  nativeDraftContract.includes('fun renameDay(') &&
  nativeDraftUi.includes('draft.matchesCurrent(verified)') &&
  nativeDraftUi.includes('Review program changes') &&
  nativeDraftUi.includes('confirmSave=true') &&
  !nativeDraftUi.includes('commitAtomically') &&
  !nativeDraftUi.includes('installApprovedBackupMerge') &&
  !nativeDraftUi.includes('generateNextProgramForShell'),
  'Current owned-program drafts must use confirmed revision-bound canonical saves'
);

const programDraftUi=fs.readFileSync(root+'NativeProgramDraftEditor.kt','utf8');
const programDraftDomain=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeProgramEditDraft.kt','utf8');
const packagedCatalog=fs.readFileSync(root+'PackagedReadOnlySchemaVerifier.kt','utf8');
assert.ok(
  screens.includes('NativeProgramDraftEditor(') &&
  screens.includes('rememberSaveable(currentPlan.program.reference)') &&
  screens.includes('Edit saved program') &&
  programDraftUi.includes('Review program changes') &&
  programDraftUi.includes('onSave(draft)') &&
  programDraftUi.includes('draft.matchesCurrent(verified)') &&
  programDraftUi.includes('LaunchedEffect(approved.reference)') &&
  programDraftUi.includes('lookupNames(approved.reference,ids)') &&
  packagedCatalog.includes('SandboxExerciseCatalog(worker)') &&
  host.includes('suspend fun lookupProgramExerciseNames(') &&
  host.includes('actual.sha256Hex==expected.snapshotSha256') &&
  host.includes('!sameSource()') &&
  activity.includes('onLookupProgramExercises=runtime::lookupProgramExerciseNames') &&
  programDraftDomain.includes('Collections.unmodifiableList(') &&
  !programDraftUi.includes('commitAtomically') &&
  !programDraftUi.includes('initialize('),
  'Native draft editing must preserve source identity and display packaged catalog names while routing confirmed saves through the runtime'
);

assert.ok(activity.includes('onSaveProgramEdits=runtime::saveProgramEdits') &&
  shell.includes('onSaveProgramEdits=onSaveProgramEdits') &&
  screens.includes('onSave=onSaveProgramEdits') &&
  host.includes('checkNotNull(owner.saveProgramEdits(request))') &&
  host.includes('current.saved.program.id!=draft.reference.programId') &&
  nativeDraftUi.includes('Save program changes?') &&
  nativeDraftUi.includes('Discard unsaved changes?') &&
  nativeDraftUi.includes('enabled=!saving') &&
  !nativeDraftUi.includes('Save program edits · not connected'),
  'Saved-program edits require an explicit review, current ownership, and guarded canonical save');

assert.ok(nativeDraftUi.includes('NativeProgramEditCodec.restoreWorkspace(verified,it)') &&
  nativeDraftUi.includes('rememberSaveable(approved.reference,stateSaver=draftSaver)') &&
  nativeDraftUi.includes('rememberSaveable(approved.reference,stateSaver=labelSaver)'),
  'Program organization and partially typed labels must survive Android SavedState restoration');

const sessionNoteSheet=fs.readFileSync(root+'NativeSessionNoteSheet.kt','utf8');
const sessionNoteJournal=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/WorkoutJournalPersistence.kt','utf8');
assert.ok(activity.includes('onSaveSessionNote=runtime::saveSessionNote') &&
  shell.includes('onReduceAffectedMuscle,onSaveSessionNote,onSaveWarmup,onSaveExerciseNote,onInspectEquipmentLoad,') &&
  fs.readFileSync(root+'NativeWorkoutScreen.kt','utf8').includes('NativeSessionNoteSheet(progress,state.workoutBusy,state.workoutError,onSaveSessionNote)') &&
  host.includes('recovery.recordNote(progress.plan.session,progress.journal.revision,note)') &&
  host.includes('restored?.journal?.note==note') &&
  sessionNoteSheet.includes('rememberSaveable(progress.plan.session.workoutId)') &&
  sessionNoteSheet.includes('Discard note changes?') &&
  sessionNoteSheet.includes('NativeEditSheet(busy,onDismiss={close()},canDismiss=') &&
  sessionNoteSheet.includes('note?.text==submitted') &&
  sessionNoteJournal.includes('state.note!=null -> 5') &&
  sessionNoteJournal.includes('if(!hasFinishCapacity(next))') &&
  !sessionNoteSheet.includes('commitAtomically'),
  'Session notes must use the durable journal, retain drafts, reserve Finish capacity and close only on an acknowledged save');

const warmupUi=fs.readFileSync(root+'NativeWarmupPanel.kt','utf8');
const warmupDomain=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeWarmups.kt','utf8');
assert.ok(activity.includes('onSaveWarmup=runtime::saveWarmup') &&
  shell.includes('onReduceAffectedMuscle,onSaveSessionNote,onSaveWarmup,onSaveExerciseNote,onInspectEquipmentLoad,') &&
  train.includes('if(showWarmups) NativeWarmupPanel(') &&
  warmupUi.includes('rememberSaveableStateHolder()') &&
  warmupUi.includes('Add warm-up') && warmupUi.includes('Retry warm-up save') &&
  warmupUi.includes('journal.warmupEvents.any {it.operationId==awaiting}') &&
  warmupUi.includes('NativeSetValueFields(unit,load,reps,rir,!busy') &&
  host.includes('recovery.recordWarmup(progress.plan,progress.journal.revision,progress.journal.warmupEvents.size,intent)') &&
  host.includes('restored?.journal?.warmupEvents?.any {it==intent}') &&
  sessionNoteJournal.includes('state.tracksWarmups -> 6') &&
  warmupDomain.includes('warmupIndex') &&
  !warmupUi.includes('commitAtomically') && !warmupUi.includes('prescribeSets('),
  'Warm-up facts must use their own durable positions, retained drafts, exact acknowledgements and shared controls');

const pinnedUi=fs.readFileSync(root+'NativeExerciseNoteSheet.kt','utf8');
const pinnedDomain=fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/NativeExerciseNotes.kt','utf8');
assert.ok(activity.includes('onSaveExerciseNote=runtime::saveExerciseNote') &&
  shell.includes('onReduceAffectedMuscle,onSaveSessionNote,onSaveWarmup,onSaveExerciseNote,onInspectEquipmentLoad,') &&
  train.includes('NativeExerciseNotePolicy.resolved(plan,journal.exerciseNotes,exercise.id)') &&
  train.includes('Icons.Outlined.PushPin') && train.includes('NativeExerciseNoteSheet(progress,exercise,') &&
  pinnedUi.includes('NativeEditSheet(busy,onDismiss={close()},canDismiss=') &&
  pinnedUi.includes('rememberSaveable(workoutId,exercise.id)') &&
  pinnedUi.includes('prior?.operationId==operationId') && pinnedUi.includes('Discard pinned changes?') &&
  host.includes('recovery.recordExerciseNote(progress.plan,progress.journal.revision,') &&
  host.includes('restored?.journal?.exerciseNotes?.any {it==edit}') &&
  sessionNoteJournal.includes('state.exerciseNotes.isNotEmpty() -> 7') &&
  pinnedDomain.includes('verifyOrigin(') && pinnedDomain.includes('verifyPublication(') &&
  !pinnedUi.includes('commitAtomically') && !pinnedDomain.includes('prescribeSets('),
  'Pinned exercise facts must use verified contexts, shared edit controls, durable independent edits and exact acknowledgements in both workout modes');

assert.ok(train.includes('var focusMode by rememberSaveable(plan.session.workoutId) { mutableStateOf(true) }'),
  'A real workout starts on the next actionable set while preserving the Show all mode');

const workoutLayout = fs.readFileSync(root+'NativeWorkoutScreen.kt','utf8');
const workingSetAt=workoutLayout.indexOf('visibleSets.forEach { set->');
const timerAt=workoutLayout.indexOf('NativeRestTimer(plan.session.workoutId,');
const discomfortAt=workoutLayout.indexOf('if(!showAffectedTools) {');
assert.ok(workingSetAt>0 && timerAt>workingSetAt &&
  discomfortAt>timerAt &&
  workoutLayout.includes('var showWarmups by rememberSaveable(plan.session.workoutId,exercise.index)') &&
  workoutLayout.includes('if(showWarmups) NativeWarmupPanel(') &&
  workoutLayout.includes('Text(if(showWarmups)"Hide warm-ups" else') &&
  workoutLayout.includes('journal.warmups.any { it.slot.exerciseIndex==exercise.index }') &&
  workoutLayout.includes('var focusMode by rememberSaveable(plan.session.workoutId) { mutableStateOf(true) }'),
  'The real workout must prioritize the current set, retain its rest timer, make warm-ups optional and keep safety adjustments reachable');

const todayStart=screens.indexOf('if(savedProgram!=null) {');
const nextWorkoutCta=screens.indexOf('Button(onClick={if(!activeWorkout)startWorkout();openTrain()}');
const programSecondary=screens.indexOf('OutlinedButton(onClick=openPlan,modifier=Modifier.fillMaxWidth())');
const shortenToggle=screens.indexOf('var showShorterSession by rememberSaveable(savedProgram.program.id)');
const shortenPanel=screens.indexOf('if(showShorterSession) {');
assert.ok(todayStart>=0 && nextWorkoutCta>todayStart &&
  programSecondary>nextWorkoutCta && shortenToggle>programSecondary &&
  shortenPanel>shortenToggle &&
  screens.includes('Text(if(showShorterSession)"Hide shorter session options" else "Need a shorter session?")') &&
  screens.includes('previewShorterSession(minutes)') &&
  screens.includes('startShortWorkout(preview.minutes)') &&
  screens.includes('if(!activeWorkout) {'),
  'Today must lead with real Start/Resume and keep canonical shorter-session options opt-in');

assert.ok(train.includes('onViewHistory: ()->Unit = {}') &&
  train.includes('if(state.completion!=null) {') &&
  train.includes('Button(onClick=onViewHistory,enabled=state.ready && !state.historyBusy &&') &&
  train.includes('Text("View saved workout")') &&
  train.indexOf('Button(onClick=onViewHistory,enabled=state.ready && !state.historyBusy &&') <
    train.indexOf('OutlinedButton(onClick=onStart,enabled=canStart,') &&
  shell.includes('onOpenLatestHistory()') &&
  shell.includes('onViewHistory={') &&
  activity.includes('onOpenLatestHistory=runtime::openLatestCompletedWorkout') &&
  host.includes('fun openLatestCompletedWorkout()') &&
  host.includes('history=history,historyPage=page,historyCalendar=calendar'),
  'A confirmed completion must show the saved workout in Insights before offering the next session, using the same canonical history');

const upNext=fs.readFileSync(root+'NativeUpNextPanel.kt','utf8');
assert.ok(train.includes('compact=focusMode)') &&
  upNext.includes('compact: Boolean = false') &&
  upNext.includes('if(compact) {') &&
  upNext.includes('next.set.load') &&
  upNext.includes('next.set.targetReps') &&
  upNext.includes('next.set.targetRir') &&
  upNext.includes('next.set.kind') &&
  upNext.includes('onOpenExercise(next.exercisePosition)') &&
  upNext.includes('contentDescription="Open ${next.exercise.name}"') &&
  !upNext.includes('prescribeSets(') &&
  !upNext.includes('store.') &&
  !upNext.includes('JournalSetIntent(') &&
  train.includes('val nextPreview=NativeNextSetPolicy.inspect(progress,exerciseIndex)'),
  'Focus Up Next must be concise, accessible and sourced only from the issued set, while full mode remains available');

const setContext=fs.readFileSync(root+'NativeWorkoutScreen.kt','utf8');
assert.ok(setContext.includes('Text("Exercise ${exerciseIndex+1} of ${plan.exercises.size}"') &&
  setContext.includes('exercise.sets.size)') &&
  setContext.includes('exerciseSetCount:Int)') &&
  setContext.includes('else "Set ${set.slot.setIndex+1} of $exerciseSetCount"') &&
  setContext.includes('set.isExtension') &&
  setContext.includes('set.extensionRestSeconds?.let'),
  'The live set header must identify current exercise and correct issued set count, including independent myo/drop extension labels');

assert.ok(train.includes('if(showLast)Text(set.last?.let') &&
  train.includes('Target: ${set.load} ${session.unit} × ${set.targetReps} reps') &&
  train.includes('set.targetRir?.let { " · $it RIR" }') &&
  train.includes('NativeSetValueFields(session.unit,load,reps,rir,!busy'),
  'The Target/Last control must show canonical issued weight, reps and RIR in that order without replacing editable actual load');

const toolbarStart=train.indexOf('    // Session note, pinned setup and Focus stay visible');
const toolbarEnd=train.indexOf('    if(pinnedNote!=null && (pinnedNote.text',toolbarStart);
assert.ok(toolbarStart>=0 && toolbarEnd>toolbarStart &&
  !train.slice(toolbarStart,toolbarEnd).includes('horizontalScroll(') &&
  train.slice(toolbarStart,toolbarEnd).includes('Spacer(Modifier.weight(1f))') &&
  train.slice(toolbarStart,toolbarEnd).includes('Icon(Icons.Outlined.Edit') &&
  train.slice(toolbarStart,toolbarEnd).includes('Icon(Icons.Outlined.PushPin') &&
  train.slice(toolbarStart,toolbarEnd).includes('FilterChip(selected=focusMode,onClick={focusMode=!focusMode}') &&
  train.slice(toolbarStart,toolbarEnd).includes('FilterChip(selected=!showLast,onClick={showLast=false}') &&
  train.slice(toolbarStart,toolbarEnd).includes('FilterChip(selected=showLast,onClick={showLast=true}'),
  'The live note, setup, Focus and Last/Target controls must be visible without horizontal scroll on phones');

assert.ok(controls.includes('val repsControl: @Composable () -> Unit') &&
  controls.includes('val rirControl: @Composable () -> Unit') &&
  controls.includes('BoxWithConstraints(Modifier.fillMaxWidth())') &&
  controls.includes('if(maxWidth>=560.dp) {') &&
  controls.includes('Box(Modifier.weight(1f)) { repsControl() }') &&
  controls.includes('Box(Modifier.weight(1f)) { rirControl() }') &&
  controls.includes('Column(verticalArrangement=Arrangement.spacedBy(8.dp))') &&
  controls.includes('NativeSetCounterField("RIR",rir,enabled') &&
  controls.includes('minWidth=48.dp,minHeight=48.dp') &&
  controls.includes('if(load=="0")"0 load = bodyweight') &&
  controls.includes('RIR is optional'),
  'The one shared set-entry control must be compact on wide phones and remain unclipped, accessible and optional on narrow phones');

const finishReadyAt=train.indexOf('    if(journal.unresolvedCount==0) {\n        PursuitCard(highlighted=true)');
const progressAt=train.indexOf('LinearProgressIndicator(progress={journal.completed.size.toFloat()/plan.slots.size}');
const movementAt=train.indexOf('Text(exercise.name,style=MaterialTheme.typography.headlineSmall)');
const loggerAt=train.indexOf('visibleSets.forEach { set->');
const injuryReviewAt=train.indexOf('if(!showAffectedTools) {');
assert.ok(progressAt>0 && finishReadyAt>progressAt &&
  movementAt>finishReadyAt && loggerAt>movementAt &&
  injuryReviewAt>loggerAt &&
  train.includes('if(focusMode && (journal.unresolvedCount==0 || exercisePending==0))') &&
  train.includes('emptyList<NativeWorkoutSet>()') &&
  train.includes('if(journal.unresolvedCount>0) {\n        NativeRestTimer(') &&
  train.includes('if(showFinishReview && !journal.isFinished && journal.unresolvedCount==0)') &&
  train.includes('confirmButton={Button(onClick={showFinishReview=false;onFinish()}') &&
  train.includes('if(journal.isFinished)"Retry finishing workout"') &&
  !train.includes('PursuitCard(highlighted=journal.unresolvedCount==0)'),
  'Once every issued set is durably resolved, Focus must prioritize confirmed finish, hide stale set/rest controls, and retain exact retry and review gates');

assert.ok(train.includes('if(!journal.isFinished && focusMode) {') &&
  train.includes('TextButton(onClick={focusMode=false},') &&
  train.includes('Text("Review saved sets")') &&
  train.indexOf('Text("Review saved sets")') <
    train.indexOf('Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())') &&
  train.includes('if(showFinishReview && !journal.isFinished && journal.unresolvedCount==0)'),
  'The ready-to-finish action must allow immediate review of saved sets without submitting a finish or losing the confirmation gate');

assert.ok(shell.includes('val readyToFinishWorkoutId=onboarding?.workout?.takeIf {') &&
  shell.includes('it.journal.unresolvedCount==0') &&
  shell.includes('LaunchedEffect(current,readyToFinishWorkoutId)') &&
  shell.includes('current==NativeDestination.TRAIN.ordinal &&') &&
  shell.includes('readyToFinishWorkoutId!=null) pageScroll.animateScrollTo(0)') &&
  !shell.includes('LaunchedEffect(pageScroll.value'),
  'A durably resolved workout must reveal Finish once without repeatedly stealing manual review scroll');

assert.ok(train.includes('Button(onClick=onViewHistory,enabled=state.ready && !state.historyBusy &&') &&
  shell.includes('onOpenLatestHistory()') &&
  shell.includes('section=NativeDestination.INSIGHTS.ordinal') &&
  host.includes('val receipt=current.completion ?: return') &&
  host.includes('val id=receipt.session.workoutId') &&
  host.includes('val history=checkNotNull(reader.workout(id))') &&
  host.includes('check(history.historyId==id)') &&
  host.includes('val page=optionalHistoryPage(0,history.snapshotSha256,filter)') &&
  host.includes('latest.storeChangeKey==current.storeChangeKey') &&
  host.includes('latest.completion?.session==receipt.session') &&
  host.includes('latest.workout==null') &&
  host.includes('history=null,historyPage=null,') &&
  host.includes('historyCalendarQuery=null,historyCalendar=null,historyFilter=filter,') &&
  host.includes('historyBusy=true,historyError=null') &&
  host.includes('historyError="The latest saved workout could not be opened.') &&
  !host.includes('onOpenHistory(current.history?.historyId'),
  'The latest completion CTA must fetch the exact durable receipt, never show a stale previously browsed workout, and guard asynchronous history updates');

assert.ok(screens.includes('viewLatestHistory: () -> Unit = {}') &&
  screens.includes('if(workoutState?.completion!=null && !activeWorkout) {') &&
  screens.includes('TextButton(onClick=viewLatestHistory,') &&
  screens.includes('Text("Review last workout")') &&
  screens.includes('!workoutState.historyBusy && !workoutState.busy') &&
  shell.includes('viewLatestHistory={') &&
  shell.includes('onOpenLatestHistory()') &&
  shell.includes('section=NativeDestination.INSIGHTS.ordinal') &&
  screens.indexOf('Button(onClick={if(!activeWorkout)startWorkout();openTrain()}') <
    screens.indexOf('TextButton(onClick=viewLatestHistory,'),
  'Today must keep Start/Resume first and route review of the last saved workout through the same guarded canonical receipt read as Train');

const historyRow=fs.readFileSync(root+'NativeHistoryPanel.kt','utf8');
assert.ok(historyRow.includes('private fun NativeHistorySetRow(') &&
  historyRow.split('NativeHistorySetRow(').length-1===4 &&
  historyRow.includes('ListItem(') &&
  historyRow.includes('headlineContent={Text(label') &&
  historyRow.includes('supportingContent={Text(detail') &&
  historyRow.includes('trailingContent={') &&
  historyRow.includes('contentDescription="$action $label"') &&
  historyRow.includes('if(observation!=null)') &&
  historyRow.includes('selectedObservation=observation.slot.exerciseIndex*20+observation.slot.setIndex') &&
  historyRow.includes('selectedExercise=exercise.id') &&
  historyRow.includes('selectedSet=set.setIndex') &&
  historyRow.includes('selectedWarmup="${row.slot.exerciseIndex}:${row.slot.warmupIndex}"') &&
  historyRow.includes('selectedObservation=exerciseIndex*20+row.slot.setIndex') &&
  historyRow.includes('if(value==null)"Skipped · no recorded reps"') &&
  historyRow.includes('canEdit,if(value==null)"Record" else "Edit"'),
  'Real history must show structured legible set values with accessible Edit/Record actions and preserve each exact correction identity');

assert.ok(historyUi.includes('val historyEditorBusy=state.historyBusy || state.workoutBusy ||') &&
  historyUi.includes('state.backupExportBusy || state.busy || state.workout!=null') &&
  historyUi.includes('val canEdit=!historyEditorBusy') &&
  historyUi.includes('LaunchedEffect(state.workout?.plan?.session?.workoutId)') &&
  historyUi.includes('if(state.workout!=null) {') &&
  historyUi.includes('selectedExercise=null') &&
  historyUi.includes('selectedObservation=-1') &&
  historyUi.includes('selectedWarmup=null') &&
  historyUi.includes('NativeHistoryObservationEditor(history,observation,historyEditorBusy') &&
  historyUi.includes('NativeHistorySetEditor(history,exercise,set,historyEditorBusy') &&
  historyUi.includes('historyEditorBusy,state.historyError,canSkip=true') &&
  historyUi.split('NativeSetEditSheet(historyEditorBusy,').length-1===3 &&
  host.includes('current.workout!=null') &&
  host.includes('current.backupExportBusy'),
  'History corrections must be disabled during backup or active workout and close obsolete edit sheets when a workout starts');

assert.ok(train.includes('if(focusMode && exercisePending==0 && journal.unresolvedCount>0)') &&
  train.includes('Use Continue below to open the next pending exercise') &&
  train.includes('else if(focusMode)listOfNotNull(') &&
  !train.includes('} ?: exercise.sets.last())') &&
  train.includes('if(journal.unresolvedCount>0) {') &&
  train.includes('NativeUpNextPanel(preview,exerciseIndex,'),
  'A completed manually selected exercise must not masquerade as an active working set; its session-scoped rest timer and receipt-derived resume remain mounted');

assert.ok(train.includes('val canStart=state.ready && !state.busy && !state.blocked &&') &&
  train.includes('!state.backupExportBusy && !state.workoutBlocked') &&
  train.includes('Button(onClick=onViewHistory,enabled=state.ready && !state.historyBusy &&') &&
  train.includes('!state.workoutBusy && !state.backupExportBusy && !state.busy &&') &&
  train.includes('!state.blocked && !state.workoutBlocked,') &&
  host.includes('if(!current.ready || current.busy || current.workoutBusy || current.historyBusy || current.backupExportBusy || current.workoutBlocked') &&
  host.includes('if(!current.ready || current.saved==null || current.busy ||'),
  'Train Start and latest-saved-history actions must not offer taps during runtime-blocked store operations');

// UI callbacks must never bypass a single host-level write gate during backup,
// history I/O, blocked recovery or other canonical store transactions.
assert.ok(host.includes('private fun canMutateWorkout(current: NativeRuntimeState,allowFinished: Boolean=false): Boolean') &&
  host.includes('current.ready && !current.blocked && !current.busy &&') &&
  host.includes('!current.workoutBusy && !current.historyBusy &&') &&
  host.includes('!current.backupExportBusy && !current.workoutBlocked &&') &&
  host.includes('allowFinished || !it.isFinished') &&
  host.includes('if(!canMutateWorkout(current) || progress.plan.slots!=progress.journal.slots)return') &&
  host.includes('if(!canMutateWorkout(current))return') &&
  host.includes('if(!canMutateWorkout(current) ||\n            !NativeWarmupPolicy.valid') &&
  host.includes('if(!canMutateWorkout(current) ||\n            !NativeExerciseNotePolicy.valid') &&
  host.includes('if(!canMutateWorkout(current) ||\n            !JournalSessionNoteCodec.validText') &&
  host.includes('if(!canMutateWorkout(current,allowFinished=true) || progress.journal.unresolvedCount!=0)return') &&
  host.includes('current.historyBusy || current.workoutBusy || current.backupExportBusy || current.workout!=null') &&
  host.includes('current.backupExportBusy || current.workoutBlocked)return'),
  'All active workout mutations must fail closed across backup, history and blocked states while preserving finished-journal publication retries');

assert.ok(train.includes('val operationBlocked=!state.ready || state.blocked || state.busy ||') &&
  train.includes('state.workoutBusy || state.historyBusy || state.backupExportBusy ||') &&
  train.includes('state.workoutBlocked') &&
  train.includes('val writeBlocked=operationBlocked || journal.isFinished') &&
  train.includes('NativeWarmupPanel(\n        progress,exercise,writeBlocked,') &&
  train.includes('NativeExerciseNoteSheet(progress,exercise,writeBlocked,') &&
  train.includes('NativeSessionNoteSheet(progress,writeBlocked,') &&
  train.includes('NativeWorkingSet(plan.session,set,commit,writeBlocked,') &&
  train.includes('enabled=removed.isNotEmpty() && !writeBlocked') &&
  train.includes('enabled=pendingCount>0 && !writeBlocked') &&
  train.includes('enabled=!operationBlocked) { Text("Finish workout")'),
  'Native Train controls and Finish must reflect the same busy/blocked predicate as the canonical journal writer');
