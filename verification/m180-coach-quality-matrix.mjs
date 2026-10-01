import { generateProgram } from '../modules/next-engine/generate.js';
import { evaluateProgramCoachQuality } from './coach-quality-oracle.mjs';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';

const gymEquipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading = {
  unit: 'lb',
  barbell: { barWeight: 45, platePairs: [{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}] },
  dumbbells: { availablePerHand: [5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100] },
  machine: { minimum: 5, increment: 5, maximum: 500 },
  cable: { minimum: 5, increment: 5, maximum: 300 },
  smith: { minimum: 5, increment: 5, maximum: 500 },
  exerciseOverrides: {}
};

const weekdays = ['monday','tuesday','wednesday','thursday','friday','saturday'];
function request({
  seed, experience, goal, days, minutes, minMinutes, split,
  musclePriorities = {}, liftPriorities = {},
  availableEquipment = gymEquipment, bodyweight = 'allow', allowSupersets = true
}) {
  return {
    athlete: { experience },
    goal: { type: goal, musclePriorities, liftPriorities },
    schedule: { days: weekdays.slice(0, days).map(day => ({ day, maxMinutes: minutes, ...(minMinutes ? { minMinutes } : {}) })) },
    equipment: { available: availableEquipment, bodyweight, loading },
    restrictions: { maxBarbellMovementsPerDay: 3, allowSupersets },
    preferences: { preferredSplit: split, lockedSplit: split, avoidedExercises: [] },
    customExercises: [],
    seed
  };
}

const cases = [
  ['2d novice hypertrophy full body', request({ seed:18001, experience:'novice', goal:'hypertrophy', days:2, minutes:45, split:'full_body' })],
  ['3d novice mixed full body', request({ seed:18002, experience:'novice', goal:'mixed', days:3, minutes:60, split:'full_body' })],
  ['3d intermediate hypertrophy full body', request({ seed:18003, experience:'intermediate', goal:'hypertrophy', days:3, minutes:60, split:'full_body' })],
  ['3d intermediate strength full body', request({ seed:18004, experience:'intermediate', goal:'strength', days:3, minutes:75, split:'strength_fb' })],
  ['4d intermediate mixed upper/lower high SBD', request({ seed:18005, experience:'intermediate', goal:'mixed', days:4, minutes:75, split:'upper_lower', liftPriorities:{ back_squat:'high', bench_press:'high', deadlift:'high' } })],
  ['4d intermediate strength upper/lower', request({ seed:18006, experience:'intermediate', goal:'strength', days:4, minutes:60, split:'upper_lower' })],
  ['4d advanced mixed PHUL', request({ seed:18007, experience:'advanced', goal:'mixed', days:4, minutes:90, split:'phul' })],
  ['4d intermediate hypertrophy upper/lower no supersets', request({ seed:18008, experience:'intermediate', goal:'hypertrophy', days:4, minutes:60, split:'upper_lower', allowSupersets:false })],
  ['5d advanced powerbuilding ULPPL high SBD', request({ seed:18009, experience:'advanced', goal:'mixed', days:5, minutes:90, split:'ulppl', liftPriorities:{ back_squat:'high', bench_press:'high', deadlift:'high' } })],
  ['5d intermediate hypertrophy ULPPL priority arms', request({ seed:18010, experience:'intermediate', goal:'hypertrophy', days:5, minutes:75, split:'ulppl', musclePriorities:{ biceps:'high', triceps:'high', side_delts:'high' } })],
  ['5d advanced mixed PPLUL', request({ seed:18011, experience:'advanced', goal:'mixed', days:5, minutes:90, split:'pplul' })],
  ['5d intermediate hypertrophy ULPPL short sessions', request({ seed:18012, experience:'intermediate', goal:'hypertrophy', days:5, minutes:45, split:'ulppl' })],
  ['6d advanced hypertrophy PPL', request({ seed:18013, experience:'advanced', goal:'hypertrophy', days:6, minutes:90, split:'ppl' })],
  ['6d advanced strength upper/lower', request({ seed:18014, experience:'advanced', goal:'strength', days:6, minutes:90, split:'upper_lower' })],
  ['3d intermediate hypertrophy machines/dumbbells no bodyweight', request({ seed:18015, experience:'intermediate', goal:'hypertrophy', days:3, minutes:60, split:'full_body', availableEquipment:['bench','dumbbell','cable','machine','leg_press'], bodyweight:'exclude' })],
  ['3d novice hypertrophy home dumbbells', request({ seed:18016, experience:'novice', goal:'hypertrophy', days:3, minutes:45, split:'full_body', availableEquipment:['bench','dumbbell','bodyweight'], bodyweight:'allow' })],
  ['5d advanced hypertrophy bro split', request({ seed:18017, experience:'advanced', goal:'hypertrophy', days:5, minutes:75, split:'bro' })],
  ['4d advanced hypertrophy torso/limbs', request({ seed:18018, experience:'advanced', goal:'hypertrophy', days:4, minutes:75, split:'torso_limbs' })],
  ['5d advanced hypertrophy ULPPL long sessions', request({ seed:18019, experience:'advanced', goal:'hypertrophy', days:5, minutes:120, minMinutes:90, split:'ulppl' })]
];

function printQualityDetails(program, quality) {
  const affectedIds = new Set(
    [...quality.hardFailures, ...quality.warnings]
      .map(finding => finding.original?.sessionId)
      .filter(Boolean)
  );
  for (const session of program.sessions) {
    const lowerBackLoad = session.exercises.reduce((sum, exercise) => sum + (EXERCISE_MAP.get(exercise.exerciseId)?.fatigue?.lowerBack ?? 0), 0);
    const shouldPrint = affectedIds.size === 0 || affectedIds.has(session.id) || lowerBackLoad >= 8;
    if (!shouldPrint) continue;
    console.log(`  Session ${session.day} · ${session.name} · ${session.estimatedMinutes}/${session.maxMinutes}m · lowerBack=${lowerBackLoad.toFixed(1)}`);
    for (const exercise of session.exercises) {
      const def = EXERCISE_MAP.get(exercise.exerciseId);
      console.log(`    - ${exercise.name} | ${exercise.role} | ${exercise.sets} sets | family=${def?.movementFamily ?? '?'} | lowerBack=${(def?.fatigue?.lowerBack ?? 0).toFixed(1)}`);
    }
  }
  for (const finding of [...quality.hardFailures, ...quality.warnings])
    console.log(`    finding ${finding.code}: ${finding.detail || finding.original?.message || ''}`);
}

const summary = [];
for (const [name, req] of cases) {
  try {
    const generated = generateProgram(req);
    const quality = evaluateProgramCoachQuality(generated.program, req, { audit: generated.program.audit });
    const codes = [...quality.hardFailures, ...quality.warnings].map(item => item.code);
    summary.push({ name, split: generated.program.split.displayName, result: quality.result, hard: quality.hardFailures.length, warnings: quality.warnings.length, codes });
    console.log(`${quality.result.toUpperCase()} ${name} :: ${generated.program.split.displayName}`);
    if (codes.length) {
      console.log(`  ${codes.join(', ')}`);
      printQualityDetails(generated.program, quality);
    }
  } catch (error) {
    summary.push({ name, result:'generation_error', error:String(error?.message ?? error) });
    console.log(`GENERATION_ERROR ${name}`);
    console.log(`  ${error?.stack ?? error}`);
  }
}

console.log('\nM180 expanded quality matrix:');
console.log(JSON.stringify(summary, null, 2));

const generationErrors = summary.filter(item => item.result === 'generation_error');
const qualityMisses = summary.filter(item => item.result !== 'pass' && item.result !== 'generation_error');
if (generationErrors.length || qualityMisses.length) {
  if (generationErrors.length) console.error(`${generationErrors.length} matrix cases could not be generated.`);
  if (qualityMisses.length) console.error(`${qualityMisses.length} generated programs failed the pass-only M180 coach-quality gate.`);
  process.exit(1);
}
console.log(`M180 pass-only matrix gate OK: ${summary.length}/${summary.length} representative programs clean.`);
