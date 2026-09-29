import assert from 'node:assert/strict';
import { generateProgram } from '../modules/next-engine/generate.js';
import { createMusclePrescriptions, normalizeRequest } from '../modules/next-engine/prescription.js';
import { initialPhaseForGoal } from '../modules/next-engine/phase-policy.js';

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
const days = ['monday','tuesday','thursday','friday','sunday'];
const bands = [
  { id:'40-60', min:40, max:60, target:5 },
  { id:'60-90', min:60, max:90, target:7 },
  { id:'90-120', min:90, max:120, target:9 },
  { id:'120+', min:120, max:150, target:11 }
];

function request({ experience, goal, band, seed }) {
  return {
    athlete: { experience },
    goal: { type: goal, musclePriorities: {}, liftPriorities: {} },
    schedule: { days: days.map(day => ({ day, minMinutes:band.min, maxMinutes:band.max, targetExercises:band.target })) },
    equipment: { available:equipment, bodyweight:'allow', loading },
    restrictions: { maxBarbellMovementsPerDay:3, allowSupersets:true },
    preferences: { preferredSplit:'ulppl', lockedSplit:'ulppl', avoidedExercises:[] },
    customExercises: [],
    seed
  };
}

function summarize(req) {
  const normalized = normalizeRequest(req);
  const phase = initialPhaseForGoal(normalized.goal.type);
  const prescriptions = createMusclePrescriptions(normalized, phase);
  const { program, diagnostics } = generateProgram(req);
  assert.equal(program.audit.result, 'pass');
  const totalSets = program.sessions.reduce((sum, session) => sum + session.exercises.reduce((s, ex) => s + ex.sets, 0), 0);
  const totalExercises = program.sessions.reduce((sum, session) => sum + session.exercises.length, 0);
  const minutes = program.sessions.reduce((sum, session) => sum + session.estimatedMinutes, 0);
  const maxMinutes = program.sessions.reduce((sum, session) => sum + session.maxMinutes, 0);
  const minMinutes = normalized.schedule.days.reduce((sum, day) => sum + (day.minMinutes ?? 0), 0);
  const core = prescriptions.filter(p => p.priority !== 'maintenance' && p.minimum > 0);
  const floorMisses = core.filter(p => (program.muscleLedger[p.muscle]?.fractionalSets ?? 0) + .001 < p.minimum);
  const preferredAttainment = core.reduce((sum, p) => sum + Math.min(1, (program.muscleLedger[p.muscle]?.fractionalSets ?? 0) / Math.max(1, p.preferred)), 0) / Math.max(1, core.length);
  const upperAttainment = core.reduce((sum, p) => sum + Math.min(1, (program.muscleLedger[p.muscle]?.fractionalSets ?? 0) / Math.max(1, p.upper)), 0) / Math.max(1, core.length);
  const directAttainmentRows = core.filter(p => p.directPreferred > 0);
  const directPreferredAttainment = directAttainmentRows.reduce((sum, p) => sum + Math.min(1, (program.muscleLedger[p.muscle]?.directSets ?? 0) / Math.max(1, p.directPreferred)), 0) / Math.max(1, directAttainmentRows.length);
  const totalFractionalDose = core.reduce((sum, p) => sum + (program.muscleLedger[p.muscle]?.fractionalSets ?? 0), 0);
  const preferredDose = core.reduce((sum, p) => sum + p.preferred, 0);
  const upperDose = core.reduce((sum, p) => sum + p.upper, 0);
  const sessionsBelowBand = program.sessions.filter(session => {
    const day = normalized.schedule.days.find(row => row.day === session.day);
    return (day?.minMinutes ?? 0) > 0 && session.estimatedMinutes + .001 < day.minMinutes;
  });
  return {
    split: program.split.displayName,
    totalSets,
    avgSets: Math.round(totalSets / program.sessions.length * 10) / 10,
    totalExercises,
    avgExercises: Math.round(totalExercises / program.sessions.length * 10) / 10,
    totalFractionalDose: Math.round(totalFractionalDose * 10) / 10,
    preferredDose: Math.round(preferredDose * 10) / 10,
    upperDose: Math.round(upperDose * 10) / 10,
    minutes: Math.round(minutes),
    avgMinutes: Math.round(minutes / program.sessions.length * 10) / 10,
    maxUtilization: Math.round((minutes / maxMinutes) * 1000) / 10,
    minimumBandFulfillment: minMinutes > 0 ? Math.round((minutes / minMinutes) * 1000) / 10 : null,
    sessionsBelowBand: sessionsBelowBand.map(session => ({ day:session.day, estimatedMinutes:session.estimatedMinutes })),
    preferredAttainment: Math.round(preferredAttainment * 1000) / 10,
    upperAttainment: Math.round(upperAttainment * 1000) / 10,
    directPreferredAttainment: Math.round(directPreferredAttainment * 1000) / 10,
    floorMisses: floorMisses.map(p => p.muscle),
    allocatorBudget: diagnostics.allocationMetrics.weeklyMinuteBudget,
    allocatorEstimatedUsed: diagnostics.allocationMetrics.estimatedMinutesUsed,
    marginalIterations: diagnostics.allocationMetrics.marginalIterations
  };
}

const rows = [];
let profileSeed = 18100;
for (const experience of ['intermediate','advanced']) {
  for (const goal of ['hypertrophy','mixed']) {
    const seed = ++profileSeed; // identical athlete/program seed across all duration bands for a fair capacity comparison
    for (const band of bands) {
      const result = summarize(request({ experience, goal, band, seed }));
      rows.push({ experience, goal, band:band.id, ...result });
      console.log(`${experience} ${goal} ${band.id}: ${result.totalSets} sets (${result.avgSets}/session), ${result.totalFractionalDose}/${result.upperDose} dose, ${result.avgMinutes}m avg, preferred=${result.preferredAttainment}%, upper=${result.upperAttainment}%, belowBand=${result.sessionsBelowBand.length}/5, allocator=${result.allocatorEstimatedUsed}/${result.allocatorBudget}`);
      assert.deepEqual(result.floorMisses, [], `${experience} ${goal} ${band.id} missed modeled floors: ${result.floorMisses.join(', ')}`);
    }
  }
}

for (const experience of ['intermediate','advanced']) {
  for (const goal of ['hypertrophy','mixed']) {
    const series = rows.filter(r => r.experience === experience && r.goal === goal);
    for (let i=1;i<series.length;i++) {
      assert.ok(series[i].totalFractionalDose + .001 >= series[i-1].totalFractionalDose,
        `${experience} ${goal}: longer session band reduced realized productive dose (${series[i-1].band} ${series[i-1].totalFractionalDose} -> ${series[i].band} ${series[i].totalFractionalDose})`);
    }
  }
}

console.log('\nM181 dose-band baseline');
console.log(JSON.stringify(rows, null, 2));
