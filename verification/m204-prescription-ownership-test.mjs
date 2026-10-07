import { productionSource } from './production-source.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {
  generateNextProgramForShell,
  getNextShellCell,
  markUserPrescriptionOverride,
  clearUserPrescriptionOverride,
  shellPrescriptionFieldOwner
} from '../modules/next-engine/app-shell-adapter.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { reconcilePendingRepTargets } from '../modules/next-engine/workout-runtime.js';

const clone = x => structuredClone(x);
const legacy = [...EXERCISE_MAP.values()].map(d => ({
  id: d.id,
  name: d.name,
  part: d.legacyPart || 'chest',
  type: d.flags?.compound ? 'compound' : 'isolation',
  equip: []
}));
const equipment = ['barbell','rack','dumbbell','bench','cable','machine','smith','ezbar','pullup','dip','kettlebell','bands','legpress','hacksquat','legext','legcurl','calfmachine'];
const baseConfig = {
  name: 'M204 ownership contract', unit: 'lb', goal: 'both', experience: 'intermediate', split: 'full_body',
  days: 3, session: 's60', weeks: 4, equipment, focus: {}, reduce: [], barbellCap: 3,
  noBodyweight: false, noSupersets: false, deload: false
};
const build = progression => generateNextProgramForShell({
  config: { ...baseConfig, progression }, legacyExercises: legacy, seed: 204,
  makeId: () => `m204-${progression}`
}).program;

// AUTO: executable week cells are the prescription owner. Metadata/migration residue in overrides
// must not silently freeze later weeks or create a second writer for the same field.
const auto = build('auto');
const day = auto.days[0];
const key = `${day.id}:0`;
const original1 = getNextShellCell(auto, day, 0, 1);
const original2 = getNextShellCell(auto, day, 0, 2);
assert.ok(original1?.nextEngine && original2?.nextEngine);
assert.equal(original1.ownership.sets, 'engine');
assert.equal(original1.ownership.role, 'engine');
assert.equal(original1.ownership.progressionStyle, 'engine');
for (const field of ['sets','reps','rir','rest','techOverride','role','progressionStyle','progressionSelection'])
  assert.equal(Object.prototype.hasOwnProperty.call(auto.overrides[key], field), false, `auto override must not duplicate generated ${field}`);

const stale = clone(auto);
stale.overrides[key] = {
  ...stale.overrides[key],
  sets: 19,
  reps: '1',
  rir: '0',
  role: 'stale-role',
  progressionStyle: 'linear'
};
const stale1 = getNextShellCell(stale, stale.days[0], 0, 1);
const stale2 = getNextShellCell(stale, stale.days[0], 0, 2);
for (const [actual, expected] of [[stale1, original1], [stale2, original2]]) {
  assert.equal(actual.sets, expected.sets, 'engine-owned set count must ignore stale shell mirrors');
  assert.equal(actual.reps, expected.reps, 'engine-owned reps must ignore stale shell mirrors');
  assert.equal(actual.rir, expected.rir, 'engine-owned RIR must ignore stale shell mirrors');
  assert.equal(actual.role, expected.role, 'engine-owned role must ignore adapter metadata');
  assert.equal(actual.progressionStyle, expected.progressionStyle, 'engine-owned progression style must ignore adapter metadata');
}

// Legacy rest and technique edits were real user-facing controls before ownership markers existed.
// Preserve them during migration rather than breaking old saved programs.
stale.overrides[key].rest = 333;
stale.overrides[key].techOverride = 'drop set';
const legacyOwned = getNextShellCell(stale, stale.days[0], 0, 1);
assert.equal(legacyOwned.rest, 333);
assert.equal(legacyOwned.tech, 'drop set');
assert.equal(legacyOwned.ownership.rest, 'user');
assert.equal(legacyOwned.ownership.tech, 'user');

// Explicit user ownership wins field-by-field and clearing it hands authority straight back to the engine.
const explicit = clone(auto);
explicit.overrides[key] = markUserPrescriptionOverride(explicit.overrides[key], 'sets', Math.max(1, original1.sets - 1));
explicit.overrides[key] = markUserPrescriptionOverride(explicit.overrides[key], 'reps', '8-9');
assert.equal(shellPrescriptionFieldOwner(explicit, explicit.overrides[key], 'sets'), 'user');
assert.equal(getNextShellCell(explicit, explicit.days[0], 0, 1).sets, Math.max(1, original1.sets - 1));
assert.equal(getNextShellCell(explicit, explicit.days[0], 0, 1).reps, '8-9');
explicit.overrides[key] = clearUserPrescriptionOverride(explicit.overrides[key], 'sets');
explicit.overrides[key] = clearUserPrescriptionOverride(explicit.overrides[key], 'reps');
assert.equal(shellPrescriptionFieldOwner(explicit, explicit.overrides[key], 'sets'), 'engine');
assert.equal(getNextShellCell(explicit, explicit.days[0], 0, 1).sets, original1.sets);
assert.equal(getNextShellCell(explicit, explicit.days[0], 0, 1).reps, original1.reps);

// MANUAL: manual-mode shell values are deliberately user-owned and remain stable across engine weeks.
const manual = build('manual');
const manualDay = manual.days[0];
const manualKey = `${manualDay.id}:0`;
for (const field of ['sets','reps','rir','rest'])
  assert.equal(manual.overrides[manualKey].prescriptionOwners[field], 'user', `manual ${field} must be explicitly user-owned`);
const manual1 = getNextShellCell(manual, manualDay, 0, 1);
const manual2 = getNextShellCell(manual, manualDay, 0, 2);
assert.equal(manual1.sets, manual2.sets);
assert.equal(manual1.reps, manual2.reps);
assert.equal(manual1.rir, manual2.rir);
assert.equal(manual1.rest, manual2.rest);

// LIVE WORKOUT: explicit ownership beats the historical `auto` boolean. This prevents a future
// restore/tuner path from overwriting something the lifter typed merely because an old flag survived.
const cell = { reps: '8-12', range: '8-12' };
const rows = [
  { reps: '14', auto: true, valueOwner: 'prescription', done: false, target: { reps: '8-12' } },
  { reps: '14', auto: true, valueOwner: 'user', done: false, target: { reps: '8-12' } },
  { reps: '14', auto: false, done: false, target: { reps: '8-12' } },       // pre-M204 manual row
  { reps: '14', auto: true, done: false, target: { reps: '8-12' } }          // pre-M204 app row
];
const reconciled = reconcilePendingRepTargets(rows, cell);
assert.equal(reconciled[0].reps, '12', 'prescription-owned row must follow current range');
assert.equal(reconciled[1].reps, '14', 'explicit user ownership must beat stale auto=true');
assert.equal(reconciled[2].reps, '14', 'legacy manual row must remain untouched');
assert.equal(reconciled[3].reps, '12', 'legacy automatic row remains backwards compatible');

// Static guardrails cover the two persistence boundaries that caused prior split-brain regressions.
const volumeRepair = fs.readFileSync(new URL('../modules/next-engine/volume-repair.js', import.meta.url), 'utf8');
assert.match(volumeRepair, /shellPrescriptionFieldOwner\(next, next\.overrides\?\.\[key\] \?\? \{\}, 'sets'\) === 'user'/,
  'volume repair must refuse to rewrite explicitly user-owned sets');
assert.doesNotMatch(volumeRepair, /next\.overrides\[key\]\s*=\s*\{[^\n]*sets:\s*e\.sets/,
  'engine volume repair must not mirror generated set counts into overrides');
const app = productionSource();
for (const marker of [
  'markUserPrescriptionOverride(cur, "rest", sec)',
  'commitProgram(apply)',
  'valueOwner: "prescription"',
  'valueOwner: "user"',
  'function userOwnsRuntimeSet(row)',
  'const p = typeof next === "function" ? next(program) : next;',
  'setProgram(p);'
]) assert.ok(app.includes(marker), `missing ownership boundary marker: ${marker}`);
assert.doesNotMatch(app, /setProgram\(prev => \{[\s\S]{0,350}setSaved\(/, 'persistent program writes must not side-effect setSaved from inside a React state updater');

console.log('PASS M204 prescription ownership: engine week cells own automatic prescriptions; explicit user/manual overrides win field-by-field; stale mirrors cannot freeze engine output; volume repair does not become a second set owner; live rows carry explicit ownership with legacy compatibility.');
