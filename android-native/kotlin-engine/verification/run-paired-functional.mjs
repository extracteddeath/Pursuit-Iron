import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runJvmProbe } from './run-jvm-probe.mjs';
import {root,sourcePath,fixturePath,artifactPath,evidencePath,runnerPaths,caseIds,
  sha256,git,scenarioDigest,parseFixture,grade,verifyBundle,verifyRecordedFunctionalEvidence} from './paired-functional.mjs';

const args=process.argv.slice(2);
const value=(flag,fallback)=>{const i=args.indexOf(flag);return i<0?fallback:args[i+1];};
const legacyRoot=resolve(value('--legacy-root','comparison-legacy'));
const output=resolve(root,value('--output','android-native/kotlin-engine/build/reports/paired-functional.json'));
const f=parseFixture(readFileSync(resolve(root,fixturePath)));
assert.equal(git(legacyRoot,'rev-parse','HEAD'),f.legacyCommit);
assert.equal(git(legacyRoot,'status','--porcelain'),'');
assert.equal(git(root,'status','--porcelain','--untracked-files=all','--',sourcePath,...runnerPaths),'',
  'commit policies and comparison sources before measuring');
const commit=git(root,'rev-parse','HEAD');
mkdirSync(dirname(output),{recursive:true});
const nativeOutput=resolve(dirname(output),'paired-functional-native.json');
runJvmProbe({root,args,probe:'PairedFunctionalProbe',task:'runPairedFunctionalProbe',
  input:resolve(root,fixturePath),output:nativeOutput});
const native=JSON.parse(readFileSync(nativeOutput,'utf8'));
assert.equal(native.fixtureDigest,f.fixtureDigest);assert.deepEqual(native.inputProperties,f.properties);
assert.deepEqual(native.results.map(r=>r.caseId),caseIds);
const {evaluateWorkoutProgression}=await import(pathToFileURL(resolve(legacyRoot,'modules/next-engine/performance.js')));
const {availableLoadAtOrBelow,nextAvailableLoad,resolveLoadingMode}=await import(pathToFileURL(resolve(legacyRoot,'modules/next-engine/loading.js')));
const inventory={unit:'lb',barbell:{barWeight:f.barWeight,platePairs:f.platePairs},
  dumbbells:{availablePerHand:f.availableLoads},machine:{availableLoads:f.machineLoads},
  cable:{availableLoads:[]},smith:{availableLoads:[]},exerciseOverrides:{}};
assert.equal(resolveLoadingMode(f.exerciseId,['dumbbell'],inventory),'dumbbell_per_hand');
assert.equal(resolveLoadingMode(f.machineExercise,['machine'],inventory),'machine_stack');
assert.equal(resolveLoadingMode(f.barExercise,['barbell','bench'],inventory),'barbell_total');
const reps=f.repRows.map(input=>{
  const session={id:input.id,exercises:[{exerciseId:f.exerciseId,name:'Dumbbell Curl',role:'accessory',
    sets:f.count,progressionStyle:'double',prescription:{reps:[f.floor,f.ceiling],rir:[f.goalRir,f.goalRir]}}]};
  const performed=input.reps.map((reps,setIndex)=>({exerciseId:f.exerciseId,setIndex,reps,
    load:input.load,rir:input.rir[setIndex]}));
  const context={loadingInventory:structuredClone(inventory),equipmentAvailable:['dumbbell'],history:[]};
  const before=JSON.stringify({session,performed,context});
  const raw=evaluateWorkoutProgression(session,performed,context)[0];
  return {id:input.id,targets:raw.setTargets,raw,mutatedInput:before!==JSON.stringify({session,performed,context})};
});
function equipment(exercise,requested,available) {
  const original=structuredClone(inventory),before=JSON.stringify(original);
  const floor=availableLoadAtOrBelow(exercise,requested,original,available);
  const next=nextAvailableLoad(exercise,floor,original,available);
  return {id:String(requested),requested,floor,next,raw:{floor,next},mutatedInput:before!==JSON.stringify(original)};
}
const results=[{caseId:caseIds[0],subcases:reps},
  {caseId:caseIds[1],subcases:f.machineRequested.map(n=>equipment(f.machineExercise,n,['machine']))},
  {caseId:caseIds[2],subcases:f.barRequested.map(n=>equipment(f.barExercise,n,['barbell','bench']))}]
  .map(r=>({...r,inputDigest:scenarioDigest(f.fixtureDigest,r.caseId)}));
const engines={
  'legacy-javascript':{commit:f.legacyCommit,sourceTree:git(legacyRoot,'rev-parse','HEAD:modules'),
    entryPoint:'evaluateWorkoutProgression + availableLoadAtOrBelow + nextAvailableLoad',results},
  'independent-kotlin':{commit,sourceTree:git(root,'rev-parse',`HEAD:${sourcePath}`),
    entryPoint:'FatigueAwareRepTargetReviewDomain.assess + EquipmentLoadDomain.inspect',results:native.results}};
const measurements=[];
for(const [engine,run] of Object.entries(engines))for(const r of run.results) {
  r.assessment=grade(f,r.caseId,r,2);
  measurements.push({caseId:r.caseId,engine,proof:'headless',outcome:r.assessment.outcome,
    commit:run.commit,inputDigest:r.inputDigest,reproducible:true});
}
const bundle=verifyBundle({schemaVersion:2,kind:'paired-headless-functional-review',
  scope:'Rep rows are observed behavior with data-integrity checks only; the one-set superiority claim is withdrawn. Physical-stock queries are exact feasibility checks. No individualized prediction, training-outcome, app or device certification.',
  fixture:fixturePath,fixtureDigest:f.fixtureDigest,inputProperties:f.properties,
  reproduction:'Run node android-native/kotlin-engine/verification/run-paired-functional.mjs --legacy-root <pinned clean web checkout> using Gradle, or add --kotlinc <Kotlin compiler> with JDK 17.',
  runnerFiles:Object.fromEntries(runnerPaths.map(p=>[p,sha256(readFileSync(resolve(root,p)))])),engines,measurements},f);
const bytes=JSON.stringify(bundle,null,2)+'\n';writeFileSync(output,bytes);
if(args.includes('--record')) {
  throw Error('The historical functional artifact is retained after retraction. New rep-quality evidence needs an independently validated oracle before recording.');
} else if(args.includes('--check-current')) {
  const evidence=JSON.parse(readFileSync(resolve(root,evidencePath),'utf8'));
  const provenance=verifyRecordedFunctionalEvidence(evidence,{allowStaleProvenance:true});
  if(provenance.currentNativeTree) {
    const recorded=JSON.parse(readFileSync(resolve(root,artifactPath),'utf8'));
    for(const engine of Object.keys(engines))assert.deepEqual(engines[engine].results,recorded.engines[engine].results);
  }
}
for(const id of caseIds)console.log(JSON.stringify({caseId:id,
  legacy:engines['legacy-javascript'].results.find(r=>r.caseId===id).assessment,
  kotlin:engines['independent-kotlin'].results.find(r=>r.caseId===id).assessment}));
if(measurements.some(m=>m.engine==='independent-kotlin'&&m.outcome!=='pass'))process.exitCode=1;
