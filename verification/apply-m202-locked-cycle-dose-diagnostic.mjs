import fs from 'node:fs';

const path = 'modules/next-engine/cycle-runtime-adapter.js';
let text = fs.readFileSync(path, 'utf8');
const from = `            if (afterOver.length)
                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_VOLUME_REJECTED', \`The locked exercise skeleton could not safely fit the \${phaseLabel(spec.phase).toLowerCase()} accessory-volume ceiling.\`, afterOver);`;
const to = `            if (afterOver.length) {
                const diagnostic = volumeRepair.after.weeks.map((week, wi) => ({
                    week: wi + 1,
                    regions: week.regions,
                    sessions: week.sessions.map(session => ({ day: session.day, exercises: session.exercises.map(exercise => ({
                        name: exercise.name, exerciseId: exercise.exerciseId, role: exercise.role, sets: exercise.sets, shellKey: exercise.shellKey
                    })) }))
                }));
                console.error('M202_LOCKED_VOLUME_DIAGNOSTIC ' + JSON.stringify({ phase: spec.phase, before: beforeOver, after: afterOver, weeks: diagnostic }));
                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_VOLUME_REJECTED', \`The locked exercise skeleton could not safely fit the \${phaseLabel(spec.phase).toLowerCase()} accessory-volume ceiling.\`, afterOver);
            }`;
if (text.includes(to)) {
  console.log('M202 diagnostic already applied.');
  process.exit(0);
}
if (!text.includes(from)) throw new Error('M202 diagnostic anchor not found');
fs.writeFileSync(path, text.replace(from, to));
console.log('M202 diagnostic instrumentation applied.');
