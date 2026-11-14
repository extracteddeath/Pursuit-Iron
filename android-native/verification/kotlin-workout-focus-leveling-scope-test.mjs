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
console.log('PASS workout Focus follows saved outcomes, while Leveling is machine scoped');
