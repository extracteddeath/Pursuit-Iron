import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import crypto from 'node:crypto';
const manifest=JSON.parse(fs.readFileSync('manifest.webmanifest','utf8'));
assert.equal(manifest.id,'./');assert.equal(manifest.start_url,'./');assert.equal(manifest.scope,'./');
for(const icon of manifest.icons){
  assert.match(icon.src,/icon-(?:192|512|maskable-512)-[a-f0-9]{12}\.png$/);
  const bytes=fs.readFileSync(icon.src);
  assert.ok(icon.src.includes(crypto.createHash('sha256').update(bytes).digest('hex').slice(0,12)));
}
const listeners={};const scope='https://example.test/Pursuit-Iron/';
let stored=new Response(JSON.stringify({name:'offline'}),{headers:{'Content-Type':'application/manifest+json'}});
let network=new Response(JSON.stringify({name:'current'}),{headers:{'Content-Type':'application/manifest+json'}});
let fail=false,requestCache;
const sandbox={URL,Request,Response,console,self:{registration:{scope},addEventListener:(t,fn)=>listeners[t]=fn},
  caches:{open:async()=>({match:async()=>stored.clone(),put:async(_key,res)=>{stored=res;}})},
  fetch:async req=>{requestCache=req.cache;if(fail)throw new Error('offline');return network.clone();}};
vm.runInNewContext(fs.readFileSync('sw.js','utf8'),sandbox);
const read=async()=>{let response;listeners.fetch({request:new Request(scope+'manifest.webmanifest'),respondWith:p=>response=p});return(await response).json();};
assert.equal((await read()).name,'current');assert.equal(requestCache,'no-cache');
fail=true;assert.equal((await read()).name,'current','offline uses last good network manifest');
fail=false;network=new Response('failure',{status:500});assert.equal((await read()).name,'current','bad network response retains good manifest');
assert.equal(Object.keys(sandbox).some(k=>/localStorage|indexedDB/.test(k)),false);
console.log('PASS M223 icon refresh: immutable content URLs, stable installed identity, current network manifest and offline/error fallback.');
