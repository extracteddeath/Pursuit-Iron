package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class CustomWorkoutSnapshotDomainTest {
    private fun setup(
        bodyweight:Boolean=true,
        supersets:Boolean=true
    ):AthleteSetupReview=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="setup-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,
        trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
        gym=GymProfileDraft("gym-1",LoadUnit.LB,
            emptyMap(),emptySet(),emptySet()),
        allowBodyweight=bodyweight,allowSupersets=supersets,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(1,3,5,6)
    ))
    private fun set(reps:Int=10,amplifier:CustomSetAmplifier?=null)=
        CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.ExplicitBodyweight,RepBand(reps,reps+2),
            2,50,90,amplifier)
    private fun day(weekday:Int)=CustomTrainingDay(
        weekday,"Custom $weekday",ComposerSessionPurpose.HYPERTROPHY,
        listOf(
            CustomExerciseSlot("crunch-a-$weekday",
                "bodyweight_crunch",ComposerRole.ACCESSORY,
                listOf(set()),pinned=true,
                coachNote="Strict form, no momentum"),
            CustomExerciseSlot("crunch-b-$weekday",
                "bodyweight_crunch",ComposerRole.ACCESSORY,
                listOf(set(12)),pinned=false,
                coachNote="Different slot, same movement")
        ))
    private fun cycle()=CustomTrainingCycle(
        "cycle-1",1,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "Athlete authored",
        listOf(CustomTrainingBlock(
            "base","Hypertrophy",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Freeform("My custom"),
            listOf(day(0),day(2),day(4))
        ))
    )
    private fun preview(cycle:CustomTrainingCycle=cycle(),
        setup:AthleteSetupReview=setup(),week:Int=1,weekday:Int=0)=
        CustomWorkoutSnapshotDomain.preview(
            cycle,setup,StarterExerciseTranche.movements(),1,
            week,weekday)

    @Test fun duplicateExerciseVisitsKeepDifferentStableSetKeys() {
        val result=preview()
        assertTrue(result.toString(),result is CustomWorkoutSnapshotResult.Preview)
        val snapshot=(result as CustomWorkoutSnapshotResult.Preview).snapshot
        assertEquals(listOf("crunch-a-0","crunch-b-0"),
            snapshot.slots.map{it.slotId})
        assertEquals(listOf("bodyweight_crunch","bodyweight_crunch"),
            snapshot.slots.map{it.exerciseId})
        assertEquals(listOf(
            "cycle-1:w1:d0:crunch-a-0:s1",
            "cycle-1:w1:d0:crunch-b-0:s1"
        ),snapshot.slots.flatMap{it.sets}.map{it.setKey})
        assertEquals(280L,snapshot.knownSetAndRestSeconds)
        assertEquals(null,snapshot.declaredNonSetSeconds)
        assertEquals("Strict form, no momentum",snapshot.slots.first().coachNote)
        assertEquals(false,snapshot.slots.last().pinned)
        assertEquals(CustomSplitIdentity.Freeform("My custom"),snapshot.split)
        assertFalse(snapshot.isIssued)
        assertFalse(snapshot.mayRecordSets)
        assertFalse(snapshot.mayFinishWorkout)
    }

    @Test fun weekOverrideAndAdvancedFollowUpStayAttachedToExactSet() {
        val advanced=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,18)
        val version=cycle().copy(weekOverrides=mapOf(
            "base" to listOf(CustomWeekSlotOverride(
                2,0,"crunch-a-0",listOf(set(16,advanced))))))
        val first=(preview(version,week=1) as
            CustomWorkoutSnapshotResult.Preview).snapshot
        val second=(preview(version,week=2) as
            CustomWorkoutSnapshotResult.Preview).snapshot
        val third=(preview(version,week=3) as
            CustomWorkoutSnapshotResult.Preview).snapshot
        assertEquals(10,first.slots.first().sets.first()
            .prescription.reps.minimum)
        assertEquals(16,second.slots.first().sets.first()
            .prescription.reps.minimum)
        assertEquals(10,third.slots.first().sets.first()
            .prescription.reps.minimum)
        assertEquals(advanced,second.slots.first().sets.first()
            .prescription.amplifier)
        assertEquals(advanced.kind,second.slots.first().sets.first()
            .prescription.amplifier?.kind)
        assertEquals(298L,second.knownSetAndRestSeconds)
        assertEquals("cycle-1:w2:d0:crunch-a-0:s1",
            second.slots.first().sets.first().setKey)
        assertEquals(10,version.blocks.single().days.first().slots.first()
            .sets.single().reps.minimum)
    }

    @Test fun exactSupersetMembershipAndSetTargetsSurviveResolution() {
        val original=cycle()
        val pair=original.blocks.first().days.first().slots.map{
            it.copy(supersetGroup="ss-1")
        }
        val copy=original.copy(blocks=listOf(
            original.blocks.single().copy(days=listOf(
                original.blocks.single().days.first().copy(slots=pair),
                day(2),day(4)))))
        val snapshot=(preview(copy) as CustomWorkoutSnapshotResult.Preview).snapshot
        assertEquals(setOf("ss-1"),snapshot.slots.map{it.supersetGroup}.toSet())
        assertEquals(2,snapshot.slots.size)
        assertEquals(2,snapshot.slots.first().sets.single().prescription.targetRir)
        assertEquals(90,snapshot.slots.first().sets.single().prescription.restSeconds)
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.INVALID_DAY),
            preview(copy,setup(supersets=false)))
    }

    @Test fun wrongWeekSpoofedRoleAndOtherMissingGymBlockPreview() {
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.INVALID_WEEK),preview(week=4))
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.INVALID_DAY),preview(weekday=1))
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.UNREVIEWED_ATHLETE),preview(setup=setup(
                bodyweight=false)))
        val base=cycle()
        val wrong=base.copy(blocks=listOf(base.blocks.single().copy(
            days=base.blocks.single().days.map{ day->
                day.copy(slots=day.slots.map{ slot->
                    slot.copy(role=ComposerRole.PRIMARY)
                })
            })))
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.INCOMPATIBLE_VARIANT),preview(wrong))
    }

    @Test fun wholeCycleReviewMustSucceedBeforeConsentBoundSnapshot() {
        val missing=CustomWorkoutSnapshotDomain.reviewForConsent(
            cycle(),setup(),StarterExerciseTranche.movements(),1,
            CustomCoachAuditInputs(),1,0)
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED),missing)
    }
    @Test fun exactDumbbellLoadAndReducedDropSetSurviveWithoutFictitiousIncrement() {
        val reviewed=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="setup-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                mapOf("dumbbell-rack" to VerifiedLoadStock.Totals(
                    LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                    listOf(java.math.BigDecimal("10"),
                        java.math.BigDecimal("15"),
                        java.math.BigDecimal("20")))),
                emptySet(),emptySet()),
            allowBodyweight=true,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,reviewed.status)
        val twenty=DraftSetLoad.Verified(java.math.BigDecimal("20"),
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND)
        val fifteen=DraftSetLoad.Verified(java.math.BigDecimal("15"),
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND)
        fun day(day:Int)=CustomTrainingDay(day,"Delts",
            ComposerSessionPurpose.HYPERTROPHY,listOf(CustomExerciseSlot(
                "lateral-$day","db_lateral_raise",ComposerRole.ACCESSORY,
                listOf(CustomAuthoredSet(DraftSetType.WORKING,twenty,
                    RepBand(12,15),2,55,75,
                    CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
                        8,15,fifteen))))))
        val custom=CustomTrainingCycle(
            "cycle-dumbbells",1,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
            "Exact weights",listOf(CustomTrainingBlock("base","Hypertrophy",
                TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,3,
                CustomSplitIdentity.Freeform("My delts"),
                listOf(day(0),day(2),day(4)))))
        val first=CustomWorkoutSnapshotDomain.preview(custom,reviewed,
            StarterExerciseTranche.movements(),1,1,0)
        assertTrue(first.toString(),first is CustomWorkoutSnapshotResult.Preview)
        val prescribed=(first as CustomWorkoutSnapshotResult.Preview)
            .snapshot.slots.single().sets.single().prescription
        assertEquals(twenty,prescribed.load)
        assertEquals(fifteen,prescribed.amplifier?.followUpLoad)
        assertEquals(145L,first.snapshot.knownSetAndRestSeconds)
        val impossible=DraftSetLoad.Verified(java.math.BigDecimal("17.5"),
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND)
        val rejected=custom.copy(blocks=listOf(
            custom.blocks.single().copy(days=listOf(
                day(0).copy(slots=listOf(day(0).slots.single().copy(
                    sets=listOf(prescribed.copy(load=impossible))))),
                day(2),day(4)))))
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.INVALID_CYCLE),
            CustomWorkoutSnapshotDomain.preview(rejected,reviewed,
                StarterExerciseTranche.movements(),1,1,0))
    }

}
