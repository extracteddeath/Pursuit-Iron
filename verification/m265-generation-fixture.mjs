import { EXERCISES, EX_BY_ID, getNextShellCell, snapshotNextShellPrescription } from '../modules/engine-api.js';

export const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
export const config = { name: 'Worker integration', unit: 'lb', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4,
    session: 's90', weeks: 6, progression: 'auto', deload: false, equipment, focus: {}, reduce: [], barbellCap: 3, noBodyweight: false, noSupersets: false };
export const generationInput = () => ({ config, banned: [], legacyExercises: EXERCISES, seed: 265265 });
export const ids = prefix => { let n = 0; return () => ++n === 1 ? prefix : `${prefix}-${n}`; };

export function completedHistory(program) {
    const history = [], start = Date.now() - 50 * 86400000;
    for (let week = 1; week <= program.config.weeks; week++) for (const day of program.days) {
        const perf = Object.fromEntries(day.exercises.map((id, slot) => {
            const cell = getNextShellCell(program, day, slot, week);
            const reps = Math.max(...String(cell.reps).match(/\d+/g).map(Number));
            const rir = Math.min(10, Math.min(...String(cell.rir).match(/\d+/g).map(Number)) + 1);
            return [id, { weight: 100 + week * 5, reps, prescription: snapshotNextShellPrescription(program, day, slot, EX_BY_ID[id], week),
                sets: Array.from({ length: cell.sets }, () => ({ w: 100 + week * 5, r: reps, rir, rirReported: true, done: true })) }];
        }));
        history.push({ id: `completed-${program.id}-${history.length}`, programId: program.id, dayId: day.id, weekIndex: week,
            date: start + history.length * 86400000, updatedAt: start + history.length * 86400000, unit: 'lb', perf });
    }
    return history;
}
