package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Convert a FULLY audited first-program prescription to the EXISTING custom
 * training-cycle draft format. It does not create another program owner and
 * it does not approve or issue workouts. The native host must re-review the
 * current athlete/gym source, verify a shown preview and perform a fenced CAS
 * save after explicit athlete confirmation.
 */
enum class FirstProgramDraftStatus {
    READY_FOR_SAVING_DRAFT, INCOMPLETE_SOURCE, UNRESOLVED_PRESCRIPTION,
    CYCLE_REVIEW_FAILED
}
data class FirstProgramDraftReview(
    val status:FirstProgramDraftStatus,
    val cycle:CustomTrainingCycle?,
    val reason:String
) {
    val mayIssueWorkout:Boolean get()=false
    val mayApproveProgram:Boolean get()=false
}
object CoachFirstProgramDraftConversionDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    fun propose(
        reviewed:FirstProgramStartReview,
        setup:AthleteSetupReview,
        curatedCatalog:List<CatalogMovement>,
        cycleId:String,
        title:String,
        firstBlockWeeks:Int
    ):FirstProgramDraftReview {
        fun reject(status:FirstProgramDraftStatus,message:String)=
            FirstProgramDraftReview(status,null,message)
        val athlete=setup.reviewed
        val generated=reviewed.generation
        val prescribed=reviewed.prescription
        val composed=generated?.candidate?.catalogReview?.composedRequest
        val selected=generated?.candidate?.catalogReview?.draft
        val policy=reviewed.policy
        if(athlete==null || !setup.mayReviewProgramCandidate ||
            !id.matches(cycleId) || title.isBlank() || title.length>120 ||
            firstBlockWeeks !in 1..52 || policy==null ||
            policy.athleteId!=athlete.athleteProfileId ||
            policy.athleteRevision!=athlete.profileRevision ||
            policy.goal!=athlete.goal || policy.split!=athlete.split)
            return reject(FirstProgramDraftStatus.INCOMPLETE_SOURCE,
                "The verified athlete, program identity, or starting policy is missing.")
        if(reviewed.status!=FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW ||
            generated==null ||
            generated.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW ||
            prescribed==null || composed==null || selected==null ||
            prescribed.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            prescribed.composer.visits!=selected.visits ||
            prescribed.sessions.size!=athlete.trainingWeekdays.size ||
            prescribed.finalAudit?.satisfiesDeclaredHardConstraints!=true ||
            prescribed.finalAudit?.daysOutsidePreferredWindow?.isNotEmpty()!=false ||
            prescribed.sessions.any {it.exercises.isEmpty() ||
                it.exercises.any {e->e.sets.none {s->
                    s.type==DraftSetType.WORKING
                }} })
            return reject(FirstProgramDraftStatus.UNRESOLVED_PRESCRIPTION,
                "The original generated exercise, muscle-dose, time or set review is incomplete.")
        val days=prescribed.sessions.map {session->
            val day=composed.days.getOrNull(session.dayIndex)
                ?:return reject(FirstProgramDraftStatus.UNRESOLVED_PRESCRIPTION,
                    "A workout day is missing from the generated calendar.")
            CustomTrainingDay(
                day.calendarDay,
                day.focus.name.replace('_',' ').lowercase()
                    .replaceFirstChar{it.uppercase()},
                session.purpose,
                Collections.unmodifiableList(
                    session.exercises.mapIndexed {index,exercise ->
                        CustomExerciseSlot(
                            "first_${day.calendarDay}_${index}",
                            exercise.exerciseId,exercise.role,
                            Collections.unmodifiableList(exercise.sets.map {set ->
                                CustomAuthoredSet(
                                    set.type,set.load,set.targetReps,
                                    set.targetRir,set.activeSeconds,
                                    set.restSeconds)
                            }),
                            pinned=false)
                    }))
        }
        val next=CustomTrainingCycle(
            cycleId,1,athlete.athleteProfileId,athlete.goal,title,
            listOf(CustomTrainingBlock(
                "first-block","Starting block",policy.phase,
                CustomBlockPurpose.TRAINING,firstBlockWeeks,
                CustomSplitIdentity.Named(athlete.split),
                Collections.unmodifiableList(days))),
            authorNote="Provisional ${CoachFirstProgramStartDomain.POLICY_VERSION} plan. Review training responses and actual fatigue before advancing."
        )
        val available=CustomExerciseAuthoringDomain.picker(setup,curatedCatalog)
            .map{it.availability}
        val complete=runCatching {
            CustomTrainingCycleDomain.review(next,available)
        }.getOrNull()
        if(complete?.verifiedDraft==null)
            return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                "The generated week did not pass the independent native custom-cycle inventory/structure review.")
        return FirstProgramDraftReview(
            FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,
            next,
            "Reviewed native draft; still requires full-cycle coaching audit, athlete consent and separate workout approval.")
    }
}
