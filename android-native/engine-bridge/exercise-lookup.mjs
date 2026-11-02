// Read-only catalog projection from canonical TRAINING-DOMAIN exercise IDs.
// Intentionally excludes app user records, custom exercises and training writes.
import { EXERCISES, EX_BY_ID, expandEquipment, nextExerciseIdForShellExercise } from '../../modules/engine-api.js';

const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
const plain = x => x !== null && typeof x === 'object' &&
  !Array.isArray(x) &&
  (Object.getPrototypeOf(x) === Object.prototype || Object.getPrototypeOf(x) === null);
const exerciseId = /^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$/;

export function validExerciseLookupParams(params) {
  if (!plain(params) || Object.keys(params).length !== 1 ||
      !own(params, 'ids') || !Array.isArray(params.ids) ||
      params.ids.length < 1 || params.ids.length > 12) return false;
  return params.ids.every(x => typeof x === 'string' && exerciseId.test(x)) &&
    new Set(params.ids).size === params.ids.length;
}

const bounded = (value, max) =>
  typeof value === 'string' && value.length > 0 && value.length <= max ? value : null;

function projectExercise(raw) {
  if (!raw || !bounded(raw.id, 80) || !bounded(raw.name, 120) ||
      !bounded(raw.part, 48) || !['compound', 'isolation'].includes(raw.type) ||
      !Array.isArray(raw.equip) || raw.equip.length > 20 ||
      !raw.equip.every(x => bounded(x, 40)) ||
      !Array.isArray(raw.rep) || raw.rep.length !== 2 ||
      !raw.rep.every(n => Number.isInteger(n) && n >= 1 && n <= 100) ||
      raw.rep[0] > raw.rep[1])
    throw new Error('Invalid canonical exercise metadata');
  return {
    id: raw.id, name: raw.name, part: raw.part, type: raw.type,
    equipment: [...raw.equip],
    repMin: raw.rep[0], repMax: raw.rep[1],
    pattern: bounded(raw.pattern, 80)
  };
}

export function canonicalExerciseLookup(params) {
  if (!validExerciseLookupParams(params))
    throw new TypeError('Invalid exercise lookup params');
  if (!Array.isArray(EXERCISES))
    throw new Error('Canonical exercise catalog unavailable');
  // Stable source ordering is dictated by caller IDs, not Map/DOM collection order.
  const index = new Map(EXERCISES.map(item => [item.id, item]));
  const exercises = [];
  const missingIds = [];
  for (const id of params.ids) {
    const found = index.get(id);
    if (!found) missingIds.push(id);
    else exercises.push(projectExercise(found));
  }
  return {
    source: 'modules/engine-api.js:EXERCISES',
    readOnly: true,
    exercises,
    missingIds
  };
}


/**
 * Catalog-only candidates for a same-stimulus accessory. No personal data or
 * training decisions cross this read-only channel. Actual saved-program
 * compatibility is checked again by prepareNativeProgramEdit against the
 * current canonical store revision.
 */
export function validExerciseAlternativesParams(p) {
  return plain(p) && Object.keys(p).length===4 &&
    typeof p.sourceId==='string' && exerciseId.test(p.sourceId) &&
    typeof p.allowBodyweight==='boolean' &&
    Array.isArray(p.excludeIds) && p.excludeIds.length<=30 &&
    p.excludeIds.every(x=>typeof x==='string' && exerciseId.test(x)) &&
    new Set(p.excludeIds).size===p.excludeIds.length &&
    Array.isArray(p.equipment) && p.equipment.length>=1 && p.equipment.length<=60 &&
    p.equipment.every(x=>typeof x==='string' && /^[a-z][a-z0-9_-]{0,39}$/.test(x)) &&
    new Set(p.equipment).size===p.equipment.length;
}

export function canonicalExerciseAlternatives(params) {
  if(!validExerciseAlternativesParams(params))
    throw new TypeError('Invalid bounded exercise alternatives request');
  const from=EX_BY_ID[params.sourceId];
  if(!from)throw new TypeError('Unknown canonical exercise identity');
  const equipped=expandEquipment(params.equipment),excluded=new Set(params.excludeIds);
  const choices=EXERCISES.filter(ex=>ex.id!==from.id && !excluded.has(ex.id) &&
    ex.part===from.part && ex.type===from.type &&
    (!ex.pattern || !from.pattern || ex.pattern===from.pattern) &&
    ex.rep[0]===from.rep[0] && ex.rep[1]===from.rep[1] &&
    ex.equip.every(id=>equipped.has(id)) &&
    (ex.equip.length>0 || params.allowBodyweight) &&
    !!nextExerciseIdForShellExercise(ex))
    .sort((a,b)=>a.name.localeCompare(b.name)||a.id.localeCompare(b.id))
    .slice(0,12).map(projectExercise);
  return {source:'modules/engine-api.js:EXERCISES',readOnly:true,
    sourceId:from.id,candidates:choices};
}
