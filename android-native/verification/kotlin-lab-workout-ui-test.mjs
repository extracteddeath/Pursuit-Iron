import assert from 'node:assert/strict';
import fs from 'node:fs';

// Narrow UI source contract; actual on-device interaction remains a separate
// acceptance milestone, not a claim made by this static test.
const read=p=>fs.readFileSync(p,'utf8');
const journal=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt');
const customFinishFeedback=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeFinishReviewPolicy.kt');
// The custom Finish result may be lost AFTER the durable transaction.
// A null acknowledgement is NOT evidence of a failed save.
assert.ok(journal.includes('NativeFinishReviewPolicy.customFinishMessage(') &&
  journal.includes('verifiedFinishedInHistory') &&
  journal.includes('host.finishIssuedCustomWorkout(workoutId,') &&
  !journal.includes('Finish not committed:') &&
  customFinishFeedback.includes('fun customFinishMessage(') &&
  customFinishFeedback.includes('Finish status is unknown.') &&
  customFinishFeedback.includes('may already be saved') &&
  customFinishFeedback.includes('NativeCustomFinishRefusal.STORAGE_UNAVAILABLE') &&
  customFinishFeedback.includes('NativeCustomFinishRefusal.FINISH_EVENT_MISMATCH'),
  'Finish uncertainty must be visible without changing the durable owner or fabricating a rollback');

const journey=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt');


// Mixed measured sessions must use ONE reviewed immutable parent plan,
// separate true seconds/distance/reps, stable keys and authenticated issuance.
// Saved Compose drafts are athlete intent, never permission to issue a set.
const measured=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinMeasuredWorkoutScreen.kt');
// Measured holds/carries/banded repetitions are a third issued native
// workout route. Its Finish must be available without scrolling past a
// potentially long list, yet cannot exist during recovery/authoring, after
// completion, or for a foreign/unresolved selected set.
const measuredDock=measured.indexOf(
  'if(entryState==NativeMeasuredWorkoutEntryState.RECORDING &&');
assert.ok(measuredDock>0 &&
  measured.includes('Column(Modifier.weight(1f).verticalScroll(') &&
  measured.slice(measuredDock).includes('measured.canProposeFinish') &&
  measured.slice(measuredDock).includes('measured.finishedByEventId==null') &&
  measured.slice(measuredDock).includes('MeasuredWorkoutFocusDomain.review(') &&
  measured.slice(measuredDock).includes('finishReview=stored?.let') &&
  measured.slice(measuredDock).includes('host.finishMeasuredWorkout(workoutId,') &&
  measured.slice(measuredDock).includes('reviewed.revision') &&
  measured.slice(measuredDock).includes('NativeFinishReviewPolicy.canCommit(') &&
  measured.split('PursuitPrimaryAction("Review and finish"').length===2 &&
  !measured.includes('Text("Finish workout")'),
  'Measured Finish must use one fixed, owner-verified bottom action and the unchanged revision-checked transaction');
const measuredBuilder=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/MeasuredSessionAuthoringDomain.kt');
assert.ok(measured.includes('rememberSaveable(workoutId)') &&
  measured.includes('MeasuredSessionAuthoringDomain.restoreDraft(it)') &&
  measured.includes('MeasuredSessionAuthoringDomain.saveDraft(current)') &&
  measured.includes('MeasuredSessionAuthoringDomain.compose(') &&
  measured.includes('sourceKey==draftSourceKey') &&
  measured.includes('Add exercise to session') &&
  measured.includes('Remove last') &&
  measured.includes('Clear draft') &&
  measured.includes('queuedCandidate') &&
  measured.includes('Review every set from this exact source-approved') &&
  measured.includes('candidateReview.plan.sets.forEachIndexed') &&
  measured.includes('approvedExternalLoad.toPlainString()') &&
  measured.includes('Matching pair confirmed') &&
  measured.includes('host.previewMeasuredWorkoutConsent(') &&
  measured.includes('host.confirmMeasuredWorkoutConsent('));
// Focus mode renders ONE authenticated active editor, not 64 expanded forms.
// Only a real durable set receipt may auto-advance; no optimistic skip/finish.
const measuredFocus=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/MeasuredWorkoutFocusDomain.kt');
assert.ok(measured.includes('MeasuredWorkoutFocusDomain.review(') &&
  measured.includes('afterDurableRecord(journal,') &&
  measured.includes('focusSetKey=next') &&
  measured.includes('Jump to a set') &&
  measured.includes('Previous') &&
  measured.includes('Up next:') &&
  measured.includes('focus.selectedOrdinal') &&
  measured.includes('NativeMeasuredExerciseSetEditor(') &&
  !measured.includes('journal.approvedPlan.sets.forEach{set->'),
  'Workout must show one selected issued set with explicit navigation and only verified progression');
assert.ok(measuredFocus.includes('after.revision!=before.revision+1') &&
  measuredFocus.includes('after.receipts.dropLast(1)!=before.receipts') &&
  measuredFocus.includes('before.bySetKey[setKey]?.effectiveRecord!=null') &&
  measuredFocus.includes('after.bySetKey[it.setKey]?.effectiveRecord==null'));

// A running manual stopwatch is an optional, monotonic draft aid.
 // No duration is ever issued or recorded by the clock itself.
const holdStopwatch=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/MeasuredHoldStopwatchDomain.kt');
const measuredSetEditor=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeMeasuredExerciseSetEditor.kt');
assert.ok(measured.includes('rememberSaveableStateHolder()') &&
  measured.includes('measuredEditorStates.SaveableStateProvider(') &&
  measured.includes('measured:$workoutId:') &&
  measuredSetEditor.includes('MeasuredHoldStopwatchDomain.start(') &&
  measuredSetEditor.includes('MeasuredHoldStopwatchDomain.stop(') &&
  measuredSetEditor.includes('MeasuredHoldStopwatchDomain.usableSeconds(stopwatch)') &&
  measuredSetEditor.includes('Use ${stopwatchSeconds}s for ') &&
  measuredSetEditor.includes('SystemClock.elapsedRealtime()') &&
  measuredSetEditor.includes('Record set') &&
  holdStopwatch.includes('MAX_BOOT_DRIFT_MS') &&
  holdStopwatch.includes('elapsed/1000L') &&
  !holdStopwatch.includes('MeasuredWorkoutOperation.Record'),
  'Stopwatch may only copy real measured seconds into independent saveable per-set drafts; it cannot log outcomes');

assert.ok(measuredBuilder.includes('MeasuredWorkoutJournalDomain.begin(it)') &&
  measuredBuilder.includes('sourceRevision') &&
  measuredBuilder.includes('set-$index') &&
  measuredBuilder.includes('EquipmentLoadDomain.inspect(') &&
  measuredBuilder.includes('MeasuredPhysicalStockSource.equipmentIdFor(') &&
  measuredBuilder.includes('it in verifiedBandIds') &&
  !measuredBuilder.includes('DraftSetLoad.ExplicitBodyweight'),
  'Mixed measured sessions must retain native typed measurements and exact verified equipment, not fabricate rep loads');

const bandGym=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymSourceCandidateScreen.kt');
const bandStock=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinPhysicalStockDraft.kt');
const bandRule=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/RatedResistanceBandDomain.kt');
const bandComposer=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CoachExerciseCatalogDomain.kt');
const bandPicker=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomExerciseAuthoringDomain.kt');
const bandCycle=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomTrainingCycleDomain.kt');
assert.ok(bandGym.includes('selected?.equipmentId==RatedResistanceBandDomain.EQUIPMENT_ID'));
assert.ok(bandGym.includes('it==PhysicalStockEditorKind.EXACT_LOADS'));
assert.ok(bandGym.includes('RatedResistanceBandDomain.confirmedDiscreteStock(equipmentId,it)'));
assert.ok(bandGym.includes('Tension varies with stretch'));
assert.ok(bandStock.includes('"rated-resistance-band-set" -> "Resistance bands (manufacturer-rated, discrete)"'));
assert.ok(bandRule.includes('stock !is VerifiedLoadStock.Totals'));
assert.ok(bandRule.includes('stock.basis!=LoadBasis.EXERCISE_OVERRIDE'));
assert.ok(bandComposer.includes('RatedResistanceBandDomain.confirmedDiscreteStock(spec.equipmentId,stock)'));
assert.ok(bandPicker.includes('RatedResistanceBandDomain.confirmedDiscreteStock(move.equipmentId,stock)'));
assert.ok(bandCycle.includes('RatedResistanceBandDomain.validForReviewedExercise('));


// Staged rest-pause shares the same durable visit UI and revision-fenced
// journal as myo-reps; technique choices cannot invent a performed set.
const restPauseSheet=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomExerciseSheet.kt');
const restPauseExecution=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomWorkoutExecutionDomain.kt');
const restPauseProtocol=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/AdvancedAmplifierProtocolDomain.kt');
const restPauseJournal=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomWorkoutJournalDomain.kt');
assert.match(restPauseSheet,/CustomAmplifierKind.REST_PAUSE -> "2"/);
assert.match(restPauseSheet,/Max rest-pause efforts \(1–3\)/);
assert.match(restPauseSheet,/Stop early if you miss reps or form deteriorates/);
assert.match(restPauseExecution,/CustomAmplifierKind.REST_PAUSE ->/);
assert.match(restPauseExecution,/AdvancedAmplifierProtocolDomain.continueRestPause/);
assert.match(restPauseProtocol,/fun validStagedFollowUps\(/);
const snapshotCodec=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomWorkoutSnapshotCodec.kt');
const snapshotAmplifierGate=snapshotCodec.slice(
  snapshotCodec.indexOf('p.amplifier?.let{ amp ->'),
  snapshotCodec.indexOf('require(duration==snapshot.knownSetAndRestSeconds)'));
assert.ok(snapshotAmplifierGate.includes('AdvancedAmplifierProtocolDomain.validStagedFollowUps(amp)'),
  'Versioned workout snapshots must use the same staged technique authority as Kotlin engine');
assert.ok(!snapshotAmplifierGate.includes('amp.kind==CustomAmplifierKind.MYO_REPS'),
  'A myo-only codec would reject valid rest-pause or cluster snapshots on restart');

assert.match(restPauseProtocol,/fun continueRestPause\(/);
// The live custom journal now protects all completed amplifier types through
// one factual activation-load/station rule. A rest-pause-only special case
// would fail to protect legacy one-drop and same-load partials.
assert.match(restPauseJournal,/nextEffective.any/);
assert.match(restPauseJournal,/CustomWorkoutExecutionDomain.sameActualLoadAndStation/);
assert.match(restPauseJournal,/key.part==CustomWorkoutVisitPart.FOLLOW_UP/);
assert.match(journal,/CustomWorkoutExecutionDomain.followUpVisits\(/);
assert.match(journal,/AdvancedAmplifierProtocolDomain\.resolveFollowUpEntryLoad\(/);
assert.match(journal,/AdvancedAmplifierProtocolDomain.stageLabel\(it,stageOrdinal\)/);
assert.match(journal,/AdvancedAmplifierProtocolDomain.stageEarlyStopTitle/);
assert.match(journal,/AdvancedAmplifierProtocolDomain.stageEarlyStopDescription/);
assert.match(journal,/isStagedShortRestStop/);
// Clusters use the same durable revision-fenced stage authority as myo and
// rest-pause, while the engine enforces a planned submaximal quality rule.
assert.match(restPauseSheet,/CustomAmplifierKind.CLUSTER -> "3"/);
assert.match(restPauseSheet,/CustomAmplifierKind.CLUSTER -> "2"/);
assert.match(restPauseSheet,/CustomAmplifierKind.CLUSTER -> "30"/);
assert.match(restPauseSheet,/Planned clusters \(2–5\)/);
assert.match(restPauseSheet,/Planned submaximal quality reps, not a failure method/);
assert.match(restPauseExecution,/AdvancedAmplifierProtocolDomain.continueCluster/);
assert.match(restPauseProtocol,/fun continueCluster\(/);
assert.match(restPauseProtocol,/amp.followUpRounds in 2\.\.5 && amp.followUpReps in 1\.\.4/);
assert.match(restPauseProtocol,/CustomAmplifierKind.CLUSTER ->/);
assert.match(restPauseJournal,/operation.outcome==KotlinJournalSetOutcome.COMPLETED/);
assert.match(journal,/AdvancedAmplifierProtocolDomain\.resolveFollowUpEntryLoad\(/);
assert.match(journal,/CustomAmplifierKind.CLUSTER\)/);
assert.match(journal,/AdvancedAmplifierProtocolDomain.stageLabel\(/);

// Lengthened partials are a separately logged, optional FULL-ROM final-set
// extension. They must be visible on the exact row, in Target and Last, after
// completion; storage and the domain must reject early-set or changed loads.
const partialProtocol=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/AdvancedAmplifierProtocolDomain.kt');
const partialCycle=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomTrainingCycleDomain.kt');
const partialAuthor=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomExerciseAuthoringDomain.kt');
const partialStore=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomWorkoutSnapshotCodec.kt');
const partialHistory=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHistoryScreen.kt');
assert.match(partialProtocol,/CustomAmplifierKind.LENGTHENED_PARTIALS -> AdvancedAmplifierTemplate/);
assert.match(partialProtocol,/amp.followUpRounds==1 && amp.followUpReps in 1\.\.8/);
assert.match(partialProtocol,/amp.followUpRestSeconds==0 && amp.followUpLoad==null/);
assert.match(restPauseSheet,/technique!=CustomAmplifierKind.LENGTHENED_PARTIALS/);
assert.match(restPauseSheet,/index==forms.indexOfLast\{it.type==DraftSetType.WORKING\}/);
assert.match(restPauseSheet,/CustomAmplifierKind.LENGTHENED_PARTIALS -> "3"/);
assert.match(restPauseSheet,/CustomAmplifierKind.LENGTHENED_PARTIALS -> "0"/);
assert.match(restPauseSheet,/Partial reps \(not full ROM\)/);
assert.match(partialCycle,/setIndex!=lastWorkingSet/);
assert.match(partialAuthor,/validateSelectedSets\(/);
assert.match(partialStore,/index==lastWorking/);
// All five techniques must be anchored to their own main-set row before
// logging, and remain visible with factual stage outcomes after logging.
const inlineTechnique=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomAmplifierSetSummaryDomain.kt');
assert.match(journal,/CustomAmplifierSetSummaryDomain.review\(current,set\)/);
assert.match(journal,/if\(part==CustomWorkoutVisitPart.MAIN && amplifierSummary!=null\)/);
assert.match(journal,/if\(position==setCount\)"Last working set"/);
assert.match(inlineTechnique,/CustomWorkoutExecutionDomain.followUpVisits\(journal,set\)/);
assert.match(inlineTechnique,/journal.effectiveVisits\[key\]/);
assert.match(inlineTechnique,/CustomAmplifierKind.DROP_SET/);
assert.match(journal,/AdvancedAmplifierProtocolDomain\.resolveFollowUpEntryLoad\(/);
assert.match(journal,/AmplifierFollowUpEntryLoad\.RecordedActivation/);
assert.match(partialHistory,/Lengthened partials · partial reps/);
assert.match(partialHistory,/final-set partial extension/);


// A second physical drop is an independently reviewed weight, never a copy
// of the first reduction, and must survive custom-cycle and issued-workout
// disk codecs with separate version guards.
const twoDropCycle=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomTrainingCycleDomain.kt');
const twoDropAuthor=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomExerciseAuthoringDomain.kt');
const twoDropHistory=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomWorkoutHistoryEvidenceDomain.kt');
const savedDropCorrection=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomSavedHistoryCorrectionDomain.kt');
assert.match(twoDropCycle,/val secondDropLoad: DraftSetLoad\? = null/);
assert.match(twoDropCycle,/validDropSequence\(/);
assert.match(twoDropAuthor,/amp.secondDropLoad/);
assert.match(restPauseProtocol,/fun dropStageLoad\(/);
assert.match(restPauseProtocol,/fun followUpLoadForStage\(/);
assert.match(restPauseProtocol,/fun validDropSequence\(/);
// Both the next-step engine and editable Focus must now consult the same
// actual preceding drop before authorizing any second-stage weight.
assert.match(restPauseExecution,/AdvancedAmplifierProtocolDomain\.resolveFollowUpEntryLoad\(/);
assert.match(restPauseProtocol,/precedingDropPerformed:KotlinPerformedSet\?=null/);
assert.match(journal,/precedingDropPerformed=\(precedingDrop\?\.operation as\?/);
assert.match(journal,/precedingDropReceiptRevision=precedingDrop\?\.appliedRevision/);
assert.match(journal,/":previous-drop:"/);

// One authoritative stage evaluation drives Focus prefill, live logging,
// saved correction and stage availability. No duplicated physical load
// inequalities in the journal or historical correction reducers.
assert.match(restPauseProtocol,/private fun eligiblePerformedDropTarget\(/);
assert.match(restPauseProtocol,/fun validDropStageObservation\(/);
assert.match(restPauseProtocol,/fun validPerformedDropStage\(/);
assert.match(restPauseExecution,/AdvancedAmplifierProtocolDomain\.resolveFollowUpEntryLoad\(/);
assert.match(restPauseJournal,/AdvancedAmplifierProtocolDomain\.validDropStageObservation\(/);
assert.match(savedDropCorrection,/AdvancedAmplifierProtocolDomain\.validDropStageObservation\(/);
// Both finished and failed drop observations must be physically lower,
// rather than trusting an authored reduction from the earlier snapshot.
assert.match(restPauseJournal,/operation\.outcome!=KotlinJournalSetOutcome\.SKIPPED/);
assert.match(savedDropCorrection,/operation\.outcome!=KotlinJournalSetOutcome\.SKIPPED/);
assert.doesNotMatch(restPauseJournal,/performedLoad\.compareTo\(previousLoad\)/);
assert.doesNotMatch(savedDropCorrection,/actualLoad\.compareTo\(previousLoad\)/);
assert.match(restPauseJournal,/followUpLoadForStage\(/);
assert.match(twoDropHistory,/followUpLoadForStage\(/);
assert.match(restPauseSheet,/val secondDropLoad: String = ""/);
assert.match(restPauseSheet,/secondDropLoad=row.getOrNull\(13\)/);
assert.match(restPauseSheet,/extraRounds=="2"/);
assert.match(restPauseSheet,/Drop 2 · verified load below drop 1/);
assert.match(journal,/Drop \$stageOrdinal of \$stageTotal/);
// New per-slot progression choices use v6; a legacy two-drop draft still
// writes v5, preserving historical fingerprints and signed custom cycles.

const protocolPreview=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleCandidateScreen.kt');
assert.match(protocolPreview,/AdvancedAmplifierProtocolDomain.stageNoun\(amp.kind,plural=true\)/);
assert.match(protocolPreview,/AdvancedAmplifierProtocolDomain.stageReviewCue\(amp\)/);
assert.doesNotMatch(protocolPreview,/Each myo mini-set saves separately/);
assert.match(protocolPreview,/private fun NativeKotlinSetTechniqueSummary\(/);
assert.match(protocolPreview,/key\(set.setKey\)/);
assert.match(protocolPreview,/rememberSaveable\(setKey\)/);
assert.match(protocolPreview,/modifier=Modifier.heightIn\(min=48.dp\).semantics/);
assert.match(protocolPreview,/if\(showDetails\) Surface\(/);
assert.match(protocolPreview,/Text\(if\(showDetails\)"Less" else "Details"\)/);
assert.match(protocolPreview,/NativeKotlinSetTechniqueSummary\(/);
const customTechniqueExcerpt=protocolPreview.slice(
  protocolPreview.indexOf('private fun NativeKotlinSetTechniqueSummary('),
  protocolPreview.indexOf('private fun pretty(value: Enum<*>)'));
assert.ok(!customTechniqueExcerpt.includes('Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,'),
  'Native amp no longer requires permanent large preview card');

assert.match(journal,/Each performed stage is logged separately/);
assert.ok(journal.includes('"follow:${set.setKey}:stage:${followKey.stage}:activation:"+'),
  "stage identity must survive Focus/All and cold resume");

for(const phrase of [
  'CustomWorkoutJournalDomain.nextUnresolvedVisit(it)',
  'initiallyExpanded=activeFocus==mainKey',
  'initiallyExpanded=activeFocus==followKey',
  'mutableStateOf(saved==null && initiallyExpanded)',
  'initialMachineScopedLoad(target,saved?.performed,machineInstanceId)',
  'prescription.reps.minimum',
  'follower?.followUpReps',
  'Blank RIR stays unreported.',
  'progress={visitProgress.fraction}',
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
  'val activeFocus=(requestedFocus ?: nextVisit).takeIf',
  'val focusDetail=upcoming?.let',
  'PursuitWorkoutFocusHeader(',
  'slot.sets.filter{activeFocus==null ||',
  '"main:${set.setKey}:station:"',
  '"follow:${set.setKey}:stage:${followKey.stage}:activation:"+',
  'activeFocus?.part!=CustomWorkoutVisitPart.MAIN',
  'activeFocus?.part!=CustomWorkoutVisitPart.FOLLOW_UP'
])assert.ok(journal.includes(phrase),'Missing safe native focus behavior: '+phrase);
// A completed main with an unfinished amplifier is not 100% done.
// Display counting must match the source-required conditional follow-up,
// not just the main set count, and never become a load-progression source.
for(const phrase of [
  'CustomWorkoutVisitFocusDomain.progress(current)',
  'visitProgress.recordedVisits',
  'visitProgress.requiredVisits',
  'visitProgress.recordedMainSets',
  'visitProgress.requiredFollowUps',
  'visitProgress.recordedFollowUps',
  'progress={visitProgress.fraction}'
])assert.ok(journal.includes(phrase),'Actual custom visit progress must include amplifiers: '+phrase);
// Focus/All must announce truthful scope. A user deliberately reviewing
// a previous set should be able to return directly to the journal's next
// unresolved set without affecting source load or receipt ownership.
const generatedFocus=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
// The same fixed footer owns the rest countdown while entries remain and
// ONE Review/Finish action once the owner-journal is fully resolved. Never
// make the user scroll past a long workout to finish, and never auto-save.
const generatedDock=generatedFocus.indexOf('if(presentation?.canFinish==true');
const customDock=journal.indexOf('if(current?.canProposeFinish==true');
assert.ok(generatedDock>0 &&
  generatedFocus.lastIndexOf('NativeKotlinWorkoutRestTimer(',generatedDock)>0 &&
  generatedFocus.includes('journal?.finishedByEventId==null)') &&
  generatedFocus.slice(generatedDock).includes('finishReview=journal?.let') &&
  generatedFocus.slice(0,generatedDock).includes('controller.finish(reviewed.revision)') &&
  generatedFocus.split('PursuitPrimaryAction("Review and finish"').length===2,
  'Generated Finish must be a single guarded bottom action after rest, never a scroll card or auto-finish');
assert.ok(customDock>0 &&
  journal.lastIndexOf('NativeKotlinWorkoutRestTimer(',customDock)>0 &&
  journal.slice(customDock,customDock+300).includes('current.finishedByEventId==null') &&
  journal.slice(customDock).includes('finishReview=journal?.let') &&
  journal.includes('CustomWorkoutVisitFocusDomain.progress(current)') &&
  journal.split('PursuitPrimaryAction("Review and finish"').length===2 &&
  !journal.includes('Text("Review Finish")') &&
  journal.includes('Recover interrupted Finish'),
  'Custom Finish must wait for all main/amplifier visits and retain explicit interrupted-commit recovery');
// A Finish dialog is consent to one exact durable workout, not permission
// to finalize whatever corrected journal is current by the time it is tapped.
const finishPolicy=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeFinishReviewPolicy.kt');
const finishController=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinSessionController.kt');
const finishRegression=read('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeFinishReviewPolicyTest.kt');
const finishBetaGradle=read('android-native/app/build.gradle.kts');
assert.ok(finishPolicy.includes('reviewed.workoutId==workoutId') &&
  finishPolicy.includes('reviewed.revision==currentRevision') &&
  finishController.includes('fun finish(reviewedRevision:Long)') &&
  finishController.includes('journal.revision!=reviewedRevision') &&
  finishController.includes('UUID.randomUUID(),reviewedRevision') &&
  generatedFocus.includes('controller.finish(reviewed.revision)') &&
  journal.includes('CustomWorkoutJournalOperation.Finish(') &&
  journal.includes('reviewed.revision))') &&
  measured.includes('MeasuredWorkoutOperation.Finish(') &&
  measured.includes('reviewed.revision))') &&
  !measured.includes('finishDialog=true') &&
  !generatedFocus.includes('showFinishConfirm=true') &&
  !journal.includes('finishConfirm=true') &&
  finishRegression.includes('acceptsOnlyTheExactReviewedWorkoutRevision') &&
  finishRegression.includes('aNewReviewIsRequiredAfterAnySavedRevisionChange') &&
  finishBetaGradle.includes('"**/NativeFinishReviewPolicy.kt"'),
  'All native Finish routes must reject stale reviewed revisions, never silently re-consent');

const concernGuide=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinMovementConcernDialog.kt');
for(const [screen,label] of [[generatedFocus,'generated'],[journal,'custom']]) {
  for(const required of [
    'pendingConcern=NativeMovementConcernRequest(',
    'NativeMovementConcernPresentation.review(',
    'NativeKotlinMovementConcernDialog(',
    'pendingExerciseSkip=selectedRequest',
    'showSessionNote=true',
    'movementRestricted=true',
    'readiness=CoachReadiness.LOW'
  ])assert.ok(screen.includes(required),label+' concern must reach safe note/skip choices: '+required);
}
assert.match(concernGuide,/request.journalRevision!=currentRevision/);
assert.match(concernGuide,/it.journalRevision==currentRevision/);
assert.match(concernGuide,/if\(count>0\)/);
assert.match(concernGuide,/Write session note/);
assert.match(concernGuide,/Review skipping remaining exercise/);
assert.match(concernGuide,/Review replacement exercises/);
assert.match(concernGuide,/replacementScopeId=if\(count>0 && canReviewReplacement/);
assert.match(concernGuide,/journal\.receipts\.none/);
assert.match(concernGuide,/slot\.supersetGroup!=null/);
for(const [source,label] of [[generatedFocus,'generated'],[journal,'custom']]) {
  assert.ok(source.includes('canReviewReplacement=NativeMovementSwapEligibility.'),
      label+' must use exact unfinished journal to determine replacement eligibility');
  assert.ok(source.includes('onReviewReplacement={') &&
      source.includes('val candidate=options.replacementScopeId') &&
      source.includes('latest?.revision==request.journalRevision'),
      label+' must revalidate journal before invoking existing reviewed swap');
}
assert.match(generatedFocus,/reviewSwap\(candidate\)/);
assert.match(journal,/reviewSwap\(target\)/);
assert.doesNotMatch(concernGuide,/host\.|\.record\(|\.finish\(|\.save\(/);
const m3focus=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitDesignComponents.kt');
for(const fragment of [
  'exerciseLabel ?: if(focusOnNext)',
  '"All entries recorded · review Finish"',
  '"All sets · choose a set"'
])assert.ok(m3focus.includes(fragment),'Shared focus cannot mislabel All sets: '+fragment);
for(const fragment of [
  'showAll -> "ALL SETS"',
  'setDetail=if(showAll)null else focusDetail',
  'onFocusChange={focus->',
  'selectedKey=presentation.firstUnresolvedSetKey ?:selectedKey',
  '!it.isResolved && !presentation.finished && !showAll',
  'Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())',
  'NativeKotlinWorkoutFocusNavigation(',
  'nextUnfinished=presentation.firstUnresolvedSetKey',
  'onReturnToNextUnfinished={'
])assert.ok(generatedFocus.includes(fragment),
  'Generated Focus/All must be accurate, accessible and recovery aware: '+fragment);
assert.equal(generatedFocus.split('NativeKotlinWorkoutFocusNavigation(').length-1,1,
  'Generated Focus must show exactly one source-typed Previous/Next navigator');
assert.ok(!generatedFocus.includes('Text("Jump to next unlogged")') &&
  !generatedFocus.includes('val nextUnlogged=presentation.firstUnresolvedSetKey'),
  'Remove the duplicate standalone Previous/Next/Jump row below the set editor');
assert.ok(journal.includes('!focusOnNext -> "ALL SETS"') &&
  journal.includes('requestedFocus!=null -> "REVIEW SET"'),
  'Custom focus header must reflect All sets and explicit set review');
// In custom Focus the selected reviewed entry is distinct from the
// journal's actual next unlogged visit and must survive rotation. Previous
// and Next include only real amplifier follow-ups.
const focusDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomWorkoutVisitFocusDomain.kt');
for(const fragment of [
  'CustomWorkoutVisitFocusDomain.visibleOrder(current)',
  'var reviewSetKey by rememberSaveable(workoutId)',
  'var reviewPart by rememberSaveable(workoutId)',
  'it.name==reviewPart',
  'it in availableVisits',
  'val activeFocus=(requestedFocus ?: nextVisit).takeIf',
  'val upcoming=activeFocus?.let {visit->',
  'if(activeFocus?.part==CustomWorkoutVisitPart.FOLLOW_UP)',
  'onSkip=activeFocus?.takeIf {',
  'it==nextVisit && machineChoices.ready',
  'steps=availableVisits.map{visit->',
  'NativeKotlinFocusStep(',
  'visit,current.effectiveVisits.containsKey(visit)',
  'nextUnfinished=nextVisit',
  'reviewSetKey=visit.setKey',
  'reviewPart=visit.part.name',
  'initiallyExpanded=activeFocus==mainKey',
  'initiallyExpanded=activeFocus==followKey',
  'reviewStage=visit.stage',
  'if(reviewSetKey==visit.setKey && reviewPart==visit.part.name &&'
])assert.ok(journal.includes(fragment),
  'Custom Focus must not steal or skip another visit: '+fragment);
const sharedNavigation=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutFocusNavigation.kt');
for(const fragment of [
  'Text("Previous set")','Text("Next set")',
  'Text("Jump to next unfinished set")',
  'steps.map{it.key}.distinct().size!=steps.size',
  'it.key==nextUnfinished && !it.resolved'
])assert.ok(sharedNavigation.includes(fragment),
  'Shared Focus navigation must keep real issued visit identity: '+fragment);
assert.ok(journal.includes('onReturnToNextUnfinished={') &&
  journal.includes('reviewSetKey=null') &&
  journal.includes('reviewPart=null'),
  'Return to the saved next visit must clear stale manual Focus, not a journal entry');
for(const fragment of [
  'fun visibleOrder(journal:CustomWorkoutJournalDraft)',
  'CustomWorkoutExecutionDomain.orderedSets(journal.snapshot)',
  'saved?.outcome==KotlinJournalSetOutcome.COMPLETED',
  'CustomWorkoutExecutionDomain.followUpVisits(journal,set)',
  'CustomWorkoutVisitPart.FOLLOW_UP'
])assert.ok(focusDomain.includes(fragment),
  'Read-only focus order must preserve main/amplifier causality: '+fragment);
assert.match(journal,/onSkip=activeFocus\?\.takeIf \{\s*it==nextVisit/);
assert.match(journal,/onFocusChange=\{[\s\S]*?reviewSetKey=null\s*reviewPart=null/);
assert.match(journal,/if\(!verifiedFinishedInHistory \|\| showFinishedSets\)/,
  'Finished sessions must still expose recorded sets for review');

// Leveling remains fully functional but only runs its more expensive
// machine-scoped reviews after explicit expansion. This prevents the
// Leveling tools from burying the actual editable set beneath the header.
for(const [source,label,scope] of [
  [read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt'),'generated','priorLevelingScope=null'],
  [journal,'custom','priorLevelingSlotId=null']
]) {
  for(const fragment of [
    'val numberOfWorkingSets=', 'if(numberOfWorkingSets>=2) {',
    'var levelingExpanded by rememberSaveable(',
    'Text(if(levelingExpanded)"Hide Leveling options"',
    '"Leveling · adjust current sets"', 'if(levelingExpanded) {',
    'DoubleProgressionLevelingDomain',scope
  ])assert.ok(source.includes(fragment),
    label+' workout has lost optional Leveling visibility or safe source scope: '+fragment);
}
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
  'onSkip=activeFocus?.takeIf {',
  '{pendingQuickSkip=visit}',
  'val quickSkip=pendingQuickSkip',
  'CustomWorkoutJournalDomain.nextUnresolvedVisit(it)',
  'val canSkip=stillNext && !busy && !finished',
  'if(latest==quickSkip && !busy && !finished',
  'record(quickSkip.setKey,quickSkip.part,',
  'KotlinJournalSetOutcome.SKIPPED,null,',
  'stage=quickSkip.stage',
  'Text("Keep training")'
])assert.ok(journal.includes(phrase),'Custom next-set shortcut must be revision-checked: '+phrase);
assert.ok(!header.includes('onClick=onSkip,enabled=enabled,modifier=Modifier.fillMaxWidth()'),
  'All actions must retain their own accessible 48dp touch targets');


const localDrafts=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinPendingWorkoutDrafts.kt');
assert.ok(journal.includes('saved?.performed?.actualRir?.toString().orEmpty()') &&
  localDrafts.includes('val rir=rememberSaveable(identity.value,receiptRevision,committed)') &&
  localDrafts.includes('if(committed)null else store.read(identity)'),
  'Unknown actual RIR must stay blank and a committed receipt must override any restored UI draft');
assert.ok(journal.includes('activationPerformed=main?.performed') &&
  journal.includes('AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(') &&
  journal.includes('activationReceiptRevision=observed?.appliedRevision') &&
  journal.includes('val startingLoad=when(followUpEntryLoad)') &&
  journal.includes('AmplifierFollowUpEntryLoad.RecordedActivation') &&
  journal.includes('AmplifierFollowUpEntryLoad.ActivationUnavailable -> ""') &&
  journal.includes('Your issued target remains unchanged.'),
  'Myo mini-set prefill must be activation-owned, correction-scoped and separate from the issued prescription');
assert.ok(journal.includes('val draftIdentity=NativePendingWorkoutDraftIdentity.make(') &&
  journal.includes('set.setKey,visitDraftIdentity,machineInstanceId,target,expectedReps,') &&
  journal.includes('":activation:"+(activationReceiptRevision?.toString() ?: "none")') &&
  localDrafts.includes('val load=rememberSaveable(identity.value,receiptRevision,committed)') &&
  localDrafts.includes('val reps=rememberSaveable(identity.value,receiptRevision,committed)') &&
  localDrafts.includes('if(committed)store.discard(identity)'),
  'Uncommitted edit fields must survive configuration and cold reopen while remaining machine- and receipt-scoped');
assert.match(journal,/onRecord\(KotlinJournalSetOutcome.COMPLETED,\s*actual\)/,
  'Actual set writing must require an explicit Complete action');
assert.ok(journal.includes('KotlinWorkoutEntryDomain.parse(target,actualLoad,actualReps,actualRir)') &&
  journal.includes('val actual=(entry as? KotlinSetEntryResult.Accepted)?.performed') &&
  journal.includes('!sameLoadInvalidCompletion && !invalidDropCompletion &&') &&
  journal.includes('AdvancedAmplifierProtocolDomain.validSameLoadFollowUp(') &&
  journal.includes('AdvancedAmplifierProtocolDomain.validDropStageObservation(') &&
  journal.includes('!sameLoadInvalidFailure && !invalidDropFailure &&') &&
  journal.includes('!invalidIssuedRepBand,') &&
  journal.includes('KotlinSetNumberAuthorityDomain.validIssuedRepBand(prescription.reps)'),
  'Custom actuals must use the bounded Kotlin parser and block invalid issued rep ranges and amplifier loads');
assert.ok(journal.includes('pendingExceptionalOutcome=KotlinJournalSetOutcome.SKIPPED') &&
  journal.includes('pendingExceptionalOutcome=KotlinJournalSetOutcome.FAILED_ATTEMPT') &&
  journal.includes('if(pending!=null && !locked) AlertDialog(') &&
  journal.includes('target,actualLoad,actualReps,actualRir,failed=true)') &&
  journal.includes('onRecord(KotlinJournalSetOutcome.FAILED_ATTEMPT,\n                                failedActual)') &&
  journal.includes('"Confirm skip" else "Log failed attempt"'),
  'Custom skipped/failed visits must require confirmation before a journal write');
// Saved rows stay compact and correction is an explicit sheet in both
// workout types. A changed receipt, not a tap or an unacknowledged write,
// resets the saveable correction UI. Runtime parser/reducer tests verify
// the failed-zero and actual-value behavior independently of this wiring.
assert.ok(generatedFocus.includes('existing?.reps?.toString()') &&
  !generatedFocus.includes('existing?.reps?.takeIf{it>0}') &&
  generatedFocus.includes('allowFailedAttempt=existing?.reps==0'),
  'A failed attempt must keep its recorded zero on opening a correction');
for(const [source,condition,sheet] of [
  [generatedFocus,'if(correcting && set.isResolved && !finished)',
    'NativeSetEditSheet(busy=busy,onDismiss={correcting=false})'],
  [journal,'if(saved!=null && editing)',
    'NativeSetEditSheet(busy=saving,onDismiss={editing=false})']
]) {
  assert.ok(source.includes(condition) && source.includes(sheet) &&
    source.includes('saveError?.let {Text(it,color=MaterialTheme.colorScheme.error,') &&
    source.includes('Saved values change only after you confirm the correction.'),
    'Corrections must show errors in the sheet and retain draft fields on a rejected save');
}
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
  saveAndFinish.includes('if(prior.busy || !journal.canProposeFinish ||') &&
  saveAndFinish.includes('journal.revision!=reviewedRevision)return') &&
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
// Generated and custom Finish share athlete-facing wording, but still
// require their OWN revision-guarded durable commit paths. No raw journal
// implementation terminology should leak into these workout confirmations.
for(const screen of [composer,journal]) {
  assert.ok(screen.includes('title={Text("Finish this workout?")}') &&
    screen.includes('Text("Finish workout")') &&
    screen.includes('Text("Review sets")') &&
    screen.includes('NativeFinishReviewPolicy.canCommit('),
    'Both native workout routes must share finish review vocabulary and own a revision guard');
}
assert.ok(journal.includes('host.finishIssuedCustomWorkout(workoutId,') &&
  composer.includes('controller.finish(reviewed.revision)') &&
  !journal.includes('Commit native Finish') &&
  !composer.includes('Native session preview · no JavaScript') &&
  !composer.includes('Open a workout issued by the independent Kotlin coach.'),
  'User-facing Finish must preserve durable writers without developer-only UI copy');

// Machine A/B must be scoped to the gym that issued the workout. The
// displayed Last AND the editable one-tap "Use Last" must share the same
// machine filter, even when no machine is selected (legacy-only).
const machineSelector=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutMachineSelector.kt');
const kotlinLastOwner=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/WorkoutLastReferenceDomain.kt');
// A station choice is a data boundary, not a late effect allowed to erase
// athlete-entered weight. Both editors use the same physical-station-aware
// initializer and stable machine-keyed state; no values migrate from A to B.
assert.ok(machineSelector.includes('internal fun initialMachineScopedLoad(') &&
  machineSelector.includes('saved.machineInstanceId==selectedMachineId') &&
  machineSelector.includes('if(selectedMachineId!=null) ""') &&
  composer.includes('initialMachineScopedLoad(target.load,existing,machineInstanceId)') &&
  journal.includes('initialMachineScopedLoad(target,saved?.performed,machineInstanceId)') &&
  composer.includes('target.setKey,set.currentReceipt?.appliedRevision,machineInstanceId') &&
  journal.includes('set.setKey,part.name,stageOrdinal,existing?.appliedRevision,machineInstanceId') &&
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
  machineSelector.includes('WorkoutLastReferenceDomain.physicalStation(previous,selectedMachineId)') &&
  kotlinLastOwner.includes('selectedMachineId==null && history.machineSets.isEmpty()') &&
  kotlinLastOwner.includes('history.machineSets.filter') &&
  !machineSelector.includes('previous.sets.filterValues'),
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
  generatedCoachHost.includes('val hasPhysicalStations=roster.configurations.any{') &&
  generatedCoachHost.includes('it.gymId==issuedGym && it.exerciseId==selected.exerciseId') &&
  generatedCoachHost.includes('it.machines.isNotEmpty()') &&
  generatedCoachHost.includes('workoutMachineChoices.read(signed.ownerKey,') &&
  generatedCoachHost.includes('as? NativeWorkoutMachineChoiceResult.Saved') &&
  generatedCoachHost.includes('KotlinMachineLiveCoachingDomain.confirmedForNext(') &&
  generatedCoachHost.includes('as? NativeMachineRosterResult.Saved)?.snapshot ?:return@withOwnerTransaction null') &&
  generatedCoachHost.includes('KotlinJournalLiveSetCoachDomain.review('),
  'Generated coaching must verify durable physical station and exact confirmed machine stack');
const customMachineCoach=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomLiveSetCoachReview.kt');
assert.ok(customMachineCoach.includes('private val machineRosters:NativeKotlinMachineRosterFileStore') &&
  customMachineCoach.includes('private val workoutMachineChoices:NativeKotlinWorkoutMachineChoiceFileStore') &&
  customMachineCoach.includes('machineRosters.read(ownerKey)') &&
  customMachineCoach.includes('as? NativeMachineRosterResult.Saved)?.snapshot ?:return@withOwnerTransaction null') &&
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
  starterVariantRetention.includes('V6_POLICY_VERSION,V7_POLICY_VERSION,V8_POLICY_VERSION,V9_POLICY_VERSION,V10_POLICY_VERSION,V11_POLICY_VERSION,POLICY_VERSION') &&
  starterVariantRetention.includes('V7_POLICY_VERSION->"v7"') &&
  starterVariantRetention.includes('V8_POLICY_VERSION->"v8"') &&
  starterVariantRetention.includes('V9_POLICY_VERSION->"v9"') &&
  starterVariantRetention.includes('V10_POLICY_VERSION->"v10"') &&
  starterVariantRetention.includes('V11_POLICY_VERSION->"v11"') &&
  starterVariantRetention.includes('else->"v12"'),
  'V6/V7 replay and V8 strength/peak policies must retain calibrated related incline presses');
const machineLast=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/WorkoutLastReferenceDomain.kt');
assert.ok(machineLast.includes('data class WorkoutMachineReferenceKey(') &&
  machineLast.includes('val machineSets:Map<WorkoutMachineReferenceKey,WorkoutLastSetReference>') &&
  machineLast.includes('fun customBase(') &&
  machineLast.includes('fun composerBase(') &&
  machineLast.includes('fun appendMachineFacts(') &&
  machineLast.includes('destination.keys.removeAll { it.key==key }') &&
  machineLast.includes('history.machineSets.filter') &&
  machineSelector.includes('WorkoutLastReferenceDomain.physicalStation(previous,selectedMachineId)'),
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
// Compact default avoids burying the next editable set below coach inputs.
// The safety shortcut remains one tap even while details are collapsed;
// changing the summary cannot mutate actual sets or planned weights.
for(const phrase of [
  'compactByDefault:Boolean=false',
  'var detailsExpanded by rememberSaveable(compactByDefault)',
  'mutableStateOf(!compactByDefault)',
  'Text("Coaching context"',
  'Text("Injury / discomfort")',
  'if(!compactByDefault || detailsExpanded)',
  'onClick=onReportConcern',
  'modifier=Modifier.heightIn(min=48.dp)'
])assert.ok(sharedFocus.includes(phrase),'Compact coach context lost its controls: '+phrase);
assert.ok(composer.includes('compactByDefault=true') &&
  journal.includes('compactByDefault=true'),
  'Both Kotlin workout types must prioritize visible set logging');
const referenceLayout=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutReference.kt');
for(const phrase of [
  'internal fun PursuitWorkoutReferencePicker(',
  'BoxWithConstraints(Modifier.fillMaxWidth())',
  'val stacked=maxWidth<360.dp || LocalDensity.current.fontScale>=1.30f',
  'choices(Modifier.fillMaxWidth())',
  'WorkoutReferenceMode.entries.forEachIndexed',
  'selected=mode==choice',
  'onClick={onChange(choice)}'
])assert.ok(referenceLayout.includes(phrase),'Target/Last cannot truncate at 200% font: '+phrase);
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
  sharedFocus.includes('Text("Injury or discomfort · review options")') &&
  sharedFocus.includes('You still choose Skip or Swap') &&
  sharedFocus.includes('modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)'),
  'The shared injury entry needs a large accessible action and truthful, nonautomatic semantics');
// The safety guide now requires a separate athlete choice: open a note
// editor with explicit Save, or open a second independently verified skip
// dialog. Selection alone MUST NOT record skipped sets or claim an injury.
for(const [source,label] of [[journal,'custom'],[composer,'generated']]) {
  const start=source.indexOf('onReportConcern=');
  assert.ok(start>=0,label+' concern action is missing');
  const handler=source.slice(start,start+1100);
  for(const phrase of [
    'movementRestricted=true',
    'readiness=CoachReadiness.LOW',
    'pendingConcern=NativeMovementConcernRequest('
  ])assert.ok(handler.includes(phrase),
    label+' concern must restrict coaching without mutating targets: '+phrase);
  assert.ok(!handler.includes('showSessionNote=true'),
    label+' must offer explicit note/skip choice instead of auto-opening notes');
  assert.ok(source.includes('onWriteNote={') &&
    source.includes('showSessionNote=true') &&
    source.includes('onReviewSkip={') &&
    source.includes('pendingExerciseSkip=selectedRequest'),
    label+' concern must hand off to the existing note and confirmed skip owners');
}
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
  journal.includes('AdvancedAmplifierProtocolDomain.stageLabel(') &&
  journal.includes('it,activeFocus.stage)') &&
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
assert.ok(composer.includes('val draftBaseline=NativePendingWorkoutDraft(') &&
  composer.includes('target.reps.minimum.toString()') &&
  composer.includes('existing?.actualRir?.toString().orEmpty()') &&
  composer.includes('draftIdentity,set.isResolved,set.currentReceipt?.appliedRevision?.toString()') &&
  composer.includes('Planned reps and load are editable starting points'),
  'Composer must prefill editable reps, preserve unknown RIR, and refresh after a saved receipt');
const sharedSetFields=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeComponents.kt');
assert.ok(journal.includes('NativeSetValueFields(') &&
  journal.includes('showLoad=verified!=null') &&
  journal.includes('onLoad={draftEditor.update(load=it)}') &&
  journal.includes('onReps={draftEditor.update(reps=it)}') &&
  journal.includes('onRir={draftEditor.update(rir=it)}') &&
  journal.includes('loadError=actualLoad.isNotBlank() && invalid in setOf(') &&
  journal.includes('rirError=invalid==KotlinSetEntryRefusal.INVALID_RIR') &&
  journal.includes('val actual=(entry as? KotlinSetEntryResult.Accepted)?.performed') &&
  journal.includes('pendingExceptionalOutcome=KotlinJournalSetOutcome.FAILED_ATTEMPT'),
  'Custom workout must share bodyweight-aware stepper fields while retaining explicit complete, skip, failed and corrected history outcomes');
assert.ok(composer.includes('NativeSetValueFields(') &&
  composer.includes('showLoad=verifiedLoad!=null') &&
  composer.includes('onLoad={draftEditor.update(load=it)}') &&
  composer.includes('onReps={draftEditor.update(reps=it)}') &&
  composer.includes('onRir={draftEditor.update(rir=it)}') &&
  sharedSetFields.includes('showLoad:Boolean=true') &&
  sharedSetFields.includes('if(showLoad)OutlinedTextField(') &&
  sharedSetFields.includes('NativeSetCounterField(') &&
  sharedSetFields.includes('label="Reps"') &&
  sharedSetFields.includes('label="RIR"') &&
  sharedSetFields.includes('showFieldHelp:Boolean=true') &&
  sharedSetFields.includes('Bodyweight movement · no external load.') &&
  sharedSetFields.includes('RIR is optional; leave it blank if unreported.') &&
  sharedSetFields.includes('KotlinSetDraftStepDomain.stepRepsBy(') &&
  sharedSetFields.includes('KotlinSetDraftStepDomain.stepRirBy(') &&
  composer.includes('showFieldHelp=false') &&
  journal.includes('showFieldHelp=false'),
  'Generated workout must use shared accessible reps/RIR steppers and omit the load field for source-verified bodyweight sets');
const infoSheet=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutExerciseInfoSheet.kt')+
  read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutInfoPresentation.kt');
const infoPolicy=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeWorkoutInfoActionPolicy.kt');
// Native details show every planned amplifier stage and never render an
// unprescribed warmup RIR as a numbered effort target.
assert.match(infoSheet,/val rirLabel=if\(set\.targetRir=="Not set"\)"" else/);
assert.match(infoSheet,/AdvancedAmplifierProtocolDomain\.dropStageLoad\(/);
assert.match(infoSheet,/AdvancedAmplifierProtocolDomain\.stageNoun\(/);
const betaBuild=read('android-native/app/build.gradle.kts');
assert.ok(betaBuild.includes('"**/NativeWorkoutInfoActionPolicy.kt"') &&
  infoPolicy.includes('mayRequestSkipRemaining=active && hasRemainingSets') &&
  infoPolicy.includes('!hasRecordedWork && !linkedSuperset') &&
  composer.includes('NativeWorkoutInfoActionPolicy.review(') &&
  journal.includes('NativeWorkoutInfoActionPolicy.review('),
  'Both native workout routes must compile the same source-gated skip/undo presentation policy');
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
  composer.includes('infoExerciseId=set.target.exerciseId') &&
  composer.includes('journal?.snapshot?.targets.orEmpty().filter') &&
  composer.includes('sets=composerWorkoutInfoSets(targets)') &&
  infoSheet.includes('internal fun composerWorkoutInfoSets(') &&
  infoSheet.includes('internal fun customWorkoutInfoSets(') &&
  infoSheet.includes('infoTitle(target.type,target.ordinal+1)') &&
  infoSheet.includes('infoTitle(target.type,position)') &&
  infoSheet.includes('targetRir?.toString() ?: "Not set"') &&
  infoSheet.includes('NativeEditSheet(busy=busy,onDismiss=onDismiss)') &&
  infoSheet.includes('sets.count{it.type==DraftSetType.WORKING}') &&
  infoSheet.includes('onChooseMachine?.let') &&
  infoSheet.includes('onSwapExercise?.let') &&
  infoSheet.includes('onSkipRemainingExercise?.let') &&
  infoSheet.includes('onUndoWorkoutSwap?.let') &&
  infoSheet.includes('Text("Skip remaining exercise sets…")') &&
  infoSheet.includes('Text("Undo workout-only exercise swap")') &&
  composer.includes('onChooseMachine=if(!infoWarmupsOnly && infoOptions.canChangeStation)') &&
  journal.includes('onSwapExercise=if(!infoWarmupsOnly && infoOptions.canReplaceExercise)') &&
  infoSheet.includes('Text("Open full exercise history")') &&
  !infoSheet.includes('controller.record('),
  'Info must be a read-only, source-backed sheet with exact planned set facts and a full History action for both workout types');
assert.ok(!journal.includes('Text("Swap exercise")') &&
  infoSheet.includes('Text("Swap to another exercise")') &&
  journal.includes('onSwapExercise=if(!infoWarmupsOnly && infoOptions.canReplaceExercise)') &&
  journal.includes('onInfo=upcoming?.first?.slotId?.let{slotId->') &&
  journal.includes('reviewSwap(slot)') &&
  journal.includes('onUndoWorkoutSwap=if(!infoWarmupsOnly &&') &&
  journal.includes('undoSwapConfirmEventId=swap.eventId') &&
  journal.includes('onClick={confirmUndoSwap(swapEventId)}') &&
  !journal.includes('Text("Undo swap")') &&
  !composer.includes('Text("Exercise options")') &&
  composer.includes('pendingExerciseSkip=NativeExerciseSkipRequest(') &&
  composer.includes('undoConfirm=event.eventId'),
  'Custom workout swap belongs in Exercise Info for Focus and All Sets; duplicate card button must stay removed and swap undo must remain available');
const customPlate=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomPlateSheet.kt');
const verifiedPlateDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinVerifiedPlateLayoutDomain.kt');
assert.ok(journal.includes('NativeKotlinCustomPlateSheet(host,request,') &&
  journal.includes('current.originalSnapshot.stamp') &&
  journal.includes('onShowPlates={load,unit,basis->') &&
  journal.includes('verified!=null && KotlinVerifiedPlateLayoutDomain.supportsBasis(verified.basis)') &&
  journal.includes('onClick={onShowPlates(actualLoad,verified.unit,verified.basis)}') &&
  customPlate.includes('host.readCustomAuthoringSource()') &&
  customPlate.includes('source.revision!=stamp.gymCatalogSourceRevision') &&
  customPlate.includes('source.athleteId!=stamp.athleteId') &&
  customPlate.includes('reviewed.gym.gymId!=stamp.gymId') &&
  customPlate.includes('CustomExerciseAuthoringDomain.picker(') &&
  customPlate.includes('stock=option.availability.stock') &&
  customPlate.includes('as? VerifiedLoadStock.PairedBar') &&
  customPlate.includes('KotlinVerifiedPlateLayoutDomain.inspect(') &&
  customPlate.includes('NativeEditSheet(busy=false,onDismiss=onDismiss)') &&
  verifiedPlateDomain.includes('EquipmentLoadDomain.inspect(bar,entered,unit)') &&
  verifiedPlateDomain.includes('private const val MAX_STATES=100_000') &&
  !customPlate.includes('controller.record('),
  'Plate viewer may only display exact current owner-matched custom physical bar/Smith stock and must not log or change workout loads');
const workoutHost=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
assert.ok(composer.includes('NativeKotlinComposerPlateSheet(host,workoutId,request,') &&
  composer.includes('onShowPlates={load,unit->') &&
  composer.includes('verifiedLoad!=null && KotlinVerifiedPlateLayoutDomain.supportsBasis(verifiedLoad.basis)') &&
  composer.includes('onClick={onShowPlates(weight,verifiedLoad.unit)}') &&
  customPlate.includes('internal fun NativeKotlinComposerPlateSheet(') &&
  customPlate.includes('host.reviewComposerPlateLayout(') &&
  customPlate.includes('private fun NativeKotlinPlateResultSheet(') &&
  workoutHost.includes('fun reviewComposerPlateLayout(') &&
  workoutHost.includes('owner.ownerKey,owner.athleteId,workoutId') &&
  workoutHost.includes('approval.approvedSource!=journal.originalSnapshot.source') &&
  workoutHost.includes('sourceExercise.equipment as?') &&
  workoutHost.includes('stock=equipment.inventory as?') &&
  workoutHost.includes('VerifiedLoadStock.PairedBar') &&
  workoutHost.includes('KotlinVerifiedPlateLayoutDomain.inspect(stock,entered,unit)'),
  'Generated workout plates must use source-coherent owner approval, exact physical bar/Smith inventory and the same read-only sheet');
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
  assert.ok(source.includes('if(running && display.keepScreenAwake)view.keepScreenOn=true') &&
    source.includes('DisposableEffect(view,running,display.keepScreenAwake)') &&
    source.includes('onDispose{view.keepScreenOn=previous}'),
    'Each active Kotlin journal respects the display setting and restores the prior view policy');
}
const displayStore=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutDisplayStore.kt');
const displaySettings=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCoachSettingsScreen.kt');
const application=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitIronApplication.kt');
assert.ok(application.includes('internal val workoutDisplay by lazy'));
assert.ok(displayStore.includes('preferences.registerOnSharedPreferenceChangeListener(listener)'));
assert.doesNotMatch(displayStore,/LaunchedEffect|NativeKotlinCandidateHost|recordSet|prepareWorkout/,
  'a display preference has no lifecycle-triggered write or training authority');
assert.ok(!referenceLayout.includes('preferences.edit()'), 'mounting Last must not write back a stale reference choice');
assert.ok(displaySettings.includes('PursuitFormSection("Workout display"') &&
  displaySettings.includes('displayPreferences::awake') &&
  displaySettings.includes('PursuitWorkoutReferencePicker(display.referenceMode,displayPreferences::reference)'));
for(const source of [journal,composer]) {
  assert.ok(source.includes('val displayPreferences=rememberNativeWorkoutDisplayStore()') &&
    source.includes('val referenceMode=display.referenceMode') &&
    source.includes('displayPreferences::reference') &&
    source.includes('display.startLayout.startsInFocus(LocalConfiguration.current.screenWidthDp)') &&
    source.includes('displayPreferences.layout('),
    'both workout routes share the process-owned display choices and preserve per-workout editors');
}
assert.ok(restBridge.includes('if(!rest.hasRemainingWork)return') &&
  restBridge.includes('NativeRestTimer(workoutId,rest.manualRestSeconds,enabled,') &&
  restBridge.includes('latestReceiptOperationId=rest.latestReceiptEventId') &&
  restBridge.includes('NativeKotlinRestCue(it.eventId,it.seconds,it.exerciseIndex)'),
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
  journey.includes('currentUnfinishedGeneratedWorkoutId(host)==') &&
  journey.includes('if(verifiedUnfinished)') &&
  journey.includes('TextButton(enabled=!sessionState.busy,') &&
  journey.includes('onClick=::minimizeGeneratedWorkout') &&
  journey.includes('BackHandler(enabled=customWorkoutId==null && selectedId!=null)') &&
  journey.includes('if(!controller.state.value.busy)') &&
  journey.includes('controller.state.value.presentation?.finished!=true') &&
  journey.includes('Text("Minimize")') &&
  journey.includes('minimizedGeneratedId=null'),
  'Both workout paths must preserve exact identity, timers and drafts, and generated workouts need an explicit resume shortcut');
assert.ok(timer.includes('var observedEvent by remember(workoutId,ownerId) { mutableStateOf(latestReceiptOperationId) }') &&
  timer.includes('observedEvent=latestReceiptOperationId') &&
  timer.includes('if(confirmedCue.seconds==0)'),
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
assert.match(composer,/rir=last\.actualRir\?\.toString\(\)\.orEmpty\(\)/);
assert.match(journal,/if\(saved==null && part==CustomWorkoutVisitPart\.MAIN\)/);
assert.doesNotMatch(journal,/if\(saved==null\) PursuitUseLastCompletedSet\(/);
assert.match(journal,/part==CustomWorkoutVisitPart\.FOLLOW_UP\)/);
assert.match(journal,/rir=last\.actualRir\?\.toString\(\)\.orEmpty\(\)/);
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
const betaFormat=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinBetaFeedback.kt');
assert.ok(betaSettings.includes('Share beta feedback') &&
  betaSettings.includes('Report a problem') &&
  betaSettings.includes('Intent(Intent.ACTION_SEND)') &&
  betaSettings.includes('context.startActivity(Intent.createChooser(') &&
  betaSettings.includes('feedbackDraft=it.take(2000)') &&
  betaSettings.includes('NativeKotlinBetaFeedbackFormat.canShare(') &&
  betaSettings.includes('Only what you type and the installed app version are included') &&
  betaSettings.includes('NativeEditSheet(') &&
  betaSettings.includes('problemActual=it.take(NativeKotlinBetaFeedbackFormat.MAX_FIELD_CHARS)') &&
  betaFormat.includes('it==\'\\u0000\'') &&
  betaFormat.includes('NativeBetaFeedbackKind.PROBLEM ->') &&
  !betaSettings.includes('READ_CONTACTS') &&
  !betaSettings.includes('Intent.EXTRA_EMAIL') &&
  !betaSettings.includes('Intent.EXTRA_STREAM'),
  'Early beta feedback and problem reporting must be explicit, bounded and privacy-safe');
assert.ok(betaGradle.includes('verifyEarlyBetaRuntime') &&
  betaGradle.includes('verifyKotlinOnlyRuntimeClasspath') &&
  betaGradle.includes('"debugRuntimeClasspath", "releaseRuntimeClasspath"') &&
  betaGradle.includes('forbiddenProjects') &&
  betaGradle.includes('":domain-contract", ":engine-runtime"') &&
  betaGradle.includes('forbiddenExternalModules') &&
  betaGradle.includes('forbiddenSourcePatterns') &&
  betaGradle.includes('forbidden.isEmpty()') &&
  betaGradle.includes('violations.isEmpty()') &&
  betaGradle.includes('dependsOn(verifyKotlinOnlyRuntimeClasspath)') &&
  betaGradle.includes('verification/BETA_DEVICE_ACCEPTANCE.md') &&
  betaGradle.includes('"packageRelease", "assembleRelease"') &&
  betaGradle.includes('"bundleRelease", "packageReleaseBundle"') &&
  !betaGradle.includes('it.name == "preReleaseBuild"') &&
  betaScope.includes('one Kotlin engine'),
  'Early beta release must refuse the legacy JS engine and document single-policy ownership');

const musclePolicyEditor=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCoachPolicyEditor.kt');
// Exact v1–v5 scope selection belongs to pure TrainingMuscleCoverage.forEditor.
// Do not require a duplicate Compose when/switch that would regress future versions.
for(const phrase of [
  'TrainingMuscleCoverage.forEditor(marker,saved)',
  'TrainingMuscleCoverage.versionOf(',
  'put("$phaseKey:muscleScope","v${TrainingMuscleCoverage.versionOf(TrainingMuscleCoverage.current)}")'])
  assert.ok(musclePolicyEditor.includes(phrase),'saved policy scope or explicit current-scope upgrade lost: '+phrase);
console.log('PASS native workout recording and verified Finish-to-History/Coach navigation and safe Last draft');


const priorLeveling=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
for(const required of [
  '"Leveling · review previous workout"',
  'rememberPriorWorkoutLevelingReview(',
  'previous=lastReferences,',
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
// An A/B Leveling review must receive all authenticated per-station facts.
// The display-only filtered Target/Last projection intentionally drops
// machineSets and is suitable only for the observed-value rows in the dialog.
for (const [source,kind] of [[priorLeveling,'generated'],[journal,'custom']]) {
  const start=source.indexOf('rememberPriorWorkoutLevelingReview(');
  const review=source.slice(start,start+850);
  assert.ok(start>=0 && review.includes('previous=lastReferences,') &&
    !review.includes('previous=physicalMachineLastReferences('),
    kind+' Leveling must receive complete per-machine references');
  assert.ok(source.includes('val matchingLast=physicalMachineLastReferences('),
    kind+' visible historical values still require a physical station filter');
}

const waveSixFixtures=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymFacilityChoices.kt');
assert.match(waveSixFixtures,/"45-degree-back-extension-station" to /);
for(const fragment of [
  '"decline-situp" to "Decline Sit-Up"',
  '"back-ext-45" to "45° Back Extension (unweighted)"',
  '"prone-y-raise" to "Prone Y-Raise (unweighted)"'
])assert.ok(waveSixLabelsGuard().includes(fragment),
  'Bench accessory must be named in native Kotlin exercise UI: '+fragment);
function waveSixLabelsGuard() {
  return read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseLabels.kt');
}
for(const id of ['squat-box','heel-wedge','step-platform','exercise-sliders','ab-wheel']) {
  assert.ok(waveSixFixtures.includes('"'+id+'" to '),
    'Workout accessory must be selectable in real Kotlin gym UI: '+id);
}
const waveSixLabels=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseLabels.kt');
for(const id of ['smith-hip-thrust','pinwheel-curl','pjr-pullover','single-cable-raise','cable-crossover']) {
  assert.ok(waveSixLabels.includes('"'+id+'" to '),
    'New reviewed physical equipment variants require proper athlete-facing names: '+id);
}


// Generated first-program advanced techniques are a strictly opt-in,
// revalidated native draft transformation. The old straight-set output and
// all v1 first-program seed bytes remain unchanged when disabled.
const myoFirst=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CoachFirstProgramMyoFinisherDomain.kt');
const firstConversion=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CoachFirstProgramDraftConversionDomain.kt');
const firstUi=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinFirstProgramCandidateScreen.kt');
const firstHost=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
const firstSeed=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinFirstProgramSeedFileStore.kt');
assert.match(myoFirst,/experience==AthleteExperience\.BEGINNER/);
assert.match(myoFirst,/movement\.pattern!=ComposerPattern\.ISOLATION/);
assert.match(myoFirst,/set\.targetRir/);
assert.match(myoFirst,/KotlinSetNumberAuthorityDomain\.sameIssuedTarget\(/);
assert.match(firstConversion,/includeMyoFinisher:Boolean=false/);
assert.match(firstConversion,/finisher\.setKey==set\.stableSetKey/);
assert.match(firstConversion,/CustomCycleCoachAuditDomain\.review\(next,setup,curatedCatalog,inputs\)/);
assert.match(firstUi,/includeMyoFinisher:Boolean=false/);
assert.match(firstUi,/fun chooseMyoFinisher\(enabled:Boolean\)/);
assert.match(firstUi,/Optional myo-reps finisher/);
assert.match(firstUi,/if\(review\.prescription==null\) scheduled\.sessions\.forEach/);
// An opted-in generated technique must be visible at its actual reviewed
// final working set, not only in an unrelated exercise-wide opt-in section.
// No marker for an unreviewed cycle or for any other exercise/day.
const myoSetStart=firstUi.indexOf('review.prescription?.sessions?.forEach {session ->');
const myoSetMarker=firstUi.indexOf('Final set ${working.size} of ${working.size} · Myo-reps: up to 4 × 4 reps');
const myoOptInPanel=firstUi.indexOf('if(myoChoice!=null && state.savedProgramId==null)');
assert.ok(myoSetStart>=0 && myoSetMarker>myoSetStart &&
  myoSetMarker<myoOptInPanel,'Final-set technique must be inside prescribed set review');
for(const clause of [
  'state.includeMyoFinisher && state.canSaveDraft',
  'myoChoice?.dayIndex==session.dayIndex',
  'myoChoice?.exerciseId==exercise.exerciseId',
  'myoChoice?.exerciseIndex==exerciseIndex',
  'myoChoice?.setIndex==exercise.sets.indexOfLast',
  'myoChoice?.setKey==working.lastOrNull()?.stableSetKey',
  'Log each mini-set separately.'
])assert.ok(firstUi.slice(myoSetStart,myoSetMarker+600).includes(clause),
  'Myo final-set marker must be exact, opt-in and source reviewed: '+clause);

assert.match(firstUi,/includeMyoFinisher=before\.includeMyoFinisher/);
assert.match(firstHost,/includeMyoFinisher=seed\.includeMyoFinisher/);
assert.match(firstHost,/includeMyoFinisher=includeMyoFinisher/);
assert.match(firstSeed,/if\(seed\.includeMyoFinisher\)version else originalVersion/);
assert.match(firstSeed,/if\(storedVersion>=2\)data\.readBoolean\(\) else false/);
assert.doesNotMatch(firstUi,/includeMyoFinisher:Boolean=true/);


// Restore auditing must inventory *physical* first-program provenance,
// including legacy v1, opted-in v2 and legitimate unfinished-create orphans.
// No malformed/foreign/renamed/partial seed may disappear just because it
// is absent from the active draft list.
const stagedFirstProgram=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinFirstProgramSeedFileStore.kt');
const stagedCoreAudit=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinStagedCoreStoreAudit.kt');
assert.match(stagedFirstProgram,/sealed interface NativeFirstProgramSeedInventory/);
assert.match(stagedFirstProgram,/fun inventoryOwner\(ownerKey:String\)/);
assert.match(stagedFirstProgram,/Files\.list\(directory\)/);
assert.match(stagedFirstProgram,/decode\(item,ownerKey,current\.athleteId,null\)/);
assert.match(stagedFirstProgram,/path\(ownerKey,seed\.cycleId\)!=item/);
assert.match(stagedFirstProgram,/!name\.endsWith\("\.bin"\)/);
assert.match(stagedCoreAudit,/FIRST_PROGRAM_SEEDS_UNVERIFIED/);
assert.match(stagedCoreAudit,/firstProgramSeeds\.inventoryOwner\(/);
assert.match(stagedCoreAudit,/seedInventory\.seeds\.size/);
assert.doesNotMatch(stagedCoreAudit,/firstProgramSeeds\.create\(/);


// The custom future workout can now move an authenticated assisted-machine
// COUNTERWEIGHT DOWN after two comparable workouts. This is a separate
// signed and durable load approval, NOT a future-cycle upward-load overwrite.
const assistedCoach=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomNextWorkoutCoachingDomain.kt');
const assistedAmendment=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomNextLoadAmendmentDomain.kt');
const assistedStore=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomNextLoadApprovalFileStore.kt');
const assistedFutureUi=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinFutureWeekCoachScreen.kt');
const assistedPrepared=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomWorkoutPreparedStore.kt');
assert.match(assistedCoach,/CustomNextWorkoutCoachingStatus.READY_FOR_ASSISTANCE_REVIEW/);
assert.match(assistedCoach,/target.machineInstanceId==request.requestedMachineInstanceId/);
assert.match(assistedCoach,/AssistedCounterweightProgressionDomain.review\(/);
const futureLoadNumbers=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinFutureLoadNumberAuthorityDomain.kt');
assert.match(assistedAmendment,/KotlinFutureLoadNumberAuthorityDomain\.review\(/);
assert.match(assistedAmendment,/KotlinFutureLoadSource\.EARNED_ASSISTANCE_DECREMENT/);
assert.match(futureLoadNumbers,/candidateLoad>=issuedLoad/);
assert.match(futureLoadNumbers,/KotlinFutureLoadSource\.EARNED_NEXT_INCREMENT/);
assert.match(futureLoadNumbers,/stock\.basis!=basis/);
assert.match(futureLoadNumbers,/!current\.exact/);
assert.match(futureLoadNumbers,/!candidate\.exact/);
assert.match(assistedAmendment,/warmup->/);
assert.match(assistedStore,/r.basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT/);
assert.match(assistedStore,/r.originalLoad.subtract\(r.proposedLoad\)/);
assert.match(assistedStore,/val now=derive\(/);
assert.match(assistedPrepared,/loadApprovals\?\.readCurrent\(/);
assert.match(assistedPrepared,/load\?\.amendment\?\.amended/);
assert.match(assistedFutureUi,/Maximum counterweight reduction · gym units/);
assert.match(assistedFutureUi,/if\(!isAssistedCounterweight\)/);
assert.match(assistedFutureUi,/host.previewNextActivatedLoadAmendment\(/);
assert.match(assistedFutureUi,/host.approveNextActivatedLoadAmendment\(/);
assert.match(assistedFutureUi,/host.prepareActivatedFutureWorkout\(/);
assert.match(assistedFutureUi,/it.loadApprovalFingerprint==expected.fingerprint/);
assert.match(assistedFutureUi,/issueActivatedFutureWorkout\(/);
assert.match(assistedFutureUi,/Keep current assistance/);

// Assisted machine History and Progress must never rank a MORE helpful
// counterweight as an improved strength best. A 0 counterweight can be a
// real verified machine setting; the same source must isolate each gym/A/B.
const helpedTrend=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeExercisePerformanceTrendDomain.kt');
const helpedFacts=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinMixedHistoryFactsDomain.kt');
const helpedPlot=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExercisePerformanceTrend.kt');
const helpedPanel=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinMachineRecordsPanel.kt');
const helpedProfile=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinStrengthStandardsScreen.kt');
assert.match(helpedTrend,/val assistedCounterweight:Boolean get\(\)=basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT/);
assert.match(helpedTrend,/LoadBasis.ASSISTANCE_COUNTERWEIGHT\)/);
assert.match(helpedTrend,/key.assistedCounterweight ->/);
assert.match(helpedTrend,/checkNotNull\(weight\)<checkNotNull\(previous.actualLoad\)/);
assert.match(helpedFacts,/val lowestAssistanceByMachine:/);
assert.match(helpedFacts,/if\(assisted\) \{/);
assert.match(helpedFacts,/lowestAssistanceByMachine\[station\]=record/);
assert.match(helpedFacts,/Collections.unmodifiableMap\(lowestAssistanceByMachine\)/);
assert.match(helpedPlot,/Lowest completed machine counterweight/);
assert.match(helpedPlot,/Machine assistance \(less = harder\)/);
assert.match(helpedPanel,/assistanceByMachine:Map<NativeMixedMachineLoadKey,NativeMixedCompletedLoadFact>/);
assert.match(helpedPanel,/if\(assisted\)"Least assistance" else "Heaviest logged"/);
assert.match(helpedProfile,/assistanceByMachine=verifiedFacts.lowestAssistanceByMachine/);


// The native Coach editor must NOT silently expand a saved 22-muscle v4
// signed policy when the latest reviewed catalog adds v5 neck functions.
// All future/unknown markers must fail closed instead of falling back.
const coachScopeUi=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCoachPolicyEditor.kt');
const coachScopeDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/MuscleVolumeAccountingDomain.kt');
assert.match(coachScopeUi,/TrainingMuscleCoverage.forEditor\(marker,saved\)/);
assert.match(coachScopeUi,/Add current muscle targets/);
assert.match(coachScopeUi,/neck flexion, extension and lateral flexion/);
assert.match(coachScopeUi,/selectedBounds\s*\?\.map\{it.muscle\}/);
assert.match(coachScopeUi,/muscleScope\(phase\).associateWith\{muscle->/);
assert.doesNotMatch(coachScopeUi,/3 -> TrainingMuscleCoverage\.legacyV3\s*else -> TrainingMuscleCoverage\.current/);
assert.match(coachScopeDomain,/fun forEditor\(marker:String\?,savedMuscles:Collection<TrainingMuscle>\?\)/);
assert.match(coachScopeDomain,/marker!=null ->/);
assert.match(coachScopeDomain,/savedMuscles!=null && savedVersion==null/);
assert.match(coachScopeDomain,/savedVersion!=null ->savedVersion/);
assert.match(coachScopeDomain,/4 -> legacyV4/);
assert.match(coachScopeDomain,/5 -> current/);
// An invalid or future/partial saved phase scope must not be silently
// changed through an upgrade button or approved from another phase.
assert.match(coachScopeUi,/val unverifiedPhases=phaseList.filter\{muscleScope\(it\).isEmpty\(\)\}/);
assert.match(coachScopeUi,/if\(visibleMuscles.isEmpty\(\)\) \{[\s\S]*?return/);
assert.match(coachScopeUi,/Button\(enabled=!busy && unverifiedPhases.isEmpty\(\)/);
// Explicit upgrades land on the first not-yet-covered muscle, so an older
// athlete does not have to search through all existing saved rows.
assert.match(coachScopeUi,/selectedMuscle=TrainingMuscleCoverage.current.firstOrNull\{/);


// Typed non-repetition set entry is an actual Compose callback surface,
// backed by the source-specific Kotlin parser and real issued set model.
// Not proof that the parent program/History routes are wired or device-tested.
const measuredEditor=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeMeasuredExerciseSetEditor.kt');
const measuredEntry=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/MeasuredExerciseEntryDomain.kt');
assert.match(measuredEditor,/NativeMeasuredStoredSet/);
assert.match(measuredEditor,/fun NativeMeasuredExerciseSetEditor/);
assert.match(measuredEditor,/MeasuredExerciseEntryDomain\.parse\(/);
assert.match(measuredEditor,/plan\.sides\.sortedBy\{it\.ordinal\}/);
assert.match(measuredEditor,/MeasuredEntryUnit\.MINUTES_SECONDS/);
assert.match(measuredEditor,/MeasuredEntryUnit\.FEET/);
// A rack with both weights does not establish that a matched pair exists
// at a newly entered weight, in another gym, or for another session.
assert.ok(measured.includes('"$workoutId|$sourceKey|"+targetWeight.stripTrailingZeros().toPlainString()'));
assert.ok(measured.includes('pairKey != null && confirmedPairKey == pairKey'));
assert.ok(measured.includes('targetExternalLoad=it;confirmedPairKey=null'));
assert.ok(measured.includes('confirmedPairKey=if(checked) pairKey else null'));
assert.ok(measured.includes('val setRange = MeasuredSessionAuthoringDomain.setsPerExercise'));
assert.match(measuredEditor,/roundedApproved/);
assert.match(measuredEditor,/Checkbox\(/);
assert.match(measuredEditor,/val failedZero=if\(unit==MeasuredEntryUnit\.MINUTES_SECONDS\)"0:00" else "0"/);
assert.match(measuredEditor,/val failedInput=submitted\.copy\(quantityBySide=plan\.sides\.associateWith\{failedZero\}\)/);
assert.match(measuredEditor,/MeasuredExerciseEntryDomain\.parse\([\s\S]*?MeasuredSetOutcome\.FAILED_ATTEMPT,failedInput/);
assert.match(measuredEditor,/MeasuredJournalOperation\.Record\(/);
assert.match(measuredEditor,/UUID\.randomUUID\(\)/);
assert.match(measuredEditor,/journal\.revision,outcome,actual,previous\?\.eventId/);
assert.match(measuredEditor,/only a confirmed native journal receipt/i);
assert.match(measuredEditor,/if\(loaded\)actualWeight else null/);
assert.match(measuredEditor,/if\(previous!=null && !editing\)/);
assert.match(measuredEditor,/onRecord\(/);
assert.doesNotMatch(measuredEditor,/MeasuredJournalOperation\.Finish/);
assert.doesNotMatch(measuredEditor,/onRecord\(.*KotlinPerformedSet/);
assert.match(measuredEntry,/unit==MeasuredEntryUnit\.FEET && !exact/);
assert.match(measuredEntry,/rounding/);
assert.match(measuredEntry,/MeasuredExerciseDomain\.validActual\(plan,outcome,actual\)/);

// Source-issued working-set reductions may occur before the final set
// (e.g. a descending pyramid). A numeric downstep is NOT evidence of a
// back-off scheme, a fatigue decision or the athlete's logged actual.
const plannedLoadNote=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinPlannedBackoffNote.kt');
const plannedLoadNoteTest=read('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinPlannedBackoffNoteTest.kt');
assert.ok(
  plannedLoadNote.includes('it.target.exerciseId==selected.exerciseId') &&
  plannedLoadNote.includes('selected.type!=DraftSetType.WORKING') &&
  plannedLoadNote.includes('working.map{it.target.ordinal}!=(0 until working.size).toList()') &&
  plannedLoadNote.includes('working.map{it.target.setKey}.distinct().size!=working.size') &&
  plannedLoadNote.includes('working[index].target!=selected') &&
  plannedLoadNote.includes('previous.unit!=current.unit || previous.basis!=current.basis') &&
  plannedLoadNote.includes('current.value.compareTo(previous.value)==0') &&
  plannedLoadNote.includes('if(!assisted && current.value>previous.value)return null') &&
  plannedLoadNote.includes('val moreAssistance=assisted && current.value>previous.value') &&
  plannedLoadNote.includes('Issued lower-assistance working set') &&
  plannedLoadNote.includes('Issued higher-assistance working set') &&
  plannedLoadNote.includes('Issued lighter working set') &&
  plannedLoadNote.includes('not lowered by fatigue coaching') &&
  !plannedLoadNote.includes('Planned last-set back-off') &&
  generatedFocus.includes('plannedBackoffNote=NativeKotlinPlannedBackoffNote.fromIssuedTargets(') &&
  generatedFocus.includes('plannedBackoffNote?.let { note ->') &&
  plannedLoadNoteTest.includes('labelsOnlyTheAuthoredLowerFinalWorkingSet') &&
  plannedLoadNoteTest.includes('straightAndUnverifiedLoadsAreNotMisrepresentedAsReductions') &&
  plannedLoadNoteTest.includes('actualsAndStaleTargetCopiesCannotInventPrescribedLoadDifferences') &&
  plannedLoadNoteTest.includes('decreasingAssistedCounterweightMeansHarderNotLighterInGeneratedWorkout') &&
  plannedLoadNoteTest.includes('increasingAssistedCounterweightIsAPlannedEasierWorkingSet'),
  'Generated Train must explain source-issued working-set reductions without inventing a backoff or fatigue change'
);

// Custom Train uses the same signed source-only display contract. No actual
// weight or coaching proposal becomes an issued prescription; warmups
// appearing after work and missing ordinals cannot be relabeled as a scheme.
assert.ok(plannedLoadNote.includes('fun fromCustomSlot(') &&
  plannedLoadNote.includes('slotSets.any{it.slotId!=selected.slotId}') &&
  plannedLoadNote.includes('slotSets.map{it.setKey}.distinct().size!=slotSets.size') &&
  plannedLoadNote.includes('slotSets.drop(firstWorking).any{') &&
  plannedLoadNote.includes('working[index]!=selected') &&
  plannedLoadNote.includes('Authored lighter working set') &&
  !plannedLoadNote.includes('Authored lighter final set') &&
  journal.includes('plannedLoadNote=NativeKotlinPlannedBackoffNote.fromCustomSlot(') &&
  journal.includes('if(part==CustomWorkoutVisitPart.MAIN)plannedLoadNote?.let {note->') &&
  plannedLoadNoteTest.includes('customLastSetReductionIsExplicitWithoutChangingTargets') &&
  plannedLoadNoteTest.includes('customLabelRejectsStraightAndMismatchedPhysicalLoads') &&
  plannedLoadNoteTest.includes('customLabelRejectsWarmupsOmittedOrdinalsAndDuplicateReceipts'),
  'Custom Train must label only exact source-authored reductions and reject malformed or mismatched targets'
);


// A same-load technique inherits the ACTUALLY LOGGED activation set it is
// attached to; never the first prescribed work row or a prior session Last
// shortcut. The Kotlin engine owns the typed policy, while corrections get
// distinct private draft identities tied to the activation receipt revision.
const amplifierLoadDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/AdvancedAmplifierProtocolDomain.kt');
const amplifierLoadTests=read('android-native/kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/AdvancedAmplifierProtocolDomainTest.kt');
assert.ok(
  amplifierLoadDomain.includes('sealed interface AmplifierFollowUpEntryLoad') &&
  amplifierLoadDomain.includes('fun resolveFollowUpEntryLoad(') &&
  amplifierLoadDomain.includes('actual.machineInstanceId!=selectedMachineId') &&
  amplifierLoadDomain.includes('AmplifierFollowUpEntryLoad.RecordedActivation(actual)') &&
  journal.includes('AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(') &&
  journal.includes('activationPerformed=main?.performed') &&
  journal.includes('activationReceiptRevision=observed?.appliedRevision') &&
  journal.includes('if(saved==null && part==CustomWorkoutVisitPart.MAIN)') &&
  journal.includes('ActivationUnavailable -> ""') &&
  !journal.includes('sameLoadActivation') &&
  amplifierLoadTests.includes('everyMyoStagePrefillsFromLastLoggedActivationNotFirstSetOrIssuedWeight') &&
  amplifierLoadTests.includes('incompatibleActivationAndMachineNeverFallbackToEarlierSet') &&
  amplifierLoadTests.includes('activationLoadResolutionIsOneSourceAcrossSingleAndStagedProtocols'),
  'Follow-up load provenance: logged activation vs authored drop vs unverified, with no Last shortcut'
);


// Numeric trust boundary: generated journal, custom journal, and editor
// parser MUST use the same Kotlin authority for actual external load,
// precise decimal range, reps, RIR, outcomes and machine-instance syntax.
// A source-level gate supplements the real JUnit replay and history matrix.
const numberAuthority=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinSetNumberAuthorityDomain.kt');
// Actual-set and future-coaching numeric envelopes are ONE predicate,
// never separately edited ceiling/scale rules. Intent, equipment stock,
// consent and read-only issuance still belong to their distinct owners.
assert.ok(numberAuthority.includes('fun validPhysicalLoadValue(') &&
  numberAuthority.includes('if(!validPhysicalLoadValue(load))') &&
  futureLoadNumbers.includes('KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(value)') &&
  !futureLoadNumbers.includes('value.stripTrailingZeros().scale()<=3') &&
  !futureLoadNumbers.includes('BigDecimal("100000")'),
  'Kotlin actual and future weight numbers must share one physical envelope');

const numberGenerated=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinWorkoutJournalDomain.kt');
const numberCustom=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomWorkoutJournalDomain.kt');
const numberEditor=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinWorkoutEntryDomain.kt');
const numberTests=read('android-native/kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinSetNumberAuthorityDomainTest.kt');
assert.ok(
  numberAuthority.includes('object KotlinSetNumberAuthorityDomain') &&
  numberAuthority.includes('KotlinSetLoadRelation.COMPLETED_DIFFERENT_ACTUAL') &&
  numberAuthority.includes('load.compareTo(issuedLoad.value)==0') &&
  numberAuthority.includes('KotlinMachineAlternativesDomain.validInstanceId') &&
  numberAuthority.includes('performed.actualRir !in 0..10') &&
  numberGenerated.includes('KotlinSetNumberAuthorityDomain.valid(target.load,outcome,performed)') &&
  numberCustom.includes('KotlinSetNumberAuthorityDomain.valid(target,outcome,performed)') &&
  numberEditor.includes('KotlinSetNumberAuthorityDomain.valid(load,') &&
  numberTests.includes('generatedCustomAndParserAcceptExactlyTheSameActualNumberMatrix') &&
  numberTests.includes('generatedAndCustomColdHistoryContainsCorrectionsNotPhantomNumbers'),
  'Kotlin generated, custom and entry parsing must share one numeric acceptance and History contract'
);


// ONE recorded-set -> working-evidence bridge now supplies generated History,
// both live journal adapters, custom next-session rep review and custom
// future-load candidate review. No adapter can silently invent actual RIR,
// re-label skipped/failed work or promote a different performed load.
const unifiedWorkingEvidence=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinWorkingSetEvidenceDomain.kt');
const unifiedWorkingEvidenceTests=read('android-native/kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinWorkingSetEvidenceDomainTest.kt');
const unifiedLiveCoach=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinJournalLiveSetCoachDomain.kt');
const unifiedLiveCoachTests=read('android-native/kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinJournalLiveSetCoachDomainTest.kt');
const unifiedHistory=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinWorkoutHistoryEvidenceDomain.kt');
const unifiedCustomFuture=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomNextWorkoutCoachingDomain.kt');
assert.ok(
  unifiedWorkingEvidence.includes('KotlinSetNumberAuthorityDomain.review(') &&
  unifiedWorkingEvidence.includes('fun review(') &&
  unifiedWorkingEvidence.includes('KotlinSetLoadRelation') &&
  unifiedWorkingEvidence.includes('performed?.actualRir') &&
  unifiedLiveCoach.includes('KotlinWorkingSetEvidenceDomain.review(') &&
  unifiedLiveCoach.includes('LiveSetCoachUnavailable.UNVERIFIED_RECORDED_EVIDENCE') &&
  unifiedHistory.includes('KotlinWorkingSetEvidenceDomain.review(') &&
  unifiedHistory.includes('KotlinHistoryProgressionGate.UNVERIFIED_RECORDED_EVIDENCE') &&
  unifiedCustomFuture.split('KotlinWorkingSetEvidenceDomain.review(').length===3 &&
  !unifiedCustomFuture.includes('WorkingSetFact(') &&
  unifiedWorkingEvidenceTests.includes('invalidObservationsNeverCrossTheSharedFutureEvidenceBoundary') &&
  unifiedLiveCoachTests.includes('forgedInvalidNumericReceiptCannotBeUsedForLiveLoadAdvice'),
  'History, live and future coaching must derive each saved working-set number from the same authority'
);


// The basic Plan preview uses the same source-verified owner as completed
// custom journals. Disabling optional fatigue coaching must not silently
// erase basic progression, nor may an informational preview issue/save work.
const basicPreviewScreen=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleCandidateScreen.kt');
const basicPreviewPanel=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinBasicProgressionPreview.kt');
const basicPreviewController=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleController.kt');
const basicPreviewHost=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
const basicNativeOwner=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomBasicNextReview.kt');
assert.ok(
  basicPreviewScreen.includes('slot.slotId in basicSlots') &&
  basicPreviewScreen.includes('ProgramProgressionRoute.BASIC_WEIGHTED') &&
  basicPreviewScreen.includes('ProgramProgressionRoute.MANUAL') &&
  basicPreviewScreen.includes('ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS') &&
  basicPreviewScreen.includes('if (slot.slotId in loadStepSlots) maxStep else BigDecimal.ZERO') &&
  basicPreviewScreen.includes('checkNotNull(basicStep)') &&
  basicPreviewScreen.includes('Preview basic progression') &&
  basicPreviewScreen.includes('!session.prescriptionEditedForTime') &&
  basicPreviewScreen.includes('state.basicDayProgressionSlotId==slot.slotId') &&
  !basicPreviewScreen.includes('state.progressionCoachingEnabled &&\n                            slot.slotId in basicSlots') &&
  basicPreviewController.includes('host.reviewNextAuthoredBasicProgression(') &&
  basicPreviewController.includes('next.copy(declaredNonSetSeconds=shown.declaredNonSetSeconds)==shown') &&
  basicPreviewHost.includes('customBasicNextReview.reviewAuthored(') &&
  basicNativeOwner.includes('history.read(ownerKey,owner.athleteId)') &&
  basicNativeOwner.includes('CustomBasicHistoryProgressionDomain.review(') &&
  basicPreviewPanel.includes('candidate.proposedWorkingLoads.forEachIndexed') &&
  basicPreviewPanel.includes('Read-only suggestion from verified workout history.') &&
  !basicPreviewPanel.includes('onClick='),
  'Basic Plan preview must remain owner-verified, independent of optional coach assistance and non-issuable'
);

const customRepEditor = read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt');
const repReference = read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutReference.kt');
assert.ok(
  customRepEditor.includes('follower?.followUpReps else exactMainTarget ?: prescription.reps.minimum') &&
  customRepEditor.includes('set.prescription.exactRepTarget ?: set.prescription.reps.minimum') &&
  customRepEditor.split('exactRepTarget = exactMainTarget').length === 3 &&
  repReference.includes('if(exactRepTarget != null) exactRepTarget.toString()'),
  'Authored main-set reps must reach the logger, correction reference and Leveling draft; follow-ups use their own reps'
);
