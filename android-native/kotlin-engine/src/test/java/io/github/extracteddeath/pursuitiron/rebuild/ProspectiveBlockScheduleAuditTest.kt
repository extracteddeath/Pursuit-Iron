package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ProspectiveBlockScheduleAuditTest {
    private val f=GeneratedAdaptiveBlockFixture
    private fun n(x:String)=BigDecimal(x)
    private fun current()=f.cycle().let {source->
        source.copy(blocks=listOf(source.blocks.single(),
            source.blocks.single().copy(blockId="block-2",
                label="Future training",weeks=2)))
    }
    private fun futureReview():AthleteSetupReview {
        val a=checkNotNull(f.athlete().reviewed)
        return CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision=a.profileRevision,
            athleteProfileId=a.athleteProfileId,
            goal=a.goal,experience=a.experience,
            split=ComposerSplit.UL,
            trainingWeekdays=setOf(0,1,2,4),
            timezoneId=a.timezoneId,sessionTimeBucket=a.sessionTimeBucket,
            hardSessionLimit=a.hardSessionLimit,
            gym=GymProfileDraft(a.gym.gymId,a.gym.unit,
                a.gym.confirmedStock,a.gym.availableFacilities,
                a.gym.availableAttachments),
            allowBodyweight=a.allowBodyweight,allowSupersets=a.allowSupersets,
            allowTricepsOnLower=a.allowTricepsOnLower,
            maxBarbellExercisesPerDay=a.maxBarbellExercisesPerDay,
            focusedMuscles=a.focusedMuscles,reducedMuscles=a.reducedMuscles,
            excludedExerciseIds=a.excludedExerciseIds,
            lockedTrainingDays=a.lockedTrainingDays,
            hardTrainingDays=a.hardTrainingDays,
            longTrainingDays=a.longTrainingDays,
            declaredRestDays=setOf(3,5,6)))
    }
    private fun futurePolicy()=checkNotNull(f.generation().policy).copy(
        revision="future-r2",split=ComposerSplit.UL,trainingDays=4,
        minimumPurposeDays=mapOf(ComposerSessionPurpose.HYPERTROPHY to 4))
    private fun future()=ProspectiveBlockCoachingContext(
        futureReview(),futurePolicy(),
        mapOf(0 to 2100,1 to 2100,2 to 2100,4 to 2100))
    private fun auditInputs()=CustomCoachAuditInputs(
        policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to
            checkNotNull(f.generation().policy)),
        nonSetSecondsByWeekday=mapOf(0 to 2100,2 to 2100,4 to 2100),
        continuityPolicy=CustomCoachVolumeContinuityPolicy(
            n("0.5"),n("0.5"),n("0.5"),n("0.5")))
    private fun changed(perExerciseSets:Int=1):CustomTrainingCycle {
        val source=current()
        val next=source.blocks.last()
        val extra=next.days.first().copy(weekday=1,label="New upper day",
            slots=next.days.first().slots.map {slot ->
                slot.copy(sets=List(perExerciseSets) {slot.sets.first()})
            })
        return source.copy(revision=source.revision+1,
            blocks=listOf(source.blocks.first(),next.copy(
                split=CustomSplitIdentity.Named(ComposerSplit.UL),
                days=(next.days+extra).sortedBy {it.weekday})))
    }
    private fun permissions()=CustomCycleCoachPermissions(
        adaptSplit=true,adaptDayCount=true,
        adaptSchedule=true,adaptExercises=true)

    @Test fun futureFourDayBlockAuditsWithoutRewritingCurrentThreeDayWeeks() {
        val source=current()
        val sourceAudit=CustomCycleCoachAuditDomain.review(
            source,f.athlete(),f.movements,auditInputs())
        assertEquals(sourceAudit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            sourceAudit.status)
        val candidate=changed()
        val refused=CustomCycleCoachAuditDomain.review(
            candidate,f.athlete(),f.movements,auditInputs())
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,refused.status)
        assertTrue(refused.issues.any {
            it.kind==CustomCoachIssueKind.ATHLETE_SCHEDULE_CONFLICT
        })
        val approvedForReview=CustomCycleCoachAuditDomain.review(
            candidate,f.athlete(),f.movements,auditInputs(),
            mapOf("block-2" to future()))
        assertEquals(approvedForReview.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            approvedForReview.status)
        assertEquals(setOf("block-2"),approvedForReview.prospectiveBlockIds)
        assertEquals("future-r2",
            approvedForReview.prospectivePolicyRevisions["block-2"])
        assertEquals(listOf(9,9,9,12,12),
            approvedForReview.weeks.map {it.workingSets})
        assertEquals(setOf(0,2,4),
            source.blocks.first().days.map {it.weekday}.toSet())
        assertEquals(4,candidate.blocks.last().days.size)
        assertEquals(source, current())
        assertFalse(approvedForReview.mayPublishApprovedProgram)
        assertFalse(approvedForReview.mayIssueWorkout)
    }

    @Test fun futureOptionsRankSeparatelyFromCurrentlyApprovedChoices() {
        val source=current()
        val candidate=changed()
        val request=AdaptiveCycleOptionRequest(
            source,"block-2",f.athlete(),f.movements,
            auditInputs(),permissions(),
            listOf(AdaptiveCycleOption("keep",
                source.copy(revision=source.revision+1)),
                AdaptiveCycleOption("four_day",candidate)),
            mapOf("four_day" to future()))
        val review=AdaptiveCycleOptionReviewDomain.review(request)
        assertEquals(AdaptiveCycleComparisonStatus.OPTIONS_COMPARED,
            review.status)
        assertEquals("keep",review.recommendedOptionId)
        assertEquals("four_day",review.recommendedProspectiveOptionId)
        assertEquals(AdaptiveCycleOptionStatus.PROSPECTIVE_REVIEW_REQUIRED,
            review.evaluated.single {it.optionId=="four_day"}.status)
        assertNotNull(review.evaluated.single {
            it.optionId=="four_day"
        }.qualityScore)
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.mayApplyAutomatically)
        assertEquals(review,AdaptiveCycleOptionReviewDomain.review(request))
    }

    @Test fun prospectiveScopeCannotAuthorizeOtherAthleteFacts() {
        val source=current()
        val currentProfile=checkNotNull(future().setup.reviewed)
        val invalid=future().copy(
            setup=future().setup.copy(reviewed=currentProfile.copy(
                experience=AthleteExperience.BEGINNER)))
        val reviewed=CustomCycleCoachAuditDomain.review(
            changed(),f.athlete(),f.movements,auditInputs(),
            mapOf("block-2" to invalid))
        assertEquals(CustomCoachAuditStatus.INVALID_SOURCE,reviewed.status)
        assertTrue(reviewed.issues.any {
            it.kind==CustomCoachIssueKind.PROSPECTIVE_CONTEXT_INVALID
        })
        assertFalse(reviewed.mayPublishApprovedProgram)
    }

    @Test fun prospectiveContextDoesNotWeakenVolumeRampCeilings() {
        val original=current()
        val candidate=changed(perExerciseSets=3)
        val reviewed=CustomCycleCoachAuditDomain.review(
            candidate,f.athlete(),f.movements,auditInputs(),
            mapOf("block-2" to future()))
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,reviewed.status)
        assertTrue(reviewed.issues.any {
            it.kind==CustomCoachIssueKind.UNJUSTIFIED_TRAINING_VOLUME_SPIKE ||
                it.kind==CustomCoachIssueKind.DIRECT_MUSCLE_SPIKE
        })
        assertEquals(9,reviewed.weeks[2].workingSets)
        assertEquals(18,reviewed.weeks[3].workingSets)
    }
}
