import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';

/**
 * Source integration sentinel for Compose focus/Leveling policy.
 * Only the full Android test journey can prove touch, rotate and TalkBack UX.
 * This file never runs as an Android training engine.
 */
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const path=join(root,
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
const screen=readFileSync(path,'utf8');
const focus=screen.split('LaunchedEffect(presentation?.revision) {')[1]
  ?.split('fun reviewSwap(exerciseId:String)')[0];
assert.ok(focus,'workout Focus must respond to verified journal presentations');
assert.match(focus,/previouslyOpenKeys\?\.contains\(current\)==true/,
  'advance only after the chosen set was previously unresolved');
assert.match(focus,/current !in stillOpen/,
  'a receipt must resolve the chosen set before advancing');
assert.match(focus,/current !in available/,
  'source-reviewed swap must never leave a missing selected key');
assert.match(focus,/if\(current==null \|\| current !in available \|\|/,
  'do not move for an unrelated session-note or coaching revision');
assert.match(focus,/previouslyOpenKeys=stillOpen/,
  'every new durable presentation updates the previous unresolved set');
assert.doesNotMatch(focus,/if\(presentation!=null\)\s*selectedKey=/,
  'an unconditional revision reset steals Focus during note editing');
assert.match(screen,/priorLevelingScope by rememberSaveable\(workoutId\)/);
assert.match(screen,/currentLevelingScope=selected\?\.target\?\.exerciseId/);
assert.match(screen,/priorLevelingScope!=currentLevelingScope/);
assert.match(screen,/if\(priorLevelingScope==currentLevelingScope/,
  'previous-workout Leveling dialog must be scoped to exact exercise and machine');
assert.match(screen,/onDismiss=\{priorLevelingScope=null\}/);
assert.doesNotMatch(screen,/var showPriorLeveling by rememberSaveable/,
  'a global Leveling Boolean cannot bind review to one physical machine');
const reference=readFileSync(join(root,
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutReference.kt'),'utf8');
const custom=readFileSync(join(root,
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt'),'utf8');
// Swapping or undoing while Last is already selected must invalidate the
// cached result immediately. Ordinary set/note receipts must not scan the
// athlete's full history again. The supplied snapshot is only an effect key;
// the host still verifies all current source and historical facts itself.
assert.match(reference,/remember\(host,workoutId,custom,enabled,source,refreshVersion\)/);
assert.match(reference,/LaunchedEffect\(host,workoutId,custom,enabled,source,refreshVersion\)/);
assert.match(reference,/if\(enabled && source!=null\)result=try/);
assert.match(reference,/host\.reviewWorkoutLastReferences\(workoutId,custom\)/);
assert.match(reference,/catch\(cancelled:CancellationException\)/);
assert.match(reference,/throw cancelled/);
assert.match(screen,/source=journal\?\.snapshot\?\.let\{WorkoutLastReferenceInput\.Generated\(it\)\}/);
assert.match(custom,/source=current\?\.snapshot\?\.let\{WorkoutLastReferenceInput\.Custom\(it\)\}/);
for(const source of [screen,custom]) {
  assert.match(source,/refreshVersion=lastReferenceRefresh/);
  assert.match(source,/onRetry=if\(lastReferences==WorkoutLastReferenceResult\.Unavailable/);
  assert.match(source,/\{lastReferenceRefresh\+\+\}/);
}
assert.match(reference,/Text\("Retry Last"\)/);
console.log('PASS Focus, machine-scoped Leveling and source-keyed retryable Last references');
