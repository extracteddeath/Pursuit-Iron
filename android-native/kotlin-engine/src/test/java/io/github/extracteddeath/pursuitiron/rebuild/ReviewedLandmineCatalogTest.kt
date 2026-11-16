package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ReviewedLandmineCatalogTest {
    private fun n(s:String)=BigDecimal(s)
    private val ids=setOf("meadows-row","landmine-press","lm-squat")
    private val entries=StarterExerciseTranche.entries().filter{it.legacyCuratedId in ids}
    private val stock=VerifiedLoadStock.Totals(LoadUnit.KG,LoadBasis.LANDMINE_ADDED_PLATES,
        listOf(n("0"),n("5"),n("7.5"),n("10"),n("15"),n("20")))
    private val template=ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,DeclaredSessionWindow(1,120,null,60))},
        TrainingMuscleCoverage.current.map{DeclaredMuscleDoseBounds(it,n("0"),n("12"),n("40"),0)},emptyList(),4,1,
        (0..2).map{ComposerSessionIntent(it,ComposerSessionPurpose.HYPERTROPHY)})
    private fun review(id:String,key:String="landmine-station",inventory:VerifiedLoadStock=stock)=
        CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(template,
            listOf(CatalogExerciseChoice(id,n("10"),90,120,2,3,2,1,1,5)),
            CatalogGym(mapOf(key to inventory),emptySet(),emptySet())))

    @Test fun exactSourceIdsRequireTheirAnchoredStationAndKeepIndependentPracticeIdentity() {
        assertEquals(ids,entries.map{it.legacyCuratedId}.toSet())
        assertTrue(StarterExerciseTranche.entries().size>=325)
        for(entry in entries) {
            assertEquals(RepBand(8,12),entry.practicalRepBand)
            assertEquals("landmine-station",entry.movement.equipmentId)
            assertEquals(LoadBasis.LANDMINE_ADDED_PLATES,entry.movement.expectedLoadBasis)
            assertFalse(entry.mayTransferPreviousLoad)
            assertTrue(review(entry.legacyCuratedId).issues.toString(),review(entry.legacyCuratedId).issues.isEmpty())
            assertTrue(review(entry.legacyCuratedId,"straight-bar").issues.isNotEmpty())
            assertTrue(review(entry.legacyCuratedId,inventory=VerifiedLoadStock.Barbell(
                LoadUnit.KG,n("20"),mapOf(n("5") to 2))).issues.isNotEmpty())
            assertTrue(review(entry.legacyCuratedId,inventory=VerifiedLoadStock.Totals(
                LoadUnit.KG,LoadBasis.DUMBBELL_PER_HAND,stock.loads)).issues.isNotEmpty())
        }
        assertEquals(listOf("landmine_row","landmine_press","landmine_squat"),entries.map{it.movement.familyId})
        val row=entries.first().movement.credits
        assertEquals(listOf(TrainingMuscle.UPPER_BACK),row.filter{it.role==MuscleCreditRole.DIRECT}.map{it.muscle})
        assertFalse(row.any{it.muscle==TrainingMuscle.LATS})
    }

    @Test fun addedPlateTotalsAreNeverDoubledCombinedWithBarMassOrConvertedBetweenUnits() {
        val zero=EquipmentLoadDomain.inspect(stock,n("0"),LoadUnit.KG) as LoadInspection.Confirmed
        assertTrue(zero.exact);assertEquals(n("5"),zero.nextAbove)
        val gap=EquipmentLoadDomain.inspect(stock,n("8"),LoadUnit.KG) as LoadInspection.Confirmed
        assertFalse(gap.exact);assertEquals(n("7.5"),gap.atOrBelow);assertEquals(n("10"),gap.nextAbove)
        assertFalse((EquipmentLoadDomain.inspect(stock,n("30"),LoadUnit.KG) as LoadInspection.Confirmed).exact)
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),
            EquipmentLoadDomain.inspect(stock,n("10"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,n("10"),LoadUnit.KG))
        assertFalse(KotlinVerifiedPlateLayoutDomain.supportsBasis(stock.basis))
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedLoadStock.Stack(LoadUnit.KG,stock.basis,n("0"),n("5"),n("20"))
        }
    }

    @Test fun sharedComposerCountsLandmineVisitsAgainstThePhysicalBarbellCapBeforeScoring() {
        val review=CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(template,
            entries.map{CatalogExerciseChoice(it.legacyCuratedId,n("10"),90,120,2,3,2,1,1,5)},
            CatalogGym(mapOf("landmine-station" to stock),emptySet(),emptySet())))
        val input=checkNotNull(review.composedRequest)
        val one=CoachWeekComposer.compose(input.copy(maximumBarbellExercisesPerDay=1))
        assertTrue(one.sessions.all{it.orderedVisits.size<=1})
        assertEquals(ids,one.visits.map{it.exerciseId}.toSet())
        val none=CoachWeekComposer.compose(input.copy(maximumBarbellExercisesPerDay=0))
        assertTrue(none.visits.isEmpty())
        assertTrue(LoadBasis.BARBELL_TOTAL.countsTowardBarbellLimit)
        assertTrue(stock.basis.countsTowardBarbellLimit)
        assertFalse(LoadBasis.SMITH_TOTAL.countsTowardBarbellLimit)
        assertFalse(LoadBasis.KETTLEBELL_EACH.countsTowardBarbellLimit)
    }

    @Test fun onlyExplicitAddedPlatesSemanticsPermitAReviewedIncreaseFromAnUnloadedLandmine() {
        fun workout(id:String)=ProgressionWorkoutEvidence(id,"landmine-press",LoadUnit.KG,RepBand(8,12),2,
            (0..1).map{WorkingSetFact(it,SetOutcome.COMPLETED,n("0"),12,null,n("0"),2)},"push-day")
        val before=workout("one");val after=workout("two")
        val ready=ConservativeProgressionReview.assess(before,after,stock,n("5"))
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,ready.reason)
        assertEquals(n("5"),ready.reviewCandidate)
        assertEquals(4,ready.assumedGoalRirSetCount)
        assertTrue(after.observations.all{it.actualLoad==n("0") && it.actualRir==null})
        val failure=after.copy(observations=after.observations.map{it.copy(actualRir=0)})
        assertEquals(IncreaseReviewReason.TOO_CLOSE_TO_FAILURE,
            ConservativeProgressionReview.assess(before,failure,stock,n("5")).reason)
        val smith=stock.copy(basis=LoadBasis.SMITH_TOTAL)
        assertEquals(IncreaseReviewReason.ZERO_LOAD_NEEDS_EXERCISE_SEMANTICS,
            ConservativeProgressionReview.assess(before,after,smith,n("5")).reason)
        assertNull(ConservativeProgressionReview.assess(before,after,stock,n("2.5")).reviewCandidate)
    }

    @Test fun optionalCurrentLevelingCountsTheUnloadedBarAndKeepsCompletedRowsOutOfItsEditKeys() {
        val rows=listOf("0.0","10","20").mapIndexed { index,weight ->
            CurrentWorkoutLevelingRow("set-$index",n(weight),8,index==0,LoadUnit.KG,stock.basis,null)
        }
        val leveled=DoubleProgressionLevelingDomain.reviewCurrentWorkout(rows,stock) as CurrentWorkoutLevelingReview.Ready
        assertEquals(n("10"),leveled.roundedLoad)
        assertEquals(3,leveled.includedWorkingSets)
        assertEquals(listOf("set-1","set-2"),leveled.eligibleSetKeys)
        assertEquals(n("0.0"),rows.first().load)
        assertTrue(rows.first().completed)
        assertEquals(n("20"),rows.last().load)
        val other=rows.map{it.copy(basis=LoadBasis.DUMBBELL_PER_HAND)}
        val dumbbells=stock.copy(basis=LoadBasis.DUMBBELL_PER_HAND)
        val original=DoubleProgressionLevelingDomain.reviewCurrentWorkout(other,dumbbells) as CurrentWorkoutLevelingReview.Ready
        assertEquals(2,original.includedWorkingSets)
        assertEquals(n("15"),original.roundedLoad)
    }

    @Test fun firstProgramAndAllFourCycleWeeksRetainExplicitPerMovementPlateCalibrations() {
        val base=StarterProgramReleaseFixture.request(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val athlete=checkNotNull(base.setup.reviewed)
        val loads=VerifiedLoadStock.Totals(LoadUnit.LB,stock.basis,listOf(n("0"),n("10"),n("20"),n("30")))
        val setup=base.setup.copy(reviewed=athlete.copy(maxBarbellExercisesPerDay=4,
            sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
            gym=athlete.gym.copy(confirmedStock=athlete.gym.confirmedStock+("landmine-station" to loads))))
        val calibrated=mapOf("meadows-row" to n("30"),"landmine-press" to n("0"),"lm-squat" to n("10"))
        val input=base.copy(setup=setup,startingLoads=base.startingLoads-
            setOf("one_arm_db_row","back_squat")+calibrated)
        val result=CoachFirstProgramStartDomain.review(input)
        assertEquals(result.issues.toString(),FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
        val conversion=CoachFirstProgramDraftConversionDomain.propose(result,setup,input.catalog,"landmine-cycle","Landmine program",4)
        assertEquals(conversion.reason,FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,conversion.status)
        val cycle=checkNotNull(conversion.cycle)
        val policy=checkNotNull(CoachFirstProgramDraftConversionDomain.policyForInitialFullCycleReview(result,setup))
        val audit=CustomCycleCoachAuditDomain.review(cycle,setup,input.catalog,policy)
        assertEquals(audit.issues.toString(),CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        for(number in 1..4) {
            val week=checkNotNull(CustomTrainingCycleDomain.week(checkNotNull(audit.structuralReview),number))
            for(id in ids) {
                val work=week.days.flatMap{it.slots}.filter{it.exerciseId==id}.flatMap{it.sets}.filter{it.type==DraftSetType.WORKING}
                assertTrue("week $number/$id",work.isNotEmpty())
                assertTrue(work.all{it.load==DraftSetLoad.Verified(LoadUnit.LB,stock.basis,calibrated.getValue(id)) && it.reps==RepBand(8,12)})
            }
        }
        val uncalibrated=CoachFirstProgramStartDomain.review(input.copy(startingLoads=input.startingLoads-ids))
        assertTrue(uncalibrated.prescription?.sessions.orEmpty().flatMap{it.exercises}.none{it.exerciseId in ids})
        val prohibited=setup.copy(reviewed=checkNotNull(setup.reviewed).copy(maxBarbellExercisesPerDay=0))
        assertTrue(CoachFirstProgramStartDomain.review(input.copy(setup=prohibited)).eligibleMovements.none{it.exerciseId in ids})
    }
}
