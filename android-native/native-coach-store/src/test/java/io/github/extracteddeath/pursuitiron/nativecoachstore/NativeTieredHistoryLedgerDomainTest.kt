package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeTieredHistoryLedgerDomainTest {
    private val scope = BasicProgressionScope("athlete-1","cycle-1","gym-1",
        "block-1","block-1:d0","db_lateral_raise",null,LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND)

    private fun session(order: Long, week: Int, gym: String = "gym-1",
        actualReps: Int = 7): NativeTieredHistorySession {
        val workoutId = "workout-$order"
        val intent = CustomPercentageSetIntent(NamedPercentageScheme.GZCLP,
            PercentageTier.PRIMARY,PercentageDayRole.STANDARD,week,4,5,5,
            BigDecimal("20"),BigDecimal("100"),BigDecimal("100"),0,true,false)
        val set = CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("20")),
            RepBand(3,3),2,60,90,null,exactRepTarget=3,percentageIntent=intent)
        val key = "cycle-1:w$week:d0:slot-1:s1"
        val snapshot = CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","revision-1","cycle-1",2,gym,5),
            week,week,"block-1",TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("My custom"),0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomWorkoutSlotSnapshot("slot-1","db_lateral_raise","lateral_raise",
                "dumbbell",ComposerRole.PRIMARY,null,null,true,"Strict",
                listOf(CustomWorkoutSetSnapshot(key,"slot-1",1,set)))),150,180)
        val evidence = CustomWorkoutHistoricalEvidence("athlete-1",workoutId,"cycle-1",2,
            "finish-$order",2,listOf(CustomHistoricalVisitFact("slot-1",
                "db_lateral_raise",key,CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                set.load,set.reps,set.targetRir,null,KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,actualReps,null),
                "corrected-$order",percentageIntent=intent)))
        val entry = KotlinHistoryManifestEntry(order,workoutId,"custom-receipt-$order",
            evidence.finishedEventId,2,1)
        return NativeTieredHistorySession(entry,snapshot,snapshot,evidence)
    }

    private fun timeline(sessions: List<NativeTieredHistorySession>) =
        NativeKotlinMixedHistoryTimeline("athlete-1",9,sessions.map {
            NativeKotlinMixedHistoryCompletion.Custom(it.entry,it.evidence,it.original.stamp.gymId)
        })

    private fun read(sessions: List<NativeTieredHistorySession>, nextWeek: Int = 4,
        complete: List<NativeTieredHistorySession> = sessions) =
        NativeTieredHistoryLedgerDomain.project(timeline(complete),sessions,scope,"slot-1",
            PercentageTier.PRIMARY,nextWeek)

    @Test fun correctedFactsKeepActualRepsAndManifestRevision() {
        val first = session(1,1,actualReps=2)
        val second = session(2,2,gym="other-gym")
        val reviewed = read(listOf(first,second)) as NativeOwnerTieredLedgerResult.Review
        assertEquals(9L,reviewed.manifestRevision)
        assertEquals(listOf("workout-1"),reviewed.exposures.map { it.workoutId })
        assertEquals(2,reviewed.exposures.single().observations.single().actualReps)
        assertEquals(3,reviewed.exposures.single().issuedTargets.single().minimumReps)
        assertFalse(reviewed.mayIssueWorkout)
    }

    @Test fun rejectsMissingRowsAlreadyTrainedAndNonchronologicalWeeks() {
        val first = session(1,1)
        val second = session(2,2,gym="other-gym")
        assertEquals(NativeOwnerTieredLedgerResult.Rejected(
            NativeOwnerTieredLedgerRefusal.INVALID_HISTORY),
            read(listOf(first),complete=listOf(first,second)))
        assertEquals(NativeOwnerTieredLedgerResult.Rejected(
            NativeOwnerTieredLedgerRefusal.TARGET_ALREADY_TRAINED,"workout-2"),
            read(listOf(first,second),nextWeek=2))
        assertEquals(NativeOwnerTieredLedgerResult.Rejected(
            NativeOwnerTieredLedgerRefusal.NONCHRONOLOGICAL_HISTORY,"workout-2"),
            read(listOf(session(1,2),session(2,1))))
    }

    @Test fun incompatibleLaterVisitCannotDisappearFromLedger() {
        val first = session(1,1)
        val second = session(2,2)
        val altered = second.copy(evidence=second.evidence.copy(visits=
            second.evidence.visits.map { it.copy(actual=it.actual?.copy(load=BigDecimal("15"))) }))
        assertEquals(NativeOwnerTieredLedgerResult.Rejected(
            NativeOwnerTieredLedgerRefusal.INCOMPATIBLE_EXPOSURE,"workout-2",
            CustomTieredJournalRefusal.INCOMPATIBLE_OBSERVATION),
            read(listOf(first,altered)))
    }
}
