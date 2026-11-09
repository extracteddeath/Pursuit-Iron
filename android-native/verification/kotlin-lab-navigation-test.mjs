import assert from 'node:assert/strict';
import fs from 'node:fs';

/**
 * Isolated Kotlin Lab launcher / navigation contract. This does not claim
 * phone accessibility or screen acceptance; it prevents an APK that opens
 * the unrelated MainActivity or makes basic screens unreachable.
 */
const read=path=>fs.readFileSync(path,'utf8');
const main=read('android-native/app/src/main/AndroidManifest.xml');
const debug=read('android-native/app/src/debug/AndroidManifest.xml');
const release=read('android-native/app/src/release/AndroidManifest.xml');
const build=read('android-native/app/build.gradle.kts');
const journey=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt');
const history=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHistoryScreen.kt');
const labActivity=read('android-native/app/src/debug/java/io/github/extracteddeath/pursuitiron/KotlinEnginePreviewActivity.kt');
const tokens=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeDesignTokens.kt');
const nativeSettings=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCoachSettingsScreen.kt');
const standards=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinStrengthStandardsScreen.kt');
const standardsOverview=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinStrengthOverview.kt');
const sharedDesign=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitDesignComponents.kt');
const targetLast=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutReference.kt');
const profile=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinProfileScreen.kt');
const shared=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitDesignComponents.kt');
const trend=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinProgressTrend.kt');
const exerciseTrendUi=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExercisePerformanceTrend.kt');
const exerciseTrendDomain=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeExercisePerformanceTrendDomain.kt');
const favorites=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeStrengthFavorites.kt');
const exerciseFavorites=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeExerciseFavorites.kt');
const plan=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleCandidateScreen.kt');
const authoringSheet=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomExerciseSheet.kt');
const swapRank=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomExerciseSwapRankingDomain.kt');
const customAuthoring=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomExerciseAuthoringDomain.kt');
const customBuilder=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomCycleBuilderDomain.kt');
const cycleController=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleController.kt');
const liveWorkout=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt');
const liveJournalDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomWorkoutJournalDomain.kt');
const liveHistoryEvidence=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomWorkoutHistoryEvidenceDomain.kt');
const liveJournalCodec=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeCustomWorkoutJournalCodec.kt');
const liveJournalStore=read('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomWorkoutJournalFileStore.kt');
const adaptiveRights=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinAdaptiveCyclePermissionsPanel.kt');
const coach=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateCoachScreen.kt');
const outline=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleOverview.kt');
const sessionBrief=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinNextSessionBrief.kt');
const blockComparison=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCycleBlockComparisonPanel.kt');
const comparisonDomain=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomCycleBlockComparisonDomain.kt');
const facilityPicker=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymFacilityChoices.kt');
const gymScreen=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymSourceCandidateScreen.kt');
const onboarding=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinOnboardingCandidateScreen.kt');

assert.match(main,/android:name="\.MainActivity"/,
  'Live MainActivity must remain registered, even when hidden from the lab launcher');
assert.doesNotMatch(main,/android.intent.category.LAUNCHER/,
  'Base manifest must not duplicate the Kotlin Lab launcher');
assert.match(release,/android:name="\.MainActivity"/);
assert.equal((release.match(/android.intent.category.LAUNCHER/g)||[]).length,1,
  'Release variant must keep the original native MainActivity launcher');
assert.match(debug,/android:name="\.KotlinEnginePreviewActivity"/);
assert.equal((debug.match(/android.intent.category.LAUNCHER/g)||[]).length,1,
  'Debug variant must have only the Kotlin Lab launcher');
assert.ok(debug.includes('android:label="${kotlinLabDisplayName}"') &&
  debug.includes('tools:replace="android:label"') &&
  build.includes('manifestPlaceholders["kotlinLabDisplayName"] = "Pursuit Iron Lab Stable"'),
  'The stable Lab launcher must retain its own default display label');
assert.ok(build.includes('applicationIdSuffix = ".kotlinlabstable"') &&
  build.includes('applicationIdSuffix = ".kotlinlabtrial$kotlinLabDisposableRun"'),
  'Stable and per-run trial must be isolated from production and other preview');
assert.ok(journey.includes('Scaffold(') && journey.includes('NavigationBar {') &&
  journey.includes('NavigationBarItem('),
  'Ordinary training screens must be reachable through a persistent native bar');
assert.ok(journey.includes('KotlinCandidateDestination.PROGRESS') &&
  journey.includes('NativeKotlinStrengthStandardsScreen(host,') &&
  journey.includes('NavigationRail {') &&
  journey.includes('candidateTabSelected(destination,page)') &&
  journey.includes('if(!activeWorkout)Surface('),
  'App must have accessible Progress and adaptive Material navigation without workout chrome');
assert.ok(targetLast.includes('SingleChoiceSegmentedButtonRow') &&
  targetLast.includes('SegmentedButton(') &&
  targetLast.includes('WorkoutReferenceMode.entries') &&
  targetLast.includes('else "Last"'),
  'Target/Last must share the compact Material segmented display-only control');
for(const phrase of ['PursuitSectionHeading','PursuitExerciseHeading',
  'PursuitEmptyState'])assert.ok(sharedDesign.includes('fun '+phrase),
    'Missing a reusable Material design component: '+phrase);
for(const phrase of ['host.reviewVerifiedKotlinHistory()', 'reviewWorkingSetFacts()',
  'LoadBasis.BARBELL_TOTAL','lift.reps','epleyEstimate(',
  'comparisonGroup','bodyweightText','Reference methodology',
  'PublishedStrengthReference("barbell_bench"',
  'PublishedStrengthReference("back_squat"',
  'PublishedStrengthReference("conventional_deadlift"',
  'PublishedStrengthReference("barbell_ohp"'])
  assert.ok(standards.includes(phrase),'Unverified strength standards design contract: '+phrase);
assert.ok(history.includes('var exerciseSearch by rememberSaveable') &&
  history.includes('visibleSessions=remember(verified,filteredMovements,exerciseSearch)') &&
  history.includes('items(visibleSessions.asReversed()') &&
  history.includes('onClick={historyExerciseId=exercise}') &&
  history.includes('Text("Search exercises")') &&
  history.includes('No matching saved exercises.'),
  'History needs a saved, correction-aware searchable exercise drilldown');
assert.ok(journey.includes('import androidx.activity.compose.BackHandler') &&
  journey.includes('BackHandler(enabled=destination==KotlinCandidateDestination.PROFILE') &&
  journey.includes('KotlinCandidateDestination.SETTINGS -> destination=settingsReturn') &&
  journey.includes('progressRequestedExerciseId=null') &&
  journey.includes('progressReturn=KotlinCandidateDestination.PROFILE') &&
  journey.includes('progressReturn=KotlinCandidateDestination.HISTORY') &&
  journey.includes('destination=progressReturn') &&
  journey.includes('progressRequestedTrendExerciseId=null') &&
  !journey.includes('BackHandler(enabled=activeWorkout'),
  'Android Back must honor Profile/Settings and pinned lift origin without interfering with Train');
assert.ok(journey.includes('KotlinCandidateDestination.PROFILE') &&
  journey.includes('NativeKotlinProfileScreen(host,themeId,') &&
  journey.includes('contentDescription="Open athlete profile"') &&
  journey.includes('profileReturn=destination') &&
  journey.includes('settingsReturn=destination') &&
  journey.includes('onBack={destination=settingsReturn}'),
  'Profile must be reachable without using a sixth bottom tab and Settings Back must preserve its origin');
assert.ok(profile.includes('host.reviewActiveAthleteSetup()') &&
  profile.includes('host.reviewVerifiedKotlinHistory()') &&
  profile.includes('KotlinHistoryPerformanceSummaryDomain.fromCustom') &&
  profile.includes('KotlinHistoryPerformanceSummaryDomain.fromComposer') &&
  profile.includes('displayName') &&
  profile.includes('prefs.edit().putString(') &&
  profile.includes('Strength Standards'),
  'Profile must be source-backed, locally customizable, and surface Strength Standards');
assert.ok(shared.includes('fun PursuitDestinationRow') &&
  shared.includes('fun PursuitScreenContent') &&
  shared.includes('min=64.dp') &&
  shared.includes('widthIn(max=920.dp)'),
  'Profile and Settings must reuse Material navigation rows and responsive screen width');
const hostProfile=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
assert.ok(hostProfile.includes('fun reviewActiveAthleteSetup()') &&
  hostProfile.includes('revisionFence.withOwnerTransaction(identity.ownerKey)') &&
  hostProfile.includes('localAthlete.signedInIdentity()'),
  'Presentation Profile must read authenticated saved source without replaying login or mutating history');

const strengthMeasure=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StrengthStandardsMeasurementDomain.kt');
assert.ok(standards.includes('LoadUnit.entries.forEachIndexed') &&
  standards.includes('StrengthStandardsMeasurementDomain.convert(') &&
  standards.includes('StrengthStandardsMeasurementDomain.relativeStrength(') &&
  !standards.includes('sharedUnit==lift.key.unit'),
  'Strength Standards must compare unit-normalized logged lifts without hiding mixed lb/kg history');
assert.ok(standards.includes('verifiedStrengthTrend(it,trendExerciseId,displayUnit)') &&
  standards.includes('StrengthStandardsMeasurementDomain.relativeStrength(') &&
  standards.includes('timeline=if(verified.first is NativeKotlinMixedFactsReadResult.Review)') &&
  trend.includes('CustomWorkoutVisitPart.MAIN') &&
  trend.includes('KotlinJournalSetOutcome.COMPLETED') &&
  trend.includes('Canvas(') &&
  trend.includes('estimatedOneRepMax'),
  'Progress trend must be drawn only from corrected complete-session strength evidence');
assert.ok(favorites.includes('private fun key(athleteId:String)') &&
  favorites.includes('preferences.getString(key(athleteId),null)') &&
  favorites.includes('editor.putString(key(athleteId),exerciseIds.joinToString(","))') &&
  favorites.includes('return editor.commit()') &&
  favorites.includes('exerciseIds.any{it !in supportedIds}') &&
  !favorites.includes('estimatedOneRepMax'),
  'Favorite lifts must be opt-in athlete-scoped UI choices, never strength outcomes');
// The general exercise-pinning flow is *not* a fifth fabricated Strength
// Standards reference: every ID is read from authenticated finished workouts.
assert.ok(exerciseFavorites.includes('NativeKotlinMixedHistoryTimeline') &&
  exerciseFavorites.includes('history.completions.flatMap') &&
  exerciseFavorites.includes('NativeKotlinMixedHistoryCompletion.Composer') &&
  exerciseFavorites.includes('NativeKotlinMixedHistoryCompletion.Custom') &&
  exerciseFavorites.includes('athlete:$athleteId:history-favorites') &&
  exerciseFavorites.includes('chosen.any{!validId.matches(it) || it !in loggedIds}') &&
  exerciseFavorites.includes('chosen.size>MAX_FAVORITES') &&
  exerciseFavorites.includes('chosen.distinct().size!=chosen.size') &&
  exerciseFavorites.includes('return editor.commit()') &&
  standards.includes('NativeExerciseFavorites.loggedIds') &&
  standards.includes('timeline?.athleteId==owner') &&
  standards.includes('timeline?.manifestRevision==version') &&
  standards.includes('NativeExerciseFavorites.write(') &&
  standards.includes('if(exercisePickerExpanded) {') &&
  standards.includes('changeExerciseFavorites(moved)') &&
  standards.includes('onClick={historyExerciseId=exerciseId}') &&
  profile.includes('NativeExerciseFavorites.read(') &&
  profile.includes('timeline?.athleteId==athleteId') &&
  profile.includes('onProgressLift(exerciseId)') &&
  journey.includes('progressRequestedExerciseId=exerciseId') &&
  standards.includes('exercise in availableMovements'),
  'General exercise favorites must be verified, ordered, athlete-scoped and history-only');

// General exercise trends are descriptive physical-load observations, not
// estimated 1RM comparisons or additional published strength standards.
assert.ok(exerciseTrendDomain.includes('timeline.completions') &&
  exerciseTrendDomain.includes('NativeKotlinMixedHistoryCompletion.Composer') &&
  exerciseTrendDomain.includes('NativeKotlinMixedHistoryCompletion.Custom') &&
  exerciseTrendDomain.includes('CustomWorkoutVisitPart.MAIN') &&
  exerciseTrendDomain.includes('DraftSetType.WORKING') &&
  exerciseTrendDomain.includes('KotlinJournalSetOutcome.COMPLETED') &&
  exerciseTrendDomain.includes('StrengthStandardsMeasurementDomain.convert(') &&
  exerciseTrendDomain.includes('issued.basis,window') &&
  exerciseTrendDomain.includes('NativeExerciseTrendKey(null,window)') &&
  exerciseTrendDomain.includes('actual.unit!=issued.unit') &&
  exerciseTrendDomain.includes('val canPrescribeWorkout:Boolean get()=false') &&
  exerciseTrendUi.includes('NativeExercisePerformanceTrendDomain.project(') &&
  exerciseTrendUi.includes('PursuitFactualLinePlot(') &&
  exerciseTrendUi.includes('Selected')===false &&
  exerciseTrendUi.includes('Show recorded points') &&
  exerciseTrendUi.includes('performedReps') &&
  standards.includes('performanceExerciseId=if(') &&
  standards.includes('NativeKotlinExercisePerformanceTrend(') &&
  standards.includes('it in availableMovements') &&
  trend.includes('fun PursuitFactualLinePlot('),
  'General exercise trends must be observed, equipment-safe, accessible and source-verified');

assert.ok(history.includes('onOpenExerciseTrend:(String)->Unit={}') &&
  history.includes('onClick={onOpenExerciseTrend(exercise)}') &&
  history.includes('Text("Trend: ${kotlinExerciseLabel(exercise)}")') &&
  journey.includes('onOpenExerciseTrend={exerciseId->') &&
  journey.includes('progressRequestedTrendExerciseId=exerciseId') &&
  journey.includes('preferredTrendExerciseId=progressRequestedTrendExerciseId') &&
  standards.includes('preferredTrendExerciseId:String?=null') &&
  standards.includes('exercise in availableMovements') &&
  standards.includes('performanceExerciseId=exercise'),
  'History search trends require verified movement routing and an origin-preserving Back action');

// In-session swap cannot be superficial: issued workout source, physical
// loading, journal event replay and saved History must agree across cold start.
assert.ok(
  liveWorkout.includes('fun reviewSwap(slot:CustomWorkoutSlotSnapshot)') &&
  liveWorkout.includes('host.readCustomAuthoringSource()') &&
  liveWorkout.includes('source.revision==stamp.gymCatalogSourceRevision') &&
  liveWorkout.includes('CustomExerciseAuthoringDomain.picker(') &&
  liveWorkout.includes('Text("Swap exercise")') &&
  liveWorkout.includes('hasRecordedWork') &&
  liveWorkout.includes('fun confirmSwap(') &&
  liveWorkout.includes('host.swapIssuedCustomExercise(') &&
  liveWorkout.includes('host.undoIssuedCustomExerciseSwap(') &&
  liveWorkout.includes('Text("Undo swap")') &&
  liveWorkout.includes('Undo workout swap?') &&
  liveWorkout.includes('This workout only · no program change') &&
  liveWorkout.includes('workoutOnlySwap=true') &&
  liveWorkout.includes('otherDayExerciseIds=snap.slots.filter{') &&
  liveWorkout.includes('set.prescription.type==DraftSetType.WORKING') &&
  liveJournalDomain.includes('data class Swap(') &&
  liveJournalDomain.includes('data class UndoSwap(') &&
  liveJournalDomain.includes('CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE') &&
  liveJournalDomain.includes('val discardedKeys=replacement.sets.map') &&
  liveJournalDomain.includes('val originalSnapshot:CustomWorkoutDaySnapshot=snapshot') &&
  liveJournalDomain.includes('CustomWorkoutJournalRefusal.SWAP_ALREADY_STARTED') &&
  liveJournalDomain.includes('CustomWorkoutJournalRefusal.SWAP_PROTECTED') &&
  liveJournalDomain.includes('prior.receipts.any') &&
  liveJournalCodec.includes('private const val VERSION=2') &&
  liveJournalCodec.includes('is CustomWorkoutJournalOperation.UndoSwap->') &&
  liveJournalCodec.includes('writeByte(4)') &&
  liveJournalCodec.includes('4->CustomWorkoutJournalOperation.UndoSwap(') &&
  liveJournalCodec.includes('version !in 1..VERSION') &&
  liveJournalCodec.includes('state.originalSnapshot') &&
  liveJournalCodec.includes('replay(state.originalSnapshot,state.workoutId,') &&
  liveJournalCodec.includes('stored workout swap state differs from events') &&
  liveJournalStore.includes('actual.snapshot!=loaded.journal.originalSnapshot') &&
  liveJournalStore.includes('fun swap(') &&
  liveJournalStore.includes('fun undoSwap(') &&
  liveJournalStore.includes('CustomWorkoutJournalOperation.UndoSwap(') &&
  liveJournalStore.includes('fence.withOwnerTransaction(ownerKey)') &&
  liveJournalStore.includes('CustomExerciseAuthoringDomain.reviewFreshSets(') &&
  liveJournalStore.includes('atomic(path(ownerKey,workoutId),encode(updated))') &&
  liveHistoryEvidence.includes('journal.workoutId,journal.originalSnapshot,') &&
  read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt').includes('fun swapIssuedCustomExercise(') &&
  read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt').includes('fun undoIssuedCustomExerciseSwap('),
  'A live swap must be an authenticated source-verified, cold-replayable workout-only journal event with unchanged History ownership');

// Web changelog 2523/2677/2248: ranked search and verified equipment are
// visible in both program-draft and separately source-fenced live-workout swaps.
assert.ok(
  customAuthoring.includes('val pattern: ComposerPattern? = null') &&
  customAuthoring.includes('val muscleCredits: List<DeclaredMuscleCredit> = emptyList()') &&
  customAuthoring.includes('gym.confirmedStock[move.equipmentId]') &&
  customAuthoring.includes('move.credits.toList()') &&
  swapRank.includes('fun rank(') &&
  swapRank.includes('samePatternOnly:Boolean=false') &&
  swapRank.includes('sharedMusclesOnly:Boolean=false') &&
  swapRank.includes('sameRoleOnly:Boolean=false') &&
  swapRank.includes('sameFamilyOnly:Boolean=false') &&
  swapRank.includes('matchesName(it.attachmentId,needle)') &&
  swapRank.includes("name.replace('_',' ').replace('-',' ')") &&
  swapRank.includes('thenByDescending {it.sameRole}') &&
  swapRank.includes('Same reviewed training role') &&
  swapRank.includes('otherDayExerciseIds:List<String>?=null') &&
  swapRank.includes('sourceHasWorkingSets:Boolean=false') &&
  swapRank.includes('Could cover sole direct-day muscles with working sets') &&
  swapRank.includes('Not directly covered by this candidate') &&
  swapRank.includes('val dayCoverageKnown=sourceHasWorkingSets && source!=null') &&
  swapRank.includes('CustomSwapFit.SAME_PATTERN_AND_FAMILY') &&
  swapRank.includes('CustomSwapFit.SHARED_REVIEWED_MUSCLES') &&
  swapRank.includes('val mayTransferWeight:Boolean get()=false') &&
  swapRank.includes('val mayApplyWithoutReview:Boolean get()=false') &&
  authoringSheet.includes('replacing: CustomExerciseSlot? = null') &&
  authoringSheet.includes('CustomExerciseSwapRankingDomain.rank(') &&
  authoringSheet.includes('Search exercise, family, pattern, attachment or muscle') &&
  authoringSheet.includes('matchesName(choice.attachmentId)') &&
  authoringSheet.includes('" · attachment: "+it.replace(') &&
  authoringSheet.includes('Text("Same pattern")') &&
  authoringSheet.includes('Text("Shared muscles")') &&
  authoringSheet.includes('Text("Same role")') &&
  authoringSheet.includes('Text("Same family")') &&
  authoringSheet.includes('otherDayExerciseIds=otherDayExerciseIds') &&
  authoringSheet.includes('sourceHasWorkingSets=replacing?.sets?.any') &&
  plan.includes('otherDayExerciseIds=day?.slots?.filter{') &&
  plan.includes('set.type==DraftSetType.WORKING') &&
  authoringSheet.includes('if(workoutOnlySwap) "Confirm workout-only replacement"') &&
  authoringSheet.includes('"Confirm workout swap" else "Confirm this program swap"') &&
  authoringSheet.includes('forms=listOf(CustomSetFields())') &&
  customBuilder.includes('fun replaceExerciseSlot(') &&
  customBuilder.includes('newSlot.slotId==oldSlotId') &&
  customBuilder.includes('previous.supersetGroup!=null') &&
  customBuilder.includes('current.weekOverrides[blockId].orEmpty().any{') &&
  customAuthoring.includes('fun replaceSelectedExercise(') &&
  customAuthoring.includes('validSet(it,option)') &&
  cycleController.includes('fun swapReviewedExercise(') &&
  cycleController.includes('editReviewed {draft,setup,catalog ->') &&
  cycleController.includes('CustomExerciseAuthoringDomain.replaceSelectedExercise(') &&
  cycleController.includes('lastUndo = result.receipt') &&
  plan.includes('var swapExerciseEditor by remember') &&
  plan.includes('Text("Swap")') &&
  plan.includes('controller.swapReviewedExercise('),
  'Reviewed gym-backed program swaps must be ranked, independently reauthored, confirmed, scoped, source-fenced, and undoable');

assert.ok(standards.includes('var comparisonExpanded by rememberSaveable') &&
  standards.includes('if(comparisonExpanded) {') &&
  standards.includes('title="Community comparison"') &&
  standards.includes('Text("Community comparison group"') &&
  standards.includes('comparisonNotice?.let') &&
  standards.includes('onToggle={comparisonExpanded=!comparisonExpanded}') &&
  standards.includes('PursuitResponsiveActionPair(') &&
  shared.includes('fun PursuitResponsiveActionPair(') &&
  shared.includes('LocalDensity.current.fontScale>=1.35f'),
  'Community reference editor must stay collapsed and its Save/Clear actions must be readable at large text');
assert.ok(standards.includes('val bestByExercise=remember(completed,displayUnit)') &&
  standards.includes('NativeKotlinStrengthOverview(overviewItems,trendExerciseId)') &&
  standards.includes('PursuitStrengthTrendChart(samples,displayUnit,') &&
  standards.indexOf('NativeKotlinStrengthOverview(overviewItems,trendExerciseId)') <
    standards.indexOf('title="Community comparison"') &&
  standards.includes('val best=bestByExercise[standard.exerciseId]') &&
  standards.includes('var standardsExpanded by rememberSaveable') &&
  standards.includes('if(standardsExpanded) {') &&
  standards.includes('onToggle={standardsExpanded=!standardsExpanded}') &&
  standards.indexOf('title="Published reference details"') <
    standards.indexOf('PursuitSectionHeading("Your exercise favorites"') &&
  standardsOverview.includes('fun NativeKotlinStrengthOverview(') &&
  standardsOverview.includes('val fontScale=LocalDensity.current.fontScale') &&
  standardsOverview.includes('fontScale>=1.35f || maxWidth<360.dp -> 1') &&
  standardsOverview.includes('maxWidth>=760.dp -> 4') &&
  standardsOverview.includes('else -> 2') &&
  standardsOverview.includes('stateDescription=if(selected)') &&
  standardsOverview.includes('item.estimatedOneRepMax ?: "No qualifying set"') &&
  !standardsOverview.includes('canApplyProgression'),
  'Strength Standards must use one verified set summary, prioritize its responsive four-lift overview, and disclose details');

assert.ok(standards.includes('NativeStrengthFavorites.read(favoritePreferences,verified)') &&
  standards.includes('NativeStrengthFavorites.write(favoritePreferences,athleteId,selected)') &&
  standards.includes('profileId==athleteId') &&
  standards.includes('Text(if(isFavorite)"Unpin from favorites" else "Pin to favorites")') &&
  standards.includes('preferredExerciseId in NativeStrengthFavorites.supportedIds') &&
  standards.includes('onOpenHistory={historyExerciseId=trendExerciseId}') &&
  profile.includes('NativeStrengthFavorites.read(favoritePreferences,athleteId)') &&
  profile.includes('onProgressLift(liftId)') &&
  journey.includes('onProgressLift={exerciseId->') &&
  journey.includes('preferredExerciseId=progressRequestedExerciseId'),
  'Favorite strength charts must round-trip through verified Progress, Profile and History');
assert.ok(trend.includes('.sortedBy {it.completionOrder}') &&
  trend.includes('val recent=samples.sortedBy{it.completionOrder}.takeLast(20)') &&
  trend.includes('Text("Open complete exercise history")') &&
  trend.includes('Text(if(showValues)"Hide chart values" else "Show chart values")') &&
  trend.includes('estimatedStrengthLabel(sample.estimatedOneRepMax,unit)') &&
  trend.includes('contentDescription="Estimated 1RM trend.') &&
  trend.includes('CustomWorkoutVisitPart.MAIN') &&
  trend.includes('LoadBasis.BARBELL_TOTAL'),
  'The accessible chart must expose chronological verified points and full history, not predictions');
assert.ok(shared.includes('fun PursuitDisclosureRow(') &&
  shared.includes('modifier.fillMaxWidth().heightIn(min=56.dp)') &&
  shared.includes('onClick=onToggle') &&
  shared.includes('.semantics { stateDescription=if(expanded)"Expanded" else "Collapsed" }') &&
  outline.includes('PursuitDisclosureRow(') &&
  plan.includes('PursuitDisclosureRow(') &&
  plan.includes('var expandedBlockId by rememberSaveable(draft?.cycleId)') &&
  plan.includes('var expandedDayKey by rememberSaveable(draft?.cycleId)') &&
  plan.includes('val blockExpanded=expandedBlockId==block.blockId') &&
  plan.includes('val dayExpanded=expandedDayKey==dayKey') &&
  plan.includes('if(blockExpanded) {') &&
  plan.includes('if(dayExpanded) {') &&
  plan.includes('controller.reorderBlock(') &&
  plan.includes('controller.moveExercise(') &&
  plan.includes('exerciseEditor = Triple(') &&
  plan.includes('controller.previewAuthoredDay(') &&
  plan.includes('if(!showProgramEditor) {') &&
  plan.includes('NativeKotlinCustomCycleOverview(draft,currentSelection,') &&
  plan.includes('NativeKotlinCustomCycleCalibrationPanel(') &&
  plan.includes('controller.undo()') &&
  plan.includes('Advanced program details') &&
  !plan.includes('Text("Custom authoring",'),
  'Program editor must have accessible collapsed block/day hierarchy without losing real authoring controls');
assert.ok(shared.includes('scrollState:ScrollState=rememberScrollState()') &&
  shared.includes('.verticalScroll(scrollState)') &&
  plan.includes('PursuitScreenContent(scrollState=pageScroll)') &&
  plan.includes('pageScroll.scrollTo(0)') &&
  plan.includes('expandedBlockId=blockId') &&
  plan.includes('expandedDayKey=programDayKey(blockId,weekday)') &&
  plan.includes('onEditDay={blockId,weekday->') &&
  outline.includes('onEditDay:((blockId:String,weekday:Int)->Unit)?=null') &&
  outline.includes('onClick={onEditDay(block.blockId,day.weekday)}') &&
  outline.includes('Text("Edit this training day")') &&
  outline.includes('onEditExercise:((blockId:String,weekday:Int,slotId:String)->Unit)?=null') &&
  outline.includes('onClick={onEditExercise(block.blockId,day.weekday,slot.slotId)}') &&
  outline.includes('Text("Edit exercise sets")') &&
  plan.includes('onEditExercise={blockId,weekday,slotId->') &&
  plan.includes('exerciseEditor=Triple(blockId,weekday,slotId)') &&
  plan.includes('controller.editReviewedExerciseSets('),
  'Program drilldown must preserve exact exercise-slot identity and native editor writes');
assert.ok(plan.includes('NativeKotlinNextSessionBrief(draft,week,weekday)') &&
  sessionBrief.includes('cycle.blocks.firstOrNull') &&
  sessionBrief.includes('block.days.firstOrNull{it.weekday==weekday}') &&
  sessionBrief.includes('slot.sets.count{it.type==DraftSetType.WORKING}') &&
  sessionBrief.includes('PursuitMetricStrip') &&
  sessionBrief.includes('Base program outline only.') &&
  !sessionBrief.includes('rememberCoroutineScope') &&
  !sessionBrief.includes('host.'),
  'Next session game plan must be display-only sourced from the exact selected day');
assert.ok(plan.includes('permissionHost:NativeKotlinCandidateHost?=null') &&
  plan.includes('NativeKotlinAdaptiveCyclePermissionsPanel(') &&
  plan.includes('ownerHost,draft.cycleId,draft.athleteId,draft.revision)') &&
  journey.includes('permissionHost=host)') &&
  adaptiveRights.includes('host.readAdaptiveCyclePermissions(cycleId)') &&
  adaptiveRights.includes('host.confirmAdaptiveCyclePermissions(') &&
  adaptiveRights.includes('host.revokeAdaptiveCyclePermissions(cycleId,expected)') &&
  adaptiveRights.includes('val revision=when(val read=state)') &&
  adaptiveRights.includes('NativeAdaptivePermissionRead.Missing -> 0L') &&
  adaptiveRights.includes('is NativeAdaptivePermissionRead.Revoked -> read.revision') &&
  adaptiveRights.includes('is NativeAdaptivePermissionRead.Found -> existing?.revision') &&
  adaptiveRights.includes('val canEdit=!loading && !saving && revision!=null') &&
  adaptiveRights.includes('LaunchedEffect(host,cycleId,expectedAthleteId,sourceRevision)') &&
  adaptiveRights.includes('found.athleteId!=expectedAthleteId') &&
  adaptiveRights.includes('verified?.revision==result.revision') &&
  adaptiveRights.includes('revoked?.revision==result.revision') &&
  adaptiveRights.includes('enabled=confirmed && !saving') &&
  adaptiveRights.includes('if(result is NativeAdaptivePermissionWrite.Committed &&') &&
  adaptiveRights.includes('CustomCycleChangeKind.SPLIT') &&
  adaptiveRights.includes('CustomCycleChangeKind.EXERCISES') &&
  adaptiveRights.includes('CustomCycleChangeKind.SCHEDULE') &&
  adaptiveRights.includes('CustomCycleChangeKind.DAY_COUNT') &&
  adaptiveRights.includes('CustomCycleChangeKind.REMOVE_PINNED') &&
  !adaptiveRights.includes('mayApplyAutomatically=true'),
  'Adaptive permissions must be source-backed, explicit, independently revocable and never an automatic edit');
assert.ok(comparisonDomain.includes('object CustomCycleBlockComparisonDomain') &&
  comparisonDomain.includes('audit.auditedCycleRevision==cycle.revision') &&
  comparisonDomain.includes('audit.structuralReview?.verifiedDraft==cycle') &&
  comparisonDomain.includes('weeks.map{it.absoluteWeek}==expectedWeeks') &&
  comparisonDomain.includes('slots.sumOf{slot->') &&
  comparisonDomain.includes('slot.sets.count{it.type==DraftSetType.WORKING}') &&
  comparisonDomain.includes('weekOverrideCount') &&
  comparisonDomain.includes('auditedDirectMuscleChanges:List<CustomBlockMuscleChange>?') &&
  comparisonDomain.includes('TrainingMuscle.entries.mapNotNull') &&
  comparisonDomain.includes('firstAuditedWeekDirectSets') &&
  blockComparison.includes('Audited direct muscle sets') &&
  blockComparison.includes('namedMuscle(change.muscle)') &&
  blockComparison.includes('Biceps, brachialis, wrist flexors and wrist extensors') &&
  comparisonDomain.includes('val mayApplyAutomatically:Boolean get()=false') &&
  comparisonDomain.includes('val mayApproveProgram:Boolean get()=false') &&
  comparisonDomain.includes('val mayIssueWorkout:Boolean get()=false') &&
  blockComparison.includes('NativeKotlinCycleBlockComparisonPanel(') &&
  blockComparison.includes('CustomCycleBlockComparisonDomain.compare(cycle,id,currentAudit)') &&
  blockComparison.includes('PursuitMetricStrip(listOf(') &&
  blockComparison.includes('firstAuditedWeekDelta') &&
  blockComparison.includes('onEditBlock(next.blockId)') &&
  blockComparison.includes('No new program has been generated or approved here.') &&
  plan.includes('NativeKotlinCycleBlockComparisonPanel(') &&
  plan.includes('state.auditSourceRevision==state.sourceRevision') &&
  plan.includes('expandedBlockId=blockId'),
  'The future block comparison must use exact authored facts and only complete current-source audit evidence');
assert.ok(coach.includes('if(assistance==CoachAssistancePolicy.STANDARD)') &&
  coach.includes('assistance==CoachAssistancePolicy.PROGRESSION_ONLY') &&
  coach.includes('PursuitExerciseHeading(exerciseId,') &&
  coach.includes('NativeKotlinExerciseHistorySheet(host,exercise,') &&
  coach.includes('host.currentVerifiedCoachExerciseChoices()') &&
  coach.includes('onOpenHistory:()->Unit={}') &&
  coach.includes('onClick=onOpenHistory') &&
  journey.includes('onOpenHistory={navigate(KotlinCandidateDestination.HISTORY)}') &&
  coach.includes('PursuitCoachDestinations(onOpenProgram,onOpenFuture)') &&
  coach.includes('showTechnicalDetails by rememberSaveable') &&
  coach.includes('if(showTechnicalDetails)') &&
  coach.includes('Text("Dismiss unaccepted review")') &&
  coach.includes('reviewed=null') &&
  coach.includes('nextRepReview=null') &&
  coach.includes('onOpenTrain(approvedId)') &&
  !coach.includes('Candidate only · no automatic'),
  'Coach should prioritize verified mode, history, non-destructive review dismiss and hidden advanced IDs');
assert.ok(strengthMeasure.includes('BigDecimal("0.45359237")') &&
  strengthMeasure.includes('fun epley(') &&
  strengthMeasure.includes('fun relativeStrength('),
  'Unit conversion and 1RM measurements must have one pure Kotlin source');
// Design-first rule: screen width, spacing and safe scroll handling belong
// to shared Material components, not seven diverging root Columns.
for(const screen of [
  'NativeKotlinCoachSettingsScreen.kt',
  'NativeKotlinCandidateCoachScreen.kt',
  'NativeKotlinCustomCycleCandidateScreen.kt',
  'NativeKotlinFirstProgramCandidateScreen.kt',
  'NativeKotlinGymSourceCandidateScreen.kt',
  'NativeKotlinFutureWeekCoachScreen.kt',
  'NativeKotlinOnboardingCandidateScreen.kt'
]) {
  const content=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/'+screen);
  assert.ok(content.includes('PursuitScreenContent {') ||
    screen==='NativeKotlinCustomCycleCandidateScreen.kt' &&
    content.includes('PursuitScreenContent(scrollState=pageScroll) {'),
    'Responsive shared Material layout is missing in '+screen);
  assert.ok(!content.includes('PageHeading("KOTLIN COACH · ISOLATED"'),
    'Raw development jargon reached a published screen in '+screen);
}
assert.ok(journey.includes('PageHeading("TRAIN",') &&
  !journey.includes('Text("Native workout ·') &&
  history.includes('PageHeading("TRAINING LOG",'),
  'Athlete-facing Train and History must not show raw Kotlin runtime IDs');
assert.ok(!journey.includes('KotlinCandidateDestination.entries.forEach'),
  'Do not reintroduce nine raw debugging chips as the primary navigation');
for(const required of [
  'KotlinCandidateDestination.PROGRAM',
  'KotlinCandidateDestination.TRAIN',
  'KotlinCandidateDestination.HISTORY',
  'KotlinCandidateDestination.COACH',
  'KotlinCandidateDestination.ONBOARD',
  'KotlinCandidateDestination.GYM',
  'KotlinCandidateDestination.STARTER',
  'KotlinCandidateDestination.FUTURE',
  'KotlinCandidateDestination.SETTINGS'
])assert.ok(journey.includes(required),'Missing navigation destination '+required);
assert.ok(journey.includes('if(!navigationTouched &&') &&
  journey.includes('host.reviewResumableCustomWorkouts()') &&
  journey.includes('host.listOwnedCustomCycleDrafts()'),
  'Cold-start routing must be based on verified native state without stealing a manual navigation');
for(const required of [
  'KotlinHistoryPerformanceSummaryDomain.fromComposer(entry.evidence.sets)',
  'KotlinHistoryPerformanceSummaryDomain.fromCustom(entry.evidence.visits)',
  'fact.issuedLoad,fact.issuedReps,fact.issuedRir',
  'fact.prescribedLoad,fact.prescribedReps,fact.prescribedRir',
  'Actual RIR not logged',
  'Amplifier follow-ups:',
  'NativeKotlinSavedComposerHistorySetRow(',
  'host.correctSavedKotlinWorkout(',
  'KotlinSavedSetCorrection(',
  'NativeSavedCorrectionFileResult.Durable',
  'currentSet.effectiveEventId!=fact.effectiveEventId',
  'current?.manifest?.savedCorrectionRevision!=',
  'kotlinHistoryCorrectionActual('
])assert.ok(history.includes(required),'Kotlin History must show source-backed target/actual facts: '+required);
// Each group is a physical-availability checkbox, not an equipment-load
// grant. Preserve all original bench/station IDs and category ordering.
const facilityIds=[...facilityPicker.matchAll(/"([a-z0-9-]+)" to "/g)]
  .map(match=>match[1]);
assert.equal(new Set(facilityIds).size,facilityIds.length,
  'Named physical facilities must have unique stable source IDs');
for(const id of ['flat-bench','rack','incline-bench','pullup-bar'])
  assert.ok(facilityIds.includes(id),'Original source facility lost: '+id);
assert.ok(facilityIds.indexOf('hip-abduction')+1===
  facilityIds.indexOf('hip-adduction'),
  'Hip Abduction and Hip Adduction must remain adjacent');
for(const group of ['Free weights','Bars','Benches & stations',
  'Cables & general machines','Leg machines','Hip & glute machines',
  'Upper-body machines','Arm & core machines'])
  assert.ok(facilityPicker.includes('KotlinGymFacilityGroup("'+group+'"'),
    'Missing grouped gym category '+group);
assert.ok(facilityPicker.includes('val updated=if(checked)selected+id else selected-id') &&
  facilityPicker.includes('Other saved facilities:') &&
  facilityPicker.includes('Additional saved IDs are preserved'),
  'Named equipment toggles must preserve manually verified uncommon IDs');
assert.ok(onboarding.includes('NativeKotlinGroupedFacilityChoices("Available gym facilities"') &&
  gymScreen.includes('NativeKotlinGroupedFacilityChoices(') &&
  gymScreen.includes('controller.setFacilitiesAndAttachments('),
  'Onboarding and gym editing must share physical-facility choices and explicit saves');

// Source contracts only: these guard the important navigation and consent
// wiring. Runtime accessibility and touch-device acceptance are still pending.
assert.ok(plan.indexOf('NativeKotlinCustomCycleOverview(draft,currentSelection,') <
  plan.indexOf('NativeKotlinCustomCycleCalibrationPanel(') &&
  plan.includes('Open full program editor') &&
  outline.includes('slot.sets.forEachIndexed') &&
  outline.includes('authoredSetDescription(set,index+1)') &&
  outline.includes('set.amplifier?.let') &&
  outline.includes('set.targetRir?.toString() ?: "not set"'),
  'Plan must offer full-program editing and inspectable authored set details before optional coaching');
assert.ok(main.includes('android:allowBackup="false"') &&
  standards.includes('getSharedPreferences("pursuit-strength-comparison-v1",0)') &&
  standards.includes('host.reviewActiveAthleteSetup()') &&
  standards.includes('Text("Save comparison")') &&
  standards.includes('Text("Clear saved")') &&
  standards.includes('comparisonGroup?.let') &&
  standards.includes('.commit()'),
  'Strength comparison settings must have explicit athlete-scoped on-device save and clear');
assert.ok(coach.includes('PursuitCoachDestinations(onOpenProgram,onOpenFuture)') &&
  coach.includes('onOpenProgram:()->Unit={}') &&
  coach.includes('onOpenFuture:()->Unit={}') &&
  journey.includes('onOpenProgram={navigate(KotlinCandidateDestination.PROGRAM)}') &&
  journey.includes('onOpenFuture={navigate(KotlinCandidateDestination.FUTURE)}'),
  'Coach must reach the saved plan and future weeks through actual native routes');
assert.ok(coach.includes('is AdaptiveSetPrescriptionResult.Rejected -> proposed.detail') &&
  coach.includes('nativeNextRepExplanation(outcome.getOrNull())') &&
  !coach.includes('No eligible next-set progression: ${result.proposal}'),
  'The athlete must see readable recovery reasons, not a Kotlin rejection DTO');
const host=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
assert.ok(journey.includes('onOpenCoach={exerciseId->') &&
  journey.includes('coachRequestedExerciseId=exerciseId') &&
  journey.includes('onReviewNext={exerciseId->') &&
  journey.includes('preferredExerciseId=coachRequestedExerciseId') &&
  history.includes('onClick={onOpenCoach(exercise)}') &&
  history.includes('Review next workout with Coach'),
  'History and Finish must carry explicit source-backed exercise focus to Coach');
assert.ok(coach.includes('preferredExerciseId?.takeIf{it in choices}') &&
  coach.includes('host.currentVerifiedCoachExerciseChoices()') &&
  coach.includes('onOpenTrain(approvedId)') &&
  journey.includes('selectedGeneratedWorkoutId=id') &&
  journey.includes('if(generatedWorkoutId!=null) {') &&
  journey.includes('if(selectedId!=generatedWorkoutId)') &&
  journey.includes('selectedId=generatedWorkoutId') &&
  journey.includes('minimizedGeneratedId=null'),
  'Coach focus requires current owner-verified program, and accepted grant must open the exact Train entry');
assert.ok((host.match(/enableRepeatedFatigueReview=assistance.offersFatigueAdvice/g)||[]).length===2,
  'Both next-rep preview and final acceptance must preserve the athlete fatigue-advice switch');
assert.ok(tokens.includes('const val DEFAULT_ID="amethyst"') &&
  !tokens.includes('const val DEFAULT_ID="lime"') &&
  labActivity.includes('PursuitIronTheme(themeId=themeId)') &&
  labActivity.includes('PursuitThemes.find(requested)!=null') &&
  labActivity.includes('appearance.edit().putString("theme_id",requested).apply()') &&
  labActivity.includes('bars.isAppearanceLightStatusBars=themeUsesLightBars') &&
  journey.includes('NativeAppearanceSheet(') &&
  journey.includes('onSelect=onThemeSelected') &&
  journey.includes('DropdownMenuItem(text={Text("Appearance")}') &&
  nativeSettings.includes('onOpenAppearance:()->Unit={}') &&
  nativeSettings.includes('Text("Choose color theme")') &&
  journey.includes('onOpenAppearance={appearanceExpanded=true}'),
  'Kotlin Lab must start with original Amethyst, persist native theme choice, expose all web palettes, and update system bar contrast live');
console.log('PASS Kotlin Lab navigation, corrected History and selectable original web themes with Amethyst default');
