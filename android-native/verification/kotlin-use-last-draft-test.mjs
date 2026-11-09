import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const app='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const engine='android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/';
const read=(root,name)=>readFileSync(root+name,'utf8');
const policy=read(engine,'WorkoutLastDraftDomain.kt');
const reference=read(app,'NativeKotlinWorkoutReference.kt');
const generated=read(app,'NativeKotlinWorkoutCandidateScreen.kt');
const custom=read(app,'NativeKotlinCustomJournalScreen.kt');

// The underlying native owner already matches athlete, day, slot, current
// gym and physical load basis. The last actual must ALSO be a real completed
// set; never substitute skipped, failed or unavailable previous values.
assert.match(policy,/history as\? WorkoutLastReferenceResult\.Ready/);
assert.match(policy,/record\.outcome!=KotlinJournalSetOutcome\.COMPLETED/);
assert.match(policy,/CustomWorkoutJournalDomain\.actualValid\(/);
assert.match(policy,/record\.loadBasis==currentLoad\.basis/);
assert.match(policy,/record\.loadBasis==null/);
assert.match(reference,/if\(mode!=WorkoutReferenceMode\.LAST\)return/);
assert.match(reference,/WorkoutLastDraftDomain\.reviewedActual\(history,key,currentLoad\)/);
assert.match(reference,/onClick=\{onApply\(actual\)\}/);
assert.match(reference,/Missing last RIR stays blank/);
assert.match(reference,/heightIn\(min=48\.dp\)/);

// Do not overwrite corrected or saved facts and never guess actual RIR from
// prescribed RIR. Recorded loads remain editable drafts until Complete.
assert.match(generated,/if\(!set\.isResolved\) PursuitUseLastCompletedSet\(/);
assert.match(generated,/key=WorkoutReferenceKey\(target\.setKey\)/);
assert.match(generated,/effort=last\.actualRir\?\.toString\(\)\.orEmpty\(\)/);
assert.match(custom,/if\(saved==null\) PursuitUseLastCompletedSet\(/);
assert.match(custom,/part==CustomWorkoutVisitPart\.FOLLOW_UP\)/);
assert.match(custom,/actualRir=last\.actualRir\?\.toString\(\)\.orEmpty\(\)/);
const helper=reference.slice(reference.indexOf('internal fun PursuitUseLastCompletedSet('),
   reference.indexOf('internal fun kotlinLoadBasisLabel('));
assert.doesNotMatch(helper,/\.record\(|\.finish\(|\.write\(|\.approve\(|changePrescription\(/);
console.log('PASS reviewed Last values are explicitly copied into drafts, never into training state');
