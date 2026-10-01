const positiveWeeks = value => {
    const n = Number(value);
    return Number.isInteger(n) && n > 0 ? n : null;
};

// The saved program owns its working-week length. Cycle/template metadata is a display fallback.
export function programWorkingWeeks(program, fallback = 6) {
    return positiveWeeks(program?.config?.weeks) ?? positiveWeeks(program?.weeks) ?? fallback;
}

// Resolve by program id, so previously saved conversions and later duration edits read correctly
// without rewriting the user's programs or history. Missing blocks retain their metadata.
export function cycleBlockMetadata(cycle, programs = []) {
    const byId = new Map(programs.filter(Boolean).map(p => [p.id, p]));
    return (cycle?.blockMeta || []).map((meta, i) => {
        const id = meta.id ?? cycle?.blockIds?.[i];
        const program = byId.get(id);
        const weeks = programWorkingWeeks(program, positiveWeeks(meta.weeks));
        const deload = program ? !!program.config?.deload : !!meta.deload;
        // Only update the duration phrase written by the cycle builder; preserve authored notes.
        const note = weeks && typeof meta.note === 'string'
            ? meta.note.replace(/( · )\d+ weeks?(?= ·|$)/, `$1${weeks} weeks`) : meta.note;
        return { ...meta, id, weeks, deload, note };
    });
}
