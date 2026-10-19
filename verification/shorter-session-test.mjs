import assert from 'node:assert/strict';
import { planShorterSession, computeCell, exerciseAt, EX_BY_ID, EXERCISES, generateNextProgramForShell,
    prescribeSets, loggedWorkoutPerformance, progressionExposureContext, estimateMinutesFor, TEMPLATES,
    templateConfig, percentagePlanFor, analyzeShellHistoryForNextEngine } from '../modules/engine-api.js';
const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
const custom = { id:'short-custom', custom:true, weeks:6, config:{unit:'lb', goal:'both', progression:'double', focus:{}},
    days:[{ id:'day', label:'Full body', primaryIndex:0, exercises:['bb-bench','back-squat','inc-curl','cable-fly','lat-raise','pushdown'] }],
    overrides:Object.fromEntries(Array.from({length:6},(_,i)=>[`day:${i}`,{sets:5,reps:i<2?'5-8':'10-15',rir:'2',rest:i<2?180:90,tech:i>1?'Myo-reps':null}])) };
const original = JSON.stringify(custom), day = custom.days[0];
assert.throws(()=>planShorterSession(custom,day,1,10),RangeError);
const full=planShorterSession(custom,day,1,null);
assert.equal(full.changed,false);
let previous=full;
for (const minutes of [45,30,20]) {
    const plan=planShorterSession(custom,day,1,minutes), d={...day,sessionLengthPlan:plan};
    assert.deepEqual(plan,planShorterSession(custom,day,1,minutes),'deterministic');
    assert.ok(plan.estimatedMinutes<=previous.estimatedMinutes);
    for (const [slot,cut] of Object.entries(plan.slots)) {
        assert.ok(cut.sets<=previous.slots[slot].sets,'less time cannot add work');
        const base=computeCell(custom,day,cut.exerciseId,+slot,1), cell=computeCell(custom,d,cut.exerciseId,+slot,1);
        assert.equal(cell.sets,cut.sets);assert.equal(cell.reps,base.reps);assert.equal(cell.rir,base.rir);assert.equal(cell.rest,base.rest);
        assert.ok(cut.sets===0||cut.sets>=2);
    }
    assert.ok(plan.slots[0].sets>=2,'key exposure kept');assert.ok(plan.slots[1].sets>=2,'complementary compound kept');
    assert.equal(plan.fits,plan.estimatedMinutes<=minutes);
    assert.equal(estimateMinutesFor(custom,d,1,{factor:1.6}),plan.estimatedMinutes,'pace charged once');
    previous=plan;
}
assert.equal(JSON.stringify(custom),original,'saved program and source roster untouched');
const short=planShorterSession(custom,day,1,30), shortDay={...day,sessionLengthPlan:short};
const data=day.exercises.flatMap((id,slot)=>short.slots[slot].sets?[{id,slot,sets:prescribeSets(custom,shortDay,EX_BY_ID[id],slot,1,'lb',null,{}, {}, [],true)}]:[]);
for (const row of data) {
    assert.equal(row.sets.filter(s=>!s.warm&&!s.sub).length,short.slots[row.slot].sets);
    assert.deepEqual(row.sets.filter(s=>s.warm),prescribeSets(custom,day,EX_BY_ID[row.id],row.slot,1,'lb',null,{}, {}, [],true).filter(s=>s.warm),'warmups unchanged');
    row.sets=row.sets.map(s=>({...s,done:!s.warm,weight:'100',reps:'10',actualRIR:2}));
}
const logged=loggedWorkoutPerformance(custom,shortDay,data,1,'lb',{},[]);
for (const row of data) {
    assert.equal(logged[row.id].prescription.sets,short.slots[row.slot].sets);
    assert.equal(progressionExposureContext([logged[row.id]]).nonComparable,true,'reduced exposure cannot earn a full-session progression or failure');
}
const slower=planShorterSession(custom,day,1,30,{paceFactor:1.6,restScale:2});
assert.ok(Object.values(slower.slots).reduce((n,r)=>n+r.sets,0)<=Object.values(short.slots).reduce((n,r)=>n+r.sets,0));
let checked=0;
for (const goal of ['hypertrophy','strength','both']) for (const split of ['fullbody','upper_lower','ppl']) {
    const config={name:'Short-session matrix',unit:'lb',goal,experience:'intermediate',split,days:split==='fullbody'?3:split==='ppl'?6:4,
        session:'s90',weeks:6,progression:'double',deload:true,equipment,focus:{},reduce:[],barbellCap:3,noBodyweight:false,noSupersets:false};
    const result=generateNextProgramForShell({config,legacyExercises:EXERCISES,seed:812,makeId:()=>`short-${goal}-${split}`});
    assert.ok(result.program,result.reason);
    const program=result.program, snapshot=JSON.stringify(program);
    for (const d of program.days) for (const week of [1,6,7]) {
        let prev=planShorterSession(program,d,week,null);
        for (const limit of [45,30,20]) {
            const plan=planShorterSession(program,d,week,limit);
            assert.ok(plan.estimatedMinutes<=prev.estimatedMinutes);
            for (const [slot,cut] of Object.entries(plan.slots)) {
                const cell=computeCell(program,d,cut.exerciseId,+slot,week);
                assert.ok(cut.sets<=prev.slots[slot].sets);
                if (['primary_strength','secondary_strength'].includes(cell.role)||cell.setTargets?.length) assert.equal(cut.sets,cell.sets,'strength protocol intact');
            }
            prev=plan;checked++;
        }
    }
    const d=program.days[0], plan=planShorterSession(program,d,1,20), sd={...d,sessionLengthPlan:plan};
    const rows=d.exercises.flatMap((_,slot)=>{const id=exerciseAt(program,d,slot,1);return plan.slots[slot]?.sets?[{id,slot,sets:prescribeSets(program,sd,EX_BY_ID[id],slot,1,'lb',null,{}, {},[],true).map(s=>({...s,done:!s.warm,weight:'100',reps:'10',actualRIR:2}))}]:[];});
    const perf=loggedWorkoutPerformance(program,sd,rows,1,'lb',{},[]);
    for(const r of rows) assert.equal(perf[r.id].prescription.sets,plan.slots[r.slot].sets,'canonical snapshot records shortened dose');
    const entry={id:'short-log',programId:program.id,dayId:d.id,date:Date.now(),unit:'lb',weekIndex:1,perf,sessionLengthPlan:plan,prescriptionEdited:plan.changed,timeLimited:plan.changed};
    const analysis=analyzeShellHistoryForNextEngine(program,[entry],EXERCISES);
    assert.ok(analysis,'shortened logs survive engine replay');
    assert.equal(analysis.workouts[0].session.exercises.length,rows.length,'omitted work is not planned failure');
    if(plan.changed) for(const decision of analysis.workouts[0].progression) {
        assert.equal(decision.reasonCode,'time_limited_session');assert.equal(decision.action,'hold');
    }
    assert.equal(JSON.stringify(program),snapshot);
}
for(const template of TEMPLATES.filter(t=>['p531','gzclp','nsuns'].includes(t.id))) {
    const config={...templateConfig(template,equipment),unit:'lb'};
    const result=generateNextProgramForShell({config,legacyExercises:EXERCISES,seed:7,makeId:()=>`pct-${template.id}`});
    if(!result.program) continue;
    const p=result.program;p.trainingMax=Object.fromEntries(p.days.flatMap(d=>d.exercises.map(id=>[id,200])));
    for(const d of p.days) for(const week of [1,3,6]) {
        const plan=planShorterSession(p,d,week,20),sd={...d,sessionLengthPlan:plan};
        for(const [slot,cut] of Object.entries(plan.slots)) {
            const ex=EX_BY_ID[cut.exerciseId],base=percentagePlanFor(p,d,ex,+slot,week,'lb',[]);
            if(base) assert.deepEqual(percentagePlanFor(p,sd,ex,+slot,week,'lb',[]),base,'literal weights, AMRAP and tier protocol intact');
        }
    }
}
console.log(`PASS shorter sessions: ${checked} generated session/week/budget cases, custom logging, warmups, timing, dose provenance and percentage protocols`);
