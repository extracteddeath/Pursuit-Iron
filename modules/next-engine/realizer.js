// M230 canonical realizer stage. Stage context is local to one realization.
import { armCoverageBias, armCoverageUseKeys, evaluateArmCoverage } from './arm-coverage.js';
import { ALL_MUSCLES } from './config.js';
import { createExerciseCatalog, createExerciseMap } from './exercise-db.js';
import { avoidableExerciseOverlap, exerciseEconomyCluster } from './exercise-economy.js';
import { functionalCoverageUseKeys } from './functional-coverage.js';
import { phasePolicyFor } from './phase-policy.js';
import { createMusclePrescriptions, productiveTimeBandDoseTarget } from './prescription.js';
import { PUBLIC_MEV_REGIONS, PUBLIC_REGION_MUSCLE, directlyTargetsPublicRegion, publicMevBaseTarget, publicMevContractApplies, publicMevLedger, publicMevRequired, publicRegionContribution } from './public-mev.js';
import { repairMajorArmCoverage as run_repairMajorArmCoverage } from './realizer-arm-repair.js';
import { diversifySparseSessions as run_diversifySparseSessions, expandProductiveCapacity as run_expandProductiveCapacity, fillSessionCapacity as run_fillSessionCapacity, redistributeFullBodyAccessories as run_redistributeFullBodyAccessories, scheduleAdvancedTechniques as run_scheduleAdvancedTechniques, usefulTarget as run_usefulTarget } from './realizer-capacity-repair.js';
import { currentDirect as run_currentDirect, currentFractional as run_currentFractional, directTotalsWithSessionProposal as run_directTotalsWithSessionProposal, totalsWithSessionProposal as run_totalsWithSessionProposal } from './realizer-dose-ledger.js';
import { enforceFinalTimeLimits as run_enforceFinalTimeLimits, fitPreferredDosage as run_fitPreferredDosage, fitProposalBySafeDosage as run_fitProposalBySafeDosage, reconcileAllocationTargets as run_reconcileAllocationTargets, repairMinimumFloors as run_repairMinimumFloors, satisfyDedicatedIntentFloors as run_satisfyDedicatedIntentFloors, satisfyDirectFloors as run_satisfyDirectFloors, satisfySmallMuscleFloors as run_satisfySmallMuscleFloors } from './realizer-dose-repair.js';
import { consolidateCompoundFragments as run_consolidateCompoundFragments, consolidateOneSetDuplicates as run_consolidateOneSetDuplicates, consolidateRedundantAccessoryFamilies as run_consolidateRedundantAccessoryFamilies, pruneCompactOneSetFragments as run_pruneCompactOneSetFragments, pruneFinalCompactFragments as run_pruneFinalCompactFragments, rebalanceOneSetFragmentsBySwap as run_rebalanceOneSetFragmentsBySwap } from './realizer-fragment-repair.js';
import { realizeDirectIsolations as run_realizeDirectIsolations, realizeMajorMuscles as run_realizeMajorMuscles } from './realizer-hypertrophy.js';
import { addToLedger, desiredDirect, integerSetPlan } from './realizer-math.js';
import { assignAccessorySupersets } from './realizer-pairing.js';
import { makePlanned } from './realizer-prescriptions.js';
import { reconcilePublicDose as run_reconcilePublicDose } from './realizer-public-dose-repair.js';
import { LARGE, SMALL, equipmentEligible, floorSpilloverAllowed, intentAcceptsMuscle, maxBarbells, muscleCandidate, muscleScore, primaryMuscle, strengthCandidate } from './realizer-ranking.js';
import { sequenceSessionExercises } from './realizer-sequence.js';
import { add as run_add, buildRealizedSessions as run_buildRealizedSessions, trackExerciseUse as run_trackExerciseUse } from './realizer-session-builder.js';
import { realizePinnedStrength as run_realizePinnedStrength, strengthSetsForAllocation } from './realizer-strength.js';
import { finalStructureValid as run_finalStructureValid, rebalanceRepeatedIntents as run_rebalanceRepeatedIntents, redistributeUpperAccessories as run_redistributeUpperAccessories, repairFullBodyStructure as run_repairFullBodyStructure, restoreFinalFloors as run_restoreFinalFloors } from './realizer-structure-repair.js';
import { estimateMinutes, fitCompactRestToSession } from './realizer-time-budget.js';
import { chooseAdvancedTechnique } from './techniques.js';
import { INTENT_MUSCLES } from './topology.js';

export function realizeSessions(plans, request, targetDose = {}, directTargetDose = {}, phase, options = {}) {
    // Invocation-local getters preserve closure timing; stages never own durable/global state.
    const context = {
        get accumulationFloorExpansion() { return accumulationFloorExpansion; },
        get add() { return add; },
        get candidates() { return candidates; },
        get capacityPrescriptions() { return capacityPrescriptions; },
        get consolidateRedundantAccessoryFamilies() { return consolidateRedundantAccessoryFamilies; },
        get currentDirect() { return currentDirect; },
        get currentFractional() { return currentFractional; },
        get dedicatedIntentMuscles() { return dedicatedIntentMuscles; },
        get directTargetDose() { return directTargetDose; },
        get directTotalsWithSessionProposal() { return directTotalsWithSessionProposal; },
        get exerciseCatalog() { return exerciseCatalog; },
        get exerciseMap() { return exerciseMap; },
        get finalStructureValid() { return finalStructureValid; },
        get fitProposalBySafeDosage() { return fitProposalBySafeDosage; },
        get latePhaseExpansion() { return latePhaseExpansion; },
        get ledger() { return ledger; },
        get lowerMovementFamilies() { return lowerMovementFamilies; },
        get phase() { return phase; },
        get pinnedStrengthAnchors() { return pinnedStrengthAnchors; },
        get plans() { return plans; },
        get policy() { return policy; },
        get prescriptionMap() { return prescriptionMap; },
        get priorityRank() { return priorityRank; },
        get priorityWeight() { return priorityWeight; },
        get pruneCompactOneSetFragments() { return pruneCompactOneSetFragments; },
        get realized() { return realized; },
        get repairMinimumFloors() { return repairMinimumFloors; },
        get request() { return request; },
        get sessions() { return sessions; },
        get smallIntentPreference() { return smallIntentPreference; },
        get targetDose() { return targetDose; },
        get techniquesUsed() { return techniquesUsed; }, set techniquesUsed(value) { techniquesUsed = value; },
        get totalsWithSessionProposal() { return totalsWithSessionProposal; },
        get trackExerciseUse() { return trackExerciseUse; },
        get upperAccessoryFamilies() { return upperAccessoryFamilies; },
        get upperAccessoryIntents() { return upperAccessoryIntents; },
        get usefulTarget() { return usefulTarget; },
        get weeklyMovementUse() { return weeklyMovementUse; }
    };

    const policy = {
        ...phasePolicyFor(phase),
        blockWeeks: options.blockWeeks,
        requestedProgressionStyle: options.requestedProgressionStyle
    };
    const exerciseCatalog = createExerciseCatalog(request.customExercises);
    const exerciseMap = createExerciseMap(request.customExercises);
    const sessions = plans.map(plan => ({ plan, defs: [], exercises: [], importance: [] }));
    const ledger = { fractional: {}, direct: {} };
    const pinnedStrengthAnchors = options.strengthAnchors ?? {};
    const weeklyMovementUse = new Map();
    const trackExerciseUse = (...args) => run_trackExerciseUse(context, ...args);
    const add = (...args) => run_add(context, ...args);
    // 1) Required/specific lift work first.
    run_realizePinnedStrength(context);
    // 2) Major-muscle work. Convert fractional topology allocations into an integer weekly set plan
    // before selecting exercises so distributing 4 target sets across 3 sessions cannot silently become 3.
    run_realizeMajorMuscles(context);
    // 3) Direct isolation after compound carryover is known.
    const smallIntentPreference = {
        biceps: ['arms', 'shoulders_arms', 'pull', 'upper', 'strength_full', 'full'], triceps: ['arms', 'shoulders_arms', 'push', 'bench_focus', 'press_focus', 'upper', 'strength_full', 'full'], rear_delts: ['pull', 'upper', 'bench_focus', 'press_focus', 'strength_full', 'full'],
        side_delts: ['shoulders', 'shoulders_arms', 'push', 'press_focus', 'upper', 'strength_full', 'full'], calves: ['legs', 'lower', 'squat_focus', 'deadlift_focus', 'strength_full', 'limbs', 'full'], core: ['legs', 'lower', 'squat_focus', 'deadlift_focus', 'strength_full', 'limbs', 'full'], front_delts: ['push', 'press_focus', 'bench_focus', 'upper', 'strength_full', 'full']
    };
    run_realizeDirectIsolations(context);
    const realized = run_buildRealizedSessions(context);
    // A full-body capacity contract should not create two bloated days and two sparse ones. Move whole
    // accessory prescriptions between full-body sessions first; weekly volume and progression stay intact.
    run_redistributeFullBodyAccessories(context);
    // If an explicit time card still leaves a session sparse, split an existing multi-set large-muscle
    // prescription across a complementary movement. This changes exercise variety, not weekly set count.
    run_diversifySparseSessions(context);
    // Last resort for an explicit time-tier contract: add useful assistance volume rather than returning a
    // technically valid but obviously underbuilt session. Additions are capped by modeled upper regions,
    // prefer muscles still below their preferred region, avoid normal front-delt filler, and must fit time.
    const capacityPrescriptions = createMusclePrescriptions(request, phase);
    const prescriptionMap = new Map(capacityPrescriptions.map(p => [p.muscle, p]));
    const currentFractional = (...args) => run_currentFractional(context, ...args);
    const totalsWithSessionProposal = (...args) => run_totalsWithSessionProposal(context, ...args);
    const directTotalsWithSessionProposal = (...args) => run_directTotalsWithSessionProposal(context, ...args);
    const fitProposalBySafeDosage = (...args) => run_fitProposalBySafeDosage(context, ...args);
    const priorityWeight = { maintenance: .5, normal: 1, high: 1.35, specialization: 1.7, primary: 1.9 };
    run_fillSessionCapacity(context);
    // Priority truth check. A high/specialization target must survive realization and time repair even when
    // its allocation was inserted after normal work. This specifically prevents list-order starvation.
    const currentDirect = (...args) => run_currentDirect(context, ...args);
    run_satisfyDirectFloors(context);
    // Final minimum-dose repair. If the structural phase solver left a modeled core-muscle floor just
    // short while there is still time available, spend that slack on the missing claim rather than returning
    // a knowingly incomplete program. This is intentionally floor-only: it never chases preferred volume.
    run_satisfySmallMuscleFloors(context);
    // Final structural truth check for locked/full-body cycles. Strength-specific pressing can consume the
    // modeled chest target before non-bench days receive any push slot, so repair missing regions directly
    // instead of allowing a label-only "Full Body" session.
    const repairFullBodyStructure = (...args) => run_repairFullBodyStructure(context, ...args);
    repairFullBodyStructure();
    // Structural repair can free time by reducing surplus strength dosage. Re-run the true minimum
    // floors afterwards so rear delts/core/etc. are not permanently skipped merely because the earlier
    // floor pass occurred before that clock space existed. This remains floor-only and never chases
    // preferred volume.
    const repairMinimumFloors = (...args) => run_repairMinimumFloors(context, ...args);
    // Provisional supersets can create legitimate clock room for minimum-dose repairs. They are
    // recomputed later, so this is a planning transform rather than a permanent pairing decision.
    for (let i = 0; i < realized.length; i++)
        realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    repairMinimumFloors();
    // Minimum-dose repairs can change which low-fatigue work exists in each session. Re-run the
    // full-body structural truth pass once more so the final program—not an intermediate state—must
    // satisfy push + pull + lower on every locked Full Body day.
    repairFullBodyStructure();
    for (let i = 0; i < realized.length; i++)
        realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    repairMinimumFloors();
    // Dedicated body-part intents need enough local structure to deserve their label. Strength carryover
    // can otherwise satisfy a weekly ledger before a Chest/Shoulders/Arms day receives any assistance.
    // Add the smallest useful local prescription (normally 2 sets) and keep it under modeled upper dose.
    const dedicatedIntentMuscles = {
        upper: ['chest', 'back', 'side_delts'], lower: ['quads', 'hamstrings', 'glutes'],
        push: ['chest', 'side_delts', 'triceps'], pull: ['back', 'rear_delts', 'biceps'],
        legs: ['quads', 'hamstrings', 'glutes', 'calves'], glute: ['glutes', 'hamstrings', 'quads'],
        chest: ['chest'], back_day: ['back', 'rear_delts'], shoulders: ['side_delts', 'rear_delts'],
        arms: ['biceps', 'triceps'], chest_back: ['chest', 'back'], shoulders_arms: ['side_delts', 'biceps', 'triceps'],
        torso: ['chest', 'back', 'side_delts'], limbs: ['quads', 'hamstrings', 'biceps', 'triceps'],
        core_day: ['core', 'abductors', 'adductors'], strength_full: ['chest', 'back', 'quads', 'hamstrings']
    };
    run_satisfyDedicatedIntentFloors(context);
    // Repeated split intents should distribute accessories rather than leaving one day skeletal while
    // sister sessions carry all optional work. Move whole non-strength prescriptions within the same
    // intent; weekly dose is unchanged, duplicates are prohibited, and the receiving session must fit.
    const rebalanceRepeatedIntents = (...args) => run_rebalanceRepeatedIntents(context, ...args);
    rebalanceRepeatedIntents();
    // Compact-session complexity reconciliation. A 30–45 minute plan should not become a circuit of
    // one-set accessories merely because weekly minima were repaired independently.
    // Remove whole low-priority assistance prescriptions when all modeled floors and the session's
    // structural promise remain intact. Priority work and strength anchors are protected.
    const finalStructureValid = (...args) => run_finalStructureValid(context, ...args);
    // One-set duplicate consolidation. Late floor repairs can leave the same accessory as a one-set
    // fragment on several sister sessions. When one session is fragment-heavy, move an exact duplicate
    // set onto an existing copy elsewhere (up to four sets) instead of paying another setup transition.
    // Weekly dose, exercise identity, and progression policy stay unchanged; the source session must keep
    // its structural promise and the receiving session must remain inside its hard time cap.
    const consolidateOneSetDuplicates = (...args) => run_consolidateOneSetDuplicates(context, ...args);
    consolidateOneSetDuplicates();
    // Compact repeated-intent balancing. A weekly plan can be perfectly valid in total yet leave one
    // 40-minute session with three one-set fragments while a sister session holds a 2+ set movement for
    // the same primary muscle. Swap those prescriptions instead of deleting work or adding time: weekly
    // exercise identity, total sets, muscle ledger, and progression cells stay unchanged, while each
    // session gets a more intentional dose shape. Strength anchors/specialization work are never moved,
    // and heterogeneous day-equipment contracts remain authoritative.
    const rebalanceOneSetFragmentsBySwap = (...args) => run_rebalanceOneSetFragmentsBySwap(context, ...args);
    rebalanceOneSetFragmentsBySwap();
    // Compact-strength polish. A short session with three or four unrelated one-set accessories is
    // usually an accounting artifact, not a prescription a coach would intentionally write. Remove only
    // normal/maintenance one-set assistance that is genuinely surplus: the session must retain its split
    // identity, every modeled fractional/direct floor must be no worse than before, and high/specialized
    // priorities plus all strength-specific work stay protected. This intentionally permits a compact
    // strength session to leave some optional small-muscle work on the table rather than creating a
    // five-exercise "sampler" of single sets.
    const pruneCompactOneSetFragments = (...args) => run_pruneCompactOneSetFragments(context, ...args);
    pruneCompactOneSetFragments();
    // Coach-like accessory consolidation. Use the same semantic economy slot as candidate selection
    // instead of raw movementFamily labels: complementary functions (curl biases, triceps positions,
    // calf knee angles, core patterns, etc.) stay distinct while interchangeable variants share a slot.
    // High-priority/specialization work may deliberately use a second variant when local dose requires
    // it; ordinary work should earn more dose before it pays another setup transition.
    const consolidateRedundantAccessoryFamilies = (...args) => run_consolidateRedundantAccessoryFamilies(context, ...args);
    consolidateRedundantAccessoryFamilies();
    run_fitPreferredDosage(context);
    // Realized spillover sanity repair. Topology can validly move a small arm/delt claim onto a Lower
    // day, but later global-ledger satisfaction may erase a planned hamstring/glute exercise and leave
    // the final session as "Squat + curls." When that happens, move the upper accessory back to the
    // least-loaded upper-compatible session instead of diluting the Lower/Leg identity. Weekly sets and
    // the exact exercise prescription are preserved.
    const upperAccessoryFamilies = new Set(['lateral_raise', 'rear_delt', 'elbow_flexion', 'elbow_extension', 'grip', 'wrist_flexion', 'wrist_extension', 'wrist_deviation']);
    const upperAccessoryIntents = new Set(['upper', 'push', 'pull', 'arms', 'shoulders', 'shoulders_arms', 'chest', 'back_day', 'chest_back', 'torso', 'bench_focus', 'press_focus']);
    const lowerMovementFamilies = new Set(['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion', 'calf', 'hip_abduction', 'hip_adduction', 'dorsiflexion']);
    run_redistributeUpperAccessories(context);
    // Supersets are a time-management transform, never a substitute for strength rest.
    for (let i = 0; i < realized.length; i++)
        realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    // Advanced techniques are scarce transforms, not extra exercises or automatic failure work.
    // Only high-priority hypertrophy work can consume the phase budget, and only when session time still fits.
    let techniquesUsed = 0;
    const priorityRank = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
    const candidates = realized.flatMap((session, sessionIndex) => session.exercises.map((exercise, exerciseIndex) => {
        const def = exerciseMap.get(exercise.exerciseId);
        const primaryMuscle = def ? Object.entries(def.muscles).find(([, c]) => c.role === 'primary')?.[0] : undefined;
        const priority = primaryMuscle ? request.goal.musclePriorities[primaryMuscle] : 'normal';
        return { sessionIndex, exerciseIndex, def, priority };
    })).filter(x => x.def).sort((a, b) => priorityRank[b.priority] - priorityRank[a.priority] || a.sessionIndex - b.sessionIndex || a.exerciseIndex - b.exerciseIndex);
    run_scheduleAdvancedTechniques(context);
    // One-set hypertrophy compounds are usually setup-heavy fragments rather than useful prescriptions.
    // Prefer a second set when the primary muscle is still below its preferred region; otherwise remove
    // the fragment when floors and session identity remain intact. Strength-specific single sets are
    // intentionally exempt because low-volume peak work can be legitimate.
    run_consolidateCompoundFragments(context);
    // Time-bucket floor reconciliation. A bucket such as 40–60 minutes is not merely a 60-minute cap:
    // its lower edge is the amount of training time the athlete said they can reliably devote. M48
    // collapsed that contract to maxMinutes only, so a technically valid 28–35 minute session could
    // escape from the 40–60 bucket with productive volume still available.
    //
    // Move toward 95% of the lower edge (the old engine's practical tolerance), but only with useful
    // work. Accumulation phases may use some of the adaptive region above `preferred` when the athlete
    // explicitly selected a longer time bucket; later phases progressively stop doing that so tapering
    // remains real. Never cross the modeled upper region or the user's upper time edge.
    const averageMinimumMinutes = request.schedule.days.reduce((sum, day) => sum + (day.minMinutes ?? 0), 0) / Math.max(1, request.schedule.days.length);
    const accumulationFloorExpansion = phase === 'hypertrophy_accumulation'
        ? (averageMinimumMinutes >= 120 ? .72 : averageMinimumMinutes >= 90 ? .62 : averageMinimumMinutes >= 60 ? .52 : .45)
        : phase === 'mixed_accumulation'
            ? (averageMinimumMinutes >= 120 ? .54 : averageMinimumMinutes >= 90 ? .47 : averageMinimumMinutes >= 60 ? .40 : .32)
            : 0;
    const latePhaseExpansion = phase === 'strength_accumulation' ? .15 : 0;
    const usefulTarget = (...args) => run_usefulTarget(context, ...args);
    run_expandProductiveCapacity(context);
    // Allocation-target reconciliation. Time/structure repairs may trim a muscle back to its bare
    // minimum even though the allocator deliberately reserved a higher productive target for the chosen
    // time band. Restore that target when there is still compatible session room. This is the missing
    // link between M50's capacity-aware allocator and the final coach-facing program: minimum volume is
    // safety, while targetDose is the productive dose the engine actually decided was worth spending.
    run_reconcileAllocationTargets(context);
    // User-facing MEV reconciliation. The internal allocator uses a compact muscle model, but the
    // shell exposes stricter landmarks (including separate lat/upper-back floors and direct-set
    // side/rear-delt guidance). On genuinely high-capacity accumulation plans, those visible MEVs are
    // part of the program contract: satisfy them before spending additional volume above preferred
    // elsewhere. A small reserve is built into the base program so planned late-block consolidation
    // cannot make an otherwise normal 5+ week plan fall below the displayed MEV.
    run_reconcilePublicDose(context);
    // Late floor/public-volume work can re-create a lopsided repeated-intent week after the earlier
    // balancing pass. Re-run the dose-neutral whole-exercise balancer now that all useful work exists.
    // The balancer is destination-equipment/barbell aware, so heterogeneous M58 day contracts remain
    // authoritative while full-body/upper/lower sister sessions avoid needless complexity spikes.
    rebalanceRepeatedIntents();
    // Final hard-limit reconciliation. Late floor/structure repairs can legitimately change a session
    // after the initial time trim, so re-run the same floor-aware dosage fitter on the finished session.
    // No movement is protected here, but an exercise can never be reduced below one set and the fitter
    // refuses any decrement that creates a new modeled muscle-floor deficit.
    run_enforceFinalTimeLimits(context);
    // Late time/public-floor reconciliation can create fresh one-set assistance after the earlier
    // complexity pass. Compact plans outside the high-capacity public-MEV contract get one final
    // surplus-fragment cleanup at the true end of realization; high-capacity plans keep their visible
    // MEV reserve untouched.
    run_pruneFinalCompactFragments(context);
    run_restoreFinalFloors(context);
    // Final anatomical arm-function reconciliation. Initial selection already prefers complementary
    // curl/wrist functions, but later phase retargeting, time trimming, minimalist caps and public-floor
    // repairs can legitimately change the realized menu after those tiebreakers ran. If that leaves a
    // *major* arm-coverage contract unmet, repair it transactionally here rather than letting a valid
    // priority request fail at the final arbiter. This remains one volume ledger: the repair adds only
    // the missing movement exposure and the existing safe-dosage fitter may reclaim a lower-priority
    // set when clock time is tight.
    const repairMajorArmCoverage = (...args) => run_repairMajorArmCoverage(context, ...args);
    repairMajorArmCoverage();
    // One last semantic-economy reconciliation sits after every floor, distribution, spillover and
    // anatomical repair. This is the invariant boundary: no late pass may silently reintroduce an
    // avoidable same-slot accessory pairing that initial candidate selection correctly filtered.
    consolidateRedundantAccessoryFamilies();
    for (let i = 0; i < realized.length; i++)
        realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    // Final coach-like sequencing pass. Repairs above may add or move exercises late in realization,
    // so ordering belongs at the true end of the pipeline. It changes no dose, prescription, or time
    // budget: it only protects high-value work from avoidable local pre-fatigue and makes supersets
    // contiguous in the rendered workout.
    for (let i = 0; i < realized.length; i++)
        realized[i] = sequenceSessionExercises(realized[i], exerciseMap);
    return request.preferences.volumeApproach === 'minimalist'
        ? realized.map(session => ({ ...session,
            exercises: session.exercises.map(ex => ({ ...ex, workingSetCap: 3 })) }))
        : realized;
}

export { repsForPhase } from './realizer-prescriptions.js';

export { rirForPhase } from './realizer-prescriptions.js';

export { restForExercise } from './realizer-prescriptions.js';

export { progressionStyleForExercise } from './realizer-prescriptions.js';

export { progressionForExercise } from './realizer-prescriptions.js';

export { estimateSessionMinutes } from './realizer-time-budget.js';

export { optimizeSetupAwareSessionSequence } from './realizer-sequence.js';

export { finalizePlannedSession } from './realizer-session-builder.js';

export { realizeStrengthAnchors } from './realizer-strength.js';
