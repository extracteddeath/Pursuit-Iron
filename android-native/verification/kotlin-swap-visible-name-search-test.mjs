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
console.log('PASS native swap searches displayed names without replacing verified option ownership');
