import assert from 'node:assert/strict';
import * as canonical from '../../modules/engine-api.js';
import { dispatchEngineRequest, dispatchEngineJson } from './protocol.mjs';
const call = (method, params = {}) => dispatchEngineRequest({
    protocolVersion: 1, id: 'test-1', method, params
});
const handshake = call('handshake');
assert.equal(handshake.ok, true);
assert.equal(handshake.result.engineCertified, false);
assert.deepEqual(handshake.result.capabilities, ['handshake','catalogSummary','progressionPreview']);
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

// Bounded canonical proof: requests cannot call arbitrary methods or write stored history.
// These are synthetic fixtures; Android is still deliberately unconnected.
const preview = (sets, extras = {}) => call('progressionPreview', {
    exerciseId: 'back_squat', repMin: 5, repMax: 8, targetRir: 2,
    sets, equipmentAvailable: ['barbell','rack'], ...extras
});
const below = [
    {load:200,reps:3,rir:null}, {load:205,reps:3,rir:null},
    {load:210,reps:2,rir:null}, {load:215,reps:2,rir:0}
];
const original = JSON.stringify(below);
const under = preview(below);
assert.equal(under.ok, true);
assert.equal(under.result.previewOnly, true);
assert.equal(under.result.action, 'decrease_load');
assert.equal(under.result.suggestedLoad, 185);
assert.equal(under.result.suggestedReps, 5);
assert.equal(JSON.stringify(below), original, 'diagnostic cannot edit caller sets');
const ceiling = preview(Array.from({length:3}, () => ({load:185,reps:8,rir:2})));
assert.equal(ceiling.result.action, 'increase_load');
assert.equal(ceiling.result.suggestedLoad, 190);
const middle = preview(Array.from({length:3}, () => ({load:185,reps:5,rir:2})));
assert.equal(middle.result.action, 'add_reps');
assert.equal(middle.result.suggestedLoad, 185);
for (const invalid of [
    {}, {sets:[]}, {sets:[{load:NaN,reps:8,rir:2}]},
    {sets:[{load:185,reps:8,rir:-1}]}, {repMin:9,repMax:8},
    {equipmentAvailable:['barbell','barbell']}, {extra:'unexpected'},
    {sets:Array.from({length:9},()=>({load:100,reps:8,rir:2}))}
]) {
    assert.equal(preview(below, invalid).error?.code,'INVALID_REQUEST');
}
assert.equal(JSON.parse(dispatchEngineJson('x'.repeat(16385))).error.code,'INVALID_JSON');
const overRoundTrip = JSON.parse(dispatchEngineJson(JSON.stringify({
    protocolVersion:1,id:'preview-1',method:'progressionPreview',
    params:{exerciseId:'back_squat',repMin:5,repMax:8,targetRir:2,sets:below,equipmentAvailable:['barbell','rack']}
})));
assert.deepEqual(overRoundTrip, {...under,id:'preview-1'});

console.log('PASS canonical JS bridge v1 handshake, read-only progression fixtures, strict bounds and fail-closed methods');
