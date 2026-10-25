// Build 879 — experience-tier corrections from "Training Variables by Lifter Experience" (2026).
//   1. Novice multi-joint work stays at 2–3 RIR in every working week (deload 3–4); isolation keeps
//      its normal target so occasional 0-RIR calibration on stable single-joint lifts remains.
//   2. A novice on a 3-day/2-day full-body split trains the squat and hinge muscles directly on at
//      least two days a week.
// Intermediate and advanced output is unchanged by construction (byte-identical across 810 configs
// when this shipped); checks 3 and 9 keep the rules from leaking into those tiers.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import * as prescriptions from '../modules/next-engine/realizer-prescriptions.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { EXERCISES } from '../modules/engine-api.js';


// Soft-bound so a pre-879 control fails on BEHAVIOUR rather than throwing on a missing export.
const applyFloor = prescriptions.applyExperienceRirFloor ?? ((rir) => rir);
const FULL = ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'smith', 'pullup', 'dip', 'legpress', 'hacksquat', 'legext', 'legcurl', 'calfmachine'];
const COMPOUND = new Set(['hypertrophy_compound', 'primary_strength', 'secondary_strength']);
const low = rir => Number(String(rir).split('-')[0]);
const generate = (over) => {
    const config = { name: 'b879', unit: 'lb', goal: 'hypertrophy', experience: 'beginner', split: 'full_body', days: 3, session: 's60', weeks: 6,
        progression: 'auto', deload: true, equipment: FULL, focus: {}, reduce: [], barbellCap: 3, noBodyweight: false, noSupersets: false, ...over };
    try { return generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: over.seed ?? 7, makeId: () => 'b879' }).program; }
    catch { return null; }
};
const cellsOf = p => Object.values(p.nextWeekPrescriptions).flatMap(cells => Object.entries(cells).map(([w, c]) => ({ week: Number(w), ...c })));
const directDays = (p, muscle) => p.nextEngine.program.sessions
    .filter(s => s.exercises.some(e => (EXERCISE_MAP.get(e.exerciseId)?.muscles?.[muscle]?.credit ?? 0) >= 1)).length;

const tierPrograms = {};
for (const experience of ['beginner', 'intermediate', 'advanced']) {
    tierPrograms[experience] = [];
    for (const goal of ['hypertrophy', 'both', 'strength'])
        for (const [split, days] of [['full_body', 3], ['upper_lower', 4], ['ppl', 6]])
            for (const session of ['s40', 's60', 's90']) {
                const p = generate({ experience, goal, split, days, session });
                if (p) tierPrograms[experience].push(p);
            }
}

// 0. The fixture is ARMED: programs exist for every tier, and the novice ROLE BASE still asks 1 RIR
// on some compound, so check 1 is testing the floor rather than a fixture with nothing to floor.
for (const [tier, list] of Object.entries(tierPrograms)) assert.ok(list.length >= 20, `fixture armed: ${tier} produced ${list.length} programs`);
const noviceBaseBelow = tierPrograms.beginner.flatMap(p => p.nextEngine.program.sessions.flatMap(s => s.exercises))
    .filter(e => COMPOUND.has(e.role) && e.prescription.rir[0] < 2).length;
assert.ok(noviceBaseBelow > 0, 'fixture armed: novice role-base compounds below 2 RIR exist for the floor to act on');
console.log(`PASS 0 fixture armed (${Object.values(tierPrograms).map(l => l.length).join('/')} programs; ${noviceBaseBelow} novice base compounds below 2 RIR).`);

// 1. Every novice compound cell: working weeks ≥2 RIR, deload ≥3 RIR.
let workCells = 0;
for (const p of tierPrograms.beginner) for (const c of cellsOf(p).filter(c => COMPOUND.has(c.role))) {
    const deload = c.week > p.weeks;
    if (!deload) workCells++;
    assert.ok(low(c.rir) >= (deload ? 3 : 2), `novice ${c.role} week ${c.week} prescribes ${c.rir} RIR`);
}
assert.ok(workCells > 500, `check 1 covered ${workCells} working cells`);
console.log(`PASS 1 novice compounds never below 2 RIR (${workCells} working cells) and 3 RIR on deload.`);

// 2. Isolation is deliberately untouched: novice isolation still reaches 0 RIR somewhere.
const noviceIsoZero = tierPrograms.beginner.flatMap(cellsOf).filter(c => c.role === 'hypertrophy_isolation' && c.week <= 6 && low(c.rir) === 0).length;
assert.ok(noviceIsoZero > 0, 'novice isolation calibration (0 RIR) was removed');
console.log(`PASS 2 novice isolation keeps its 0-RIR calibration (${noviceIsoZero} cells).`);

// 3. The floor does not leak: intermediate and advanced compounds still reach 1 RIR.
for (const tier of ['intermediate', 'advanced']) {
    const atOne = tierPrograms[tier].flatMap(cellsOf).filter(c => COMPOUND.has(c.role) && c.week <= 6 && low(c.rir) === 1).length;
    assert.ok(atOne > 0, `${tier} compounds lost their 1-RIR weeks`);
}
console.log('PASS 3 intermediate/advanced compound effort unchanged by the novice floor.');

// 4. The pure rule, stated independently of the engine.
assert.deepEqual(applyFloor([1, 2], 'hypertrophy_compound', 'novice'), [2, 3]);
assert.deepEqual(applyFloor([0, 1], 'primary_strength', 'novice'), [2, 3]);
assert.deepEqual(applyFloor([3, 4], 'secondary_strength', 'novice'), [3, 4]);
assert.deepEqual(applyFloor([0, 2], 'hypertrophy_isolation', 'novice'), [0, 2]);
assert.deepEqual(applyFloor([1, 2], 'hypertrophy_compound', 'intermediate'), [1, 2]);
assert.deepEqual(applyFloor([1, 2], 'hypertrophy_compound', 'advanced'), [1, 2]);
console.log('PASS 4 applyExperienceRirFloor states the rule exactly.');

// 5. Every week projection receives the athlete's experience. A caller that omits it silently drops
// the floor, so name the one exemption rather than widening a pattern.
const engineDir = new URL('../modules/next-engine/', import.meta.url);
const EXEMPT = { 'shell-projection.js': 'reads only the projected set count' };
for (const file of fs.readdirSync(engineDir).filter(f => f.endsWith('.js'))) {
    const src = fs.readFileSync(new URL(file, engineDir), 'utf8').replace(/\/\*[\s\S]*?\*\/|\/\/.*$/gm, '');
    for (const m of src.matchAll(/prescriptionForSimulationWeek\(([^;]*?)\)\s*(?:\.|\?\.|;)/g)) {
        if (/export function/.test(src.slice(Math.max(0, m.index - 20), m.index))) continue;
        const args = m[1].split(',').length;
        if (EXEMPT[file]) continue;
        assert.ok(args >= 5, `${file}: prescriptionForSimulationWeek called without the experience option`);
    }
}
console.log('PASS 5 every RIR-reading week projection is told the athlete experience.');

// 6. Manual-mode plans seed their editable RIR from the base program; the floor applies there too.
const manual = generate({ progression: 'manual' });
assert.ok(manual, 'manual novice program generated');
const seeded = Object.entries(manual.overrides).filter(([, o]) => o.rir !== undefined).map(([key, o]) => ({ key, rir: o.rir, role: manual.nextWeekPrescriptions[key]?.[1]?.role }));
assert.ok(seeded.some(s => COMPOUND.has(s.role)), 'fixture armed: manual plan seeds a compound');
for (const s of seeded.filter(s => COMPOUND.has(s.role))) assert.ok(low(s.rir) >= 2, `manual novice seed ${s.key} = ${s.rir}`);
console.log(`PASS 6 manual novice plans seed compounds at 2+ RIR (${seeded.length} seeded slots).`);

// 7. Novice hypertrophy full body: quads AND hamstrings trained directly on ≥2 days, every program.
let fb = 0;
for (const days of [2, 3]) for (const session of ['s40', 's60', 's90']) for (const seed of [1, 2, 3]) {
    const p = generate({ days, session, seed });
    assert.ok(p, `novice full_body ${days}d ${session} seed ${seed} generated`);
    fb++;
    for (const muscle of ['quads', 'hamstrings'])
        assert.ok(directDays(p, muscle) >= 2, `novice full_body ${days}d ${session} seed ${seed}: ${muscle} on ${directDays(p, muscle)} day(s)`);
}
console.log(`PASS 7 novice hypertrophy full body trains squat and hinge muscles ≥2x/week (${fb} programs).`);

// 8. Across goals, quads always ≥2x; hamstrings ≥2x is a FLOOR that may rise and must not fall. Strength
// goals keep the one-deadlift-a-week structure (the anchor already meets the hamstring dose, so no
// hamstring work is allocated); raising that is a separate decision, not drift.
let hams2 = 0, total = 0;
for (const goal of ['hypertrophy', 'both', 'strength']) for (const session of ['s40', 's60', 's90']) for (const seed of [1, 2, 3]) {
    const p = generate({ goal, session, seed });
    if (!p) continue;
    total++;
    assert.ok(directDays(p, 'quads') >= 2, `novice full_body ${goal} ${session}: quads on ${directDays(p, 'quads')} day(s)`);
    if (directDays(p, 'hamstrings') >= 2) hams2++;
}
assert.ok(total === 27 && hams2 >= 15, `novice full body hamstrings ≥2x in ${hams2}/${total} (floor 15/27)`);
console.log(`PASS 8 novice full body quads ≥2x in all ${total}; hamstrings ≥2x in ${hams2}/${total} (floor 15).`);

// 9. Frequency rule does not leak: intermediate full body still allows a single hamstring day.
const interSingle = ['s40', 's60', 's90'].flatMap(session => [1, 2, 3].map(seed => generate({ experience: 'intermediate', session, seed })))
    .filter(Boolean).filter(p => directDays(p, 'hamstrings') < 2).length;
assert.ok(interSingle > 0, 'intermediate full-body structure changed by a novice-only rule');
console.log('PASS 9 novice frequency rule scoped to novices.');
