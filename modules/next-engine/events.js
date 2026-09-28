import { createExerciseMap } from './exercise-db.js';
export function createTrainingSetEvents(sessions, customExercises = []) {
    const exerciseMap = createExerciseMap(customExercises);
    const events = [];
    for (const session of sessions) {
        for (const exercise of session.exercises) {
            const def = exerciseMap.get(exercise.exerciseId);
            if (!def)
                continue;
            for (let setIndex = 0; setIndex < exercise.sets; setIndex++) {
                const muscles = {};
                for (const [muscle, contribution] of Object.entries(def.muscles)) {
                    muscles[muscle] = contribution.credit;
                }
                events.push({
                    id: `${session.id}-${exercise.exerciseId}-${setIndex + 1}`,
                    sessionId: session.id,
                    exerciseId: exercise.exerciseId,
                    role: exercise.role,
                    setIndex,
                    muscles,
                    fatigue: def.fatigue,
                    reps: exercise.prescription.reps,
                    rir: exercise.prescription.rir,
                    restSeconds: exercise.prescription.restSeconds,
                    advancedTechnique: setIndex === exercise.sets - 1 ? exercise.advancedTechnique?.type : undefined
                });
            }
        }
    }
    return events;
}
