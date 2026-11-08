import assert from 'node:assert/strict';
import fs from 'node:fs';

// Narrow UI source contract; actual on-device interaction remains a separate
// acceptance milestone, not a claim made by this static test.
const read=p=>fs.readFileSync(p,'utf8');
const journal=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt');
const journey=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt');

for(const phrase of [
  'val nextVisit=current.snapshot.slots.flatMap',
  'it !in current.effectiveVisits',
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
console.log('PASS next native set gets a focused prefilled editable form; actual effort stays unknown and recording requires an explicit action');
