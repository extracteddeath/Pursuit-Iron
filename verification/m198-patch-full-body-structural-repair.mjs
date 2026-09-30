import fs from 'node:fs';

const path = 'modules/next-engine/realizer.js';
let source = fs.readFileSync(path, 'utf8');

const before = `                        const structuralSets = session.maxMinutes <= 35 ? 1 : 2;\n                        const added = makePlanned(def, 'hypertrophy_compound', structuralSets, policy, request.athlete.experience);\n                        let proposal = [...session.exercises, added];\n                        const fitted = fitProposalBySafeDosage(session, proposal, proposal.length - 1);\n                        if (!fitted)\n                            continue;\n                        proposal = fitted;\n                        const minutes = estimateMinutes(proposal);`;

const after = `                        // Full-body identity is structural, not a two-set volume mandate. Try the normal\n                        // two-set repair first, but if it cannot fit without violating protected weekly\n                        // minimums, retain a one-set push/pull/lower exposure rather than returning a\n                        // label-only Full Body session. The normal weekly dose ledger remains authoritative.\n                        const structuralSetOptions = session.maxMinutes <= 35 ? [1] : [2, 1];\n                        let proposal = null;\n                        for (const structuralSets of structuralSetOptions) {\n                            const added = makePlanned(def, 'hypertrophy_compound', structuralSets, policy, request.athlete.experience);\n                            const candidate = [...session.exercises, added];\n                            const fitted = fitProposalBySafeDosage(session, candidate, candidate.length - 1);\n                            if (fitted) {\n                                proposal = fitted;\n                                break;\n                            }\n                        }\n                        if (!proposal)\n                            continue;\n                        const minutes = estimateMinutes(proposal);`;

const count = source.split(before).length - 1;
if (count !== 1) throw new Error(`Expected one structural repair block, found ${count}`);
source = source.replace(before, after);
fs.writeFileSync(path, source);
console.log('M198 full-body structural repair fallback applied.');
