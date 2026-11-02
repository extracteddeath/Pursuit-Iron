/** Program organization only. The canonical save planner remains the editor owner. */
import { parseStoredData, programSavePlan } from '../../modules/engine-api.js';
import { projectCanonicalSavedProgram } from './current-program-projection.mjs';
import { sha256Utf8, utf8ByteLength } from './json-identity.mjs';

const ensure=value=>{if(!value)throw new TypeError('Program edit rejected');};
const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length && keys.every(k=>Object.prototype.hasOwnProperty.call(v,k));
const label=v=>typeof v==='string' && v.trim().length>0 && v.length<=120 && !/[\x00-\x1f\x7f-\x9f]/.test(v);
const id=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(v);
const source='modules/engine-api.js:programSavePlan';
// These are the canonical shell's slot-keyed stores. Values stay byte-for-byte
// JSON-equivalent, including custom amplifiers and week-specific prescriptions.
const slotFields=['overrides','nextWeekPrescriptions','progStyle','slotBias',
  'autoBias','rounds','weekOff','pairs','ss'];

function requestFrom(raw) {
  ensure(typeof raw==='string' && utf8ByteLength(raw)<=32768);
  const r=JSON.parse(raw);
  ensure(exact(r,['format','operationId','reference','editedAt','name','days']) &&
    (r.format===1 || r.format===2) && id(r.operationId) && label(r.name) &&
    Number.isSafeInteger(r.editedAt) && r.editedAt>0 && r.editedAt<=8_640_000_000_000_000 &&
    exact(r.reference,['programId','programUpdatedAt','storeSavedAt','storeVersion','snapshotSha256']) &&
    id(r.reference.programId) && r.reference.storeVersion===13 &&
    /^[0-9a-f]{64}$/.test(r.reference.snapshotSha256) && Array.isArray(r.days) && r.days.length>=1 && r.days.length<=7);
  for(const d of r.days) ensure(exact(d,['id','label','slots']) && id(d.id) && label(d.label) &&
    Array.isArray(d.slots) && d.slots.length>=1 && d.slots.length<=30 &&
    d.slots.every(s=>Number.isInteger(s) && s>=0 && s<30) && new Set(d.slots).size===d.slots.length);
  ensure(new Set(r.days.map(d=>d.id)).size===r.days.length);
  return r;
}

export function prepareNativeProgramEdit(currentSnapshot,editJson) {
  ensure(typeof currentSnapshot==='string' && utf8ByteLength(currentSnapshot)<=1024*1024);
  parseStoredData(currentSnapshot);
  const before=JSON.parse(currentSnapshot),r=requestFrom(editJson),ref=r.reference;
  const currentSha256=sha256Utf8(currentSnapshot),requestSha256=sha256Utf8(editJson);
  const prior=before.nativeProgramEdit;
  const alreadyPresent=prior?.operationId===r.operationId;
  let candidateSnapshot=currentSnapshot;
  if(alreadyPresent) {
    ensure(exact(prior,['format','operationId','requestSha256','sourceSnapshotSha256','programId','editedAt']) &&
      prior.format===1 && prior.requestSha256===requestSha256 &&
      prior.sourceSnapshotSha256===ref.snapshotSha256 && prior.programId===ref.programId && prior.editedAt===r.editedAt);
  } else {
    const projected=projectCanonicalSavedProgram(currentSnapshot,ref.programId,currentSha256);
    ensure(Object.keys(projected.reference).every(k=>projected.reference[k]===ref[k]) && r.editedAt>ref.programUpdatedAt &&
      r.editedAt>ref.storeSavedAt);
    const original=before.saved.find(p=>p.id===ref.programId);
    ensure(original && r.days.length===original.days.length);
    // Imported legacy positional-only history must first acquire stable day
    // identity; never silently reinterpret an older workout after a reorder.
    const dayOrderChanged=r.days.some((d,i)=>d.id!==original.days[i].id);
    if(dayOrderChanged)ensure((before.history||[]).every(h=>h.programId!==original.id ||
      (typeof h.dayId==='string' && original.days.some(d=>d.id===h.dayId))));
    let edited={...original,name:r.name,updatedAt:r.editedAt};
    const days=r.days.map(d=>{
      const old=original.days.find(x=>x.id===d.id);
      ensure(old && d.slots.length>=1 && d.slots.length<=old.exercises.length &&
        (r.format===2 || d.slots.length===old.exercises.length) &&
        d.slots.every(s=>s<old.exercises.length));
      const moved=d.slots.length!==old.exercises.length || d.slots.some((s,i)=>s!==i);
      if(!moved)return {...old,label:d.label};
      const positions=new Map(d.slots.map((from,to)=>[from,to]));
      // Keep linked superset/circuit members together, in their authored order.
      // Moving a whole group is safe; splitting it requires an explicit pairing edit.
      for(let i=0;i<old.exercises.length;i++)if(original.ss?.[`${d.id}:${i}`])
        ensure(i+1<old.exercises.length && positions.has(i) && positions.has(i+1) &&
          positions.get(i+1)===positions.get(i)+1);
      for(const field of slotFields) {
        if(!Object.prototype.hasOwnProperty.call(original,field))continue;
        ensure(object(original[field]));
        const prefix=d.id+':',out={...(edited[field]||{})};
        for(const [key,value] of Object.entries(original[field])) {
          if(!key.startsWith(prefix))continue;
          const match=key.slice(prefix.length).match(/^(\d+)(:.*)?$/);
          if(!match)continue;
          const from=Number(match[1]);ensure(Number.isSafeInteger(from) && from>=0 && from<old.exercises.length);
          delete out[key];
        }
        for(const [key,value] of Object.entries(original[field])) {
          if(!key.startsWith(prefix))continue;
          const match=key.slice(prefix.length).match(/^(\d+)(:.*)?$/);
          if(match && positions.has(Number(match[1])))
            out[`${prefix}${positions.get(Number(match[1]))}${match[2]||''}`]=value;
        }
        edited[field]=out;
      }
      if(old.primaryIndex!==undefined)ensure(Number.isInteger(old.primaryIndex) && positions.has(old.primaryIndex));
      return {...old,label:d.label,exercises:d.slots.map(s=>old.exercises[s]),
        ...(old.primaryIndex!==undefined?{primaryIndex:positions.get(old.primaryIndex)}:{})};
    });
    edited={...edited,days,...(r.days.some(d=>
      d.slots.length!==original.days.find(x=>x.id===d.id).exercises.length ||
      d.slots.some((from,to)=>from!==to))?{edited:true}:{})};
    ensure(r.name!==original.name || JSON.stringify(days)!==JSON.stringify(original.days) ||
      r.days.some(d=>d.slots.length!==original.days.find(x=>x.id===d.id).exercises.length ||
        d.slots.some((from,to)=>from!==to)));
    // Scope is explicitly this program. A linked cycle and its sibling blocks
    // are never implicitly propagated by an organization-only save.
    const plan=programSavePlan(edited,before.saved,before.cycles||[],'program');
    ensure(plan.length===1 && plan[0].before.id===original.id && plan[0].after===edited);
    const candidate={...before,saved:before.saved.map(p=>p.id===edited.id?edited:p),savedAt:r.editedAt,
      nativeProgramEdit:{format:1,operationId:r.operationId,requestSha256,
        sourceSnapshotSha256:ref.snapshotSha256,programId:edited.id,editedAt:r.editedAt}};
    candidateSnapshot=JSON.stringify(candidate);
    ensure(utf8ByteLength(candidateSnapshot)<=1024*1024);
    parseStoredData(candidateSnapshot);
  }
  const program=projectCanonicalSavedProgram(candidateSnapshot,ref.programId,sha256Utf8(candidateSnapshot));
  ensure(program.program.name===r.name && program.program.days.length===r.days.length &&
    program.program.days.every((d,i)=>d.id===r.days[i].id && d.label===r.days[i].label));
  return {source,preparedOnly:true,approvedForWorkoutExecution:false,operationId:r.operationId,
    requestSha256,sourceSnapshotSha256:currentSha256,candidateSnapshot,
    candidateSnapshotSha256:sha256Utf8(candidateSnapshot),alreadyPresent,program};
}
