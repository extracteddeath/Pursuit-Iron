/** Canonical native-issued IDs only; never infer carrier modes from newer IDs. */
import { EX_BY_ID, expandEquipment } from '../../modules/engine-api.js';

const unsafe=new Set(['ezbar','trapbar','safetybar','landmine','kettlebell','bands',
  'legpress','hacksquat','legext','legcurl','pecdeck','machinerow','assisted',
  'calfmachine','abduction','machinepress','machineshoulder','machinelatraise',
  'machinecurl','machineext']);
// Machine families with an ambiguous resistance mechanism must NEVER use
// the generic selectorized/plate solver. Only user-entered exact LOGGED loads
// may authorize stepping, not plate-per-side, sled weight or counterbalance.
const specialty=new Set(['legpress','hacksquat','legext','legcurl','calfmachine',
  'pecdeck','machinerow','machinepress','machineshoulder','machinelatraise',
  'machinecurl','machineext','abduction','adduction','beltsquat',
  'hipthrustmachine','kickback','machinecrunch','machineshrug','machinepullover']);
const modes={barbell:'barbell_total',dumbbell:'dumbbell_per_hand',smith:'smith_total',
  cable:'cable_stack',machine:'machine_stack'};
export function nativeIssuedLoadMode(exerciseId,equipment) {
  const exercise=EX_BY_ID[exerciseId];
  if(!exercise || !Array.isArray(exercise.equip) || !Array.isArray(equipment)) return null;
  // Use the canonical hardware implication model for *eligibility* only:
  // barbell implies a rack unless explicitly disabled, and an incline bench
  // also functions as a flat bench. This never invents physical load steps.
  const available=expandEquipment(equipment);
  if(exercise.equip.some(key=>unsafe.has(key) || !available.has(key))) return null;
  const carriers=exercise.equip.filter(key=>Object.hasOwn(modes,key));
  return carriers.length===1?modes[carriers[0]]:null;
}
/** Only movements in the EXACT saved program with a verified unambiguous carrier. */
export function nativeStackCandidates(program) {
  if(!program || !Array.isArray(program.days) || !Array.isArray(program.config?.equipment))
    return [];
  const ids=[...new Set(program.days.flatMap(day=>Array.isArray(day.exercises)?day.exercises:[]))];
  const available=expandEquipment(program.config.equipment);
  return ids.flatMap(id=>{
    const exercise=EX_BY_ID[id];
    if(!exercise || !Array.isArray(exercise.equip) ||
        !exercise.equip.every(key=>available.has(key)) ||
        typeof exercise.name!=='string' || exercise.name.length<1 || exercise.name.length>120)
      return [];
    const nativeMode=nativeIssuedLoadMode(id,program.config.equipment);
    if(nativeMode==='cable_stack' || nativeMode==='machine_stack')
      return [{id,name:exercise.name,mode:nativeMode}];
    const types=exercise.equip.filter(key=>specialty.has(key));
    const unambiguous=types.length===1 && !exercise.equip.some(key=>Object.hasOwn(modes,key)) &&
      exercise.equip.every(key=>specialty.has(key) || ['bench','rack'].includes(key));
    return unambiguous?[{id,name:exercise.name,mode:'external_load'}]:[];
  });
}
