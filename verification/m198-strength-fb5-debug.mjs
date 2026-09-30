import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { shellConfigToNextRequest } from '../modules/next-engine/app-shell-adapter.js';
import { firstPassingCapacityProgram } from '../modules/next-engine/capacity-generation.js';
import { blocksForCycleTemplate, goalForCycleTemplate } from '../modules/next-engine/simulation.js';
import { normalizeRequest, createMusclePrescriptions } from '../modules/next-engine/prescription.js';
import { createEngineContext } from '../modules/next-engine/engine-context.js';
import { avoidableExerciseOverlap } from '../modules/next-engine/exercise-economy.js';

const fullEquipment=['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const legacyExercises=[...EXERCISE_MAP.values()].map(def=>({
  id:def.id,name:def.name,equip:Array.isArray(def.equipment)?[...def.equipment]:[],
  part:Object.entries(def.muscles??{}).sort((a,b)=>(b[1]?.credit??0)-(a[1]?.credit??0))[0]?.[0]??'other',
  type:def.flags?.compound?'compound':'isolation',pattern:def.movementFamily
}));
const config={name:'M198 FB5 deep debug',experience:'intermediate',goal:'both',split:'full_body',days:5,session:'s90',weeks:6,equipment:fullEquipment,focus:{},focusList:[],reduce:[],progression:'auto',progressionStyle:'auto',deload:true,barbellCap:3,percentScheme:null,volumeApproach:'standard',noSupersets:false,noBodyweight:false,unit:'lb'};
const seed=19905;
const specs=blocksForCycleTemplate('powerbuilding');
const goal=goalForCycleTemplate('powerbuilding');
let request=shellConfigToNextRequest({...config,goal:goal==='mixed'?'both':goal},[],legacyExercises,seed);
request.goal={...request.goal,type:goal};
const entry=firstPassingCapacityProgram(request,config,{phase:specs[0].phase,blockWeeks:specs[0].weeks,progressionStyle:request.preferences?.progressionStyle});
request=normalizeRequest(entry.request);
const strength=firstPassingCapacityProgram(request,config,{phase:specs[1].phase,blockWeeks:specs[1].weeks,progressionStyle:request.preferences?.progressionStyle});
const p=strength.result.program;
const context=createEngineContext(strength.request);
const map=context.exerciseMap;
const primary=def=>Object.entries(def?.muscles??{}).find(([,c])=>c.role==='primary')?.[0]??'';
const region=def=>['horizontal_press','vertical_press','chest_adduction'].includes(def?.movementFamily)?'push':['horizontal_pull','vertical_pull','shoulder_extension'].includes(def?.movementFamily)?'pull':['squat','leg_press','knee_extension','hip_hinge','hip_extension','knee_flexion'].includes(def?.movementFamily)?'lower':'other';
console.log('ENTRY', {adjusted:entry.adjusted, requestedTarget:entry.requestedTarget,effectiveTarget:entry.effectiveTarget,requestedMin:entry.requestedMinimumMinutes,effectiveMin:entry.effectiveMinimumMinutes,audit:entry.result.program.audit.result});
console.log('STRENGTH', {adjusted:strength.adjusted,requestedTarget:strength.requestedTarget,effectiveTarget:strength.effectiveTarget,requestedMin:strength.requestedMinimumMinutes,effectiveMin:strength.effectiveMinimumMinutes,audit:p.audit.result,findings:p.audit.findings});
for(const s of p.sessions){
 console.log('\nSESSION',s.id,s.name,{intent:s.intent,day:s.day,min:s.minMinutes,max:s.maxMinutes,target:s.targetExercises,estimated:s.estimatedMinutes});
 for(const [i,e] of s.exercises.entries()){const d=map.get(e.exerciseId);console.log(i,{id:e.exerciseId,name:e.name,role:e.role,sets:e.sets,family:d?.movementFamily,region:region(d),primary:primary(d),fatigue:d?d.fatigue.systemic+d.fatigue.axial+d.fatigue.lowerBack:null,setup:d?.setupCost});}
}
const bad=p.sessions.find(s=>s.id==='session-3')??p.sessions.find(s=>s.name?.includes('Full Body C'));
if(!bad) process.exit(0);
const chosen=bad.exercises.map(e=>map.get(e.exerciseId)).filter(Boolean);
const barbells=chosen.filter(d=>d.flags.barbell).length;
console.log('\nBAD SESSION SUMMARY',{barbells,maxBarbells:strength.request.restrictions.maxBarbellMovementsPerDay});
console.log('DONORS');
for(const [i,e] of bad.exercises.entries()){
 const d=map.get(e.exerciseId); const pm=primary(d); const priority=pm?(strength.request.goal.musclePriorities[pm]??'normal'):'normal';
 const protectedRole=['primary_strength','secondary_strength','strength_support','specialization'].includes(e.role);
 console.log({i,id:e.exerciseId,name:e.name,role:e.role,sets:e.sets,primary:pm,priority,protectedRole,region:region(d)});
}
console.log('\nPULL CANDIDATES');
const pulls=[...map.values()].filter(d=>['horizontal_pull','vertical_pull','shoulder_extension'].includes(d.movementFamily));
for(const d of pulls){
 const reasons=[];
 if(!context.equipmentEligible(d,bad.day)) reasons.push('equipment');
 if(strength.request.preferences.avoidedExercises?.includes(d.id)) reasons.push('avoided');
 if(chosen.some(c=>c.id===d.id)) reasons.push('duplicate');
 if((d.muscles.back?.credit??0)<=0) reasons.push('no-back-credit');
 const familyCount=chosen.filter(c=>c.movementFamily===d.movementFamily).length;
 if(familyCount>=2) reasons.push('family-cap');
 if(d.flags.barbell&&barbells>=strength.request.restrictions.maxBarbellMovementsPerDay) reasons.push('barbell-cap');
 if(avoidableExerciseOverlap(d,'hypertrophy_compound',chosen.map(def=>({def})),{priority:'normal'})) reasons.push('overlap');
 console.log({id:d.id,name:d.name,family:d.movementFamily,back:d.muscles.back?.credit??0,barbell:!!d.flags.barbell,reasons});
}
const rx=createMusclePrescriptions(strength.request,specs[1].phase).find(x=>x.muscle==='back');
console.log('\nBACK PRESCRIPTION',rx);
