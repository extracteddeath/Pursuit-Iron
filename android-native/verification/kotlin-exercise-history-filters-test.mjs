import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const ui=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinExerciseHistorySheet.kt','utf8');

// The user sees one owner-verified, correction-aware mixed history. The
// filter is display-only; it cannot modify receipts or infer missing RIR.
assert.match(ui,/host\.reviewVerifiedKotlinHistory\(\)/);
assert.match(ui,/NativeKotlinMixedHistoryReadResult\.Review/);
assert.match(ui,/exerciseHistoryRows\(result\.timeline,exerciseId\)/);
assert.match(ui,/timeline\.completions\.asReversed\(\)\.flatMap/);
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
console.log('PASS verified exercise history session grouping, non-mutating filters, full-source ordinals and favorite authority');
