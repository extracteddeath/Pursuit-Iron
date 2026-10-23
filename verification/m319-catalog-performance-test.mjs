import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';
import { EXERCISES as BUILTINS, EXERCISE_MAP, createExerciseCatalog, createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { createEngineContext } from '../modules/next-engine/engine-context.js';
import { EXERCISES as SHELL_EXERCISES } from '../modules/App.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { config } from './m265-generation-fixture.mjs';

// M319: the fast catalog helpers must still return independent mutable
// containers with the EXACT old merge semantics. Shared built-in definition
// objects remain immutable by contract, while custom metadata must not leak.
const baselineNames=BUILTINS.map(x=>x.id);
const expectedBuiltin=new Map(BUILTINS.map(x=>[x.id,x]));
const emptyA=createExerciseCatalog();
const emptyB=createExerciseCatalog();
assert.notEqual(emptyA,emptyB,'Catalog calls must not share an editable array');
assert.deepEqual(emptyA,BUILTINS);
emptyA.pop();
assert.deepEqual(emptyB.map(x=>x.id),baselineNames);
const mapA=createExerciseMap(),mapB=createExerciseMap();
assert.notEqual(mapA,mapB,'Lookup maps are independently editable');
assert.deepEqual(mapA,expectedBuiltin);
mapA.clear();
assert.deepEqual(mapB,expectedBuiltin);
assert.deepEqual(EXERCISE_MAP,expectedBuiltin,'The global lookup must never be mutated');
const base=BUILTINS.find(x=>x.id==='barbell_bench');
assert.ok(base,'Test must verify a real built-in exercise');
const custom={...base,id:'m319_custom_press',name:'Custom Press for M319',
 source:'external',metadataConfidence:undefined};
const collision={...base,name:'Attempt to shadow barbell bench'};
const input=[custom,collision,custom,{id:null,name:'No id'}];
const expected=[...BUILTINS,{...custom,source:'custom',metadataConfidence:'low'}];
const merged=createExerciseCatalog(input);
assert.deepEqual(merged,expected,'Built-in precedence, stable order and duplicate-ID filtering cannot change');
assert.deepEqual(createExerciseMap(input),new Map(expected.map(e=>[e.id,e])),
 'Direct map assembly must be exactly equivalent to the historical catalog-derived map');
merged.pop();
assert.equal(createExerciseMap(input).get(custom.id).name,custom.name);
assert.equal(createExerciseMap(input).get('barbell_bench'),base);
assert.equal(EXERCISE_MAP.has(custom.id),false,'Custom IDs must not enter the global registry');
const generated=generateNextProgramForShell({
 config:{...config,goal:'both',weeks:4},legacyExercises:SHELL_EXERCISES,
 seed:31901,makeId:()=> 'm319-control'
});
assert.equal(generated.nextProgram.audit.result,'pass');
const context=createEngineContext({...generated.request,customExercises:[custom]});
assert.equal(context.exerciseById(custom.id).name,custom.name);
assert.ok(context.candidatesForMuscle('chest').some(e=>e.id===custom.id),
 'Custom eligible candidate must be reachable from indexed generation context');
assert.equal(context.exerciseMap.size,BUILTINS.length+1);
assert.equal(context.exerciseCatalog.length,BUILTINS.length+1);
assert.equal(createEngineContext({...generated.request,customExercises:[]}).exerciseMap.has(custom.id),false);
const measure=(factory,n)=>{
 const start=performance.now();
 for(let i=0;i<n;i++) factory();
 return Math.round((performance.now()-start)*10)/10;
};
const runs=100;
const baseline=measure(()=>new Map(createExerciseCatalog().map(e=>[e.id,e])),runs);
const optimized=measure(()=>createExerciseMap(),runs);
const contextMs=measure(()=>createEngineContext(generated.request),20);
console.log('M319 lookup creation diagnostic (Node, non-gating): '+
 JSON.stringify({builtinExercises:BUILTINS.length,iterations:runs,
 baselineRebuildMs:baseline,optimizedMapMs:optimized,
 context20CallsMs:contextMs}));
console.log('PASS M319: independent catalogs/maps, custom precedence, isolation and engine-candidate indexing survive reduced catalog work.');
