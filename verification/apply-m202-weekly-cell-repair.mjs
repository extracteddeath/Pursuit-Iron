import fs from 'node:fs';

const path = 'modules/next-engine/volume-repair.js';
let text = fs.readFileSync(path, 'utf8');
function replaceOnce(from, to, label) {
  if (text.includes(to)) return;
  if (!text.includes(from)) throw new Error('M202 weekly-cell anchor missing: ' + label);
  text = text.replace(from, to);
}

replaceOnce(
`        if (!e) return null;
        if (change.remove) {`,
`        if (!e) return null;
        // A locked roster can already be at the one-base-set floor while the session-level weekly
        // allocator rounds that slot up to two sets. In that case the safe repair surface is the
        // exact generated week cell, not a nonexistent zero-set base prescription.
        if (Number.isInteger(change.week) && change.week > 0) {
            const key = e.shellKey, cells = next.nextWeekPrescriptions?.[key], prior = cells?.[change.week];
            if (!prior || !Number.isFinite(prior.sets) || prior.sets + change.delta < 1) return null;
            changed.add(key);
            continue;
        }
        if (change.remove) {`,
'weekly-only change branch');

replaceOnce(
`            next.nextWeekPrescriptions[key] = cells;
        }
    }`,
`            for (const change of changes.filter(change => Number.isInteger(change.week) && change.week > 0 && \`${'${change.dayId}:${change.slot}'}\` === key)) {
                const prior = cells[change.week];
                if (!prior || !Number.isFinite(prior.sets) || prior.sets + change.delta < 1) return null;
                cells[change.week] = { ...prior, sets: prior.sets + change.delta };
            }
            next.nextWeekPrescriptions[key] = cells;
        }
    }`,
'apply exact week cell delta');

const loopAnchor = `            for (const r of recipients) {
                const floor = preserveRoster ? 1 : 2;`;
const loopReplacement = `            for (const r of recipients) {
                // At cycle creation there are no user-edited weekly cells to preserve. If a locked
                // isolation is already at one base set but this specific week was rounded up, trim
                // that generated cell directly. The public-region transaction still checks every
                // other region and session clock before accepting it.
                if (preserveRoster && issue.status === 'over' && r.e.role === 'hypertrophy_isolation' && r.e.sets <= 1) {
                    const weekSession = audit.weeks[issue.week - 1]?.sessions.find(session => session.shellDayId === r.dayId);
                    const weekExercise = weekSession?.exercises.find(exercise => exercise.shellKey === r.e.shellKey);
                    if (weekExercise?.sets > 1)
                        consider([{ dayId: r.dayId, slot: r.slot, delta: -1, week: issue.week }]);
                }
                const floor = preserveRoster ? 1 : 2;`;
replaceOnce(loopAnchor, loopReplacement, 'week-cell candidate');

fs.writeFileSync(path, text);
console.log('M202 weekly-cell repair applied for locked one-set isolation floors.');
