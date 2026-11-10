import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const ui=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateCoachScreen.kt','utf8');
const domain=readFileSync(
 'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CoachExerciseChoiceSearchDomain.kt','utf8');

// Only already-approved choices can be searched and shown. Neither a query
// nor a preferred/favorite movement grants training authority.
assert.match(ui,/host\.currentVerifiedCoachExerciseChoices\(\)/);
assert.match(ui,/preferredExerciseId\?\.takeIf\{it in choices\}/);
assert.match(ui,/val permitted=exerciseId in exerciseChoices/);
assert.match(ui,/CoachExerciseChoiceSearchDomain\.filter\(\s*exerciseChoices,exerciseSearch,::kotlinExerciseLabel\)/);
assert.match(domain,/return approved\.distinct\(\)\.filter/);
assert.match(domain,/tokens\.all \{ searchable\.contains\(it\) \}/);

// Touch-safe full-width results, explicit selection, searchable pagination.
assert.match(ui,/label=\{Text\("Search approved exercises"\)\}/);
assert.match(ui,/val matches=CoachExerciseChoiceSearchDomain/);
assert.match(ui,/matches\.take\(visibleCoachChoices\)\.forEach/);
assert.match(ui,/onClick=\{exerciseId=id;invalidate\(\)\}/);
assert.match(ui,/enabled=!busy/);
assert.match(ui,/heightIn\(min=48\.dp\)/);
assert.match(ui,/\(visibleCoachChoices\+10\)\.coerceAtMost\(matches\.size\)/);
assert.match(ui,/visibleCoachChoices=10/);
assert.doesNotMatch(ui,/else -> Row\(Modifier\.horizontalScroll\(rememberScrollState\(\)\)/);
assert.match(ui,/host\.reviewCurrentNativeLoad\(/);
assert.match(ui,/host\.reviewNextNativeSetTargets\(/);
console.log('PASS Coach approved-exercise search, current selection, accessible pagination and authority boundaries');
