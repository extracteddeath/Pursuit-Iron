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
  'val focusDetail=upcoming?.let',
  'PursuitWorkoutFocusHeader(',
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
  exerciseHistory.includes('CustomWorkoutVisitPart.FOLLOW_UP') &&
  exerciseHistory.includes('Text("All entries")') &&
  exerciseHistory.includes('showAllVisits') &&
  exerciseHistory.includes('DraftSetType.WORKING') &&
  exerciseHistory.includes('RIR not reported'),
  'Custom workout History must open complete correction-aware exercise facts, never invent RIR');
const composer=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
const sharedFocus=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitDesignComponents.kt');
for(const phrase of [
  'internal fun PursuitWorkoutFocusHeader(',
  'Text("Focus on next")',
  'Text("All sets")',
  'modifier=Modifier.heightIn(min=48.dp)',
  'horizontalScroll(rememberScrollState())'
])assert.ok(sharedFocus.includes(phrase),'Missing shared workout focus behavior: '+phrase);
assert.ok(sharedFocus.includes('internal fun PursuitWorkoutReadinessPanel(') &&
  sharedFocus.includes('onReadinessChange(option)') &&
  sharedFocus.includes('onMovementRestrictedChange') &&
  sharedFocus.includes('modifier=Modifier.heightIn(min=48.dp)') &&
  composer.includes('PursuitWorkoutReadinessPanel(') &&
  journal.includes('PursuitWorkoutReadinessPanel(') &&
  composer.includes('if(!presentation.finished) {') &&
  journal.includes('if(!finished && current.finishedByEventId==null) {'),
  'Both workout types must expose the shared editable readiness/restriction controls only while training');
assert.ok(journal.indexOf('PursuitWorkoutFocusHeader(') <
    journal.indexOf('PursuitWorkoutReadinessPanel(') &&
  composer.indexOf('PursuitWorkoutFocusHeader(') <
    composer.indexOf('PursuitWorkoutReadinessPanel('),
  'Both workout types must place next-set focus before optional readiness input');
assert.ok(composer.includes('PursuitWorkoutFocusHeader(') &&
  journal.includes('PursuitWorkoutFocusHeader('),
  'Custom and generated workouts must share the same accessible focus panel');
assert.ok(composer.includes('val editorStates=rememberSaveableStateHolder()') &&
  composer.includes('editorStates.SaveableStateProvider("composer-set:${set.target.setKey}")') &&
  journal.includes('val editorStates=rememberSaveableStateHolder()') &&
  journal.includes('editorStates.SaveableStateProvider("main:${set.setKey}")'),
  'Switching Focus/All or visiting another set must preserve unsubmitted fields in both workout types');

assert.ok(composer.includes('NativeKotlinExerciseHistorySheet(host,exerciseId,') &&
  composer.includes('PursuitExerciseHeading(target.exerciseId,') &&
  composer.includes('onHistory=onHistory,onInfo=onInfo'),
  'Generated workout must open the same verified full exercise history');
assert.ok(composer.includes('rememberSaveable(target.setKey,set.currentReceipt?.appliedRevision)') &&
  composer.includes('?:target.reps.minimum.toString()') &&
  composer.includes('existing?.actualRir?.toString().orEmpty()') &&
  composer.includes('Planned reps and load are editable starting points'),
  'Composer must prefill editable reps, preserve unknown RIR, and refresh a saved correction');
const sharedSetFields=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeComponents.kt');
assert.ok(journal.includes('NativeSetValueFields(') &&
  journal.includes('showLoad=verified!=null') &&
  journal.includes('onLoad={actualLoad=it}') &&
  journal.includes('onReps={actualReps=it}') &&
  journal.includes('onRir={actualRir=it}') &&
  journal.includes('loadError=actualLoad.isNotBlank()&&weight==null') &&
  journal.includes('rirError=!effortValid') &&
  journal.includes('KotlinPerformedSet(weight,verified?.unit,') &&
  journal.includes('pendingExceptionalOutcome=KotlinJournalSetOutcome.FAILED_ATTEMPT'),
  'Custom workout must share bodyweight-aware stepper fields while retaining explicit complete, skip, failed and corrected history outcomes');
assert.ok(composer.includes('NativeSetValueFields(') &&
  composer.includes('showLoad=verifiedLoad!=null') &&
  composer.includes('onLoad={weight=it}') &&
  composer.includes('onReps={reps=it}') &&
  composer.includes('onRir={effort=it}') &&
  sharedSetFields.includes('showLoad:Boolean=true') &&
  sharedSetFields.includes('if(showLoad) OutlinedTextField(') &&
  sharedSetFields.includes('NativeSetCounterField("Reps"') &&
  sharedSetFields.includes('NativeSetCounterField("RIR"') &&
  sharedSetFields.includes('Bodyweight movement · no external weight') &&
  sharedSetFields.includes('RIR is optional · leave blank if unknown.'),
  'Generated workout must use shared accessible reps/RIR steppers and omit the load field for source-verified bodyweight sets');
const infoSheet=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutExerciseInfoSheet.kt');
assert.ok(sharedFocus.includes('onInfo:(()->Unit)?=null') &&
  sharedFocus.includes('contentDescription="Exercise details"') &&
  sharedFocus.includes('val stacked=maxWidth<380.dp || LocalDensity.current.fontScale>=1.35f') &&
  sharedFocus.includes('historyAction(Modifier.weight(1f))') &&
  sharedFocus.includes('historyAction(Modifier)') &&
  journal.includes('onInfo={infoSlotId=slot.slotId}') &&
  journal.includes('journal?.snapshot?.slots?.firstOrNull{it.slotId==slotId}') &&
  journal.includes('sets=customWorkoutInfoSets(slot.sets)') &&
  composer.includes('onInfo={infoExerciseId=set.target.exerciseId}') &&
  composer.includes('journal?.snapshot?.targets.orEmpty().filter') &&
  composer.includes('sets=composerWorkoutInfoSets(targets)') &&
  infoSheet.includes('internal fun composerWorkoutInfoSets(') &&
  infoSheet.includes('internal fun customWorkoutInfoSets(') &&
  infoSheet.includes('infoTitle(target.type,target.ordinal+1)') &&
  infoSheet.includes('infoTitle(target.type,snapshot.ordinalInSlot)') &&
  infoSheet.includes('targetRir?.toString() ?: "Not set"') &&
  infoSheet.includes('NativeEditSheet(busy=false,onDismiss=onDismiss)') &&
  infoSheet.includes('Text("Open full exercise history")') &&
  !infoSheet.includes('controller.record('),
  'Info must be a read-only, source-backed sheet with exact planned set facts and a full History action for both workout types');
const customPlate=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomPlateSheet.kt');
const verifiedPlateDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinVerifiedPlateLayoutDomain.kt');
assert.ok(journal.includes('NativeKotlinCustomPlateSheet(host,request,') &&
  journal.includes('current.originalSnapshot.stamp') &&
  journal.includes('onShowPlates={load,unit,basis->') &&
  journal.includes('verified?.basis==LoadBasis.BARBELL_TOTAL') &&
  journal.includes('onClick={onShowPlates(actualLoad,verified.unit,verified.basis)}') &&
  customPlate.includes('host.readCustomAuthoringSource()') &&
  customPlate.includes('source.revision!=stamp.gymCatalogSourceRevision') &&
  customPlate.includes('source.athleteId!=stamp.athleteId') &&
  customPlate.includes('reviewed.gym.gymId!=stamp.gymId') &&
  customPlate.includes('CustomExerciseAuthoringDomain.picker(') &&
  customPlate.includes('stock=option.availability.stock') &&
  customPlate.includes('as? VerifiedLoadStock.Barbell') &&
  customPlate.includes('KotlinVerifiedPlateLayoutDomain.inspect(') &&
  customPlate.includes('NativeEditSheet(busy=false,onDismiss=onDismiss)') &&
  verifiedPlateDomain.includes('EquipmentLoadDomain.inspect(bar,entered,unit)') &&
  verifiedPlateDomain.includes('private const val MAX_STATES=100_000') &&
  !customPlate.includes('controller.record('),
  'Plate viewer may only display exact current owner-matched custom barbell stock and must not log or change workout loads');
const workoutHost=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
assert.ok(composer.includes('NativeKotlinComposerPlateSheet(host,workoutId,request,') &&
  composer.includes('onShowPlates={load,unit->') &&
  composer.includes('verifiedLoad?.basis==LoadBasis.BARBELL_TOTAL') &&
  composer.includes('onClick={onShowPlates(weight,verifiedLoad.unit)}') &&
  customPlate.includes('internal fun NativeKotlinComposerPlateSheet(') &&
  customPlate.includes('host.reviewComposerPlateLayout(') &&
  customPlate.includes('private fun NativeKotlinPlateResultSheet(') &&
  workoutHost.includes('fun reviewComposerPlateLayout(') &&
  workoutHost.includes('owner.ownerKey,owner.athleteId,workoutId') &&
  workoutHost.includes('approval.approvedSource!=journal.originalSnapshot.source') &&
  workoutHost.includes('sourceExercise.equipment as?') &&
  workoutHost.includes('stock=equipment.inventory as?') &&
  workoutHost.includes('VerifiedLoadStock.Barbell') &&
  workoutHost.includes('KotlinVerifiedPlateLayoutDomain.inspect(stock,entered,unit)'),
  'Generated workout plates must use source-coherent owner approval, exact barbell inventory and the same read-only sheet');
// Both independently verified workout routes must present the SAME source-backed
// physical plate geometry, with responsive overflow and a complete readable count.
const visualPlates=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinVerifiedPlateDiagram.kt');
for(const phrase of [
  'internal fun NativeKotlinVerifiedPlateDiagram(layout: KotlinVerifiedPlateLayout)',
  'layout.perSide.flatMapIndexed',
  'shownTypes.asReversed().forEach',
  'shownTypes.forEach',
  'horizontalScroll(rememberScrollState())',
  'clearAndSetSemantics',
  'contentDescription=',
  'The full verified counts are below.',
  'layout.perSide.forEachIndexed',
  'Matching plates on both ends',
  'diagram not to scale'
])assert.ok(visualPlates.includes(phrase),
  'Missing safe, symmetric Material plate presentation: '+phrase);
assert.ok(customPlate.includes('NativeKotlinVerifiedPlateDiagram(layout)') &&
  customPlate.includes('private fun NativeKotlinPlateResultSheet(') &&
  visualPlates.includes('MAX_DIAGRAM_PLATES_PER_SIDE = 12') &&
  !visualPlates.includes('KotlinVerifiedPlateLayoutDomain.inspect(') &&
  !visualPlates.includes('controller.') &&
  !visualPlates.includes('host.') &&
  !visualPlates.includes('onRecord('),
  'The diagram must stay presentation-only and share the approved plate result from custom and Composer workouts');
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
for(const source of [journal,composer]) {
  assert.ok(source.includes('.widthIn(max=920.dp).align(Alignment.CenterHorizontally)') &&
    source.includes('Column(Modifier.widthIn(max=920.dp).fillMaxWidth()') &&
    source.includes('.align(Alignment.CenterHorizontally)'),
    'Both active workout routes must cap form and rest dock width for wide Android displays');
}
assert.ok(journey.includes('workoutScreens.SaveableStateProvider("custom:$customWorkoutId")') &&
  journey.includes('workoutScreens.SaveableStateProvider("composer:$selectedId")') &&
  journey.includes('var minimizedGeneratedId by rememberSaveable') &&
  journey.includes('minimizedGeneratedId=selectedId') &&
  journey.includes('PursuitPrimaryAction("Resume generated workout"') &&
  journey.includes('Text("Minimize")') &&
  journey.includes('minimizedGeneratedId=null'),
  'Both workout paths must preserve exact identity, timers and drafts, and generated workouts need an explicit resume shortcut');
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
const composerSwap=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinComposerSwapSheet.kt');
const composerSwapDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinComposerSwapReviewDomain.kt');
assert.ok(composerSwapDomain.includes('it.role==MuscleCreditRole.DIRECT') &&
  composerSwapDomain.includes('it.fractionalSet.signum()>0') &&
  composerSwapDomain.includes('reviewedDirectMuscles(candidate).intersect(baseline).size') &&
  composerSwap.includes('shared directly trained muscles: '),
  'Composer workout substitutes must rank by unique positive DIRECT credits, not duplicates or incidental muscle mentions');
assert.ok(composer.includes('NativeKotlinComposerSwapSheet(') &&
  composerSwap.includes('NativeEditSheet(busy=busy,onDismiss=onDismiss)') &&
  !composerSwap.includes('AlertDialog(') &&
  composerSwap.includes('KotlinComposerSwapRankingDomain.filterRanked(') &&
  composerSwap.includes('Text("Same pattern")') &&
  composerSwap.includes('Text("Shared direct muscles")') &&
  composerSwap.includes('sourceTargets.indices.forEach') &&
  composerSwap.includes('EquipmentLoadDomain.inspect(') &&
  composerSwap.includes('choice.option.sourcePracticalReps') &&
  composerSwapDomain.includes('fun filterRanked(') &&
  composerSwapDomain.includes('target.type==DraftSetType.WORKING'),
  'Generated workout swap must offer ranked muscle/pattern search, separately authored verified targets, and safe working-set rep bounds');
// The old UI draft's safe "Use Last" behavior is now carried forward into
// the current engine/Compose branch, NOT a wholesale rollback to its screens.
const draftPolicy=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/WorkoutLastDraftDomain.kt');
const lastPicker=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutReference.kt');
assert.match(draftPolicy,/history as\? WorkoutLastReferenceResult\.Ready/);
assert.match(draftPolicy,/record\.outcome!=KotlinJournalSetOutcome\.COMPLETED/);
assert.match(draftPolicy,/CustomWorkoutJournalDomain\.actualValid\(/);
assert.match(draftPolicy,/record\.loadBasis==currentLoad\.basis/);
assert.match(draftPolicy,/record\.loadBasis==null/);
assert.match(lastPicker,/if\(mode!=WorkoutReferenceMode\.LAST\)return/);
assert.match(lastPicker,/WorkoutLastDraftDomain\.reviewedActual\(history,key,currentLoad\)/);
assert.match(lastPicker,/onClick=\{onApply\(actual\)\}/);
assert.match(lastPicker,/Missing last RIR stays blank/);
assert.match(lastPicker,/heightIn\(min=48\.dp\)/);
assert.match(composer,/if\(!set\.isResolved\) PursuitUseLastCompletedSet\(/);
assert.match(composer,/key=WorkoutReferenceKey\(target\.setKey\)/);
assert.match(composer,/effort=last\.actualRir\?\.toString\(\)\.orEmpty\(\)/);
assert.match(journal,/if\(saved==null\) PursuitUseLastCompletedSet\(/);
assert.match(journal,/part==CustomWorkoutVisitPart\.FOLLOW_UP\)/);
assert.match(journal,/actualRir=last\.actualRir\?\.toString\(\)\.orEmpty\(\)/);
const lastHelper=lastPicker.slice(
    lastPicker.indexOf('internal fun PursuitUseLastCompletedSet('),
    lastPicker.indexOf('internal fun kotlinLoadBasisLabel('));
assert.doesNotMatch(lastHelper,/\.record\(|\.finish\(|\.write\(|\.approve\(|changePrescription\(/);

console.log('PASS native workout recording and verified Finish-to-History/Coach navigation and safe Last draft');
