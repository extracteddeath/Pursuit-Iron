package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ReviewedKettlebellCatalogTest {
    private fun n(s:String)=BigDecimal(s)
    private val entries=StarterExerciseTranche.entries().filter{it.legacyCuratedId in setOf("kb-swing","kb-gorilla-row")}
    private val stock=VerifiedLoadStock.Totals(LoadUnit.KG,LoadBasis.KETTLEBELL_EACH,
        listOf(n("12"),n("16"),n("20"),n("24")))
    private val template=ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,DeclaredSessionWindow(1,120,null,60))},
        TrainingMuscleCoverage.current.map{DeclaredMuscleDoseBounds(it,n("0"),n("12"),n("40"),0)},emptyList(),4,1,
        (0..2).map{ComposerSessionIntent(it,ComposerSessionPurpose.HYPERTROPHY)})
    private fun review(id:String,stockKey:String,inventory:VerifiedLoadStock=stock,load:String="16")=
        CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(template,
            listOf(CatalogExerciseChoice(id,n(load),90,120,2,3,2,1,1,5)),
            CatalogGym(mapOf(stockKey to inventory),emptySet(),emptySet())))

    @Test fun sourceIdentitiesAndPracticalRepsKeepTheirOwnPhysicalMeaning() {
        assertEquals(listOf("kb-swing","kb-gorilla-row"),entries.map{it.legacyCuratedId})
        assertEquals(listOf(RepBand(12,20),RepBand(8,12)),entries.map{it.practicalRepBand})
        assertTrue(entries.all{!it.mayTransferPreviousLoad && it.movement.expectedLoadBasis==LoadBasis.KETTLEBELL_EACH})
        assertEquals(listOf("single-kettlebell","matched-kettlebell-pair"),entries.map{it.movement.equipmentId})
        assertEquals(TrainingMuscle.HAMSTRINGS,entries[0].movement.credits.single{it.role==MuscleCreditRole.DIRECT}.muscle)
        assertEquals(TrainingMuscle.UPPER_BACK,entries[1].movement.credits.single{it.role==MuscleCreditRole.DIRECT}.muscle)
        assertTrue(StarterExerciseTranche.entries().size>=325)
    }

    @Test fun oneBellNeverConfirmsAMatchedPairAndNeitherCanBorrowDumbbells() {
        for(entry in entries) {
            val id=entry.legacyCuratedId;val key=checkNotNull(entry.movement.equipmentId)
            assertTrue(review(id,key).issues.toString(),review(id,key).issues.isEmpty())
            for(wrong in listOf("dumbbell-rack","single-kettlebell","matched-kettlebell-pair").filter{it!=key})
                assertTrue("$id/$wrong",review(id,wrong).issues.any{it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE})
            val db=VerifiedLoadStock.Totals(LoadUnit.KG,LoadBasis.DUMBBELL_PER_HAND,stock.loads)
            assertTrue(review(id,key,db).issues.isNotEmpty())
            assertTrue(review(id,key,load="32").issues.any{it.kind==CatalogIssueType.LOAD_UNVERIFIED})
        }
    }

    @Test fun discreteBellMassNeverBecomesAnInventedIncrementDoubledPairOrDifferentUnit() {
        val exact=EquipmentLoadDomain.inspect(stock,n("16"),LoadUnit.KG) as LoadInspection.Confirmed
        assertTrue(exact.exact)
        assertEquals(LoadBasis.KETTLEBELL_EACH,exact.basis)
        assertEquals(n("16"),exact.atOrBelow);assertEquals(n("20"),exact.nextAbove)
        val gap=EquipmentLoadDomain.inspect(stock,n("18"),LoadUnit.KG) as LoadInspection.Confirmed
        assertFalse(gap.exact);assertEquals(n("16"),gap.atOrBelow);assertEquals(n("20"),gap.nextAbove)
        assertFalse((EquipmentLoadDomain.inspect(stock,n("32"),LoadUnit.KG) as LoadInspection.Confirmed).exact)
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),EquipmentLoadDomain.inspect(stock,n("16"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,n("16"),LoadUnit.KG))
        val invalid=stock.copy(loads=listOf(n("0"),n("16")))
        assertTrue(EquipmentLoadDomain.inspect(invalid,n("16"),LoadUnit.KG) is LoadInspection.Unavailable)
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedLoadStock.Stack(LoadUnit.KG,LoadBasis.KETTLEBELL_EACH,n("4"),n("4"),n("24"))
        }
    }

    @Test fun firstProgramAndEveryCycleWeekUseOnlyExplicitBellCalibration() {
        val base=StarterProgramReleaseFixture.request(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val athlete=checkNotNull(base.setup.reviewed)
        val physical=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.KETTLEBELL_EACH,
            listOf(n("20"),n("30"),n("40")))
        val setup=base.setup.copy(reviewed=athlete.copy(sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
            gym=athlete.gym.copy(confirmedStock=athlete.gym.confirmedStock+mapOf(
                "single-kettlebell" to physical,"matched-kettlebell-pair" to physical))))
        val input=base.copy(setup=setup,startingLoads=base.startingLoads-
            setOf("one_arm_db_row","db_rdl")+mapOf("kb-swing" to n("30"),"kb-gorilla-row" to n("30")))
        val result=CoachFirstProgramStartDomain.review(input)
        assertEquals(result.issues.toString(),FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
        val conversion=CoachFirstProgramDraftConversionDomain.propose(result,setup,input.catalog,"bell-cycle","Bell program",4)
        assertEquals(conversion.reason,FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,conversion.status)
        val cycle=checkNotNull(conversion.cycle)
        val inputs=checkNotNull(CoachFirstProgramDraftConversionDomain.policyForInitialFullCycleReview(result,setup))
        val audit=CustomCycleCoachAuditDomain.review(cycle,setup,input.catalog,inputs)
        assertEquals(audit.issues.toString(),CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        for(number in 1..4) {
            val week=checkNotNull(CustomTrainingCycleDomain.week(checkNotNull(audit.structuralReview),number))
            for(entry in entries) {
                val work=week.days.flatMap{it.slots}.filter{it.exerciseId==entry.legacyCuratedId}
                    .flatMap{it.sets}.filter{it.type==DraftSetType.WORKING}
                assertTrue("$number/${entry.legacyCuratedId}",work.isNotEmpty())
                assertTrue(work.all{it.load==DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.KETTLEBELL_EACH,n("30")) &&
                    it.reps.minimum>=entry.practicalRepBand.minimum && it.reps.maximum<=entry.practicalRepBand.maximum})
            }
        }
        val borrowed=CoachFirstProgramStartDomain.review(input.copy(startingLoads=input.startingLoads-
            setOf("kb-swing","kb-gorilla-row")))
        assertTrue(borrowed.prescription?.sessions.orEmpty().flatMap{it.exercises}.none{
            it.exerciseId in setOf("kb-swing","kb-gorilla-row")})
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun reviewedBellExercisesReachAnAuditedWeekWithStableWorkingLoadsAndSourceRepBands() {
        val gym=CatalogGym(entries.associate{checkNotNull(it.movement.equipmentId) to stock},emptySet(),emptySet())
        val choices=entries.map{CatalogExerciseChoice(it.legacyCuratedId,n("16"),90,120,2,3,2,1,1,5)}
        val review=CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(template,choices,gym))
        assertTrue(review.issues.toString(),review.issues.isEmpty())
        val policies=entries.map{ExercisePrescriptionPolicy(it.legacyCuratedId,
            ComposerSessionPurpose.HYPERTROPHY,it.practicalRepBand,2,90,120,SetLoadScheme.STRAIGHT)}
        val draft=CoachSessionPrescriptionDomain.draft(checkNotNull(review.composedRequest),policies)
        assertEquals(draft.reviewNotes.toString(),PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,draft.status)
        assertTrue(checkNotNull(draft.finalAudit).satisfiesDeclaredHardConstraints)
        assertTrue(draft.sessions.flatMap{it.exercises}.isNotEmpty())
        for(exercise in draft.sessions.flatMap{it.exercises}) {
            val band=entries.single{it.legacyCuratedId==exercise.exerciseId}.practicalRepBand
            val sets=exercise.sets.filter{it.type==DraftSetType.WORKING}
            assertTrue(sets.isNotEmpty())
            assertTrue(sets.all{it.targetReps==band && it.targetRir==2 &&
                it.load==DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.KETTLEBELL_EACH,n("16"))})
        }
        assertFalse(draft.canIssueWorkout)
    }
}
