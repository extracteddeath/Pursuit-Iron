import assert from 'node:assert/strict';
import { historyVolumeIn, weeklyRecap } from '../modules/App.js';

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
console.log('PASS history volume integrity: ledger-first tonnage, stale-cache repair, sub-set exclusion, legacy fallback, and unit-normalized recap.');
