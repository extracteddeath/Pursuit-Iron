export const summaryProgram = {
    id: 'm324-custom', name: 'Alternating summary', custom: true, engineV: 70, weeks: 4,
    config: { unit: 'lb', goal: 'both', experience: 'intermediate', progression: 'manual', weeks: 4, days: 1,
        focus: {}, deload: false },
    days: [{ id: 'm324-day', label: 'Upper', primaryIndex: 0, exercises: ['bb-bench', 'lat-raise'] }],
    pairs: { 'm324-day:0': 'db-bench', 'm324-day:1': 'back-squat' },
    overrides: { 'm324-day:0': { sets: 3, reps: '5-8', rir: '2', rest: 120 },
        'm324-day:1': { sets: 2, reps: '10-15', rir: '2', rest: 90 } }
};
