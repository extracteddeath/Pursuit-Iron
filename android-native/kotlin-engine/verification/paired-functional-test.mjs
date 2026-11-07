import assert from 'node:assert/strict';
import test from 'node:test';
// v1 cases authenticate a withdrawn historical archive, not coaching quality.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {root,fixturePath,caseIds,parseFixture,barLoads,grade as assess,verifyBundle,
  scenarioDigest,evidencePath,verifyRecordedFunctionalEvidence} from './paired-functional.mjs';
const bytes=readFileSync(resolve(root,fixturePath)),f=parseFixture(bytes);
const grade=(f,id,result)=>assess(f,id,result,1);
function output(id) {
  if(id===caseIds[0])return {caseId:id,subcases:f.repRows.map(input=>({id:input.id,
    targets:input.reps.map((n,i)=>({weight:30,reps:n+(input.id==='headroom'&&i===1?1:0)})),
    actualRirCount:input.rir.filter(r=>r!==null).length,assumedGoalRirSetCount:input.rir.filter(r=>r===null).length,
    mayAutoApply:false,mutatedInput:false}))};
  const requests=id===caseIds[1]?[[159.5,155,160],[38.5,35,40]]:[[47.5,40,55]];
  return {caseId:id,subcases:requests.map(([requested,floor,next])=>({id:String(requested),requested,floor,next,mutatedInput:false}))};
}
test('fixed shared facts preserve real equipment and missing actual effort',()=>{
  assert.deepEqual(barLoads(f),[35,40,55,60]);
  assert.deepEqual(f.repRows[2].rir,[null,null,null]);assert.equal(f.goalRir,2);
  for(const addition of ['unit=kg','unknown=1','rep.load=\\u0033\\u0030'])
    assert.throws(()=>parseFixture(Buffer.from(bytes+'\n'+addition+'\n')));
});
test('withdrawn v1 archive still authenticates its historical capable-set rule',()=>{
  const held=output(caseIds[0]);held.subcases[0].targets[1].reps=13;
  assert.equal(grade(f,caseIds[0],held).outcome,'fail');
  assert.equal(grade(f,caseIds[0],output(caseIds[0])).outcome,'pass');
});
test('withdrawn v1 result contents remain reproducible without endorsing its rule',()=>{
  for(const mutate of [
    r=>r.subcases[0].targets.forEach((t,i)=>t.reps=f.repRows[0].reps[i]+1),
    r=>r.subcases[0].targets[1].reps=15,
    r=>r.subcases[0].targets[1].weight=38.5,
    r=>r.subcases[2].targets[0].reps=15,
    r=>r.subcases[3].targets[0].reps=15,
    r=>r.subcases[0].mutatedInput=true]) {
    const r=output(caseIds[0]);mutate(r);assert.equal(grade(f,caseIds[0],r).outcome,'fail');
  }
});
test('machine rounding and finite plate pairs require the independent exact answer',()=>{
  for(const id of caseIds.slice(1)) {
    assert.equal(grade(f,id,output(id)).outcome,'pass');
    const invented=output(id);invented.subcases[0].floor=invented.subcases[0].requested;
    assert.equal(grade(f,id,invented).outcome,'fail');
    const refusal=output(id);refusal.subcases[0].floor=null;refusal.subcases[0].next=null;
    assert.equal(grade(f,id,refusal).outcome,'fail');
  }
});
function syntheticBundle() {
  const engines={},measurements=[];
  for(const engine of ['legacy-javascript','independent-kotlin']) {
    const commit=engine==='legacy-javascript'?f.legacyCommit:'a'.repeat(40);
    const results=caseIds.map(id=>{
      const r=output(id);r.inputDigest=scenarioDigest(f.fixtureDigest,id);
      if(engine==='legacy-javascript')for(const sub of r.subcases)
        sub.raw=id===caseIds[0]?{setTargets:structuredClone(sub.targets)}:{floor:sub.floor,next:sub.next};
      r.assessment=grade(f,id,r);return r;
    });
    engines[engine]={commit,sourceTree:'b'.repeat(40),results};
    measurements.push(...results.map(r=>({caseId:r.caseId,engine,proof:'headless',outcome:r.assessment.outcome,
      commit,inputDigest:r.inputDigest,reproducible:true})));
  }
  return {schemaVersion:1,kind:'paired-headless-functional-review',fixtureDigest:f.fixtureDigest,
    inputProperties:structuredClone(f.properties),engines,measurements};
}
test('results, actual effort, raw outputs and measured outcomes stay content-bound',()=>{
  assert.doesNotThrow(()=>verifyBundle(syntheticBundle(),f));
  for(const mutate of [
    b=>b.inputProperties['rep.goalRir']='3',
    b=>b.measurements[0].outcome='fail',
    b=>b.engines['independent-kotlin'].results[0].subcases[2].actualRirCount=3,
    b=>b.engines['independent-kotlin'].results[0].subcases[2].assumedGoalRirSetCount=0,
    b=>b.engines['legacy-javascript'].results[0].subcases[0].raw.setTargets[0].reps=99,
    b=>b.engines['independent-kotlin'].results[0].inputDigest='0'.repeat(64)]) {
    const b=syntheticBundle();mutate(b);assert.throws(()=>verifyBundle(b,f));
  }
});
test('absent or stale records cannot claim current functional comparisons',()=>{
  const e=JSON.parse(readFileSync(resolve(root,evidencePath),'utf8'));
  const result=verifyRecordedFunctionalEvidence(e,{allowStaleProvenance:true});
  assert.ok([0,3].includes(result.recordedPairs));
  if(result.recordedPairs) {
    const changed=structuredClone(e);changed.measurements.find(r=>caseIds.includes(r.caseId)).artifactDigest='0'.repeat(64);
    assert.throws(()=>verifyRecordedFunctionalEvidence(changed,{allowStaleProvenance:true}));
  }
});

test('v2 rep checks do not rank unchanged, first-set, or multiple-set progressions',()=>{
  for(const proposed of [[14,13,12],[15,13,12],[15,14,12]]) {
    const r=output(caseIds[0]);r.subcases[0].targets.forEach((t,i)=>t.reps=proposed[i]);
    assert.equal(assess(f,caseIds[0],r).outcome,'pass');
  }
  const invalid=output(caseIds[0]);invalid.subcases[0].targets[0].weight=38.5;
  assert.equal(assess(f,caseIds[0],invalid).outcome,'fail');
  const invented=output(caseIds[0]);invented.subcases[0].mutatedInput=true;
  assert.equal(assess(f,caseIds[0],invented).outcome,'fail');
});

test('v2 bundles bind rep-integrity results without awarding prediction superiority',()=>{
  const bundle=syntheticBundle();bundle.schemaVersion=2;
  for(const [engine,run] of Object.entries(bundle.engines))for(const result of run.results) {
    if(result.caseId===caseIds[0]) {
      result.subcases[0].targets[0].reps=15;
      if(engine==='legacy-javascript')result.subcases[0].raw.setTargets=structuredClone(result.subcases[0].targets);
    }
    result.assessment=assess(f,result.caseId,result);
    bundle.measurements.find(m=>m.engine===engine&&m.caseId===result.caseId).outcome=result.assessment.outcome;
  }
  assert.doesNotThrow(()=>verifyBundle(bundle,f));
});
