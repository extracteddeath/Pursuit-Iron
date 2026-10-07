import assert from 'node:assert/strict';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { generateProgram } from '../modules/next-engine/generate.js';
import { shellConfigToNextRequest } from '../modules/next-engine/app-shell-adapter.js';
import { EXERCISES } from '../modules/App.js';

const gym = ['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
const config = { name: 'M227 canonicalization fixture', unit: 'kg', goal: 'both', experience: 'intermediate',
    split: 'full_body', days: 3, session: 's60', weeks: 6, progression: 'auto', deload: false,
    equipment: gym, focus: {}, reduce: [], barbellCap: 3, noBodyweight: false, noSupersets: false,
    volumeApproach: 'minimalist' };
const request = shellConfigToNextRequest(config, [], EXERCISES, 227);
request.schedule.days[0].equipmentOverride = ['barbell','rack','bench'];
const original = structuredClone(request);

const cosmetic = structuredClone(request);
cosmetic.equipment.available = cosmetic.equipment.available.map(item => ` ${item.toUpperCase()} `);
cosmetic.equipment.loading.unit = ' KG ';
cosmetic.equipment.bodyweight = ' ALLOW ';
cosmetic.restrictions.allowSupersets = ' TRUE ';
cosmetic.preferences.volumeApproach = ' MINIMALIST ';
cosmetic.preferences.preferredSplit = ' FULL_BODY ';
cosmetic.preferences.lockedSplit = ' FULL_BODY ';
cosmetic.schedule.days[0].day = ` ${cosmetic.schedule.days[0].day.toUpperCase()} `;
cosmetic.schedule.days[0].equipmentOverride = [' BARBELL ', ' RACK ', ' BENCH '];
const canonical = normalizeRequest(request);
const normalizedCosmetic = normalizeRequest(cosmetic);
assert.deepEqual(normalizedCosmetic.equipment, canonical.equipment);
assert.equal(normalizedCosmetic.restrictions.allowSupersets, true);
assert.equal(normalizedCosmetic.preferences.volumeApproach, 'minimalist');
assert.equal(normalizedCosmetic.preferences.preferredSplit, 'full_body');
assert.equal(normalizedCosmetic.preferences.lockedSplit, 'full_body');
assert.deepEqual(normalizedCosmetic.schedule.days[0].equipmentOverride, ['barbell','rack','bench']);
assert.equal(normalizedCosmetic.equipment.loading.unit, 'kg');
const options = { phase: 'hypertrophy_accumulation', blockWeeks: 5, progressionStyle: 'auto' };
const baselineProgram = generateProgram(request, options);
const cosmeticProgram = generateProgram(cosmetic, options);
assert.deepEqual(cosmeticProgram, baselineProgram);
assert.deepEqual(request, original, 'normalization/generation must not mutate the caller request');

const bools = structuredClone(request);
bools.restrictions.allowSupersets = ' false ';
bools.equipment.bodyweight = false;
const normalizedBools = normalizeRequest(bools);
assert.equal(normalizedBools.restrictions.allowSupersets, false);
assert.equal(normalizedBools.equipment.bodyweight, 'exclude');

const reject = (mutate, re) => {
    const bad = structuredClone(request);
    mutate(bad);
    assert.throws(() => normalizeRequest(bad), re);
};
reject(x => x.restrictions.allowSupersets = 'sometimes', /Allow supersets must be true or false/);
reject(x => x.equipment.bodyweight = 'sometimes', /bodyweight mode/i);
reject(x => x.equipment.loading.unit = 'stone', /Unsupported loading unit/);
reject(x => x.preferences.volumeApproach = 'maximalist', /Unsupported volume approach/);
reject(x => x.preferences.preferredSplit = 'unknown_split', /Unsupported preferred split/);
reject(x => x.preferences.lockedSplit = 'unknown_split', /Unsupported locked split/);
reject(x => x.preferences = [], /Preferences must be an object/);
reject(x => x.restrictions = 'none', /Restrictions must be an object/);
reject(x => x.equipment.loading = [], /Loading inventory must be an object/);
reject(x => x.schedule.days[0].equipmentOverride = 'barbell', /Per-day equipment override must be a list/);
reject(x => x.preferences.preferredExercises = 'bb-bench', /Preferred exercises must be a list/);

const custom = structuredClone(request);
custom.customExercises = [{
    id: ' custom-curl ', name: ' Cable Curl ',
    equipment: [' CABLE ', ' CABLE '], equipmentAlternatives: [[' DUMBBELL ']],
    muscles: { biceps: { credit: 1 } }, flags: { compound: false }
}];
const normalizedCustom = normalizeRequest(custom).customExercises[0];
assert.equal(normalizedCustom.id, 'custom-curl');
assert.equal(normalizedCustom.name, 'Cable Curl');
assert.deepEqual(normalizedCustom.equipment, ['cable']);
assert.deepEqual(normalizedCustom.equipmentAlternatives, [['dumbbell']]);
reject(x => x.customExercises = [{ id: 'x', name: 'X', equipment: ['cable'], muscles: { biceps: { credit: 1 } }, flags: { compound: 'false' } }], /compound flag.*true or false/i);
reject(x => x.customExercises = [{ id: 'x', name: 'X', equipment: ['cable'], muscles: { biceps: { credit: 1 } }, equipmentAlternatives: ['dumbbell'] }], /Equipment alternative 1.*list/i);

console.log('PASS M227: canonical input equivalence, semantic boolean safety, loading/split/volume validation, nested-shape rejection and custom-exercise normalization.');
