/** Read-only, source-pinned equipment accuracy gate for native set entry. */
import { parseStoredData } from '../../modules/engine-api.js';
import { nativeIssuedLoadMode } from './native-loading-catalog.mjs';
import { authoredInventory } from './native-onboarding.mjs';
import { loadingInventoryInUnit, resolveLoadingMode, nextAvailableLoad,
  availableLoadAtOrBelow } from '../../modules/next-engine/loading.js';
import { issuedNativeWorkoutFrom,MAX_ISSUED_WORKOUT_BYTES } from './native-workout.mjs';
import { sha256Utf8,utf8ByteLength } from './json-identity.mjs';

const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const ensure=v=>{if(!v)throw new TypeError('Equipment loading review unavailable')};
const decimal=/^(?:0|[1-9][0-9]{0,4})(?:\.[0-9]{1,3})?$/;
const exactList=v=>Array.isArray(v) && v.length<=250 &&
  v.every(n=>typeof n==='number' && Number.isFinite(n) && n>=0 && n<=99999.999);
const naturalNumber=v=>Number.isInteger(v) && v>=0 && v<=50;
const profile=p=>object(p) && (exactList(p.availableLoads) ||
  (Number.isFinite(p.minimum) && p.minimum>=0 &&
    Number.isFinite(p.increment) && p.increment>0 &&
    Number.isFinite(p.maximum) && p.maximum>=p.minimum));
const fullBar=b=>object(b) && Number.isFinite(b.barWeight) && b.barWeight>=0 &&
  b.barWeight<=500 && Array.isArray(b.platePairs) && b.platePairs.length<=24 &&
  b.platePairs.every(p=>object(p) && Number.isFinite(p.weight) && p.weight>0 &&
    p.weight<=100 && naturalNumber(p.pairs));
function authenticatedFamily(raw,exId,mode) {
  const override=raw.exerciseOverrides?.[exId];
  if(override && (exactList(override.availableLoads) ||
      (Number.isFinite(override.minimum) && override.minimum>=0 &&
       Number.isFinite(override.increment) && override.increment>0 &&
       (override.maximum===undefined ||
        Number.isFinite(override.maximum) && override.maximum>=override.minimum))))
    return 'exercise_override';
  switch(mode) {
    case 'barbell_total': return fullBar(raw.barbell)?'barbell_plates':null;
    case 'dumbbell_per_hand': return exactList(raw.dumbbells?.availablePerHand)?'dumbbells_per_hand':null;
    case 'cable_stack': return profile(raw.cable)?'cable_stack':null;
    case 'machine_stack': return profile(raw.machine)?'machine_stack':null;
    // A Smith rail may be counterbalanced. Generic machine increments are
    // not enough to claim actual TOTAL bar weight. Require exact totals.
    case 'smith_total':return exactList(raw.smith?.availableLoads)?'smith_confirmed_totals':null;
    default:return null;
  }
}

/** No state writes and no implicit gym inventory. All results refer to issued bytes. */
export function inspectNativeEquipmentLoad(rawSnapshot,issuedWorkout,slot,load) {
  ensure(typeof rawSnapshot==='string' && utf8ByteLength(rawSnapshot)<=1024*1024 &&
    typeof issuedWorkout==='string' && utf8ByteLength(issuedWorkout)<=MAX_ISSUED_WORKOUT_BYTES &&
    object(slot) && Object.keys(slot).sort().join(',')==='exerciseId,exerciseIndex,setIndex' &&
    Number.isInteger(slot.exerciseIndex) && slot.exerciseIndex>=0 && slot.exerciseIndex<48 &&
    Number.isInteger(slot.setIndex) && slot.setIndex>=0 && slot.setIndex<20 &&
    typeof slot.exerciseId==='string' && slot.exerciseId.length>0 &&
    typeof load==='string' && decimal.test(load));
  const {plan}=issuedNativeWorkoutFrom(rawSnapshot,issuedWorkout);
  const row=plan.rows.find(r=>r.slot===slot.exerciseIndex && r.id===slot.exerciseId);
  ensure(row && row.sets.filter(s=>!s.warm)[slot.setIndex]);
  const store=parseStoredData(rawSnapshot),program=store.saved.find(p=>p.id===plan.session.reference.programId);
  ensure(program && program.config.unit===plan.session.unit);
  const raw=program.config.loadingInventory;
  let confirmed=false,family=null,mode='unconfirmed',previous=null,next=null,exact=false;
  // A canonical snapshot may contain old/injected loading fields. Only a
  // fully reauthenticated user-authored physical inventory may enable steps.
  const trusted=object(raw) && raw.unit===plan.session.unit &&
    (()=>{try {authoredInventory(raw,plan.session.unit,program);return true;}
      catch{return false;}})();
  if(trusted) {
    const inventory=loadingInventoryInUnit(raw,plan.session.unit);
    const actualMode=nativeIssuedLoadMode(slot.exerciseId,program.config.equipment);
    const userMode=inventory.exerciseOverrides?.[slot.exerciseId]?.mode;
    // An explicit exercise rack can support otherwise unknown hardware.
    // Without that override, require a single unambiguous canonical mode.
    mode=actualMode ?? 'external_load';
    const chosen=actualMode && (!userMode || userMode===actualMode) ? actualMode : null;
    const modeForSolver=chosen || userMode || mode;
    family=authenticatedFamily(raw,slot.exerciseId,chosen);
    if(!chosen && family!=='exercise_override')family=null;
    confirmed=family!==null;
    if(confirmed) {
      const evaluated={...inventory,exerciseOverrides:{
        ...inventory.exerciseOverrides,
        [slot.exerciseId]:{...inventory.exerciseOverrides?.[slot.exerciseId],mode:modeForSolver}
      }};
      const input=Number(load);
      // Previous is strictly below entered weight, even if entered weight is
      // not a physical plate/rack setting. No step ever rounds UP a logged set.
      previous=availableLoadAtOrBelow(slot.exerciseId,Math.max(0,input-0.001),evaluated,program.config.equipment);
      next=nextAvailableLoad(slot.exerciseId,input,evaluated,program.config.equipment);
      const onGrid=availableLoadAtOrBelow(slot.exerciseId,input,evaluated,program.config.equipment);
      exact=onGrid!==null && Math.abs(onGrid-input)<0.000001;
      // Any incorrect/range-exceeding worker result disables the controls.
      const feasible=value=>value===null || Number.isFinite(value) && value>=0 &&
        value<=99999.999 && Math.round(value*1000)===value*1000;
      if(!feasible(previous) || !feasible(next) ||
          previous!==null && !(previous<input) ||
          next!==null && !(next>input)) {
        confirmed=false;family=null;previous=null;next=null;exact=false;
      }
    }
  }
  return {source:'modules/next-engine/loading.js:confirmed-inventory',snapshotSha256:sha256Utf8(rawSnapshot),
    issuedWorkoutSha256:sha256Utf8(issuedWorkout),workoutId:plan.session.workoutId,
    slot,unit:plan.session.unit,inputLoad:load,mode,family,
    confirmed,exact,previous:confirmed?previous:null,next:confirmed?next:null,
    readOnly:true,applied:false};
}
