import assert from 'node:assert/strict';
import fs from 'node:fs';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { inspectNativeHistoryPage,inspectNativeHistory,prepareNativeHistoryCorrection,NATIVE_HISTORY_PAGE_SIZE } from './native-history.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';
import { nextSessionCursor } from '../../modules/engine-api.js';

let source=prepareNativeProgram(JSON.stringify({creationId:'create-pages',programId:'program-pages',createdAt:1000,seed:223,
  profile:{name:'History pages',goal:'both',experience:'intermediate',unit:'kg',split:'full_body',days:3,session:'s60',
    equipment:nativeProgramOptions().equipment,allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot;
assert.deepEqual(inspectNativeHistoryPage(source,JSON.stringify({offset:0,snapshotSha256:null})).entries,[]);
for(let n=0;n<4;n++) {
  const plan=prepareNativeWorkout(source,JSON.stringify({programId:'program-pages',workoutId:`native-${n}`,startedAt:2000+n*1000000}));
  const issued=JSON.stringify(plan),slots=issuedNativeWorkoutFrom(source,issued).slots;
  const journal={format:2,session:plan.session,slots,startRevision:0,events:slots.map((slot,i)=>({
    operationId:`set-${n}-${i}`,slot,disposition:'COMPLETED',values:{load:n===0?'200':'90',unit:'kg',reps:6,rir:null},
    fromRevision:i,toRevision:i+1})),finish:{operationId:`finish-${n}`,fromRevision:slots.length,toRevision:slots.length+1,
      completedAt:plan.session.startedAt+60000,durationMs:60000}};
  source=prepareCanonicalWorkoutHistory(source,source,JSON.stringify(journal),issued).candidateSnapshot;
}
// Earlier journal-only native completions retain real summaries, even when their
// original set observations cannot be projected into an editable detail view.
const golden=JSON.parse(fs.readFileSync('android-native/domain-contract/src/test/resources/finished-journal-golden.json','utf8'));
const legacy=golden.preparation.historyEntry;
const store=JSON.parse(source);
for(let n=0;n<25;n++)store.history.push({...structuredClone(legacy),id:`legacy-${String(n).padStart(2,'0')}`,
  date:100000+n*30000,updatedAt:100000+n*30000});
store.history.push({...structuredClone(legacy),id:'deleted-history',date:9999999});
store.tombs['deleted-history']=9999999;
store.history.push({id:'web-history-not-imported',date:9999999,perf:{}});
source=JSON.stringify(store);
const query=(offset=0,snapshotSha256=null)=>JSON.stringify({offset,snapshotSha256});
const first=inspectNativeHistoryPage(source,query()),ids=[];
assert.equal(NATIVE_HISTORY_PAGE_SIZE,12);assert.equal(first.total,29);assert.equal(first.entries[0].historyId,'native-3');
let page=first;
for(;;) {
  assert.ok(page.entries.length<=12);
  ids.push(...page.entries.map(e=>e.historyId));
  if(page.nextOffset===null)break;
  page=inspectNativeHistoryPage(source,query(page.nextOffset,page.snapshotSha256));
}
assert.equal(ids.length,29);assert.equal(new Set(ids).size,29);assert.equal(ids.at(-1),'native-0');
assert.ok(page.entries.some(e=>e.historyId==='native-0' && e.detailsAvailable));
assert.ok(first.entries.some(e=>e.historyId.startsWith('legacy') && !e.detailsAvailable));
assert.ok(!ids.includes('deleted-history') && !ids.includes('web-history-not-imported'));
const before=JSON.parse(source),old=inspectNativeHistory(source,'native-0'),latest=before.history.find(e=>e.id==='native-3');
const lift=old.exercises.find(e=>latest.perf[e.id]);assert.ok(lift);
const result=prepareNativeHistoryCorrection(source,JSON.stringify({operationId:'correct-older-workout',snapshotSha256:old.snapshotSha256,
  historyId:old.historyId,entrySha256:old.entrySha256,correctedAt:5000000,changes:lift.sets.map(s=>({exerciseId:lift.id,
    setIndex:s.setIndex,load:'50',reps:s.reps,rir:s.rir}))}));
const after=JSON.parse(result.candidateSnapshot);
assert.deepEqual(after.perf[lift.id],before.perf[lift.id],'an older correction cannot replace the newer Last result');
assert.equal(result.history.exercises.find(e=>e.id===lift.id).record.w,90,'the old false high cannot survive as a record');
assert.deepEqual(nextSessionCursor(after.saved[0],after.history),nextSessionCursor(before.saved[0],before.history));
assert.throws(()=>inspectNativeHistoryPage(result.candidateSnapshot,query(12,first.snapshotSha256)),'pages cannot mix store revisions');
const refreshed=inspectNativeHistoryPage(result.candidateSnapshot,query());
const order=[];page=refreshed;
for(;;) {
  order.push(...page.entries.map(e=>e.historyId));if(page.nextOffset===null)break;
  page=inspectNativeHistoryPage(result.candidateSnapshot,query(page.nextOffset,page.snapshotSha256));
}
assert.deepEqual(order,ids,'editing must not move an old workout to the top of chronological history');
for(const bad of [query(1),query(12),query(1992,first.snapshotSha256),query(0,'f'.repeat(64)),
  JSON.stringify({offset:0,snapshotSha256:null,includeWebHistory:true})])
  assert.throws(()=>inspectNativeHistoryPage(source,bad));
const duplicate=JSON.parse(source);duplicate.history.push(duplicate.history.find(e=>e.id==='native-0'));
assert.throws(()=>inspectNativeHistoryPage(JSON.stringify(duplicate),query()));
const request=JSON.stringify({protocolVersion:1,id:'history-page',method:'inspectNativeHistoryPage',params:{rawSnapshot:source,queryJson:query()}});
assert.equal(JSON.parse(dispatchWorkoutStoreJson(request)).ok,true);
assert.equal(JSON.parse(dispatchEngineJson(request)).ok,false);assert.equal(JSON.parse(dispatchBackupSchemaJson(request)).ok,false);
const tied=JSON.parse(source);for(const e of tied.history)if(e.id.startsWith('legacy'))e.date=700000;
const tiedPage=inspectNativeHistoryPage(JSON.stringify(tied),query());
const tiedIds=tiedPage.entries.filter(e=>e.historyId.startsWith('legacy')).map(e=>e.historyId);
assert.deepEqual(tiedIds,[...tiedIds].sort());
// Filters apply before pagination and preserve exact request identity, not a
// client-side slice of whichever twelve entries happened to be visible.
const filteredStore=JSON.parse(source);
for(const e of filteredStore.history) {
  if(e.id.startsWith('legacy')) { e.programName='Legacy Progress';e.dayLabel='Full Body'; }
  if(e.id.startsWith('native-')) { e.programName='Strength [A].*';e.dayLabel=['Upper Body','Lower Body'][Number(e.id.at(-1))%2]; }
}
const filteredSource=JSON.stringify(filteredStore);
const filter=(search='',fromInclusive=null,untilExclusive=null)=>({search,fromInclusive,untilExclusive});
const filteredQuery=(f,offset=0,snapshotSha256=null)=>JSON.stringify({offset,snapshotSha256,filter:f});
const full=inspectNativeHistoryPage(filteredSource,filteredQuery(filter('  PrOgReSs   BODY  ')));
assert.equal(full.total,25);assert.equal(full.unfilteredTotal,29);
assert.deepEqual(full.filter,filter('  PrOgReSs   BODY  '));
const filteredIds=[];page=full;
for(;;) {
  filteredIds.push(...page.entries.map(e=>e.historyId));
  if(page.nextOffset===null)break;
  page=inspectNativeHistoryPage(filteredSource,filteredQuery(page.filter,page.nextOffset,page.snapshotSha256));
}
assert.equal(filteredIds.length,25);assert.equal(new Set(filteredIds).size,25);
assert.ok(filteredIds.every(id=>id.startsWith('legacy')));assert.equal(page.entries.length,1);
const literal=inspectNativeHistoryPage(filteredSource,filteredQuery(filter('[a].* upper')));
assert.deepEqual(literal.entries.map(e=>e.historyId),['native-2','native-0']);
const window=inspectNativeHistoryPage(filteredSource,filteredQuery(filter('legacy',100000,400000)));
assert.equal(window.total,10);assert.equal(window.entries.at(-1).date,100000);
assert.ok(window.entries.every(e=>e.date>=100000 && e.date<400000));
assert.equal(inspectNativeHistoryPage(filteredSource,filteredQuery(filter('',62000,62001))).entries[0].historyId,'native-0');
assert.equal(inspectNativeHistoryPage(filteredSource,filteredQuery(filter('',null,62000))).total,0);
const none=inspectNativeHistoryPage(filteredSource,filteredQuery(filter('not a saved program')));
assert.equal(none.total,0);assert.equal(none.unfilteredTotal,29);assert.equal(none.nextOffset,null);assert.deepEqual(none.entries,[]);
assert.equal(inspectNativeHistoryPage(filteredSource,filteredQuery(filter('   '))).total,29);
const unicode=JSON.parse(filteredSource);unicode.history.find(e=>e.id==='native-0').programName='Fuerza Ágil';
assert.equal(inspectNativeHistoryPage(JSON.stringify(unicode),filteredQuery(filter('FUERZA ágil'))).total,1);
const filterView=inspectNativeHistory(filteredSource,'native-0'),filterLift=filterView.exercises[0];
const filteredEdit=prepareNativeHistoryCorrection(filteredSource,JSON.stringify({operationId:'filter-correction',
  snapshotSha256:filterView.snapshotSha256,historyId:filterView.historyId,entrySha256:filterView.entrySha256,correctedAt:5100000,
  changes:[changeForFilter(filterLift)]}));
function changeForFilter(lift) { const s=lift.sets[0];return {exerciseId:lift.id,setIndex:s.setIndex,load:'55',reps:s.reps,rir:s.rir}; }
const afterFilteredEdit=inspectNativeHistoryPage(filteredEdit.candidateSnapshot,filteredQuery(literal.filter));
assert.deepEqual(afterFilteredEdit.entries.map(e=>e.historyId),literal.entries.map(e=>e.historyId));
assert.deepEqual(afterFilteredEdit.filter,literal.filter);
assert.throws(()=>inspectNativeHistoryPage(filteredEdit.candidateSnapshot,filteredQuery(full.filter,12,full.snapshotSha256)));
for(const bad of [filter('x'.repeat(81)),filter('bad\nquery'),filter('bad\u0085query'),filter(17),
  filter('',1.5),filter('',0,0),filter('',20,10),filter('',null,8640000000000002),
  {...filter(),includeDeleted:true},{search:''},null])
  assert.throws(()=>inspectNativeHistoryPage(filteredSource,filteredQuery(bad)));
const filteredWire=JSON.stringify({protocolVersion:1,id:'filtered-history',method:'inspectNativeHistoryPage',
  params:{rawSnapshot:filteredSource,queryJson:filteredQuery(filter('legacy',100000,400000))}});
assert.equal(JSON.parse(dispatchWorkoutStoreJson(filteredWire)).result.total,10);
assert.equal(JSON.parse(dispatchEngineJson(filteredWire)).ok,false);assert.equal(JSON.parse(dispatchBackupSchemaJson(filteredWire)).ok,false);
console.log('PASS native history pages: real completions, stable filtered pagination, literal multi-term/Unicode search, date boundaries, summary-only rows, tombstones, isolated channels, stale-page refusal and older-correction record/Last/cursor integrity');
