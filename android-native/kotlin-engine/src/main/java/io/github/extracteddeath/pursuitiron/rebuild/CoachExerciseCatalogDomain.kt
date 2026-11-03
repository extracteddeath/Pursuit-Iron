package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Explicit exercise catalog input for the headless weekly composer.
 *
 * Stable exercise/variant identity, physical implements, attachments and
 * muscle credits are source data. None may be guessed from a display name.
 * "Reviewed" means documented catalog author review, NOT independently
 * established physiological truth or medical validation.
 */
enum class CatalogCreditStatus { UNREVIEWED, AUTHOR_REVIEWED_PROVISIONAL }

data class CatalogMovement(
    val exerciseId: String,
    val familyId: String,
    val variantId: String,
    val attachmentId: String?,
    val pattern: ComposerPattern,
    val role: ComposerRole,
    val permittedFocus: Set<ComposerFocus>,
    /** Null for explicitly load-free bodyweight movements. */
    val equipmentId: String?,
    val expectedLoadBasis: LoadBasis?,
    val credits: List<DeclaredMuscleCredit>,
    val creditStatus: CatalogCreditStatus,
    /** Must identify the documented coefficients version, not just a name. */
    val creditReviewReference: String?,
    /** Required physical fixtures beyond the loaded implement (bench, rack). */
    val requiredFacilityIds: Set<String> = emptySet()
)

data class CatalogExerciseChoice(
    val exerciseId: String,
    /** Null ONLY for explicitly bodyweight exercises. */
    val selectedLoad: BigDecimal?,
    val activeSecondsPerSet: Int,
    val restSecondsPerSet: Int,
    val minimumSetsPerVisit: Int,
    val maximumSetsPerVisit: Int,
    val maximumVisits: Int,
    val requiredVisits: Int,
    val minimumCalendarDayGap: Int,
    val priority: Int,
    val recoveryGroup: String? = null,
    val minimumGroupCalendarDayGap: Int = 1
)

data class CatalogGym(
    /** Exact implement identifiers, not generic free-text equipment names. */
    val confirmedStock: Map<String,VerifiedLoadStock>,
    val availableAttachmentIds: Set<String>,
    val availableFacilityIds: Set<String> = emptySet()
)

data class CatalogWeekRequest(
    /**
     * The independent phase, schedule and dose bounds. Exercise choices
     * must be EMPTY so callers cannot smuggle an unreviewed option.
     */
    val template: ComposerRequest,
    val catalog: List<CatalogMovement>,
    val chosen: List<CatalogExerciseChoice>,
    val gym: CatalogGym
)

enum class CatalogIssueType {
    UNKNOWN_EXERCISE, CREDIT_REVIEW_MISSING, ATTACHMENT_UNAVAILABLE,
    EQUIPMENT_UNAVAILABLE, EQUIPMENT_BASIS_MISMATCH, LOAD_UNVERIFIED,
    MISSING_PRIMARY_MOVEMENT, INCOMPATIBLE_EXERCISE_IDENTITY,
    FACILITY_UNAVAILABLE
}

data class CatalogIssue(
    val exerciseId: String,
    val kind: CatalogIssueType,
    val explanation: String
)

enum class CatalogWeekStatus {
    VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
    CATALOG_OR_GYM_UNRESOLVED,
    WEEK_CONSTRAINTS_UNRESOLVED
}

data class CatalogWeekReview(
    val status: CatalogWeekStatus,
    val requestedExerciseIds: List<String>,
    val issues: List<CatalogIssue>,
    val composedRequest: ComposerRequest?,
    val draft: ComposerDraft?
) {
    /** Catalog correctness is one gate only; never a workout authority. */
    val mayIssueWorkout: Boolean get() = false
    val mayOverwriteAuthoredProgram: Boolean get() = false
}

object CoachExerciseCatalogDomain {
    private val identifier=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,54}$")
    private val zero=BigDecimal.ZERO
    private val maxLoad=BigDecimal("100000")

    private fun canonical(value:BigDecimal)=BigDecimal(
        value.stripTrailingZeros().toPlainString())

    private fun validLoad(value:BigDecimal)=value>=zero &&
        value<=maxLoad && value.stripTrailingZeros().scale()<=3

    private fun validId(value:String?)=value!=null && identifier.matches(value)

    private fun validate(request:CatalogWeekRequest) {
        require(request.template.exercises.isEmpty()) {
            "Exercise choices must come from the catalog only"
        }
        // Validate authored primary references before indexing day/focus.
        require(request.template.sessionIntents.all {
            it.dayIndex in request.template.days.indices
        })
        require(request.catalog.size in 1..256)
        require(request.chosen.size in 1..32)
        require(request.catalog.map {it.exerciseId}.distinct().size==
            request.catalog.size)
        require(request.chosen.map {it.exerciseId}.distinct().size==
            request.chosen.size)
        require(request.catalog.map {it.familyId to it.variantId}.distinct().size==
            request.catalog.size) {
            "Variant IDs must be unique inside a movement family"
        }
        require(request.gym.confirmedStock.size<=128)
        require(request.gym.confirmedStock.keys.all(::validId))
        require(request.gym.availableAttachmentIds.size<=128)
        require(request.gym.availableAttachmentIds.all(::validId))
        require(request.gym.availableFacilityIds.size<=128)
        require(request.gym.availableFacilityIds.all(::validId))
        for(spec in request.catalog) {
            require(validId(spec.exerciseId) && validId(spec.familyId) &&
                validId(spec.variantId) &&
                (spec.attachmentId==null || validId(spec.attachmentId)))
            require((spec.equipmentId==null)==(spec.expectedLoadBasis==null))
            require(spec.equipmentId==null || validId(spec.equipmentId))
            require(spec.permittedFocus.isNotEmpty())
            require(spec.requiredFacilityIds.size<=8)
            require(spec.requiredFacilityIds.all(::validId))
            require(spec.credits.isNotEmpty() &&
                spec.credits.size<=TrainingMuscle.entries.size)
            require(spec.credits.map {it.muscle}.distinct().size==spec.credits.size)
            require(spec.credits.all {c ->
                c.fractionalSet>zero && c.fractionalSet<=BigDecimal.ONE &&
                    c.fractionalSet.stripTrailingZeros().scale()<=3
            })
            require(spec.creditReviewReference==null ||
                validId(spec.creditReviewReference))
        }
        for(choice in request.chosen) {
            require(validId(choice.exerciseId))
            require(choice.selectedLoad==null || validLoad(choice.selectedLoad))
            require(choice.activeSecondsPerSet in 1..3600 &&
                choice.restSecondsPerSet in 0..3600 &&
                choice.minimumSetsPerVisit in 1..8 &&
                choice.maximumSetsPerVisit in choice.minimumSetsPerVisit..12 &&
                choice.maximumVisits in 1..request.template.days.size &&
                choice.requiredVisits in 0..choice.maximumVisits &&
                choice.minimumCalendarDayGap in 1..6 &&
                choice.priority in 1..10 &&
                (choice.recoveryGroup==null || validId(choice.recoveryGroup)) &&
                choice.minimumGroupCalendarDayGap in 1..6)
        }
    }

    fun review(request:CatalogWeekRequest):CatalogWeekReview {
        validate(request)
        val byId=request.catalog.associateBy {it.exerciseId}
        val issues=ArrayList<CatalogIssue>()
        val options=ArrayList<ComposerExerciseOption>()
        for(choice in request.chosen) {
            val spec=byId[choice.exerciseId]
            if(spec==null) {
                issues+=CatalogIssue(choice.exerciseId,CatalogIssueType.UNKNOWN_EXERCISE,
                    "Exercise identity is absent from the supplied catalog.")
                continue
            }
            if(spec.creditStatus!=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                spec.creditReviewReference==null) {
                issues+=CatalogIssue(spec.exerciseId,CatalogIssueType.CREDIT_REVIEW_MISSING,
                    "Muscle credit coefficients lack a traceable catalog review.")
            }
            if(!request.gym.availableFacilityIds.containsAll(spec.requiredFacilityIds)) {
                issues+=CatalogIssue(spec.exerciseId,CatalogIssueType.FACILITY_UNAVAILABLE,
                    "The supporting bench, rack, or other required fixture is not confirmed.")
            }
            if(spec.attachmentId!=null &&
                spec.attachmentId !in request.gym.availableAttachmentIds) {
                issues+=CatalogIssue(spec.exerciseId,CatalogIssueType.ATTACHMENT_UNAVAILABLE,
                    "The selected attachment is not confirmed in this gym.")
            }
            val equipment=if(spec.equipmentId==null) {
                if(choice.selectedLoad!=null) {
                    issues+=CatalogIssue(spec.exerciseId,
                        CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY,
                        "Bodyweight-only variants cannot accept a numeric working load.")
                }
                ComposerEquipment.Bodyweight
            } else {
                val stock=request.gym.confirmedStock[spec.equipmentId]
                if(stock==null) {
                    issues+=CatalogIssue(spec.exerciseId,
                        CatalogIssueType.EQUIPMENT_UNAVAILABLE,
                        "The required implement is not confirmed in this gym.")
                } else if(stock.basis!=spec.expectedLoadBasis) {
                    issues+=CatalogIssue(spec.exerciseId,
                        CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,
                        "The implement uses a different weight basis than this variant.")
                }
                if(choice.selectedLoad==null) {
                    issues+=CatalogIssue(spec.exerciseId,CatalogIssueType.LOAD_UNVERIFIED,
                        "A working load cannot be inferred from equipment availability.")
                } else {
                    val inspection=EquipmentLoadDomain.inspect(
                        stock,choice.selectedLoad,stock?.unit ?: LoadUnit.LB)
                    if(inspection !is LoadInspection.Confirmed || !inspection.exact) {
                        issues+=CatalogIssue(spec.exerciseId,
                            CatalogIssueType.LOAD_UNVERIFIED,
                            "The specified working load is not physically confirmed.")
                    }
                }
                // If the exact equipment is missing the whole request fails;
                // this placeholder is never forwarded to the composer.
                ComposerEquipment.Verified(
                    stock?.unit ?: LoadUnit.LB,
                    choice.selectedLoad ?: zero,stock)
            }
            options+=ComposerExerciseOption(
                spec.exerciseId,spec.pattern,spec.permittedFocus,equipment,
                spec.credits.toList(),choice.activeSecondsPerSet,
                choice.restSecondsPerSet,choice.minimumSetsPerVisit,
                choice.maximumSetsPerVisit,choice.maximumVisits,
                choice.requiredVisits,choice.minimumCalendarDayGap,
                choice.priority,choice.recoveryGroup,
                choice.minimumGroupCalendarDayGap,spec.role)
        }
        val missingPrimary=request.template.sessionIntents.mapNotNull {intent->
            val required=intent.requiredPrimaryExerciseId ?:return@mapNotNull null
            if(options.none {it.exerciseId==required &&
                    it.role==ComposerRole.PRIMARY &&
                    request.template.days[intent.dayIndex].focus in it.allowedFocus})
                required else null
        }
        for(id in missingPrimary)issues+=CatalogIssue(
            id,CatalogIssueType.MISSING_PRIMARY_MOVEMENT,
            "The authored primary lift cannot be silently substituted.")
        if(issues.isNotEmpty())return CatalogWeekReview(
            CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,
            Collections.unmodifiableList(request.chosen.map {it.exerciseId}),
            Collections.unmodifiableList(issues),null,null)

        val composedRequest=request.template.copy(
            exercises=Collections.unmodifiableList(options))
        val draft=CoachWeekComposer.compose(composedRequest)
        return CatalogWeekReview(
            if(draft.status==ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW)
                CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW
            else CatalogWeekStatus.WEEK_CONSTRAINTS_UNRESOLVED,
            Collections.unmodifiableList(request.chosen.map {it.exerciseId}),
            emptyList(),composedRequest,draft)
    }
}
