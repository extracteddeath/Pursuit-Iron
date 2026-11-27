package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Exact source-pinned discovery candidates for the historical web shell.
 * These are NOT receipt migration aliases: historical physical stock, unit,
 * attachments, measurement, and original observation still require review.
 * Never use this lookup for an ordinary native ID or to rewrite saved facts.
 * The CI catalog parity audit checks this projection against all frozen rows.
 */
object HistoricalExerciseIdentityDomain {
    const val PINNED_SHELL_COMMIT="29faa204212a4d54088b4870341350e605939185"

    private val unchangedIds = """
        inc-bb-bench low-inc-bb-bench high-inc-bb-bench db-bench dips-chest assisted-dip
        inc-cable-fly pec-deck smith-bench db-fly pushup lat-pulldown
        seated-row tbar-row machine-row deadlift straight-pulldown inv-row
        superman ohp db-shoulder rear-fly face-pull arnold
        front-raise pike-pushup inc-curl bayesian-curl ez-curl hammer
        bb-curl chinup assisted-chinup band-curl oh-cable-ext pushdown
        rope-pushdown ez-pushdown cgbp skullcrusher db-oh-ext dips-tri
        diamond-pushup bench-dip front-squat bulgarian smith-squat goblet
        walking-lunge bw-squat bw-lunge bw-bulgarian rdl lying-curl
        db-rdl good-morning slrdl nordic hip-thrust db-hip-thrust
        cable-kickback sl-hip-thrust glute-bridge sumo-dl seated-calf smith-calf
        bw-calf hanging-raise leg-raise crunch bicycle russian-twist
        plank cable-woodchop oblique-crunch hanging-oblique serratus-pushup cable-serratus
        banded-tib-raise bb-shrug db-shrug cable-shrug wrist-curl reverse-curl
        farmers decline-bench decline-db-press incline-machine-press floor-press machine-dip
        high-cable-fly incline-pushup decline-pushup cable-press pendlay-row meadows-row
        seal-row inc-db-row wide-pulldown close-pulldown one-arm-pulldown assisted-pullup
        rack-pull kroc-row db-pullover floor-db-pullover bent-db-pullover seated-ohp
        seated-db-press smith-ohp landmine-press machine-lat-raise leaning-lat-raise cable-rear-fly
        cable-front-raise upright-row db-upright-row concentration-curl spider-curl machine-curl
        rope-hammer-curl cross-hammer zottman-curl drag-curl v-bar-pushdown straight-bar-pushdown
        dual-rope-pushdown single-pushdown jm-press tricep-kickback machine-ext ez-oh-ext
        pendulum-squat belt-squat box-squat split-squat step-up reverse-lunge
        single-leg-press sissy-squat stiff-deadlift standing-curl ghr pull-through
        kb-swing machine-hip-thrust smith-hip-thrust hip-abduction cable-abduction reverse-hyper
        frog-pump leg-press-calf donkey-calf single-calf ab-wheel hanging-knee
        machine-crunch decline-situp toes-to-bar side-plank pallof woodchopper
        v-up mountain-climber smith-shrug machine-shrug behind-shrug reverse-wrist-curl
        behind-wrist-curl wrist-roller hammer-radial-dev hammer-ulnar-dev cable-radial-dev band-ulnar-dev
        plate-pinch push-press side-lying-raise single-cable-raise underhand-row underhand-pulldown
        single-cable-row close-cable-row wide-cable-row underhand-cable-row rope-cable-row dual-cable-row
        wide-machine-row neutral-machine-row wide-tbar-row kelso-shrug single-cable-curl incline-hammer
        neck-extension neck-curl neck-harness neck-lateral cable-crossover squeeze-press
        tate-press z-press cable-y-raise bstance-hip-thrust curtsy-lunge lateral-walk
        band-leg-curl jefferson-curl bw-single-calf trap-bar-shrug power-shrug dead-hang
        suitcase-carry dead-bug hollow-hold dragon-flag cable-side-bend copenhagen
        cyclist-squat spanish-squat incline-cable-press smith-incline low-incline-db band-pushup
        svend-press yates-row cs-db-row cable-pullover machine-pullover trap-bar-deadlift
        snatch-deadlift renegade-row band-pulldown cable-upright-row bradford-press viking-press
        rear-band-pull-apart plate-front-raise prone-y-raise ez-cable-curl waiter-curl seated-incline-curl
        rev-grip-curl pinwheel-curl underhand-pushdown pjr-pullover lying-db-ext cable-tri-kickback
        california-press zercher-squat lm-squat heels-up-goblet wall-sit pistol-squat
        v-squat cable-rdl slider-curl band-good-morning single-lying-curl kas-glute-bridge
        band-hip-thrust glute-kickback-machine sumo-squat step-through-lunge hack-calf tibialis-raise
        single-leg-press-calf reverse-crunch cable-reverse-crunch l-sit weighted-plank windshield-wiper
        stir-pot incline-shrug cable-face-shrug cable-wrist-curl gripper towel-hang
        back-ext-45 situp adduction-machine cable-adduction band-adduction cossack-squat
        adductor-sumo standing-cable-abduction side-lying-abduction band-abduction db-floor-press db-neutral-press
        db-incline-fly bodyweight-deficit-pushup helms-row kb-gorilla-row t-bar-chest-supported bodyweight-doorway-row
        doorway-biceps-curl prone-rear-delt-raise db-lu-raise prone-db-rear-fly cable-behind-back-lateral db-6-way-raise
        cable-rear-delt-row bands-face-pull db-deficit-lunge smith-reverse-lunge db-deficit-step-up jefferson-squat
        bodyweight-reverse-nordic bands-squat deficit-db-rdl db-good-morning deficit-barbell-deadlift cable-behind-back-curl
        cable-cross-body-extension bands-tricep-ext db-weighted-crunch bands-chest-press db-incline-shrug high-bar-squat
        low-bar-squat paused-squat pin-squat tempo-squat spoto-press larsen-press
        board-press pin-bench pause-bench paused-deadlift pin-press-ohp barbell-skullover
        seated-db-lat-raise single-db-lat-raise chest-supported-lat-raise seated-cable-lat-raise cuff-cable-lat-raise seated-rear-fly
        single-cable-rear-fly chest-supported-rear-fly high-inc-db-press bottom-half-db-fly deficit-pendlay-row low-to-high-face-pull
        leaning-cable-ohext ad-press-barbell ad-press-smith
    """.trimIndent().split(Regex("\\s+")).toSet()

    private val remappedIds=mapOf(
        "bb-bench" to "barbell_bench",
        "inc-db-press" to "incline_db_press",
        "machine-press" to "machine_press",
        "cable-fly" to "cable_fly",
        "pullup" to "weighted-pullup",
        "chest-row" to "chest-supported-machine-row",
        "bb-row" to "barbell_row",
        "db-row" to "one_arm_db_row",
        "lat-raise" to "db_lateral_raise",
        "cable-lat-raise" to "cable_lateral_raise",
        "band-lateral-raise" to "band_lateral_raise",
        "machine-shoulder" to "machine_ohp",
        "reverse-pec" to "rear_delt_fly",
        "db-curl" to "db_curl",
        "preacher" to "ez-bar-preacher-curl",
        "cable-curl" to "cable_curl",
        "back-squat" to "back_squat",
        "hack-squat" to "hack_squat",
        "leg-press" to "leg_press",
        "leg-ext" to "leg_extension",
        "seated-curl" to "seated_leg_curl",
        "standing-calf" to "standing_calf",
        "db-calf" to "db_calf_raise",
        "cable-crunch" to "cable_crunch",
        "dumbbell-crunch" to "dumbbell_crunch",
        "neutral-pulldown" to "neutral_pulldown",
        "bw-pullup" to "pullup"
    )

    data class Candidate(val originalShellId:String,val nativeExerciseId:String)

    /** Explicit historical provenance only. Unknown or unpinned input is unresolved. */
    fun candidateForPinnedShell(sourceCommit:String,originalShellId:String):Candidate? {
        if(sourceCommit!=PINNED_SHELL_COMMIT)return null
        val nativeId=remappedIds[originalShellId]
            ?:originalShellId.takeIf{it in unchangedIds} ?:return null
        return Candidate(originalShellId,nativeId)
    }

    // Presentation-only reverse lookup. Caller must still supply an already
    // eligible native ID; no historical alias is ever returned as a choice.
    private val aliasesByNativeId=remappedIds.entries.groupBy({it.value},{it.key})
    fun searchAliases(nativeExerciseId:String):List<String> =
        aliasesByNativeId[nativeExerciseId].orEmpty()
}
