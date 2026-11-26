package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Typed progression coverage for program generation, NOT another prescriber.
 *
 * Exactly one primary route is chosen per block/day/slot. Load schemes,
 * special set techniques, training phases and autoregulation are separate
 * dimensions; a drop, top/backoff, RIR target, deload or myo mini-set must
 * never masquerade as an earned next working-set weight.
 */
/** All seven progression labels currently supported by the web engine.
 * Only safe, explicitly defined basic strategies may calculate simple
 * numbers; Wave/e1RM must retain their separate reviewed source evidence. */
enum class NativeProgressionStyle {
    AUTO, DOUBLE, DYNAMIC, LADDER, LINEAR, WAVE, E1RM
}

enum class ProgramProgressionRoute {
    BASIC_WEIGHTED,
    BASIC_BODYWEIGHT_REPS,
    AUTHORED_WEEKLY_PERIODIZATION,
    PEAK_OR_TEST_HOLD,
    RECOVERY_HOLD,
    SPECIAL_LOAD_SCHEME,
    E1RM_COACH_REVIEW,
    MANUAL
}

data class ProgramProgressionWeek(val blockId: String, val weekInBlock: Int)

data class ProgramProgressionSlotKey(
    val blockId:String,
    val weekday:Int,
    val slotId:String
)

data class ProgramProgressionAssignment(
    val key:ProgramProgressionSlotKey,
    val exerciseId:String,
    val route:ProgramProgressionRoute,
    /** Null when source-authored periodization or a specialist owns the load. */
    val basicStrategy:BasicProgressionStrategy?,
    val workingSetCount:Int,
    val hasAttachedAmplifier:Boolean,
    /** Explicit week overrides are authoritative, never displaced by basic DP. */
    val authoredWeekNumbers:Set<Int>,
    val explanation:String,
    val selectedStyle:NativeProgressionStyle?=null,
    val savedMethod:SavedProgressionMethod?=null
) {
    val mayChangeSavedProgram:Boolean get()=false
    val mayModifyIssuedSet:Boolean get()=false
}

sealed interface ProgramProgressionPlan {
    data class Refused(val explanation:String):ProgramProgressionPlan
    data class Ready(
        val assignments:List<ProgramProgressionAssignment>
    ):ProgramProgressionPlan {
        val mayIssueWorkout:Boolean get()=false
        val maySaveProgram:Boolean get()=false
    }
}

/**
 * Basic numeric modes are implemented by BasicProgressionPolicyDomain or
 * BasicBodyweightRepProgressionDomain. This selector does not call those
 * domains without authenticated completed workouts.
 *
 * TRAINING MAX percentages, weekly waves, strength-to-peak transitions,
 * volume ramps, deloads and RIR/RPE adaptation remain exact-source-authored
 * program structures or specialist coaching reviews. They are modeled
 * explicitly here so generation cannot accidentally substitute linear DP.
 */
object BasicProgramProgressionPlanDomain {
    /** One canonical mapping for generated, reviewed decisions to stored
     * per-slot choices. Authoring/codec never invents a second strategy. */
    fun savedChoice(assignment:ProgramProgressionAssignment):SavedProgressionMethod? =
        when(assignment.route) {
            ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS ->
                if (assignment.selectedStyle == NativeProgressionStyle.LADDER)
                    SavedProgressionMethod.LADDER
                else SavedProgressionMethod.BODYWEIGHT_REPS
            ProgramProgressionRoute.MANUAL -> SavedProgressionMethod.MANUAL
            ProgramProgressionRoute.BASIC_WEIGHTED ->
                when(assignment.basicStrategy) {
                    BasicProgressionStrategy.MANUAL_LOGGING ->
                        SavedProgressionMethod.MANUAL
                    BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION ->
                        SavedProgressionMethod.DOUBLE
                    BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION ->
                        SavedProgressionMethod.DYNAMIC
                    BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION ->
                        SavedProgressionMethod.LINEAR
                    BasicProgressionStrategy.REP_TARGET_PROGRESSION ->
                        SavedProgressionMethod.REP_TARGETS
                    BasicProgressionStrategy.REP_LADDER_PROGRESSION ->
                        SavedProgressionMethod.LADDER
                    BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION ->
                        SavedProgressionMethod.ASSISTANCE_DOUBLE
                    null -> null
                }
            // A reviewed wave/e1RM selection is a saved *method*, not a
            // numerical basic strategy. Preserve its name only on the
            // matching specialist route; the source owns all wave weeks
            // and the effort reviewer owns any e1RM recommendation.
            ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION ->
                if(assignment.selectedStyle==NativeProgressionStyle.WAVE)
                    SavedProgressionMethod.WAVE
                else assignment.savedMethod // preserve a signed basic method behind the authored week
            ProgramProgressionRoute.E1RM_COACH_REVIEW ->
                if(assignment.selectedStyle==NativeProgressionStyle.E1RM)
                    SavedProgressionMethod.E1RM else null
            // Recovery, peak and other authored schemes are not overload.
            else -> null
        }

    fun review(
        cycle:CustomTrainingCycle,
        /** Only the explicitly selected slots can use independent per-set load progression. */
        selectedPerSetDynamic:Set<ProgramProgressionSlotKey> = emptySet(),
        /** Explicit user/programmer choices, never inferred from fatigue. */
        selectedStrategies:Map<ProgramProgressionSlotKey,BasicProgressionStrategy> =
            emptyMap(),
        /** Explicit compatible web-family styles are preserved as named intent. */
        selectedStyles:Map<ProgramProgressionSlotKey,NativeProgressionStyle> =
            emptyMap(),
        /** Provisional catalog roles/patterns; never infer exercise traits from its name. */
        reviewedCatalog:Map<String,CatalogMovement> = emptyMap(),
        experience:AthleteExperience?=null,
        previewWeek: ProgramProgressionWeek? = null,
        /** New generated plans opt in; historical generation replays its frozen policy. */
        reviewedAuto:Boolean=false
    ):ProgramProgressionPlan {
        fun no(why:String)=ProgramProgressionPlan.Refused(why)
        if(cycle.athleteId.isBlank() || cycle.cycleId.isBlank() ||
            cycle.blocks.isEmpty())
            return no("An authenticated full cycle is required.")
        if (previewWeek != null) {
            val block = cycle.blocks.singleOrNull { it.blockId == previewWeek.blockId }
            if (block == null || previewWeek.weekInBlock !in 1..block.weeks)
                return no("Choose a scheduled week in this cycle.")
        }
        val allKeys=cycle.blocks.flatMap {block->
            block.days.flatMap {day->
                day.slots.map {ProgramProgressionSlotKey(
                    block.blockId,day.weekday,it.slotId)}
            }
        }
        if(allKeys.size !in 1..2048 || allKeys.distinct().size!=allKeys.size ||
            !allKeys.toSet().containsAll(selectedPerSetDynamic) ||
            !allKeys.toSet().containsAll(selectedStrategies.keys) ||
            !allKeys.toSet().containsAll(selectedStyles.keys) ||
            selectedStyles.keys.any {it in selectedStrategies || it in selectedPerSetDynamic} ||
            selectedPerSetDynamic.any { key ->
                selectedStrategies[key]!=null &&
                    selectedStrategies[key]!=BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
            })
            return no("Every selected progression must match one unique actual source slot.")

        val assignments=ArrayList<ProgramProgressionAssignment>()
        for(block in cycle.blocks) for(day in block.days) for(slot in day.slots) {
            val key=ProgramProgressionSlotKey(block.blockId,day.weekday,slot.slotId)
            val working=slot.sets.filter {it.type==DraftSetType.WORKING}
            if(working.isEmpty() || working.size>32)
                return no("A slot without a complete working-set definition cannot get a progression.")
            val bodyweight=working.all {it.load==DraftSetLoad.ExplicitBodyweight}
            val loaded=working.all {it.load is DraftSetLoad.Verified}
            if(!bodyweight && !loaded)
                return no("A single exercise slot cannot mix unweighted and weighted work.")
            val values=working.mapNotNull {(it.load as? DraftSetLoad.Verified)?.value}
            val unitBasis=working.mapNotNull {(it.load as? DraftSetLoad.Verified)?.let {load->
                load.unit to load.basis
            }}.distinct()
            if(loaded && (unitBasis.size!=1 || values.any {
                !KotlinSetNumberAuthorityDomain.validPrescribedLoad(it,unitBasis.single().second)
            }))
                return no("The working set has mixed units, load bases or invalid weights.")
            val bands=working.map{it.reps}.distinct()
            val sameWeights=bodyweight || values.all{it.compareTo(values.first())==0}
            val assistance=loaded && unitBasis.single().second==
                LoadBasis.ASSISTANCE_COUNTERWEIGHT
            val catalog=reviewedCatalog[slot.exerciseId]
            val compound=catalog?.pattern!=null &&
                catalog.pattern !in setOf(ComposerPattern.ISOLATION,ComposerPattern.CORE)
            val quickChange=loaded && unitBasis.single().second in setOf(
                LoadBasis.DUMBBELL_PER_HAND,LoadBasis.MACHINE_STACK,
                LoadBasis.CABLE_STACK,LoadBasis.SMITH_TOTAL)
            val sourceWeekOverrides=cycle.weekOverrides[block.blockId].orEmpty().filter {
                it.weekday==day.weekday && it.slotId==slot.slotId
            }
            val authored=sourceWeekOverrides.map {it.weekInBlock}.toSet()
            val amplifier=working.any {it.amplifier!=null}
            // A named percentage set must never fall through to straight
            // double progression just because its current weights are equal.
            // Even a named override on a base ordinary slot retains ownership.
            val hasNamedProtocol=slot.sets.any{it.percentageIntent!=null} ||
                sourceWeekOverrides.any{edit->edit.sets.any{it.percentageIntent!=null}}
            if(hasNamedProtocol) {
                if(key in selectedPerSetDynamic || key in selectedStrategies ||
                    key in selectedStyles)
                    return no("Named percentage prescriptions require their dedicated reviewed progression path.")
                assignments+=ProgramProgressionAssignment(
                    key,slot.exerciseId,ProgramProgressionRoute.SPECIAL_LOAD_SCHEME,null,
                    working.size,amplifier,Collections.unmodifiableSet(authored),
                    "Named percentage protocol owns the prescription; no basic weight or rep override.",
                    null,slot.progressionMethod)
                continue
            }
            val saved=slot.progressionMethod
            if(saved!=null && (key in selectedPerSetDynamic ||
                key in selectedStrategies || key in selectedStyles))
                return no("The saved progression owns this slot; edit its reviewed source revision rather than overlaying a second selection.")
            val savedStyle=when(saved) {
                SavedProgressionMethod.AUTO -> NativeProgressionStyle.AUTO
                SavedProgressionMethod.DOUBLE -> NativeProgressionStyle.DOUBLE
                SavedProgressionMethod.DYNAMIC -> NativeProgressionStyle.DYNAMIC
                SavedProgressionMethod.LADDER -> NativeProgressionStyle.LADDER
                SavedProgressionMethod.LINEAR -> NativeProgressionStyle.LINEAR
                SavedProgressionMethod.WAVE -> NativeProgressionStyle.WAVE
                SavedProgressionMethod.E1RM -> NativeProgressionStyle.E1RM
                else -> null
            }
            val savedBasic=when(saved) {
                SavedProgressionMethod.MANUAL ->
                    BasicProgressionStrategy.MANUAL_LOGGING
                SavedProgressionMethod.REP_TARGETS ->
                    BasicProgressionStrategy.REP_TARGET_PROGRESSION
                SavedProgressionMethod.ASSISTANCE_DOUBLE ->
                    BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION
                else -> null
            }
            if(saved==SavedProgressionMethod.BODYWEIGHT_REPS && !bodyweight)
                return no("Saved bodyweight rep progression cannot apply to a loaded station.")
            val explicitStyle=selectedStyles[key] ?: savedStyle
            // A standardized, source-matched free bar is a reliable physical
            // signal. A compound pattern alone does not establish novice
            // linear suitability or a trustworthy strength estimate.
            val reviewedBarbell=compound && loaded &&
                catalog?.expectedLoadBasis==LoadBasis.BARBELL_TOTAL &&
                unitBasis.single().second==LoadBasis.BARBELL_TOTAL &&
                slot.machineInstanceId==null
            // Explicit Auto is a current source choice, not a historical
            // generator default. v1–v12 null/default replay remains frozen.
            val automatic=(reviewedAuto || explicitStyle==NativeProgressionStyle.AUTO) && (explicitStyle==null ||
                explicitStyle==NativeProgressionStyle.AUTO) &&
                savedBasic==null && saved!=SavedProgressionMethod.BODYWEIGHT_REPS &&
                key !in selectedStrategies && key !in selectedPerSetDynamic
            val autoPeakE1rm=automatic && reviewedBarbell &&
                experience in setOf(AthleteExperience.INTERMEDIATE,AthleteExperience.ADVANCED) &&
                block.phase==TrainingPhase.PEAK && block.purpose==CustomBlockPurpose.TRAINING &&
                slot.role==ComposerRole.PRIMARY && sameWeights && bands.size==1 &&
                bands.single().maximum<=5 &&
                (authored.isEmpty() || saved==SavedProgressionMethod.AUTO) && !amplifier
            // This is the same existing e1RM review route, not a new load
            // prescriber. It can only advance from comparable recorded work.
            val style=if(autoPeakE1rm)NativeProgressionStyle.E1RM else explicitStyle
            val styledBasic=when(style) {
                NativeProgressionStyle.DOUBLE ->
                    BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
                NativeProgressionStyle.DYNAMIC ->
                    BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
                NativeProgressionStyle.LADDER ->
                    if(bodyweight)null else BasicProgressionStrategy.REP_LADDER_PROGRESSION
                NativeProgressionStyle.LINEAR ->
                    BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION
                NativeProgressionStyle.AUTO,
                NativeProgressionStyle.WAVE,
                NativeProgressionStyle.E1RM, null -> null
            }
            val requested=selectedStrategies[key] ?: savedBasic ?: styledBasic ?:
                if(key in selectedPerSetDynamic)
                    BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION else null
            // A named rep progression is not ready solely because its range
            // is valid. Every working set needs a separately saved exact
            // current target. Generated/default logging remains possible
            // without one, but cannot claim this specific method is ready.
            val needsExactTargets=style==NativeProgressionStyle.LADDER ||
                saved==SavedProgressionMethod.BODYWEIGHT_REPS ||
                requested in setOf(BasicProgressionStrategy.REP_TARGET_PROGRESSION,
                    BasicProgressionStrategy.REP_LADDER_PROGRESSION)
            if(needsExactTargets && working.any {set->
                set.exactRepTarget?.let{it in set.reps.minimum..set.reps.maximum}!=true
            })return no("The selected rep progression needs an exact current target for every working set, separately from its approved rep range.")
            // The base slot prescribes week 1. Every later week must have
            // an explicit source-authored prescription before we can call
            // the block a Wave. A single week-2 override is NOT a wave:
            // silently repeating week 1 on weeks 3/4 would fabricate its
            // numerical periodization. A separate week-1 override is allowed.
            if(style==NativeProgressionStyle.WAVE &&
                (block.weeks<2 || sourceWeekOverrides.size!=authored.size ||
                    authored.any { it !in 1..block.weeks } ||
                    (2..block.weeks).any { it !in authored }))
                return no("Wave progression needs a complete separately authored weekly load plan, not an invented wave.")
            // e1RM may review one comparable straight, loaded primary,
            // never a bodyweight prescription, assistance counterweight,
            // mixed-load scheme, technique extension or a week already
            // prescribed by the athlete. No fallback to guessed maxes.
            if(style==NativeProgressionStyle.E1RM &&
                (slot.role!=ComposerRole.PRIMARY ||
                    block.purpose!=CustomBlockPurpose.TRAINING ||
                    block.phase==TrainingPhase.HYPERTROPHY ||
                    !loaded || assistance || !sameWeights ||
                    bands.size!=1 ||
                    (authored.isNotEmpty() && saved!=SavedProgressionMethod.E1RM && !autoPeakE1rm) || amplifier))
                return no("e1RM progression requires a straight weighted primary strength or peak exercise with no conflicting authored week or amplifier.")
            val selectedDynamic=requested==
                BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
            // A durable basic method can coexist with a later explicitly
            // reviewed week override. That authored week is authoritative;
            // the saved method remains the fallback intent, NOT a load writer
            // on that week. A new transient basic selection cannot override
            // periodization, and recovery/peak still block basic overload.
            val unavailablePhase=block.purpose!=CustomBlockPurpose.TRAINING ||
                block.phase==TrainingPhase.PEAK ||
                (authored.isNotEmpty() && saved==null)
            if(requested!=null && requested!=BasicProgressionStrategy.MANUAL_LOGGING &&
                unavailablePhase)
                return no("An authored week, recovery or peak block cannot be overridden by an earned basic progression.")
            if(requested!=null && requested!=BasicProgressionStrategy.MANUAL_LOGGING &&
                (bodyweight || bands.size!=1 || (assistance &&
                    requested !in setOf(
                        BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION,
                        BasicProgressionStrategy.REP_TARGET_PROGRESSION)) ||
                    (!assistance &&
                        requested==BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION) ||
                    ((requested==BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION ||
                        requested==BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION ||
                        requested==BasicProgressionStrategy.REP_LADDER_PROGRESSION) &&
                        !sameWeights)))
                return no("The requested basic progression cannot operate on this authored set scheme.")
            if(selectedDynamic && (bodyweight || assistance))
                return no("Independent weighted-set progression requires an ordinary weighted movement.")
            val special=loaded && !sameWeights && !selectedDynamic &&
                requested!=BasicProgressionStrategy.REP_TARGET_PROGRESSION
            // No inferential distinction between an intended backoff, pyramid,
            // percentage wave or different manually set loads: require a real
            // authored load scheme instead of silently leveling all sets.
            val route:ProgramProgressionRoute
            val strategy:BasicProgressionStrategy?
            val note:String
            when {
                requested==BasicProgressionStrategy.MANUAL_LOGGING -> {
                    route=ProgramProgressionRoute.MANUAL
                    strategy=BasicProgressionStrategy.MANUAL_LOGGING
                    note="Keep the athlete's exact prescription unchanged and log factual work."
                }
                block.purpose==CustomBlockPurpose.RECOVERY -> {
                    route=ProgramProgressionRoute.RECOVERY_HOLD
                    strategy=null
                    note="A recovery block is a deliberate dose change, not earned overload."
                }
                authored.isNotEmpty() && (previewWeek == null ||
                    previewWeek.blockId != block.blockId || previewWeek.weekInBlock in authored ||
                    saved == null || style == NativeProgressionStyle.WAVE) -> {
                    route=ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION
                    strategy=null
                    note="Exact source-authored weekly sets own the changing loads and reps."
                }
                style==NativeProgressionStyle.E1RM -> {
                    route=ProgramProgressionRoute.E1RM_COACH_REVIEW
                    strategy=null
                    note="Review effort-adjusted strength evidence and the shared all-set physical increment gate; no automatic load change."
                }
                block.purpose==CustomBlockPurpose.PERFORMANCE_TEST ||
                    block.phase==TrainingPhase.PEAK -> {
                    route=ProgramProgressionRoute.PEAK_OR_TEST_HOLD
                    strategy=null
                    note="Peak/test targets require authored periodization, not a surprise load jump."
                }
                amplifier -> {
                    route=ProgramProgressionRoute.SPECIAL_LOAD_SCHEME
                    strategy=null
                    note="An attached working-set technique needs specialist main-set and follow-up review, not an ordinary basic load increase."
                }
                special || bands.size!=1 -> {
                    route=ProgramProgressionRoute.SPECIAL_LOAD_SCHEME
                    strategy=null
                    note="Mixed working weights or rep bands are an intentional or unverified set scheme, not ordinary straight-set progression."
                }
                bodyweight -> {
                    route=ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS
                    strategy=null
                    note="Add one approved exact bodyweight rep target at a time; never invent a load."
                }
                requested!=null -> {
                    route=ProgramProgressionRoute.BASIC_WEIGHTED
                    strategy=requested
                    note="Source-selected, explicit basic progression; no automatic load or target changes."
                }
                assistance -> {
                    route=ProgramProgressionRoute.BASIC_WEIGHTED
                    strategy=BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION
                    note="Earn one verified lower counterweight after all straight work reaches the upper rep bound."
                }
                experience==AthleteExperience.BEGINNER &&
                    compound && slot.role==ComposerRole.PRIMARY &&
                    (!automatic || reviewedBarbell) -> {
                    route=ProgramProgressionRoute.BASIC_WEIGHTED
                    strategy=BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION
                    note="A calibrated novice primary compound may earn a verified linear step after completing every set."
                }
                compound && quickChange -> {
                    route=ProgramProgressionRoute.BASIC_WEIGHTED
                    strategy=BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
                    note="A source-reviewed compound with quick-change equipment may advance each working set independently."
                }
                else -> {
                    route=ProgramProgressionRoute.BASIC_WEIGHTED
                    strategy=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
                    note="All straight sets must reach the upper rep bound before an earned increment."
                }
            }
            assignments+=ProgramProgressionAssignment(
                key,slot.exerciseId,route,strategy,working.size,amplifier,
                Collections.unmodifiableSet(authored),note,style,saved)
        }
        return ProgramProgressionPlan.Ready(
            Collections.unmodifiableList(assignments))
    }
}
