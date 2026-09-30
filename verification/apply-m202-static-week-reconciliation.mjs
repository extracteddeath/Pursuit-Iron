import fs from 'node:fs';

function replaceOnce(text, from, to, label) {
  if (text.includes(to)) return text;
  if (!text.includes(from)) throw new Error('M202 static-week anchor missing: ' + label);
  return text.replace(from, to);
}

let volume = fs.readFileSync('modules/next-engine/volume-repair.js', 'utf8');
const insertAnchor = `/** Verified transaction: existing set allocation, then reallocation, then one compatible new
 * movement. Every proposal uses the same public region ledger and complete engine safety audit.
 * Failed proposals are discarded; a partial repair is reported as partial, never as success. */`;
const fn = `/** Static-cycle finishing pass. Base repair and exact week-cell repair are deliberately separate:
 * rebuilding a base session regenerates its weekly cells, so mixing both mutation layers can undo a
 * safe weekly trim. This pass runs only after base repair has plateaued and changes generated work-week
 * cells only; exercise identity and the immutable engine program are untouched. */
export function reconcileLockedCycleWeekOverflows(program, legacyExercises = []) {
    let current = program, audit = auditShellVolume(program, legacyExercises), changed = false;
    const context = createEngineContext(requestOf(program));
    const safeCell = (before, after) => {
        if (after.missing || deficit(after) >= deficit(before) - EPS) return false;
        for (let wi = 0; wi < after.weeks.length; wi++) {
            const b = before.weeks[wi], a = after.weeks[wi];
            for (const target of before.targets) {
                const lo = Math.min(target.mev, b.regions[target.region]);
                const hi = Math.max(target.mrv, b.regions[target.region]);
                if (a.regions[target.region] + EPS < lo || a.regions[target.region] > hi + EPS) return false;
            }
            for (let di = 0; di < a.sessions.length; di++)
                if (a.sessions[di].estimatedMinutes > Math.max(a.sessions[di].maxMinutes, b.sessions[di].estimatedMinutes)) return false;
        }
        return true;
    };
    for (let guard = 0; guard < 96; guard++) {
        const issue = audit.issues.find(x => x.status === 'over');
        if (!issue) break;
        const week = audit.weeks[issue.week - 1];
        const candidates = week.sessions.flatMap(session => session.exercises.map(exercise => ({ session, exercise,
            def: context.exerciseById(exercise.exerciseId) })))
            .filter(x => x.def && !STRENGTH.has(x.exercise.role) && x.exercise.sets > 1 && publicRegionContribution(x.def, issue.region) > 0)
            .sort((a, b) => (a.def.flags.compound ? 1 : 0) - (b.def.flags.compound ? 1 : 0) ||
                publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) ||
                b.exercise.sets - a.exercise.sets || a.exercise.shellKey.localeCompare(b.exercise.shellKey));
        let best = null;
        for (const x of candidates) {
            const key = x.exercise.shellKey, cells = current.nextWeekPrescriptions?.[key], prior = cells?.[issue.week];
            if (!prior || !Number.isFinite(prior.sets) || prior.sets <= 1) continue;
            const candidate = { ...current, nextWeekPrescriptions: { ...current.nextWeekPrescriptions,
                [key]: { ...cells, [issue.week]: { ...prior, sets: prior.sets - 1 } } } };
            const after = auditShellVolume(candidate, legacyExercises);
            if (!safeCell(audit, after)) continue;
            const score = deficit(audit) - deficit(after);
            if (!best || score > best.score + EPS) best = { candidate, after, score, key, week: issue.week };
        }
        if (!best) break;
        current = best.candidate;
        audit = best.after;
        changed = true;
    }
    return { program: current, after: audit, changed,
        status: audit.issues.some(x => x.status === 'over') ? (changed ? 'partial' : 'unable') : changed ? 'success' : 'unchanged' };
}

`;
if (!volume.includes('export function reconcileLockedCycleWeekOverflows')) {
  if (!volume.includes(insertAnchor)) throw new Error('M202 static-week function insertion anchor not found');
  volume = volume.replace(insertAnchor, fn + insertAnchor);
}
fs.writeFileSync('modules/next-engine/volume-repair.js', volume);

let cycle = fs.readFileSync('modules/next-engine/cycle-runtime-adapter.js', 'utf8');
cycle = replaceOnce(cycle,
  `import { repairShellVolume } from './volume-repair.js';`,
  `import { repairShellVolume, reconcileLockedCycleWeekOverflows } from './volume-repair.js';`,
  'static-week import');
cycle = replaceOnce(cycle,
  `            const volumeRepair = repairShellVolume(legacy, options.legacyExercises, { preserveRoster: true });
            legacy = volumeRepair.program;`,
  `            let volumeRepair = repairShellVolume(legacy, options.legacyExercises, { preserveRoster: true });
            const weeklyReconciliation = reconcileLockedCycleWeekOverflows(volumeRepair.program, options.legacyExercises);
            if (weeklyReconciliation.changed || weeklyReconciliation.after !== volumeRepair.after)
                volumeRepair = { ...volumeRepair, program: weeklyReconciliation.program, after: weeklyReconciliation.after,
                    changed: volumeRepair.changed || weeklyReconciliation.changed,
                    status: weeklyReconciliation.status === 'success' && !weeklyReconciliation.after.issues.length ? 'success' : volumeRepair.status };
            legacy = volumeRepair.program;`,
  'static-week post-base reconciliation');
fs.writeFileSync('modules/next-engine/cycle-runtime-adapter.js', cycle);
console.log('M202 static weekly-cell reconciliation phase applied.');
