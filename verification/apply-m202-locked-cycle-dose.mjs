import fs from 'node:fs';
import crypto from 'node:crypto';

const read = p => fs.readFileSync(p, 'utf8');
const write = (p, s) => fs.writeFileSync(p, s);
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');

function replaceOnce(text, from, to, label) {
  if (text.includes(to)) return text;
  if (!text.includes(from)) throw new Error(`M202 patch anchor missing: ${label}`);
  return text.replace(from, to);
}

let volume = read('modules/next-engine/volume-repair.js');
volume = replaceOnce(volume,
  'function rebuild(program, changes, legacyExercises, addition = null) {',
  'function rebuild(program, changes, legacyExercises, addition = null, options = {}) {\n    const preserveRoster = !!options.preserveRoster;',
  'volume rebuild options');
volume = replaceOnce(volume,
  '        if (change.remove) {\n            const di = next.days.findIndex(d => d.id === change.dayId), day = next.days[di];',
  '        if (change.remove) {\n            if (preserveRoster) return null;\n            const di = next.days.findIndex(d => d.id === change.dayId), day = next.days[di];',
  'roster-preserving removal guard');
volume = replaceOnce(volume,
  '        if (e.sets < 2) return null; // avoid new one-set fragments',
  '        if (e.sets < (preserveRoster ? 1 : 2)) return null; // locked cycles may retain a one-set accessory rather than delete the roster entry',
  'roster-preserving set floor');
volume = replaceOnce(volume,
  '    if (addition) {\n        const di = next.days.findIndex(d => d.id === addition.dayId), day = next.days[di], s = sessions[di];',
  '    if (addition) {\n        if (preserveRoster) return null;\n        const di = next.days.findIndex(d => d.id === addition.dayId), day = next.days[di], s = sessions[di];',
  'roster-preserving addition guard');
volume = replaceOnce(volume,
  'export function repairShellVolume(program, legacyExercises = []) {\n    const before = auditShellVolume(program, legacyExercises);',
  'export function repairShellVolume(program, legacyExercises = [], options = {}) {\n    const preserveRoster = !!options.preserveRoster;\n    const before = auditShellVolume(program, legacyExercises);',
  'repair options');
volume = replaceOnce(volume,
  '            const candidate = rebuild(current, ops, legacyExercises, addition);',
  '            const candidate = rebuild(current, ops, legacyExercises, addition, { preserveRoster });',
  'repair rebuild forwarding');
write('modules/next-engine/volume-repair.js', volume);

let cycle = read('modules/next-engine/cycle-runtime-adapter.js');
cycle = replaceOnce(cycle,
  "import { shellConfigToNextRequest, nextProgramToShellProgram, NextShellAdapterError } from './app-shell-adapter.js';\nimport { firstPassingCapacityProgram } from './capacity-generation.js';",
  "import { shellConfigToNextRequest, nextProgramToShellProgram, NextShellAdapterError } from './app-shell-adapter.js';\nimport { repairShellVolume } from './volume-repair.js';\nimport { firstPassingCapacityProgram } from './capacity-generation.js';",
  'cycle volume repair import');
cycle = replaceOnce(cycle,
  '        const legacy = nextProgramToShellProgram(next, cfg, options.legacyExercises, options.makeId);',
  '        let legacy = nextProgramToShellProgram(next, cfg, options.legacyExercises, options.makeId);',
  'mutable legacy block');
cycle = replaceOnce(cycle,
  "        attachBlockContext(legacy, next, blockRequest(baseRequest, spec.phase), baseRequest, { templateId, plannedIndex: i, blockIndex: i, label: spec.label, weeks: spec.weeks, phase: spec.phase, preview: i > 0, adaptBetweenBlocks: !!options.adaptBetweenBlocks });\n        blocks.push(legacy);",
  "        attachBlockContext(legacy, next, blockRequest(baseRequest, spec.phase), baseRequest, { templateId, plannedIndex: i, blockIndex: i, label: spec.label, weeks: spec.weeks, phase: spec.phase, preview: i > 0, adaptBetweenBlocks: !!options.adaptBetweenBlocks });\n        // Static cycles intentionally lock exercise identity across blocks. The old retargeter scaled\n        // base sets and accepted an engine-pass block before checking the exact week-by-week public\n        // regional ceilings used by the UI, so a strength block could still show accessory overflow.\n        // Reuse the verified shell-volume transaction after weekly cells exist, but forbid additions\n        // and removals: only accessory set counts may move, and every proposal is engine re-audited.\n        if (!options.adaptBetweenBlocks && i > 0) {\n            const rosterBefore = legacy.days.map(day => [...day.exercises]);\n            const volumeRepair = repairShellVolume(legacy, options.legacyExercises, { preserveRoster: true });\n            legacy = volumeRepair.program;\n            const rosterAfter = legacy.days.map(day => [...day.exercises]);\n            if (JSON.stringify(rosterAfter) !== JSON.stringify(rosterBefore))\n                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_ROSTER_CHANGED', 'Locked cycle volume repair changed the exercise roster.');\n            const beforeOver = volumeRepair.before.issues.filter(issue => issue.status === 'over');\n            const afterOver = volumeRepair.after.issues.filter(issue => issue.status === 'over');\n            if (afterOver.length)\n                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_VOLUME_REJECTED', `The locked exercise skeleton could not safely fit the ${phaseLabel(spec.phase).toLowerCase()} accessory-volume ceiling.`, afterOver);\n            next = legacy.nextEngine.program;\n            legacy.nextEngine.lockedCycleVolumeRepair = {\n                status: volumeRepair.status, changed: volumeRepair.changed, rosterPreserved: true,\n                beforeOver: beforeOver.map(issue => ({ region: issue.region, actual: issue.v, ceiling: issue.mrv })),\n                afterOver: []\n            };\n        }\n        blocks.push(legacy);",
  'locked cycle shell-volume reconciliation');
write('modules/next-engine/cycle-runtime-adapter.js', cycle);

const test = `import assert from 'node:assert/strict';\nimport { EXERCISES, EQUIPMENT } from '../modules/App.js';\nimport { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';\nimport { auditShellVolume } from '../modules/next-engine/volume-repair.js';\n\nconst base = { name: 'M202 locked-cycle dose', split: 'ppl', days: 5, session: 's90', goal: 'both',\n  experience: 'intermediate', weeks: 6, deload: true, barbellCap: 3, equipment: EQUIPMENT.map(e => e.id) };\nconst seeds = [19908, 19811, 199, 20201];\nlet reproduced = 0, repaired = 0;\nfor (const seed of seeds) {\n  let n = 0;\n  const cycle = generateNextCycleForShell({ templateId: 'powerbuilding', config: base, legacyExercises: EXERCISES,\n    seed, adaptBetweenBlocks: false, makeId: () => 'm202-' + seed + '-' + (++n) });\n  const roster = cycle.blocks[0].days.map(day => [...day.exercises]);\n  for (let i = 1; i < cycle.blocks.length; i++) {\n    const block = cycle.blocks[i];\n    assert.deepEqual(block.days.map(day => [...day.exercises]), roster, 'locked block ' + i + ' must preserve every exercise identity');\n    assert.equal(block.nextEngine.program.audit.result, 'pass', 'locked block ' + i + ' engine audit');\n    const audit = auditShellVolume(block, EXERCISES);\n    const over = audit.issues.filter(issue => issue.status === 'over');\n    assert.deepEqual(over, [], 'locked block ' + i + ' must not exceed public phase ceilings');\n    assert.ok(audit.weeks.every(w => w.sessions.every(s => s.estimatedMinutes <= s.maxMinutes)), 'locked block ' + i + ' must still fit session time');\n    const marker = block.nextEngine.lockedCycleVolumeRepair;\n    assert.equal(marker?.rosterPreserved, true, 'locked block must record roster-preserving reconciliation');\n    reproduced += marker.beforeOver.length;\n    repaired += marker.changed ? 1 : 0;\n  }\n}\nassert.ok(reproduced > 0, 'regression matrix must reproduce at least one pre-repair locked-cycle overflow');\nassert.ok(repaired > 0, 'at least one locked block must require a real set-count repair');\nconsole.log('PASS M202 locked-cycle dose: ' + seeds.length + ' powerbuilding cycles; ' + reproduced + ' reproduced overflow issue(s); roster preserved; all repaired blocks within displayed ceilings.');\n`;
write('verification/m202-locked-cycle-dose-test.mjs', test);

// Release identity for the candidate branch.
let app = read('modules/App.js').replace(/const __APP_VERSION__='4\\.0\\.0'; const __BUILD__='\\d+';/, "const __APP_VERSION__='4.0.0'; const __BUILD__='792';");
write('modules/App.js', app);
let index = read('index.html').replace(/build:'\\d+'/g, "build:'792'").replace(/build='\\d+'/g, "build='792'");
write('index.html', index);
let config = read('modules/next-engine/config.js').replace(/ENGINE_VERSION = '[^']+'/, "ENGINE_VERSION = '0.64.3'");
write('modules/next-engine/config.js', config);
const cache = 'pursuit-iron-production-v4-0-0-m202-locked-cycle-dose-b792';
let sw = read('sw.js').replace(/const CACHE="[^"]+"/, `const CACHE="${cache}"`);
write('sw.js', sw);

const profile = JSON.parse(read('BUILD_PROFILE.json'));
Object.assign(profile, { milestone: 'M202', source: 'M201 production baseline + locked-cycle phase-specific volume reconciliation',\n  engine: '0.64.3 with roster-preserving locked-cycle accessory-volume repair', cache,\n  uiMilestone: 'Pursuit Iron 4.0 Production Release · M202 Locked Cycle Dose' });
write('BUILD_PROFILE.json', JSON.stringify(profile, null, 2) + '\\n');

const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
Object.assign(manifest, { milestone: 'M202', build: 792, engineVersion: '0.64.3', cache, uiMilestone: profile.uiMilestone,\n  candidateStatus: 'verification_enforced_by_ci', runtimeHotfix: 'M202 reconcile locked-cycle weekly accessory volume to phase-specific displayed ceilings without changing the exercise roster' });
for (const file of Object.keys(manifest.runtimeFiles)) manifest.runtimeFiles[file] = hash(fs.readFileSync(file));
manifest.runtimeAggregate = hash(Object.keys(manifest.runtimeFiles).sort().map(file => file + ':' + manifest.runtimeFiles[file] + '\\n').join(''));
write('RELEASE_MANIFEST.json', JSON.stringify(manifest, null, 2) + '\\n');

let changelog = read('CHANGELOG.md');
if (!changelog.startsWith('## M202')) changelog = `## M202 — Locked-cycle phase volume reconciliation (build 792)\n\n- Locked/static cycle blocks now audit the exact weekly regional volume shown in the UI after phase retargeting.\n- Over-limit accessory work is reduced transactionally while every exercise identity remains locked and strength work stays protected.\n- Added a powerbuilding regression that reproduces the prior overflow, verifies roster identity, session-time fit, engine pass status, and zero remaining displayed-region overflow.\n- Pursuit Engine 0.64.3; service-worker cache rotated for build 792.\n\n` + changelog;
write('CHANGELOG.md', changelog);

const report = `# M202 — Locked-cycle phase volume reconciliation\n\nPursuit Iron 4.0.0, build 792. Pursuit Engine 0.64.3.\n\n## Problem\n\nStatic cycles intentionally preserve the same exercise roster across blocks. The phase retargeter correctly changed reps, RIR, rest, progression, and base set counts, then stopped once the core engine audit passed. The shell later expanded those base prescriptions into exact working-week cells. In some powerbuilding strength blocks, that week-by-week realization could leave accessory regional volume above the phase-specific ceiling displayed by the app (the audit finding that opened this pass was calf volume reaching 12 effective sets against a 9.6-set displayed upper boundary).\n\n## Change\n\nLocked blocks now run the existing verified shell-volume transaction after their weekly cells are built. A new preserve-roster mode forbids exercise additions and removals and permits a one-set accessory when that is the safe way to retain the locked movement. The transaction still excludes protected strength roles, rejects any proposal that worsens the engine audit, honors time limits and regional floors, and rechecks every working week. If a locked block still has a displayed-region overflow after legal set-count repairs, cycle creation fails closed instead of presenting an internally inconsistent plan.\n\n## Regression contract\n\nThe M202 regression builds multiple 5-day PPL powerbuilding cycles with exercise adaptation disabled. It requires at least one previously overflowing block to be reproduced, then verifies that the repair actually changes set counts, every later block preserves the first block's exercise IDs exactly, all core engine audits pass, all sessions fit their time cap, and no working week exceeds the public phase-specific regional ceiling.\n`;
write('M202_LOCKED_CYCLE_DOSE_REPORT.md', report);

console.log('M202 patch applied; candidate build 792 / engine 0.64.3 prepared.');
