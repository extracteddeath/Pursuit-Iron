import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const source=(file)=>readFileSync(dir+file,'utf8');
const shared=source('PursuitDesignComponents.kt');
const generated=source('NativeKotlinWorkoutCandidateScreen.kt');
const custom=source('NativeKotlinCustomJournalScreen.kt');
const coach=source('NativeKotlinCandidateCoachScreen.kt');

// Both real native loggers must use the same editable readiness UI.
assert.match(shared,/internal fun PursuitTrainingReadinessControls\(/);
assert.match(shared,/CoachReadiness\.entries/);
assert.match(shared,/horizontalScroll\(rememberScrollState\(\)\)/);
assert.match(shared,/enabled=enabled/);
assert.match(shared,/onReadinessChange\(option\)/);
assert.match(shared,/onCheckedChange=onRestrictionChange/);
assert.match(generated,/PursuitTrainingReadinessControls\(/);
assert.match(custom,/PursuitTrainingReadinessControls\(/);
assert.match(coach,/PursuitTrainingReadinessControls\(/);
// Coach must never show readiness UI when assistance explicitly disables it.
assert.match(shared,/if\(showReadiness\)/);
assert.match(coach,/showReadiness=assistance\?\.offersFatigueAdvice==true/);
assert.match(coach,/onReadinessChange=\{readiness=it;invalidate\(\)\}/);
assert.match(coach,/onRestrictionChange=\{restricted=it;invalidate\(\)\}/);
assert.match(coach,/restrictionLabel="Movement restricted or painful"/);
assert.match(coach,/Fatigue-aware advice is off/);

// Never turn a display preference or readiness selection into a journal write.
const sharedReadiness=shared.slice(shared.indexOf('internal fun PursuitTrainingReadinessControls('));
assert.doesNotMatch(sharedReadiness,/record\(|finish\(|issue\(|write\(|approve\(/);

// In generated Focus, Complete is above the secondary coach/readiness/swap
// controls. All-sets keeps those options above the full set ledger.
const generatedSection=generated.slice(
    generated.indexOf('val renderSecondaryControls:'),
    generated.indexOf('if(presentation.canFinish)PursuitCard'));
assert.ok(generatedSection.includes('renderSetEntry()'));
assert.match(generatedSection,/if\(showAll\) renderSecondaryControls\(\)\s*renderSetEntry\(\)\s*if\(!showAll\) renderSecondaryControls\(\)/);

// In custom Focus, the next-workout entry appears ahead of readiness, but the
// unfocused full-ledger path still keeps readiness above the program's sets.
const beforeNext=custom.indexOf('if(!focusOnNext && !finished');
const next=custom.indexOf('if(!finished && current.finishedByEventId==null)');
const entries=custom.indexOf('if(!verifiedFinishedInHistory || showFinishedSets)');
const afterEntries=custom.indexOf('if(focusOnNext && !finished');
const finish=custom.indexOf('if(!verifiedFinishedInHistory)PursuitCard {',afterEntries);
assert.ok(beforeNext>0 && beforeNext<next && next<entries &&
    entries<afterEntries && afterEntries<finish);

// All actuals, history and coaching remain owned by the durable controllers.
assert.match(custom,/CustomWorkoutJournalDomain\.nextUnresolvedVisit/);
assert.match(generated,/controller\.record\(set\.target\.setKey,outcome,actual\)/);
console.log('PASS Kotlin workouts and Coach share guarded readiness controls without training-state writes');
