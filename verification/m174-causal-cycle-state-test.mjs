import assert from 'node:assert/strict';
import { advanceCycleState, createInitialCycleState, startPhase } from '../modules/next-engine/cycles.js';

const normalRecovery={status:'normal',confidence:'moderate',evidenceCount:0,rationale:'No broad causal fatigue evidence.'};
const neutralReview=()=>({action:'review',reasonCode:'loading_inventory_blocked',role:'primary_strength'});
const neutralHold=()=>({action:'hold',reasonCode:'normal_progression',role:'hypertrophy_compound'});
const hard=()=>({action:'hold',reasonCode:'effort_overshoot',role:'hypertrophy_compound'});
const success=()=>({action:'increase_load',reasonCode:'progression_success',role:'hypertrophy_compound'});

let development=createInitialCycleState('mixed',3);
development={...development,workoutsInPhase:development.minimumWorkouts-1};
const neutralDevelopment=advanceCycleState(development,[neutralReview(),neutralReview(),neutralReview(),neutralReview()],false,normalRecovery,'mixed');
assert.equal(neutralDevelopment.status,'building','neutral review labels must not masquerade as fatigue');
const hardDevelopment=advanceCycleState(development,[hard(),hard(),hard(),hard()],false,normalRecovery,'mixed');
assert.equal(hardDevelopment.status,'recovery_review');

const prior={phase:'mixed_accumulation'};
let recovery=startPhase(prior,'recovery','mixed',3);
recovery=advanceCycleState(recovery,[neutralHold(),neutralReview()],false,normalRecovery,'mixed');
assert.equal(recovery.recoveryExitEvidence,0);
assert.equal(recovery.status,'building');
recovery=advanceCycleState(recovery,[neutralHold(),neutralReview()],false,normalRecovery,'mixed');
assert.equal(recovery.recoveryExitEvidence,0);
assert.notEqual(recovery.status,'review_eligible');

let recovered=startPhase(prior,'recovery','mixed',3);
recovered=advanceCycleState(recovered,[success(),neutralReview()],false,normalRecovery,'mixed');
assert.equal(recovered.recoveryExitEvidence,1);
recovered=advanceCycleState(recovered,[success(),neutralHold()],false,normalRecovery,'mixed');
assert.equal(recovered.recoveryExitEvidence,2);
assert.equal(recovered.status,'review_eligible');
assert.equal(recovered.recommendedNextPhase,'mixed_accumulation');

let setback=startPhase(prior,'recovery','mixed',3);
setback={...setback,recoveryExitEvidence:1};
setback=advanceCycleState(setback,[hard(),neutralHold()],false,normalRecovery,'mixed');
assert.equal(setback.recoveryExitEvidence,0);
assert.notEqual(setback.status,'review_eligible');

console.log('M174 causal cycle-state tests OK: neutral labels stay neutral, causal negatives review, and causal success earns recovery exit.');
