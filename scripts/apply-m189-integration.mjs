import fs from 'node:fs';

function replaceOnce(path, before, after) {
  const source = fs.readFileSync(path, 'utf8');
  if (source.includes(after)) return false;
  if (!source.includes(before)) throw new Error(`M189 integration marker missing in ${path}`);
  fs.writeFileSync(path, source.replace(before, after));
  return true;
}

const changed = [];
const mark = (path, didChange) => { if (didChange) changed.push(path); };

mark('modules/next-engine/generate.js', replaceOnce(
  'modules/next-engine/generate.js',
  `    const policy = phasePolicyFor(phase);`,
  `    const policy = {\n        ...phasePolicyFor(phase),\n        // M189: block duration is part of progression-method selection. A four-week\n        // intensification block should not start a wave that needs five+ weeks to justify itself.\n        blockWeeks: Number(options?.blockWeeks) > 0 ? Math.max(1, Math.round(Number(options.blockWeeks))) : undefined,\n        requestedProgressionStyle: options?.progressionStyle ?? requestInput?.preferences?.progressionStyle\n    };`
));

mark('modules/next-engine/realizer.js', replaceOnce(
  'modules/next-engine/realizer.js',
  `export function progressionStyleForExercise(ex, role, policy, experience = 'intermediate') {\n    return resolveProgressionStyle(ex, role, { phase: policy.phase, experience });\n}`,
  `export function progressionStyleForExercise(ex, role, policy, experience = 'intermediate') {\n    return resolveProgressionStyle(ex, role, {\n        phase: policy.phase,\n        experience,\n        blockWeeks: policy.blockWeeks,\n        requestedStyle: policy.requestedProgressionStyle\n    });\n}`
));

mark('modules/next-engine/app-shell-adapter.js', replaceOnce(
  'modules/next-engine/app-shell-adapter.js',
  `    const result = generateProgram(request);`,
  `    const result = generateProgram(request, {\n        // The shell's configured work weeks are the block length the athlete will actually run.\n        // Feed that into Auto instead of letting progression selection assume a generic six-week block.\n        blockWeeks: Math.max(1, Math.round(Number(options.config?.weeks) || 4)),\n        progressionStyle: options.config?.progressionStyle\n    });`
));

const evidenceHelper = `export function deriveProgressionSelectionEvidence(workouts = []) {\n    const rows = new Map();\n    const excluded = new Set(['unobserved', 'non_comparable', 'context_limited', 'interrupted', 'incomplete']);\n    const ensure = (id) => {\n        const existing = rows.get(id);\n        if (existing) return existing;\n        const row = { comparableExposures: 0, styleExposures: 0, failureCount: 0, stallCount: 0, loadingBlockedCount: 0, rirReportedSets: 0, rirEligibleSets: 0, e1rmSamples: 0 };\n        rows.set(id, row);\n        return row;\n    };\n    for (const workout of workouts ?? []) {\n        for (const decision of workout?.progression ?? []) {\n            const id = decision?.exerciseId;\n            if (!id) continue;\n            const row = ensure(id);\n            const sets = (workout?.performedSets ?? []).filter(set => set.exerciseId === id);\n            const comparable = !excluded.has(decision.outcome);\n            if (!comparable) continue;\n            row.comparableExposures += 1;\n            // Saved shell history does not record a separate style-version stamp. Within a block the style\n            // is stable, so comparable exposures are also the conservative evidence count for that style.\n            row.styleExposures += 1;\n            if (decision.outcome === 'failure') {\n                row.failureCount += 1;\n                row.stallCount += 1;\n            }\n            if (decision.outcome === 'success_blocked') row.loadingBlockedCount += 1;\n            if (Number.isFinite(Number(decision.estimated1RM))) row.e1rmSamples += 1;\n            for (const set of sets) {\n                row.rirEligibleSets += 1;\n                if (set.rir !== null && Number.isFinite(Number(set.rir))) row.rirReportedSets += 1;\n            }\n        }\n    }\n    return Object.fromEntries([...rows.entries()].map(([id, row]) => [id, {\n        comparableExposures: row.comparableExposures,\n        styleExposures: row.styleExposures,\n        failureCount: row.failureCount,\n        stallCount: row.stallCount,\n        loadingBlockedCount: row.loadingBlockedCount,\n        rirCoverage: row.rirEligibleSets ? row.rirReportedSets / row.rirEligibleSets : 0,\n        e1rmSamples: row.e1rmSamples\n    }]));\n}\n\n`;

mark('modules/next-engine/workout-history-adapter.js', replaceOnce(
  'modules/next-engine/workout-history-adapter.js',
  `export function analyzeShellHistoryForNextEngine(program, history, legacyExercises) {`,
  `${evidenceHelper}export function analyzeShellHistoryForNextEngine(program, history, legacyExercises) {`
));

mark('modules/next-engine/workout-history-adapter.js', replaceOnce(
  'modules/next-engine/workout-history-adapter.js',
  `    const evidence = deriveLongitudinalExerciseEvidence(diagnoses, [...latest.values()], sourceExercises);\n    const successful = evidence.successfulExerciseIds;`,
  `    const evidence = deriveLongitudinalExerciseEvidence(diagnoses, [...latest.values()], sourceExercises);\n    // M189 keeps progression-method evidence separate from volume/response diagnosis. Method changes\n    // need repeated comparable exposures, actual failure counts, effort coverage and loading constraints.\n    const progressionEvidenceByExercise = deriveProgressionSelectionEvidence(workouts);\n    const successful = evidence.successfulExerciseIds;`
));

mark('modules/next-engine/workout-history-adapter.js', replaceOnce(
  'modules/next-engine/workout-history-adapter.js',
  `        techniqueLimitedExerciseIds: evidence.techniqueLimitedExerciseIds, fatigueLimitedExerciseIds: evidence.fatigueLimitedExerciseIds, diagnoses,`,
  `        techniqueLimitedExerciseIds: evidence.techniqueLimitedExerciseIds, fatigueLimitedExerciseIds: evidence.fatigueLimitedExerciseIds,\n        progressionEvidenceByExercise, diagnoses,`
));

mark('modules/next-engine/workout-history-adapter.js', replaceOnce(
  'modules/next-engine/workout-history-adapter.js',
  `    const normalized = normalizeRequest(adaptedRequest);\n    const transitioned = transitionProgramPhase(snap.program, normalized, phase, {\n        successfulExerciseIds: analysis.successfulExerciseIds,\n        protectedExerciseIds: analysis.protectedExerciseIds,\n        replaceExerciseIds: analysis.replaceExerciseIds\n    });`,
  `    const normalized = normalizeRequest(adaptedRequest);\n    const nextBlockWeeks = Math.max(1, Math.round(Number(\n        options.nextBlockWeeks\n        ?? current?.nextEngine?.nextBlock?.weeks\n        ?? current?.config?.weeks\n        ?? current?.nextEngine?.cycleTemplate?.weeks\n        ?? 4\n    ) || 4));\n    const transitioned = transitionProgramPhase(snap.program, normalized, phase, {\n        successfulExerciseIds: analysis.successfulExerciseIds,\n        protectedExerciseIds: analysis.protectedExerciseIds,\n        replaceExerciseIds: analysis.replaceExerciseIds,\n        techniqueLimitedExerciseIds: analysis.techniqueLimitedExerciseIds,\n        fatigueLimitedExerciseIds: analysis.fatigueLimitedExerciseIds,\n        progressionEvidenceByExercise: analysis.progressionEvidenceByExercise,\n        nextBlockWeeks\n    });`
));

mark('modules/next-engine/workout-history-adapter.js', replaceOnce(
  'modules/next-engine/workout-history-adapter.js',
  `    const legacy = nextProgramToShellProgram(transitioned.program, current.config, options.legacyExercises, options.makeId);`,
  `    const nextConfig = { ...current.config, weeks: nextBlockWeeks };\n    const legacy = nextProgramToShellProgram(transitioned.program, nextConfig, options.legacyExercises, options.makeId);`
));

console.log(changed.length ? `M189 integration applied: ${changed.join(', ')}` : 'M189 integration already applied.');
