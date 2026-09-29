import assert from 'node:assert/strict';
import { diagnoseExerciseResponse, longitudinalEvidenceWindow } from '../modules/next-engine/response.js';

const exercise = {
  exerciseId:'cable_curl', name:'Cable Curl', role:'hypertrophy_isolation', sets:3,
  prescription:{ reps:[8,12], rir:[1,2] }
};
const recovery = { status:'normal' };
const date = i => new Date(Date.UTC(2026,8,1+i)).toISOString();
const sets = ({ reps=10, rir=2, pain=false, technique='good', load=50 }={}) => [0,1,2].map(()=>({ load, reps, rir, painFlag:pain, techniqueQuality:technique }));
const exposure = (i, setOptions={}, progression={ outcome:'productive', reasonCode:'normal_progression' }) => ({ completedAt:date(i), sets:sets(setOptions), progression });

// One explicitly context-limited bad day is logged for explainability, but excluded from durable response evidence.
const badDay = exposure(1,{reps:5,rir:0},{ outcome:'context_limited', reasonCode:'readiness_limited_exposure' });
const normal = exposure(2,{reps:10,rir:2});
const oneBadDay = diagnoseExerciseResponse(exercise,[badDay,normal],recovery);
assert.notEqual(oneBadDay.state,'fatigue_limited');
assert.equal(oneBadDay.evidence.logged,2);
assert.equal(oneBadDay.evidence.comparable,1);
assert.equal(oneBadDay.evidence.excluded,1);
assert.equal(oneBadDay.evidence.excludedReasons.context_limited,1);

// Edits/substitutions cannot become pain/poor-fit memory for the original movement.
const editedPain1 = exposure(3,{pain:true},{ outcome:'non_comparable', reasonCode:'non_comparable_exposure' });
const editedPain2 = exposure(4,{pain:true},{ outcome:'non_comparable', reasonCode:'non_comparable_exposure' });
const editedPainDiagnosis = diagnoseExerciseResponse(exercise,[editedPain1,editedPain2,normal],recovery);
assert.notEqual(editedPainDiagnosis.state,'poor_fit');
assert.equal(editedPainDiagnosis.evidence.excluded,2);

// Repeated comparable evidence still triggers the intended structural diagnoses.
const hard1 = exposure(5,{reps:6,rir:0},{ outcome:'failure', reasonCode:'rep_floor_miss' });
const hard2 = exposure(6,{reps:6,rir:0},{ outcome:'failure', reasonCode:'effort_overshoot' });
const fatigue = diagnoseExerciseResponse(exercise,[hard1,hard2],recovery);
assert.equal(fatigue.state,'fatigue_limited');
assert.equal(fatigue.intervention.action,'reduce_set');
assert.equal(fatigue.evidenceCount,2);

const pain1 = exposure(7,{pain:true});
const pain2 = exposure(8,{pain:true});
const poorFit = diagnoseExerciseResponse(exercise,[pain1,pain2],recovery);
assert.equal(poorFit.state,'poor_fit');
assert.equal(poorFit.intervention.action,'replace_exercise');

// Recency applies after filtering. A context-limited day does not evict an older useful comparable exposure.
const c1=exposure(10), c2=exposure(11), c3=exposure(12), c4=exposure(13), contextLimited=exposure(14,{reps:4,rir:0},{outcome:'context_limited',reasonCode:'readiness_limited_exposure'});
const window = longitudinalEvidenceWindow([c1,c2,c3,c4,contextLimited],4);
assert.equal(window.includedExposureCount,4);
assert.equal(window.excludedExposureCount,1);
assert.equal(window.olderComparableExposureCount,0);
assert.equal(window.exposures[0].completedAt,c1.completedAt);

// Stale failures outside the last four comparable exposures no longer dominate current response.
const oldFail1=exposure(20,{reps:5,rir:0},{outcome:'failure',reasonCode:'rep_floor_miss'});
const oldFail2=exposure(21,{reps:5,rir:0},{outcome:'failure',reasonCode:'rep_floor_miss'});
const stable=[22,23,24,25].map(i=>exposure(i,{reps:10,rir:2}));
const recoveredTrend = diagnoseExerciseResponse(exercise,[oldFail1,oldFail2,...stable],recovery);
assert.notEqual(recoveredTrend.state,'fatigue_limited');
assert.equal(recoveredTrend.evidence.included,4);
assert.equal(recoveredTrend.evidence.olderComparable,2);

// Three comparable stable exposures can support a cautious one-set experiment; one exposure never can.
const stableThree=[30,31,32].map(i=>exposure(i,{reps:10,rir:2,load:50}));
const underStim = diagnoseExerciseResponse(exercise,stableThree,recovery);
assert.equal(underStim.state,'possibly_understimulated');
assert.equal(underStim.intervention.action,'add_set');
assert.equal(underStim.evidenceCount,3);
const oneStable = diagnoseExerciseResponse(exercise,[stableThree[0]],recovery);
assert.equal(oneStable.intervention.structural,false);
assert.equal(oneStable.intervention.action,'collect_more_data');

console.log('PASS M184 longitudinal evidence: contextual/edit/interruption exclusions, repeated-evidence threshold, stale-evidence decay, cautious adaptation.');
