import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';

function primaryPart(def) {
  if (def.legacyPart) return def.legacyPart;
  const top = Object.entries(def.muscles ?? {}).sort((a,b)=>(b[1]?.credit??0)-(a[1]?.credit??0))[0]?.[0] ?? 'core';
  return ({back:'lats',side_delts:'shoulders',rear_delts:'shoulders',front_delts:'shoulders',core:'abs'})[top] ?? top;
}
const legacyExercises=[...EXERCISE_MAP.values()].map((def,index)=>({
  id:def.id,name:def.name,part:primaryPart(def),pattern:def.movementFamily,
  type:def.flags?.compound?'compound':'isolation',region:'full',equip:[...(def.equipment??[])],pri:100-index
}));
const baseConfig={
  name:'M193 Strength Peak', experience:'advanced', goal:'strength', split:'upper_lower', days:4, session:'s90',
  progression:'auto', progressionStyle:'auto', equipment:['barbell','bench','rack','dumbbell','cable','machine','smith','legpress','pullup'],
  noBodyweight:false,noSupersets:false,barbellCap:3,unit:'lb',deload:false,endless:false
};
let id=0;
const makeId=()=>`m193-${++id}`;

// ADAPT-BETWEEN-BLOCKS PREVIEW: the 4-week intensification block must be selected as a 4-week block now,
// not as phase-transition's old six-week fallback. That specifically prevents wave loading here.
const adaptive=generateNextCycleForShell({
  templateId:'strength_peak',config:baseConfig,banned:[],legacyExercises,seed:19301,makeId,adaptBetweenBlocks:true
});
assert.equal(adaptive.blocks.length,3,'strength peak should prebuild all three blocks');
assert.deepEqual(adaptive.blocks.map(b=>b.config.weeks),[4,4,2],'prebuilt shell blocks must retain template durations');
const adaptiveIntensify=adaptive.blocks[1];
assert.equal(adaptiveIntensify.nextEngine.program.phase,'intensification');
const adaptivePrimary=adaptiveIntensify.nextEngine.program.sessions.flatMap(s=>s.exercises).filter(ex=>ex.role==='primary_strength');
assert.ok(adaptivePrimary.length>0,'intensification preview should have primary strength work');
for (const ex of adaptivePrimary) {
  assert.notEqual(ex.progressionStyle,'wave',`4-week cycle preview must not choose a 5+ week wave for ${ex.name}`);
  assert.ok(ex.progressionSelection?.reason,'adaptive preview must carry a selector explanation');
}
assert.ok(adaptiveIntensify.nextEngine.progressionPlan.every(x=>x.style!=='wave' || x.role!=='primary_strength'),'shell preview must serialize the same short-block progression decision');

// STATIC / LOCKED-EXERCISE PREVIEW: this path used to change only the instruction string and silently retain
// the previous block's progressionStyle/selection metadata. It must now truly reselect for target phase+weeks.
id=100;
const locked=generateNextCycleForShell({
  templateId:'strength_peak',config:baseConfig,banned:[],legacyExercises,seed:19302,makeId,adaptBetweenBlocks:false
});
const lockedIntensify=locked.blocks[1];
const lockedReviewed=lockedIntensify.nextEngine.program.sessions.flatMap(s=>s.exercises).filter(ex=>ex.progressionSelection?.previousStyle);
assert.ok(lockedReviewed.length>0,'locked preview should record a real before/after progression review');
for (const ex of lockedReviewed) {
  assert.equal(typeof ex.progressionSelection.changed,'boolean',`${ex.name} should carry changed/kept metadata`);
  assert.equal(ex.progressionSelection.changed,ex.progressionSelection.previousStyle!==ex.progressionStyle,`${ex.name} changed flag must match its styles`);
  assert.ok(ex.progressionSelection.reason?.length>12,`${ex.name} should explain the target-block selection`);
}
const lockedPrimary=lockedReviewed.filter(ex=>ex.role==='primary_strength');
assert.ok(lockedPrimary.length>0,'locked intensification preview should review primary strength progressions');
assert.ok(lockedPrimary.every(ex=>ex.progressionStyle!=='wave'),'locked 4-week intensification preview must also respect the short block');
assert.ok(lockedPrimary.some(ex=>/too short|short|block/i.test(ex.progressionSelection.reason)),'short-block primary selection should explain why a longer wave was not used when relevant');

// MANUAL CYCLE CHOICE: every preview block must preserve the user's explicit method while still refreshing
// target-block selection metadata rather than carrying stale metadata from block one.
id=200;
const manual=generateNextCycleForShell({
  templateId:'powerbuilding',config:{...baseConfig,name:'M193 Manual Cycle',goal:'both',progressionStyle:'double'},
  banned:[],legacyExercises,seed:19303,makeId,adaptBetweenBlocks:false
});
for (const [blockIndex,block] of manual.blocks.entries()) {
  for (const ex of block.nextEngine.program.sessions.flatMap(s=>s.exercises)) {
    assert.equal(ex.progressionStyle,'double',`manual double must persist in block ${blockIndex+1}: ${ex.name}`);
    assert.equal(ex.progressionSelection?.source,'manual',`manual source must remain explicit in block ${blockIndex+1}: ${ex.name}`);
  }
}
assert.ok(manual.blocks.slice(1).some(block=>block.nextEngine.program.sessions.flatMap(s=>s.exercises).some(ex=>ex.progressionSelection?.previousStyle==='double')),'later manual previews should refresh before/after metadata');

// Actual history-driven cycle advancement and standalone->cycle conversion share the same adapter. Keep a
// wiring guard alongside the behavioral preview tests so they cannot regress to the old context-dropping calls.
const runtime=fs.readFileSync(new URL('../modules/next-engine/cycle-runtime-adapter.js',import.meta.url),'utf8');
assert.match(runtime,/progressionEvidenceByExercise: analysis\.progressionEvidenceByExercise/,'real cycle advancement must pass per-lift M189 evidence');
assert.match(runtime,/techniqueLimitedExerciseIds: analysis\.techniqueLimitedExerciseIds/,'real cycle advancement must pass technique-limited evidence');
assert.match(runtime,/fatigueLimitedExerciseIds: analysis\.fatigueLimitedExerciseIds/,'real cycle advancement must pass fatigue-limited evidence');
assert.match(runtime,/nextBlockWeeks: weeks/,'real cycle advancement must pass the actual target block duration');
assert.ok((runtime.match(/nextBlockWeeks: spec\.weeks/g)??[]).length>=2,'both new-cycle and converted-cycle adaptive previews must pass target block duration');
assert.ok((runtime.match(/retargetStatic\(previous, baseRequest, spec\.phase, spec\.weeks\)/g)??[]).length>=2,'both new-cycle and converted-cycle locked previews must pass target block duration');

console.log(`PASS M193: cycle creation built ${adaptive.blocks.length+locked.blocks.length+manual.blocks.length} audited preview blocks with phase/duration-aware progression; adaptive, locked, manual, conversion, and real-advance paths now carry progression context.`);
