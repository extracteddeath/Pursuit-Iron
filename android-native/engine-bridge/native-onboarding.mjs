/** Canonical first-program creation. Pure preparation; the native owner saves. */
import { generateNextProgramForShell, EXERCISES, parseStoredData,
  splitSupportsTrainingDays, STORE_VERSION } from '../../modules/engine-api.js';
import { projectCanonicalSavedProgram } from './current-program-projection.mjs';
import { sha256Utf8, utf8ByteLength } from './json-identity.mjs';

const SPLITS={full_body:'Full body',upper_lower:'Upper / Lower',ppl:'Push / Pull / Legs',ulppl:'Upper / Lower / Push / Pull / Legs'};
export const NATIVE_SETUP_EQUIPMENT=Object.freeze([
  'barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip',
  'legpress','hacksquat','legext','legcurl','calfmachine','ezbar','kettlebell','bands'
]);
const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length &&
  keys.every(k=>Object.prototype.hasOwnProperty.call(v,k));
const id=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(v);
const clock=v=>Number.isSafeInteger(v) && v>0 && v<=8_640_000_000_000_000;
const ensure=v=>{if(!v) throw new TypeError('Native program setup rejected');};

export function nativeProgramOptions() {
  return {source:'modules/engine-api.js:splitSupportsTrainingDays',readOnly:true,
    splits:Object.entries(SPLITS).map(([id,label])=>({id,label,
      days:[3,4,5,6].filter(n=>splitSupportsTrainingDays(id,n))})),equipment:[...NATIVE_SETUP_EQUIPMENT]};
}

function setupFrom(raw) {
  ensure(typeof raw==='string' && raw.length>0 && utf8ByteLength(raw)<=16384);
  const s=JSON.parse(raw);
  ensure(exact(s,['creationId','programId','createdAt','seed','profile']) &&
    id(s.creationId) && id(s.programId) && s.creationId!==s.programId && clock(s.createdAt) &&
    Number.isInteger(s.seed) && s.seed>=1 && s.seed<=2147483646);
  const p=s.profile;
  ensure(exact(p,['name','goal','experience','unit','split','days','session','equipment',
    'allowBodyweight','allowSupersets','barbellCap']) &&
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
    p.equipment.every(eq=>NATIVE_SETUP_EQUIPMENT.includes(eq)));
  return s;
}

export function prepareNativeProgram(setupJson) {
  const s=setupFrom(setupJson),p=s.profile;
  const config={name:p.name.trim(),unit:p.unit,goal:p.goal,experience:p.experience,
    split:p.split,days:p.days,session:p.session,weeks:6,progression:'auto',deload:false,
    equipment:[...p.equipment],focus:{},reduce:[],barbellCap:p.barbellCap,
    noBodyweight:!p.allowBodyweight,noSupersets:!p.allowSupersets};
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
  return {source:'modules/engine-api.js:parseStoredData',readOnly:true,creationId:s.creationId,
    setupJson:meta.setupJson,snapshotSha256:sha256Utf8(rawSnapshot),
    program:projectCanonicalSavedProgram(rawSnapshot,s.programId,sha256Utf8(rawSnapshot))};
}
