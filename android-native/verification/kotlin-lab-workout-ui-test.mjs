import assert from 'node:assert/strict';
import fs from 'node:fs';

// Narrow UI source contract; actual on-device interaction remains a separate
// acceptance milestone, not a claim made by this static test.
const read=p=>fs.readFileSync(p,'utf8');
const journal=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt');
const journey=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt');

for(const phrase of [
  'CustomWorkoutJournalDomain.nextUnresolvedVisit(it)',
  'initiallyExpanded=nextVisit==mainKey',
  'initiallyExpanded=nextVisit==CustomWorkoutVisitKey(',
  'mutableStateOf(saved==null && initiallyExpanded)',
  'verified?.value?.toPlainString().orEmpty()',
  'prescription.reps.minimum',
  'follower?.followUpReps',
  'Blank RIR stays unreported.',
  'progress={if(mainTotal==0)0f',
  'Text("Log this set")',
  'Text("Minimize")',
  'BringIntoViewRequester()',
  'reveal.bringIntoView()',
  'bringIntoViewRequester(reveal)',
  'CustomWorkoutJournalOperation.Finish('
])assert.ok(journal.includes(phrase),'Missing native set-entry behavior: '+phrase);

// A focus filter may unmount all other set editors. Their unsubmitted
// values must survive a Focus/All switch and configuration restoration.
for(const phrase of [
  'var focusOnNext by rememberSaveable(workoutId)',
  'val editorStates=rememberSaveableStateHolder()',
  'activeFocus=nextVisit.takeIf',
  'Text("UP NEXT"',
  'Text("Focus on next")',
  'Text("All sets")',
  'slot.sets.filter{activeFocus==null ||',
  'editorStates.SaveableStateProvider("main:${set.setKey}")',
  'editorStates.SaveableStateProvider("follow:${set.setKey}")',
  'activeFocus?.part!=CustomWorkoutVisitPart.MAIN',
  'activeFocus?.part!=CustomWorkoutVisitPart.FOLLOW_UP'
])assert.ok(journal.includes(phrase),'Missing safe native focus behavior: '+phrase);
assert.match(journal,/if\(!verifiedFinishedInHistory \|\| showFinishedSets\)/,
  'Finished sessions must still expose recorded sets for review');

assert.match(journal,/var actualRir by rememberSaveable\(set.setKey,part.name,existing\?\.appliedRevision\) \{\s*mutableStateOf\(saved\?\.performed\?\.actualRir\?\.toString\(\)\.orEmpty\(\)\)/,
  'Unknown actual RIR must remain unreported, never copied from the target');
assert.ok(journal.includes('var actualLoad by rememberSaveable(set.setKey,part.name,existing?.appliedRevision)') &&
  journal.includes('var actualReps by rememberSaveable(set.setKey,part.name,existing?.appliedRevision)'),
  'Uncommitted edit fields must survive an Android configuration change');
assert.match(journal,/onRecord\(KotlinJournalSetOutcome.COMPLETED,\s*KotlinPerformedSet\(weight,verified\?\.unit/,
  'Actual set writing must require an explicit Complete action');
assert.ok(journal.includes('pendingExceptionalOutcome=KotlinJournalSetOutcome.SKIPPED') &&
  journal.includes('pendingExceptionalOutcome=KotlinJournalSetOutcome.FAILED_ATTEMPT') &&
  journal.includes('if(pending!=null && !locked) AlertDialog(') &&
  journal.includes('KotlinPerformedSet(weight,verified?.unit,0,null)') &&
  journal.includes('"Confirm skip" else "Log failed attempt"'),
  'Custom skipped/failed visits must require confirmation before a journal write');
assert.ok(journey.includes('if(!wide && !activeWorkout)NavigationBar {') &&
  journey.includes('if(wide && !activeWorkout)NavigationRail {') &&
  journey.includes('onActiveWorkoutChanged={trainScreenExpanded=it}'),
  'Active Kotlin workouts must hide global navigation, with adaptive bottom bar/rail otherwise');
const exerciseHistory=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseHistorySheet.kt');
assert.ok(journal.includes('NativeKotlinExerciseHistorySheet(host,exerciseId,') &&
  journal.includes('PursuitExerciseHeading(slot.exerciseId,') &&
  exerciseHistory.includes('host.reviewVerifiedKotlinHistory()') &&
  exerciseHistory.includes('CustomWorkoutVisitPart.MAIN') &&
  exerciseHistory.includes('DraftSetType.WORKING') &&
  exerciseHistory.includes('RIR not reported'),
  'Custom workout History must open complete correction-aware exercise facts, never invent RIR');
const composer=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
assert.ok(composer.includes('NativeKotlinExerciseHistorySheet(host,exerciseId,') &&
  composer.includes('PursuitExerciseHeading(target.exerciseId,onHistory=onHistory)'),
  'Generated workout must open the same verified full exercise history');
assert.ok(composer.includes('rememberSaveable(target.setKey,set.currentReceipt?.appliedRevision)') &&
  composer.includes('?:target.reps.minimum.toString()') &&
  composer.includes('existing?.actualRir?.toString().orEmpty()') &&
  composer.includes('Planned reps and load are editable starting points'),
  'Composer must prefill editable reps, preserve unknown RIR, and refresh a saved correction');
const restBridge=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutRestTimer.kt');
const timer=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeRestTimer.kt');
for(const source of [journal,composer]) {
  assert.ok(source.includes('NativeKotlinWorkoutRestTimer(workoutId,') &&
    source.includes('KotlinWorkoutRestDomain.inspect(') &&
    source.includes('Column(Modifier.weight(1f).verticalScroll('),
    'Kotlin workout rest must use the recovered journal and stay below the scrolling logger');
  assert.ok(source.includes('view.keepScreenOn=true') &&
    source.includes('onDispose{view.keepScreenOn=previous}'),
    'Each active Kotlin journal keeps the screen on and restores the prior view policy');
}
assert.ok(restBridge.includes('if(!rest.hasRemainingWork)return') &&
  restBridge.includes('NativeRestTimer(workoutId,rest.manualRestSeconds,enabled,') &&
  restBridge.includes('latestReceiptOperationId=rest.latestReceiptEventId') &&
  restBridge.includes('NativeRestCue(it.eventId,it.seconds,it.exerciseIndex)'),
  'Both journal paths share the native timer with exact receipt-bound durations and no final rest');
assert.ok(journey.includes('workoutScreens.SaveableStateProvider("custom:$customWorkoutId")') &&
  journey.includes('workoutScreens.SaveableStateProvider("composer:$selectedId")'),
  'Minimize/resume must retain timer and unsubmitted fields by exact journal identity');
assert.ok(timer.includes('var receiptBaselineChecked by remember(workoutId)') &&
  timer.includes('if(!receiptBaselineChecked)') &&
  timer.includes('observedEvent=latestReceiptOperationId') &&
  timer.includes('if(autoCue.seconds==0)'),
  'Remount must baseline old saved receipts; an immediate follow-up must clear a preceding countdown');
assert.ok(composer.includes('NativeKotlinFinishedComposerHandoff.inspect(stored,history)') &&
  composer.includes('host.reviewVerifiedKotlinHistory()') &&
  composer.includes('View finished workout in History') &&
  composer.includes('onClick={onReviewNext(exerciseId)}') &&
  composer.includes('Recheck committed History') &&
  journey.includes('onViewHistory={') &&
  journey.includes('onReviewNext={exerciseId->') &&
  journey.includes('controller.clear()'),
  'Generated Finish navigation requires independently verified committed history, never just the Finish flag');
console.log('PASS native workout recording and verified Finish-to-History/Coach navigation');
