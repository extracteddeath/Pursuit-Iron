import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { dispatchEngineJson } from '../engine-bridge/protocol.mjs';

/**
 * Ephemeral Android emulator assets only. This is an exact per-field oracle
 * from the JS canonical source, not a new or inferred Kotlin training policy.
 */
export function materializeAndroidParity(golden) {
  assert.equal(golden.schemaVersion,1);
  assert.equal(golden.feature,'progressionPreview');
  assert.ok(golden.cases.length >= 7 && golden.cases.length <= 64);
  const seen = new Set();
  const cases = golden.cases.map(item => {
    assert.ok(!seen.has(item.id), 'duplicate fixture ID');
    seen.add(item.id);
    const request = {
      protocolVersion:1, id:item.id, method:'progressionPreview', params:item.params
    };
    const original = JSON.stringify(item.params);
    const response = JSON.parse(dispatchEngineJson(JSON.stringify(request)));
    assert.equal(response.ok,true,'Canonical source rejected '+item.id);
    assert.equal(response.id,item.id);
    assert.equal(response.result.previewOnly,true);
    assert.equal(JSON.stringify(item.params),original);
    for (const [key,value] of Object.entries(item.expected))
      assert.deepEqual(response.result[key],value,item.id+':'+key);
    return {...item, expectedResponse:response.result};
  });
  return {
    schemaVersion:golden.schemaVersion,
    feature:golden.feature,
    deviceOracle:'modules/engine-api.js:dispatchEngineJson',
    cases
  };
}

if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  assert.equal(process.argv.length,4,
    'Usage: node export-android-parity.mjs <canonical-golden.json> <emulator-asset.json>');
  const document=materializeAndroidParity(JSON.parse(fs.readFileSync(process.argv[2],'utf8')));
  fs.writeFileSync(process.argv[3],JSON.stringify(document,null,2)+'\n');
  console.log('PASS exported exact canonical Android fixture responses: '+document.cases.length);
}
