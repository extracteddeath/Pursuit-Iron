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
