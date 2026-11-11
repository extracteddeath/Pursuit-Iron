package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * A bounded bridge from authentic, day-specific exercise coaching to ONE
 * unissued future week override. It reuses the existing rep/load amendment,
 * builder and whole-cycle auditor. It never estimates fatigue itself,
 * invents weights, changes issued workouts or claims a saved history grant.
 *
 * Caller MUST construct [coachingRequest.completed] from the complete,
 * owner-authenticated journal and validate lastCompletedAbsoluteWeek against
 * that same owner ledger. These arguments are not authentication proofs.
 * Separate durable owner consent and revision fencing are always required.
 */
data class ReviewedFutureWeekProgressionRequest(
    val approvedCycle:CustomTrainingCycle,
    val setup:AthleteSetupReview,
    val catalog:List<CatalogMovement>,
    val coachPolicy:CustomCoachAuditInputs,
    val catalogSourceRevision:Long,
    val lastCompletedAbsoluteWeek:Int,
    val coachingRequest:CustomNextWorkoutCoachingRequest,
    val permissions:CustomCycleCoachPermissions,
    /** Trusted caller supplies the latest owner-verified roster, never UI input. */
    val verifiedMachineConfigurations:List<KotlinGymMachineAlternatives>?=null
)

enum class ReviewedFutureWeekProgressionStatus {
    UNVERIFIED_SOURCE, NOT_NEXT_UNISSUED_WEEK,
    ALREADY_CUSTOMIZED, PERMISSION_REQUIRED,
    PHYSICAL_MACHINE_REVIEW_REQUIRED, HOLD_WITHOUT_AMENDMENT,
    AMENDMENT_UNAVAILABLE, FULL_CYCLE_AUDIT_FAILED,
    CANDIDATE_FOR_ATHLETE_REVIEW
}

data class ReviewedFutureWeekProgression(
    val status:ReviewedFutureWeekProgressionStatus,
    val coaching:CustomNextWorkoutCoachingReview?,
    val candidate:CustomTrainingCycle?,
    val candidateAudit:CustomCoachAudit?,
    val changedAbsoluteWeek:Int?,
    val changedSlotId:String?,
    val explanation:String
) {
    val maySaveProgram:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayChangeWorkoutHistory:Boolean get()=false
}

object ReviewedFutureWeekProgressionDomain {
    internal fun requiresPhysicalMachineIdentity(basis:LoadBasis?):Boolean =
        basis in setOf(LoadBasis.MACHINE_STACK,LoadBasis.CABLE_STACK,
            LoadBasis.SMITH_TOTAL,LoadBasis.EXERCISE_OVERRIDE)

    /** A bound station's attainable stack, never the generic exercise stock. */
    internal fun confirmedBoundMachineStock(
        gymId:String,exercise:CatalogMovement,machineInstanceId:String?,
        configurations:List<KotlinGymMachineAlternatives>?
    ):VerifiedLoadStock? {
        val machineId=machineInstanceId ?:return null
        val basis=exercise.expectedLoadBasis
        if(!requiresPhysicalMachineIdentity(basis))return null
        val config=configurations?.let{
            KotlinMachineAlternativesDomain.forGymExercise(
                it,gymId,exercise.exerciseId)
        } ?:return null
        return KotlinMachineAlternativesDomain.confirmedStack(
            config,machineId,basis ?:return null)
    }

    fun propose(request:ReviewedFutureWeekProgressionRequest):
        ReviewedFutureWeekProgression {
        fun refuse(
            status:ReviewedFutureWeekProgressionStatus,
            explanation:String,
            coaching:CustomNextWorkoutCoachingReview?=null,
            audit:CustomCoachAudit?=null
        )=ReviewedFutureWeekProgression(status,coaching,null,audit,
            null,null,explanation)

        val current=request.approvedCycle
        val next=request.coachingRequest.next
        val slot=next.slots.singleOrNull {
            it.slotId==request.coachingRequest.slotId
        }
        if(!request.setup.mayReviewProgramCandidate ||
            request.catalogSourceRevision<1 ||
            current.revision !in 1..9_007_199_254_740_990L ||
            next.stamp.athleteId!=current.athleteId ||
            next.stamp.cycleId!=current.cycleId ||
            next.stamp.cycleRevision!=current.revision ||
            slot==null || next.prescriptionEditedForTime ||
            next.blockPurpose!=CustomBlockPurpose.TRAINING)
            return refuse(ReviewedFutureWeekProgressionStatus.UNVERIFIED_SOURCE,
                "A current complete, non-shortened custom training source is required.")

        if(request.lastCompletedAbsoluteWeek<0 ||
            next.absoluteWeek!=request.lastCompletedAbsoluteWeek+1)
            return refuse(ReviewedFutureWeekProgressionStatus.NOT_NEXT_UNISSUED_WEEK,
                "Only the immediately following unissued cycle week may be adjusted.")

        // A completed workout on another training day must not make an
        // older successful session look like fresh evidence for this day.
        // Require exactly one completed appearance of the selected same
        // day in the immediately preceding week, even across old signed
        // cycle revisions. The owner adapter supplies the full ledger.
        val previousSameDay=request.coachingRequest.completed.count{
            it.snapshot.stamp.cycleId==current.cycleId &&
                it.snapshot.blockId==next.blockId &&
                it.snapshot.weekday==next.weekday &&
                it.snapshot.absoluteWeek==next.absoluteWeek-1
        }
        if(previousSameDay!=1)
            return refuse(ReviewedFutureWeekProgressionStatus.HOLD_WITHOUT_AMENDMENT,
                "Review the immediately preceding same-day workout before progressing this slot; a missed or duplicate visit cannot earn an increase.")

        // Signed native preflight always includes the audited per-day
        // non-set overhead. A raw preview omits it and cannot equal the
        // authenticated next session even when the training is identical.
        // Re-derive the *consent-reviewed* workout, never relax equality.
        val original=(CustomWorkoutSnapshotDomain.reviewForConsent(
            current,request.setup,request.catalog,
            request.catalogSourceRevision,request.coachPolicy,
            next.absoluteWeek,next.weekday)
            as? CustomWorkoutSnapshotResult.Preview)?.snapshot
        if(original==null || original!=next)
            return refuse(ReviewedFutureWeekProgressionStatus.UNVERIFIED_SOURCE,
                "The proposed next workout differs from the re-derived full source and gym.")

        val sourceAudit=CustomCycleCoachAuditDomain.review(
            current,request.setup,request.catalog,request.coachPolicy)
        if(sourceAudit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW)
            return refuse(ReviewedFutureWeekProgressionStatus.UNVERIFIED_SOURCE,
                "The current complete cycle has unresolved dose, recovery, schedule or inventory audits.",
                audit=sourceAudit)

        if(current.weekOverrides[next.blockId].orEmpty().any {
            it.weekInBlock==next.weekInBlock &&
                it.weekday==next.weekday && it.slotId==slot.slotId
        })
            return refuse(ReviewedFutureWeekProgressionStatus.ALREADY_CUSTOMIZED,
                "An existing athlete-authored week override cannot be silently replaced.",
                audit=sourceAudit)

        val exercise=request.catalog.singleOrNull {it.exerciseId==slot.exerciseId}
            ?:return refuse(ReviewedFutureWeekProgressionStatus.UNVERIFIED_SOURCE,
                "The selected exercise is missing from the reviewed catalog.",
                audit=sourceAudit)
        val physical=requiresPhysicalMachineIdentity(exercise.expectedLoadBasis)
        val stock=if(physical)confirmedBoundMachineStock(
            next.stamp.gymId,exercise,slot.machineInstanceId,
            request.verifiedMachineConfigurations)
        else exercise.equipmentId?.let {
            request.setup.reviewed?.gym?.confirmedStock?.get(it)
        }
        // A custom cycle now persists physical A/B slot bindings. Only the
        // EXACT bound station's separately calibrated inventory and same-day,
        // same-station history may support its next-week amendment.
        if(physical && (slot.machineInstanceId==null ||
            request.coachingRequest.requestedMachineInstanceId!=
                slot.machineInstanceId || stock==null))
            return refuse(
                ReviewedFutureWeekProgressionStatus.PHYSICAL_MACHINE_REVIEW_REQUIRED,
                "This cycle slot must bind an available, calibrated physical station before receiving a machine-specific change.",
                audit=sourceAudit)
        if(request.coachingRequest.verifiedStock!=stock)
            return refuse(ReviewedFutureWeekProgressionStatus.UNVERIFIED_SOURCE,
                "The confirmed load inventory changed or is not the selected physical station.",
                audit=sourceAudit)

        if(!request.permissions.adaptWeekOverrides)
            return refuse(ReviewedFutureWeekProgressionStatus.PERMISSION_REQUIRED,
                "Future week overrides must be explicitly enabled.",
                audit=sourceAudit)

        val coaching=CustomNextWorkoutCoachingDomain.review(
            request.coachingRequest)
        val kind=coaching.status
        if(kind!=CustomNextWorkoutCoachingStatus.BUILD_REPS &&
            kind!=CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW)
            return refuse(ReviewedFutureWeekProgressionStatus.HOLD_WITHOUT_AMENDMENT,
                coaching.explanation,coaching,sourceAudit)

        val modified:CustomWorkoutDaySnapshot=when(kind) {
            CustomNextWorkoutCoachingStatus.BUILD_REPS -> {
                if(!request.permissions.adaptReps)
                    return refuse(ReviewedFutureWeekProgressionStatus.PERMISSION_REQUIRED,
                        "Rep adaptation has not been enabled.",coaching,sourceAudit)
                val result=CustomNextRepAmendmentDomain.propose(
                    next,slot.slotId,coaching)
                (result as? CustomNextRepAmendmentResult.Proposed)
                    ?.amendment?.amended
            }
            CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW -> {
                if(!request.permissions.adaptLoads)
                    return refuse(ReviewedFutureWeekProgressionStatus.PERMISSION_REQUIRED,
                        "Load adaptation has not been enabled.",coaching,sourceAudit)
                val result=CustomNextLoadAmendmentDomain.propose(
                    next,slot.slotId,coaching,stock,
                    request.coachingRequest.maximumAuthorizedIncrease)
                (result as? CustomNextLoadAmendmentResult.Proposed)
                    ?.amendment?.amended
            }
            else ->null
        } ?:return refuse(ReviewedFutureWeekProgressionStatus.AMENDMENT_UNAVAILABLE,
            "The original verified per-set amendment could not be reconstructed.",
            coaching,sourceAudit)

        val replacement=modified.slots.singleOrNull {
            it.slotId==slot.slotId
        }?.sets?.map {it.prescription}
        if(replacement==null ||
            modified.slots.filterNot{it.slotId==slot.slotId}!=
                next.slots.filterNot{it.slotId==slot.slotId})
            return refuse(ReviewedFutureWeekProgressionStatus.AMENDMENT_UNAVAILABLE,
                "An amendment must retain every unrelated exercise and target.",
                coaching,sourceAudit)

        val changed=CustomCycleBuilderDomain.editWeek(
            current,current.revision,next.blockId,next.weekInBlock,
            next.weekday,slot.slotId,replacement
        ) as? CustomCycleBuilderResult.Proposed
            ?:return refuse(ReviewedFutureWeekProgressionStatus.AMENDMENT_UNAVAILABLE,
                "The source revision or authored slot changed.",coaching,sourceAudit)
        val candidate=changed.draft
        val candidateAudit=CustomCycleCoachAuditDomain.review(
            candidate,request.setup,request.catalog,request.coachPolicy)
        if(candidateAudit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW)
            return refuse(ReviewedFutureWeekProgressionStatus.FULL_CYCLE_AUDIT_FAILED,
                "The proposed amendment would invalidate a complete-cycle coaching constraint: "+
                    candidateAudit.issues.take(3).joinToString("; "){it.explanation},
                coaching,candidateAudit)

        val repreview=(CustomWorkoutSnapshotDomain.reviewForConsent(
            candidate,request.setup,request.catalog,
            request.catalogSourceRevision,request.coachPolicy,
            next.absoluteWeek,next.weekday)
            as? CustomWorkoutSnapshotResult.Preview)?.snapshot
        if(repreview==null ||
            repreview!=modified.copy(stamp=modified.stamp.copy(
                cycleRevision=candidate.revision)))
            return refuse(ReviewedFutureWeekProgressionStatus.AMENDMENT_UNAVAILABLE,
                "The saved-cycle projection differs from the reviewed amendment.",
                coaching,candidateAudit)

        return ReviewedFutureWeekProgression(
            ReviewedFutureWeekProgressionStatus.CANDIDATE_FOR_ATHLETE_REVIEW,
            coaching,candidate,candidateAudit,next.absoluteWeek,slot.slotId,
            "An exact, evidence-backed single-week option passed full-cycle review. The athlete must explicitly approve it through the current owner-fenced native workflow; no workout or history has changed.")
    }
}
