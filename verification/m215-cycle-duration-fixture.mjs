export const currentProgram = {
    id: 'seku-duration', name: 'SekuFit', custom: true, weeks: 10,
    config: { name: 'SekuFit', goal: 'both', experience: 'intermediate', progression: 'auto',
        split: 'custom', weeks: 10, days: 1, session: 's90', deload: true },
    days: [{ id: 'upper-duration', label: 'Upper', primaryIndex: 0,
        exercises: ['bb-bench', 'inc-curl', 'lat-raise'] }], overrides: {}
};
export const generatedCycle = {
    cycle: { id: 'duration-cycle', name: 'Powerbuilding', engineSource: 'pursuit-next',
        activeBlock: 0, blockIds: ['entry-duration', 'strength-duration', 'peak-duration'],
        blockMeta: [
            { id: 'entry-duration', label: 'Hypertrophy', goal: 'hypertrophy', phase: 'hypertrophy_accumulation', weeks: 6, plannedIndex: 0, note: 'Hypertrophy Accumulation · 6 weeks' },
            { id: 'strength-duration', label: 'Strength', goal: 'strength', phase: 'strength_accumulation', weeks: 5, plannedIndex: 1, note: 'Strength Accumulation · 5 weeks' },
            { id: 'peak-duration', label: 'Peak', goal: 'strength', phase: 'peak', weeks: 3, plannedIndex: 2, note: 'Peak · 3 weeks' }
        ], nextEngineCycle: { plannedBlocks: [{ weeks: 6 }, { weeks: 5 }, { weeks: 3 }] } },
    blocks: [6, 5, 3].map((weeks, index) => ({
        id: ['entry-duration', 'strength-duration', 'peak-duration'][index],
        name: ['Hypertrophy', 'Strength', 'Peak'][index], weeks, engineSource: 'pursuit-next',
        config: { goal: index ? 'strength' : 'hypertrophy', weeks, deload: false, progression: 'auto', days: 1 },
        days: [{ id: `duration-day-${index}`, label: 'Upper', primaryIndex: 0, exercises: ['bb-bench'] }], overrides: {}
    }))
};
// A previously saved conversion has the correct ten-week program but old six-week metadata.
export const savedPrograms = [{ ...currentProgram, cycleId: 'duration-cycle', cycleIndex: 0 },
    ...generatedCycle.blocks.slice(1).map((p, i) => ({ ...p, cycleId: 'duration-cycle', cycleIndex: i + 1 }))];
export const staleCycle = { ...generatedCycle.cycle,
    blockIds: savedPrograms.map(p => p.id),
    blockMeta: generatedCycle.cycle.blockMeta.map((m, i) => ({ ...m, id: savedPrograms[i].id })) };
