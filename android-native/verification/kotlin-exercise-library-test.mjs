import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const app='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const store='android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/';
const read=p=>readFileSync(p,'utf8');
const screen=read(app+'NativeKotlinExerciseLibraryScreen.kt');
const model=read(app+'NativeKotlinExerciseLibraryPresentation.kt');
const gym=read(app+'NativeKotlinGymSourceCandidateScreen.kt');
const controller=read(app+'NativeKotlinGymSourceController.kt');
const host=read(app+'NativeKotlinCandidateHost.kt');
const domain=read(store+'NativeKotlinGymCatalogEditingDomain.kt');
const shared=read(app+'NativeComponents.kt');
const workflow=read('.github/workflows/android-kotlin-rebuild.yml');

// One verified home, above the long inventory, with no global catalog fallback.
assert.match(gym,/key\(source\.ownerKey,source\.athleteId\)/);
assert.match(gym,/NativeKotlinExerciseLibraryAccess\(source,state\.busy,state\.message,state\.lastExclusionEdit/);
assert.doesNotMatch(gym,/showMovementCatalog|source\.catalog\.forEach/);
assert.match(model,/CustomExerciseAuthoringDomain\.picker\(source\.setup,source\.catalog\)/);
assert.match(model,/CoachExerciseChoiceSearchDomain\.filter\(byId\.keys\.toList\(\),query\)/);
assert.match(model,/move\?\.credits\?\.any/);
assert.match(model,/MeasuredSessionAuthoringDomain\.available\(athlete\)/);
assert.match(model,/NativeMeasuredLibraryRecord/);
assert.match(screen,/nativeExerciseLibraryEntries\(source/);
assert.match(domain,/val known=standard\+typed/);
assert.match(model,/gearId in record\.gearIds/);
assert.doesNotMatch(model,/StarterExerciseTranche|\.publish\(|\.record\(|\.issue\(/);
assert.match(screen,/val matches=filterNativeExerciseLibrary/);
assert.match(screen,/matches\.take\(visibleCount\)/);
assert.match(screen,/Show more exercises/);
assert.match(screen,/rememberSaveable\(source\.ownerKey,source\.athleteId,\s*query,muscleId,gearId,familyId,angleId,attachmentId,patternId,roleId,\s*availability\)/);
assert.match(screen,/familyId=null;angleId=null;attachmentId=null/);
assert.match(screen,/nativeExerciseFamilyVariants\(records\.filterIsInstance<NativeKotlinExerciseLibraryRecord>\(\),id\)/);
assert.match(model,/move\?\.pattern==pattern/);
assert.match(model,/move\?\.role==role/);
assert.match(screen,/"pattern" to \(pattern\?/);
assert.match(screen,/"role" to \(role\?/);
assert.match(screen,/patternId=null;roleId=null/);
assert.match(screen,/var open by rememberSaveable\(source\.ownerKey,source\.athleteId\)/);
for(const name of ['baseRevision','original','draft','query','muscleId','gearId',
  'patternId','roleId','availability','showFilters','selectedId','historyId']) {
  assert.match(screen,new RegExp('var '+name+' by rememberSaveable\\(source\\.ownerKey,source\\.athleteId\\)'));
}
for(const name of ['filterPicker','discard','submitted']) {
  assert.match(screen,new RegExp('var '+name+' by remember\\(source\\.ownerKey,source\\.athleteId\\)'));
}
assert.match(screen,/Role\.RadioButton/);
assert.match(screen,/older catalog will be retained/);

// An unsent edit is never an approval. Stale views require an explicit reload.
assert.match(screen,/val stale=baseRevision!=source\.revision/);
assert.match(screen,/source\.ownerKey,source\.athleteId,\s*baseRevision,original\.toSet\(\),draft\.toSet\(\)/);
assert.match(screen,/changed && !stale && !overLimit && records\.isNotEmpty\(\)/);
assert.match(screen,/if\(changed\) \{discard=true;false\} else true/);
assert.match(screen,/Discard exclusion changes\?/);
assert.match(screen,/footer=\{/);
assert.match(screen,/lastEdit\.request==submitted && lastEdit\.matches\(source\)/);
assert.match(shared,/footer:\(@Composable ColumnScope\.\(\)->Unit\)\?=null/);
assert.match(shared,/if\(footer==null\)/);
assert.match(shared,/Modifier\.weight\(1f,fill=false\)\.verticalScroll/);
assert.doesNotMatch(screen,/authoringSourceStore|\.publish\(|\.finish\(|\.record\(|\.issue\(/);

// Existing native source CAS owns the save and the exact-context Undo.
assert.match(controller,/current\.value=loaded\.copy\(busy=pending\.get\(\)\)/);
assert.match(controller,/previousExclusionEdit\?\.takeIf\{it\.matches\(result\.source\)\}/);
assert.match(controller,/edit==current\.value\.lastExclusionEdit && edit\.matches\(current\.value\.source\)/);
assert.match(host,/editCustomAuthoringSource\(captured\.sourceRevision\)/);
assert.match(host,/NativeKotlinGymCatalogEditingDomain\.setExerciseExclusions\(it,captured\)/);
assert.match(domain,/source\.ownerKey!=change\.ownerKey \|\| source\.athleteId!=change\.athleteId/);
assert.match(domain,/athlete\.excludedExerciseIds!=change\.previous/);
assert.match(domain,/change\.next-known!=change\.previous-known/);
assert.match(domain,/CoachAthleteSetupDomain\.MAX_EXCLUDED_EXERCISES/);
assert.match(model,/source==savedSource/);
assert.match(model,/previous=request\.next\.toSet\(\),next=request\.previous\.toSet\(\)/);
assert.ok(workflow.includes('node android-native/verification/kotlin-exercise-library-test.mjs'));
console.log('PASS native exercise discovery, pending preferences, source CAS and exact-context Undo');

assert.match(read(app+'NativeKotlinCandidateJourney.kt'),/onReviewPlan=\{navigate\(KotlinCandidateDestination\.PROGRAM\)\}/);
assert.match(screen,/TextButton\(onClick=review,enabled=!busy/);

assert.match(screen,/NativeKotlinExerciseHistorySheet\(historyHost,id,onDismiss=\{historyId=null\}\)/);
assert.match(read(app+'NativeKotlinCandidateJourney.kt'),/historyHost=host/);
