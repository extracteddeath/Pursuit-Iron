package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure feasibility checks. The store/owner test separately proves that the
 * source was decoded from real approved, signed-in, owner-fenced storage.
 */
class NativeKotlinApprovedGymCoherenceDomainTest {
    private fun n(x: String)=BigDecimal(x)
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.CABLE_STACK,
        listOf(n("20"),n("25"),n("30"),n("35")))
    private val credits=listOf(DeclaredMuscleCredit(
        TrainingMuscle.TRICEPS,MuscleCreditRole.DIRECT,BigDecimal.ONE))
    private val move=CatalogMovement(
        "rope-pushdown","pushdown","rope","rope",
        ComposerPattern.ISOLATION,ComposerRole.SECONDARY,
        setOf(ComposerFocus.FULL_BODY,ComposerFocus.UPPER),
        "cable-1",LoadBasis.CABLE_STACK,credits,
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"review-1",
        setOf("cable-station"),null,null,1)
    private fun setup():AthleteSetupReview=CoachAthleteSetupDomain.review(
        AthleteSetupDraft(
            profileRevision="profile-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                mapOf("cable-1" to stock),setOf("cable-station"),setOf("rope")),
            allowBodyweight=true,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
    private val source=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",1,setup(),listOf(move))
    private val option=ComposerExerciseOption(
        "rope-pushdown",ComposerPattern.ISOLATION,
        setOf(ComposerFocus.FULL_BODY),
        ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock),
        credits,45,90,2,3,3,3,1,2,role=ComposerRole.SECONDARY)
    private val program=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map { d ->
            ComposerDay(d,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(40,60,null,120))
        },
        listOf(DeclaredMuscleDoseBounds(
            TrainingMuscle.TRICEPS,n("2"),n("8"),n("12"),1)),
        listOf(option),3,1,
        listOf(0,1,2).map { i ->
            ComposerSessionIntent(i,ComposerSessionPurpose.HYPERTROPHY)
        },maximumBarbellExercisesPerDay=0,
        allowDirectTricepsOnLower=false)
    private val approvedSource=KotlinTrainingSourceRevision(
        "athlete-1",1,"program-1",1,"gym-1",1,"catalog-1","rules-1")
    private val approval=NativeKotlinWorkoutApproval(
        "owner-1","athlete-1",approvedSource,approvedSource,
        KotlinWorkoutSelectionGrant("grant-1","workout-1",approvedSource,1),
        program,emptyList(),"receipt-1",1)
    private fun check(
        source:NativeCustomAuthoringSnapshot=this.source,
        approval:NativeKotlinWorkoutApproval=this.approval
    )=NativeKotlinApprovedGymCoherenceDomain.inspect(approval,source)

    @Test fun fullProgramMatchesCurrentConfirmedVariantAndExactPhysicalStock() {
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup().status)
        assertEquals(NativeApprovedGymCoherenceResult.Matched,check())
        val equivalent=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.CABLE_STACK,listOf(n("20.0"),n("25"),n("30"),n("35")))
        assertEquals(NativeApprovedGymCoherenceResult.Matched,
            check(approval=approval.copy(program=program.copy(
                exercises=listOf(option.copy(
                    equipment=ComposerEquipment.Verified(
                        LoadUnit.LB,n("30"),equivalent)))))))
    }

    @Test fun removedOrChangedStockCannotQuietlyPermitAnOldProgramLoad() {
        val athlete=checkNotNull(source.setup.reviewed)
        val removed=source.copy(setup=source.setup.copy(
            reviewed=athlete.copy(gym=athlete.gym.copy(confirmedStock=emptyMap()))))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.PHYSICAL_EQUIPMENT_CHANGED,
            "rope-pushdown"),check(source=removed))
        val other=source.copy(setup=source.setup.copy(
            reviewed=athlete.copy(gym=athlete.gym.copy(
                confirmedStock=mapOf("cable-1" to
                    VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.CABLE_STACK,
                        listOf(n("20"),n("25"),n("30")))))))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.PHYSICAL_EQUIPMENT_CHANGED,
            "rope-pushdown"),check(source=other))
        val differentBasis=source.copy(catalog=listOf(
            move.copy(expectedLoadBasis=LoadBasis.MACHINE_STACK)))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.PHYSICAL_EQUIPMENT_CHANGED,
            "rope-pushdown"),check(source=differentBasis))
    }

    @Test fun missingAttachmentFacilityOrChangedVariantRefusesReview() {
        val athlete=checkNotNull(source.setup.reviewed)
        val noRope=source.copy(setup=source.setup.copy(
            reviewed=athlete.copy(gym=athlete.gym.copy(
                availableAttachments=emptySet()))))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.ATTACHMENT_OR_FACILITY_UNAVAILABLE,
            "rope-pushdown"),check(source=noRope))
        val otherVariant=source.copy(catalog=listOf(
            move.copy(variantId="straight",attachmentId="straight-bar",
                exerciseId="straight-pushdown")))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.UNKNOWN_EXERCISE_VARIANT,
            "rope-pushdown"),check(source=otherVariant))
        val alteredCredits=source.copy(catalog=listOf(move.copy(
            credits=listOf(DeclaredMuscleCredit(
                TrainingMuscle.BRACHIALIS,MuscleCreditRole.DIRECT,BigDecimal.ONE)))))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.CATALOG_INTENT_CHANGED,
            "rope-pushdown"),check(source=alteredCredits))
    }

    @Test fun trainingDayAndAthleteIntentCannotBeOverwrittenByOldProgram() {
        val athlete=checkNotNull(source.setup.reviewed)
        val restDayChanged=source.copy(setup=source.setup.copy(
            reviewed=athlete.copy(trainingWeekdays=setOf(0,1,4))))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.INVALID_SCHEDULE),
            check(source=restDayChanged))
        val excluded=source.copy(setup=source.setup.copy(
            reviewed=athlete.copy(excludedExerciseIds=setOf("rope-pushdown"))))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.EXCLUDED_EXERCISE,
            "rope-pushdown"),check(source=excluded))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.WRONG_ATHLETE_OR_GYM),
            check(approval=approval.copy(
                currentSource=approvedSource.copy(gymId="other-gym"))))
    }

    @Test fun unreviewedAndForeignSourcesCannotAuthorizeNewLoads() {
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.UNREVIEWED_SOURCE),
            check(source=source.copy(setup=source.setup.copy(
                status=AthleteSetupStatus.NEEDS_INPUT))))
        assertEquals(NativeApprovedGymCoherenceResult.Rejected(
            NativeApprovedGymCoherenceRefusal.WRONG_ATHLETE_OR_GYM),
            check(source=source.copy(ownerKey="foreign")))
    }
}
