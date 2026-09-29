import assert from 'node:assert/strict';
import { generateProgram } from '../modules/next-engine/generate.js';
import { evaluateProgramCoachQuality } from '../modules/next-engine/coach-quality-oracle.js';
import { runPowerbuildingSimulation, blocksForCycleTemplate } from '../modules/next-engine/simulation.js';

const fullGym=['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading={
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500},
  cable:{minimum:5,increment:5,maximum:300},
  smith:{minimum:5,increment:5,maximum:500},
  exerciseOverrides:{}
};
const weekdays=['monday','tuesday','wednesday','thursday','friday','saturday'];

function request({seed,experience='intermediate',goal='mixed',days=4,minutes=75,split='upper_lower',availableEquipment=fullGym,bodyweight='allow',allowSupersets=true,barbellCap=3,musclePriorities={},liftPriorities={}}){
  return {
    athlete:{experience},
    goal:{type:goal,musclePriorities,liftPriorities},
    schedule:{days:weekdays.slice(0,days).map(day=>({day,maxMinutes:minutes}))},
    equipment:{available:availableEquipment,bodyweight,loading},
    restrictions:{maxBarbellMovementsPerDay:barbellCap,allowSupersets},
    preferences:{preferredSplit:split,lockedSplit:split,avoidedExercises:[]},
    customExercises:[],
    seed
  };
}

function validateProgram(program,label){
  assert.equal(program.audit.result,'pass',`${label}: engine audit must pass`);
  assert.ok(program.sessions.length>0,`${label}: no sessions`);
  for(const session of program.sessions){
    assert.ok(session.exercises.length>0,`${label}: ${session.day} has no exercises`);
    assert.ok(session.estimatedMinutes<=session.maxMinutes+0.001,`${label}: ${session.day} exceeds time cap (${session.estimatedMinutes}/${session.maxMinutes})`);
    for(const ex of session.exercises){
      assert.ok(Number.isFinite(ex.sets)&&ex.sets>0,`${label}: ${ex.name} has invalid sets`);
      assert.ok(Array.isArray(ex.prescription?.reps)&&ex.prescription.reps.length===2,`${label}: ${ex.name} missing rep range`);
      assert.ok(ex.prescription.reps[0]>0&&ex.prescription.reps[1]>=ex.prescription.reps[0],`${label}: ${ex.name} invalid reps`);
      assert.ok(Array.isArray(ex.prescription?.rir)&&ex.prescription.rir.length===2,`${label}: ${ex.name} missing RIR range`);
      assert.ok(ex.prescription.rir[0]>=0&&ex.prescription.rir[1]>=ex.prescription.rir[0],`${label}: ${ex.name} invalid RIR`);
    }
  }
}

const broad=[];
const scheduleMatrix=[
  {days:2,minutes:45,split:'full_body'},
  {days:3,minutes:60,split:'full_body'},
  {days:4,minutes:75,split:'upper_lower'},
  {days:5,minutes:90,split:'ulppl'}
];
let seed=188000;
for(const experience of ['novice','intermediate','advanced']){
  for(const goal of ['hypertrophy','strength','mixed']){
    for(const schedule of scheduleMatrix){
      broad.push([`${experience}/${goal}/${schedule.days}d/${schedule.minutes}m/${schedule.split}`,request({seed:++seed,experience,goal,...schedule})]);
    }
  }
}

broad.push(
  ['6d advanced hypertrophy PPL',request({seed:++seed,experience:'advanced',goal:'hypertrophy',days:6,minutes:90,split:'ppl'})],
  ['5d advanced mixed PPLUL high SBD',request({seed:++seed,experience:'advanced',goal:'mixed',days:5,minutes:90,split:'pplul',liftPriorities:{back_squat:'high',bench_press:'high',deadlift:'high'}})],
  ['4d advanced mixed PHUL',request({seed:++seed,experience:'advanced',goal:'mixed',days:4,minutes:90,split:'phul'})],
  ['5d hypertrophy arms priority',request({seed:++seed,goal:'hypertrophy',days:5,minutes:75,split:'ulppl',musclePriorities:{biceps:'high',triceps:'high',side_delts:'high'}})],
  ['4d no supersets',request({seed:++seed,goal:'hypertrophy',days:4,minutes:60,split:'upper_lower',allowSupersets:false})],
  ['4d one barbell movement per day',request({seed:++seed,goal:'mixed',days:4,minutes:75,split:'upper_lower',barbellCap:1})],
  ['3d machines+dumbbells no bodyweight',request({seed:++seed,goal:'hypertrophy',days:3,minutes:60,split:'full_body',availableEquipment:['bench','dumbbell','cable','machine','leg_press'],bodyweight:'exclude'})],
  ['3d home dumbbells',request({seed:++seed,experience:'novice',goal:'hypertrophy',days:3,minutes:45,split:'full_body',availableEquipment:['bench','dumbbell','bodyweight']})]
);

const generationFailures=[];
for(const [label,req] of broad){
  try{
    const generated=generateProgram(req);
    validateProgram(generated.program,label);
    const quality=evaluateProgramCoachQuality(generated.program,req,{audit:generated.program.audit});
    assert.equal(quality.result,'pass',`${label}: coach-quality result ${quality.result}; ${[...quality.hardFailures,...quality.warnings].map(x=>x.code).join(', ')}`);
  }catch(error){
    generationFailures.push({label,error:String(error?.stack??error)});
  }
}
if(generationFailures.length){
  console.error(JSON.stringify(generationFailures,null,2));
  throw new Error(`${generationFailures.length}/${broad.length} broad production-matrix cases failed.`);
}
console.log(`PASS M188 broad generation matrix: ${broad.length}/${broad.length} programs pass engine + coach-quality gates.`);

const powerbuildingReq=request({seed:188901,experience:'advanced',goal:'mixed',days:5,minutes:90,split:'ulppl',liftPriorities:{back_squat:'high',bench_press:'high',deadlift:'high'}});
const strengthReq=request({seed:188902,experience:'advanced',goal:'strength',days:4,minutes:90,split:'upper_lower',liftPriorities:{back_squat:'high',bench_press:'high',deadlift:'high'}});
const foundationReq=request({seed:188903,experience:'novice',goal:'mixed',days:3,minutes:60,split:'full_body'});
const hypertrophyReq=request({seed:188904,experience:'intermediate',goal:'hypertrophy',days:5,minutes:75,split:'ulppl',musclePriorities:{biceps:'high',triceps:'high',side_delts:'high'}});
const longitudinal=[
  ['powerbuilding steady',powerbuildingReq,'powerbuilding','steady'],
  ['powerbuilding fast',powerbuildingReq,'powerbuilding','fast_responder'],
  ['powerbuilding mixed',powerbuildingReq,'powerbuilding','mixed'],
  ['powerbuilding fatigue',powerbuildingReq,'powerbuilding','fatigue_prone'],
  ['strength peak mixed',strengthReq,'strength_peak','mixed'],
  ['strength peak fatigue',strengthReq,'strength_peak','fatigue_prone'],
  ['foundation mixed',foundationReq,'foundation','mixed'],
  ['hypertrophy specialization mixed',hypertrophyReq,'hypertrophy_spec','mixed']
];

const allowedResponse=new Set(['productive','mixed','stalled','fatigue_limited']);
const summaries=[];
for(const [label,req,template,profile] of longitudinal){
  const blocks=blocksForCycleTemplate(template);
  const sim=runPowerbuildingSimulation({request:req,blocks,adaptBetweenBlocks:true,responseProfile:profile});
  assert.ok(sim.totalWeeks>=10,`${label}: longitudinal run too short`);
  assert.equal(sim.blocks.length,blocks.length,`${label}: block count drift`);
  let absoluteWeek=0;
  let sawWeek9=false;
  for(let i=0;i<sim.blocks.length;i++){
    const block=sim.blocks[i];
    assert.equal(block.program.phase,block.spec.phase,`${label}: block ${i+1} phase mismatch`);
    validateProgram(block.program,`${label} block ${i+1}`);
    assert.ok(allowedResponse.has(block.response.classification),`${label}: unknown response classification ${block.response.classification}`);
    if(i>0){
      assert.ok(block.historyCarryover.returningExercises>0,`${label}: block ${i+1} lost all prior training history`);
      const priorStalls=new Set(sim.blocks[i-1].response.stalledExerciseIds);
      const nextIds=new Set(block.program.sessions.flatMap(s=>s.exercises.map(ex=>ex.exerciseId)));
      for(const stalled of priorStalls) assert.equal(nextIds.has(stalled),false,`${label}: stalled exercise ${stalled} resurrected in next adaptive block`);
    }
    for(const week of block.weeks){
      absoluteWeek++;
      assert.ok(week.workouts.length===block.program.sessions.length,`${label}: week ${absoluteWeek} session count drift`);
      assert.ok(week.prescription.weeklySets>0,`${label}: week ${absoluteWeek} has zero work`);
      assert.ok(week.prescription.peakSessionMinutes<=Math.max(...block.program.sessions.map(s=>s.maxMinutes))+0.001,`${label}: week ${absoluteWeek} time overflow`);
      if(absoluteWeek===9){
        sawWeek9=true;
        assert.ok(week.workouts.every(w=>w.prescription.length>0),`${label}: week 9 contains an empty workout`);
      }
    }
  }
  assert.equal(sawWeek9,true,`${label}: week 9 was never exercised`);
  const later=sim.blocks.slice(1);
  assert.ok(later.every(b=>b.metrics.weeklySets>0&&b.metrics.exerciseSlots>0),`${label}: later-block degeneration produced empty dose`);
  summaries.push({label,weeks:sim.totalWeeks,blocks:sim.blocks.length,fingerprint:sim.fingerprint,responses:sim.blocks.map(b=>b.response.classification)});
}

const deterministicA=runPowerbuildingSimulation({request:powerbuildingReq,blocks:blocksForCycleTemplate('powerbuilding'),adaptBetweenBlocks:true,responseProfile:'mixed'});
const deterministicB=runPowerbuildingSimulation({request:powerbuildingReq,blocks:blocksForCycleTemplate('powerbuilding'),adaptBetweenBlocks:true,responseProfile:'mixed'});
assert.equal(deterministicA.fingerprint,deterministicB.fingerprint,'identical longitudinal inputs must be deterministic');
assert.deepEqual(deterministicA.blocks.map(b=>b.metrics),deterministicB.blocks.map(b=>b.metrics),'deterministic replay changed block metrics');

console.log(`PASS M188 longitudinal torture: ${longitudinal.length} multi-block simulations, ${summaries.reduce((n,x)=>n+x.weeks,0)} simulated weeks, week-9/second-block integrity preserved.`);
console.log(JSON.stringify(summaries,null,2));
