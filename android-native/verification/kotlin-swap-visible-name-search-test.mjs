import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** Source-only safety contract; no Android artifact and no runtime JS. */
const ui=readFileSync(
    'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinComposerSwapSheet.kt','utf8');
const engine=readFileSync(
    'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinComposerSwapReviewDomain.kt','utf8');
for(const phrase of [
    'kotlinExerciseLabel(it.option.exerciseId)',
    'choices,query,matchingPattern,sharedMusclesOnly,displayLabels',
    'val displayLabels=remember(choices)',
    'label={Text("Shared direct muscles")}',
    'label={Text("Same pattern")}',
    'sourceTargets.map {ComposerSwapDraftRow()}',
    'onConfirm(selected.option.exerciseId,approved)'
]) assert.ok(ui.includes(phrase),'Swap UI failed label and verified-target contract: '+phrase);
for(const phrase of [
    'displayLabels:Map<String,String> = emptyMap()',
    'matchesSearch(it.option,search,displayLabels[it.option.exerciseId])',
    'matchesSearch(it,search,displayLabels[it.exerciseId])',
    'it.role==source.role',
    'it.exerciseId !in currentExerciseIds'
]) assert.ok(engine.includes(phrase),'Kotlin swap matching/ranking contract missing: '+phrase);

const workout=readFileSync(
    'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt','utf8');
for(const phrase of [
    'listSaver<SnapshotStateList<ComposerSwapDraftRow>,String>(',
    'rows.flatMap{listOf(it.weight,it.min,it.max,it.targetRir)}',
    'val issuedTargetsSignature=remember(sourceTargets)',
    'target.setKey+":"+target.exerciseId+":"+target.type+":"',
    'var query by rememberSaveable(fromExerciseId,issuedTargetsSignature)',
    'var matchingPattern by rememberSaveable(fromExerciseId,issuedTargetsSignature)',
    'var sharedMusclesOnly by rememberSaveable(fromExerciseId,issuedTargetsSignature)',
    'var selectedId by rememberSaveable(fromExerciseId,issuedTargetsSignature)',
    'saver=composerSwapDraftSaver',
    'sourceTargets.map {ComposerSwapDraftRow()}'
]) assert.ok(ui.includes(phrase),
    'Pending workout-only swap authoring must survive rotation but reset for changed issued targets: '+phrase);
for(const phrase of [
    'var swapSourceId by rememberSaveable(workoutId)',
    'LaunchedEffect(host,workoutId,swapSourceId,journal?.revision)',
    'host.reviewComposerWorkoutSwapChoices(workoutId,source)',
    'if(verified.isEmpty()) {',
    'swapSourceId=null',
    'swapChoices=verified'
]) assert.ok(workout.includes(phrase),
    'Restored swap must obtain fresh Kotlin owner-verified alternatives before confirmation: '+phrase);
assert.ok(!workout.includes('var swapChoices by rememberSaveable('),
    'An obsolete option list is never an authorization across process recreation');
assert.ok(!ui.includes('sourceTargets.map { target -> target.load }'),
    'Source exercise load may never prepopulate a replacement exercise');

console.log('PASS native swap searches displayed names without replacing verified option ownership');
