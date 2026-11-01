/**
 * Read-only synthetic program outline produced by the REAL canonical generator.
 * No user config/history enters this diagnostic. It is a BASE block/session
 * outline, not a week-specific prescription or authorization to log a workout.
 */
import { generateNextProgramForShell, EXERCISES } from '../../modules/engine-api.js';

const VALID_DAYS = Object.freeze({ full_body:3, upper_lower:4, ulppl:5 });
const equipment = Object.freeze([
  'barbell','rack','bench','dumbbell','cable','machine','smith','pullup',
  'dip','legpress','hacksquat','legext','legcurl','calfmachine'
]);
const permitted = value => value && typeof value === 'object' && !Array.isArray(value) &&
  (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null);
const boundedText = (value, max) =>
  typeof value === 'string' && value.length > 0 && value.length <= max;
const finiteMinutes = n => typeof n === 'number' && Number.isFinite(n) && n >= 0 && n <= 600;
const validPair = (value, lower, upper) => Array.isArray(value) && value.length === 2 &&
  value.every(n => Number.isInteger(n) && n >= lower && n <= upper) && value[0] <= value[1];

export function validWorkoutOutlineParams(p) {
  return permitted(p) && Object.keys(p).length === 2 &&
    Object.prototype.hasOwnProperty.call(p,'split') &&
    Object.prototype.hasOwnProperty.call(p,'dayIndex') &&
    typeof p.split === 'string' &&
    Object.prototype.hasOwnProperty.call(VALID_DAYS,p.split) &&
    Number.isInteger(p.dayIndex) && p.dayIndex >= 0 &&
    p.dayIndex < VALID_DAYS[p.split];
}

function projectSlot(slot) {
  if (!permitted(slot) || !boundedText(slot.exerciseId,80) ||
      !boundedText(slot.name,120) || !boundedText(slot.role,60) ||
      !Number.isInteger(slot.sets) || slot.sets < 1 || slot.sets > 20 ||
      !permitted(slot.prescription) ||
      !validPair(slot.prescription.reps,1,100) ||
      !validPair(slot.prescription.rir,0,10) ||
      !finiteMinutes(slot.prescription.restSeconds) ||
      !boundedText(slot.progressionStyle,50))
    throw new Error('Unprojectable canonical session exercise');
  return {
    exerciseId:slot.exerciseId,
    name:slot.name,
    role:slot.role,
    sets:slot.sets,
    repMin:slot.prescription.reps[0],
    repMax:slot.prescription.reps[1],
    rirMin:slot.prescription.rir[0],
    rirMax:slot.prescription.rir[1],
    restSeconds:slot.prescription.restSeconds,
    progressionStyle:slot.progressionStyle
  };
}

export function canonicalWorkoutOutline(params) {
  if (!validWorkoutOutlineParams(params))
    throw new TypeError('Unsupported synthetic workout outline request');
  // Same proven deterministic configuration already used by independent
  // engine-lab/verify-parity.mjs. No storage, custom authoring or writes.
  const config={
    name:'Android synthetic verification',unit:'lb',goal:'both',
    experience:'intermediate',split:params.split,days:VALID_DAYS[params.split],
    session:'s60',weeks:6,progression:'auto',deload:false,
    equipment:[...equipment],focus:{},reduce:[],barbellCap:3,
    noBodyweight:false,noSupersets:false
  };
  const result=generateNextProgramForShell({
    config,legacyExercises:EXERCISES,seed:223,makeId:()=> 'android-synthetic'
  });
  if (!result?.program || result.program.nextEngine?.program?.audit?.result !== 'pass')
    throw new Error('Canonical program was not audited successfully');
  const sessions=result.program.nextEngine.program.sessions;
  if (!Array.isArray(sessions) || sessions.length !== VALID_DAYS[params.split])
    throw new Error('Unexpected canonical session count');
  const session=sessions[params.dayIndex];
  if (!permitted(session) || !boundedText(session.id,128) ||
      !boundedText(session.day,40) || !finiteMinutes(session.maxMinutes) ||
      !finiteMinutes(session.estimatedMinutes) ||
      !Array.isArray(session.exercises) || session.exercises.length < 1 ||
      session.exercises.length > 24)
    throw new Error('Invalid canonical session outline');
  const exercises=session.exercises.map(projectSlot);
  return {
    source:'modules/engine-api.js:generateNextProgramForShell',
    readOnly:true,
    syntheticOnly:true,
    split:params.split,
    dayIndex:params.dayIndex,
    sessionId:session.id,
    day:session.day,
    estimatedMinutes:session.estimatedMinutes,
    maxMinutes:session.maxMinutes,
    exercises
  };
}
