package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Base64
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real source -> approval -> issuance -> actuals -> cold reopen -> History. */
class NativeKotlinLandmineJourneyTest {
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

    @Test fun coldSourceKeepsThePlateBasisAndRejectsStraightBarFallbackOrZeroBarbellAllowance() {
        val value=source()
        assertEquals(value,NativeKotlinCustomAuthoringSourceCodec.decode(NativeKotlinCustomAuthoringSourceCodec.encode(value)))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(owner.ownerKey,0,value))
        val cold=(sources().read(owner.ownerKey) as NativeAuthoringSourceRead.Found).source
        assertEquals(value,cold)
        assertEquals(setOf("meadows-row","landmine-press","lm-squat"),CustomExerciseAuthoringDomain.picker(cold.setup,cold.catalog).map{it.exerciseId}.toSet())
        for(id in listOf("meadows-row","landmine-press","lm-squat")) {
            val unloaded=CustomAuthoredSet(DraftSetType.WORKING,
                DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.LANDMINE_ADDED_PLATES,n("0.0")),RepBand(8,12),2,40,90)
            assertNull(CustomExerciseAuthoringDomain.reviewFreshSets(cold.setup,cold.catalog,id,listOf(unloaded)))
            assertEquals(CustomAuthoringRefusal.INATTAINABLE_LOAD,
                CustomExerciseAuthoringDomain.reviewFreshSets(cold.setup,cold.catalog,id,listOf(unloaded.copy(
                    load=DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.SMITH_TOTAL,n("0"))))))
        }
        val athlete=checkNotNull(value.setup.reviewed)
        val capped=value.setup.copy(reviewed=athlete.copy(maxBarbellExercisesPerDay=0))
        assertTrue(CustomExerciseAuthoringDomain.picker(capped,value.catalog).isEmpty())
        val wrong=value.setup.copy(reviewed=athlete.copy(gym=athlete.gym.copy(confirmedStock=mapOf(
            "straight-bar" to VerifiedLoadStock.Barbell(LoadUnit.KG,n("20"),mapOf(n("5") to 2))))))
        assertTrue(CustomExerciseAuthoringDomain.picker(wrong,value.catalog).isEmpty())
    }

    private fun program(source:NativeCustomAuthoringSnapshot):Pair<CustomTrainingCycle,CustomCoachAuditInputs> {
        val cycle=CustomTrainingCycle("landmine-cycle",1,owner.athleteId,AthleteTrainingGoal.HYPERTROPHY,
            "Landmine program",listOf(CustomTrainingBlock("base","Base",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,3,CustomSplitIdentity.Freeform("Three days"),listOf(0,2,4).map{day->
                    CustomTrainingDay(day,"Landmine $day",ComposerSessionPurpose.HYPERTROPHY,source.catalog.map {movement->
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
        return cycle to inputs
    }

    @Test fun fullNativeLandmineWorkoutKeepsActualsAndTargetsThroughRetryColdReopenFinishAndCsv() {
        val source=source()
        val drafts=NativeCustomCycleDraftFileStore(root,fence(),identity)
        val consent=NativeKotlinCustomCycleSignoffFileStore(root,fence(),identity,drafts,sources())
        val preflight=NativeKotlinCustomWorkoutPreflight(fence(),identity,drafts,sources(),consent)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(root,fence(),identity,preflight)
        val history=NativeKotlinHistoryManifestFileStore(root)
        val generated=KotlinNativeJournalFileStore(root)
        fun issued()=NativeKotlinCustomWorkoutJournalFileStore(root,fence(),identity,prepared,preflight,history,generated)
        val (cycle,inputs)=program(source)
        val reviewed=CustomCycleCoachAuditDomain.review(cycle,source.setup,source.catalog,inputs)
        assertEquals(reviewed.issues.toString(),CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,reviewed.status)
        val limited=source.setup.copy(reviewed=checkNotNull(source.setup.reviewed).copy(maxBarbellExercisesPerDay=2))
        assertTrue(CustomCycleCoachAuditDomain.review(cycle,limited,source.catalog,inputs).issues.any {
            it.kind==CustomCoachIssueKind.BARBELL_LIMIT
        })
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(owner.ownerKey,0,source))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts.save(owner.ownerKey,"landmine-cycle",0,cycle))
        val signoff=consent.confirm(owner.ownerKey,"landmine-cycle",1,1,0,inputs,true)
        assertTrue(signoff.toString(),signoff is NativeCustomSignoffWrite.Committed)
        val prepare=prepared.prepare(owner.ownerKey,"landmine-workout","landmine-cycle",1,0,inputs)
        assertTrue(prepare.toString(),prepare is NativeCustomPrepareResult.Prepared)
        assertTrue(history.enroll(owner.ownerKey,owner.athleteId){o,a->owner==NativeKotlinOwnerIdentity(o,a)} is NativeHistoryLedgerResult.Durable)
        var journal=(issued().issue(owner.ownerKey,"landmine-workout","landmine-cycle",1,0,inputs) as NativeCustomJournalResult.Durable).issued.journal
        val snapshot=journal.snapshot
        var count=0
        for(slot in snapshot.slots)for(set in slot.sets) {
            val target=set.prescription.load as DraftSetLoad.Verified
            assertEquals(LoadBasis.LANDMINE_ADDED_PLATES,target.basis)
            assertEquals(LoadUnit.KG,target.unit)
            val actual=if(set.ordinalInSlot==3) {
                when(slot.exerciseId) { "meadows-row"->n("15");"landmine-press"->n("0");else->n("20") }
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
        for(id in listOf("meadows-row","landmine-press","lm-squat"))assertTrue(csv.csv.contains(id))
        assertEquals(9,csv.rowCount)
        assertEquals(source,(sources().read(owner.ownerKey) as NativeAuthoringSourceRead.Found).source)
        assertEquals(snapshot,(issued().open(owner.ownerKey,"landmine-workout") as NativeCustomJournalResult.Durable).issued.journal.snapshot)
    }

    @Test fun newBlockCalibrationCanExplicitlyRetainZeroAddedLandminePlates() {
        val source=source()
        val (base,inputs)=program(source)
        val original=base.blocks.single()
        val first=original.copy(days=original.days.map{day->
            day.copy(slots=day.slots.map{slot->
                if(slot.exerciseId!="landmine-press")slot else slot.copy(
                    sets=slot.sets.map{set->set.copy(load=(set.load as DraftSetLoad.Verified).copy(value=n("0")))})
            })})
        val second=original.copy(blockId="build",label="Build")
        val cycle=base.copy(blocks=listOf(first,second))
        val draft=NativeCustomCycleDraftFileStore(root,fence(),identity)
        val consent=NativeKotlinCustomCycleSignoffFileStore(root,fence(),identity,draft,sources())
        val preflight=NativeKotlinCustomWorkoutPreflight(fence(),identity,draft,sources(),consent)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(root,fence(),identity,preflight)
        val history=NativeKotlinHistoryManifestFileStore(root)
        val composer=KotlinNativeJournalFileStore(root)
        fun journalStore()=NativeKotlinCustomWorkoutJournalFileStore(root,fence(),identity,
            prepared,preflight,history,composer)
        val permissions=NativeKotlinAdaptiveCyclePermissionFileStore(root,fence(),identity)
        val key=owner.ownerKey
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(key,0,source))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),draft.save(key,cycle.cycleId,0,cycle))
        val audit=CustomCycleCoachAuditDomain.review(cycle,source.setup,source.catalog,inputs)
        assertEquals(audit.issues.toString(),CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        assertTrue(consent.confirm(key,cycle.cycleId,1,1,0,inputs,true) is NativeCustomSignoffWrite.Committed)
        assertTrue(history.enroll(key,owner.athleteId){o,a->owner==NativeKotlinOwnerIdentity(o,a)}
            is NativeHistoryLedgerResult.Durable)
        assertTrue(permissions.confirm(key,cycle.cycleId,0,
            setOf(CustomCycleChangeKind.LOADS,CustomCycleChangeKind.WEEK_OVERRIDES),true)
            is NativeAdaptivePermissionWrite.Committed)
        // Complete the adjacent block's actual scheduled exposures.
        for(week in 1..3)for(day in listOf(0,2,4)){
            val id="landmine-w$week-d$day"
            assertTrue(prepared.prepare(key,id,cycle.cycleId,week,day,inputs)
                is NativeCustomPrepareResult.Prepared)
            var journal=(journalStore().issue(key,id,cycle.cycleId,week,day,inputs)
                as NativeCustomJournalResult.Durable).issued.journal
            for((index,set) in journal.originalSnapshot.slots.flatMap{it.sets}.withIndex()){
                val p=set.prescription
                val load=p.load as DraftSetLoad.Verified
                journal=(journalStore().record(key,id,CustomWorkoutJournalOperation.Record(
                    "landmine-log-$index",journal.revision,
                    CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(load.value,load.unit,p.reps.minimum,p.targetRir)))
                    as NativeCustomJournalResult.Durable).issued.journal
            }
            assertTrue(NativeKotlinCustomHistoryTransaction(history,journalStore(),fence(),identity)
                .complete(key,owner.athleteId,id,CustomWorkoutJournalOperation.Finish("finish",journal.revision))
                is NativeCustomFinishResult.Committed)
        }
        fun calibration():NativeKotlinBlockCalibrationConsentStore {
            val journals=journalStore()
            val reader=NativeKotlinMixedHistoryReadCoordinator(history,composer,
                KotlinNativeSavedCorrectionFileStore(root,composer),journals,
                NativeKotlinApprovalFileAuthority(root,fence(),identity),identity,fence(),
                NativeKotlinCustomSavedCorrectionFileStore(root,fence(),identity,journals,history))
            val reviewer=NativeKotlinCustomNextWorkoutReview(fence(),identity,null,null,sources(),
                reader,journals,NativeKotlinCoachAssistanceSettingsStore(root,fence(),identity),preflight)
            return NativeKotlinBlockCalibrationConsentStore(root,fence(),identity,reviewer,draft,sources(),
                permissions,reader,NativeKotlinCustomOutstandingWorkoutInventory(prepared,journals),
                composer,prepared)
        }
        val id="landmine-calibrated"
        val slot="landmine-press-0"
        fun preview(step:BigDecimal)=calibration().preview(key,id,cycle.cycleId,inputs,4,0,slot,
            CoachReadiness.NORMAL,false,step)
        assertEquals(NativeBlockCalibrationPreview.Rejected(NativeCalibrationRefusal.INVALID_SELECTION),
            preview(BigDecimal.ZERO))
        val result=preview(n("5"))
        assertTrue(result.toString(),result is NativeBlockCalibrationPreview.Ready)
        val offer=result as NativeBlockCalibrationPreview.Ready
        assertEquals(listOf(n("10"),n("10")),offer.receipt.authoredLoads)
        assertEquals(listOf(n("0"),n("0")),offer.receipt.proposedLoads)
        assertEquals(LoadBasis.LANDMINE_ADDED_PLATES,offer.receipt.basis)
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.CONFIRMATION_REQUIRED),
            calibration().accept(offer.receipt,inputs,false))
        assertEquals(NativeBlockCalibrationWrite.Accepted(offer.receipt,false),
            calibration().accept(offer.receipt,inputs,true))
        assertEquals(NativeBlockCalibrationRead.Current(offer),
            calibration().readCurrent(key,id,cycle.cycleId,4,0,inputs))
        val prep=prepared.prepare(key,id,cycle.cycleId,4,0,inputs,calibration())
            as NativeCustomPrepareResult.Prepared
        assertEquals(offer.projected,prep.workout.snapshot)
        val issued=journalStore().issue(key,id,cycle.cycleId,4,0,inputs,calibration())
            as NativeCustomJournalResult.Durable
        assertEquals(offer.projected,issued.issued.journal.originalSnapshot)
        assertEquals(cycle,(draft.read(key,cycle.cycleId) as NativeCustomDraftReadResult.Found).draft)
        val prior=(journalStore().open(key,"landmine-w3-d0") as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(listOf(n("0"),n("0")),prior.originalSnapshot.slots.single{it.slotId==slot}
            .sets.filter{it.prescription.type==DraftSetType.WORKING}
            .map{(it.prescription.load as DraftSetLoad.Verified).value})
        assertEquals(issued.issued.journal,(journalStore().open(key,id)
            as NativeCustomJournalResult.Durable).issued.journal)
    }
}
