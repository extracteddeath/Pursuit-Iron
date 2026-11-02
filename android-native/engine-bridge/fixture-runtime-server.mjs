/** JVM integration harness only. All requests use invented test data. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import readline from 'node:readline';
import { verifyBundleManifest } from '../engine-contract/create-bundle-manifest.mjs';
const bundleFile=process.argv[2];
assert.ok(bundleFile && process.argv.length===3);
const bytes=fs.readFileSync(bundleFile);
const manifest=JSON.parse(fs.readFileSync(path.join(path.dirname(bundleFile),'canonical-engine-manifest.json'),'utf8'));
assert.ok(verifyBundleManifest(bytes,manifest),'Integration runtime must use checksum-pinned build output');
const context=vm.createContext({});
vm.runInContext(bytes.toString('utf8'),context,{timeout:15000});
assert.equal(typeof context.PursuitIronBridge.dispatchWorkoutStoreJson,'function');
const input=readline.createInterface({input:process.stdin,crlfDelay:Infinity});
input.on('line',wire=>{
  try {
    assert.ok(Buffer.byteLength(wire,'utf8')<=12*1024*1024);
    context.requestWire=wire;
    const response=vm.runInContext('PursuitIronBridge.dispatchWorkoutStoreJson(requestWire)',context,{timeout:15000});
    process.stdout.write(response+'\n');
  } catch {
    // Do not print request data or private parser exceptions on a failed test.
    process.stdout.write('{"protocolVersion":1,"id":null,"ok":false,"error":{"code":"HARNESS_FAILURE","message":"Canonical test runtime unavailable"}}\n');
  } finally { delete context.requestWire; }
});
