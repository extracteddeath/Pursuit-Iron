import { generateProgram } from '../modules/next-engine/generate.js';
import { evaluateProgramCoachQuality } from '../modules/next-engine/coach-quality-oracle.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';

const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
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
function request({ seed, experience, goal, days, minutes, split, musclePriorities = {}, liftPriorities = {} }) {
  return {
    athlete: { experience },
    goal: { type: goal, musclePriorities, liftPriorities },
    schedule: { days: weekdays.slice(0, days).map(day => ({ day, maxMinutes: minutes })) },
    equipment: { available: equipment, bodyweight: 'allow', loading },
    restrictions: { maxBarbellMovementsPerDay: 3, allowSupersets: true },
    preferences: { preferredSplit: split, lockedSplit: split, avoidedExercises: [] },
    customExercises: [],
    seed
  };
}

const cases = [
  ['3d intermediate hypertrophy full body', request({ seed:18001, experience:'intermediate', goal:'hypertrophy', days:3, minutes:60, split:'full_body' })],
  ['4d intermediate mixed upper/lower', request({ seed:18002, experience:'intermediate', goal:'mixed', days:4, minutes:75, split:'upper_lower', liftPriorities:{ back_squat:'high', bench_press:'high', deadlift:'high' } })],
  ['5d advanced powerbuilding ULPPL', request({ seed:18003, experience:'advanced', goal:'mixed', days:5, minutes:90, split:'ulppl', liftPriorities:{ back_squat:'high', bench_press:'high', deadlift:'high' } })],
  ['5d intermediate hypertrophy ULPPL priority arms', request({ seed:18004, experience:'intermediate', goal:'hypertrophy', days:5, minutes:75, split:'ulppl', musclePriorities:{ biceps:'high', triceps:'high', side_delts:'high' } })],
  ['6d advanced hypertrophy PPL', request({ seed:18005, experience:'advanced', goal:'hypertrophy', days:6, minutes:90, split:'ppl' })]
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

console.log('\nM180 baseline quality matrix:');
console.log(JSON.stringify(summary, null, 2));

const generationErrors = summary.filter(item => item.result === 'generation_error');
if (generationErrors.length) {
  console.error(`${generationErrors.length} matrix cases could not be generated.`);
  process.exit(1);
}
