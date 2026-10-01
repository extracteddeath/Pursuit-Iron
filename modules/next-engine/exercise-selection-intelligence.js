import { armCoverageBias } from './arm-coverage.js';
import { functionalCoverageBiases } from './functional-coverage.js';
import { exerciseSetupDescriptors, setupTransitionCost } from './setup-economy.js';

const STRENGTH_ROLES = new Set(['primary_strength', 'secondary_strength', 'strength_support']);
const SPECIALIZATION_PRIORITIES = new Set(['high', 'specialization', 'primary']);
const COMPATIBLE_MOVEMENT_GROUP = Object.freeze({
  squat: 'knee_dominant',
  leg_press: 'knee_dominant',
  horizontal_press: 'horizontal_press',
  chest_adduction: 'chest_adduction',
  horizontal_pull: 'horizontal_pull',
  vertical_pull: 'vertical_pull',
  shoulder_extension: 'shoulder_extension',
  vertical_press: 'vertical_press',
  lateral_raise: 'lateral_raise',
  rear_delt: 'rear_delt',
  hip_hinge: 'hip_hinge',
  hip_extension: 'hip_extension',
  knee_flexion: 'knee_flexion',
  knee_extension: 'knee_extension',
  elbow_flexion: 'elbow_flexion',
  elbow_extension: 'elbow_extension',
  calf: 'calf',
  core: 'core',
  shrug: 'shrug',
  grip: 'grip',
  wrist_flexion: 'wrist_flexion',
  wrist_extension: 'wrist_extension',
  wrist_deviation: 'wrist_deviation',
  hip_abduction: 'hip_abduction',
  hip_adduction: 'hip_adduction',
  neck: 'neck',
  dorsiflexion: 'dorsiflexion'
});

const round = value => Math.round(value * 1000) / 1000;
const clamp01 = value => Math.max(0, Math.min(1, value));
const textFor = def => `${def?.id ?? ''} ${def?.name ?? ''} ${def?.legacyPattern ?? ''} ${def?.legacySubregion ?? ''} ${def?.legacyPart ?? ''}`.toLowerCase();
const unilateral = def => !!def?.flags?.unilateral || /\b(single[- ]?(?:arm|leg)|one[- ]arm|unilateral|b[- ]stance)\b/.test(textFor(def));

function primaryBias(def) {
  const primaries = Object.entries(def?.muscles ?? {})
    .filter(([, contribution]) => contribution && contribution.credit >= 1)
    .map(([muscle]) => muscle)
    .sort();
  if (primaries.length) return primaries.join('+');
  return Object.entries(def?.muscles ?? {})
    .filter(([, contribution]) => contribution && contribution.credit >= .5)
    .sort((a, b) => (b[1]?.credit ?? 0) - (a[1]?.credit ?? 0))[0]?.[0] ?? 'generic';
}

function movementSubslot(def) {
  if (!def) return 'unknown';
  const text = textFor(def);
  const pattern = String(def.legacyPattern ?? '').toLowerCase();
  const sub = String(def.legacySubregion ?? '').toLowerCase();
  const part = String(def.legacyPart ?? '').toLowerCase();
  const laterality = unilateral(def) ? 'unilateral' : 'bilateral';
  const kind = def.flags?.compound ? 'compound' : 'isolation';
  switch (def.movementFamily) {
    case 'squat':
    case 'leg_press':
      return `knee_dominant:${laterality}:${kind}`;
    case 'horizontal_press': {
      const angle = pattern.includes('incline') || /\bincline\b/.test(text) ? 'incline'
        : /\bdecline\b/.test(text) ? 'decline'
          : /\bdip\b/.test(text) ? 'dip'
            : /\bfloor\b/.test(text) ? 'floor' : 'flat';
      return `horizontal_press:${angle}:${laterality}:${kind}`;
    }
    case 'vertical_press': {
      const fn = pattern.includes('shoulder-flexion') || /front raise/.test(text) ? 'front_raise'
        : pattern.includes('scapular-upward') || /\by[- ]raise\b/.test(text) ? 'scapular_raise'
          : /landmine/.test(text) ? 'landmine_press' : 'overhead_press';
      return `vertical_press:${fn}:${laterality}:${kind}`;
    }
    case 'horizontal_pull': {
      const mechanic = /doorway row/.test(text) ? 'doorway_bodyweight'
        : /inverted row/.test(text) ? 'inverted_bodyweight'
          : part === 'lats' || sub.includes('lat') ? 'lat_bias'
            : part === 'upper_back' || sub.includes('upper_back') ? 'upper_back_bias' : 'row';
      return `horizontal_pull:${mechanic}:${laterality}:${kind}`;
    }
    case 'rear_delt':
      return `rear_delt:${/face pull/.test(text) ? 'face_pull' : /\brow\b/.test(text) ? 'rear_delt_row' : 'rear_delt_fly'}:${kind}`;
    case 'elbow_flexion': {
      const arm = armCoverageBias(def);
      const bias = arm === 'brachialis_bias' ? 'brachialis_bias' : arm === 'biceps_bias' ? 'biceps_bias'
        : /hammer|reverse[- ]?grip|reverse curl|pronated/.test(text) ? 'brachialis_bias' : 'biceps_bias';
      return `elbow_flexion:${bias}:${kind}`;
    }
    case 'elbow_extension': {
      const biases = functionalCoverageBiases(def);
      return `elbow_extension:${biases.includes('triceps_lengthened') ? 'lengthened' : 'non_overhead'}:${kind}`;
    }
    case 'calf': {
      const biases = functionalCoverageBiases(def);
      return `calf:${biases.includes('calves_bent_knee') ? 'bent_knee' : 'straight_knee'}:${kind}`;
    }
    case 'hip_hinge':
    case 'hip_extension':
      return `${def.movementFamily}:${primaryBias(def)}:${laterality}:${kind}`;
    case 'core':
      return `core:${pattern || (/side bend/.test(text) ? 'lateral-flexion' : 'generic')}:${kind}`;
    case 'wrist_deviation':
      return `wrist_deviation:${/radial/.test(text) ? 'radial' : /ulnar/.test(text) ? 'ulnar' : 'generic'}:${kind}`;
    case 'neck':
      return `neck:${/lateral/.test(text) ? 'lateral' : /extension|harness/.test(text) ? 'extension' : /curl|flexion/.test(text) ? 'flexion' : 'generic'}:${kind}`;
    default:
      return `${def.movementFamily}:${primaryBias(def)}:${laterality}:${kind}`;
  }
}

function weightedJaccard(a, b) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  let intersection = 0;
  let union = 0;
  for (const key of keys) {
    const av = Math.max(0, a[key] ?? 0);
    const bv = Math.max(0, b[key] ?? 0);
    intersection += Math.min(av, bv);
    union += Math.max(av, bv);
  }
  return union > 0 ? intersection / union : 0;
}

function setJaccard(a, b) {
  if (!a.length && !b.length) return .5;
  const as = new Set(a);
  const bs = new Set(b);
  const union = new Set([...as, ...bs]).size;
  if (!union) return .5;
  let intersection = 0;
  for (const item of as) if (bs.has(item)) intersection++;
  return intersection / union;
}

function numericProfileSimilarity(a, b) {
  const keys = ['systemic', 'axial', 'lowerBack', 'grip', 'shoulder', 'elbow', 'knee'];
  const distance = keys.reduce((sum, key) => sum + Math.abs((a?.[key] ?? 0) - (b?.[key] ?? 0)), 0);
  return clamp01(1 - distance / (keys.length * 7));
}

function specificitySimilarity(a, b) {
  return weightedJaccard(a ?? {}, b ?? {});
}

function stimulusCredits(def) {
  return Object.fromEntries(Object.entries(def?.muscles ?? {}).map(([muscle, contribution]) => [muscle, contribution?.credit ?? 0]));
}

/**
 * M182 canonical exercise knowledge node. It intentionally derives from the existing exercise catalog rather
 * than creating a second exercise database, so selection intelligence cannot drift from the source of truth.
 */
export function exerciseKnowledgeNode(def) {
  if (!def) return null;
  const functionalBiases = functionalCoverageBiases(def);
  const setups = exerciseSetupDescriptors(def);
  const primaryStimulus = Object.entries(def.muscles ?? {}).filter(([, c]) => c?.role === 'primary' || (c?.credit ?? 0) >= 1).map(([muscle]) => muscle).sort();
  const secondaryStimulus = Object.entries(def.muscles ?? {}).filter(([, c]) => (c?.credit ?? 0) > 0 && (c?.credit ?? 0) < 1).map(([muscle, c]) => ({ muscle, credit: c.credit, role: c.role ?? 'secondary' })).sort((a, b) => a.muscle.localeCompare(b.muscle));
  return Object.freeze({
    id: def.id,
    name: def.name,
    stimulus: Object.freeze(stimulusCredits(def)),
    primaryStimulus: Object.freeze(primaryStimulus),
    secondaryStimulus: Object.freeze(secondaryStimulus),
    lengthenedBias: def.lengthenedBias ?? def.metadata?.lengthenedBias ?? functionalBiases.find(bias => bias.includes('lengthened')) ?? null,
    functionalBiases: Object.freeze([...functionalBiases].sort()),
    armBias: armCoverageBias(def),
    movementPattern: def.movementFamily,
    movementGroup: COMPATIBLE_MOVEMENT_GROUP[def.movementFamily] ?? def.movementFamily,
    movementSubslot: movementSubslot(def),
    equipment: Object.freeze((def.equipment ?? []).slice().sort()),
    equipmentAlternatives: Object.freeze((def.equipmentAlternatives ?? []).map(setup => Object.freeze([...setup].sort()))),
    setupDescriptors: Object.freeze(setups.map(item => Object.freeze({ zone: item.zone, signature: item.signature, equipment: Object.freeze([...item.equipment]) }))),
    setupCost: def.setupCost ?? 0,
    stability: def.stability ?? 0,
    systemicFatigue: def.fatigue?.systemic ?? 0,
    jointStress: Object.freeze({
      shoulder: def.fatigue?.shoulder ?? 0,
      elbow: def.fatigue?.elbow ?? 0,
      knee: def.fatigue?.knee ?? 0,
      lowerBack: def.fatigue?.lowerBack ?? 0,
      grip: def.fatigue?.grip ?? 0,
      axial: def.fatigue?.axial ?? 0
    }),
    fatigueProfile: Object.freeze({ ...(def.fatigue ?? {}) }),
    skillDemand: def.technicalComplexity ?? 0,
    loadability: def.loadability ?? 0,
    strengthSpecificity: Object.freeze({ ...(def.liftSpecificity ?? {}) }),
    substitutions: Object.freeze([...(def.substitutions ?? def.replacements ?? def.swapIds ?? [])]),
    attachments: Object.freeze([...(def.attachments ?? def.attachmentOptions ?? def.legacyAttachments ?? [])]),
    compound: !!def.flags?.compound,
    unilateral: unilateral(def)
  });
}

export function exerciseSelectionRelationship(a, b) {
  if (!a || !b) return { score: 0, nearDuplicate: false, stimulusSimilarity: 0, movementSimilarity: 0, functionalSimilarity: 0, setupAffinity: 0 };
  const na = exerciseKnowledgeNode(a);
  const nb = exerciseKnowledgeNode(b);
  const stimulusSimilarity = weightedJaccard(na.stimulus, nb.stimulus);
  const movementSimilarity = na.movementSubslot === nb.movementSubslot ? 1 : na.movementGroup === nb.movementGroup ? .58 : 0;
  const functionalSimilarity = setJaccard([...na.functionalBiases, na.armBias].filter(Boolean), [...nb.functionalBiases, nb.armBias].filter(Boolean));
  const fatigueSimilarity = numericProfileSimilarity(na.fatigueProfile, nb.fatigueProfile);
  const skillStabilitySimilarity = 1 - (Math.abs(na.skillDemand - nb.skillDemand) + Math.abs(na.stability - nb.stability)) / 14;
  const setupAffinity = clamp01(1 - setupTransitionCost(a, b) / 1.5);
  const strengthSimilarity = specificitySimilarity(na.strengthSpecificity, nb.strengthSpecificity);
  const score = clamp01(
    stimulusSimilarity * .44 +
    movementSimilarity * .25 +
    functionalSimilarity * .09 +
    fatigueSimilarity * .07 +
    skillStabilitySimilarity * .05 +
    setupAffinity * .04 +
    strengthSimilarity * .06
  );
  const exact = a.id === b.id;
  const sameSubslot = na.movementSubslot === nb.movementSubslot;
  const nearDuplicate = exact || (sameSubslot && stimulusSimilarity >= .72 && score >= .72);
  return {
    score: round(score),
    nearDuplicate,
    exact,
    stimulusSimilarity: round(stimulusSimilarity),
    movementSimilarity: round(movementSimilarity),
    functionalSimilarity: round(functionalSimilarity),
    setupAffinity: round(setupAffinity),
    sameSubslot,
    subslot: sameSubslot ? na.movementSubslot : null
  };
}

/**
 * Numeric redundancy pressure used by M182 diagnostics and selection gates. Strength anchors are exempt.
 * Specialization can justify a second variant, but still carries a small penalty so equal-quality choices
 * prefer complementary work; a third near-duplicate is never treated as free variety.
 */
export function exerciseRedundancyPenalty(candidate, candidateRole, chosen, context = {}) {
  if (!candidate || STRENGTH_ROLES.has(candidateRole)) return 0;
  const priority = context.priority ?? 'normal';
  const specialized = candidateRole === 'specialization' || SPECIALIZATION_PRIORITIES.has(priority);
  let penalty = 0;
  for (const item of chosen ?? []) {
    const def = item?.def ?? item;
    const role = item?.role;
    if (!def || STRENGTH_ROLES.has(role)) continue;
    const relationship = exerciseSelectionRelationship(candidate, def);
    if (relationship.exact) {
      penalty += 100;
      continue;
    }
    if (relationship.nearDuplicate) {
      penalty += specialized ? .65 : 2.25;
      continue;
    }
    if (relationship.score >= .68 && relationship.stimulusSimilarity >= .72)
      penalty += specialized ? .12 : .35;
  }
  return round(penalty);
}

/**
 * Setup pressure is deliberately separate from redundancy. It should break ties between similarly useful
 * movements, never override a distinct function, a strength-specific anchor, or a user specialization need.
 */
export function exerciseSetupInefficiencyPenalty(candidate, chosen) {
  if (!candidate || !chosen?.length) return 0;
  const defs = chosen.map(item => item?.def ?? item).filter(Boolean);
  if (!defs.length) return 0;
  const nearestTransition = Math.min(...defs.map(def => setupTransitionCost(def, candidate)));
  const intrinsic = Math.max(0, (candidate.setupCost ?? 0) - 2) * .03;
  return round(nearestTransition * .18 + intrinsic);
}

