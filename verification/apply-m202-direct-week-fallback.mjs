import fs from 'node:fs';

const path = 'modules/next-engine/volume-repair.js';
let text = fs.readFileSync(path, 'utf8');
const from = `        if (!best) break;
        current = best.candidate; audit = best.after;`;
const to = `        // A static-cycle roster can be structurally safe while a one-set accessory is rounded
        // upward by the session-level week allocator. Once base-set proposals are exhausted, repair
        // only the offending generated work-week cell. This does not alter the exercise identity or
        // base engine prescription, so validate it at the shell layer where the change actually lives:
        // the displayed deficit must shrink, no other region may leave its prior safe envelope, and
        // no session may become longer. The engine audit is unchanged because engine sessions are unchanged.
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
                if (after.missing || deficit(after) >= deficit(audit) - EPS) continue;
                let exactSafe = true;
                for (let wi = 0; wi < after.weeks.length && exactSafe; wi++) {
                    const beforeWeek = audit.weeks[wi], afterWeek = after.weeks[wi];
                    for (const target of audit.targets) {
                        const lo = Math.min(target.mev, beforeWeek.regions[target.region]);
                        const hi = Math.max(target.mrv, beforeWeek.regions[target.region]);
                        if (afterWeek.regions[target.region] + EPS < lo || afterWeek.regions[target.region] > hi + EPS) {
                            exactSafe = false;
                            break;
                        }
                    }
                    if (!exactSafe) break;
                    for (let di = 0; di < afterWeek.sessions.length; di++) {
                        const a = afterWeek.sessions[di], b = beforeWeek.sessions[di];
                        if (a.estimatedMinutes > Math.max(a.maxMinutes, b.estimatedMinutes)) {
                            exactSafe = false;
                            break;
                        }
                    }
                }
                if (!exactSafe) continue;
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
