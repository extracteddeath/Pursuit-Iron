import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';

const fullEquipment = [
  'barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'
];

const legacyExercises = [...EXERCISE_MAP.values()].map(def => ({
  id: def.id,
  name: def.name,
  equip: Array.isArray(def.equipment) ? [...def.equipment] : [],
  part: Object.entries(def.muscles ?? {}).sort((a,b)=>(b[1]?.credit ?? 0)-(a[1]?.credit ?? 0))[0]?.[0] ?? 'other',
  type: def.flags?.compound ? 'compound' : 'isolation',
  pattern: def.movementFamily
}));

const config = {
  name: 'M198 FB5 debug',
  experience: 'intermediate',
  goal: 'both',
  split: 'full_body',
  days: 5,
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

for (const adaptBetweenBlocks of [false, true]) {
  try {
    const built = generateNextCycleForShell({
      templateId: 'powerbuilding',
      config,
      banned: [],
      legacyExercises,
      seed: adaptBetweenBlocks ? 19905 : 19904,
      adaptBetweenBlocks,
      makeId: (() => { let n = 0; return () => `debug-${adaptBetweenBlocks ? 'a':'l'}-${++n}`; })()
    });
    console.log('PASS', adaptBetweenBlocks ? 'adaptive' : 'locked', built.blocks?.length ?? 0);
  } catch (err) {
    console.error('\nFAIL', adaptBetweenBlocks ? 'adaptive' : 'locked');
    console.error('own properties:', Object.getOwnPropertyNames(err));
    console.dir(err, { depth: 12 });
    for (const key of ['details','audit','recovery','cause','data']) {
      if (err?.[key] !== undefined) {
        console.error(`\n${key}:`);
        console.dir(err[key], { depth: 12 });
      }
    }
  }
}
