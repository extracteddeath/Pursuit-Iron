/** Native factual-history preservation around the current positive-rep edit API. */
import { normalizeEditedHistoryEntry } from '../../modules/engine-api.js';

const clone=v=>JSON.parse(JSON.stringify(v));
const ensure=v=>{if(!v)throw new TypeError('Native history evidence normalization rejected');};

/** The legacy edit helper drops zero reps. Preserve verified failed observations
 * without turning them into a successful rep or prescribing a replacement load.
 * Positive working-set summaries still come from the canonical helper. */
export function normalizeNativeHistoryEntry(original,draft) {
  const result=clone(normalizeEditedHistoryEntry(original,draft));
  for(const performance of Object.values(result.perf))for(const set of performance.sets)
    if(set.r>0)delete set.failedAttempt;
  let restored=false;
  for(const [id,performance] of Object.entries(draft.perf || {})) {
    const failures=(performance.sets || []).filter(set=>set.r===0);
    if(!failures.length)continue;
    ensure(failures.every(set=>set.failedAttempt===true && !set.warm && !set.sub && Number.isFinite(set.w) && set.w>0));
    const sets=clone(performance.sets);
    for(const set of sets) {
      ensure(Number.isSafeInteger(set.r) && set.r>=0 && set.r<=100 && Number.isFinite(set.w) && set.w>=0);
      if(set.r===0)set.failedAttempt=true;else delete set.failedAttempt;
      if(set.rir==null) { delete set.rir;delete set.rirReported; }
      else ensure(Number.isFinite(set.rir) && set.rir>=0 && set.rir<=10);
    }
    const positive=result.perf[id];
    result.perf[id]={...clone(positive || performance),sets,date:result.date,
      // A zero-only row's displayed load is the heaviest actual failed attempt,
      // matching the logger's factual summary; reps stay exactly zero.
      ...(!positive?{weight:Math.max(...failures.map(set=>set.w)),reps:0}:{})};
    restored=true;
  }
  if(restored) {
    result.perf=Object.fromEntries(Object.keys(draft.perf).filter(id=>Object.hasOwn(result.perf,id)).map(id=>[id,result.perf[id]]));
    result.volume=Math.round(Object.values(result.perf).flatMap(p=>p.sets)
      .filter(set=>!set.warm).reduce((sum,set)=>sum+set.w*set.r,0));
  }
  return result;
}
