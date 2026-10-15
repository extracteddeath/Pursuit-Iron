// Session choices are independent from program edits until the completed workout is saved.
export function withProgressionChoices(program, choices = {}) {
    const progStyle = { ...program.progStyle }, progStyleLifter = { ...program.progStyleLifter };
    for (const [id, style] of Object.entries(choices)) {
        if (!['auto','double','dynamic','ladder','linear','wave','e1rm'].includes(style)) continue;
        if (style === 'auto') { delete progStyle[id]; delete progStyleLifter[id]; }
        else { progStyle[id] = style; progStyleLifter[id] = true; }
    }
    return { ...program, progStyle, progStyleLifter };
}
export function repeatedNote(text, repeat) {
    return text && (repeat === 'once' || repeat === 'always')
        ? { text, repeat: repeat === 'once' ? 'none' : 'always' }
        : { text: '', repeat: 'none' };
}
export function refreshProgressionSets(current, prescribed, owns) {
    let workIndex = 0;
    const work = prescribed.filter(s => !s.warm && !s.sub);
    return current.map(set => {
        if (set.warm || set.sub) return set;
        const fresh = work[workIndex++];
        if (!fresh || set.done || owns(set) || set.added || set.typeOverride) return set;
        return { ...set, weight: fresh.weight, reps: fresh.reps, target: fresh.target,
            hint: fresh.hint, suggested: fresh.suggested, autoTuned: false };
    });
}
export function exerciseSessionTags(ex, row, day, cell) {
    const role = row.added ? 'Added lift' : row.slot === day.primaryIndex || cell?.role === 'primary'
        ? 'Key lift' : ex.type === 'compound' ? 'Supporting lift' : 'Accessory lift';
    return [role, ex.type === 'compound' ? 'Compound' : 'Isolation',
        ...(row.injuryAdjusted ? ['Injury adjusted'] : [])];
}
