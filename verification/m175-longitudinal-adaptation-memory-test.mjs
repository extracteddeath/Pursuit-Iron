import assert from 'node:assert/strict';
import fs from 'node:fs';
import { carryForwardAvoidedExercises } from '../modules/next-engine/workout-history-adapter.js';

const base={
  goal:{type:'mixed'},
  preferences:{preferredSplit:'full_body',avoidedExercises:['user-ban']},
  schedule:{days:[{day:'monday',maxMinutes:60}]}
};
const prior={
  ...base,
  preferences:{...base.preferences,avoidedExercises:['user-ban','poor-fit-block-1'],responseCapacityScale:.72}
};
const afterBlock2=carryForwardAvoidedExercises(base,prior,{replaceExerciseIds:['poor-fit-block-2']});
assert.deepEqual(afterBlock2.preferences.avoidedExercises,['user-ban','poor-fit-block-1','poor-fit-block-2']);
assert.equal(afterBlock2.preferences.responseCapacityScale,undefined,'temporary fatigue capacity must not become permanent adaptation memory');
assert.deepEqual(base.preferences.avoidedExercises,['user-ban'],'immutable base request must not be mutated');

// Prove memory survives another block even when the old poor-fit exercise is no longer present and
// therefore cannot be re-diagnosed in the new block.
const afterBlock3=carryForwardAvoidedExercises(base,afterBlock2,{replaceExerciseIds:['poor-fit-block-3']});
assert.deepEqual(afterBlock3.preferences.avoidedExercises,['user-ban','poor-fit-block-1','poor-fit-block-2','poor-fit-block-3']);

const cycle=fs.readFileSync(new URL('../modules/next-engine/cycle-runtime-adapter.js',import.meta.url),'utf8');
assert.ok(cycle.includes("carryForwardAvoidedExercises(clone(base), current?.nextEngine?.request, analysis)"),'adaptive cycle request must inherit accumulated avoidances');
assert.ok(cycle.includes('replaceExerciseIds: analysis.replaceExerciseIds'),'adaptive cycle phase transition must receive explicit poor-fit replacement evidence');
const history=fs.readFileSync(new URL('../modules/next-engine/workout-history-adapter.js',import.meta.url),'utf8');
assert.ok(history.includes('requestAdaptedFromHistory(baseRequest, snap.request, analysis)'),'standalone next-block adaptation must inherit accumulated avoidances');
console.log('M175 longitudinal adaptation memory OK: user bans and poor-fit replacements persist across standalone and adaptive-cycle blocks without persisting temporary fatigue capacity.');
