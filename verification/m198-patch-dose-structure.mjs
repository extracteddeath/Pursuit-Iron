import fs from 'node:fs';

const path = 'modules/next-engine/dose-reconciliation.js';
let text = fs.readFileSync(path, 'utf8');
const before = `                const proposed = cloneSessions(sessions);\n                if (removeExercise)\n                    proposed[sessionIndex].exercises.splice(exerciseIndex, 1);\n                else\n                    proposed[sessionIndex].exercises[exerciseIndex].sets -= 1;\n                const after = doseSnapshot(proposed, exerciseMap, prescriptions);`;
const after = `                const proposed = cloneSessions(sessions);\n                if (removeExercise)\n                    proposed[sessionIndex].exercises.splice(exerciseIndex, 1);\n                else\n                    proposed[sessionIndex].exercises[exerciseIndex].sets -= 1;\n\n                // Dose reconciliation is subtractive, but it may not invalidate the named session\n                // contract that generation already satisfied. In particular, removing a two-set row/\n                // pulldown from a Full Body day can leave weekly back dose acceptable while turning the\n                // actual session into lower + push only. Preserve push + pull + lower identity before\n                // considering the candidate's volume benefit.\n                if (!sourceStructurePreserved(session.intent, proposed[sessionIndex].exercises, exerciseMap))\n                    return;\n\n                const after = doseSnapshot(proposed, exerciseMap, prescriptions);`;
const count = text.split(before).length - 1;
if (count !== 1) throw new Error(`Expected one dose-reconciliation candidate block, found ${count}`);
text = text.replace(before, after);
fs.writeFileSync(path, text);
console.log('M198 structural-preservation guard applied to dose reconciliation.');
