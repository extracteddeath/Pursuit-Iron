/** Pinned exercise facts only; equipment fields come from the canonical catalog. */
import { EX_BY_ID,setupFieldsWithStored } from '../../modules/engine-api.js';
import { validNativeNoteText } from './native-session-note.mjs';
import { equalJson } from './json-identity.mjs';

const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length && keys.every(k=>Object.hasOwn(v,k));
const id=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(v);
const key=v=>typeof v==='string' && v.length>=1 && v.length<=64 && validNativeNoteText(v) &&
  !/[\n\t]/.test(v) && !['__proto__','constructor','prototype'].includes(v);
const ensure=v=>{if(!v)throw new TypeError('Pinned exercise note rejected');};
export const validPinnedSetup=v=>object(v) && Object.keys(v).length<=32 &&
  Object.entries(v).every(([k,text])=>key(k) && validNativeNoteText(text) && text.length<=200);

/** Unsupported legacy values remain untouched; they never become an empty editable note. */
export function pinnedExerciseContext(store,exerciseId) {
  const notes=store.exNotes || {},setups=store.exSetup || {};
  const text=Object.hasOwn(notes,exerciseId)?notes[exerciseId]:'',setup=Object.hasOwn(setups,exerciseId)?setups[exerciseId]:{};
  if(!validNativeNoteText(text) || !validPinnedSetup(setup))return null;
  const fields=setupFieldsWithStored(EX_BY_ID[exerciseId],Object.fromEntries(Object.keys(setup).map(k=>[k,'x'])))
    .map(f=>({key:f.key,label:f.label,orphan:!!f.orphan}));
  if(fields.length>32)return null;
  return {text,setup,fields};
}

export function pinnedExerciseEdits(plan,journal) {
  if(journal.format!==7)return [];
  const edits=journal.exerciseNotes,operations=new Set(journal.events.map(e=>e.operationId));
  operations.add(journal.finish.operationId);
  if(journal.note)operations.add(journal.note.operationId);
  for(const e of journal.warmups)operations.add(e.operationId);
  ensure(plan && Array.isArray(edits) && edits.length>=1 && edits.length<=128);
  const latest=new Map();
  for(const edit of edits) {
    ensure(exact(edit,['operationId','exerciseId','text','setup','updatedAt','replacesOperationId']) &&
      id(edit.operationId) && !operations.has(edit.operationId) && id(edit.exerciseId) &&
      validNativeNoteText(edit.text) && validPinnedSetup(edit.setup) &&
      Number.isSafeInteger(edit.updatedAt) && edit.updatedAt>=journal.session.startedAt &&
      edit.updatedAt<=journal.finish.completedAt);
    const rows=plan.rows.filter(r=>r.id===edit.exerciseId),context=rows[0]?.pinnedNote;
    ensure(context && rows.every(r=>equalJson(r.pinnedNote,context)) &&
      Object.keys(edit.setup).every(k=>context.fields.some(f=>f.key===k)));
    const prior=latest.get(edit.exerciseId);
    ensure(edit.replacesOperationId===(prior?.operationId ?? null) &&
      (!prior || edit.updatedAt>prior.updatedAt));
    operations.add(edit.operationId);latest.set(edit.exerciseId,edit);
  }
  return edits;
}

/** Applied only inside the existing atomic history publication. Empty values are explicit clears. */
export function applyPinnedExerciseEdits(store,edits) {
  const exNotes={...store.exNotes},exSetup={...store.exSetup};
  for(const edit of edits) {exNotes[edit.exerciseId]=edit.text;exSetup[edit.exerciseId]=edit.setup;}
  return {...store,exNotes,exSetup};
}
