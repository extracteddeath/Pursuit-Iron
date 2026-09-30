import fs from 'node:fs';

const path = 'modules/next-engine/volume-repair.js';
let text = fs.readFileSync(path, 'utf8');
const from = `        if (!best) break;
        current = best.candidate; audit = best.after;`;
const to = `        // A static-cycle roster can be structurally safe while a one-set accessory is rounded
        // upward by the session-level week allocator. Once base-set proposals are exhausted, repair
        // only the offending generated work-week cell. This does not alter the exercise identity or
        // the base engine prescription; the same all-region, time, and engine-audit transaction still
        // has to show a strict improvement before the cell is accepted.
        if (!best && preserveRoster) for (const issue of audit.issues.filter(issue => issue.status === 'over')) {
            const week = audit.weeks[issue.week - 1];
            const exact = week.sessions.flatMap(session => session.exercises.map(exercise => ({ session, exercise,
                def: context.exerciseById(exercise.exerciseId) })))
                .filter(x => x.def && x.exercise.role === 'hypertrophy_isolation' && x.exercise.sets > 1 &&
                    publicRegionContribution(x.def, issue.region) > 0)
                .sort((a, b) => publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) ||
                    b.exercise.sets - a.exercise.sets || a.exercise.shellKey.localeCompare(b.exercise.shellKey));
            for (const x of exact) {
                const key = x.exercise.shellKey, cells = current.nextWeekPrescriptions?.[key], prior = cells?.[issue.week];
                if (!prior || !Number.isFinite(prior.sets) || prior.sets <= 1) continue;
                const candidate = { ...current,
                    nextWeekPrescriptions: { ...current.nextWeekPrescriptions,
                        [key]: { ...cells, [issue.week]: { ...prior, sets: prior.sets - 1 } } },
                    nextEngine: { ...current.nextEngine } };
                const after = auditShellVolume(candidate, legacyExercises);
                const keys = new Set([key]);
                if (!safeImprovement(audit, after, candidate, initialEngineAudit, keys)) continue;
                const score = deficit(audit) - deficit(after);
                if (!best || score > best.score + EPS) best = { candidate, after,
                    ops: [{ dayId: x.session.shellDayId, slot: x.exercise.shellSlot, delta: -1, week: issue.week, weeklyCell: true }],
                    addition: null, score };
            }
        }
        if (!best) break;
        current = best.candidate; audit = best.after;`;
if (text.includes(to)) {
  console.log('M202 direct-week fallback already applied.');
  process.exit(0);
}
if (!text.includes(from)) throw new Error('M202 direct-week fallback anchor not found');
fs.writeFileSync(path, text.replace(from, to));
console.log('M202 direct-week fallback applied.');
