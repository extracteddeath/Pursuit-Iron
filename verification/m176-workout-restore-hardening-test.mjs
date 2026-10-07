import { productionSource } from './production-source.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES, sessionSnapshotStatus, mergeSessionData } from '../modules/App.js';

const ids=EXERCISES.slice(0,3).map(ex=>ex.id);
assert.equal(ids.length,3);
const [a,b,c]=ids;
const snap={
  schemaVersion:2,programId:'p1',dayId:'d1',weekIndex:2,dayExSig:`${a}|${b}`,
  data:[
    {id:a,slot:0,note:'keep A',sets:[{w:100,r:8,done:true}]},
    {id:b,slot:1,note:'keep B',sets:[{w:50,r:12,done:true}]}
  ]
};
const ctx={programId:'p1',dayId:'d1',weekIndex:2,dayExSig:`${a}|${b}`};
assert.equal(sessionSnapshotStatus(snap,ctx),'resume','same program/day/week/signature must resume exactly');
assert.equal(sessionSnapshotStatus(snap,{...ctx,dayExSig:`${b}|${a}|${c}`}),'partial','program edits must trigger partial resume instead of discarding work');
assert.equal(sessionSnapshotStatus(snap,{...ctx,programId:'other'}),'none');
assert.equal(sessionSnapshotStatus(snap,{...ctx,dayId:'other'}),'none');
assert.equal(sessionSnapshotStatus(snap,{...ctx,weekIndex:3}),'none');
assert.equal(sessionSnapshotStatus({...snap,data:[{id:a,sets:[null]}]},ctx),'none','malformed persisted rows must be rejected instead of crashing resume');

const fresh=[
  {id:b,slot:0,note:'fresh B',sets:[{w:0,r:0}]},
  {id:c,slot:1,note:'fresh C',sets:[{w:0,r:0}]},
  {id:a,slot:2,note:'fresh A',sets:[{w:0,r:0}]}
];
const merged=mergeSessionData(snap.data,fresh);
assert.deepEqual(merged.map(x=>x.id),[b,c,a],'current program owns exercise order');
assert.deepEqual(merged[0].sets,snap.data[1].sets,'logged sets must follow the matching exercise across a reorder');
assert.equal(merged[0].note,'keep B');
assert.deepEqual(merged[1].sets,fresh[1].sets,'new/replacement exercise must use the current prescription');
assert.deepEqual(merged[2].sets,snap.data[0].sets);
assert.equal(merged[2].note,'keep A');

const src=productionSource();
for(const marker of [
  'schemaVersion: 2, programId: program.id, dayId: day.id, weekIndex, dayExSig',
  'elapsedMs: runElapsedMs()',
  'restEndMs: restEndRef.current',
  'const minimizeWorkout = () => { if (!persistLive()) return;',
  'useLayoutEffect(() => {\n        persistLive();',
  'document.addEventListener("visibilitychange", onVis);',
  'window.addEventListener("pagehide", save);',
  'document.addEventListener("freeze", save);',
  'return () => { document.removeEventListener("visibilitychange", onVis); window.removeEventListener("pagehide", save); document.removeEventListener("freeze", save); };'
]) assert.ok(src.includes(marker),`missing live-session persistence marker: ${marker}`);
assert.ok(src.includes('const startRef = useRef((liveMatch && Number.isFinite(liveMatch.elapsedMs))'),'workout clock must resume from actual training time, not wall time away');
assert.ok(src.includes('if (m.restPaused && m.restRemain > 0)'),'paused rest timer must restore remaining seconds');
assert.ok(src.includes('if (m.restEndMs && m.restEndMs > Date.now())'),'running rest timer must restore by absolute end time');
console.log('M176 workout restore hardening OK: exact/partial resume, malformed-data rejection, merge preservation, timer state, and mobile lifecycle flush hooks are release-gated.');
