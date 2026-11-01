import assert from 'node:assert/strict';
import fs from 'node:fs';
const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const ui=fs.readFileSync(root+'NativeOnboardingScreen.kt','utf8');
const screens=fs.readFileSync(root+'NativeScreens.kt','utf8');
const host=fs.readFileSync(root+'NativeRuntimeHost.kt','utf8');
const activity=fs.readFileSync(root+'MainActivity.kt','utf8');
const shell=fs.readFileSync(root+'NativePreviewApp.kt','utf8');
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
// Real Train controls are journal-driven; UI must never manufacture a persisted
// set or publish a workout directly from an unconfirmed tap.
const train=fs.readFileSync(root+'NativeWorkoutScreen.kt','utf8');
const controls=fs.readFileSync(root+'NativeComponents.kt','utf8');
assert.ok(train.includes('val committedSlots=journal.completed.map { it.intent.slot }.toSet()') &&
  train.includes('plan.exercises[index].sets.any { it.slot !in committedSlots }') &&
  train.includes('Go to next unfinished exercise'),
  'Train navigation follows committed journal slots, not optimistic taps');
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
  controls.includes('input.length<=10 && input.all { it.isDigit() || it==\'.\' }') &&
  controls.includes('input.length<=3 && input.all(Char::isDigit)') &&
  controls.includes('input.length<=2 && input.all(Char::isDigit)') &&
  train.includes('validLoad && repCount!=null'),
  'Native set edits remain bounded and cannot be committed with invalid load or reps');
assert.ok(train.includes('var focusMode by rememberSaveable(plan.session.workoutId)') &&
  train.includes('val visibleSets=if(focusMode)listOf(exercise.sets.firstOrNull') &&
  train.includes('it.slot !in committedSlots') &&
  train.includes('visibleSets.forEach { set->') &&
  train.includes('NativeWorkingSet(plan.session,set,commit,state.workoutBusy || journal.isFinished,showLast,onRecord,') &&
  train.includes('onCorrect,state.workoutError)') &&
  train.includes('FilterChip(selected=focusMode,onClick={focusMode=!focusMode}') &&
  train.includes('val setDraftStates=rememberSaveableStateHolder()'),
  'Focus mode must project the identical receipt-based set logger and preserve unsent drafts');
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
  rest.includes('Current rest: $activeSeconds s') &&
  rest.includes('activeSeconds = prescribedSeconds') &&
  rest.includes('activeSeconds = 0'),
  'Recovered rest deadlines must reject reboot/time drift and preserve the active interval when selecting another exercise');

assert.ok(train.includes('set.isExtension') &&
  train.includes('set.extensionRestSeconds?.let') &&
  train.includes('if(set.isExtension)"Complete extension" else "Complete set"') &&
  train.includes('if(focusMode)exercise.sets.firstOrNull { it.slot !in committedSlots }') &&
  train.includes('activeExtension ?: exercise.restSeconds'),
  'Myo/drop extensions must remain independently visible, loggable and rest-aware');
assert.ok(train.includes('JournalSetValues(load,session.unit,checkNotNull(repCount),effort)') &&
  train.includes('var rir by rememberSaveable { mutableStateOf("") }') &&
  train.includes('if(journal.isFinished)onFinish() else showFinishReview=true'),
  'The review must not synthesize actual RIR or block an exact retry of a durably finished session');
// Insights may only display a previously committed canonical receipt, not
// estimate achievements from demo controls or from an unfinished workout.
assert.ok(shell.includes('NativeDestination.INSIGHTS -> InsightsScreen(onboarding,onCorrectHistory,onReloadHistory)') &&
  screens.includes('internal fun InsightsScreen(state: NativeRuntimeState?,') &&
  screens.includes('val receipt=state?.completion') &&
  screens.includes('saved?.program?.days?.firstOrNull { it.id==id }?.label') &&
  screens.includes('if(receipt!=null)') &&
  screens.includes('NO VERIFIED NATIVE COMPLETION') &&
  !screens.includes('YOUR RECORD", "Progress with context.",\n        "No fabricated personal bests or charts. Your history will appear'),
  'Native Insights must distinguish a confirmed completion from missing/imported history');
assert.ok(host.includes('val completion=owner.lastCompletionReceipt()') &&
  host.includes('Starting another workout cannot erase the last durable completion receipt.') &&
  !host.includes('workoutError=null,completion=null'),
  'Starting the next workout must not hide the previous confirmed history receipt');
const historyUi=fs.readFileSync(root+'NativeHistoryPanel.kt','utf8');
const activeEdit=fs.readFileSync(root+'NativeActiveSetCorrection.kt','utf8');
assert.ok(controls.includes('internal fun NativeSetEditSheet(') &&
  controls.includes('confirmValueChange={it!=SheetValue.Hidden || !currentBusy}') &&
  controls.includes('imePadding().verticalScroll(rememberScrollState())') &&
  historyUi.includes('NativeSetEditSheet(state.historyBusy,') && activeEdit.includes('NativeSetEditSheet(busy,'),
  'Both correction sheets share keyboard layout and prevent drag-dismissal during an unacknowledged save');
assert.ok(train.includes('NativeActiveSetCorrection(session,set,commit,busy,error,onCorrect)') &&
  activeEdit.includes('rememberSaveable(commit.intent.operationId)') &&
  activeEdit.includes('NativeSetValueFields(session.unit,load,reps,rir,!busy') &&
  activeEdit.includes('onCorrect(JournalSetIntent(id,set.slot,disposition,values,commit.intent.operationId))') &&
  activeEdit.includes('pendingDisposition==JournalSetDisposition.SKIPPED.name') &&
  activeEdit.includes('Mark this set as skipped?') && activity.includes('onCorrectSet=runtime::correctSet') &&
  host.includes('previous?.receipt?.fromRevision ?: progress.journal.revision,intent)') &&
  !activeEdit.includes('recordSet(') && !activeEdit.includes('commitAtomically'),
  'Active edits share controls, bind the prior receipt, preserve exact retry intent and wait for durable acknowledgement');
assert.ok(historyUi.includes('NativeSetValueFields(history.unit,load,reps,rir,!busy') &&
  historyUi.includes('val canEdit=state.workout==null && !state.workoutBusy && !state.historyBusy') &&
  historyUi.includes('val exact=command ?: NativeHistoryCodec.correctionJson(') &&
  historyUi.includes('System.currentTimeMillis(),listOf(NativeHistoryChange(') &&
  historyUi.includes('onCorrect(exact)') && activity.includes('onCorrectHistory=runtime::correctHistory') &&
  host.includes('owner.correctHistory(correctionJson)') && !historyUi.includes('commitAtomically'),
  'History edits share input controls, retry exact operations and go through the owned canonical transaction');
console.log('PASS native onboarding draft retention, canonical options, review/save separation, single-owner restore and logging gate, plus real-workout safe navigation, timer and confirmed history');
