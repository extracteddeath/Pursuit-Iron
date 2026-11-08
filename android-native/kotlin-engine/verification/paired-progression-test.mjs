import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { root, fixturePath, caseIds, parseFixture, physicalLoads, grade,
  scenarioDigest, verifyBundle, evidencePath, verifyRecordedProgressionEvidence } from './paired-progression.mjs';
import { verifyRecordedFunctionalEvidence } from './paired-functional.mjs';

const bytes=readFileSync(resolve(root,fixturePath));
const fixture=parseFixture(bytes);
const byId=id=>fixture.cases.find(c=>c.id===id);
const result=(candidateLoad,extra={})=>({candidateLoad,mutatedInput:false,...extra});

test('shared input retains actual effort separately from issued effort',()=>{
  const missing=byId('progression-no-rir');
  assert.ok(missing.latest.every(s=>s.rir===null && s.issuedRir===2));
  assert.deepEqual(caseIds,fixture.cases.map(c=>c.id));
  assert.ok(physicalLoads(fixture).includes(205));
  assert.ok(!physicalLoads(fixture).includes(202.5));
});

test('ambiguous duplicate, escaped, unknown and incomplete inputs are refused',()=>{
  for(const text of [bytes+'\nunit=lb\n',bytes+'\nextra=1\n',
    bytes.toString().replace('unit=lb','unit=\\u006c\\u0062'),
    bytes.toString().replace('workingSets=4','workingSets=3'),
    bytes.toString().replace('dp-earned-step.latest.rir=2,2,2,2','dp-earned-step.latest.rir=2,2')])
    assert.throws(()=>parseFixture(Buffer.from(text)));
});

test('blanket refusal cannot pass earned progression or failed-load recovery',()=>{
  for(const id of caseIds)
    assert.equal(grade(fixture,byId(id),result(null)).outcome,'fail',id);
  assert.equal(grade(fixture,byId('dp-earned-step'),result(205)).outcome,'pass');
  assert.equal(grade(fixture,byId('dp-uniform-failure'),result(215)).outcome,'fail');
  assert.equal(grade(fixture,byId('dp-uniform-failure'),result(200)).outcome,'pass');
});

test('original goal RIR supports normal progression while missing actuals remain null',()=>{
  const input=byId('progression-no-rir');
  assert.equal(grade(fixture,input,result(205)).outcome,'pass');
  assert.equal(grade(fixture,input,result(null)).outcome,'fail');
  assert.ok(input.latest.every(s=>s.rir===null && s.issuedRir===2));
  const reportedHard=structuredClone(input);reportedHard.latest[0].rir=0;
  assert.equal(grade(fixture,reportedHard,result(205)).outcome,'fail');
  const noGoal=structuredClone(input);noGoal.latest[0].issuedRir=null;
  assert.equal(grade(fixture,noGoal,result(205)).outcome,'fail');
});

test('unbuildable load, fabricated set targets and mutated actuals fail',()=>{
  assert.equal(grade(fixture,byId('dp-uniform-failure'),result(159.5)).outcome,'fail');
  assert.equal(grade(fixture,byId('dp-earned-step'),result(205,{setTargets:[{weight:202.5}]})).outcome,'fail');
  assert.equal(grade(fixture,byId('dp-earned-step'),result(205,{mutatedInput:true})).outcome,'fail');
});

// Synthetic validator inputs below are NEVER written to the measured ledger.
function syntheticBundle() {
  const engines={};
  const measurements=[];
  for(const engine of ['legacy-javascript','independent-kotlin']) {
    const results=fixture.cases.map(item=>{
      const candidateLoad=['dp-earned-step','progression-no-rir'].includes(item.id)?205:195;
      const row={caseId:item.id,inputDigest:scenarioDigest(fixture.fixtureDigest,item.id),
        previous:structuredClone(item.previous),latest:structuredClone(item.latest),
        candidateLoad,mutatedInput:false};
      if(engine==='independent-kotlin')Object.assign(row,{completedSets:4,
        actualRirCount:item.latest.filter(s=>s.rir!==null).length,
        assumedGoalRirSetCount:[...item.previous,...item.latest].filter(s=>s.rir===null).length,
        mayAutoApply:false});
      else Object.assign(row,{raw:{suggestedLoad:candidateLoad}});
      row.assessment=grade(fixture,item,row);
      return row;
    });
    engines[engine]={commit:engine==='legacy-javascript'?fixture.legacyCommit:'a'.repeat(40),
      sourceTree:'b'.repeat(40),results};
    measurements.push(...results.map(r=>({caseId:r.caseId,engine,proof:'headless',
      outcome:r.assessment.outcome,commit:engines[engine].commit,inputDigest:r.inputDigest,reproducible:true})));
  }
  return {schemaVersion:1,kind:'paired-headless-progression-review',fixtureDigest:fixture.fixtureDigest,
    engines,measurements};
}

test('content and scenario identities must match both executed output sets',()=>{
  assert.doesNotThrow(()=>verifyBundle(syntheticBundle(),fixture));
  for(const mutate of [
    b=>b.fixtureDigest='0'.repeat(64),
    b=>b.engines['independent-kotlin'].results[0].inputDigest='0'.repeat(64),
    b=>b.engines['legacy-javascript'].results.pop(),
    b=>b.engines['legacy-javascript'].commit='c'.repeat(40),
    b=>b.measurements.push({...b.measurements[0]}),
    b=>b.measurements[0].commit='d'.repeat(40)]) {
    const bundle=syntheticBundle();mutate(bundle);
    assert.throws(()=>verifyBundle(bundle,fixture));
  }
});

test('changed actual RIR, overwritten targets and made-up pass outcomes are refused',()=>{
  for(const mutate of [
    b=>b.engines['independent-kotlin'].results[4].latest[0].rir=2,
    b=>b.engines['independent-kotlin'].results[0].latest[0].issuedLoad=195,
    b=>b.engines['independent-kotlin'].results[4].actualRirCount=4,
    b=>b.engines['independent-kotlin'].results[4].assumedGoalRirSetCount=0,
    b=>b.engines['independent-kotlin'].results[0].mayAutoApply=true,
    b=>b.engines['legacy-javascript'].results[0].raw.suggestedLoad=215,
    b=>b.measurements[0].outcome='fail']) {
    const bundle=syntheticBundle();mutate(bundle);
    assert.throws(()=>verifyBundle(bundle,fixture));
  }
});

test('recorded measurements require the exact artifact bytes and bound engine results',()=>{
  const evidence=JSON.parse(readFileSync(resolve(root,evidencePath),'utf8'));
  const provenance=verifyRecordedProgressionEvidence(evidence,
    {allowStaleProvenance:true});
  assert.equal(provenance.recordedPairs,5);
  if(provenance.currentNativeTree)
    assert.doesNotThrow(()=>verifyRecordedProgressionEvidence(evidence));
  else
    assert.throws(()=>verifyRecordedProgressionEvidence(evidence),
      /native engine source changed|uncommitted native policies|comparison runner\/fixture changed/);
  for(const mutate of [
    record=>record.artifactDigest='0'.repeat(64),
    record=>record.outcome='fail',
    record=>record.inputDigest='0'.repeat(64),
    record=>record.commit='0'.repeat(40),
    (record,e)=>e.measurements.push({...record})]) {
    const altered=structuredClone(evidence);
    const record=altered.measurements.find(r=>r.proof==='headless'&&caseIds.includes(r.caseId));
    assert.ok(record,'Select the progression tranche regardless of ledger ordering');
    mutate(record,altered);
    assert.throws(()=>verifyRecordedProgressionEvidence(altered,
      {allowStaleProvenance:true}));
  }
});

test('partial matched measurements still cannot certify the complete engine replacement',()=>{
  const audit=JSON.parse(execFileSync(process.execPath,
    ['android-native/kotlin-engine/verification/engine-superiority-gate.mjs','--audit'],
    {cwd:root,encoding:'utf8'}));
  const evidence=JSON.parse(readFileSync(resolve(root,evidencePath),'utf8'));
  const verified=verifyRecordedProgressionEvidence(evidence,{allowStaleProvenance:true});
  const functional=verifyRecordedFunctionalEvidence(evidence,{allowStaleProvenance:true});
  const currentWithdrawals=(evidence.retractions??[]).filter(r=>r.proof==='headless'&&
    ((caseIds.includes(r.caseId)&&verified.currentNativeTree)||
        (['dp-rep-target-jumps','machine-increments','bar-stock'].includes(r.caseId)&&functional.currentNativeTree))).length;
  const completed=(verified.currentNativeTree?verified.recordedPairs:0)+
    (functional.currentNativeTree?functional.recordedPairs:0)-currentWithdrawals;
  assert.equal(audit.completedPairs,completed);
  assert.equal(audit.requiredPairs,49);
  assert.equal(audit.missingPairs,49-completed);
  assert.equal(audit.staleRecordedHeadlessPairs,
    (verified.currentNativeTree?0:verified.recordedPairs)+
    (functional.currentNativeTree?0:functional.recordedPairs));
  assert.equal(audit.verdict,'NOT_PROVEN_BETTER');
  const repResults=evidence.measurements.filter(r=>r.caseId==='dp-rep-target-jumps'&&r.proof==='headless');
  const repWithdrawn=(evidence.retractions??[]).some(r=>r.caseId==='dp-rep-target-jumps'&&r.proof==='headless');
  const repImproved=!repWithdrawn&&functional.currentNativeTree&&repResults.some(r=>r.engine==='legacy-javascript'&&r.outcome==='fail')&&
    repResults.some(r=>r.engine==='independent-kotlin'&&r.outcome==='pass');
  assert.deepEqual(audit.improvedDomains,repImproved?['progression']:[]);
  assert.deepEqual(audit.regressions,[]);
  if(repWithdrawn) {
    assert.ok(audit.withdrawnPairs.some(r=>r.caseId==='dp-rep-target-jumps'));
    assert.ok(!audit.measuredImprovements.some(r=>r.id==='dp-rep-target-jumps'));
  }
});
