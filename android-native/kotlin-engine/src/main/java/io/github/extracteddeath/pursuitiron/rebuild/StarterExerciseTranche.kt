package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * First source-linked, ENGINEERING-REVIEWED-PROVISIONAL starter tranche.
 * It is not a medically or independently science-validated exercise library.
 *
 * Each ID below is an exact curated legacy ID, not a newly minted alias.
 * Facilities and weight conventions are explicit, and no user load is set
 * here. Numeric loads must be provided and verified against a real gym.
 *
 * Ambiguous legacy metadata is explicitly resolved or excluded; in
 * particular "back" does not become an undocumented full 1.0 credit for
 * both upper back and lats, nor does "core" imply every trunk muscle.
 */
data class StarterCatalogEntry(
    val legacyCuratedId:String,
    val movement:CatalogMovement,
    val practicalRepBand:RepBand,
    val mappingNotes:String,
    /** No source exercise permits silent loading transfer on substitution. */
    val mayTransferPreviousLoad:Boolean=false
)

object StarterExerciseTranche {
    private const val REVIEW="engineering-provisional-v1"
    private const val BAR="straight-bar"
    private const val DBS="dumbbell-rack"
    // Different machines must NEVER share an invented generic stack.
    private const val LEG_EXTENSION="leg-extension-machine"
    private const val SEATED_LEG_CURL="seated-leg-curl-machine"
    private val press=setOf(ComposerFocus.FULL_BODY,ComposerFocus.UPPER,
        ComposerFocus.PUSH)
    private val pull=setOf(ComposerFocus.FULL_BODY,ComposerFocus.UPPER,
        ComposerFocus.PULL)
    private val lower=setOf(ComposerFocus.FULL_BODY,ComposerFocus.LOWER,
        ComposerFocus.LEGS)
    private val all=ComposerFocus.entries.toSet()

    private fun direct(m:TrainingMuscle)=DeclaredMuscleCredit(
        m,MuscleCreditRole.DIRECT,BigDecimal.ONE)
    private fun indirect(m:TrainingMuscle,value:String)=DeclaredMuscleCredit(
        m,MuscleCreditRole.INDIRECT,BigDecimal(value))

    private fun entry(
        legacyId:String,family:String,variant:String,pattern:ComposerPattern,
        role:ComposerRole,focus:Set<ComposerFocus>,implement:String?,
        basis:LoadBasis?,facilities:Set<String>,reps:IntRange,
        note:String,vararg credits:DeclaredMuscleCredit
    ):StarterCatalogEntry {
        require(note.isNotBlank())
        val movement=CatalogMovement(
            exerciseId=legacyId,familyId=family,variantId=variant,
            attachmentId=null,pattern=pattern,role=role,
            permittedFocus=focus,equipmentId=implement,
            expectedLoadBasis=basis,credits=credits.toList(),
            creditStatus=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
            creditReviewReference=REVIEW,requiredFacilityIds=facilities,
            sourcePracticalReps=RepBand(reps.first,reps.last))
        return StarterCatalogEntry(legacyId,movement,
            RepBand(reps.first,reps.last),note)
    }

    private val definitions=listOf(
        entry("barbell_bench","bench_press","barbell",
            ComposerPattern.HORIZONTAL_PRESS,ComposerRole.PRIMARY,press,
            BAR,LoadBasis.BARBELL_TOTAL,setOf("flat-bench","rack"),1..15,
            "Curated chest direct, triceps/front delts indirect; requires bench and rack.",
            direct(TrainingMuscle.CHEST),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.FRONT_DELTS,"0.5")),
        entry("paused_bench","bench_press","paused",
            ComposerPattern.HORIZONTAL_PRESS,ComposerRole.PRIMARY,press,
            BAR,LoadBasis.BARBELL_TOTAL,setOf("flat-bench","rack"),2..12,
            "Paused bench keeps distinct exercise identity and authored strength targets.",
            direct(TrainingMuscle.CHEST),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.FRONT_DELTS,"0.5")),
        entry("back_squat","squat","back-barbell",
            ComposerPattern.SQUAT,ComposerRole.PRIMARY,lower,
            BAR,LoadBasis.BARBELL_TOTAL,setOf("rack"),1..12,
            "Curated quadriceps direct, glutes indirect; rack explicitly required.",
            direct(TrainingMuscle.QUADS),indirect(TrainingMuscle.GLUTES,"0.5")),
        entry("barbell_ohp","overhead_press","barbell",
            ComposerPattern.VERTICAL_PRESS,ComposerRole.PRIMARY,press,
            BAR,LoadBasis.BARBELL_TOTAL,setOf("rack"),1..12,
            "Curated front delts direct, triceps and side delts indirect.",
            direct(TrainingMuscle.FRONT_DELTS),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.SIDE_DELTS,"0.25")),
        entry("incline_db_press","bench_press","incline-dumbbell",
            ComposerPattern.HORIZONTAL_PRESS,ComposerRole.SECONDARY,press,
            DBS,LoadBasis.DUMBBELL_PER_HAND,setOf("incline-bench"),5..20,
            "Incline-bench fixture is distinct from flat bench; per-hand loads.",
            direct(TrainingMuscle.CHEST),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.FRONT_DELTS,"0.5")),
        entry("one_arm_db_row","dumbbell_row","one-arm",
            ComposerPattern.HORIZONTAL_PULL,ComposerRole.SECONDARY,pull,
            DBS,LoadBasis.DUMBBELL_PER_HAND,setOf("flat-bench"),6..20,
            "Legacy broad back mapped provisionally to upper back direct and lats indirect; biceps/rear delts remain indirect. Requires independent physiology review.",
            direct(TrainingMuscle.UPPER_BACK),
            indirect(TrainingMuscle.LATS,"0.5"),
            indirect(TrainingMuscle.BICEPS,"0.5"),
            indirect(TrainingMuscle.REAR_DELTS,"0.25")),
        entry("db_lateral_raise","lateral_raise","dumbbell",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),8..35,
            "Explicit side-delt isolation; no invented front-delt credit.",
            direct(TrainingMuscle.SIDE_DELTS)),
        entry("db_curl","elbow_flexion","supinated-dumbbell",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),6..25,
            "Explicit biceps stimulus; brachialis is NOT silently credited.",
            direct(TrainingMuscle.BICEPS)),
        entry("db_overhead_triceps","elbow_extension","overhead-dumbbell",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),8..25,
            "No cable attachment or stack is implied.",
            direct(TrainingMuscle.TRICEPS)),
        entry("db_split_squat","squat","split-dumbbell",
            ComposerPattern.SQUAT,ComposerRole.SECONDARY,lower,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),6..20,
            "Curated quadriceps direct, glutes indirect; unilateral target is per side.",
            direct(TrainingMuscle.QUADS),indirect(TrainingMuscle.GLUTES,"0.5")),
        entry("db_rdl","hip_hinge","romanian-dumbbell",
            ComposerPattern.HINGE,ComposerRole.SECONDARY,lower,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),5..20,
            "Curated hamstrings direct and glutes indirect. Legacy generic back 0.25 is intentionally quarantined, not assigned to lats.",
            direct(TrainingMuscle.HAMSTRINGS),
            indirect(TrainingMuscle.GLUTES,"0.5")),
        entry("db_calf_raise","calf_raise","dumbbell",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,lower,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),8..30,
            "Calf credit only; no generic lower-leg double counting.",
            direct(TrainingMuscle.CALVES)),
        entry("bodyweight_crunch","trunk_flexion","bodyweight",
            ComposerPattern.CORE,ComposerRole.ACCESSORY,all,
            null,null,emptySet(),10..40,
            "Legacy 'core' provisionally maps to ABS only; explicit bodyweight has no phantom zero-pound stock.",
            direct(TrainingMuscle.ABS)),
        entry("barbell_row","barbell_row","standard-barbell",
            ComposerPattern.HORIZONTAL_PULL,ComposerRole.SECONDARY,pull,
            BAR,LoadBasis.BARBELL_TOTAL,emptySet(),3..15,
            "Legacy broad back provisionally maps to upper back DIRECT and lats INDIRECT. This is a distinct barbell-total lift; loading is never transferred from dumbbell rows.",
            direct(TrainingMuscle.UPPER_BACK),
            indirect(TrainingMuscle.LATS,"0.5"),
            indirect(TrainingMuscle.BICEPS,"0.5"),
            indirect(TrainingMuscle.REAR_DELTS,"0.5")),
        entry("rdl","hip_hinge","romanian-barbell",
            ComposerPattern.HINGE,ComposerRole.PRIMARY,lower,
            BAR,LoadBasis.BARBELL_TOTAL,emptySet(),3..15,
            "Barbell Romanian deadlift stays distinct from dumbbell RDL. Legacy generic back credit is quarantined; no fictitious lat or lower-back credit.",
            direct(TrainingMuscle.HAMSTRINGS),
            indirect(TrainingMuscle.GLUTES,"0.5")),
        entry("conventional_deadlift","hip_hinge","conventional-barbell",
            ComposerPattern.HINGE,ComposerRole.PRIMARY,lower,
            BAR,LoadBasis.BARBELL_TOTAL,emptySet(),1..8,
            "Legacy hamstrings and glutes are separately direct. The generic back secondary credit is quarantined until anatomical review; fatigue and skill are NOT inferred from this metadata.",
            direct(TrainingMuscle.HAMSTRINGS),
            direct(TrainingMuscle.GLUTES)),
        entry("db_rear_delt_raise","rear_delt_raise","dumbbell",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),10..30,
            "Rear delts direct. Unresolved legacy generic back minor credit is intentionally quarantined, not double-counted as lats and upper back.",
            direct(TrainingMuscle.REAR_DELTS)),
        entry("pullup","vertical_pull","unweighted-overhand",
            ComposerPattern.VERTICAL_PULL,ComposerRole.PRIMARY,pull,
            null,null,setOf("pullup-bar"),3..20,
            "Only an unweighted bodyweight pull-up with a confirmed bar is represented. Weighted or assisted pull-ups require distinct loaded variants. Legacy broad back is provisionally mapped to lats; no invented load.",
            direct(TrainingMuscle.LATS),
            indirect(TrainingMuscle.BICEPS,"0.5")),
        entry("leg_extension","knee_extension","seated-machine",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,lower,
            LEG_EXTENSION,LoadBasis.EXERCISE_OVERRIDE,emptySet(),8..30,
            "The exact leg-extension machine uses its own verified selectable logged loads. No global machine stack, lever weight, mechanical advantage or increment is presumed.",
            direct(TrainingMuscle.QUADS)),
        entry("seated_leg_curl","knee_flexion","seated-machine",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,lower,
            SEATED_LEG_CURL,LoadBasis.EXERCISE_OVERRIDE,emptySet(),6..25,
            "The seated leg curl uses its own independent verified machine loads. Machine labels or adjacent gym inventory never imply equivalent resistance.",
            direct(TrainingMuscle.HAMSTRINGS))
    )

    init {
        require(definitions.size==20)
        require(definitions.map {it.legacyCuratedId}.distinct().size==
            definitions.size)
        require(definitions.all {
            it.legacyCuratedId==it.movement.exerciseId &&
                it.movement.creditReviewReference==REVIEW
        })
    }

    fun entries():List<StarterCatalogEntry> =
        Collections.unmodifiableList(definitions)

    fun movements():List<CatalogMovement> =
        Collections.unmodifiableList(definitions.map {it.movement})

    fun forGym(
        template:ComposerRequest,
        athleteSelections:List<CatalogExerciseChoice>,
        actualGym:CatalogGym
    ):CatalogWeekRequest=CatalogWeekRequest(
        template,movements(),
        Collections.unmodifiableList(athleteSelections.toList()),actualGym)
}
