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
// Coach and both live Kotlin workout types use the same Material readiness
// surface. Turning off fatigue advice hides its readiness selector only;
// movement restriction stays available and still invalidates stale review.
const shared=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/PursuitDesignComponents.kt','utf8');
const custom=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt','utf8');
const generated=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt','utf8');
assert.match(shared,/internal fun PursuitWorkoutReadinessPanel\(/);
assert.match(shared,/showReadiness:Boolean=true/);
assert.match(shared,/restrictionLabel:String="Movement restricted"/);
assert.match(shared,/if\(showReadiness\)/);
assert.match(ui,/PursuitWorkoutReadinessPanel\(/);
assert.match(ui,/onReadinessChange=\{readiness=it;invalidate\(\)\}/);
assert.match(ui,/onMovementRestrictedChange=\{restricted=it;invalidate\(\)\}/);
assert.match(ui,/showReadiness=assistance\?\.offersFatigueAdvice==true/);
assert.match(custom,/PursuitWorkoutReadinessPanel\(/);
assert.match(generated,/PursuitWorkoutReadinessPanel\(/);
console.log('PASS Coach approved-exercise search, current selection, accessible pagination and authority boundaries');
