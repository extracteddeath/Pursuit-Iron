package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.IOException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinSmithLoadingJourneyTest {
    @get:Rule val temp=TemporaryFolder()
    private var owner:NativeKotlinOwnerIdentity?=NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{owner}
    private val root get()=temp.root.toPath()
    private fun fence()=NativeKotlinApprovalRevisionFence(root)
    private fun sources()=NativeKotlinCustomAuthoringSourceFileStore(root,fence(),identity)
    private fun n(s:String)=BigDecimal(s)
    private fun smith()=VerifiedLoadStock.Smith(LoadUnit.LB,n("15"),mapOf(n("45") to 1,n("5") to 1))
    private fun frozen(name:String)=Base64.getMimeDecoder().decode(checkNotNull(
        javaClass.getResourceAsStream("/$name.base64")).use{it.readBytes()})
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun legacyApproval()=NativeKotlinApprovalCodec.decode(frozen("legacy-smith-totals-approval"))
    private fun legacySource()=NativeKotlinCustomAuthoringSourceCodec.decode(frozen("legacy-smith-totals-source"))
    private fun physicalSource():NativeCustomAuthoringSnapshot {
        val before=legacySource()
        val athlete=checkNotNull(before.setup.reviewed)
        return before.copy(setup=before.setup.copy(reviewed=athlete.copy(gym=athlete.gym.copy(
            confirmedStock=mapOf("flat-smith-station" to smith()),availableFacilities=setOf("flat-bench")))))
    }

    @Test fun independentlyCapturedOldSmithInputsAndApprovalsKeepTheirOriginalBytesAndMeaning() {
        val approval=frozen("legacy-smith-totals-approval")
        val source=frozen("legacy-smith-totals-source")
        assertEquals("92cb8e834450467c91d52218570c195e89f2ce1d37a6bfe0f4687b8fe3774130",sha(approval))
        assertEquals("3eeb09babbfb445ce782911a5ac3dcab87cc414527bacdb0f7a7fa76836f7788",sha(source))
        assertArrayEquals(approval,NativeKotlinApprovalCodec.encode(legacyApproval()))
        assertArrayEquals(source,NativeKotlinCustomAuthoringSourceCodec.encode(legacySource()))
        val stock=(legacyApproval().program.exercises.first().equipment as ComposerEquipment.Verified).inventory
        assertTrue(stock is VerifiedLoadStock.Totals)
        assertEquals(LoadBasis.SMITH_TOTAL,checkNotNull(stock).basis)
        assertTrue((EquipmentLoadDomain.inspect(stock,n("30"),LoadUnit.LB) as LoadInspection.Confirmed).exact)
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,n("30"),LoadUnit.LB))
    }

    @Test fun newPhysicalSmithStockUsesExplicitNewSchemaVersionsAndCannotHideInOldHeaders() {
        val source=physicalSource()
        val sourceBytes=NativeKotlinCustomAuthoringSourceCodec.encode(source)
        assertEquals(2,ByteBuffer.wrap(sourceBytes).getInt(4))
        assertEquals(source,NativeKotlinCustomAuthoringSourceCodec.decode(sourceBytes))
        assertThrows(IOException::class.java){NativeKotlinCustomAuthoringSourceCodec.decode(
            sourceBytes.copyOf().also{ByteBuffer.wrap(it).putInt(4,1)})}
        val original=legacyApproval()
        val stock=VerifiedLoadStock.Smith(LoadUnit.LB,n("20"),mapOf(n("2.5") to 1,n("5") to 1))
        val approval=original.copy(program=original.program.copy(exercises=original.program.exercises.mapIndexed {i,x->
            if(i==0)x.copy(equipment=ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock)) else x }))
        val approvalBytes=NativeKotlinApprovalCodec.encode(approval)
        assertEquals(9,ByteBuffer.wrap(approvalBytes).getInt(4))
        assertEquals(approval,NativeKotlinApprovalCodec.decode(approvalBytes))
        assertThrows(IOException::class.java){NativeKotlinApprovalCodec.decode(
            approvalBytes.copyOf().also{ByteBuffer.wrap(it).putInt(4,8)})}
    }

    @Test fun savedSourceReturnsOnlyTheExactReviewedSmithStationAndFreeBarsCannotSatisfyIt() {
        val source=physicalSource()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish("owner-1",0,source))
        val restored=(sources().read("owner-1") as NativeAuthoringSourceRead.Found).source
        assertEquals(source,restored)
        val option=CustomExerciseAuthoringDomain.picker(restored.setup,restored.catalog).single()
        assertEquals("smith-bench",option.exerciseId)
        assertEquals(LoadBasis.SMITH_TOTAL,option.loadBasis)
        assertEquals(smith(),option.availability.stock)
        val athlete=checkNotNull(source.setup.reviewed)
        val wrong=source.setup.copy(reviewed=athlete.copy(gym=athlete.gym.copy(confirmedStock=mapOf(
            "flat-smith-station" to VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),smith().pairedPlates)))))
        assertTrue(CustomExerciseAuthoringDomain.picker(wrong,source.catalog).isEmpty())
        owner=NativeKotlinOwnerIdentity("owner-2","athlete-2")
        assertFalse(sources().read("owner-1") is NativeAuthoringSourceRead.Found)
    }

    @Test fun generatedApprovalColdIssuesWarmupsAndWorkingSetsInSmithBasisWithoutChangingTargets() {
        val before=legacyApproval()
        val stock=VerifiedLoadStock.Smith(LoadUnit.LB,n("20"),mapOf(n("2.5") to 1,n("5") to 1))
        val approval=before.copy(program=before.program.copy(exercises=before.program.exercises.mapIndexed {i,x->
            if(i==0)x.copy(equipment=ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock)) else x }))
        fun authority()=NativeKotlinApprovalFileAuthority(root,fence(),identity)
        assertEquals(NativeApprovalWriteResult.Committed(1),authority().publish("owner-1",0,approval))
        val coordinator=NativeKotlinWorkoutCoordinator(KotlinNativeJournalFileStore(root),authority(),fence())
        val issued=coordinator.issue("owner-1","workout-1") as NativeKotlinIssueResult.Committed
        val targets=issued.journal.snapshot.targets.filter{it.exerciseId=="db-press"}
        assertTrue(targets.any{it.type==DraftSetType.WARMUP})
        assertTrue(targets.any{it.type==DraftSetType.WORKING})
        for(target in targets) {
            val load=target.load as DraftSetLoad.Verified
            assertEquals(LoadBasis.SMITH_TOTAL,load.basis)
            assertNotNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,load.value,load.unit))
        }
        assertEquals(issued.journal,(coordinator.issue("owner-1","workout-1") as NativeKotlinIssueResult.Committed).journal)
    }

    @Test fun customSourceApprovalIssueColdLoggingFinishAndHistoryKeepPhysicalSmithLoadsExact() {
        val source=physicalSource()
        val drafts=NativeCustomCycleDraftFileStore(root,fence(),identity)
        val consent=NativeKotlinCustomCycleSignoffFileStore(root,fence(),identity,drafts,sources())
        val preflight=NativeKotlinCustomWorkoutPreflight(fence(),identity,drafts,sources(),consent)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(root,fence(),identity,preflight)
        val history=NativeKotlinHistoryManifestFileStore(root)
        val composer=KotlinNativeJournalFileStore(root)
        fun issuedStore()=NativeKotlinCustomWorkoutJournalFileStore(root,fence(),identity,prepared,preflight,history,composer)
        val work=CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n("105")),RepBand(6,10),2,40,90)
        val warm=work.copy(type=DraftSetType.WARMUP,load=work.load.let{
            (it as DraftSetLoad.Verified).copy(value=n("15"))},reps=RepBand(5,5),targetRir=null,restSeconds=45)
        val cycle=CustomTrainingCycle("smith-cycle",1,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
            "Smith training",listOf(CustomTrainingBlock("base","Base",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,3,CustomSplitIdentity.Freeform("Three days"),listOf(0,2,4).map{day->
                    CustomTrainingDay(day,"Press $day",ComposerSessionPurpose.HYPERTROPHY,listOf(
                        CustomExerciseSlot("press-$day","smith-bench",ComposerRole.SECONDARY,listOf(warm,work,work))))})))
        val bounds=TrainingMuscle.entries.map {m->when(m) {
            TrainingMuscle.CHEST->DeclaredMuscleDoseBounds(m,n("6"),n("6"),n("8"),3)
            TrainingMuscle.TRICEPS,TrainingMuscle.FRONT_DELTS->DeclaredMuscleDoseBounds(m,n("0"),n("3"),n("8"),0)
            else->DeclaredMuscleDoseBounds(m,n("0"),n("0"),n("0"),0)
        }}
        val policy=AuthoredGoalProgramPolicy("smith-policy","r1","Exact reviewed Smith program","athlete-1","profile-1",
            AthleteTrainingGoal.HYPERTROPHY,AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
            3,bounds,bounds,mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
        val inputs=CustomCoachAuditInputs(mapOf(TrainingPhase.HYPERTROPHY to policy),mapOf(0 to 200,2 to 200,4 to 200))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish("owner-1",0,source))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts.save("owner-1","smith-cycle",0,cycle))
        assertTrue(consent.confirm("owner-1","smith-cycle",1,1,0,inputs,true) is NativeCustomSignoffWrite.Committed)
        assertTrue(prepared.prepare("owner-1","smith-workout","smith-cycle",1,0,inputs) is NativeCustomPrepareResult.Prepared)
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->owner==NativeKotlinOwnerIdentity(o,a)} is NativeHistoryLedgerResult.Durable)
        var journal=(issuedStore().issue("owner-1","smith-workout","smith-cycle",1,0,inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        val exact=journal.snapshot
        for((i,set) in exact.slots.single().sets.withIndex()) {
            val target=set.prescription.load as DraftSetLoad.Verified
            assertEquals(LoadBasis.SMITH_TOTAL,target.basis)
            val entered=if(i==2)n("115") else target.value
            assertNotNull(KotlinVerifiedPlateLayoutDomain.inspect(smith(),entered,LoadUnit.LB))
            val record=CustomWorkoutJournalOperation.Record("set-$i",journal.revision,
                CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(entered,LoadUnit.LB,if(i==0)5 else 8,if(i==2)0 else null))
            val result=issuedStore().record("owner-1","smith-workout",record) as NativeCustomJournalResult.Durable
            journal=(issuedStore().open("owner-1","smith-workout") as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(result.issued.journal,journal)
            assertEquals(exact,journal.snapshot)
            assertEquals(exact,journal.originalSnapshot)
            assertTrue((issuedStore().record("owner-1","smith-workout",record) as NativeCustomJournalResult.Durable).replayed)
        }
        val transaction=NativeKotlinCustomHistoryTransaction(history,issuedStore(),fence(),identity)
        val finish=CustomWorkoutJournalOperation.Finish("finish",journal.revision)
        val saved=transaction.complete("owner-1","athlete-1","smith-workout",finish) as NativeCustomFinishResult.Committed
        assertEquals(listOf(n("15"),n("105"),n("115")),saved.evidence.visits.map{it.actual!!.load})
        assertEquals(listOf(n("15"),n("105"),n("105")),saved.evidence.visits.map{(it.prescribedLoad as DraftSetLoad.Verified).value})
        assertEquals(saved.copy(replayed=true),transaction.complete("owner-1","athlete-1","smith-workout",finish))
        assertEquals(exact,(issuedStore().open("owner-1","smith-workout") as NativeCustomJournalResult.Durable).issued.journal.snapshot)
    }
    @Test fun approvedSmithHardwareCannotBeReplacedByOverlappingTotalsDifferentBarsOrPairs() {
        val source=physicalSource()
        val move=source.catalog.single()
        val before=legacyApproval()
        val option=before.program.exercises.first().copy(exerciseId=move.exerciseId,pattern=move.pattern,
            role=move.role,allowedFocus=move.permittedFocus,credits=move.credits,
            sourcePracticalReps=move.sourcePracticalReps,recoveryGroup=move.recoveryGroupId,
            minimumGroupCalendarDayGap=move.minimumRecoveryGroupDayGap,
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("105"),smith()))
        val approval=before.copy(program=before.program.copy(exercises=listOf(option),
            maximumBarbellExercisesPerDay=0,allowDirectTricepsOnLower=false))
        assertEquals(NativeApprovedGymCoherenceResult.Matched,
            NativeKotlinApprovedGymCoherenceDomain.inspect(approval,source))
        val athlete=checkNotNull(source.setup.reviewed)
        val alternatives=listOf<VerifiedLoadStock>(
            VerifiedLoadStock.Smith(LoadUnit.LB,n("25"),mapOf(n("40") to 1)),
            VerifiedLoadStock.Smith(LoadUnit.LB,n("15"),mapOf(n("45") to 2,n("5") to 1)),
            VerifiedLoadStock.Barbell(LoadUnit.LB,n("15"),smith().pairedPlates),
            VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n("105"))))
        for(stock in alternatives) {
            assertTrue((EquipmentLoadDomain.inspect(stock,n("105"),LoadUnit.LB) as LoadInspection.Confirmed).exact)
            val changed=source.copy(setup=source.setup.copy(reviewed=athlete.copy(gym=athlete.gym.copy(
                confirmedStock=mapOf("flat-smith-station" to stock)))))
            assertEquals(NativeApprovedGymCoherenceResult.Rejected(
                NativeApprovedGymCoherenceRefusal.PHYSICAL_EQUIPMENT_CHANGED,"smith-bench"),
                NativeKotlinApprovedGymCoherenceDomain.inspect(approval,changed))
        }
    }

}
