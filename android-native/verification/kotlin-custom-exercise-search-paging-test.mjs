import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const sheet=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomExerciseSheet.kt',
 'utf8');
const rank=readFileSync(
 'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomExerciseSwapRankingDomain.kt',
 'utf8');

// Source candidates always come from the already-reviewed current gym/catalog.
assert.match(sheet,/val labeled=available\.filter \{ choice ->/);
assert.match(sheet,/val terms=search\.trim\(\)\.replace\('_',' '\)\.replace\('-',' '\)/);
assert.match(sheet,/terms\.all \{ searchable\.contains\(it\) \}/);
for(const field of [
 'choice.exerciseId','choice.familyId','choice.variantId',
 'choice.attachmentId','kotlinExerciseLabel(choice.exerciseId)',
 'choice.pattern?.name','choice.role?.name',
 'choice.muscleCredits.map { it.muscle.name }'
]) assert.ok(sheet.includes(field),'search missing '+field);

// Rank before narrowing the matches, preserving pattern/family/role/muscle.
assert.match(sheet,/CustomExerciseSwapRankingDomain\.rank\(\s*available,replacing\?\.exerciseId/);
assert.match(sheet,/samePatternOnly=samePatternOnly/);
assert.match(sheet,/sharedMusclesOnly=sharedMusclesOnly/);
assert.match(sheet,/sameRoleOnly=sameRoleOnly/);
assert.match(sheet,/sameFamilyOnly=sameFamilyOnly/);
assert.match(sheet,/otherDayExerciseIds=otherDayExerciseIds/);
assert.match(sheet,/sourceHasWorkingSets=replacing\?\.sets\?\.any/);
assert.match(rank,/fun rank\(/);

// The same ID can represent different reviewed attachment/variant choices.
// Only the exact matching choice may pass the visible search.
assert.match(sheet,/val matchedChoices=labeled\.toSet\(\)/);
assert.match(sheet,/val searched=ranked\.filter \{ it\.choice in matchedChoices \}/);
assert.doesNotMatch(sheet,/it\.choice\.exerciseId in matchingIds/);

// The first 20 are a page, not a catalog limit.
assert.match(sheet,/var visibleCount by remember\(search,samePatternOnly,sharedMusclesOnly,/);
assert.match(sheet,/mutableIntStateOf\(20\)/);
assert.match(sheet,/val filtered=searched\.take\(visibleCount\)/);
assert.match(sheet,/\(visibleCount\+20\)\.coerceAtMost\(searched\.size\)/);
assert.match(sheet,/onClick=\{visibleCount=searched\.size\}/);
assert.match(sheet,/Text\("Show all"\)/);
assert.doesNotMatch(sheet,/searched\.take\(20\)/);
assert.doesNotMatch(sheet,/Narrow your search for more/);

// A swap does not carry across old prescriptions or secretly accept itself.
assert.match(sheet,/LaunchedEffect\(exerciseId,replacing\?\.slotId\)/);
assert.match(sheet,/forms=listOf\(CustomSetFields\(\)\)/);
assert.match(sheet,/swapConfirmation=false/);
console.log('PASS native custom swap: ranked, exact variant filter, full approved catalog paging');
