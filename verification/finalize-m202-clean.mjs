import fs from 'node:fs';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const showMain = path => execFileSync('git', ['show', `origin/main:${path}`], { encoding: 'utf8' });
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
function replaceOnce(text, from, to, label) {
  if (!text.includes(from)) throw new Error(`M202 finalizer anchor missing: ${label}`);
  return text.replace(from, to);
}

// Rebuild the volume transaction from the production M201 source and apply only the minimal
// M202 contract. This intentionally discards every diagnostic/experimental mutation used while
// isolating the tied-week rounding failure.
let volume = showMain('modules/next-engine/volume-repair.js');
volume = replaceOnce(volume,
  'function rebuild(program, changes, legacyExercises, addition = null) {',
  'function rebuild(program, changes, legacyExercises, addition = null, options = {}) {\n    const preserveRoster = !!options.preserveRoster;',
  'rebuild preserve-roster option');
volume = replaceOnce(volume,
  '        if (change.remove) {',
  '        if (change.remove) {\n            if (preserveRoster) return null;',
  'forbid locked removal');
volume = replaceOnce(volume,
  '        if (e.sets < 2) return null; // avoid new one-set fragments',
  '        if (e.sets < (preserveRoster ? 1 : 2)) return null; // locked cycles may retain a one-set accessory instead of changing the roster',
  'locked one-set floor');
volume = replaceOnce(volume,
  '    if (addition) {',
  '    if (addition) {\n        if (preserveRoster) return null;',
  'forbid locked addition');

const insertion = `/** Static locked-cycle finishing pass. Base-set repair must finish first because rebuilding a\n * session regenerates all of its work-week cells. This pass touches generated work-week set counts\n * only, preserves exercise identity and strength roles, and never allows another region or the\n * session clock to leave its prior safe envelope. Tied worst weeks may require several individually\n * non-worsening trims before the block-wide maximum falls. */\nexport function reconcileLockedCycleWeekOverflows(program, legacyExercises = []) {\n    let current = program, audit = auditShellVolume(program, legacyExercises), changed = false;\n    const context = createEngineContext(requestOf(program));\n    const safeCell = (before, after, issue) => {\n        if (after.missing || deficit(after) > deficit(before) + EPS) return false;\n        const bw = before.weeks[issue.week - 1], aw = after.weeks[issue.week - 1];\n        if (!bw || !aw || aw.regions[issue.region] >= bw.regions[issue.region] - EPS) return false;\n        for (let wi = 0; wi < after.weeks.length; wi++) {\n            const b = before.weeks[wi], a = after.weeks[wi];\n            for (const target of before.targets) {\n                const lo = Math.min(target.mev, b.regions[target.region]);\n                const hi = Math.max(target.mrv, b.regions[target.region]);\n                if (a.regions[target.region] + EPS < lo || a.regions[target.region] > hi + EPS) return false;\n            }\n            for (let di = 0; di < a.sessions.length; di++)\n                if (a.sessions[di].estimatedMinutes > Math.max(a.sessions[di].maxMinutes, b.sessions[di].estimatedMinutes)) return false;\n        }\n        return true;\n    };\n    for (let guard = 0; guard < 96; guard++) {\n        const issue = audit.issues.find(x => x.status === 'over');\n        if (!issue) break;\n        const week = audit.weeks[issue.week - 1];\n        const candidates = week.sessions.flatMap(session => session.exercises.map(exercise => ({ session, exercise,\n            def: context.exerciseById(exercise.exerciseId) })))\n            .filter(x => x.def && !STRENGTH.has(x.exercise.role) && x.exercise.sets > 1 && publicRegionContribution(x.def, issue.region) > 0)\n            .sort((a, b) => (a.def.flags.compound ? 1 : 0) - (b.def.flags.compound ? 1 : 0) ||\n                publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) ||\n                b.exercise.sets - a.exercise.sets || a.exercise.shellKey.localeCompare(b.exercise.shellKey));\n        let best = null;\n        for (const x of candidates) {\n            const key = x.exercise.shellKey, cells = current.nextWeekPrescriptions?.[key], prior = cells?.[issue.week];\n            if (!prior || !Number.isFinite(prior.sets) || prior.sets <= 1) continue;\n            const candidate = { ...current, nextWeekPrescriptions: { ...current.nextWeekPrescriptions,\n                [key]: { ...cells, [issue.week]: { ...prior, sets: prior.sets - 1 } } } };\n            const after = auditShellVolume(candidate, legacyExercises);\n            if (!safeCell(audit, after, issue)) continue;\n            const score = deficit(audit) - deficit(after);\n            if (!best || score > best.score + EPS) best = { candidate, after, score };\n        }\n        if (!best) break;\n        current = best.candidate;\n        audit = best.after;\n        changed = true;\n    }\n    return { program: current, after: audit, changed,\n        status: audit.issues.some(x => x.status === 'over') ? (changed ? 'partial' : 'unable') : changed ? 'success' : 'unchanged' };\n}\n\n`;
const repairComment = `/** Verified transaction: existing set allocation, then reallocation, then one compatible new\n * movement. Every proposal uses the same public region ledger and complete engine safety audit.\n * Failed proposals are discarded; a partial repair is reported as partial, never as success. */`;
if (!volume.includes(repairComment)) throw new Error('M202 finalizer anchor missing: repair comment');
volume = volume.replace(repairComment, insertion + repairComment);
volume = replaceOnce(volume,
  'export function repairShellVolume(program, legacyExercises = []) {\n    const before = auditShellVolume(program, legacyExercises);',
  'export function repairShellVolume(program, legacyExercises = [], options = {}) {\n    const preserveRoster = !!options.preserveRoster;\n    const before = auditShellVolume(program, legacyExercises);',
  'repair preserve-roster option');
volume = replaceOnce(volume,
  '            const candidate = rebuild(current, ops, legacyExercises, addition);',
  '            const candidate = rebuild(current, ops, legacyExercises, addition, { preserveRoster });',
  'forward preserve-roster option');
fs.writeFileSync('modules/next-engine/volume-repair.js', volume);

// Rebuild cycle adapter from production M201 and insert only the post-materialization locked-cycle
// reconciliation. The immutable exercise skeleton is checked before and after, and remaining overage
// fails closed rather than silently disagreeing with the UI ceiling.
let cycle = showMain('modules/next-engine/cycle-runtime-adapter.js');
cycle = replaceOnce(cycle,
  "import { shellConfigToNextRequest, nextProgramToShellProgram, NextShellAdapterError } from './app-shell-adapter.js';\nimport { firstPassingCapacityProgram } from './capacity-generation.js';",
  "import { shellConfigToNextRequest, nextProgramToShellProgram, NextShellAdapterError } from './app-shell-adapter.js';\nimport { repairShellVolume, reconcileLockedCycleWeekOverflows } from './volume-repair.js';\nimport { firstPassingCapacityProgram } from './capacity-generation.js';",
  'cycle repair import');
cycle = replaceOnce(cycle,
  '        const legacy = nextProgramToShellProgram(next, cfg, options.legacyExercises, options.makeId);',
  '        let legacy = nextProgramToShellProgram(next, cfg, options.legacyExercises, options.makeId);',
  'mutable shell block');
const lockedBlock = `        // Static cycles lock exercise identity across blocks, but phase retargeting is complete\n        // before the shell expands base sets into exact work-week cells. Reconcile those exact public\n        // regional doses after materialization without adding, removing, or swapping any movement.\n        if (!options.adaptBetweenBlocks && i > 0) {\n            const rosterBefore = legacy.days.map(day => [...day.exercises]);\n            const baseRepair = repairShellVolume(legacy, options.legacyExercises, { preserveRoster: true });\n            const weeklyRepair = reconcileLockedCycleWeekOverflows(baseRepair.program, options.legacyExercises);\n            legacy = weeklyRepair.program;\n            const rosterAfter = legacy.days.map(day => [...day.exercises]);\n            if (JSON.stringify(rosterAfter) !== JSON.stringify(rosterBefore))\n                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_ROSTER_CHANGED', 'Locked cycle volume repair changed the exercise roster.');\n            const beforeOver = baseRepair.before.issues.filter(issue => issue.status === 'over');\n            const afterOver = weeklyRepair.after.issues.filter(issue => issue.status === 'over');\n            if (afterOver.length)\n                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_VOLUME_REJECTED', \`The locked exercise skeleton could not safely fit the \${phaseLabel(spec.phase).toLowerCase()} accessory-volume ceiling.\`, afterOver);\n            next = legacy.nextEngine.program;\n            legacy.nextEngine.lockedCycleVolumeRepair = {\n                status: baseRepair.changed || weeklyRepair.changed ? 'success' : 'unchanged',\n                changed: baseRepair.changed || weeklyRepair.changed,\n                rosterPreserved: true,\n                beforeOver: beforeOver.map(issue => ({ region: issue.region, actual: issue.v, ceiling: issue.mrv })),\n                afterOver: []\n            };\n        }\n`;
cycle = replaceOnce(cycle,
  '        blocks.push(legacy);',
  lockedBlock + '        blocks.push(legacy);',
  'locked cycle reconciliation');
fs.writeFileSync('modules/next-engine/cycle-runtime-adapter.js', cycle);

const report = `# M202 — Locked-cycle phase volume reconciliation\n\nPursuit Iron 4.0.0, build 792. Pursuit Engine 0.64.3.\n\n## Problem\n\nStatic cycles deliberately keep the same exercise skeleton across blocks. Phase retargeting happens before the shell expands base prescriptions into exact work-week set counts, so whole-session rounding could leave a later strength block above the phase-specific regional ceiling shown by the app even though the immutable engine program passed. The reproduced case was 12 effective lower-leg sets against a 9.6-set strength-block ceiling.\n\n## Fix\n\nM202 uses two separate transactions. First, the existing shell-volume repair may change accessory base-set counts while a preserve-roster mode forbids exercise additions/removals, permits a one-set accessory floor, and continues to protect all strength roles and re-audit the engine. Second, after base repair has stopped, a locked-cycle finishing pass may reduce generated work-week accessory cells only. That second pass is intentionally separate because rebuilding a base session regenerates its weekly cells. It protects exercise identity and strength work, never increases session time, and never lets another public region leave its prior safe envelope.\n\nTied worst weeks are handled explicitly: a trim can improve the current offending week while the same block-wide maximum moves to the next tied week. Such a plateau step is accepted only when total deficit does not worsen and the exact offending week/region strictly improves. The pass continues until the displayed ceiling is satisfied or no safe trim remains; unresolved overage fails cycle creation closed.\n\n## Regression contract\n\nThe M202 regression builds multiple 5-day PPL powerbuilding cycles with exercise adaptation disabled. It must reproduce at least one pre-repair locked-cycle overflow, perform a real repair, preserve every exercise ID across all blocks, keep the core engine audit passing, keep every session within its time cap, and leave zero phase-specific displayed-region overages. M200 regional-dose, M199 volume-repair, and M201 workout-prescription regressions are also required to pass.\n`;
fs.writeFileSync('M202_LOCKED_CYCLE_DOSE_REPORT.md', report);

// Runtime hashes must describe the cleaned candidate, not the diagnostic implementation that was
// used to discover it.
const manifest = JSON.parse(fs.readFileSync('RELEASE_MANIFEST.json', 'utf8'));
for (const file of Object.keys(manifest.runtimeFiles)) manifest.runtimeFiles[file] = hash(fs.readFileSync(file));
manifest.runtimeAggregate = hash(Object.keys(manifest.runtimeFiles).sort().map(file => file + ':' + manifest.runtimeFiles[file] + '\n').join(''));
manifest.runtimeHotfix = 'M202 reconcile locked-cycle weekly accessory volume to phase-specific displayed ceilings without changing the exercise roster';
fs.writeFileSync('RELEASE_MANIFEST.json', JSON.stringify(manifest, null, 2) + '\n');

console.log('M202 clean final candidate written from M201 production source.');
// Trigger marker: verify this clean finalizer under the cleanup-only workflow.
