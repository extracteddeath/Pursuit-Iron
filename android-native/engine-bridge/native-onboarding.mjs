/** Canonical first-program creation. Pure preparation; the native owner saves. */
import { generateNextProgramForShell, EXERCISES, EQUIPMENT, parseStoredData,
  splitSupportsTrainingDays, STORE_VERSION } from '../../modules/engine-api.js';
import { projectCanonicalSavedProgram } from './current-program-projection.mjs';
import { sha256Utf8, utf8ByteLength } from './json-identity.mjs';
import { nativeStackCandidates } from './native-loading-catalog.mjs';

const SPLITS={full_body:'Full body',upper_lower:'Upper / Lower',ppl:'Push / Pull / Legs',ulppl:'Upper / Lower / Push / Pull / Legs'};
// IDs come from the same append-only exercise/equipment catalog as the
// production engine. Grouping and labels belong to the Compose UI only.
export const NATIVE_SETUP_EQUIPMENT=Object.freeze(EQUIPMENT.map(({id})=>id));
export const NATIVE_MUSCLE_PRIORITIES=Object.freeze([
  ['chest','Chest'],['back','Back · lats and upper back'],
  ['shoulders','Shoulders'],['biceps','Biceps'],['triceps','Triceps'],
  ['quads','Quads'],['hamstrings','Hamstrings'],['glutes','Glutes'],
  ['calves','Calves'],['abs','Abs'],['traps','Traps'],
  ['forearms','Forearms'],['adductors','Adductors'],['abductors','Abductors'],
  ['lower_back','Lower back'],['neck','Neck']
].map(([id,label])=>Object.freeze({id,label})));
const MUSCLE_IDS=new Set(NATIVE_MUSCLE_PRIORITIES.map(x=>x.id));
const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length &&
  keys.every(k=>Object.prototype.hasOwnProperty.call(v,k));
const id=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(v);
const clock=v=>Number.isSafeInteger(v) && v>0 && v<=8_640_000_000_000_000;
const ensure=v=>{if(!v) throw new TypeError('Native program setup rejected');};

// Only explicitly owned physical settings may authorize native weight controls.
const physicalDecimal=n=>typeof n==='number' && Number.isFinite(n) && n>=0 && n<=99999.999 && Number(n.toFixed(2))===n;
const physicalLoads=v=>Array.isArray(v) && v.length>=1 && v.length<=250 && v.every(physicalDecimal) && new Set(v).size===v.length;
const physicalRange=v=>exact(v,['minimum','increment','maximum']) && physicalDecimal(v.minimum) &&
  physicalDecimal(v.increment) && v.increment>0 && physicalDecimal(v.maximum) && v.maximum>=v.minimum;
export function authoredInventory(v,unit,program=null) {
  ensure(object(v) && v.unit===unit && Object.keys(v).every(k=>
    ['unit','barbell','dumbbells','cable','machine','smith','exerciseOverrides'].includes(k)));
  let families=0;
  if(Object.prototype.hasOwnProperty.call(v,'barbell')) {
    const bar=v.barbell;
    ensure(exact(bar,['barWeight','platePairs']) && physicalDecimal(bar.barWeight) &&
      bar.barWeight<=500 && Array.isArray(bar.platePairs) && bar.platePairs.length>=1 && bar.platePairs.length<=24 &&
      bar.platePairs.every(p=>exact(p,['weight','pairs']) && physicalDecimal(p.weight) &&
        p.weight>0 && p.weight<=100 && Number.isInteger(p.pairs) && p.pairs>=1 && p.pairs<=50) &&
      new Set(bar.platePairs.map(p=>p.weight)).size===bar.platePairs.length);
    families++;
  }
  if(Object.prototype.hasOwnProperty.call(v,'dumbbells')) {
    ensure(exact(v.dumbbells,['availablePerHand']) && physicalLoads(v.dumbbells.availablePerHand));families++;
  }
  for(const key of ['cable','machine']) if(Object.prototype.hasOwnProperty.call(v,key)) {
    ensure(physicalRange(v[key]));families++;
  }
  if(Object.prototype.hasOwnProperty.call(v,'smith')) {
    ensure(exact(v.smith,['availableLoads']) && physicalLoads(v.smith.availableLoads));families++;
  }
  if(Object.prototype.hasOwnProperty.call(v,'exerciseOverrides')) {
    ensure(object(v.exerciseOverrides) && Object.keys(v.exerciseOverrides).length<=24);
    const eligible=new Map(nativeStackCandidates(program).map(row=>[row.id,row.mode]));
    for(const [exerciseId,setting] of Object.entries(v.exerciseOverrides)) {
      // No user-chosen carrier and no inferred plate/smith/bodyweight loading.
      const mode=eligible.get(exerciseId);
      ensure(mode && (exact(setting,['availableLoads']) && physicalLoads(setting.availableLoads) ||
        mode!=='external_load' && physicalRange(setting)));
      families++;
    }
  }
  ensure(families>0);
  return v;
}

export function nativeProgramOptions() {
  return {source:'modules/engine-api.js:splitSupportsTrainingDays',readOnly:true,
    splits:Object.entries(SPLITS).map(([id,label])=>({id,label,
      days:[3,4,5,6].filter(n=>splitSupportsTrainingDays(id,n))})),equipment:[...NATIVE_SETUP_EQUIPMENT],
    muscles:NATIVE_MUSCLE_PRIORITIES.map(({id,label})=>({id,label}))};
}

function setupFrom(raw) {
  ensure(typeof raw==='string' && raw.length>0 && utf8ByteLength(raw)<=16384);
  const s=JSON.parse(raw);
  ensure(exact(s,['creationId','programId','createdAt','seed','profile']) &&
    id(s.creationId) && id(s.programId) && s.creationId!==s.programId && clock(s.createdAt) &&
    Number.isInteger(s.seed) && s.seed>=1 && s.seed<=2147483646);
  const p=s.profile;
  const legacy=['name','goal','experience','unit','split','days','session','equipment',
    'allowBodyweight','allowSupersets','barbellCap'];
  const priorities=Object.prototype.hasOwnProperty.call(p,'focus') ||
    Object.prototype.hasOwnProperty.call(p,'reduced');
  const inventory=Object.prototype.hasOwnProperty.call(p,'loadingInventory');
  ensure((inventory?exact(p,[...legacy,'focus','reduced','loadingInventory']):
    priorities?exact(p,[...legacy,'focus','reduced']):exact(p,legacy)) &&
    typeof p.name==='string' && p.name.trim().length>0 && p.name.length<=60 &&
    !/[\x00-\x1f\x7f-\x9f]/.test(p.name) &&
    ['hypertrophy','strength','both'].includes(p.goal) &&
    ['beginner','intermediate','advanced'].includes(p.experience) && ['lb','kg'].includes(p.unit) &&
    Object.prototype.hasOwnProperty.call(SPLITS,p.split) && p.days>=3 &&
    splitSupportsTrainingDays(p.split,p.days) && ['s60','s90','s120p'].includes(p.session) &&
    typeof p.allowBodyweight==='boolean' && typeof p.allowSupersets==='boolean' &&
    (p.barbellCap===null || Number.isInteger(p.barbellCap) && p.barbellCap>=0 && p.barbellCap<=6) &&
    Array.isArray(p.equipment) && p.equipment.length>0 &&
    new Set(p.equipment).size===p.equipment.length &&
    p.equipment.every(eq=>NATIVE_SETUP_EQUIPMENT.includes(eq) || eq==='rack')); // Legacy native saves only: rack is auto-implied by a barbell.
  if(priorities) {
    ensure(object(p.focus) && Object.keys(p.focus).length<=3 &&
      Object.entries(p.focus).every(([part,level])=>
        MUSCLE_IDS.has(part) && Number.isInteger(level) && level>=1 && level<=2) &&
      Array.isArray(p.reduced) && p.reduced.length<=3 &&
      new Set(p.reduced).size===p.reduced.length &&
      p.reduced.every(part=>MUSCLE_IDS.has(part) &&
        !Object.prototype.hasOwnProperty.call(p.focus,part)));
  }
  if(inventory) authoredInventory(p.loadingInventory,p.unit);
  return s;
}

export function prepareNativeProgram(setupJson) {
  const s=setupFrom(setupJson),p=s.profile;
  const config={name:p.name.trim(),unit:p.unit,goal:p.goal,experience:p.experience,
    split:p.split,days:p.days,session:p.session,weeks:6,progression:'auto',deload:false,
    equipment:[...p.equipment],focus:{...(p.focus||{})},
    focusList:Object.entries(p.focus||{}).flatMap(([part,level])=>Array(level).fill(part)),
    reduce:[...(p.reduced||[])],barbellCap:p.barbellCap,
    noBodyweight:!p.allowBodyweight,noSupersets:!p.allowSupersets,
     ...(p.loadingInventory?{loadingInventory:authoredInventory(p.loadingInventory,p.unit)}:{})};
  const generated=generateNextProgramForShell({config,legacyExercises:EXERCISES,
    seed:s.seed,makeId:()=>s.programId});
  const program=generated.program;
  ensure(program?.nextEngine?.program?.audit?.result==='pass' &&
    program.id===s.programId && program.days.length===p.days);
  program.createdAt=s.createdAt;program.updatedAt=s.createdAt;
  const store={v:STORE_VERSION,savedAt:s.createdAt,saved:[program],history:[],
    cycles:[],custom:[],gyms:[],tombs:{},perf:{},
    nativeOnboarding:{schemaVersion:1,creationId:s.creationId,programId:s.programId,
      setupJson,createdAt:s.createdAt}};
  const snapshot=JSON.stringify(store);
  ensure(utf8ByteLength(snapshot)<=1024*1024);
  parseStoredData(snapshot);
  return {source:'modules/engine-api.js:generateNextProgramForShell',preparedOnly:true,
    approvedForWorkoutExecution:false,creationId:s.creationId,setupSha256:sha256Utf8(setupJson),
    snapshot,snapshotSha256:sha256Utf8(snapshot),
    program:projectCanonicalSavedProgram(snapshot,s.programId,sha256Utf8(snapshot))};
}

/** Restore only our own first-program identity; ordinary backups never enter this flow. */
export function inspectNativeProgram(rawSnapshot) {
  ensure(typeof rawSnapshot==='string' && utf8ByteLength(rawSnapshot)<=1024*1024);
  const store=parseStoredData(rawSnapshot),meta=store.nativeOnboarding;
  ensure(store.v===13 && exact(meta,['schemaVersion','creationId','programId','setupJson','createdAt']) &&
    meta.schemaVersion===1);
  const s=setupFrom(meta.setupJson);
  ensure(s.creationId===meta.creationId && s.programId===meta.programId && s.createdAt===meta.createdAt);
  const saved=store.saved.find(item=>item.id===s.programId);
  ensure(saved && saved.config?.unit===s.profile.unit);
  let loadingInventory=null;
  try {if(saved.config.loadingInventory)
    loadingInventory=authoredInventory(saved.config.loadingInventory,s.profile.unit,saved);
  } catch { /* Older or foreign stock is never asserted as user-confirmed. */ }
  // This is a source-SLOT protection map, not merely a set of exercise IDs:
  // identical movements can have different primary/pairing roles on each day.
  const protectedSlots=[];
  for(const day of saved.days) {
    const protectedBySource=new Map();
    if(Number.isInteger(day.primaryIndex) && day.primaryIndex>=0 &&
       day.primaryIndex<day.exercises.length)
      protectedBySource.set(day.primaryIndex,'primary');
    for(let i=0;i<day.exercises.length;i++) {
      if(saved.ss?.[`${day.id}:${i}`]) {
        for(const sourceIndex of [i,i+1]) {
          if(sourceIndex>=day.exercises.length)continue;
          if(protectedBySource.get(sourceIndex)==='primary')
            protectedBySource.set(sourceIndex,'primary_paired');
          else if(!protectedBySource.has(sourceIndex))
            protectedBySource.set(sourceIndex,'paired');
        }
      }
    }
    for(const [sourceIndex,reason] of protectedBySource)
      protectedSlots.push({dayId:day.id,sourceIndex,reason});
  }
  return {source:'modules/engine-api.js:parseStoredData',readOnly:true,creationId:s.creationId,
    setupJson:meta.setupJson,loadingInventory,loadingCandidates:nativeStackCandidates(saved),
    protectedSlots,
    snapshotSha256:sha256Utf8(rawSnapshot),
    program:projectCanonicalSavedProgram(rawSnapshot,s.programId,sha256Utf8(rawSnapshot))};
}
