// One owner for a session's DISPLAYED length. The realizer budgets a session with its own internal
// clock (estimateSessionMinutes), while the app shows the shell's clock, which also charges changing
// stations, loading and warm-up ramps. A plan that fit the first could show over the lifter's chosen
// limit on the second. The shell registers its clock here (same pattern as shell-equipment.js, so this
// module graph stays acyclic) and generation checks the finished plan against it.
let clock = null;

/** The session lengths a lifter can choose: the limits the displayed clock is held to. */
export const SESSION_BANDS = {
    s20: { minMinutes: 0, maxMinutes: 20 }, s40: { minMinutes: 20, maxMinutes: 40 }, s60: { minMinutes: 40, maxMinutes: 60 },
    s90: { minMinutes: 60, maxMinutes: 90 }, s120: { minMinutes: 90, maxMinutes: 120 }, s120p: { minMinutes: 120, maxMinutes: 150 }
};
export const sessionBandFor = (config) => SESSION_BANDS[String(config?.session || 's60')] ?? SESSION_BANDS.s60;

/** One day's length on the displayed clock, in the whole minutes the app shows. Null without a shell clock. */
export function shellSessionMinutes(program, day, week) {
    if (!clock) return null;
    const minutes = Number(clock(program, day, week));
    return Number.isFinite(minutes) ? Math.round(minutes) : null;
}

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
