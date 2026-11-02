/** Program organization only. The canonical save planner remains the editor owner. */
import { parseStoredData, programSavePlan, EX_BY_ID, expandEquipment, nextExerciseIdForShellExercise, isBarLike } from '../../modules/engine-api.js';
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
  ensure(exact(r,['format','operationId','reference','editedAt','name','days',
    ...(r?.format===3?['replacement']:[])]) &&
    [1,2,3].includes(r.format) && id(r.operationId) && label(r.name) &&
    Number.isSafeInteger(r.editedAt) && r.editedAt>0 && r.editedAt<=8_640_000_000_000_000 &&
    exact(r.reference,['programId','programUpdatedAt','storeSavedAt','storeVersion','snapshotSha256']) &&
    id(r.reference.programId) && r.reference.storeVersion===13 &&
    /^[0-9a-f]{64}$/.test(r.reference.snapshotSha256) && Array.isArray(r.days) && r.days.length>=1 && r.days.length<=7);
  for(const d of r.days) ensure(exact(d,['id','label','slots']) && id(d.id) && label(d.label) &&
    Array.isArray(d.slots) && d.slots.length>=1 && d.slots.length<=30 &&
    d.slots.every(s=>Number.isInteger(s) && s>=0 && s<30) && new Set(d.slots).size===d.slots.length);
  ensure(new Set(r.days.map(d=>d.id)).size===r.days.length);
  if(r.format===3)ensure(exact(r.replacement,['dayId','sourceSlot','exerciseId']) &&
    id(r.replacement.dayId) && Number.isInteger(r.replacement.sourceSlot) &&
    r.replacement.sourceSlot>=0 && r.replacement.sourceSlot<30 &&
    id(r.replacement.exerciseId) && r.replacement.exerciseId.length<=80);
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
    if(r.format===3) {
      // This is deliberately one substitution, NOT a roster reauthoring,
      // historical load transfer, or cycle-wide mutation.
      ensure(r.name===original.name && r.days.every((d,i)=>{
        const old=original.days[i];
        return d.id===old.id && d.label===old.label && d.slots.length===old.exercises.length &&
          d.slots.every((slot,j)=>slot===j);
      }));
      const change=r.replacement,day=original.days.find(d=>d.id===change.dayId);
      ensure(original.engineSource==='pursuit-next' && day &&
        change.sourceSlot<day.exercises.length && change.sourceSlot!==(Number.isInteger(day.primaryIndex)?day.primaryIndex:0) &&
        !original.ss?.[`${day.id}:${change.sourceSlot}`] &&
        (change.sourceSlot===0 || !original.ss?.[`${day.id}:${change.sourceSlot-1}`]));
      const oldId=day.exercises[change.sourceSlot],from=EX_BY_ID[oldId],to=EX_BY_ID[change.exerciseId];
      ensure(from && to && from.id!==to.id && from.part===to.part &&
        from.type===to.type && (!from.pattern || !to.pattern || from.pattern===to.pattern) &&
        !day.exercises.includes(to.id));
      const available=expandEquipment(original.config?.equipment);
      ensure(Array.isArray(original.config?.equipment) && to.equip.every(e=>available.has(e)) &&
        (to.equip.length>0 || !original.config.noBodyweight));
      const cap=original.config.barbellCap;
      if(Number.isInteger(cap) && cap>=0) {
        const bars=day.exercises.reduce((n,id,i)=>
          n+(isBarLike(EX_BY_ID[i===change.sourceSlot?to.id:id]?.equip)?1:0),0);
        ensure(bars<=cap);
      }
      const sourceKey=`${day.id}:${change.sourceSlot}`;
      // Replacing the movement explicitly discards its *slot-local* loading,
      // progression, amplifier and alternate-week settings. Do not transfer
      // any source exercise targets to a different physical movement.
      const cleaned={};
      for(const field of slotFields) {
        if(!Object.prototype.hasOwnProperty.call(original,field))continue;
        const store=original[field];
        ensure(object(store));
        const without={...store};
        for(const key of Object.keys(without))
          if(key===sourceKey || key.startsWith(sourceKey+':'))delete without[key];
        cleaned[field]=without;
      }
      const engineId=nextExerciseIdForShellExercise(to);
      ensure(typeof engineId==='string' && engineId.length>0);
      const newDay={...day,exercises:day.exercises.map((id,i)=>i===change.sourceSlot?to.id:id)};
      edited={...edited,...cleaned,edited:true,days:original.days.map(d=>d.id===day.id?newDay:d),
        overrides:{...(cleaned.overrides||{}),[sourceKey]:{
          nextEngine:true,legacyExerciseId:to.id,nextExerciseId:engineId
        }}};
    }
    const days=r.format===3?edited.days:r.days.map(d=>{
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
    if(r.format!==3) {
      edited={...edited,days,...(r.days.some(d=>
        d.slots.length!==original.days.find(x=>x.id===d.id).exercises.length ||
        d.slots.some((from,to)=>from!==to))?{edited:true}:{})};
      ensure(r.name!==original.name || JSON.stringify(days)!==JSON.stringify(original.days) ||
        r.days.some(d=>d.slots.length!==original.days.find(x=>x.id===d.id).exercises.length ||
          d.slots.some((from,to)=>from!==to)));
    }
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
  if(r.format===3) {
    const day=program.program.days.find(d=>d.id===r.replacement.dayId);
    ensure(day?.exerciseIds[r.replacement.sourceSlot]===r.replacement.exerciseId);
  }
  return {source,preparedOnly:true,approvedForWorkoutExecution:false,operationId:r.operationId,
    requestSha256,sourceSnapshotSha256:currentSha256,candidateSnapshot,
    candidateSnapshotSha256:sha256Utf8(candidateSnapshot),alreadyPresent,program};
}
