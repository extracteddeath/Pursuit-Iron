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
  'initialMachineScopedLoad(target,saved?.performed,machineInstanceId)',
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
  '"main:${set.setKey}:station:"',
  'editorStates.SaveableStateProvider("follow:${set.setKey}")',
  'activeFocus?.part!=CustomWorkoutVisitPart.MAIN',
  'activeFocus?.part!=CustomWorkoutVisitPart.FOLLOW_UP'
])assert.ok(journal.includes(phrase),'Missing safe native focus behavior: '+phrase);
assert.match(journal,/if\(!verifiedFinishedInHistory \|\| showFinishedSets\)/,
  'Finished sessions must still expose recorded sets for review');

// One shared M3E hero action reaches the same durable owner as the set editor.
// No shortcut can skip a different selected set or an amplifier after the
// journal advances; a positive confirmation is always required.
const header=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitDesignComponents.kt');
const generated=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
for(const phrase of [
  'onSkip:(()->Unit)?=null',
  'val skipAction:@Composable (Modifier)->Unit=',
  'val stacked=maxWidth<520.dp',
  'Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())',
  'skipAction(Modifier)',
  'historyAction(Modifier)',
  'infoAction()'
])assert.ok(header.includes(phrase),'Focus header lacks responsive Skip/History/Info: '+phrase);
for(const phrase of [
  'onSkip=selected?.takeIf {',
  '!it.isResolved && !presentation.finished',
  '{pendingSkip=key}',
  'val skip=allSets.firstOrNull{it.target.setKey==pendingSkip}',
  'controller.record(skip.target.setKey,KotlinJournalSetOutcome.SKIPPED,null)'
])assert.ok(generated.includes(phrase),'Generated quick skip must reuse confirmation: '+phrase);
for(const phrase of [
  'var pendingQuickSkip by remember(workoutId)',
  'onSkip=nextVisit?.takeIf {',
  '{pendingQuickSkip=visit}',
  'val quickSkip=pendingQuickSkip',
  'CustomWorkoutJournalDomain.nextUnresolvedVisit(it)',
  'val canSkip=stillNext && !busy && !finished',
  'if(latest==quickSkip && !busy && !finished',
  'record(quickSkip.setKey,quickSkip.part,',
  'KotlinJournalSetOutcome.SKIPPED,null)',
  'Text("Keep training")'
])assert.ok(journal.includes(phrase),'Custom next-set shortcut must be revision-checked: '+phrase);
assert.ok(!header.includes('onClick=onSkip,enabled=enabled,modifier=Modifier.fillMaxWidth()'),
  'All actions must retain their own accessible 48dp touch targets');


assert.match(journal,/var actualRir by rememberSaveable\(set.setKey,part.name,existing\?\.appliedRevision\) \{\s*mutableStateOf\(saved\?\.performed\?\.actualRir\?\.toString\(\)\.orEmpty\(\)\)/,
  'Unknown actual RIR must remain unreported, never copied from the target');
assert.ok(journal.includes('var actualLoad by rememberSaveable(') &&
  journal.includes('set.setKey,part.name,existing?.appliedRevision,machineInstanceId') &&
  journal.includes('var actualReps by rememberSaveable(set.setKey,part.name,existing?.appliedRevision)'),
  'Uncommitted edit fields must survive configuration changes and remain machine-scoped');
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
  exerciseHistory.includes('ExerciseHistoryView.SETBACKS') &&
  exerciseHistory.includes('it.outcome!=KotlinJournalSetOutcome.COMPLETED') &&
  exerciseHistory.includes('val allSessions=rows.groupBy { it.sessionOrder }') &&
  exerciseHistory.includes('DraftSetType.WORKING') &&
  exerciseHistory.includes('RIR not reported'),
  'Custom workout History must open complete correction-aware exercise facts, never invent RIR');
const controller=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinSessionController.kt');
// Beta reliability: a previous session must NEVER remain actionable after
// invalid navigation, a failed store write or an asynchronous late callback.
// These source invariants supplement (not replace) Android-device acceptance.
const sessionRun=controller.slice(
  controller.indexOf('private fun run('),
  controller.indexOf('/** Reading never issues a new workout. */'));
for(const required of [
  'private val generation=AtomicLong(0)',
  'private val closed=AtomicBoolean(false)',
  'private val stateGate=Any()',
  'hideUnverified:Boolean=false',
  'if(!pending.compareAndSet(false,true)) {',
  'generation.incrementAndGet()',
  'NativeKotlinSessionState(workoutId=workoutId,busy=true)',
  'if(closed.get() || generation.get()!=requestGeneration)return@launch',
  'catch(cancelled:CancellationException)',
  'throw cancelled',
  'catch(_:Exception)',
  'unavailableReason=NativeJournalFileRefusal.STORAGE_UNAVAILABLE',
  'private fun rejectInvalidSelection(workoutId:String):Boolean',
  'NativeJournalFileRefusal.INVALID_ARGUMENT',
  'run(workoutId,{host.recoverWorkout(workoutId)},hideUnverified=true)',
  '},hideUnverified=true)'
])assert.ok(controller.includes(required),
  'Generated workout must invalidate stale/unverified session state: '+required);
assert.ok(!sessionRun.includes('busy=false,unavailableReason='),
  'Unknown storage failure must clear journal and presentation, not retain stale editable receipts');
const acceptedPublication=controller.slice(
  controller.indexOf('private fun accepted('),
  controller.indexOf('private fun run('));
assert.ok(acceptedPublication.includes('synchronized(stateGate)') &&
  acceptedPublication.includes('generation.get()==requestGeneration') &&
  acceptedPublication.includes('if(!closed.get() &&'),
  'Durable recovered journal publication must be atomic with Close and Clear');
assert.ok(sessionRun.includes('val requestGeneration=synchronized(stateGate)') &&
  sessionRun.includes('synchronized(stateGate) {') &&
  sessionRun.includes('accepted(workoutId,current,requestGeneration,'),
  'Opening, late recoveries and disk-failure state must all pass the same session publication lock');
const cleanup=controller.slice(controller.indexOf('fun clear()'));
assert.ok(cleanup.includes('fun clear() {\n        synchronized(stateGate)') &&
  cleanup.includes('override fun close() {\n        synchronized(stateGate)') &&
  cleanup.includes('scope.cancel()'),
  'Clear/Close must atomically invalidate pending journal writes and coaching callbacks');

const saveAndFinish=controller.slice(
  controller.indexOf('fun record('),
  controller.indexOf('fun clear()'));
assert.ok(saveAndFinish.includes('if(prior.busy || journal.finishedByEventId!=null ||') &&
  saveAndFinish.includes('if(prior.busy || !journal.canProposeFinish)return') &&
  saveAndFinish.includes('if(!closed.get() &&'),
  'No set, Finish or late coaching update may bypass busy, closed or journal revision gates');
const reset=controller.slice(controller.indexOf('fun clear()'),controller.indexOf('override fun close()'));
assert.ok(reset.includes('generation.incrementAndGet()') &&
  reset.includes('latestCoachRequest.incrementAndGet()') &&
  reset.includes('mutable.value=NativeKotlinSessionState()') &&
  !reset.includes('if(pending.get())return'),
  'Clearing the logger must suppress in-flight callbacks, not preserve the previous athlete session');
const failclosedScreen=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
assert.ok(failclosedScreen.includes('Verifying the saved workout and athlete ownership.') &&
  failclosedScreen.includes('state.unavailableReason==NativeJournalFileRefusal.NOT_ISSUED && !state.busy'),
  'Unverified workout recovery must never show enabled Start or stale set-entry controls');

const generatedCoach=controller.slice(controller.indexOf('fun reviewNextSet('),
  controller.indexOf('fun saveSessionNote('));
const customCoachStart=journal.indexOf('LaunchedEffect(host,workoutId,current.revision,');
const customCoach=journal.slice(customCoachStart,
  journal.indexOf('PageHeading("TODAY',customCoachStart));
for(const [name,body] of [['generated',generatedCoach],['custom',customCoach]]) {
  assert.ok(body.includes('host.reviewLive'+(name==='generated'
      ?'IssuedNextSet(':'CustomNextSet(')) &&
    body.includes('catch(cancelled:CancellationException)') &&
    body.includes('throw cancelled') &&
    body.includes('catch(_:Exception)') &&
    body.includes('null'),
    name+' optional live-coaching storage errors must fail closed, not crash the workout or hide coroutine cancellation');
}
const composer=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
// Machine A/B must be scoped to the gym that issued the workout. The
// displayed Last AND the editable one-tap "Use Last" must share the same
// machine filter, even when no machine is selected (legacy-only).
const machineSelector=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutMachineSelector.kt');
// A station choice is a data boundary, not a late effect allowed to erase
// athlete-entered weight. Both editors use the same physical-station-aware
// initializer and stable machine-keyed state; no values migrate from A to B.
assert.ok(machineSelector.includes('internal fun initialMachineScopedLoad(') &&
  machineSelector.includes('saved.machineInstanceId==selectedMachineId') &&
  machineSelector.includes('if(selectedMachineId!=null) ""') &&
  composer.includes('initialMachineScopedLoad(target.load,existing,machineInstanceId)') &&
  journal.includes('initialMachineScopedLoad(target,saved?.performed,machineInstanceId)') &&
  composer.includes('target.setKey,set.currentReceipt?.appliedRevision,machineInstanceId') &&
  journal.includes('set.setKey,part.name,existing?.appliedRevision,machineInstanceId') &&
  !composer.includes('LaunchedEffect(machineInstanceId)') &&
  !journal.includes('LaunchedEffect(machineInstanceId)'),
  'Machine identity changes must not silently overwrite pending native set weight edits');
assert.ok(composer.includes('editorStates.SaveableStateProvider(') &&
  composer.includes('(recordedMachineId?.let{"id:$it"} ?: "none")') &&
  journal.includes('(mainMachineId?.let{"id:$it"} ?: "none")') &&
  journal.includes('editorStates.SaveableStateProvider(') &&
  !composer.includes('editorStates.SaveableStateProvider("composer-set:${set.target.setKey}")') &&
  !journal.includes('editorStates.SaveableStateProvider("main:${set.setKey}")'),
  'A -> B -> A must recover separate unsaved drafts, including after Focus/All or rotation');
const machineChoiceHost=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
const machineChoiceStore=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinWorkoutMachineChoiceFileStore.kt');
assert.ok(machineChoiceHost.includes('fun recoverWorkoutMachineChoices(') &&
  machineChoiceHost.includes('fun selectWorkoutPhysicalMachine(') &&
  machineChoiceHost.includes('issuedMachineWorkoutScope(signed,workoutId,custom)') &&
  machineChoiceHost.includes('scope.observedMachines.forEach') &&
  machineChoiceHost.includes('scope.gymId,0,defaults') &&
  machineChoiceStore.includes('fun read(') &&
  machineChoiceStore.includes('@Synchronized fun save(') &&
  machineChoiceStore.includes('StandardCopyOption.ATOMIC_MOVE') &&
  machineChoiceStore.includes('MessageDigest.isEqual('),
  'Workout machine A/B choice must be a durable, source-verified, owner-scoped CAS file');
assert.ok(machineSelector.includes('internal fun rememberNativeWorkoutMachineChoices(') &&
  machineSelector.includes('host.recoverWorkoutMachineChoices(workoutId,custom)') &&
  machineSelector.includes('host.selectWorkoutPhysicalMachine(workoutId,custom,') &&
  machineSelector.includes('saved=result.snapshot'),
  'Both native workout screens must share the durable station selection controller');
for(const source of [journal,composer]) {
  assert.ok(source.includes('rememberNativeWorkoutMachineChoices(') &&
    source.includes('machineChoices.onSelect(') &&
    source.includes('machineChoices.ready') &&
    source.includes('machineChoices.busy') &&
    !source.includes('var machineSelections by rememberSaveable('),
    'Workout machine identity cannot depend on screen recreation state alone');
}

// Issuance should normally freeze defaults BEFORE the athlete opens the UI.
// Existing recorded sets remain journal-owned, and the durability bootstrap
// does not turn a partial preference write into a fake successful set.
assert.ok(machineChoiceHost.includes('private fun pinMachineChoiceForIssuedWorkout(') &&
  machineChoiceHost.includes('if(issued is NativeKotlinIssueResult.Committed)') &&
  machineChoiceHost.match(/if\(issued is NativeCustomJournalResult\.Durable\)/g)?.length>=2 &&
  machineChoiceHost.includes('pinMachineChoiceForIssuedWorkout(workoutId,custom=false)') &&
  machineChoiceHost.match(/pinMachineChoiceForIssuedWorkout\(workoutId,custom=true\)/g)?.length===2,
  'All native issuer paths must pin verified A/B defaults on successful issuance');
assert.ok(journal.includes('val requiresNewMachineChoice=') &&
  journal.includes('val previouslyAttempted=recordedMain?.performed') &&
  journal.includes('config.machines.none{it.id==selectedMachineId && it.active}') &&
  journal.includes('outcome==KotlinJournalSetOutcome.SKIPPED ||') &&
  journal.includes('?.performed!=null') &&
  journal.includes('hadAttempt || !requiresNewMachineChoice'),
  'Custom machine swaps must require an active station for new weighted observations');
assert.ok(composer.includes('val needsMachineSelection=set.actual==null') &&
  composer.includes('previouslyAttempted.machineInstanceId else perSetMachineId') &&
  composer.includes('perSetMachineConfig.machines.none{') &&
  composer.includes('!needsMachineSelection))') &&
  composer.includes('!needsMachineSelection)'),
  'Generated swaps must not record a legacy or inactive station by accident');


for(const source of [journal,composer]) {
  assert.ok(source.includes('KotlinMachineAlternativesDomain.forGymExercise('),
    'Every native workout must resolve physical machines for the issued gym');
  assert.ok(source.includes('history=physicalMachineLastReferences(lastReferences,machineInstanceId)'),
    'One-tap Use Last may not copy actuals from another physical machine');
}
assert.ok(journal.includes('current.originalSnapshot.stamp.gymId') &&
  composer.includes('journal?.originalSnapshot?.source?.gymId') &&
  machineSelector.includes('normalizeMachineId(selectedMachineId)') &&
  machineSelector.includes('normalizeMachineId(\n                it.actual?.machineInstanceId)'),
  'Issued gym and legacy-only Last selection must remain explicit');
const generatedCoachHost=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
const liveCoachDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinJournalLiveSetCoachDomain.kt');
const customLiveSetBridge=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomLiveSetCoachReview.kt');
assert.ok(liveCoachDomain.includes('sealed interface LiveMachineCoachScope') &&
  liveCoachDomain.includes('LiveMachineCoachScope.Physical') &&
  liveCoachDomain.includes('PHYSICAL_MACHINE_HISTORY_MISMATCH') &&
  liveCoachDomain.includes('target.performed?.machineInstanceId!=machineId') &&
  generatedCoachHost.includes('LiveMachineCoachScope.Physical(selectedPhysicalMachine)') &&
  generatedCoachHost.includes('maximumReviewStep,assistance,machineScope)') &&
  customLiveSetBridge.includes('LiveMachineCoachScope.Physical(selectedPhysicalMachine)') &&
  customLiveSetBridge.includes('machineScope)'),
  'Both native live-coaching bridges must enforce A/B history identity, even when stock is unverified');
assert.ok(liveCoachDomain.includes('val machineScope:LiveMachineCoachScope=') &&
  liveCoachDomain.includes('ordered[index-1].setKey,coaching,machineScope') &&
  composer.includes('it.machineScope==expectedCoachMachineScope') &&
  journal.includes('it.machineScope==expectedCoachMachineScope') &&
  composer.includes('LiveMachineCoachScope.Physical(currentMachine)') &&
  journal.includes('LiveMachineCoachScope.Physical(selectedMachineId)'),
  'A saved A/B selector change must hide old asynchronous coaching in BOTH screens before the next review');

const physicalStockDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinMachineLiveCoachingDomain.kt');
assert.ok(generatedCoachHost.includes('val issuedGym=journal.originalSnapshot.source.gymId') &&
  generatedCoachHost.includes('val hasPhysicalStations=roster?.configurations?.any{') &&
  generatedCoachHost.includes('it.gymId==issuedGym && it.exerciseId==selected.exerciseId') &&
  generatedCoachHost.includes('it.machines.isNotEmpty()') &&
  generatedCoachHost.includes('workoutMachineChoices.read(signed.ownerKey,') &&
  generatedCoachHost.includes('as? NativeWorkoutMachineChoiceResult.Saved') &&
  generatedCoachHost.includes('KotlinMachineLiveCoachingDomain.confirmedForNext(') &&
  generatedCoachHost.includes('roster==null) null') &&
  generatedCoachHost.includes('KotlinJournalLiveSetCoachDomain.review('),
  'Generated coaching must verify durable physical station and exact confirmed machine stack');
const customMachineCoach=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomLiveSetCoachReview.kt');
assert.ok(customMachineCoach.includes('private val machineRosters:NativeKotlinMachineRosterFileStore') &&
  customMachineCoach.includes('private val workoutMachineChoices:NativeKotlinWorkoutMachineChoiceFileStore') &&
  customMachineCoach.includes('machineRosters.read(ownerKey)') &&
  customMachineCoach.includes('val issuedGym=journal.originalSnapshot.stamp.gymId') &&
  customMachineCoach.includes('it.machines.isNotEmpty()') &&
  customMachineCoach.includes('workoutMachineChoices.read(ownerKey,workoutId,') &&
  customMachineCoach.includes('planned?.basis==movement.expectedLoadBasis') &&
  customMachineCoach.includes('KotlinMachineLiveCoachingDomain.confirmedForNext(') &&
  generatedCoachHost.includes('coachSettings,machineRosters,workoutMachineChoices'),
  'Custom A/B coaching must verify source, physical station, and exact confirmed stack');
assert.ok(physicalStockDomain.includes('journal.effectiveSets[target.setKey]?.operation') &&
  physicalStockDomain.includes('journal.effectiveVisits[CustomWorkoutVisitKey(') &&
  physicalStockDomain.includes('actual?.machineInstanceId==selectedMachineId') &&
  physicalStockDomain.includes('exactPhysicalLoad(machineStock,actual?.load,actual?.unit)') &&
  physicalStockDomain.includes('exactPhysicalLoad(it,load.value,load.unit)') &&
  physicalStockDomain.includes('confirmedStack(it,') &&
  physicalStockDomain.includes('journal.originalSnapshot.source.gymId!=config.gymId') &&
  physicalStockDomain.includes('journal.originalSnapshot.stamp.gymId!=config.gymId'),
  'A/B stock cannot use earlier working sets on another machine or in another issued gym');
// Journal revisions do not change on a durable A/B selector write.
// Any in-flight coach reading the previous station is stale; the UI must
// suppress it until the new owner-verified choice produces fresh advice.
assert.ok(composer.includes('readiness,movementRestricted,machineChoices.encoded,') &&
  composer.includes('machineChoices.ready,machineChoices.busy)') &&
  composer.includes('if(machineChoices.ready && !machineChoices.busy &&') &&
  composer.includes('val coach=state.liveCoach?.takeIf {') &&
  composer.includes('machineChoices.ready && !machineChoices.busy &&'),
  'Generated next-set coaching must refresh and hide stale A/B advice on switch');
assert.ok(journal.includes('machineChoices.encoded,machineChoices.ready,machineChoices.busy)') &&
  journal.includes('if(busy || !machineChoices.ready || machineChoices.busy ||') &&
  journal.includes('val advice=liveCoach?.takeIf {') &&
  journal.includes('!busy && !finished && machineChoices.ready &&'),
  'Custom next-set coaching must refresh and hide stale A/B advice on switch');
const machineProgression=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinProgressionTimelineDomain.kt');
assert.ok(machineProgression.includes('UNRESOLVED_PHYSICAL_MACHINE') &&
  machineProgression.includes('val interveningUnknownMachine=dayApplicable.any') &&
  machineProgression.includes('stored.completionOrder<=olderStored.completionOrder') &&
  machineProgression.includes('it.isEmpty() || it.size>1') &&
  machineProgression.includes('KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID in it') &&
  machineProgression.includes('KotlinTimelineReviewStatus.UNRESOLVED_PHYSICAL_MACHINE'),
  'A/B progression cannot cherry-pick around unknown, legacy, or mixed-machine exposures');
const strengthStarter=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CoachFirstProgramStartDomain.kt');
assert.ok(strengthStarter.includes('data class PhaseOffer(val lifts:List<String>,val requiredDays:Int)') &&
  strengthStarter.includes('groups.filter{2*it.size<=days}') &&
  strengthStarter.includes('PhaseOffer(it,2)') &&
  strengthStarter.includes('val phaseOffers=repeatOffers+groups.map{PhaseOffer(it,1)}') &&
  strengthStarter.includes('namedPrimaries.associateWith{requiredPrimaryDays}'),
  'New strength and peak starter must audit feasible repeat-lift skill policies before one-day fallback');
const starterVariantRetention=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CoachFirstProgramStartDomain.kt');
assert.ok(starterVariantRetention.includes('val calibratedPrimaryFamilies=selected.filter') &&
  starterVariantRetention.includes('it.role==ComposerRole.SECONDARY') &&
  starterVariantRetention.includes('it.variantId.contains("incline",ignoreCase=true)') &&
  starterVariantRetention.includes('it.familyId in calibratedPrimaryFamilies') &&
  starterVariantRetention.includes('choices[index]=choices[index].copy(requiredVisits=1)') &&
  starterVariantRetention.includes('if(input.policyVersion in setOf(') &&
  starterVariantRetention.includes('V6_POLICY_VERSION,V7_POLICY_VERSION,POLICY_VERSION') &&
  starterVariantRetention.includes('V7_POLICY_VERSION->"v7"') &&
  starterVariantRetention.includes('else->"v8"'),
  'V6/V7 replay and V8 strength/peak policies must retain calibrated related incline presses');
const machineLast=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/WorkoutLastReferenceDomain.kt');
assert.ok(machineLast.includes('data class WorkoutMachineReferenceKey(') &&
  machineLast.includes('val machineSets:Map<WorkoutMachineReferenceKey,WorkoutLastSetReference>') &&
  machineLast.includes('fun customBase(') &&
  machineLast.includes('fun composerBase(') &&
  machineLast.includes('fun appendMachineFacts(') &&
  machineLast.includes('destination.keys.removeAll { it.key==key }') &&
  machineSelector.includes('previous.machineSets.filterKeys { it.machineId==requested }'),
  'Native A/B Last must recover each station without borrowing other stacks or bypassing unknown skips');

const trendUi=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExercisePerformanceTrend.kt');
const trendDomain=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeExercisePerformanceTrendDomain.kt');
for(const phrase of [
  'val gymId:String?=null',
  'val machineInstanceId:String?=null',
  'entry.evidence.source.gymId',
  'entry.gymId',
  'actual.machineInstanceId!=null',
  'NativeExerciseTrendKey(issued.basis,window,'
])assert.ok(trendDomain.includes(phrase),'Missing machine-isolated factual trend: '+phrase);
for(const phrase of [
  'var stationGym by rememberSaveable(exerciseId)',
  'var stationMachine by rememberSaveable(exerciseId)',
  'val stationSeries=basisSeries.filter{',
  'Text("Gym · physical machine"',
  'stationGym=item.first',
  'stationMachine=item.second'
])assert.ok(trendUi.includes(phrase),'Missing independent A/B trend selector: '+phrase);
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
// Quick injury/discomfort entry is a real, explicit path to the SAME
// owner-scoped note receipts in both workouts. It changes optional coaching
// context, not a durable set result or a signed program prescription.
assert.ok(sharedFocus.includes('onReportConcern:(()->Unit)?=null') &&
  sharedFocus.includes('onClick=onReportConcern') &&
  sharedFocus.includes('Text("Injury or discomfort · add a note")') &&
  sharedFocus.includes('You still choose Skip or Swap') &&
  sharedFocus.includes('modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)'),
  'The shared injury entry needs a large accessible action and truthful, nonautomatic semantics');
assert.match(journal,/onReportConcern=\{\s*(?:\/\/[^\n]*\n\s*)*movementRestricted=true\s*readiness=CoachReadiness.LOW\s*noteError=null\s*showSessionNote=true\s*\}/,
  'Custom injury entry must restrict coaching and open its durable note editor');
assert.match(composer,/onReportConcern=if\(journal!=null\) \(\{[\s\S]*?movementRestricted=true\s*readiness=CoachReadiness.LOW\s*showSessionNote=true\s*\}\) else null/,
  'Generated injury entry must require a verified journal and open its own note editor');
assert.ok(journal.includes('host.saveIssuedCustomSessionNote(workoutId,operation)') &&
  composer.includes('onSave={controller.saveSessionNote(it)}') &&
  journal.includes('CustomWorkoutJournalOperation.SessionNote('),
  'Injury notes may only persist through an explicit saved journal note, never an implicit set action');
assert.ok(composer.includes('val coach=state.liveCoach?.takeIf {') &&
  composer.includes('!movementRestricted && !state.busy &&') &&
  journal.includes('val advice=liveCoach?.takeIf {') &&
  /val advice=liveCoach\?\.takeIf \{[\s\S]*?!movementRestricted &&\s*!busy && !finished/.test(journal),
  'Neither workout may display a previous next-set recommendation after an injury restriction is selected');

// The focus header must agree with the type-specific set editor, not
// count warm-ups as working sets or the amplifier as a new main set.
assert.ok(journal.includes('val sameType=slot.sets.filter {') &&
  journal.includes('it.prescription.type==set.prescription.type') &&
  journal.includes('val number=sameType.indexOfFirst {') &&
  journal.includes('"Amplifier follow-up · $label $number of ${sameType.size}"') &&
  journal.includes('"$label $number of ${sameType.size}"') &&
  !journal.includes('entry $number of ${slot.sets.size}') &&
  composer.includes('"Set"} $number of ${typeSets.size}"'),
  'Focus headings must use the verified warm-up/working ordinal in both journals');
assert.ok(sharedFocus.includes('import androidx.compose.foundation.selection.toggleable') &&
  sharedFocus.includes('role=Role.Checkbox') &&
  sharedFocus.includes('value=movementRestricted,enabled=enabled') &&
  sharedFocus.includes('onValueChange=onMovementRestrictedChange') &&
  sharedFocus.includes('onCheckedChange=null'),
  'Movement restriction must be one labeled accessible 48dp toggle, not a small isolated checkbox or double tap handler');

assert.ok(journal.indexOf('PursuitWorkoutFocusHeader(') <
    journal.indexOf('PursuitWorkoutReadinessPanel(') &&
  composer.indexOf('PursuitWorkoutFocusHeader(') <
    composer.indexOf('PursuitWorkoutReadinessPanel('),
  'Both workout types must place next-set focus before optional readiness input');
assert.ok(composer.includes('PursuitWorkoutFocusHeader(') &&
  journal.includes('PursuitWorkoutFocusHeader('),
  'Custom and generated workouts must share the same accessible focus panel');
assert.ok(composer.includes('val editorStates=rememberSaveableStateHolder()') &&
  composer.includes('"composer-set:${set.target.setKey}:station:"') &&
  journal.includes('val editorStates=rememberSaveableStateHolder()') &&
  journal.includes('"main:${set.setKey}:station:"'),
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
  sharedSetFields.includes('if(showLoad)OutlinedTextField(') &&
  sharedSetFields.includes('NativeSetCounterField(') &&
  sharedSetFields.includes('label="Reps"') &&
  sharedSetFields.includes('label="RIR"') &&
  sharedSetFields.includes('showFieldHelp:Boolean=true') &&
  sharedSetFields.includes('Bodyweight movement · no external load.') &&
  sharedSetFields.includes('RIR is optional; leave it blank if unreported.') &&
  sharedSetFields.includes('NativeSetEntryPolicy.stepRepsBy(') &&
  sharedSetFields.includes('NativeSetEntryPolicy.stepRirBy(') &&
  composer.includes('showFieldHelp=false') &&
  journal.includes('showFieldHelp=false'),
  'Generated workout must use shared accessible reps/RIR steppers and omit the load field for source-verified bodyweight sets');
const infoSheet=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutExerciseInfoSheet.kt');
assert.ok(sharedFocus.includes('onInfo:(()->Unit)?=null') &&
  sharedFocus.includes('contentDescription="Exercise details"') &&
  sharedFocus.includes('val stacked=maxWidth<520.dp ||') &&
  sharedFocus.includes('LocalDensity.current.fontScale>=1.30f') &&
  sharedFocus.includes('val hasActions=exerciseLabel!=null &&') &&
  sharedFocus.includes('historyAction(Modifier)') &&
  sharedFocus.includes('skipAction(Modifier)') &&
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
  journey.includes('minimizedGeneratedId=openId.takeIf') &&
  journey.includes('PursuitPrimaryAction("Resume workout"') &&
  journey.includes('host.currentlyApprovedKotlinWorkoutId()') &&
  journey.includes('if(approved==awaitingWorkoutId)') &&
  journey.includes('TextButton(enabled=!sessionState.busy,') &&
  journey.includes('onClick=::minimizeGeneratedWorkout') &&
  journey.includes('BackHandler(enabled=customWorkoutId==null && selectedId!=null)') &&
  journey.includes('if(!controller.state.value.busy)') &&
  journey.includes('controller.state.value.presentation?.finished!=true') &&
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

// Both package identities launch the identical one-engine UI and never
// instantiate the obsolete JS training runtime during app startup.
const betaMain=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/MainActivity.kt');
const betaApplication=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitIronApplication.kt');
const betaDebugLauncher=read('android-native/app/src/debug/java/io/github/extracteddeath/pursuitiron/KotlinEnginePreviewActivity.kt');
const betaReleaseManifest=read('android-native/app/src/release/AndroidManifest.xml');
const betaDebugManifest=read('android-native/app/src/debug/AndroidManifest.xml');
assert.ok(betaMain.includes('NativeKotlinCandidateJourney(') &&
  !betaMain.includes('NativeRuntimeHost(') &&
  !betaMain.includes('nativeRuntime') &&
  !betaMain.includes('NativePreviewApp(') &&
  !betaApplication.includes('NativeRuntimeHost') &&
  betaApplication.includes('NativeKotlinCandidateHost(applicationContext)') &&
  betaDebugLauncher.includes('class KotlinEnginePreviewActivity:MainActivity()') &&
  betaReleaseManifest.includes('android:name=".MainActivity"') &&
  betaDebugManifest.includes('android:name=".KotlinEnginePreviewActivity"'),
  'Release and debug entry points must start one Kotlin journey, never the legacy JS runtime');
assert.ok(betaMain.includes('pursuit_native_appearance') &&
  betaMain.includes('PursuitIronTheme(themeId=themeId)') &&
  betaMain.includes('safeDrawingPadding()'),
  'Kotlin launcher must preserve existing theme choice and system-bar insets');

// Kotlin beta packaging must not include the old JS execution graph.
// Launch readiness still requires physical-device evidence before release.
const migrationGradle=read('android-native/app/build.gradle.kts');
assert.ok(!migrationGradle.includes('implementation(project(":engine-runtime"))') &&
  migrationGradle.includes('stageKotlinBetaSources') &&
  migrationGradle.includes('"**/NativeKotlin*.kt"') &&
  migrationGradle.includes('verifyEarlyBetaRuntime') &&
  migrationGradle.includes('verification/BETA_DEVICE_ACCEPTANCE.md'),
  'Ship only Kotlin source, block release until on-device beta acceptance');

// Early beta uses one Kotlin runtime and a voluntary feedback route. These
// are source-only guards: final release still needs signed artifact inspection
// and real Android testing, and no feedback is uploaded by the app itself.
const betaSettings=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCoachSettingsScreen.kt');
const betaGradle=read('android-native/app/build.gradle.kts');
const betaScope=read('android-native/LAUNCH_BETA_SCOPE.md');
assert.ok(betaSettings.includes('Share beta feedback') &&
  betaSettings.includes('Intent(Intent.ACTION_SEND)') &&
  betaSettings.includes('context.startActivity(Intent.createChooser(') &&
  betaSettings.includes('feedbackDraft=it.take(2000)') &&
  betaSettings.includes('enabled=feedbackDraft.isNotBlank()') &&
  betaSettings.includes('No training records, injury notes or diagnostics are attached automatically.') &&
  !betaSettings.includes('READ_CONTACTS'),
  'Early beta feedback requires an explicit opt-in share, no silent training/medical data upload');
assert.ok(betaGradle.includes('verifyEarlyBetaRuntime') &&
  betaGradle.includes('releaseRuntimeClasspath') &&
  betaGradle.includes('forbiddenProjectPaths') &&
  betaGradle.includes('":engine-runtime"') &&
  betaGradle.includes('forbiddenExternalModules') &&
  betaGradle.includes('forbiddenSourcePatterns') &&
  betaGradle.includes('forbiddenComponents.isEmpty()') &&
  betaGradle.includes('violations.isEmpty()') &&
  betaGradle.includes('verification/BETA_DEVICE_ACCEPTANCE.md') &&
  betaGradle.includes('"packageRelease", "assembleRelease"') &&
  betaGradle.includes('"bundleRelease", "packageReleaseBundle"') &&
  !betaGradle.includes('it.name == "preReleaseBuild"') &&
  betaScope.includes('one Kotlin engine'),
  'Early beta release must refuse the legacy JS engine and document single-policy ownership');

console.log('PASS native workout recording and verified Finish-to-History/Coach navigation and safe Last draft');


const priorLeveling=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
for(const required of [
  '"Leveling · review previous workout"',
  'rememberPriorWorkoutLevelingReview(',
  'previous=physicalMachineLastReferences(',
  'currentSets=workingSets.map { set->',
  'goalRir=first.targetRir?.takeIf{sameGoal}',
  'levelWeight=prior.proposedStraightSetLoad',
  'unfinished.forEach { set->',
  'levelVersion++',
  'PursuitPreviousWorkoutLevelingDialog(',
  'onApply={',
  'observed=observed',
  'onDismiss={priorLevelingScope=null}'
]) assert.ok(priorLeveling.includes(required),
  'Prior-session Leveling must be explicit, same-machine and draft-only: '+required);
assert.ok(priorLeveling.includes(
  'referenceMode==WorkoutReferenceMode.LAST ||\n            priorLevelingScope!=null'),
  'Prior-session Leveling must read source-verified Last even when Target is selected');

const m3eLeveling=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitDesignComponents.kt');
for(const phrase of [
  'internal fun PursuitPreviousWorkoutLevelingDialog(',
  'review:LevelingReviewResult?',
  'observed:List<KotlinPerformedSet>',
  'Modifier.fillMaxWidth().verticalScroll(rememberScrollState())',
  'actual.actualRir?.let',
  'RIR not reported',
  'candidate.averageObservedLoad.stripTrailingZeros()',
  'candidate.proposedStraightSetLoad.stripTrailingZeros()',
  'if(actionable) Button(',
  'onClick=onApply,enabled=!busy',
  'Modifier.heightIn(min=48.dp)',
  '"Keep existing values"',
  'future workouts do not change.',
  'Log what you actually perform.'
]) assert.ok(m3eLeveling.includes(phrase),
  'The shared M3 Expressive Leveling dialog needs accessible, honest, source-visible UI: '+phrase);

const priorReviewCoroutine=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutReference.kt');
for(const required of [
  'internal fun rememberPriorWorkoutLevelingReview(',
  'remember(currentSets,previous,machineInstanceId,repBand,',
  'LaunchedEffect(currentSets,previous,machineInstanceId,repBand,',
  'review=null',
  'withContext(Dispatchers.Default)',
  'DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(',
  'catch(cancelled:CancellationException)',
  'throw cancelled',
  'WorkoutLastReferenceResult.Unavailable'
]) assert.ok(priorReviewCoroutine.includes(required),
  'Prior-session Leveling must be cancellation-scoped and avoid UI-thread equipment inspection: '+required);
assert.ok(!priorLeveling.includes('DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout('),
  'No expensive verified equipment review on the generated Compose UI thread');

const waveSixFixtures=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymFacilityChoices.kt');
for(const id of ['squat-box','heel-wedge','step-platform','exercise-sliders','ab-wheel']) {
  assert.ok(waveSixFixtures.includes('"'+id+'" to '),
    'Workout accessory must be selectable in real Kotlin gym UI: '+id);
}
const waveSixLabels=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseLabels.kt');
for(const id of ['smith-hip-thrust','pinwheel-curl','pjr-pullover','single-cable-raise','cable-crossover']) {
  assert.ok(waveSixLabels.includes('"'+id+'" to '),
    'New reviewed physical equipment variants require proper athlete-facing names: '+id);
}
