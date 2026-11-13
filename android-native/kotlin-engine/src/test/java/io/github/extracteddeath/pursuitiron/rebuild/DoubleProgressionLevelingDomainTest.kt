package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class DoubleProgressionLevelingDomainTest {
    private fun n(s: String) = BigDecimal(s)
    private val stock = VerifiedLoadStock.Totals(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
        listOf("100","105","110","115","120","125","130").map(::n))
    private val band = RepBand(5,8)
    private fun facts(vararg weights: String) =
        weights.map { LevelingSetFact(n(it),5,3) }

    @Test fun ascendingPyramidOffersOptionalRoundedStraightSetCandidate() {
        val review = DoubleProgressionLevelingDomain.review(
            facts("100","120","125","130"),band,2,stock)
            as LevelingReviewResult.Candidate
        assertEquals(n("118.75"),review.averageObservedLoad)
        assertEquals(n("120"),review.proposedStraightSetLoad)
        assertEquals(5,review.repsPerSet)
        assertEquals(4,review.setCount)
        assertFalse(review.mayModifyIssuedWorkout)
    }

    @Test fun levelingNeverIgnoresReportedRirBelowGoalOrRepFloor() {
        assertEquals(LevelingReviewResult.Rejected(
            LevelingRefusal.INSUFFICIENT_EFFORT_EVIDENCE),
            DoubleProgressionLevelingDomain.review(
                listOf(LevelingSetFact(n("100"),5,3),
                    LevelingSetFact(n("120"),5,1)),band,2,stock))
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.BELOW_REP_FLOOR),
            DoubleProgressionLevelingDomain.review(
                listOf(LevelingSetFact(n("100"),4,3),
                    LevelingSetFact(n("120"),5,3)),band,2,stock))
    }

    @Test fun refusesUnverifiedStockAndAlreadyLevelWorkouts() {
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.UNVERIFIED_STOCK),
            DoubleProgressionLevelingDomain.review(
                facts("100","120"),band,2,null))
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.ALREADY_LEVEL),
            DoubleProgressionLevelingDomain.review(
                facts("120","120","120"),band,2,stock))
    }
    @Test fun missingRirIsClearlyLabeledAsAssumptionNotMeasuredCapacity() {
        val review = DoubleProgressionLevelingDomain.review(
            listOf(LevelingSetFact(n("100"),5,null),
                LevelingSetFact(n("120"),5,null)),band,2,stock)
            as LevelingReviewResult.Candidate
        assertTrue(review.note.contains("RIR was unreported for 2"))
        assertFalse(review.mayModifyIssuedWorkout)
    }

    @Test fun invalidBandAndAboveCeilingAreRejectedInsteadOfClamped() {
        // RepBand rejects inverted ranges at construction; the leveling
        // domain receives only already-valid rep bands.
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.ABOVE_REP_CEILING),
            DoubleProgressionLevelingDomain.review(
                listOf(LevelingSetFact(n("100"),9,3),
                    LevelingSetFact(n("120"),9,3)),band,2,stock))
    }

    private fun current(
        vararg loads:String,
        finished:Set<Int> = emptySet(),
        station:String? = null,
        repOverrides:Map<Int,Int> = emptyMap()
    ) = loads.mapIndexed{index,weight->
        CurrentWorkoutLevelingRow(
            setKey="working-${index+1}",load=n(weight),
            reps=repOverrides[index] ?: 5,completed=index in finished,
            unit=LoadUnit.LB,basis=LoadBasis.SMITH_TOTAL,
            machineInstanceId=station
        )
    }

    @Test fun webParityPyramidUsesCurrentEntriesAndRoundsToVerifiedStock() {
        val review=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("100","120","125","130"),stock)
            as CurrentWorkoutLevelingReview.Ready
        assertEquals(n("118.75"),review.rawMean)
        assertEquals(n("120"),review.roundedLoad)
        assertEquals(5,review.averageReps)
        assertEquals(4,review.includedWorkingSets)
        assertEquals(listOf("working-1","working-2","working-3","working-4"),
            review.eligibleSetKeys)
    }

    @Test fun alreadyCompletedSetsCountInMeanButNeverReceiveLevelingPrefill() {
        val review=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("100","120","125","130",finished=setOf(0,1)),stock)
            as CurrentWorkoutLevelingReview.Ready
        assertEquals(n("118.75"),review.rawMean)
        assertEquals(listOf("working-3","working-4"),review.eligibleSetKeys)
        assertEquals(5,review.averageReps)
    }

    @Test fun averagesActualEnteredRepetitionsRatherThanPrescribedMinimum() {
        val review=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("100","120","125","130",
                repOverrides=mapOf(0 to 8,1 to 6,2 to 5,3 to 5)),stock)
            as CurrentWorkoutLevelingReview.Ready
        assertEquals(6,review.averageReps)
        assertEquals(n("120"),review.roundedLoad)
    }

    @Test fun neverRewritesCompletedOrAlreadyLevelSets() {
        val completed=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("100","120","125","130",finished=setOf(0,1,2,3)),stock)
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.NO_UNFINISHED_SETS),completed)
        val level=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("120","120","120","120"),stock)
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.NO_RISING_WEIGHTS),level)
        val descending=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("130","125","120","100"),stock)
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.NO_RISING_WEIGHTS),descending)
    }

    @Test fun refusesMissingStockWithoutGuessingMachineOrPlateIncrement() {
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.UNVERIFIED_STOCK),
            DoubleProgressionLevelingDomain.reviewCurrentWorkout(
                current("100","120","125","130"),null))
        val differentUnit=VerifiedLoadStock.Totals(
            LoadUnit.KG,LoadBasis.SMITH_TOTAL,listOf(n("50"),n("60")))
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.UNVERIFIED_STOCK),
            DoubleProgressionLevelingDomain.reviewCurrentWorkout(
                current("100","120"),differentUnit))
    }

    @Test fun differentPhysicalMachinesAreNeverAveragedTogether() {
        val a=current("100","120","125","130",station="machine-A")
        val mixed=a.toMutableList().apply {
            this[2]=this[2].copy(machineInstanceId="machine-B")
        }
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.MIXED_EQUIPMENT),
            DoubleProgressionLevelingDomain.reviewCurrentWorkout(mixed,stock))
        assertTrue(DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            a,stock) is CurrentWorkoutLevelingReview.Ready)
    }

    @Test fun invalidAndBlankCurrentEntriesCannotBecomeFabricatedPerformance() {
        val duplicate=current("100","120").map{it.copy(setKey="same")}
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.INVALID_ENTRY),
            DoubleProgressionLevelingDomain.reviewCurrentWorkout(duplicate,stock))
        val invalid=current("100","120").toMutableList().apply {
            this[1]=this[1].copy(reps=null)
        }
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.INVALID_ENTRY),
            DoubleProgressionLevelingDomain.reviewCurrentWorkout(invalid,stock))
        val blank=current("100","120").toMutableList().apply {
            this[1]=this[1].copy(load=null)
        }
        assertEquals(CurrentWorkoutLevelingReview.Unavailable(
            CurrentWorkoutLevelingReview.Reason.INSUFFICIENT_ENTERED_SETS),
            DoubleProgressionLevelingDomain.reviewCurrentWorkout(blank,stock))
    }

    @Test fun halfStepUsesWebsRoundUpNotConservativeRoundDown() {
        val review=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("115","120"),stock) as CurrentWorkoutLevelingReview.Ready
        assertEquals(n("117.5"),review.rawMean)
        assertEquals(n("120"),review.roundedLoad)
    }

    @Test fun exactEquipmentValuesWinOverArithmeticAverage() {
        val stockWithGaps=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            listOf("100","110","130").map(::n))
        val reviewed=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("100","110","130"),stockWithGaps)
            as CurrentWorkoutLevelingReview.Ready
        // 113.333 is nearer verified 110 than 130.
        assertEquals(n("113.333"),reviewed.rawMean)
        assertEquals(n("110"),reviewed.roundedLoad)
    }

    /** The Last action must use a coherent prior session, not a mean of days or machines. */
    private fun priorSession(
        loads:List<String>,
        sessionFor:(Int)->String={"prior-day"},
        machineFor:(Int)->String?={null},
        rirFor:(Int)->Int?={3}
    ):WorkoutLastReferenceResult.Ready = WorkoutLastReferenceResult.Ready(
        loads.mapIndexed { index,load->
            WorkoutReferenceKey("working-$index") to WorkoutLastSetReference(
                workoutId=sessionFor(index),completionOrder=7,
                outcome=KotlinJournalSetOutcome.COMPLETED,
                actual=KotlinPerformedSet(n(load),LoadUnit.LB,5,
                    rirFor(index),machineInstanceId=machineFor(index)),
                loadBasis=LoadBasis.SMITH_TOTAL
            )
        }.toMap()
    )

    private fun currentKeys() = (0..3).map {
        WorkoutReferenceKey("working-$it") to
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n("120"))
    }

    @Test fun priorSessionPyramidPrefillsOnlyAnOptionalCurrentEditorStartingPoint() {
        val result=DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(
            currentKeys(),
            priorSession(listOf("100","120","125","130")),
            null,band,2,stock
        ) as LevelingReviewResult.Candidate
        assertEquals(n("118.75"),result.averageObservedLoad)
        assertEquals(n("120"),result.proposedStraightSetLoad)
        assertEquals(5,result.repsPerSet)
        assertEquals(4,result.setCount)
        assertFalse(result.mayModifyIssuedWorkout)
    }

    @Test fun priorLevelingRejectsMixedHistoricalSessionsAndPhysicalMachines() {
        val loads=listOf("100","120","125","130")
        val differentDays=priorSession(loads,sessionFor={
            if(it==3)"older-day" else "prior-day"
        })
        assertEquals(LevelingReviewResult.Rejected(
            LevelingRefusal.INCOMPLETE_OR_INVALID),
            DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(
                currentKeys(),differentDays,null,band,2,stock))
        val machines=priorSession(loads,machineFor={
            if(it==2)"machine-B" else "machine-A"
        })
        assertEquals(LevelingReviewResult.Rejected(
            LevelingRefusal.INCOMPLETE_OR_INVALID),
            DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(
                currentKeys(),machines,"machine-A",band,2,stock))
    }

    @Test fun priorLevelingRequiresVerifiedStockAndRespectsLowActualRir() {
        val loads=listOf("100","120","125","130")
        assertEquals(LevelingReviewResult.Rejected(
            LevelingRefusal.UNVERIFIED_STOCK),
            DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(
                currentKeys(),priorSession(loads),null,band,2,null))
        assertEquals(LevelingReviewResult.Rejected(
            LevelingRefusal.INSUFFICIENT_EFFORT_EVIDENCE),
            DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(
                currentKeys(),priorSession(loads,rirFor={
                    if(it==2)1 else 3
                }),null,band,2,stock))
        // A verified cable stack with the same numeric weights is not
        // authority to Level a Smith exercise, even in the same unit.
        val wrongEquipment=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.CABLE_STACK,
            listOf("100","105","110","115","120","125","130").map(::n))
        assertEquals(LevelingReviewResult.Rejected(
            LevelingRefusal.UNVERIFIED_STOCK),
            DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(
                currentKeys(),priorSession(loads),null,band,2,wrongEquipment))
    }

    @Test fun priorWorkoutMidpointMatchesCurrentWebStyleRoundUp() {
        val previous=DoubleProgressionLevelingDomain.review(
            facts("115","120"),band,2,stock
        ) as LevelingReviewResult.Candidate
        val today=DoubleProgressionLevelingDomain.reviewCurrentWorkout(
            current("115","120"),stock
        ) as CurrentWorkoutLevelingReview.Ready
        assertEquals(n("117.5"),previous.averageObservedLoad)
        assertEquals(today.roundedLoad,previous.proposedStraightSetLoad)
        assertEquals(n("120"),previous.proposedStraightSetLoad)
        assertFalse(previous.mayModifyIssuedWorkout)
    }

    @Test fun priorWorkoutCannotSuggestZeroOrInventStockWhenMeanIsNearZero() {
        val unloadedAndTen=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n("0"),n("10")))
        val candidate=DoubleProgressionLevelingDomain.review(
            facts("2","4"),band,2,unloadedAndTen
        )
        // These very low old observations are below today's rep floor and
        // are not accepted merely because the present stock has a zero.
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.NO_SUPPORTED_CANDIDATE),
            DoubleProgressionLevelingDomain.review(
                facts("2","4"),band,2,
                VerifiedLoadStock.Totals(
                    LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n("0")))))
        assertTrue(candidate.toString(),candidate is LevelingReviewResult.Candidate)
        assertEquals(n("10"),
            (candidate as LevelingReviewResult.Candidate).proposedStraightSetLoad)
        assertFalse(candidate.mayModifyIssuedWorkout)
    }

}
