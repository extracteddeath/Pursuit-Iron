import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { runJvmProbe } from './run-jvm-probe.mjs';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const args=process.argv.slice(2);
const value=(flag,fallback)=>{const i=args.indexOf(flag);return i<0?fallback:args[i+1];};
const webRoot=resolve(value('--web-root','comparison-current-web'));
const webPin='29faa204212a4d54088b4870341350e605939185';
const git=(cwd,...cmd)=>execFileSync('git',cmd,{cwd,encoding:'utf8'}).trim();
assert.equal(git(webRoot,'rev-parse','HEAD'),webPin);
assert.equal(git(webRoot,'status','--porcelain'),'');
const fixture=resolve(root,'android-native/kotlin-engine/verification/stage2-basic-cases.txt');
const bytes=readFileSync(fixture);
const allRows=bytes.toString('utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{
  const f=l.split('|');assert.equal(f.length,10);return f;
});
assert.equal(new Set(allRows.map(f=>f[0])).size,allRows.length);
const isSelection=f=>['select','select-v13'].includes(f[1].split(':')[0]);
const selectionRows=allRows.filter(isSelection).map(f=>{
  const [id,mode,exerciseId,phase,experience,role,band,expectedWeb,expectedKotlin,classification]=f;
  const manualStyle=mode.split(':')[1];
  assert.ok(['matched','catalog-trait-gap','unloaded-rep-route','advanced-auto-gap','explicit-source-gate'].includes(classification));
  return {id,exerciseId,phase,experience,role,band:band.split(',').map(Number),manualStyle,selectionPolicy:mode.startsWith('select-v13')?'v13':'v12',expectedWeb,expectedKotlin,classification};
});
const rows=allRows.filter(f=>!isSelection(f)).map(f=>{
  const [id,style,loads,reps,rir,stock,expectedWeb,expectedKotlin,estimates,classification]=f;
  assert.ok(['double','dynamic','linear','e1rm'].includes(style));
  assert.ok(['matched','basic-effort-policy','basic-linear-floor','original-goal-assumption'].includes(classification));
  const numbers=s=>s.split(',').map(Number);
  return {id,style,loads:numbers(loads),reps:numbers(reps),
    rir:rir.split(',').map(v=>v==='null'?null:Number(v)),stock:numbers(stock),
    expectedWeb,expectedKotlin,estimates,classification};
});
assert.equal(new Set(rows.map(r=>r.id)).size,rows.length);
const output=resolve(root,value('--output','android-native/kotlin-engine/build/reports/stage2-current-basic.json'));
const nativeOutput=resolve(dirname(output),'stage2-current-basic-native.txt');
mkdirSync(dirname(output),{recursive:true});
runJvmProbe({root,args,probe:'Stage2BasicProbe',task:'runStage2BasicProbe',input:fixture,output:nativeOutput});
const native=readFileSync(nativeOutput,'utf8').trim().split(/\r?\n/).map(l=>l.split('|'));
assert.deepEqual(native.map(r=>r[0]),allRows.map(r=>r[0]));
const nativeById=new Map(native.map(r=>[r[0],r]));
const {evaluateWorkoutProgression}=await import(pathToFileURL(resolve(webRoot,'modules/next-engine/performance.js')));
const results=rows.map(row=>{
  const exerciseId='machine_press';
  const exercise={exerciseId,name:'Machine Chest Press',role:'primary',
    sets:row.loads.length,progressionStyle:row.style,prescription:{reps:[5,8],rir:[2,2]}};
  const performed=row.loads.map((load,setIndex)=>({exerciseId,setIndex,load,reps:row.reps[setIndex],rir:row.rir[setIndex]}));
  const observed=evaluateWorkoutProgression({exercises:[exercise]},performed,{
    loadingInventory:{unit:'lb',exerciseOverrides:{[exerciseId]:{availableLoads:row.stock}}},
    equipmentAvailable:['machine']})[0];
  const web=observed.setTargets.map(x=>x.weight).join(',');
  const kotlin=nativeById.get(row.id)[1];
  const webEstimate=row.style==='e1rm'?String(observed.estimated1RM):'-';
  const kotlinEstimate=nativeById.get(row.id)[2];
  const expectedEstimates=row.estimates==='-'?['-','-']:row.estimates.split(',');
  const webPass=web===row.expectedWeb&&webEstimate===expectedEstimates[0];
  const kotlinPass=kotlin===row.expectedKotlin&&kotlinEstimate===expectedEstimates[1];
  return {id:row.id,style:row.style,classification:row.classification,
    expectedWeb:row.expectedWeb,expectedKotlin:row.expectedKotlin,
    web,kotlin,webEstimate,kotlinEstimate,expectedEstimates,
    webAction:observed.action,webReason:observed.reasonCode,webPass,kotlinPass,
    matched:web===kotlin&&webEstimate===kotlinEstimate};
});
const {EXERCISE_MAP}=await import(pathToFileURL(resolve(webRoot,'modules/next-engine/exercise-db.js')));
const {selectProgressionStyle}=await import(pathToFileURL(resolve(webRoot,'modules/next-engine/progression-style.js')));
const selectionResults=selectionRows.map(row=>{
  const exercise=EXERCISE_MAP.get(row.exerciseId);
  assert.ok(exercise,`Missing pinned catalog identity: ${row.exerciseId}`);
  const observed=selectProgressionStyle(exercise,row.role,{
    phase:row.phase,experience:row.experience,blockWeeks:6,prescription:{reps:row.band},
    ...(row.manualStyle?{manualStyle:row.manualStyle}:{})
  });
  const web=observed.style,kotlin=nativeById.get(row.id)[1];
  if(row.manualStyle)assert.equal(observed.source,'manual');
  return {...row,web,kotlin,webSource:observed.source,webReason:observed.reason,
    webPass:web===row.expectedWeb,kotlinPass:kotlin===row.expectedKotlin,matched:web===kotlin};
});
const report={schemaVersion:1,scope:'Same-prescription ordinary working sets, explicit e1RM estimates, and separate real-catalog selection probes. Not durable issuance, full phase generation, changed-phase calibration, or release certification.',
  webCommit:webPin,kotlinCommit:git(root,'rev-parse','HEAD'),
  fixtureSha256:createHash('sha256').update(bytes).digest('hex'),results,selectionResults};
writeFileSync(output,JSON.stringify(report,null,2)+'\n');
for(const row of results)console.log(JSON.stringify(row));
assert.ok(results.every(r=>r.webPass&&r.kotlinPass),'Unexpected observed result; investigate before revising expected values');
assert.ok(results.every(r=>r.matched===(r.classification==='matched')),'Unexpected policy divergence');

for(const row of selectionResults)console.log(JSON.stringify({kind:'selection',...row}));
assert.ok(selectionResults.every(r=>r.webPass&&r.kotlinPass),'Unexpected recommendation result; investigate before revising expected values');
assert.ok(selectionResults.every(r=>r.matched===(r.classification==='matched')),'Unexpected recommendation divergence');
