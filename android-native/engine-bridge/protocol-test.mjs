import assert from 'node:assert/strict';
import * as canonical from '../../modules/engine-api.js';
import { dispatchEngineRequest, dispatchEngineJson } from './protocol.mjs';
const call = (method, params = {}) => dispatchEngineRequest({
    protocolVersion: 1, id: 'test-1', method, params
});
const handshake = call('handshake');
assert.equal(handshake.ok, true);
assert.equal(handshake.result.engineCertified, false);
assert.deepEqual(handshake.result.capabilities, ['handshake','catalogSummary']);
const catalog = call('catalogSummary');
assert.equal(catalog.ok, true);
assert.equal(catalog.result.exercises, canonical.EXERCISES.length);
assert.equal(catalog.result.templates, canonical.TEMPLATES.length);
assert.ok(catalog.result.exercises > 0 && catalog.result.templates > 0);
for (const method of ['generateProgram','evaluateWorkoutProgression','deleteHistory','constructor','__proto__']) {
    const response = call(method);
    assert.equal(response.ok, false, method + ' must fail closed');
    assert.equal(response.error.code, 'UNKNOWN_METHOD');
}
assert.equal(dispatchEngineRequest({protocolVersion:2,id:'x',method:'handshake',params:{}}).error.code,'UNSUPPORTED_VERSION');
assert.equal(dispatchEngineRequest({protocolVersion:1,id:'',method:'handshake',params:{}}).error.code,'INVALID_REQUEST');
assert.equal(dispatchEngineRequest({protocolVersion:1,id:'x',method:'handshake',params:{extra:1}}).error.code,'INVALID_REQUEST');
assert.equal(JSON.parse(dispatchEngineJson('{')).error.code,'INVALID_JSON');
assert.deepEqual(JSON.parse(dispatchEngineJson(JSON.stringify({
    protocolVersion:1,id:'roundtrip',method:'catalogSummary',params:{}
}))),{...catalog,id:'roundtrip'});
console.log('PASS canonical JS bridge v1 handshake, catalog identity, JSON transport and fail-closed methods');
