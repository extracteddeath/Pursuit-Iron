import assert from 'node:assert/strict';
import { withProgressionChoices, repeatedNote, refreshProgressionSets, exerciseSessionTags } from '../modules/session-tools.js';
import { mergeStores, loggedExercisePerformance, userOwnsRuntimeSet } from '../modules/engine-api.js';
const base={id:'p',progStyle:{bench:'double'},progStyleLifter:{bench:true}};
const next=withProgressionChoices(base,{bench:'dynamic',curl:'ladder'});
assert.equal(next.progStyle.bench,'dynamic'); assert.equal(base.progStyle.bench,'double');
assert.equal(next.progStyleLifter.curl,true);
assert.equal(withProgressionChoices(next,{bench:'auto'}).progStyle.bench,undefined);
assert.deepEqual(repeatedNote('Try straps','once'),{text:'Try straps',repeat:'none'});
assert.deepEqual(repeatedNote('Try straps','none'),{text:'',repeat:'none'});
assert.deepEqual(repeatedNote('Cue','always'),{text:'Cue',repeat:'always'});
const rows=[{done:true,weight:'100',reps:'8'}, {weight:'105',reps:'7',auto:false,valueOwner:'user'},
    {weight:'100',reps:'8',auto:true,valueOwner:'prescription'}, {warm:true,weight:'50'}, {sub:true,weight:'70'}];
const fresh=[0,1,2].map(()=>({weight:'110',reps:'6',target:{w:'110',reps:'6'}}));
const refreshed=refreshProgressionSets(rows,fresh,userOwnsRuntimeSet);
assert.equal(refreshed[0],rows[0]); assert.equal(refreshed[1],rows[1]);
assert.equal(refreshed[2].weight,'110'); assert.equal(refreshed[3],rows[3]); assert.equal(refreshed[4],rows[4]);
assert.equal(exerciseSessionTags({type:'compound'},{slot:0},{primaryIndex:0},{})[0],'Key lift');
assert.equal(exerciseSessionTags({type:'compound'},{slot:1},{primaryIndex:0},{})[0],'Supporting lift');
assert.equal(exerciseSessionTags({type:'isolation'},{slot:1},{primaryIndex:0},{})[0],'Accessory lift');
const merged=mergeStores({v:13,savedAt:1,injuryLog:[{id:'a',part:'quads',at:1}]},
 {v:13,savedAt:3,injuryLog:[{id:'a',part:'quads',at:1,resolvedAt:2,updatedAt:2},{id:'b',part:'biceps',at:3}]}).data;
assert.equal(merged.injuryLog.length,2); assert.equal(merged.injuryLog.find(i=>i.id==='a').resolvedAt,2);
console.log('PASS B888 choices: immutable progression, protected set edits, note expiry, lift roles and injury merge');

const { resizeWorkingSets } = await import('../modules/workout-context.js');
const { workoutProgramChanges, withWorkoutProgramChanges } = await import('../modules/session-tools.js');
const { computeCell } = await import('../modules/App.js');
const { currentProgram } = await import('./m215-cycle-duration-fixture.mjs');
const logged={done:true,reps:'8',weight:'40'},warm={warm:true,done:true},extension={sub:true,done:true};
const row={id:'reverse-curl',added:true,sets:[warm,logged,extension,{done:false,reps:'8',weight:'40'},{done:false,reps:'8',weight:'40'}]};
const smaller=resizeWorkingSets(row,1);
assert.deepEqual(smaller.sets,[warm,logged,extension]);assert.equal(row.sets.length,5);
assert.equal(resizeWorkingSets(row,0),row);assert.equal(resizeWorkingSets(row,21),row);
const bigger=resizeWorkingSets(smaller,4);assert.equal(bigger.sets.filter(s=>!s.warm&&!s.sub).length,4);
assert.equal(bigger.sets[1],logged);assert.ok(bigger.sets.slice(3).every(s=>!s.done&&s.valueOwner==='user'));
const change=workoutProgramChanges([row],currentProgram.days[0],()=>({sets:3}));
assert.equal(change.length,1);assert.equal(change[0].sets,3);assert.equal(change[0].added,true);
assert.deepEqual(workoutProgramChanges([{...row,injuryAdjusted:'biceps'}],currentProgram.days[0],()=>({sets:3})),[]);
const changes=[{...change[0],sets:'4'}];
const applied=withWorkoutProgramChanges(currentProgram,currentProgram.days[0].id,changes);
assert.equal(currentProgram.days[0].exercises.length,3);
assert.deepEqual(applied.days[0].exercises,[...currentProgram.days[0].exercises,'reverse-curl']);
for(const week of [1,5,10])assert.equal(computeCell(applied,applied.days[0],'reverse-curl',3,week).sets,4);
assert.deepEqual(withWorkoutProgramChanges(applied,applied.days[0].id,changes).days,applied.days,'retry is idempotent');
assert.throws(()=>withWorkoutProgramChanges(applied,applied.days[0].id,[{...changes[0],sets:''}]));
const { generateNextCycleForShell }=await import('../modules/next-engine/cycle-runtime-adapter.js');
const { EXERCISES }=await import('../modules/App.js');
const fs=await import('node:fs');
const cfg=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url))).rows[3].config;
let serial=0;const generated=generateNextCycleForShell({templateId:'powerbuilding',config:cfg,legacyExercises:EXERCISES,seed:903,makeId:()=>`b903-${++serial}`});
const gp=generated.allBlocks?.[0]||generated.blocks[0],gd=gp.days[0];
const addedId=['reverse-curl','inc-curl','lat-raise'].find(id=>!gd.exercises.includes(id));
const ga=withWorkoutProgramChanges(gp,gd.id,[{exerciseId:addedId,sets:2,added:true}]);
assert.equal(computeCell(ga,ga.days[0],addedId,gd.exercises.length,1).sets,2);
assert.equal(computeCell(ga,ga.days[0],addedId,gd.exercises.length,gp.config.weeks).sets,2);
console.log('PASS B903: protected logged work, custom/generated future set counts, explicit ownership and idempotent program edits');

assert.deepEqual(workoutProgramChanges([{id:'bb-bench',sets:Array.from({length:5},()=>({}))}],currentProgram.days[0],()=>({sets:3})),[],'automatic dose changes are not proposed as authored edits');
assert.equal(workoutProgramChanges([{id:'bb-bench',setCountEdited:true,sets:[{},{}]}],currentProgram.days[0],()=>({sets:3}))[0].sets,2);

// M330: set-count review follows the saved weekly exercise and stable slot.
const pairedDay={id:'paired',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']};
const pairedProgram={...currentProgram,id:'m330',days:[pairedDay],pairs:{'paired:0':'db-bench'},
    overrides:{'paired:0':{sets:3,reps:'6-8',rir:2}},weeks:4,config:{...currentProgram.config,weeks:4,deload:false}};
const pairedRow={id:'db-bench',slot:0,setCountEdited:true,sets:[{done:true,weight:'50',reps:'8'},{},{},{}]};
const inputBefore=JSON.stringify({pairedProgram,pairedRow});
const proposed=workoutProgramChanges([pairedRow],pairedDay,()=>({sets:3}),pairedProgram,2);
assert.equal(proposed.length,1);assert.equal(proposed[0].slot,0);assert.equal(proposed[0].weekIndex,2);
assert.deepEqual(proposed[0].slotExerciseIds,['bb-bench','db-bench']);
const pairedUpdated=withWorkoutProgramChanges(pairedProgram,pairedDay.id,JSON.parse(JSON.stringify(proposed)));
assert.deepEqual(pairedUpdated.days,pairedProgram.days,'alternate lift is never appended as a duplicate');
assert.deepEqual(pairedUpdated.pairs,pairedProgram.pairs);
for(const week of [1,2,3,4]){
    const id=week%2===0?'db-bench':'bb-bench';
    assert.equal(computeCell(pairedUpdated,pairedDay,id,0,week).sets,4,'shared slot applies in every week');
}
assert.equal(pairedUpdated.overrides['paired:1'],pairedProgram.overrides['paired:1']);
assert.deepEqual(withWorkoutProgramChanges(pairedUpdated,pairedDay.id,proposed).overrides,pairedUpdated.overrides,'repeat application is idempotent');
assert.equal(JSON.stringify({pairedProgram,pairedRow}),inputBefore,'proposal and application preserve source/history inputs');
assert.deepEqual(workoutProgramChanges([{...pairedRow,id:'bb-row',exerciseSubstituted:true}],pairedDay,()=>({sets:3}),pairedProgram,2),[],
    'session substitution cannot silently edit the authored slot');
assert.equal(workoutProgramChanges([{...pairedRow,exerciseSubstituted:true}],pairedDay,()=>({sets:3}),pairedProgram,2).length,1,
    'a program-scope replacement already in the saved roster can still update its set count');
assert.deepEqual(workoutProgramChanges([{...pairedRow,id:'bb-row'}],pairedDay,()=>({sets:3}),pairedProgram,2),[]);
assert.throws(()=>withWorkoutProgramChanges({...pairedProgram,pairs:{'paired:0':'db-incline'}},pairedDay.id,proposed),/slot changed/);
assert.throws(()=>withWorkoutProgramChanges({...pairedProgram,days:[{...pairedDay,exercises:['inc-curl','bb-bench']}]},pairedDay.id,proposed),/slot changed/);
assert.throws(()=>withWorkoutProgramChanges(pairedProgram,pairedDay.id,[{...proposed[0],slot:-1}]),/slot changed/);
// Pending reviews saved before M330 can safely resolve a unique alternate exercise.
const oldChange={exerciseId:'db-bench',sets:4,added:false,previousSets:3};
assert.equal(withWorkoutProgramChanges(pairedProgram,pairedDay.id,[oldChange]).overrides['paired:0'].sets,4);
assert.throws(()=>withWorkoutProgramChanges({...pairedProgram,pairs:{'paired:0':'db-bench','paired:1':'db-bench'}},pairedDay.id,[oldChange]),/more than one/);
console.log('PASS M330: alternate-week set review, shared-slot scope, immutable inputs, stale/ambiguous identity refusal and legacy pending reviews.');

// M331: pending review must not replace a later method or draft set-count edit.
const { workoutProgressionOrigin, withWorkoutProgramReview } = await import('../modules/session-tools.js');
const original={...pairedProgram,progStyle:{'db-bench':'double'},progStyleLifter:{'db-bench':true}};
const origin=workoutProgressionOrigin(original,'db-bench');
const review={id:'m331-review',programId:original.id,dayId:pairedDay.id,
    progressionChanges:{'db-bench':'dynamic'},progressionOrigins:{'db-bench':origin}};
const evidence=JSON.stringify({original,review});
const changedMethod=withProgressionChoices(original,{'db-bench':'linear'});
assert.equal(withProgressionChoices(changedMethod,review.progressionChanges).progStyle['db-bench'],'dynamic',
    'reproduce the previous silent overwrite of a later manual method');
assert.throws(()=>withWorkoutProgramReview(changedMethod,review),/progression or workout slots changed/);
const reviewed=withWorkoutProgramReview(original,JSON.parse(JSON.stringify(review)));
assert.equal(reviewed.progStyle['db-bench'],'dynamic');assert.equal(reviewed.progStyleLifter['db-bench'],true);
assert.deepEqual(withWorkoutProgramReview(reviewed,review),reviewed,'already-applied choices are idempotent');
const unrelatedEdit={...original,name:'Renamed during review',updatedAt:123,
    overrides:{...original.overrides,'paired:1':{reps:'10-15',sets:5}}};
const preserved=withWorkoutProgramReview(unrelatedEdit,review);
assert.equal(preserved.name,unrelatedEdit.name);assert.deepEqual(preserved.overrides,unrelatedEdit.overrides);
assert.throws(()=>withWorkoutProgramReview({...original,pairs:{'paired:0':'bb-row'}},review),/no longer in the program/);
assert.throws(()=>withWorkoutProgramReview({...original,config:{...original.config,progression:'linear'}},review),/progression or workout slots changed/);
assert.throws(()=>withWorkoutProgramReview({...original,config:{...original.config,percentScheme:'531'}},review),/percentage stages/);
assert.throws(()=>withWorkoutProgramReview({...original,id:'other'},review),/no longer the one reviewed/);
assert.throws(()=>withWorkoutProgramReview(original,{...review,progressionOrigins:undefined}),/older review/);
assert.equal(withWorkoutProgramReview(reviewed,{...review,progressionOrigins:undefined}).progStyle['db-bench'],'dynamic',
    'old already-applied choices remain safe no-ops');
const autoReview={...review,progressionChanges:{'db-bench':'auto'}};
assert.equal(withWorkoutProgramReview(original,autoReview).progStyle['db-bench'],undefined);
assert.throws(()=>withWorkoutProgramReview(original,{...review,progressionChanges:{'db-bench':'unknown'}}),/unavailable/);
const additions={...original,days:[...original.days,{id:'second',exercises:['db-bench'],primaryIndex:0}]};
assert.throws(()=>withWorkoutProgramReview(additions,review),/workout slots changed/);
assert.deepEqual(workoutProgressionOrigin(additions,'db-bench'),workoutProgressionOrigin({...additions,days:[...additions.days].reverse()},'db-bench'),
    'reordering whole days does not create a method conflict');
const mixed={...review,programExerciseChanges:proposed};
const draftCountEdit={...original,overrides:{...original.overrides,'paired:0':{...original.overrides['paired:0'],sets:5}}};
assert.throws(()=>withWorkoutProgramReview(draftCountEdit,mixed),/set count changed/);
assert.equal(draftCountEdit.progStyle['db-bench'],'double','failed combined proposal has no partial method mutation');
const sameCount={...original,overrides:{...original.overrides,'paired:0':{...original.overrides['paired:0'],sets:4}}};
assert.equal(withWorkoutProgramReview(sameCount,mixed).overrides['paired:0'].sets,4,'an already-applied count remains safe');
const addedReview={programId:original.id,dayId:pairedDay.id,progressionChanges:{'reverse-curl':'ladder'},
    progressionOrigins:{'reverse-curl':workoutProgressionOrigin(original,'reverse-curl')},
    programExerciseChanges:[{exerciseId:'reverse-curl',sets:2,added:true}]};
const withAdded=withWorkoutProgramReview(original,addedReview);
assert.equal(withAdded.progStyle['reverse-curl'],'ladder');
assert.equal(withAdded.days[0].exercises.at(-1),'reverse-curl');
assert.throws(()=>withWorkoutProgramReview(original,{...addedReview,programExerciseChanges:[]}),/no longer in the program/);
assert.equal(JSON.stringify({original,review}),evidence,'review, source plan and evidence remain immutable');
console.log('PASS M331: method provenance, conflicting draft counts, global exercise scope, immutable/idempotent review, legacy restraint and added-lift ownership.');
