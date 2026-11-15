package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Base64
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real source -> approval -> issuance -> actuals -> cold reopen -> History. */
class NativeKotlinKettlebellJourneyTest {
    @get:Rule val temp=TemporaryFolder()
    private val owner=NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{owner}
    private val root get()=temp.root.toPath()
    private fun n(s:String)=BigDecimal(s)
    private fun fence()=NativeKotlinApprovalRevisionFence(root)
    private fun sources()=NativeKotlinCustomAuthoringSourceFileStore(root,fence(),identity)
    private val bells=VerifiedLoadStock.Totals(LoadUnit.KG,LoadBasis.KETTLEBELL_EACH,
        listOf(n("12"),n("16"),n("20"),n("24")))
    private fun source():NativeCustomAuthoringSnapshot {
        val frozen=Base64.getMimeDecoder().decode(checkNotNull(javaClass.getResourceAsStream(
            "/legacy-smith-totals-source.base64")).use{it.readBytes()})
        val previous=NativeKotlinCustomAuthoringSourceCodec.decode(frozen)
        val athlete=checkNotNull(previous.setup.reviewed)
        return previous.copy(setup=previous.setup.copy(reviewed=athlete.copy(
            gym=athlete.gym.copy(unit=LoadUnit.KG,confirmedStock=mapOf(
                "single-kettlebell" to bells,"matched-kettlebell-pair" to bells),
                availableFacilities=emptySet(),availableAttachments=emptySet()))),
            catalog=StarterExerciseTranche.movements().filter{it.exerciseId in setOf("kb-swing","kb-gorilla-row")})
    }

    @Test fun savedSourceKeepsExactSingleAndPairStockAndDoesNotOfferAnUnconfirmedPair() {
        val value=source()
        assertEquals(value,NativeKotlinCustomAuthoringSourceCodec.decode(NativeKotlinCustomAuthoringSourceCodec.encode(value)))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(owner.ownerKey,0,value))
        val cold=(sources().read(owner.ownerKey) as NativeAuthoringSourceRead.Found).source
        assertEquals(value,cold)
        assertEquals(setOf("kb-swing","kb-gorilla-row"),CustomExerciseAuthoringDomain.picker(cold.setup,cold.catalog).map{it.exerciseId}.toSet())
        val athlete=checkNotNull(value.setup.reviewed)
        val single=value.setup.copy(reviewed=athlete.copy(gym=athlete.gym.copy(
            confirmedStock=mapOf("single-kettlebell" to bells))))
        assertEquals(listOf("kb-swing"),CustomExerciseAuthoringDomain.picker(single,value.catalog).map{it.exerciseId})
        val dumbbells=single.copy(reviewed=athlete.copy(gym=athlete.gym.copy(confirmedStock=mapOf(
            "single-kettlebell" to VerifiedLoadStock.Totals(LoadUnit.KG,LoadBasis.DUMBBELL_PER_HAND,bells.loads)))))
        assertTrue(CustomExerciseAuthoringDomain.picker(dumbbells,value.catalog).isEmpty())
    }

    @Test fun fullNativeKettlebellWorkoutKeepsActualsAndTargetsThroughRetryColdReopenFinishAndCsv() {
        val source=source()
        val drafts=NativeCustomCycleDraftFileStore(root,fence(),identity)
        val consent=NativeKotlinCustomCycleSignoffFileStore(root,fence(),identity,drafts,sources())
        val preflight=NativeKotlinCustomWorkoutPreflight(fence(),identity,drafts,sources(),consent)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(root,fence(),identity,preflight)
        val history=NativeKotlinHistoryManifestFileStore(root)
        val generated=KotlinNativeJournalFileStore(root)
        fun issued()=NativeKotlinCustomWorkoutJournalFileStore(root,fence(),identity,prepared,preflight,history,generated)
        val cycle=CustomTrainingCycle("bell-cycle",1,owner.athleteId,AthleteTrainingGoal.HYPERTROPHY,
            "Kettlebell program",listOf(CustomTrainingBlock("base","Base",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,3,CustomSplitIdentity.Freeform("Three days"),listOf(0,2,4).map{day->
                    CustomTrainingDay(day,"Bells $day",ComposerSessionPurpose.HYPERTROPHY,source.catalog.map {movement->
                        val reps=checkNotNull(movement.sourcePracticalReps)
                        val work=CustomAuthoredSet(DraftSetType.WORKING,
                            DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.KETTLEBELL_EACH,n("16")),reps,2,40,90)
                        val warm=work.copy(type=DraftSetType.WARMUP,
                            load=DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.KETTLEBELL_EACH,n("12")),
                            reps=RepBand(5,5),targetRir=null,restSeconds=45)
                        CustomExerciseSlot("${movement.exerciseId}-$day",movement.exerciseId,movement.role,listOf(warm,work,work))
                    })})))
        val bounds=TrainingMuscle.entries.map {m->when(m) {
            TrainingMuscle.HAMSTRINGS,TrainingMuscle.UPPER_BACK->DeclaredMuscleDoseBounds(m,n("6"),n("6"),n("8"),3)
            else->DeclaredMuscleDoseBounds(m,n("0"),n("0"),n("8"),0)
        }}
        val policy=AuthoredGoalProgramPolicy("bell-policy","r1","Reviewed bell program",owner.athleteId,"profile-1",
            AthleteTrainingGoal.HYPERTROPHY,AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
            3,bounds,bounds,mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
        val inputs=CustomCoachAuditInputs(mapOf(TrainingPhase.HYPERTROPHY to policy),mapOf(0 to 200,2 to 200,4 to 200))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(owner.ownerKey,0,source))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts.save(owner.ownerKey,"bell-cycle",0,cycle))
        val signoff=consent.confirm(owner.ownerKey,"bell-cycle",1,1,0,inputs,true)
        assertTrue(signoff.toString(),signoff is NativeCustomSignoffWrite.Committed)
        val prepare=prepared.prepare(owner.ownerKey,"bell-workout","bell-cycle",1,0,inputs)
        assertTrue(prepare.toString(),prepare is NativeCustomPrepareResult.Prepared)
        assertTrue(history.enroll(owner.ownerKey,owner.athleteId){o,a->owner==NativeKotlinOwnerIdentity(o,a)} is NativeHistoryLedgerResult.Durable)
        var journal=(issued().issue(owner.ownerKey,"bell-workout","bell-cycle",1,0,inputs) as NativeCustomJournalResult.Durable).issued.journal
        val snapshot=journal.snapshot
        var count=0
        for(slot in snapshot.slots)for(set in slot.sets) {
            val target=set.prescription.load as DraftSetLoad.Verified
            assertEquals(LoadBasis.KETTLEBELL_EACH,target.basis)
            assertEquals(LoadUnit.KG,target.unit)
            val actual=if(set.ordinalInSlot==3) {
                if(slot.exerciseId=="kb-swing")n("20") else n("12")
            } else target.value
            val record=CustomWorkoutJournalOperation.Record("bell-set-${count++}",journal.revision,
                CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(actual,LoadUnit.KG,set.prescription.reps.minimum,if(set.ordinalInSlot==3)0 else null))
            val saved=issued().record(owner.ownerKey,"bell-workout",record) as NativeCustomJournalResult.Durable
            journal=(issued().open(owner.ownerKey,"bell-workout") as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(saved.issued.journal,journal)
            assertEquals(snapshot,journal.snapshot)
            assertEquals(snapshot,journal.originalSnapshot)
            assertTrue((issued().record(owner.ownerKey,"bell-workout",record) as NativeCustomJournalResult.Durable).replayed)
        }
        assertEquals(6,count)
        val finish=CustomWorkoutJournalOperation.Finish("bell-finish",journal.revision)
        fun transaction()=NativeKotlinCustomHistoryTransaction(history,issued(),fence(),identity)
        val saved=transaction().complete(owner.ownerKey,owner.athleteId,"bell-workout",finish) as NativeCustomFinishResult.Committed
        assertEquals(saved.copy(replayed=true),transaction().complete(owner.ownerKey,owner.athleteId,"bell-workout",finish))
        assertEquals(listOf(n("12"),n("16"),n("20"),n("12"),n("16"),n("12")),saved.evidence.visits.map{it.actual!!.load})
        assertEquals(listOf(n("12"),n("16"),n("16"),n("12"),n("16"),n("16")),saved.evidence.visits.map{(it.prescribedLoad as DraftSetLoad.Verified).value})
        val corrections=KotlinNativeSavedCorrectionFileStore(root,generated)
        val approvals=NativeKotlinApprovalFileAuthority(root,fence(),identity)
        val customCorrections=NativeKotlinCustomSavedCorrectionFileStore(root,fence(),identity,issued(),history)
        val reader=NativeKotlinMixedHistoryReadCoordinator(history,generated,corrections,issued(),approvals,identity,fence(),customCorrections)
        val cold=reader.read(owner.ownerKey,owner.athleteId) as NativeKotlinMixedHistoryReadResult.Review
        assertEquals(1,cold.timeline.completions.size)
        val csv=NativeKotlinVerifiedHistoryCsvExport.prepare(cold.timeline) as NativeKotlinHistoryCsvResult.Ready
        assertTrue(csv.csv.contains("KETTLEBELL_EACH"))
        assertTrue(csv.csv.contains("kb-swing"));assertTrue(csv.csv.contains("kb-gorilla-row"))
        assertEquals(6,csv.rowCount)
        assertEquals(source,(sources().read(owner.ownerKey) as NativeAuthoringSourceRead.Found).source)
        assertEquals(snapshot,(issued().open(owner.ownerKey,"bell-workout") as NativeCustomJournalResult.Durable).issued.journal.snapshot)
    }
}
