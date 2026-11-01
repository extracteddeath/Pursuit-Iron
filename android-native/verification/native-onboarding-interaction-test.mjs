import assert from 'node:assert/strict';
import fs from 'node:fs';
const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const ui=fs.readFileSync(root+'NativeOnboardingScreen.kt','utf8');
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
assert.ok(train.includes('input.length<=10 && input.all { ch->ch.isDigit() || ch==\'.\' }') &&
  train.includes('input.length<=3 && input.all(Char::isDigit)') &&
  train.includes('input.length<=2 && input.all(Char::isDigit)') &&
  train.includes('validLoad && repCount!=null'),
  'Native set edits remain bounded and cannot be committed with invalid load or reps');
const rest=fs.readFileSync(root+'NativeRestTimer.kt','utf8');
assert.ok(train.includes('NativeRestTimer(plan.session.workoutId, exercise.restSeconds,') &&
  rest.includes('rememberSaveable(workoutId)') &&
  rest.includes('SystemClock.elapsedRealtime()') &&
  rest.includes('LaunchedEffect(deadline)') &&
  rest.includes('delay(250L)') &&
  rest.includes('onLongClickLabel = "Reset rest timer"') &&
  rest.includes('onLongClick = { deadline = 0L }') &&
  rest.includes('enabled = enabled') &&
  !rest.includes('onRecord(') && !rest.includes('store.') && !rest.includes('journal.'),
  'The rest countdown must be a session-scoped manual M3 control, not an unacknowledged set write or engine re-prescription');
assert.ok(train.includes('JournalSetValues(load,session.unit,checkNotNull(repCount),effort)') &&
  train.includes('var rir by rememberSaveable { mutableStateOf("") }') &&
  train.includes('if(journal.isFinished)onFinish() else showFinishReview=true'),
  'The review must not synthesize actual RIR or block an exact retry of a durably finished session');
console.log('PASS native onboarding draft retention, canonical options, review/save separation, single-owner restore and logging gate, plus real-workout safe navigation and confirmation');
