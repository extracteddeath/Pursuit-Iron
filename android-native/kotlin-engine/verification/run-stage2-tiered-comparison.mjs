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
const fixture=resolve(root,'android-native/kotlin-engine/verification/stage2-tiered-cases.txt');
const bytes=readFileSync(fixture);
const rows=bytes.toString('utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{
  const fields=l.split('|');assert.equal(fields.length,6);
  const [id,tier,ledger,expectedWeb,expectedKotlin,classification]=fields;
  assert.ok(['t1','t2'].includes(tier));
  const exposures=ledger==='-'?[]:ledger.split(';').map(entry=>{
    const [stage,weight,reps,count,kind]=entry.split(',');
    assert.ok(['normal','failed','skipped','interrupted'].includes(kind));
    return {stage:Number(stage),weight:Number(weight),reps:Number(reps),count:Number(count),kind};
  });
  return {id,tier,exposures,expectedWeb,expectedKotlin,classification};
});
assert.equal(new Set(rows.map(x=>x.id)).size,rows.length);
const output=resolve(root,value('--output','android-native/kotlin-engine/build/reports/stage2-current-tiered.json'));
const nativeOutput=resolve(dirname(output),'stage2-current-tiered-native.txt');
mkdirSync(dirname(output),{recursive:true});
runJvmProbe({root,args,probe:'Stage2TieredProbe',task:'runStage2TieredProbe',input:fixture,output:nativeOutput});
const nativeRows=readFileSync(nativeOutput,'utf8').trim().split(/\r?\n/).map(l=>l.split('|'));
assert.deepEqual(nativeRows.map(r=>r[0]),rows.map(r=>r.id));
const {deriveTieredLinearState}=await import(pathToFileURL(resolve(webRoot,'modules/next-engine/percentage-protocols.js')));
const results=rows.map((row,i)=>{
  const entries=row.exposures.map((e,j)=>{
    const minimum=(row.tier==='t1'?[3,2,1]:[10,8,6])[e.stage];
    const sets=Array.from({length:e.count},(_,k)=>{
      const last=k===e.count-1;
      if(last&&e.kind==='skipped')return {w:e.weight,r:0,done:false};
      if(last&&e.kind==='failed')return {w:e.weight,r:0,done:true,failedAttempt:true};
      return {w:e.weight,r:e.reps,done:true};
    });
    return {programId:'cycle',id:'workout-'+j,date:j+1,unit:'lb',
      interrupted:e.kind==='interrupted',
      perf:{press:{sets,prescription:{sets:e.count,
        protocol:{scheme:'gzclp',tier:row.tier,stage:e.stage},
        setTargets:Array.from({length:e.count},()=>({weight:e.weight,reps:minimum,unit:'lb'}))
      }}}};
  });
  // Controlled shared fixture stock: 5 lb increments, 5..500 lb.
  // Reset is a reviewed 85% reduction rounded down, never a silent ordinary drop.
  const state=deriveTieredLinearState({programId:'cycle',exerciseId:'press',tier:row.tier,
    initialStage:0,initialLoad:100,entries,unit:'lb',
    nextLoad:w=>w+5<=500?w+5:null,resetLoad:w=>Math.floor((w*.85+1e-9)/5)*5});
  const web=state.stage+','+state.weight;
  const kotlin=nativeRows[i][1];
  return {id:row.id,classification:row.classification,expectedWeb:row.expectedWeb,
    expectedKotlin:row.expectedKotlin,web,kotlin,webPass:web===row.expectedWeb,
    kotlinPass:kotlin===row.expectedKotlin,matched:web===kotlin};
});
const report={schemaVersion:1,scope:'GZCLP replay numerical policy on controlled stock, not live issuance, complete history persistence, or device acceptance.',
  webCommit:webPin,kotlinCommit:git(root,'rev-parse','HEAD'),
  fixtureSha256:createHash('sha256').update(bytes).digest('hex'),results};
writeFileSync(output,JSON.stringify(report,null,2)+'\n');
for(const row of results)console.log(JSON.stringify(row));
assert.ok(results.every(r=>r.webPass&&r.kotlinPass),'Unexpected observed result: investigate before changing fixture');
assert.ok(results.every(r=>r.matched===(r.classification==='matched')),'Unexpected divergence or loss of safety guard');
