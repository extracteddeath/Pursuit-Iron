import assert from 'node:assert/strict';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import {
  generateNextProgramForShell,
  splitBuildability
} from '../modules/next-engine/app-shell-adapter-capacity.js';
import {
  generateNextCycleForShell,
  nextCycleTemplatesForShell
} from '../modules/next-engine/cycle-runtime-adapter.js';

const fullEquipment = [
  'barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'
];

// Identity-compatible shell catalog: this keeps the audit on the same Next->shell bridge used by the
// product without requiring the giant UI catalog to be imported into Node.
const legacyExercises = [...EXERCISE_MAP.values()].map(def => ({
  id: def.id,
  name: def.name,
  equip: Array.isArray(def.equipment) ? [...def.equipment] : [],
  part: Object.entries(def.muscles ?? {}).sort((a,b)=>(b[1]?.credit ?? 0)-(a[1]?.credit ?? 0))[0]?.[0] ?? 'other',
  type: def.flags?.compound ? 'compound' : 'isolation',
  pattern: def.movementFamily
}));

const base = {
  name: 'M198 creation audit',
  experience: 'intermediate',
  goal: 'both',
  split: 'full_body',
  days: 3,
  session: 's90',
  weeks: 6,
  equipment: fullEquipment,
  focus: {},
  focusList: [],
  reduce: [],
  progression: 'auto',
  progressionStyle: 'auto',
  deload: true,
  barbellCap: 3,
  percentScheme: null,
  volumeApproach: 'standard',
  noSupersets: false,
  noBodyweight: false,
  unit: 'lb'
};

const templateGoal = new Map(nextCycleTemplatesForShell().map(t => [t.id, t.goal]));
const programCases = [
  ['FB3 s60', { split:'full_body', days:3, session:'s60', goal:'both' }],
  ['FB3 s90', { split:'full_body', days:3, session:'s90', goal:'both' }],
  ['FB3 s120', { split:'full_body', days:3, session:'s120', goal:'both' }],
  ['FB5 s90', { split:'full_body', days:5, session:'s90', goal:'both' }],
  ['UL4 s90', { split:'upper_lower', days:4, session:'s90', goal:'both' }],
  ['ULPPL5 s90', { split:'ulppl', days:5, session:'s90', goal:'both' }],
  ['PPL5 s90', { split:'ppl', days:5, session:'s90', goal:'both' }],
  ['PHUL4 s90', { split:'phul', days:4, session:'s90', goal:'both' }],
  ['ULPPL5 s90 hypertrophy', { split:'ulppl', days:5, session:'s90', goal:'hypertrophy' }],
  ['ULPPL5 s90 strength', { split:'ulppl', days:5, session:'s90', goal:'strength' }],
  ['FB3 s90 beginner', { split:'full_body', days:3, session:'s90', goal:'both', experience:'beginner' }],
  ['FB3 s90 advanced', { split:'full_body', days:3, session:'s90', goal:'both', experience:'advanced' }]
];

const cycleCases = [
  ['Powerbuilding FB3 s60', 'powerbuilding', { split:'full_body', days:3, session:'s60' }],
  ['Powerbuilding FB3 s90', 'powerbuilding', { split:'full_body', days:3, session:'s90' }],
  ['Powerbuilding FB5 s90', 'powerbuilding', { split:'full_body', days:5, session:'s90' }],
  ['Powerbuilding ULPPL5 s90', 'powerbuilding', { split:'ulppl', days:5, session:'s90' }],
  ['Powerbuilding UL4 s90', 'powerbuilding', { split:'upper_lower', days:4, session:'s90' }],
  ['Powerbuilding PPL5 s90', 'powerbuilding', { split:'ppl', days:5, session:'s90' }],
  ['Strength Peak FB3 s90', 'strength_peak', { split:'full_body', days:3, session:'s90' }],
  ['Strength Peak UL4 s90', 'strength_peak', { split:'upper_lower', days:4, session:'s90' }],
  ['Hypertrophy ULPPL5 s90', 'hypertrophy_spec', { split:'ulppl', days:5, session:'s90' }],
  ['Foundation FB3 s90 beginner', 'foundation', { split:'full_body', days:3, session:'s90', experience:'beginner' }]
];

const rows = [];
const failures = [];
const summarizeError = err => ({
  code: err?.code ?? err?.name ?? 'Error',
  message: String(err?.message ?? err),
  recovery: err?.recovery?.findings?.slice?.(0, 4)?.map?.(f => `${f.severity}:${f.code}`) ?? undefined
});

for (let i = 0; i < programCases.length; i++) {
  const [label, patch] = programCases[i];
  const cfg = { ...base, ...patch, name: `Audit ${label}` };
  const verdict = splitBuildability(cfg, legacyExercises);
  let built = null;
  let error = null;
  try {
    built = generateNextProgramForShell({ config: cfg, banned: [], legacyExercises, seed: 19800 + i });
  } catch (err) { error = summarizeError(err); }
  const pass = !!built && built.nextProgram?.audit?.result === 'pass';
  rows.push({ kind:'program', label, wizard: verdict.ok, built: pass, error });
  if (verdict.ok && !pass)
    failures.push(`PROGRAM CONTRACT MISMATCH: ${label} is enabled by wizard but final build rejects (${error?.code}: ${error?.message})`);
}

for (let i = 0; i < cycleCases.length; i++) {
  const [label, templateId, patch] = cycleCases[i];
  const goal = templateGoal.get(templateId) ?? 'both';
  const cfg = { ...base, ...patch, goal, name: `Audit ${label}` };
  const verdict = splitBuildability(cfg, legacyExercises);
  for (const adaptBetweenBlocks of [false, true]) {
    let built = null;
    let error = null;
    try {
      built = generateNextCycleForShell({
        templateId,
        config: cfg,
        banned: [],
        legacyExercises,
        seed: 19900 + i * 2 + (adaptBetweenBlocks ? 1 : 0),
        adaptBetweenBlocks,
        makeId: (() => { let n = 0; return () => `audit-${i}-${adaptBetweenBlocks ? 'a':'l'}-${++n}`; })()
      });
    } catch (err) { error = summarizeError(err); }
    const blocks = built?.blocks ?? [];
    const pass = blocks.length > 0 && blocks.every(block => block?.nextEngine?.program?.audit?.result === 'pass');
    rows.push({ kind:'cycle', label, adapt:adaptBetweenBlocks, wizard:verdict.ok, built:pass, blocks:blocks.length, error });
    if (verdict.ok && !pass)
      failures.push(`CYCLE CONTRACT MISMATCH: ${label} (${adaptBetweenBlocks ? 'adaptive':'locked'}) is enabled by wizard but final cycle rejects (${error?.code}: ${error?.message})`);
  }
}

console.table(rows.map(r => ({
  kind:r.kind,
  case:r.label + (r.kind === 'cycle' ? ` ${r.adapt ? 'adaptive':'locked'}` : ''),
  wizard:r.wizard ? 'enabled':'blocked',
  final:r.built ? 'PASS':'FAIL',
  code:r.error?.code ?? ''
})));

const enabled = rows.filter(r => r.wizard).length;
const mismatches = rows.filter(r => r.wizard && !r.built).length;
console.log(`M198 creation audit: ${rows.length} routes; ${enabled} wizard-enabled; ${mismatches} enabled→build mismatches.`);
if (failures.length) {
  console.error('\n' + failures.join('\n'));
  throw new Error(`M198 creation audit found ${failures.length} wizard/final-generation contract mismatches.`);
}

assert.ok(rows.some(r => r.kind === 'program' && r.label === 'ULPPL5 s90' && r.built), 'ULPPL 60–90 must build');
assert.ok(rows.some(r => r.kind === 'cycle' && r.label === 'Powerbuilding FB3 s90' && r.built), 'Full Body 60–90 powerbuilding must build');
console.log('M198 creation audit: pass');
