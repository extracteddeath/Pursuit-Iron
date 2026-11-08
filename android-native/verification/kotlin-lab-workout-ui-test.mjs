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

assert.match(journal,/var actualRir by rememberSaveable\(set.setKey,part.name,existing\?\.appliedRevision\) \{\s*mutableStateOf\(saved\?\.performed\?\.actualRir\?\.toString\(\)\.orEmpty\(\)\)/,
  'Unknown actual RIR must remain unreported, never copied from the target');
assert.ok(journal.includes('var actualLoad by rememberSaveable(set.setKey,part.name,existing?.appliedRevision)') &&
  journal.includes('var actualReps by rememberSaveable(set.setKey,part.name,existing?.appliedRevision)'),
  'Uncommitted edit fields must survive an Android configuration change');
assert.match(journal,/onRecord\(KotlinJournalSetOutcome.COMPLETED,\s*KotlinPerformedSet\(weight,verified\?\.unit/,
  'Actual set writing must require an explicit Complete action');
assert.ok(journey.includes('selectedCustomWorkoutId==null)NavigationBar'),
  'Active custom workout must not have the tab strip above the logger');
const composer=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
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
console.log('PASS next native set gets a focused prefilled editable form; actual effort stays unknown and recording requires an explicit action');
