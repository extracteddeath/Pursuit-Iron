import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const controller=readFileSync(root+'NativeKotlinSessionController.kt','utf8');
const workout=readFileSync(root+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const journey=readFileSync(root+'NativeKotlinCandidateJourney.kt','utf8');
const history=readFileSync(root+'NativeKotlinCandidateHistoryScreen.kt','utf8');
const required=(source,parts,reason)=>{
  for(const part of parts)assert.ok(source.includes(part),reason+': '+part);
};

// This is a static wiring regression gate, not a simulated on-device
// transaction or proof of parity. Durable end-to-end tests remain mandatory.
required(controller,[
  'host.recoverWorkout(workoutId)',
  'private fun rejectInvalidSelection(workoutId:String):Boolean',
  'if(rejectInvalidSelection(workoutId))return',
  'unavailableReason=NativeJournalFileRefusal.INVALID_ARGUMENT',
  'current.snapshot.targets.none{it.setKey==setKey}',
  'host.issueWorkout(workoutId)',
  'host.recordWorkoutOperation(workoutId,operation)',
  'expectedJournalRevision=journal.revision',
  'correctsEventId=existing?.operation?.eventId',
  'journal.snapshot.targets.none{it.setKey==setKey}',
  'if(prior.busy || journal.finishedByEventId!=null ||',
  'if(prior.busy || !journal.canProposeFinish)return',
  'journal.finishedByEventId!=null',
  'if(!journal.canProposeFinish)return',
  'KotlinJournalOperation.Finish(',
  'recoverOnReject=true',
  'if(closed.get() || pending.get())return',
  'latestCoachRequest.incrementAndGet()',
  'private val generation=AtomicLong(0)',
  'private val closed=AtomicBoolean(false)',
  'if(!pending.compareAndSet(false,true)) {',
  'if(!closed.compareAndSet(false,true))return',
  'val requestGeneration=generation.incrementAndGet()',
  'if(closed.get() || generation.get()!=requestGeneration)return@launch',
  'generation.incrementAndGet()',
  'if(!closed.get() &&'
], 'Native controller must retain durable revision-checked lifecycle guards');
assert.match(controller,/is NativeJournalFileResult.Durable -> NativeKotlinSessionState\([\s\S]*?journal=result.journal/);
assert.match(controller,/if\(result is NativeJournalFileResult.Rejected && recoverOnReject\)[\s\S]*?host.recoverWorkout\(workoutId\)/);
assert.doesNotMatch(controller,/WebView|evaluateJavascript|engineBridge/);
required(controller,[
  'hideUnverified:Boolean=false',
  'NativeKotlinSessionState(workoutId=workoutId,busy=true)',
  'run(workoutId,{host.recoverWorkout(workoutId)},hideUnverified=true)',
  '},hideUnverified=true)'
], 'Cold recovery and issuance must not expose unverified previous-session receipts');

// A second Open/Issue while a prior workout is in-flight must revoke old
// receipts, invalidate the previous callback and require an explicit retry.
const pendingSelection=controller.slice(
  controller.indexOf('if(!pending.compareAndSet(false,true)) {'),
  controller.indexOf('val requestGeneration=generation.incrementAndGet()')
);
required(pendingSelection,[
  'if(hideUnverified) {',
  'generation.incrementAndGet()',
  'latestCoachRequest.incrementAndGet()',
  'NativeKotlinSessionState(',
  'workoutId=workoutId',
  'NativeJournalFileRefusal.STORAGE_UNAVAILABLE'
], 'Concurrent navigation must fail closed rather than expose the old workout');

// Invalid navigation must revoke stale receipts even while disk I/O is in flight.
// A late callback must not re-enable the previously selected workout.
const invalidSelection=controller.slice(
  controller.indexOf('private fun rejectInvalidSelection('),
  controller.indexOf('/** Reading never issues a new workout. */')
);
required(invalidSelection,[
  'if(closed.get())return true',
  'generation.incrementAndGet()',
  'latestCoachRequest.incrementAndGet()',
  'mutable.value=NativeKotlinSessionState(',
  'NativeJournalFileRefusal.INVALID_ARGUMENT'
], 'Invalid workout selection must invalidate pending callbacks and old actionable state');
assert.ok(!invalidSelection.includes('pending.get()'),
  'An in-flight storage operation must not preserve the previous actionable workout');

// A failed write/read cannot leave an actionable stale presentation in memory.
// Cancellation is not a storage failure and must propagate to the coroutine.
const exceptionHandler=controller.slice(
  controller.indexOf('} catch(cancelled:CancellationException) {',
    controller.indexOf('private fun run(')),
  controller.indexOf('} finally {',controller.indexOf('private fun run('))
);
required(exceptionHandler,[
  'throw cancelled',
  'latestCoachRequest.incrementAndGet()',
  'mutable.value=NativeKotlinSessionState(',
  'workoutId=workoutId',
  'NativeJournalFileRefusal.STORAGE_UNAVAILABLE'
], 'Unverified storage exception must fail closed and invalidate live coaching');
assert.ok(!exceptionHandler.includes('mutable.value=mutable.value.copy('),
  'Do not retain actionable journal and presentation after an unknown disk failure');

required(workout,[
  'LaunchedEffect(workoutId) { controller.open(workoutId) }',
  'controller.record(set.target.setKey,outcome,',
  'controller.finish()',
  'showFinishConfirm=false',
  'presentation?.canFinish==true',
  'host.reviewVerifiedKotlinHistory()',
  'NativeKotlinFinishedComposerHandoff.inspect(stored,history)',
  'onViewHistory()',
  'controller.undoExerciseSwap(swapId)',
  'rememberSaveableStateHolder()',
  'if(state.busy)',
  'Verifying the saved workout and athlete ownership.'
], 'Workout UI must retain durable recording, finish confirmation, verified history and undo');
required(journey,[
  'currentUnfinishedGeneratedWorkoutId(host)',
  'host.currentlyApprovedKotlinWorkoutId()',
  'host.recoverWorkout(id)',
  'opened.journal.finishedByEventId==null',
  'PursuitPrimaryAction("Resume workout"',
  'selectedId=awaitingWorkoutId',
  'controller.clear()'
], 'Cold start and minimized resume must revalidate approved unfinished journal');
required(history,['host.reviewVerifiedKotlinHistory()'],
  'History must read owner-verified native facts');

console.log('PASS native beta lifecycle wiring: approved issuance, durable set edits, conflict recovery, minimize/resume, confirmed finish, history and swap undo');
