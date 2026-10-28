// Compact labels belong only to the workout's horizontal exercise rail.
// Never change an exercise's canonical name, saved history or accessible label.
const NAV_WORDS = Object.freeze({
    barbell: 'BB',
    dumbbell: 'DB',
    dumbbells: 'DBs',
    extension: 'Ext.',
    extensions: 'Ext.',
    machine: 'Mach.',
    incline: 'Incl.',
    decline: 'Decl.',
    overhead: 'OH',
    alternating: 'Alt.',
    supported: 'Supp.',
    reverse: 'Rev.'
});

/** Abbreviate whole exercise-name words, never substrings or the full identity. */
export function workoutNavLabel(name) {
    if (typeof name !== 'string') return 'Exercise';
    const readable = name.trim().replace(/\s+/g, ' ');
    if (!readable) return 'Exercise';
    return readable.replace(/\b(barbell|dumbbells?|extensions?|machine|incline|decline|overhead|alternating|supported|reverse)\b/gi,
        word => NAV_WORDS[word.toLowerCase()] ?? word);
}
