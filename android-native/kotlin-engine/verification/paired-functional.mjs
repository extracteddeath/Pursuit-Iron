import assert from 'node:assert/strict';
import { readFileSync, realpathSync } from 'node:fs';
import { resolve, relative, sep } from 'node:path';
import { root, sourcePath, sha256, git, scenarioDigest } from './paired-progression.mjs';

export { root, sourcePath, sha256, git, scenarioDigest };
export const fixturePath='android-native/kotlin-engine/verification/paired-functional-input.properties';
export const artifactPath='android-native/kotlin-engine/verification/paired-functional-measurement.json';
export const evidencePath='android-native/kotlin-engine/verification/engine-superiority-evidence.json';
export const caseIds=['dp-rep-target-jumps','machine-increments','bar-stock'];
export const runnerPaths=[fixturePath,
  'android-native/kotlin-engine/verification/paired-functional.mjs',
  'android-native/kotlin-engine/verification/run-paired-functional.mjs',
  'android-native/kotlin-engine/verification/run-jvm-probe.mjs',
  'android-native/kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/PairedFunctionalProbe.kt',
  'android-native/kotlin-engine/build.gradle.kts'];

export function parseFixture(bytes) {
  const p={};
  for(const line of bytes.toString('utf8').split(/\r?\n/).filter(x=>x&&!x.startsWith('#'))) {
    assert.match(line,/^[A-Za-z0-9_.-]+=[A-Za-z0-9_:.,-]+$/);
    const i=line.indexOf('=');const key=line.slice(0,i);
    assert.ok(!Object.hasOwn(p,key),'duplicate input');p[key]=line.slice(i+1);
  }
  const used=new Set();
  const field=k=>{used.add(k);assert.ok(Object.hasOwn(p,k),'missing '+k);return p[k];};
  const number=v=>{assert.match(v,/^\d+(?:\.\d+)?$/);return Number(v);};
  const integer=v=>{const n=number(v);assert.ok(Number.isSafeInteger(n));return n;};
  const numbers=k=>field(k).split(',').map(number);
  assert.equal(field('schemaVersion'),'1');assert.equal(field('unit'),'lb');
  const legacyCommit=field('legacyCommit');assert.match(legacyCommit,/^[a-f0-9]{40}$/);
  assert.deepEqual(field('caseIds').split(','),caseIds);
  const exerciseId=field('rep.exerciseId');assert.equal(exerciseId,'db_curl');
  const count=integer(field('rep.count'));assert.equal(count,3);
  const floor=integer(field('rep.floor')),ceiling=integer(field('rep.ceiling'));
  assert.deepEqual([floor,ceiling],[10,15]);
  const load=number(field('rep.load')),availableLoads=numbers('rep.availableLoads');
  assert.ok(availableLoads.includes(load));
  const goalRir=integer(field('rep.goalRir'));assert.equal(goalRir,2);
  const scenarios=field('rep.scenarios').split(',');
  assert.deepEqual(scenarios,['headroom','goal-effort','missing-effort','near-failure']);
  const repRows=scenarios.map(id=>{
    const reps=field(`rep.${id}.reps`).split(',').map(integer);
    const rir=field(`rep.${id}.rir`).split(',').map(v=>v==='null'?null:integer(v));
    assert.equal(reps.length,count);assert.equal(rir.length,count);
    assert.ok(reps.every(n=>n>=floor&&n<=ceiling));assert.ok(rir.every(n=>n===null||n<=10));
    return {id,reps,rir,load,goalRir};
  });
  const machineExercise=field('machine.exerciseId');assert.equal(machineExercise,'machine_press');
  const machineLoads=numbers('machine.loads'),machineRequested=numbers('machine.requested');
  assert.deepEqual(machineRequested,[159.5,38.5]);
  assert.ok(machineLoads.every(n=>n>0&&n%5===0)&&new Set(machineLoads).size===machineLoads.length);
  const barExercise=field('bar.exerciseId');assert.equal(barExercise,'barbell_bench');
  const barWeight=number(field('bar.weight'));
  const platePairs=field('bar.platePairs').split(',').map(v=>{
    const [weight,pairs]=v.split(':').map(number);
    assert.ok(weight>0&&Number.isSafeInteger(pairs)&&pairs>0&&pairs<=10);return {weight,pairs};
  });
  assert.ok(barWeight>0&&platePairs.length<=8);
  const barRequested=numbers('bar.requested');
  assert.equal(used.size,Object.keys(p).length,'unknown input');
  return {properties:p,fixtureDigest:sha256(bytes),legacyCommit,count,floor,ceiling,
    exerciseId,load,goalRir,availableLoads,repRows,machineExercise,machineLoads,
    machineRequested,barExercise,barWeight,platePairs,barRequested};
}
export function barLoads(f) {
  let values=new Set([f.barWeight]);
  for(const {weight,pairs} of f.platePairs) {
    const next=new Set();
    for(const base of values)for(let n=0;n<=pairs;n++)next.add(base+2*n*weight);
    values=next;assert.ok(values.size<=100000);
  }
  return [...values].sort((a,b)=>a-b);
}

/** Frozen v1 conformance oracle. Retracted as scientific/superiority evidence. */
function gradeHistoricalV1(f,id,result) {
  const failures=[];const rows=result.subcases;
  if(!Array.isArray(rows))return {outcome:'fail',failures:['missing measured subcases']};
  if(id==='dp-rep-target-jumps') {
    if(JSON.stringify(rows.map(r=>r.id))!==JSON.stringify(f.repRows.map(r=>r.id)))
      failures.push('rep scenarios missing or reordered');
    for(const input of f.repRows) {
      const row=rows.find(r=>r.id===input.id);
      if(!row||row.mutatedInput!==false||!Array.isArray(row.targets)||row.targets.length!==f.count) {
        failures.push(input.id+': original facts/complete targets not preserved');continue;
      }
      const effort=input.rir.map(r=>r??input.goalRir);
      const eligible=input.reps.map((n,i)=>n<f.ceiling&&effort[i]>=3);
      const mustHold=effort.some(n=>n<=1)||!eligible.some(Boolean);
      const changed=[];
      row.targets.forEach((t,i)=>{
        const delta=t.reps-input.reps[i];
        if(t.weight!==input.load||!Number.isInteger(t.reps)||t.reps<f.floor||t.reps>f.ceiling||
          ![0,1].includes(delta))failures.push(input.id+': changed load or jumped/invalid reps');
        if(delta!==0)changed.push(i);
      });
      if(mustHold&&changed.length)failures.push(input.id+': added work without demonstrated reserve');
      if(!mustHold&&(changed.length!==1||!eligible[changed[0]]))
        failures.push(input.id+': must improve exactly one capable set by one rep');
    }
  } else {
    assert.ok(['machine-increments','bar-stock'].includes(id));
    const stock=id==='machine-increments'?[...f.machineLoads].sort((a,b)=>a-b):barLoads(f);
    const requested=id==='machine-increments'?f.machineRequested:f.barRequested;
    if(JSON.stringify(rows.map(r=>r.id))!==JSON.stringify(requested.map(String)))failures.push('equipment queries differ');
    for(const desired of requested) {
      const row=rows.find(r=>r.id===String(desired));
      const floor=stock.filter(v=>v<=desired).at(-1)??null;
      const next=stock.find(v=>v>desired)??null;
      if(!row||row.mutatedInput!==false||row.requested!==desired||row.floor!==floor||row.next!==next)
        failures.push(`${desired}: did not return the actual attainable floor and next step`);
    }
  }
  return {outcome:failures.length?'fail':'pass',failures};
}

/** V2 verifies physical/data integrity, not individualized accuracy or superiority.
 * Rep feasibility requires held-out outcomes and fatigue context absent here.
 * It intentionally does not reward fewer changed sets or a particular vector.
 */
export function grade(f,id,result,oracleVersion=2) {
  if(oracleVersion===1)return gradeHistoricalV1(f,id,result);
  assert.equal(oracleVersion,2);
  if(id!=='dp-rep-target-jumps')return gradeHistoricalV1(f,id,result);
  const failures=[];
  const rows=result.subcases;
  if(!Array.isArray(rows)||JSON.stringify(rows.map(r=>r.id))!==JSON.stringify(f.repRows.map(r=>r.id)))
    return {outcome:'fail',failures:['rep scenarios missing or reordered']};
  for(const row of rows) {
    if(row.mutatedInput!==false||!Array.isArray(row.targets)||row.targets.length!==f.count) {
      failures.push(row.id+': original facts/complete targets not preserved');continue;
    }
    if(row.targets.some(t=>t.weight!==f.load||!Number.isInteger(t.reps)||t.reps<f.floor||t.reps>f.ceiling))
      failures.push(row.id+': changed load or invalid rep range');
  }
  return {outcome:failures.length?'fail':'pass',failures};
}

export function verifyBundle(bundle,f) {
  assert.ok([1,2].includes(bundle.schemaVersion));assert.equal(bundle.kind,'paired-headless-functional-review');
  assert.equal(bundle.fixtureDigest,f.fixtureDigest);assert.deepEqual(bundle.inputProperties,f.properties);
  assert.equal(bundle.measurements.length,caseIds.length*2);
  for(const engine of ['legacy-javascript','independent-kotlin']) {
    const run=bundle.engines[engine];assert.match(run.commit,/^[a-f0-9]{40}$/);
    assert.match(run.sourceTree,/^[a-f0-9]{40}$/);
    if(engine==='legacy-javascript')assert.equal(run.commit,f.legacyCommit);
    assert.deepEqual(run.results.map(r=>r.caseId),caseIds);
    for(const row of run.results) {
      assert.equal(row.inputDigest,scenarioDigest(f.fixtureDigest,row.caseId));
      if(engine==='legacy-javascript')for(const sub of row.subcases) {
        if(row.caseId==='dp-rep-target-jumps')assert.deepEqual(sub.targets,sub.raw.setTargets);
        else {assert.equal(sub.floor,sub.raw.floor);assert.equal(sub.next,sub.raw.next);}
      }
      else for(const sub of row.subcases)if(row.caseId==='dp-rep-target-jumps') {
        const input=f.repRows.find(r=>r.id===sub.id);assert.ok(input);
        assert.equal(sub.actualRirCount,input.rir.filter(r=>r!==null).length);
        assert.equal(sub.assumedGoalRirSetCount,input.rir.filter(r=>r===null).length);
        assert.equal(sub.mayAutoApply,false);
      }
      const assessment=grade(f,row.caseId,row,bundle.schemaVersion);assert.deepEqual(row.assessment,assessment);
      const records=bundle.measurements.filter(m=>m.caseId===row.caseId&&m.engine===engine);
      assert.equal(records.length,1);
      assert.deepEqual(records[0],{caseId:row.caseId,engine,proof:'headless',outcome:assessment.outcome,
        commit:run.commit,inputDigest:row.inputDigest,reproducible:true});
    }
  }
  return bundle;
}

export function verifyRecordedFunctionalEvidence(evidence,{allowStaleProvenance=false}={}) {
  const records=evidence.measurements.filter(r=>r.proof==='headless'&&caseIds.includes(r.caseId));
  if(!records.length)return {currentNativeTree:false,recordedPairs:0};
  assert.equal(records.length,caseIds.length*2,'incomplete functional comparison tranche');
  const path=realpathSync(resolve(root,artifactPath));
  assert.ok(!relative(root,path).startsWith('..'+sep),'artifact outside repository');
  const bytes=readFileSync(path),f=parseFixture(readFileSync(resolve(root,fixturePath)));
  const bundle=verifyBundle(JSON.parse(bytes),f);
  for(const record of records) {
    assert.equal(record.artifact,artifactPath);assert.equal(record.artifactDigest,sha256(bytes));
    const {artifact,artifactDigest,...measurement}=record;
    assert.deepEqual(measurement,bundle.measurements.find(m=>m.caseId===record.caseId&&m.engine===record.engine));
  }
  const changedRunnerFiles=runnerPaths.filter(file=>bundle.runnerFiles[file]!==sha256(readFileSync(resolve(root,file))));
  const currentNativeTree=bundle.engines['independent-kotlin'].sourceTree===git(root,'rev-parse',`HEAD:${sourcePath}`)&&
    git(root,'status','--porcelain','--untracked-files=all','--',sourcePath)===''&&changedRunnerFiles.length===0;
  if(!allowStaleProvenance)assert.ok(currentNativeTree,'functional measurements have stale source/runner provenance');
  return {currentNativeTree,recordedPairs:records.length/2,changedRunnerFiles};
}
