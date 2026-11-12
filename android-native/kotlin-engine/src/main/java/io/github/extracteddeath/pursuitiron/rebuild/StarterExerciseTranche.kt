package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * First source-linked, ENGINEERING-REVIEWED-PROVISIONAL starter tranche.
 * It is not a medically or independently science-validated exercise library.
 *
 * Each ID below is an exact canonical legacy curated/extended source ID, not a newly minted alias.
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
    // Conservative, provisional coaching guard for closely related heavy
    // variants. Never infer this group from a display name or old JS scores.
    private val reviewedRecoveryGroups=mapOf(
        "barbell_bench" to ("heavy-bench" to 2),
        "paused_bench" to ("heavy-bench" to 2),
        "inc-bb-bench" to ("heavy-bench" to 2),
        "rdl" to ("heavy-barbell-hinge" to 2),
        "conventional_deadlift" to ("heavy-barbell-hinge" to 2)
    )

    private fun direct(m:TrainingMuscle)=DeclaredMuscleCredit(
        m,MuscleCreditRole.DIRECT,BigDecimal.ONE)
    private fun indirect(m:TrainingMuscle,value:String)=DeclaredMuscleCredit(
        m,MuscleCreditRole.INDIRECT,BigDecimal(value))

    private fun entry(
        legacyId:String,family:String,variant:String,pattern:ComposerPattern,
        role:ComposerRole,focus:Set<ComposerFocus>,implement:String?,
        basis:LoadBasis?,facilities:Set<String>,reps:IntRange,
        note:String,vararg credits:DeclaredMuscleCredit,
        attachmentId:String?=null
    ):StarterCatalogEntry {
        require(note.isNotBlank())
        val recovery=reviewedRecoveryGroups[legacyId]
        val movement=CatalogMovement(
            exerciseId=legacyId,familyId=family,variantId=variant,
            attachmentId=attachmentId,pattern=pattern,role=role,
            permittedFocus=focus,equipmentId=implement,
            expectedLoadBasis=basis,credits=credits.toList(),
            creditStatus=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
            creditReviewReference=REVIEW,requiredFacilityIds=facilities,
            sourcePracticalReps=RepBand(reps.first,reps.last),
            recoveryGroupId=recovery?.first,
            minimumRecoveryGroupDayGap=recovery?.second ?:1)
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
            direct(TrainingMuscle.HAMSTRINGS)),
        // Source: canonical legacy extended IDs wrist-curl/reverse-wrist-curl.
        // Their generic 'forearms' metadata is intentionally NOT imported:
        // wrist flexion and extension have independently named anatomy and
        // distinct working-set credits. ACE Exercise Library independently
        // documents Wrist Curl — Flexion and Wrist Curl — Extension
        // (acefitness.org/resources/everyone/exercise-library/body-part/arms/).
        // These are still engineering-provisional, NOT outcome-calibrated MEV.
        entry("wrist-curl","wrist_flexion","dumbbell",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),12..20,
            "Source extended wrist-curl, not generic forearms. Direct wrist flexors only; no wrist-extensor or brachialis credit. Per-hand dumbbell load.",
            direct(TrainingMuscle.WRIST_FLEXORS)),
        entry("reverse-wrist-curl","wrist_extension","dumbbell",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),12..20,
            "Source extended reverse-wrist-curl, not generic forearms. Direct wrist extensors only; no wrist-flexor or brachialis credit. Per-hand dumbbell load.",
            direct(TrainingMuscle.WRIST_EXTENSORS)),
        // Source-backed additional candidates. Exact identity, real gym stock
        // and verified load semantics are required before composition; this
        // tranche does not reuse legacy loading, invent station increments, or
        // treat web muscle coefficients as individualized dose science.
        entry("inc-bb-bench","bench_press","incline-barbell",
            ComposerPattern.HORIZONTAL_PRESS,ComposerRole.SECONDARY,press,
            BAR,LoadBasis.BARBELL_TOTAL,setOf("incline-bench","rack"),6..10,
            "Incline barbell press is a separate movement identity from flat bench and dumbbell incline. Requires an actual incline bench and rack; verified total-bar loading. Never borrow flat-bench weight or PR.",
            direct(TrainingMuscle.CHEST),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.FRONT_DELTS,"0.5")),
        entry("incline_smith","bench_press","incline-smith",
            ComposerPattern.HORIZONTAL_PRESS,ComposerRole.SECONDARY,press,
            "incline-smith-station",LoadBasis.SMITH_TOTAL,
            setOf("incline-bench"),5..20,
            "Incline Smith loading must use the verified station-specific Smith total, not a free-weight bar or generic machine stack. No automatic transfer of free-weight or alternative Smith loads.",
            direct(TrainingMuscle.CHEST),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.FRONT_DELTS,"0.5")),
        entry("machine_press","machine_chest_press","chest-press",
            ComposerPattern.HORIZONTAL_PRESS,ComposerRole.SECONDARY,press,
            "chest-press-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),5..25,
            "The selected physical chest-press machine must have its own verified loads. Machine A/B histories and progression remain separate; zero generic machine stacks or equivalences are inferred.",
            direct(TrainingMuscle.CHEST),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.FRONT_DELTS,"0.5")),
        entry("leg_press","leg_press","machine",
            ComposerPattern.SQUAT,ComposerRole.SECONDARY,lower,
            "leg-press-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),6..25,
            "Leg press uses a distinct physical machine and its confirmed exercise-specific load scale, not the squat barbell or a generic machine increment. Loaded sled mechanics and cross-machine comparison are not presumed.",
            direct(TrainingMuscle.QUADS),
            indirect(TrainingMuscle.GLUTES,"0.5")),
        // Five independently stock-qualified source identities; source fatigue
        // scores are NOT transplanted as individualized prescription rules.
        entry("hack_squat","squat","hack-machine",
            ComposerPattern.SQUAT,ComposerRole.SECONDARY,lower,
            "hack-squat-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),5..20,
            "Hack squat uses its confirmed physical machine load scale; never transfer back-squat or leg-press numbers.",
            direct(TrainingMuscle.QUADS),
            indirect(TrainingMuscle.GLUTES,"0.5")),
        entry("hip_thrust","hip_extension","barbell",
            ComposerPattern.HINGE,ComposerRole.SECONDARY,lower,
            BAR,LoadBasis.BARBELL_TOTAL,setOf("flat-bench"),5..20,
            "Source barbell hip thrust requires a real bench and bar; direct glutes only. Minor hamstring source credit is deliberately quarantined.",
            direct(TrainingMuscle.GLUTES)),
        entry("cable_lateral_raise","lateral_raise","cable-handle",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),8..35,
            "Cable side-deltoid isolation requires a verified cable stack and single-handle attachment, not dumbbell loading.",
            direct(TrainingMuscle.SIDE_DELTS),attachmentId="single-handle"),
        entry("lat-pulldown","vertical_pull","machine-lat",
            ComposerPattern.VERTICAL_PULL,ComposerRole.SECONDARY,pull,
            "lat-pulldown-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),8..12,
            "A dedicated lat-pulldown station must have its own verified physical loads. Legacy broad back maps conservatively to lats; traps minor credit is quarantined.",
            direct(TrainingMuscle.LATS),
            indirect(TrainingMuscle.BICEPS,"0.5")),
        entry("rope-pushdown","elbow_extension","cable-rope",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),10..15,
            "Rope pushdown requires the actual rope attachment and the confirmed cable-stack increment. No weight carryover from different pulleys.",
            direct(TrainingMuscle.TRICEPS),attachmentId="rope"),
        // Eleven MORE exact currently active web-curated source identities.
        // No web fatigue scores or generic machine loads are inherited;
        // the exercise-specific implement, facility, attachment and verified
        // stock must be proven by the actual gym before a target is offered.
        entry("cable_fly","chest_adduction","dual-handle",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),8..30,
            "Dual-handle cable fly with verified cable-stack resistance. Physical pulley and attachment must be confirmed; never borrow pec-deck or dumbbell fly loads.",
            direct(TrainingMuscle.CHEST),attachmentId="dual-handles"),
        entry("chest_supported_row","chest_supported_row","incline-dumbbell",
            ComposerPattern.HORIZONTAL_PULL,ComposerRole.SECONDARY,pull,
            DBS,LoadBasis.DUMBBELL_PER_HAND,setOf("incline-bench"),5..20,
            "Selects the dumbbell-on-incline-bench source equipment path only; machine row is a distinct physical variant requiring separate stock. Legacy broad back maps provisionally to upper back, not every back muscle.",
            direct(TrainingMuscle.UPPER_BACK),
            indirect(TrainingMuscle.LATS,"0.5"),
            indirect(TrainingMuscle.BICEPS,"0.5"),
            indirect(TrainingMuscle.REAR_DELTS,"0.5")),
        entry("neutral_pulldown","vertical_pull","neutral-cable",
            ComposerPattern.VERTICAL_PULL,ComposerRole.SECONDARY,pull,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),5..20,
            "Neutral-grip cable pulldown needs its actual neutral handle and verified cable stack; it is not equivalent to the separately calibrated lat-pulldown machine.",
            direct(TrainingMuscle.LATS),
            indirect(TrainingMuscle.BICEPS,"0.5"),
            attachmentId="neutral-grip-bar"),
        entry("rear_delt_fly","rear_delt_fly","reverse-pec-deck",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            "reverse-pec-deck-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),8..30,
            "Reverse pec deck needs its own verified resistance; generic back minor source credit is quarantined instead of double-counting lats or upper back.",
            direct(TrainingMuscle.REAR_DELTS)),
        entry("machine_ohp","overhead_press","shoulder-machine",
            ComposerPattern.VERTICAL_PRESS,ComposerRole.SECONDARY,press,
            "shoulder-press-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),5..20,
            "Seated shoulder-press machine uses its own physical load history, never barbell OHP weights; front delts direct and triceps/side delts conservatively indirect.",
            direct(TrainingMuscle.FRONT_DELTS),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.SIDE_DELTS,"0.25")),
        entry("preacher_curl","elbow_flexion","preacher-machine",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            "preacher-curl-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),6..25,
            "Curated preacher machine is NOT an assumed EZ-bar preacher bench or a generic cable curl; biceps only, not undocumented brachialis credit.",
            direct(TrainingMuscle.BICEPS)),
        entry("cable_curl","elbow_flexion","cable-straight-bar",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),8..30,
            "Curated cable curl represented as an explicit straight-bar attachment; verified cable-stack loads do not transfer to dumbbells or another pulley.",
            direct(TrainingMuscle.BICEPS),attachmentId="straight-bar"),
        entry("cable_triceps","elbow_extension","cable-straight-bar",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),8..30,
            "Curated generic cable triceps extension represented with a real straight-bar attachment; rope pushdown is separately identified.",
            direct(TrainingMuscle.TRICEPS),attachmentId="straight-bar"),
        entry("overhead_cable_triceps","elbow_extension","overhead-cable-rope",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),8..30,
            "Overhead cable triceps is the rope variant; load is verified on the actual pulley, never borrowed from pushdown.",
            direct(TrainingMuscle.TRICEPS),attachmentId="rope"),
        entry("standing_calf","calf_raise","standing-machine",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,lower,
            "standing-calf-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),6..25,
            "Standing calf machine has its own resistance scale; direct calves only, distinct from dumbbell raises.",
            direct(TrainingMuscle.CALVES)),
        entry("cable_crunch","trunk_flexion","cable-rope",
            ComposerPattern.CORE,ComposerRole.ACCESSORY,all,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),8..30,
            "Curated generic core is narrowly mapped to abdominal trunk flexion; rope attachment and verified cable loading are mandatory.",
            direct(TrainingMuscle.ABS),attachmentId="rope"),
        // Seven source-matched active web exercise IDs. These are separate,
        // athlete-calibrated physical movements, not aliases for the first
        // 42 lifts or authority to transfer their working loads/history.
        entry("db-bench","bench_press","flat-dumbbell",
            ComposerPattern.HORIZONTAL_PRESS,ComposerRole.SECONDARY,press,
            DBS,LoadBasis.DUMBBELL_PER_HAND,setOf("flat-bench"),5..20,
            "Active web flat dumbbell bench uses verified PER-HAND dumbbells and an actual flat bench. Incline, barbell and machine presses retain independent baselines.",
            direct(TrainingMuscle.CHEST),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.FRONT_DELTS,"0.5")),
        entry("db-fly","chest_adduction","flat-dumbbell-fly",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            DBS,LoadBasis.DUMBBELL_PER_HAND,setOf("flat-bench"),8..25,
            "Active web dumbbell fly: verified per-hand loads and flat-bench availability; no cable fly or pec-deck loading is presumed.",
            direct(TrainingMuscle.CHEST)),
        entry("db-shoulder","overhead_press","dumbbell",
            ComposerPattern.VERTICAL_PRESS,ComposerRole.SECONDARY,press,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),5..20,
            "Active web dumbbell shoulder press: verified per-hand loading distinct from barbell OHP and shoulder-press machines.",
            direct(TrainingMuscle.FRONT_DELTS),
            indirect(TrainingMuscle.TRICEPS,"0.5"),
            indirect(TrainingMuscle.SIDE_DELTS,"0.25")),
        entry("inc-curl","elbow_flexion","incline-dumbbell-supinated",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            DBS,LoadBasis.DUMBBELL_PER_HAND,setOf("incline-bench"),6..25,
            "Active web incline dumbbell curl requires a confirmed incline bench and per-hand stock; direct biceps without undocumented brachialis credit.",
            direct(TrainingMuscle.BICEPS)),
        entry("hammer","elbow_flexion","dumbbell-neutral-grip",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            DBS,LoadBasis.DUMBBELL_PER_HAND,emptySet(),6..25,
            "Active web hammer curl is a separate neutral-grip movement: provisional direct brachialis and indirect biceps, not a generic mixed elbow-flexor bucket or an automatic DB-curl load transfer.",
            direct(TrainingMuscle.BRACHIALIS),
            indirect(TrainingMuscle.BICEPS,"0.5")),
        entry("face-pull","rear_delt_fly","cable-rope",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,pull,
            "cable-station",LoadBasis.CABLE_STACK,emptySet(),10..30,
            "Active web cable face pull is offered only with the real rope attachment and verified local cable stack; rear-deltoid direct credit only.",
            direct(TrainingMuscle.REAR_DELTS),attachmentId="rope"),
        entry("pec-deck","chest_adduction","pec-deck-machine",
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,press,
            "pec-deck-machine",LoadBasis.EXERCISE_OVERRIDE,
            emptySet(),8..25,
            "Active web pec deck uses one verified physical machine resistance scale; cannot borrow chest-press, cable or dumbbell-fly values.",
            direct(TrainingMuscle.CHEST))
    )

    init {
        require(definitions.size==49)
        require(definitions.map {it.legacyCuratedId}.distinct().size==
            definitions.size)
        require(definitions.all {
            it.legacyCuratedId==it.movement.exerciseId &&
                it.movement.creditReviewReference==REVIEW
        })
        require(reviewedRecoveryGroups.keys.all {id ->
            definitions.any {it.legacyCuratedId==id}
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
