package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinCustomAuthoringDraftCoordinatorTest {
    @get:Rule val temp=TemporaryFolder()
    private var active:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity { active }
    private val fence by lazy {
        NativeKotlinApprovalRevisionFence(temp.root.toPath())
    }
    private val sources by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(
            temp.root.toPath(),fence,identity)
    }
    private val drafts by lazy {
        NativeCustomCycleDraftFileStore(temp.root.toPath(),fence,identity)
    }
    private val coordinator by lazy {
        NativeKotlinCustomAuthoringDraftCoordinator(
            sources,drafts,fence,identity)
    }
    private fun reviewed():AthleteSetupReview =
        CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                mapOf("cable" to VerifiedLoadStock.Totals(
                    LoadUnit.LB,LoadBasis.CABLE_STACK,
                    listOf(BigDecimal("20"),BigDecimal("25")))),
                setOf("cable-station"),setOf("rope")),
            allowBodyweight=true,allowSupersets=true,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
    private val pushdown=CatalogMovement(
        "rope-pushdown","pushdown","rope","rope",
        ComposerPattern.ISOLATION,ComposerRole.SECONDARY,
        setOf(ComposerFocus.FULL_BODY),"cable",LoadBasis.CABLE_STACK,
        listOf(DeclaredMuscleCredit(TrainingMuscle.TRICEPS,
            MuscleCreditRole.DIRECT,BigDecimal.ONE)),
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"source-1",
        setOf("cable-station"))
    private fun source(revision:Long)=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",revision,reviewed(),listOf(pushdown))
    private fun blank()=CustomCycleBuilderDomain.blank(
        "cycle-1","athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "My custom plan","base",4,TrainingPhase.HYPERTROPHY,
        CustomSplitIdentity.Freeform("Upper / Arms"))!!

    @Test fun staleGymSourceRevisionCannotWriteEvenWhenDraftCASMatches() {
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,
            reviewed().status)
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source(1)))
        val base=blank()
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,base))
        val add=(CustomCycleBuilderDomain.addDay(
            base,1,"base",CustomTrainingDay(0,"Upper",
                ComposerSessionPurpose.HYPERTROPHY,emptyList()))
            as CustomCycleBuilderResult.Proposed).draft
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            coordinator.saveVerifiedExerciseEdit(
                "owner-1",add,1,1))
        assertEquals(NativeCustomDraftReadResult.Found(add),
            drafts.read("owner-1","cycle-1"))

        // Gym/catalog editor changes stock revision before author saves again.
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        val edit=(CustomCycleBuilderDomain.renameDay(
            add,2,"base",0,"New upper")
            as CustomCycleBuilderResult.Proposed).draft
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            coordinator.saveVerifiedExerciseEdit("owner-1",edit,2,1))
        assertEquals(NativeCustomDraftReadResult.Found(add),
            drafts.read("owner-1","cycle-1"))
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            coordinator.saveVerifiedExerciseEdit("owner-1",edit,2,2))
        assertEquals(NativeCustomDraftReadResult.Found(edit),
            drafts.read("owner-1","cycle-1"))
    }

    @Test fun revocationAndIdentityChangeBlockAuthoringButKeepDraftHistory() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source(1)))
        val base=blank()
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,base))
        val next=(CustomCycleBuilderDomain.addDay(
            base,1,"base",CustomTrainingDay(0,"Arms",
                ComposerSessionPurpose.HYPERTROPHY,emptyList()))
            as CustomCycleBuilderResult.Proposed).draft
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.revoke("owner-1",1))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            coordinator.saveVerifiedExerciseEdit("owner-1",next,1,1))
        assertEquals(NativeCustomDraftReadResult.Found(base),
            drafts.read("owner-1","cycle-1"))
        assertEquals(NativeAuthoringSourceWrite.Committed(3),
            sources.publish("owner-1",2,source(3)))
        active=NativeKotlinOwnerIdentity("owner-1","other-athlete")
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            coordinator.saveVerifiedExerciseEdit("owner-1",next,1,3))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            coordinator.saveVerifiedExerciseEdit("owner-1",next,1,3))
    }

    @Test fun missingSourceCanNeverAuthorizeExerciseEdit() {
        val base=blank()
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,base))
        val next=(CustomCycleBuilderDomain.addDay(
            base,1,"base",CustomTrainingDay(0,"Arms",
                ComposerSessionPurpose.HYPERTROPHY,emptyList()))
            as CustomCycleBuilderResult.Proposed).draft
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            coordinator.saveVerifiedExerciseEdit("owner-1",next,1,1))
        assertEquals(NativeCustomDraftReadResult.Found(base),
            drafts.read("owner-1","cycle-1"))
    }
}
