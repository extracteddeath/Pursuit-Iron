import assert from 'node:assert/strict';
import { historyVolumeIn, weeklyRecap, loggedVolume, loggedSubVolume } from '../modules/App.js';

const now=Date.now();
const stale={id:'stale',date:now,unit:'lb',volume:1554,setsDone:4,perf:{'back-squat':{sets:[
 {w:200,r:3},{w:205,r:3},{w:210,r:2},{w:215,r:2},{w:50,r:5,sub:true}
]}}};
assert.equal(historyVolumeIn(stale,'lb'),2065,'real working sets must override stale cached h.volume');
assert.ok(Math.abs(historyVolumeIn(stale,'kg')-(2065/2.2046226218))<1e-9,'ledger volume must convert units');

const legacy={id:'legacy',date:now-1000,unit:'lb',volume:1000,setsDone:1,perf:{}};
assert.equal(historyVolumeIn(legacy,'lb'),1000,'old entries without a set ledger keep cached volume');

const kg={id:'kg',date:now-2000,unit:'kg',volume:1,setsDone:1,perf:{'back-squat':{sets:[{w:100,r:10}]}}};
const recap=weeklyRecap([stale,kg],'lb');
assert.equal(recap.count,2);
assert.equal(recap.vol,Math.round(2065+1000*2.2046226218),'weekly recap must recompute ledgers and normalize every session to the displayed unit');

// Resolve revisions before applying a date window: editing a session can move it into or out of
// the week, and a stale revision must not keep its former dose or PR alive.
const day = 86400000;
const workout = (id, programId, date, n, weight, updatedAt = date) => ({
 id, programId, date, updatedAt, unit:'lb', setsDone:n,
 perf:{'back-squat':{weight,reps:8,sets:Array.from({length:n},()=>({w:weight,r:8}))}}
});
const baseline = workout('baseline','p1',now-10*day,1,100);
const old = workout('edited','p1',now-2*day,8,300);
const corrected = workout('edited','p1',old.date,2,80,now);
const movedOld = workout('moved','p1',now-day,6,200);
const moved = workout('moved','p1',now-9*day,1,90,now);
const otherProgram = workout('edited','p2',now-day,1,80);
const bodyweight = {date:now-day,unit:'lb',setsDone:1,perf:{'bw-pullup':{weight:0,reps:8,sets:[
 {w:0,r:8},{w:0,r:3,sub:true},{w:0,r:5,warm:true}
]}}};
const legacyExposure = workout(null,'p1',now-day,1,80);
const winners = [baseline,corrected,moved,otherProgram,bodyweight,legacyExposure,structuredClone(legacyExposure)];
const revisions = [...winners,old,movedOld]; // input order cannot resurrect an older revision
const original = structuredClone(revisions);
const weekly = weeklyRecap(winners,'lb');
assert.equal(weekly.count,5);
assert.equal(weekly.sets,6);
assert.equal(weekly.vol,3200);
assert.equal(weekly.prevCount,2);
assert.equal(weekly.prs,0);
assert.deepEqual(weeklyRecap(revisions,'lb'),weekly,'recap must use current revisions for counts, dose, top muscle and PRs');
assert.deepEqual(loggedVolume(revisions,7,now),loggedVolume(winners,7,now),'muscle dose must discard superseded revisions before windowing');
assert.deepEqual(loggedSubVolume(revisions,7,now),loggedSubVolume(winners,7,now),'regional dose must share the same revision ownership');
assert.equal(loggedVolume(revisions,7,now).quads,5,'identical row IDs in different programs and independent ID-less legacy sessions survive');
assert.equal(loggedVolume(revisions,7,now).lats,1,'zero-load bodyweight work still counts; warmups and extensions stay excluded');
const malformed = [...revisions,null,{...old,id:'bad-date',date:'invalid'}, {...old,id:'missing-date',date:null}];
assert.deepEqual(weeklyRecap(malformed,'lb'),weekly,'unusable imported dates cannot contribute recap evidence');
assert.deepEqual(loggedVolume(malformed,7,now),loggedVolume(winners,7,now));
assert.deepEqual(loggedSubVolume(malformed,7,now),loggedSubVolume(winners,7,now));
assert.deepEqual(revisions,original,'summary calculations must leave persisted history untouched');
console.log('PASS history volume integrity: ledger-first tonnage, stale-cache repair, sub-set exclusion, legacy fallback, and unit-normalized recap.');
console.log('PASS weekly history revision ownership: recap/PRs, muscle and regional dose, edited date windows, program identities, legacy exposures, and invalid imports.');
