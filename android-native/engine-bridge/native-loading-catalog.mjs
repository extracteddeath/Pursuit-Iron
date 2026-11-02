/** Canonical native-issued IDs only; never infer carrier modes from newer IDs. */
import { EX_BY_ID } from '../../modules/engine-api.js';

const unsafe=new Set(['ezbar','trapbar','safetybar','landmine','kettlebell','bands',
  'legpress','hacksquat','legext','legcurl','pecdeck','machinerow','assisted',
  'calfmachine','abduction','machinepress','machineshoulder','machinelatraise',
  'machinecurl','machineext']);
const modes={barbell:'barbell_total',dumbbell:'dumbbell_per_hand',smith:'smith_total',
  cable:'cable_stack',machine:'machine_stack'};
export function nativeIssuedLoadMode(exerciseId,equipment) {
  const exercise=EX_BY_ID[exerciseId];
  if(!exercise || !Array.isArray(exercise.equip) || !Array.isArray(equipment) ||
    exercise.equip.some(key=>unsafe.has(key) || !equipment.includes(key))) return null;
  const carriers=exercise.equip.filter(key=>Object.hasOwn(modes,key));
  return carriers.length===1?modes[carriers[0]]:null;
}
/** Only movements in the EXACT saved program with a verified unambiguous carrier. */
export function nativeStackCandidates(program) {
  if(!program || !Array.isArray(program.days) || !Array.isArray(program.config?.equipment))
    return [];
  const ids=[...new Set(program.days.flatMap(day=>Array.isArray(day.exercises)?day.exercises:[]))];
  return ids.flatMap(id=>{
    const mode=nativeIssuedLoadMode(id,program.config.equipment);
    const exercise=EX_BY_ID[id];
    return (mode==='cable_stack' || mode==='machine_stack') &&
      typeof exercise?.name==='string' && exercise.name.length>0 && exercise.name.length<=120
      ? [{id,name:exercise.name,mode}]:[];
  });
}
