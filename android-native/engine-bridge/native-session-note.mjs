import { utf8ByteLength } from './json-identity.mjs';

const id=value=>typeof value==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(value);
export function validNativeNoteText(text) {
  if(typeof text!=='string' || text.startsWith('\ufeff') || text.length>2000 || /[\x00-\x08\x0b-\x1f\x7f-\x9f]/.test(text) ||
    utf8ByteLength(text)>8192)return false;
  for(let i=0;i<text.length;i++) {
    const c=text.charCodeAt(i);
    if(c>=0xd800 && c<=0xdbff) {
      const next=text.charCodeAt(++i);if(!(next>=0xdc00 && next<=0xdfff))return false;
    } else if(c>=0xdc00 && c<=0xdfff)return false;
  }
  return true;
}

export function readNativeSessionNote(note,startedAt,completedAt) {
  const keys=['operationId','text','updatedAt','replacesOperationId'];
  if(!note || typeof note!=='object' || Array.isArray(note) || Object.keys(note).length!==keys.length ||
      !keys.every(k=>Object.prototype.hasOwnProperty.call(note,k)) ||
      !id(note.operationId) || !validNativeNoteText(note.text) ||
      !Number.isSafeInteger(note.updatedAt) || note.updatedAt<startedAt || note.updatedAt>completedAt ||
      !(note.replacesOperationId===null || id(note.replacesOperationId) && note.replacesOperationId!==note.operationId))
    throw new TypeError('Invalid bounded session note');
  return note;
}
