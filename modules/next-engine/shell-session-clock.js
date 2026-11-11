// One owner for a session's DISPLAYED length. The realizer budgets a session with its own internal
// clock (estimateSessionMinutes), while the app shows the shell's clock, which also charges changing
// stations, loading and warm-up ramps. A plan that fit the first could show over the lifter's chosen
// limit on the second. The shell registers its clock here (same pattern as shell-equipment.js, so this
// module graph stays acyclic) and generation checks the finished plan against it.
let clock = null;

export function setShellSessionClock(fn) { clock = typeof fn === 'function' ? fn : null; }

/** Minutes each day runs past `maxMinutes` on the displayed clock, worst work week of the block.
 *  Positive = over. Null when no shell clock is registered (engine used standalone). */
export function shellSessionOvershoot(program, maxMinutes) {
    if (!clock || !Number.isFinite(maxMinutes) || !Array.isArray(program?.days)) return null;
    const weeks = Math.max(1, Math.round(Number(program.weeks) || 1));
    return program.days.map(day => {
        let worst = -Infinity;
        for (let week = 1; week <= weeks; week++) {
            const minutes = Number(clock(program, day, week));
            // The app shows whole minutes, so 20.4 reads "20 min" and is not over a 20-minute limit.
            if (Number.isFinite(minutes)) worst = Math.max(worst, Math.round(minutes) - maxMinutes);
        }
        return Number.isFinite(worst) ? worst : 0;
    });
}
