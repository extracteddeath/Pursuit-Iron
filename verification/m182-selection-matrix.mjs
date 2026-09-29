import assert from 'node:assert/strict';
import { generateProgram } from '../modules/next-engine/generate.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { evaluateObjectiveCoachGuardrails } from '../modules/next-engine/coach-regression.js';
import { optimizeSetupAwareSessionSequence } from '../modules/next-engine/realizer.js';
import { sessionSetupTransitionScore } from '../modules/next-engine/setup-economy.js';

const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading = {
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500},
  cable:{minimum:5,increment:5,maximum:300},
  smith:{minimum:5,increment:5,maximum:500},
  exerciseOverrides:{}
};
const weekdays = ['monday','tuesday','thursday','friday','sunday'];
const cases = [
  {id:'intermediate-hypertrophy-60',experience:'intermediate',goal:'hypertrophy',min:60,max:90,target:7,seed:18201},
  {id:'advanced-hypertrophy-90',experience:'advanced',goal:'hypertrophy',min:90,max:120,target:9,seed:18202},
  {id:'intermediate-mixed-60',experience:'intermediate',goal:'mixed',min:60,max:90,target:7,seed:18203},
  {id:'advanced-mixed-90',experience:'advanced',goal:'mixed',min:90,max:120,target:9,seed:18204}
];

function requestFor(row) {
  return {
    athlete:{experience:row.experience},
    goal:{type:row.goal,musclePriorities:{},liftPriorities:{}},
    schedule:{days:weekdays.map(day=>({day,minMinutes:row.min,maxMinutes:row.max,targetExercises:row.target}))},
    equipment:{available:equipment,bodyweight:'allow',loading},
    restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
    preferences:{preferredSplit:'ulppl',lockedSplit:'ulppl',avoidedExercises:[]},
    customExercises:[],
    seed:row.seed
  };
}

for (const row of cases) {
  const input = requestFor(row);
  const normalized = normalizeRequest(input);
  const { program } = generateProgram(input);
  assert.equal(program.audit.result,'pass',`${row.id} engine audit must pass`);
  const guardrails = evaluateObjectiveCoachGuardrails(program,normalized);
  const redundancy = guardrails.filter(f=>['REDUNDANT_COMPOUND_OVERLAP','REDUNDANT_SEMANTIC_OVERLAP','REDUNDANT_ACCESSORY_FAMILY'].includes(f.code));
  assert.deepEqual(redundancy,[],`${row.id} contains coach-detectable redundant exercise selection: ${redundancy.map(x=>x.detail).join(' | ')}`);

  const map = createExerciseMap(normalized.customExercises);
  const setupRegressions = [];
  for (const session of program.sessions) {
    const current = sessionSetupTransitionScore(session.exercises,map);
    const optimizedSession = optimizeSetupAwareSessionSequence(session,map,normalized);
    const optimized = sessionSetupTransitionScore(optimizedSession.exercises,map);
    if (optimized + .001 < current) setupRegressions.push({day:session.day,current,optimized});
  }
  assert.deepEqual(setupRegressions,[],`${row.id} finalization discarded setup-aware ordering: ${JSON.stringify(setupRegressions)}`);
  console.log(`PASS ${row.id}: ${program.sessions.length} sessions, no redundant-selection guardrails, final setup ordering retained.`);
}

console.log(`PASS M182 generated selection matrix (${cases.length} representative profiles).`);
