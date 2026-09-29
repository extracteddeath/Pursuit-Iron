import assert from 'node:assert/strict';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import {
  exerciseKnowledgeNode,
  exerciseRedundancyPenalty,
  exerciseSelectionRelationship,
  exerciseSetupInefficiencyPenalty
} from '../modules/next-engine/exercise-selection-intelligence.js';

const defs = createExerciseMap([]);
const get = id => {
  const def = defs.get(id);
  assert.ok(def, `missing fixture exercise ${id}`);
  return def;
};

const hack = get('hack_squat');
const legPress = get('leg_press');
const pulldown = get('neutral_pulldown');
const row = get('chest_supported_row');
const pushdown = get('cable_triceps');
const overhead = get('overhead_cable_triceps');
const cableCurl = get('cable_curl');
const preacher = get('preacher_curl');
const bench = get('barbell_bench');

const node = exerciseKnowledgeNode(bench);
assert.equal(node.id, 'barbell_bench');
assert.deepEqual(node.primaryStimulus, ['chest']);
assert.ok(node.secondaryStimulus.some(x => x.muscle === 'triceps' && x.credit === .5));
assert.equal(node.movementPattern, 'horizontal_press');
assert.ok(node.movementSubslot.includes('horizontal_press'));
assert.ok(node.setupDescriptors.length > 0);
assert.equal(typeof node.systemicFatigue, 'number');
assert.equal(typeof node.jointStress.shoulder, 'number');
assert.equal(typeof node.skillDemand, 'number');
assert.equal(node.strengthSpecificity.bench_press, 1);
assert.ok(Array.isArray(node.substitutions));
assert.ok(Array.isArray(node.attachments));

const quadPair = exerciseSelectionRelationship(hack, legPress);
assert.equal(quadPair.nearDuplicate, true, `hack squat + leg press should be recognized as a near-duplicate knee-dominant pair: ${JSON.stringify(quadPair)}`);
assert.ok(quadPair.stimulusSimilarity >= .9);
assert.ok(exerciseRedundancyPenalty(legPress, 'hypertrophy_compound', [{ def: hack, role: 'hypertrophy_compound' }], { priority: 'normal' }) >= 2,
  'ordinary programming should strongly penalize a second near-duplicate knee-dominant compound');
assert.ok(exerciseRedundancyPenalty(legPress, 'hypertrophy_compound', [{ def: hack, role: 'hypertrophy_compound' }], { priority: 'specialization' }) > 0,
  'specialization may justify a second variant but it should not be treated as free variety');
assert.equal(exerciseRedundancyPenalty(legPress, 'primary_strength', [{ def: hack, role: 'hypertrophy_compound' }], { priority: 'normal' }), 0,
  'strength-specific work remains exempt from hypertrophy redundancy pressure');

const backPair = exerciseSelectionRelationship(pulldown, row);
assert.equal(backPair.nearDuplicate, false, `vertical pull + row must remain complementary: ${JSON.stringify(backPair)}`);

const tricepsPair = exerciseSelectionRelationship(pushdown, overhead);
assert.equal(tricepsPair.nearDuplicate, false, `non-overhead + lengthened triceps work must remain complementary: ${JSON.stringify(tricepsPair)}`);
assert.notEqual(exerciseKnowledgeNode(pushdown).movementSubslot, exerciseKnowledgeNode(overhead).movementSubslot);

const cableNextToCable = exerciseSetupInefficiencyPenalty(cableCurl, [pushdown]);
const machineNextToCable = exerciseSetupInefficiencyPenalty(preacher, [pushdown]);
assert.ok(cableNextToCable < machineNextToCable,
  `equal-value cable work should have lower setup pressure than a station change (${cableNextToCable} vs ${machineNextToCable})`);

console.log('PASS M182 exercise-selection intelligence knowledge graph and pair semantics.');
