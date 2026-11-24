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
const fixture=resolve(root,'android-native/kotlin-engine/verification/stage2-percentage-cases.txt');
const bytes=readFileSync(fixture);
const rows=bytes.toString('utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{
  const f=l.split('|');assert.equal(f.length,12);
  const [id,scheme,nativeScheme,tier,day,week,total,count,reference,weights,reps,repOut]=f;
  const nums=s=>s==='-'?[]:s.split(',').map(Number);
  const expected={weights:nums(weights),reps:nums(reps),repOut:nums(repOut)};
  assert.equal(expected.weights.length,Number(count));assert.equal(expected.reps.length,Number(count));
  return {id,scheme,nativeScheme,tier,day,week:Number(week),total:Number(total),count:Number(count),reference:Number(reference),expected};
});
assert.equal(new Set(rows.map(x=>x.id)).size,rows.length);
assert.equal(new Set(rows.map(x=>x.scheme)).size,8);
const output=resolve(root,value('--output','android-native/kotlin-engine/build/reports/stage2-current-percentage.json'));
const nativeOutput=resolve(dirname(output),'stage2-current-percentage-native.txt');
mkdirSync(dirname(output),{recursive:true});
runJvmProbe({root,args,probe:'Stage2PercentageProbe',task:'runStage2PercentageProbe',input:fixture,output:nativeOutput});
const nativeRows=readFileSync(nativeOutput,'utf8').trim().split(/\r?\n/).map(l=>{
  const [id,w,r,a]=l.split('|');const nums=s=>s==='-'?[]:s.split(',').map(Number);
  return {id,weights:nums(w),reps:nums(r),repOut:nums(a)};
});
assert.deepEqual(nativeRows.map(r=>r.id),rows.map(r=>r.id));
const {percentageProtocolFor,adaptPercentageSetBudget}=await import(pathToFileURL(resolve(webRoot,'modules/next-engine/percentage-protocols.js')));
// Explicit shared fixture stock: 5..500 lb in 5 lb increments.
// Tolerance only cancels representation error at an exact integer boundary;
// this is not a claim to execute the entire web equipment resolver.
const snapLoad=v=>Math.min(500,Math.floor((v+1e-9)/5)*5);
const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const results=rows.map((row,i)=>{
  const source=percentageProtocolFor({scheme:row.scheme,tm:row.reference,weekIndex:row.week,
    weeksTotal:row.total,dayType:row.day==='standard'?undefined:row.day,tier:row.tier,snapLoad});
  const adapted=adaptPercentageSetBudget(source,row.count);
  assert.ok(adapted?.sets?.length);
  const web={weights:adapted.sets.map(s=>s.weight),reps:adapted.sets.map(s=>parseInt(s.reps,10)),
    repOut:adapted.sets.flatMap((s,j)=>s.amrap?[j+1]:[])};
  const {id,...kotlin}=nativeRows[i];
  return {id,expected:row.expected,web,kotlin,webPass:equal(web,row.expected),
    kotlinPass:equal(kotlin,row.expected),matched:equal(web,kotlin)};
});
const report={schemaVersion:1,scope:'Named per-set numerical intent with controlled shared stock only; not persistence, issuance, GZCLP, full web loading path or functional parity.',
  webCommit:webPin,kotlinCommit:git(root,'rev-parse','HEAD'),
  fixtureSha256:createHash('sha256').update(bytes).digest('hex'),results};
writeFileSync(output,JSON.stringify(report,null,2)+'\n');
for(const row of results)console.log(JSON.stringify(row));
assert.ok(results.every(x=>x.kotlinPass),'Kotlin differs from independent expected values');
assert.ok(results.every(x=>x.webPass&&x.matched),'Current web comparison differs; review and classify rather than silently changing oracle');
