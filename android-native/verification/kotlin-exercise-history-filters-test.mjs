import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const ui=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseHistorySheet.kt','utf8');

// The user sees one owner-verified, correction-aware mixed history. The
// filter is display-only; it cannot modify receipts or infer missing RIR.
assert.match(ui,/host\.reviewVerifiedKotlinHistory\(\)/);
assert.match(ui,/NativeKotlinMixedHistoryReadResult\.Review/);
// Measured identities use the same full, verified timeline and the same
// result renderer as workout History, never a rep-based history adapter.
assert.match(ui,/if\(MeasuredExerciseCatalogDomain\.find\(exerciseId\)!=null\)/);
assert.match(ui,/\.filterIsInstance<NativeKotlinMixedHistoryCompletion\.Measured>\(\)/);
assert.match(ui,/NativeKotlinMeasuredHistoryRows\(session,exerciseId\)/);
const measuredRows=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHistoryScreen.kt','utf8');
assert.match(measuredRows,/nativeMeasuredHistoryPlans\(journal\.approvedPlan\.sets,exerciseId\)/);
assert.match(measuredRows,/plans\.filter\{it\.exerciseId==exerciseId\}/);
assert.match(measuredRows,/MeasuredWorkoutResultReviewDomain\.review\(/);
const library=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseLibraryScreen.kt','utf8');
assert.match(library,/if\(historyHost!=null\)OutlinedButton\(onClick=\{historyId=id\}/);
assert.match(library,/NativeKotlinExerciseHistorySheet\(historyHost,id,onDismiss=/);
assert.match(ui,/exerciseHistoryRows\(result\.timeline,exerciseId\)/);
assert.match(ui,/timeline\.completions\.asReversed\(\)\.flatMap/);
// Staged technique rows once shared a single MAIN/FOLLOW_UP key. In a
// LazyColumn that made myo-rep mini 1..5 collide. Preserve one row per
// physically logged stage, with corrections/partials attached to the exact
// main set in Working and All views.
const identity=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseHistoryRowIdentity.kt','utf8');
assert.match(identity,/\$\{workoutId\.length\}/);
assert.match(identity,/\$\{setKey\.length\}/);
assert.match(identity,/\(part\?\.name \?: "COMPOSER"\)\+":\$stage"/);
assert.match(ui,/nativeExerciseHistoryRowKey\(completed\.manifest\.workoutId,/);
assert.match(ui,/set\.part,set\.stage/);
assert.match(ui,/CustomHistoricalTechniqueGroupingDomain\.group\(/);
assert.match(ui,/techniqueCaptions\[parent\]/);
// Saved-set corrections can change actual facts without creating another
// Finish receipt. Cached UI summaries must track the verified timeline
// content rather than only a coarse revision number.
assert.match(ui,/val rows=remember\(result\.timeline,exerciseId\)/);
assert.match(ui,/val techniqueCaptions=remember\(result\.timeline,exerciseId\)/);

assert.match(ui,/AdvancedAmplifierProtocolDomain\.stageNoun\(kind\)/);
assert.match(ui,/items\(visible,key=\{it\.key\}\)/);

assert.match(ui,/val order=completed\.manifest\.completionOrder/);
assert.match(ui,/completed\.evidence\.sets\.filter/);
assert.match(ui,/completed\.evidence\.visits\.filter/);
assert.match(ui,/it\.exerciseId==exerciseId/);
assert.match(ui,/actual\.actualRir\?\.let/);
assert.match(ui,/RIR not reported/);
assert.doesNotMatch(ui,/recordWorkoutOperation\(/);

assert.match(ui,/private enum class ExerciseHistoryView \{ WORKING, ALL, SETBACKS \}/);
assert.match(ui,/rememberSaveable\(exerciseId\)/);
for (const label of ['Working sets','All entries','Skipped / failed'])
  assert.ok(ui.includes('Text("'+label+'")'),label+' remains visible');
assert.match(ui,/ExerciseHistoryView\.WORKING -> rows\.filter/);
assert.match(ui,/it\.type==DraftSetType\.WORKING && !it\.followUp/);
assert.match(ui,/ExerciseHistoryView\.ALL -> rows/);
assert.match(ui,/ExerciseHistoryView\.SETBACKS -> rows\.filter/);
assert.match(ui,/it\.outcome!=KotlinJournalSetOutcome\.COMPLETED/);

// Each entry is grouped under its actual completed workout. A filtered
// working-set ordinal is computed against the entire source sequence,
// not assigned using the warmup/amplifier or filtered row number.
assert.match(ui,/val sessions=visible\.groupBy \{ it\.sessionOrder \}/);
assert.match(ui,/val allSessions=rows\.groupBy \{ it\.sessionOrder \}/);
assert.match(ui,/if\(visits\.first\(\)\.key==row\.key\)/);
assert.match(ui,/val completed=visits\.count/);
assert.match(ui,/val skipped=visits\.count/);
assert.match(ui,/val failed=visits\.count/);
assert.match(ui,/allSessions\.getValue\(row\.sessionOrder\)/);
assert.match(ui,/\.takeWhile \{ it\.key!=row\.key \}/);
assert.match(ui,/it\.type==row\.type && it\.followUp==row\.followUp/);
assert.match(ui,/KotlinJournalSetOutcome\.SKIPPED -> "Skipped · no performed set"/);
assert.match(ui,/KotlinJournalSetOutcome\.FAILED_ATTEMPT ->/);
assert.match(ui,/KotlinJournalSetOutcome\.COMPLETED ->/);

// Favoriting an exercise must still be permission-checked and backed by
// a fresh manifest before private saved preferences can change.
assert.match(ui,/NativeExerciseFavorites\.loggedIds\(verified\)/);
assert.match(ui,/live\.athleteId!=verified\.athleteId/);
assert.match(ui,/live\.manifestRevision!=verified\.manifestRevision/);
assert.match(ui,/NativeExerciseFavorites\.write\(/);
// A and B labels are local to each reviewed gym. The same machine ID may
// exist in two gyms without making their performance histories comparable.
const historyOwner=readFileSync(
  'android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinMixedHistoryReadCoordinator.kt','utf8');
assert.match(historyOwner,/val gymId: String = ""/);
assert.match(historyOwner,/journal\.originalSnapshot\.stamp\.gymId/);
assert.match(ui,/private data class PhysicalMachineHistoryKey\(val gymId:String,val machineId:String\)/);
assert.match(ui,/completed\.evidence\.source\.gymId/);
assert.match(ui,/completed\.gymId/);
assert.match(ui,/val machineKeys=rows\.map\(::historyMachine\)\.distinct\(\)/);
const focusPolicy=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinHistoryFocusPolicy.kt','utf8');
assert.match(ui,/var showAllMachines by rememberSaveable\(exerciseId\)\{mutableStateOf\(true\)\}/);
assert.match(ui,/selectedMachineAthleteId=verified\.athleteId/);
assert.match(ui,/NativeKotlinHistoryFocusPolicy\.admitStation\(/);
assert.match(ui,/selectedMachineAthleteId,verified\.athleteId/);
assert.match(focusPolicy,/selectedAthleteId!=currentAthleteId/);
assert.match(focusPolicy,/verifiedStations\.count\{it==chosen\}==1/);
assert.match(ui,/activeStation\?\.let\{chosen->/);
assert.match(ui,/it\.gymId==chosen\.first && it\.machineId==chosen\.second/);
assert.doesNotMatch(ui,/\?:machineKeys\.firstOrNull\(\)/);
assert.match(ui,/NativeKotlinMachineStationLabels\.label\(/);
assert.match(ui,/displayStations=machineKeys\.map/);
assert.doesNotMatch(ui,/Machine \$\{key\.machineId\}|\$\{row\.gymId\}/);
assert.match(ui,/val visible=if\(machineKeys\.size>1 && activeMachine!=null\)/);
assert.match(ui,/FilterChip\(selected=activeMachine==null,/);
assert.match(ui,/Text\("All machines · labeled"\)/);
assert.match(ui,/showAllMachines=false/);
assert.match(ui,/shown\.filter\{historyMachine\(it\)==activeMachine\}/);
assert.match(ui,/row\.gymId/);
// The History sheet must remain useful after a storage/inventory read error,
// and session headings must use recorded Finish dates (never receipt order).
assert.match(ui,/var refreshVersion by remember\(host,exerciseId\)/);
assert.match(ui,/LaunchedEffect\(host,exerciseId,refreshVersion\)/);
assert.match(ui,/refreshVersion\+\+/);
assert.match(ui,/Retry history/);
assert.match(ui,/Refresh history/);
assert.match(ui,/catch\(cancelled:CancellationException\) \{ throw cancelled \}/);
assert.match(ui,/sessionManifests\[row\.sessionOrder\]/);
assert.match(ui,/nativeExerciseHistorySessionTitle\(it,historyZone,historyLocale\)/);
console.log('PASS verified exercise history session grouping, gym-specific A\/B filters, and correction-aware favorites');
