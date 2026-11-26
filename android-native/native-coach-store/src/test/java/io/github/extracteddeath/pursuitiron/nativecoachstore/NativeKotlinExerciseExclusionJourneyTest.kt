package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Base64
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real source -> approval -> issuance -> actuals -> cold reopen -> History. */
class NativeKotlinExerciseExclusionJourneyTest {
    @get:Rule val temp=TemporaryFolder()
    private val owner=NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{owner}
    private val root get()=temp.root.toPath()
    private fun n(s:String)=BigDecimal(s)
    private fun fence()=NativeKotlinApprovalRevisionFence(root)
    private fun sources()=NativeKotlinCustomAuthoringSourceFileStore(root,fence(),identity)
    private val plates=VerifiedLoadStock.Totals(LoadUnit.KG,LoadBasis.LANDMINE_ADDED_PLATES,
        listOf(n("0"),n("5"),n("10"),n("15"),n("20")))
    private fun source():NativeCustomAuthoringSnapshot {
        val frozen=Base64.getMimeDecoder().decode(checkNotNull(javaClass.getResourceAsStream(
            "/legacy-smith-totals-source.base64")).use{it.readBytes()})
        val previous=NativeKotlinCustomAuthoringSourceCodec.decode(frozen)
        val athlete=checkNotNull(previous.setup.reviewed)
        return previous.copy(setup=previous.setup.copy(reviewed=athlete.copy(
            maxBarbellExercisesPerDay=3,gym=athlete.gym.copy(unit=LoadUnit.KG,confirmedStock=mapOf(
                "landmine-station" to plates),
                availableFacilities=emptySet(),availableAttachments=emptySet()))),
            catalog=StarterExerciseTranche.movements().filter{it.exerciseId in setOf("meadows-row","landmine-press","lm-squat")})
    }

    @Test fun issuedWorkoutKeepsLoggingAndHistoryWhileNewIssuanceRequiresReviewAfterExclusionAndUndo() =
        runExclusionJourney(false)

    @Test fun athleteCreatedExerciseIssuesLogsCorrectsFinishesAndRetiresWithExactHistory() =
        runExclusionJourney(true)

    private fun runExclusionJourney(userExercise:Boolean) {
        val initial=source()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(owner.ownerKey,0,initial))
        val exerciseId=if(userExercise)"user-my-landmine-press" else "landmine-press"
        val source=if(userExercise) {
            val original=initial.catalog.single{it.exerciseId=="landmine-press"}
            val intent=NativeUserExerciseDraft(owner.ownerKey,owner.athleteId,1,
                "My Landmine Press",original.pattern,original.role,original.permittedFocus,
                false,original.equipmentId,original.attachmentId,original.requiredFacilityIds,
                setOf(TrainingMuscle.FRONT_DELTS),checkNotNull(original.sourcePracticalReps),true)
            val next=(NativeKotlinGymCatalogEditingDomain.addUserExercise(initial,intent)
                as NativeGymCatalogEdit.Proposed).source
            assertEquals(NativeAuthoringSourceWrite.Committed(2),
                sources().publish(owner.ownerKey,1,next))
            (sources().read(owner.ownerKey) as NativeAuthoringSourceRead.Found).source
                .also{assertEquals(next,it)}
        } else initial
        val movements=initial.catalog.map{old->
            if(old.exerciseId=="landmine-press")source.catalog.single{it.exerciseId==exerciseId}
            else old
        }
        assertTrue(CustomExerciseAuthoringDomain.picker(source.setup,source.catalog)
            .any{it.exerciseId==exerciseId})
        val drafts=NativeCustomCycleDraftFileStore(root,fence(),identity)
        val consent=NativeKotlinCustomCycleSignoffFileStore(root,fence(),identity,drafts,sources())
        val preflight=NativeKotlinCustomWorkoutPreflight(fence(),identity,drafts,sources(),consent)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(root,fence(),identity,preflight)
        val history=NativeKotlinHistoryManifestFileStore(root)
        val generated=KotlinNativeJournalFileStore(root)
        fun issued()=NativeKotlinCustomWorkoutJournalFileStore(root,fence(),identity,prepared,preflight,history,generated)
        val cycle=CustomTrainingCycle("landmine-cycle",1,owner.athleteId,AthleteTrainingGoal.HYPERTROPHY,
            "Landmine program",listOf(CustomTrainingBlock("base","Base",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,3,CustomSplitIdentity.Freeform("Three days"),listOf(0,2,4).map{day->
                    CustomTrainingDay(day,"Landmine $day",ComposerSessionPurpose.HYPERTROPHY,movements.map {movement->
                        val reps=checkNotNull(movement.sourcePracticalReps)
                        val work=CustomAuthoredSet(DraftSetType.WORKING,
                            DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.LANDMINE_ADDED_PLATES,n("10")),reps,2,40,90)
                        val warm=work.copy(type=DraftSetType.WARMUP,
                            load=DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.LANDMINE_ADDED_PLATES,n("0")),
                            reps=RepBand(5,5),targetRir=null,restSeconds=45)
                        CustomExerciseSlot("${movement.exerciseId}-$day",movement.exerciseId,movement.role,listOf(warm,work,work))
                    })})))
        val bounds=TrainingMuscle.entries.map {m->when(m) {
            TrainingMuscle.QUADS,TrainingMuscle.FRONT_DELTS,TrainingMuscle.UPPER_BACK->DeclaredMuscleDoseBounds(m,n("6"),n("6"),n("8"),3)
            else->DeclaredMuscleDoseBounds(m,n("0"),n("0"),n("8"),0)
        }}
        val policy=AuthoredGoalProgramPolicy("landmine-policy","r1","Reviewed bell program",owner.athleteId,"profile-1",
            AthleteTrainingGoal.HYPERTROPHY,AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
            3,bounds,bounds,mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
        val inputs=CustomCoachAuditInputs(mapOf(TrainingPhase.HYPERTROPHY to policy),mapOf(0 to 200,2 to 200,4 to 200))
        val reviewed=CustomCycleCoachAuditDomain.review(cycle,source.setup,source.catalog,inputs)
        assertEquals(reviewed.issues.toString(),CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,reviewed.status)
        val limited=source.setup.copy(reviewed=checkNotNull(source.setup.reviewed).copy(maxBarbellExercisesPerDay=2))
        assertTrue(CustomCycleCoachAuditDomain.review(cycle,limited,source.catalog,inputs).issues.any {
            it.kind==CustomCoachIssueKind.BARBELL_LIMIT
        })
        assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts.save(owner.ownerKey,"landmine-cycle",0,cycle))
        val signoff=consent.confirm(owner.ownerKey,"landmine-cycle",1,source.revision,0,inputs,true)
        assertTrue(signoff.toString(),signoff is NativeCustomSignoffWrite.Committed)
        val prepare=prepared.prepare(owner.ownerKey,"landmine-workout","landmine-cycle",1,0,inputs)
        assertTrue(prepare.toString(),prepare is NativeCustomPrepareResult.Prepared)
        assertTrue(history.enroll(owner.ownerKey,owner.athleteId){o,a->owner==NativeKotlinOwnerIdentity(o,a)} is NativeHistoryLedgerResult.Durable)
        assertTrue(prepared.prepare(owner.ownerKey,"library-future","landmine-cycle",2,0,inputs) is NativeCustomPrepareResult.Prepared)
        var journal=(issued().issue(owner.ownerKey,"landmine-workout","landmine-cycle",1,0,inputs) as NativeCustomJournalResult.Durable).issued.journal
        val snapshot=journal.snapshot
        val intent=NativeGymExerciseExclusionChange(owner.ownerKey,owner.athleteId,source.revision,
            checkNotNull(source.setup.reviewed).excludedExerciseIds,setOf(exerciseId))
        val excluded=(NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(source,intent) as NativeGymCatalogEdit.Proposed).source
        assertEquals(NativeAuthoringSourceWrite.Committed(source.revision+1),sources().publish(owner.ownerKey,source.revision,excluded))
        val coldSource=(sources().read(owner.ownerKey) as NativeAuthoringSourceRead.Found).source
        assertTrue(CustomExerciseAuthoringDomain.picker(coldSource.setup,coldSource.catalog).none{it.exerciseId==exerciseId})
        assertTrue(CustomCycleCoachAuditDomain.review(cycle,coldSource.setup,coldSource.catalog,inputs).issues.any{
            it.kind==CustomCoachIssueKind.EXCLUDED_EXERCISE})
        assertTrue(issued().issue(owner.ownerKey,"library-future","landmine-cycle",2,0,inputs) is NativeCustomJournalResult.Rejected)
        assertEquals(snapshot,(issued().open(owner.ownerKey,"landmine-workout") as NativeCustomJournalResult.Durable).issued.journal.snapshot)

        var count=0
        for(slot in snapshot.slots)for(set in slot.sets) {
            val target=set.prescription.load as DraftSetLoad.Verified
            assertEquals(LoadBasis.LANDMINE_ADDED_PLATES,target.basis)
            assertEquals(LoadUnit.KG,target.unit)
            val actual=if(set.ordinalInSlot==3) {
                when(slot.exerciseId) { "meadows-row"->n("15");exerciseId->n("0");else->n("20") }
            } else target.value
            val record=CustomWorkoutJournalOperation.Record("landmine-set-${count++}",journal.revision,
                CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(actual,LoadUnit.KG,set.prescription.reps.minimum,if(set.ordinalInSlot==3)0 else null))
            val saved=issued().record(owner.ownerKey,"landmine-workout",record) as NativeCustomJournalResult.Durable
            journal=(issued().open(owner.ownerKey,"landmine-workout") as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(saved.issued.journal,journal)
            assertEquals(snapshot,journal.snapshot)
            assertEquals(snapshot,journal.originalSnapshot)
            assertTrue((issued().record(owner.ownerKey,"landmine-workout",record) as NativeCustomJournalResult.Durable).replayed)
        }
        assertEquals(9,count)
        val originalRecord=journal.receipts.first{it.operation is CustomWorkoutJournalOperation.Record}
            .operation as CustomWorkoutJournalOperation.Record
        val corrected=originalRecord.copy(eventId="landmine-correction",expectedRevision=journal.revision,
            performed=checkNotNull(originalRecord.performed).copy(reps=6),correctsEventId=originalRecord.eventId)
        val correction=issued().record(owner.ownerKey,"landmine-workout",corrected) as NativeCustomJournalResult.Durable
        journal=(issued().open(owner.ownerKey,"landmine-workout") as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(correction.issued.journal,journal)
        assertTrue((issued().record(owner.ownerKey,"landmine-workout",corrected) as NativeCustomJournalResult.Durable).replayed)
        assertEquals(snapshot,journal.snapshot)
        val finish=CustomWorkoutJournalOperation.Finish("landmine-finish",journal.revision)
        fun transaction()=NativeKotlinCustomHistoryTransaction(history,issued(),fence(),identity)
        val saved=transaction().complete(owner.ownerKey,owner.athleteId,"landmine-workout",finish) as NativeCustomFinishResult.Committed
        assertEquals(saved.copy(replayed=true),transaction().complete(owner.ownerKey,owner.athleteId,"landmine-workout",finish))
        assertEquals(listOf(n("0"),n("10"),n("15"),n("0"),n("10"),n("0"),n("0"),n("10"),n("20")),saved.evidence.visits.map{it.actual!!.load})
        assertEquals(listOf(n("0"),n("10"),n("10"),n("0"),n("10"),n("10"),n("0"),n("10"),n("10")),saved.evidence.visits.map{(it.prescribedLoad as DraftSetLoad.Verified).value})
        assertEquals(6,saved.evidence.visits.first().actual!!.reps)
        val corrections=KotlinNativeSavedCorrectionFileStore(root,generated)
        val approvals=NativeKotlinApprovalFileAuthority(root,fence(),identity)
        val customCorrections=NativeKotlinCustomSavedCorrectionFileStore(root,fence(),identity,issued(),history)
        val reader=NativeKotlinMixedHistoryReadCoordinator(history,generated,corrections,issued(),approvals,identity,fence(),customCorrections)
        val cold=reader.read(owner.ownerKey,owner.athleteId) as NativeKotlinMixedHistoryReadResult.Review
        assertEquals(1,cold.timeline.completions.size)
        val csv=NativeKotlinVerifiedHistoryCsvExport.prepare(cold.timeline) as NativeKotlinHistoryCsvResult.Ready
        assertTrue(csv.csv.contains("LANDMINE_ADDED_PLATES"))
        for(id in listOf("meadows-row",exerciseId,"lm-squat"))assertTrue(csv.csv.contains(id))
        if(userExercise) {
            assertEquals(3,snapshot.slots.single{it.exerciseId==exerciseId}.sets.size)
            assertTrue(csv.csv.contains("user-my-landmine-press"))
            assertEquals(source.catalog,coldSource.catalog)
        }
        assertEquals(9,csv.rowCount)
        val reverse=intent.copy(sourceRevision=source.revision+1,previous=intent.next,next=intent.previous)
        val restored=(NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(coldSource,reverse) as NativeGymCatalogEdit.Proposed).source
        assertEquals(NativeAuthoringSourceWrite.Committed(source.revision+2),sources().publish(owner.ownerKey,source.revision+1,restored))
        assertEquals(source.copy(revision=source.revision+2),(sources().read(owner.ownerKey) as NativeAuthoringSourceRead.Found).source)
        assertTrue(issued().issue(owner.ownerKey,"library-future","landmine-cycle",2,0,inputs) is NativeCustomJournalResult.Rejected)

        assertEquals(snapshot,(issued().open(owner.ownerKey,"landmine-workout") as NativeCustomJournalResult.Durable).issued.journal.snapshot)
    }
}
