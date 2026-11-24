package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinCustomHistoryTransactionTest {
    @get:Rule val temp=TemporaryFolder()
    private var supersetFixture=false
    private var stagedMyoFixture=false
    private var partnerIdeasFixture=false
    private var current:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity {current}
    private val fence by lazy {
        NativeKotlinApprovalRevisionFence(temp.root.toPath())
    }
    private val drafts by lazy {
        NativeCustomCycleDraftFileStore(temp.root.toPath(),fence,identity)
    }
    private val sources by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(
            temp.root.toPath(),fence,identity)
    }
    private val consent by lazy {
        NativeKotlinCustomCycleSignoffFileStore(
            temp.root.toPath(),fence,identity,drafts,sources)
    }
    private val preflight by lazy{
        NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,consent)
    }
    private val prepared by lazy {
        NativeKotlinCustomWorkoutPreparedStore(
            temp.root.toPath(),fence,identity,preflight)
    }
    private val history by lazy {
        NativeKotlinHistoryManifestFileStore(temp.root.toPath())
    }
    private val composerJournal by lazy {
        KotlinNativeJournalFileStore(temp.root.toPath())
    }
    private val issued by lazy {
        NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),fence,identity,prepared,preflight,
            history,composerJournal)
    }
    private fun enroll() {
        val ready=history.enroll("owner-1","athlete-1") { o,a ->
            o=="owner-1" && a=="athlete-1" && current==
                NativeKotlinOwnerIdentity(o,a)
        }
        assertTrue(ready.toString(),ready is NativeHistoryLedgerResult.Durable)
    }
    private fun prepareWeek() {
        assertTrue(prepared.prepare("owner-1","workout-2",
            "cycle-1",2,0,input()) is NativeCustomPrepareResult.Prepared)
    }
    private fun issueWeek():NativeCustomJournalResult.Durable {
        val result=issued.issue("owner-1","workout-2",
            "cycle-1",2,0,input())
        assertTrue(result.toString(),result is NativeCustomJournalResult.Durable)
        return result as NativeCustomJournalResult.Durable
    }
    private fun recordSet(event:String,revision:Long,reps:Int=16)=
        CustomWorkoutJournalOperation.Record(
            event,revision,
            CustomWorkoutVisitKey("cycle-1:w2:d0:slot-0:s1",
                CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(null,null,reps,1)
        )
    private fun n(value:String)=BigDecimal(value)
    private fun setup()=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="rev-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,
        trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.Minutes(60),
        gym=GymProfileDraft("gym-1",LoadUnit.LB,emptyMap(),
            emptySet(),emptySet()),
        allowBodyweight=true,allowSupersets=supersetFixture,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(1,3,5,6)
    ))
    private fun source(rev:Long=1)=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",rev,setup(),StarterExerciseTranche.movements())
    private fun set(reps:Int)=CustomAuthoredSet(
        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
        RepBand(reps,reps+2),2,55,75)
    private fun cycle(rev:Long=1)=CustomTrainingCycle(
        "cycle-1",rev,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "Custom cycle",listOf(CustomTrainingBlock(
            "base","Hypertrophy",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Freeform("My split"),
            listOf(0,2,4).map {day ->
                CustomTrainingDay(day,"Core $day",
                    ComposerSessionPurpose.HYPERTROPHY,
                    listOf(CustomExerciseSlot("slot-$day",
                        "bodyweight_crunch",ComposerRole.ACCESSORY,
                        if(supersetFixture)listOf(set(10),set(10)) else listOf(set(10)),
                        supersetGroup=if(supersetFixture)"pair" else null,pinned=true,
                        coachNote="My exact prescription"))+
                        if(supersetFixture)listOf(CustomExerciseSlot("partner-$day",
                            if(partnerIdeasFixture)"bw-calf" else "bodyweight_crunch",ComposerRole.ACCESSORY,
                            List(2){set(if(partnerIdeasFixture)16 else 10)},
                            supersetGroup="pair")) else emptyList())
            }
        )),mapOf("base" to listOf(
            CustomWeekSlotOverride(2,0,"slot-0",if(supersetFixture)listOf(
                set(16).copy(amplifier=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
                    followUpActiveSeconds=15)),set(16)) else if(stagedMyoFixture)
                listOf(set(16).copy(amplifier=CustomSetAmplifier(
                    CustomAmplifierKind.MYO_REPS,4,20,followUpActiveSeconds=15,
                    followUpRounds=3))) else listOf(set(16))))))
    private fun bounds()=TrainingMuscle.entries.map { muscle ->
        DeclaredMuscleDoseBounds(muscle,
            if(partnerIdeasFixture && muscle in setOf(TrainingMuscle.ABS,TrainingMuscle.CALVES))n("6")
                else if(muscle==TrainingMuscle.ABS)n(if(supersetFixture)"12" else "3") else BigDecimal.ZERO,
            if(partnerIdeasFixture && muscle in setOf(TrainingMuscle.ABS,TrainingMuscle.CALVES))n("8")
                else if(muscle==TrainingMuscle.ABS)n(if(supersetFixture)"16" else "4") else BigDecimal.ZERO,
            if(partnerIdeasFixture && muscle in setOf(TrainingMuscle.ABS,TrainingMuscle.CALVES))n("10")
                else if(muscle==TrainingMuscle.ABS)n(if(supersetFixture)"20" else "5") else BigDecimal.ZERO,
            if(muscle==TrainingMuscle.ABS || (partnerIdeasFixture && muscle==TrainingMuscle.CALVES))3 else 0)
    }
    private fun input()=CustomCoachAuditInputs(
        policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to
            AuthoredGoalProgramPolicy(
                policyId="coach-1",revision="policy-1",
                rationale="Reviewed training volume policy",
                athleteId="athlete-1",athleteRevision="rev-1",
                goal=AthleteTrainingGoal.HYPERTROPHY,
                experience=AthleteExperience.ADVANCED,
                split=ComposerSplit.FULL_BODY,phase=TrainingPhase.HYPERTROPHY,
                trainingDays=3,baselineBounds=bounds(),
                selectedBounds=bounds(),
                minimumPurposeDays=mapOf(ComposerSessionPurpose.HYPERTROPHY to 3)
            )),
        nonSetSecondsByWeekday=mapOf(0 to 200,2 to 200,4 to 200)
    )
    private fun initialize() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,cycle()))
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            CustomCycleCoachAuditDomain.review(
                cycle(),setup(),source().catalog,input()).status)
    }
    private fun approve() {
        assertTrue(consent.confirm(
            "owner-1","cycle-1",1,1,0,input(),true
        ) is NativeCustomSignoffWrite.Committed)
    }



    private val transactions by lazy {
        NativeKotlinCustomHistoryTransaction(history,issued,fence,identity)
    }
    private fun ready() {
        initialize();approve();prepareWeek();enroll();issueWeek()
    }
    private fun logged() {
        ready()
        val result=issued.record("owner-1","workout-2",
            recordSet("set-1",0))
        assertTrue(result.toString(),result is NativeCustomJournalResult.Durable)
    }
    private fun finish()=CustomWorkoutJournalOperation.Finish(
        "finish-1",1)

    @Test fun authoredSupersetAlternatesThroughColdRecoveryAndSavesEveryMainAndAmplifierFact() {
        supersetFixture=true
        ready()
        fun cold()=(NativeKotlinCustomWorkoutJournalFileStore(temp.root.toPath(),fence,identity,
            prepared,preflight,history,composerJournal).open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal
        var journal=cold()
        val original=journal.originalSnapshot
        val mainA="cycle-1:w2:d0:slot-0:s1"
        val mainB="cycle-1:w2:d0:partner-0:s1"
        val secondA="cycle-1:w2:d0:slot-0:s2"
        val secondB="cycle-1:w2:d0:partner-0:s2"
        val visits=listOf(CustomWorkoutVisitKey(mainA,CustomWorkoutVisitPart.MAIN),
            CustomWorkoutVisitKey(mainA,CustomWorkoutVisitPart.FOLLOW_UP),
            CustomWorkoutVisitKey(mainB,CustomWorkoutVisitPart.MAIN),
            CustomWorkoutVisitKey(secondA,CustomWorkoutVisitPart.MAIN),
            CustomWorkoutVisitKey(secondB,CustomWorkoutVisitPart.MAIN))
        val pauses=listOf(20,0,75,0,null)
        visits.forEachIndexed {index,visit->
            assertEquals(visit,CustomWorkoutJournalDomain.nextUnresolvedVisit(journal))
            val operation=CustomWorkoutJournalOperation.Record("round-$index",journal.revision,
                visit,KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(null,null,
                    if(visit.part==CustomWorkoutVisitPart.FOLLOW_UP)4 else 16,null))
            val durable=issued.record("owner-1","workout-2",operation) as NativeCustomJournalResult.Durable
            journal=cold()
            assertEquals(durable.issued.journal,journal)
            assertEquals(original,journal.snapshot)
            assertEquals(original,journal.originalSnapshot)
            val next=CustomWorkoutJournalDomain.nextUnresolvedVisit(journal)
            assertEquals(pauses[index],KotlinWorkoutRestDomain.inspect(journal,next).automaticCue?.seconds)
            val retry=issued.record("owner-1","workout-2",operation) as NativeCustomJournalResult.Durable
            assertTrue(retry.replayed)
            assertEquals(journal,retry.issued.journal)
        }
        assertEquals(5,journal.effectiveVisits.size)
        assertEquals(5,CustomWorkoutVisitFocusDomain.progress(journal).recordedVisits)
        val finish=CustomWorkoutJournalOperation.Finish("finish-rounds",journal.revision)
        val complete=transactions.complete("owner-1","athlete-1","workout-2",finish)
            as NativeCustomFinishResult.Committed
        assertEquals(5,complete.evidence.visits.size)
        assertTrue(complete.evidence.visits.all{it.actual?.actualRir==null})
        assertEquals(4,complete.evidence.visits.count{it.part==CustomWorkoutVisitPart.MAIN})
        assertEquals(1,complete.evidence.visits.count{it.part==CustomWorkoutVisitPart.FOLLOW_UP})
        assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(cold()))
        assertEquals(complete.copy(replayed=true),transactions.complete("owner-1","athlete-1","workout-2",finish))
    }

    @Test fun supersetEditColdSavesAndSourceOrOwnerFencesProtectRevisionedWrites() {
        supersetFixture=true
        initialize()
        val original=cycle()
        val edit=CustomCycleBuilderDomain.setSupersetMembers(original,1,"base",0,"pair",emptyList(),true)
            as CustomCycleBuilderResult.Proposed
        val coordinator=NativeKotlinCustomAuthoringDraftCoordinator(sources,drafts,fence,identity)
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            coordinator.saveVerifiedExerciseEdit("owner-1",edit.draft,1,1))
        val cold=NativeCustomCycleDraftFileStore(temp.root.toPath(),fence,identity)
        assertEquals(edit.draft,(cold.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft)
        val undo=CustomCycleBuilderDomain.undo(edit.draft,edit.receipt) as CustomCycleBuilderResult.Proposed
        assertEquals(NativeAuthoringSourceWrite.Committed(2),sources.publish("owner-1",1,source(2)))
        assertTrue(coordinator.saveVerifiedExerciseEdit("owner-1",undo.draft,2,1)
            is NativeCustomDraftWriteResult.Rejected)
        current=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeCustomDraftWriteResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),
            coordinator.saveVerifiedExerciseEdit("owner-1",undo.draft,2,2))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            coordinator.saveVerifiedExerciseEdit("owner-1",undo.draft,2,2))
        assertEquals(original.copy(revision=3),
            (cold.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft)
    }
    @Test fun changingProgramSupersetsInvalidatesFutureApprovalAndKeepsTheIssuedWorkoutExact() {
        supersetFixture=true
        ready()
        val original=(issued.open("owner-1","workout-2") as NativeCustomJournalResult.Durable).issued.journal
        val edit=CustomCycleBuilderDomain.setSupersetMembers(cycle(),1,"base",0,"pair",emptyList(),false)
            as CustomCycleBuilderResult.Proposed
        val coordinator=NativeKotlinCustomAuthoringDraftCoordinator(sources,drafts,fence,identity)
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            coordinator.saveVerifiedExerciseEdit("owner-1",edit.draft,1,1))
        assertFalse((consent.read("owner-1","cycle-1",input()) as NativeCustomSignoffRead.Found).isCurrent)
        val reopened=(issued.open("owner-1","workout-2") as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(original,reopened)
        assertEquals(original.originalSnapshot,reopened.snapshot)
    }

    @Test fun reviewedPartnerIdeaRequiresASaveAndApprovalBeforeIssuingAndLoggingThePair() {
        supersetFixture=true;partnerIdeasFixture=true
        val original=cycle().let {cycle->cycle.copy(blocks=cycle.blocks.map{block->
            block.copy(days=block.days.map{day->day.copy(slots=day.slots.map{it.copy(supersetGroup=null)})})
        })}
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources.publish("owner-1",0,source()))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts.save("owner-1","cycle-1",0,original))
        val currentSource=(sources.read("owner-1") as NativeAuthoringSourceRead.Found).source
        val day=original.blocks.single().days.single{it.weekday==0}
        val ideas=CustomSupersetPartnerRankingDomain.rank(day,
            CustomExerciseAuthoringDomain.picker(currentSource.setup,currentSource.catalog),
            "slot-0",null,true,CoachAssistancePolicy.STANDARD.coachingSuggestionsEnabled)
        assertEquals(listOf("partner-0"),ideas.partners.map{it.slotId})
        assertEquals(original,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft)
        val proposal=CustomCycleBuilderDomain.setSupersetMembers(original,1,"base",0,"chosen-pair",
            listOf("slot-0",ideas.partners.single().slotId),true) as CustomCycleBuilderResult.Proposed
        val coordinator=NativeKotlinCustomAuthoringDraftCoordinator(sources,drafts,fence,identity)
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            coordinator.saveVerifiedExerciseEdit("owner-1",proposal.draft,1,1))
        assertEquals(original.weekOverrides,proposal.draft.weekOverrides)
        assertTrue(consent.confirm("owner-1","cycle-1",2,1,0,input(),true) is NativeCustomSignoffWrite.Committed)
        prepareWeek();enroll();issueWeek()
        var journal=(issued.open("owner-1","workout-2") as NativeCustomJournalResult.Durable).issued.journal
        val snapshot=journal.originalSnapshot
        val visited=mutableListOf<CustomWorkoutVisitKey>()
        while(true) {
            val next=CustomWorkoutJournalDomain.nextUnresolvedVisit(journal) ?:break
            visited+=next
            val record=CustomWorkoutJournalOperation.Record("idea-${journal.revision}",journal.revision,next,
                KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(null,null,
                    if(next.part==CustomWorkoutVisitPart.FOLLOW_UP)4 else 16,null))
            assertTrue(issued.record("owner-1","workout-2",record) is NativeCustomJournalResult.Durable)
            journal=(issued.open("owner-1","workout-2") as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(snapshot,journal.originalSnapshot)
        }
        assertEquals(listOf("slot-0","slot-0","partner-0","slot-0","partner-0"),
            visited.map{key->snapshot.slots.single{slot->slot.sets.any{it.setKey==key.setKey}}.slotId})
        val completed=transactions.complete("owner-1","athlete-1","workout-2",
            CustomWorkoutJournalOperation.Finish("finish-ideas",journal.revision)) as NativeCustomFinishResult.Committed
        assertEquals(5,completed.evidence.visits.size)
        assertEquals(setOf("bodyweight_crunch","bw-calf"),completed.evidence.visits.map{it.exerciseId}.toSet())
        assertTrue(completed.evidence.visits.all{it.actual?.actualRir==null})
    }

    @Test fun customCompletionRecordsTimeOnceAndColdRetryKeepsTheOriginalObservation() {
        logged()
        var now=1794690000000L
        var reads=0
        fun transaction()=NativeKotlinCustomHistoryTransaction(history,issued,fence,identity,
            NativeHistoryCompletionClock{reads++;now})
        val first=transaction().complete("owner-1","athlete-1","workout-2",finish())
            as NativeCustomFinishResult.Committed
        assertEquals(now,first.completion.completedAtEpochMillis)
        now+=86400000L
        val replay=transaction().complete("owner-1","athlete-1","workout-2",finish())
            as NativeCustomFinishResult.Committed
        assertEquals(first.completion,replay.completion)
        assertEquals(1,reads)
    }

    @Test fun customCrashRecoveryUsesThePersistedIntentTimeInsteadOfTheRecoveryClock() {
        logged()
        val original=1794690000000L
        val ledger=(history.open("owner-1","athlete-1") as NativeHistoryLedgerResult.Durable).ledger
        val intent=NativeHistoryWriteIntent.CustomFinish("workout-2",finish(),original)
        assertTrue(history.prepare("owner-1","athlete-1",ledger.revision,intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(issued.applyPendingFinish("owner-1","workout-2",finish()) is NativeCustomJournalResult.Durable)
        val recovered=NativeKotlinCustomHistoryTransaction(history,issued,fence,identity,
            NativeHistoryCompletionClock{error("Recovery cannot invent a new observation")})
            .recover("owner-1","athlete-1") as NativeCustomFinishResult.Committed
        assertEquals(original,recovered.completion.completedAtEpochMillis)
    }

    @Test fun oneExactCustomFinishCommitsSharedHistoryOrderAndFactualEvidence() {
        logged()
        val result=transactions.complete("owner-1","athlete-1",
            "workout-2",finish())
        assertTrue(result.toString(),result is NativeCustomFinishResult.Committed)
        val durable=result as NativeCustomFinishResult.Committed
        assertFalse(durable.replayed)
        assertEquals(1L,durable.completion.completionOrder)
        assertEquals("finish-1",durable.completion.finishEventId)
        assertEquals(2L,durable.completion.originalJournalRevision)
        assertTrue(durable.completion.storedReceiptId.startsWith("custom-"))
        assertEquals(1,durable.evidence.visits.size)
        assertEquals(16,durable.evidence.visits.single().actual?.reps)
        assertEquals(1,durable.evidence.visits.single().actual?.actualRir)
        assertFalse(durable.evidence.canPublishGlobalHistory)
        assertFalse(durable.evidence.canApplyProgression)
        assertEquals(null,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.pending)
        val repeated=transactions.complete("owner-1","athlete-1",
            "workout-2",finish())
        assertEquals(durable.copy(replayed=true),repeated)
        assertEquals(1,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions.size)
        val denied=transactions.complete("owner-1","athlete-1",
            "workout-2",finish().copy(eventId="different"))
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.FINISH_EVENT_MISMATCH),denied)
    }

    @Test fun cannotFinishBeforeEverySetWasExplicitlyLogged() {
        ready()
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.FINISH_NOT_READY),
            transactions.complete("owner-1","athlete-1",
                "workout-2",CustomWorkoutJournalOperation.Finish("early",0)))
        val manifest=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertNull(manifest.pending)
        assertTrue(manifest.completions.isEmpty())
    }

    @Test fun redoRecoversAfterIntentPersistedBeforeJournalFinish() {
        logged()
        val base=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        val intent=NativeHistoryWriteIntent.CustomFinish("workout-2",finish())
        assertTrue(history.prepare("owner-1","athlete-1",base.revision,intent)
            is NativeHistoryLedgerResult.Durable)
        val pending=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.pending
        assertEquals(intent,pending)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.HISTORY_NOT_READY),
            issued.record("owner-1","workout-2",recordSet("stale",1)))
        val reopened=NativeKotlinCustomHistoryTransaction(
            NativeKotlinHistoryManifestFileStore(temp.root.toPath()),
            issued,NativeKotlinApprovalRevisionFence(temp.root.toPath()),identity)
        val recovered=reopened.recover("owner-1","athlete-1")
        assertTrue(recovered.toString(),recovered is NativeCustomFinishResult.Committed)
        assertEquals("finish-1",(issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal.finishedByEventId)
        assertEquals(null,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.pending)
    }

    @Test fun redoRecoversAfterJournalFinishButBeforeManifestFinalization() {
        logged()
        val ledger=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        val intent=NativeHistoryWriteIntent.CustomFinish("workout-2",finish())
        assertTrue(history.prepare("owner-1","athlete-1",ledger.revision,intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(issued.applyPendingFinish("owner-1","workout-2",finish())
            is NativeCustomJournalResult.Durable)
        assertEquals(intent,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.pending)
        val recovered=transactions.recover("owner-1","athlete-1")
        assertTrue(recovered.toString(),recovered is NativeCustomFinishResult.Committed)
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        assertEquals(1,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions.size)
    }

    @Test fun oldComposerHistoryReaderCannotSilentlyPublishCustomFacts() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        val legacy=NativeKotlinHistoryManifestFileAuthority(
            history,{o,a->o=="owner-1" && a=="athlete-1"})
        assertNull(legacy.currentHistoryManifest("owner-1","athlete-1"))
    }

    @Test fun accountChangeAndForeignPendingIntentCannotBeReplayed() {
        logged()
        current=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.NOT_AUTHORIZED),
            transactions.complete("owner-1","athlete-1",
                "workout-2",finish()))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.FINISH_NOT_READY),
            transactions.recover("owner-1","athlete-1"))
        val manifest=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertTrue(history.prepare("owner-1","athlete-1",manifest.revision,
            NativeHistoryWriteIntent.Finish("composer-other",
                KotlinJournalOperation.Finish("composer-event",0)))
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.FOREIGN_PENDING_INTENT),
            transactions.recover("owner-1","athlete-1"))
    }

    @Test fun tamperedFinishedJournalCannotBeMistakenForIndexedCompletion() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        val file=java.nio.file.Files.list(temp.root.toPath()).use{paths->
            paths.filter{it.fileName.toString().startsWith("issued-custom-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.JOURNAL_UNAVAILABLE),
            transactions.complete("owner-1","athlete-1",
                "workout-2",finish()))
    }

    private val generatedAuthority=object:NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey:String,workoutId:String
        ):NativeKotlinWorkoutApproval?=null
        override fun canAccessIssuedWorkout(
            ownerKey:String,source:KotlinTrainingSourceRevision
        ):Boolean=ownerKey=="owner-1" && source.athleteId=="athlete-1" &&
            current==NativeKotlinOwnerIdentity("owner-1","athlete-1")
    }

    private val customSaved by lazy {
        NativeKotlinCustomSavedCorrectionFileStore(
            temp.root.toPath(),fence,identity,issued,history)
    }
    private val customEdits by lazy {
        NativeKotlinCustomHistoryCorrectionTransaction(
            history,issued,customSaved,fence,identity)
    }
    private fun correction(
        event:String="correct-1",revision:Long=0,
        predecessor:String="set-1",reps:Int=15,
        actualRir:Int?=null
    )=CustomSavedVisitCorrection(
        event,revision,CustomWorkoutVisitKey(
            "cycle-1:w2:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
        predecessor,KotlinJournalSetOutcome.COMPLETED,
        KotlinPerformedSet(null,null,reps,actualRir))
    private fun mixedReader()=NativeKotlinMixedHistoryReadCoordinator(
        history,composerJournal,
        KotlinNativeSavedCorrectionFileStore(temp.root.toPath(),composerJournal),
        issued,generatedAuthority,identity,fence,customSaved)

    private fun finishGenerated() {
        val src=KotlinTrainingSourceRevision(
            "athlete-1",1,"generated-1",1,"gym-1",1,"catalog-1","policy-1")
        val target=KotlinWorkoutTarget(
            "bench",ComposerRole.PRIMARY,ComposerSessionPurpose.HYPERTROPHY,
            "generated-set",DraftSetType.WORKING,0,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n("200")),
            RepBand(5,8),2,60,90)
        val snapshot=KotlinStagedWorkout(
            src,"grant-generated","generated-1",0,0,
            ComposerSessionPurpose.HYPERTROPHY,40,listOf(target))
        val identity=KotlinStoredWorkoutIdentity(
            "stored-generated","generated-1",src,1)
        assertTrue(composerJournal.issue("owner-1",snapshot,identity)
            is NativeJournalFileResult.Durable)
        assertTrue(composerJournal.appendStored("owner-1","generated-1",
            KotlinJournalOperation.RecordSet(
                "generated-set-event",0,"generated-set",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("200"),LoadUnit.LB,8,2)
            ),{owner,revision->
                generatedAuthority.canAccessIssuedWorkout(owner,revision)
            }) is NativeJournalFileResult.Durable)
        val result=NativeKotlinHistoryTransactionCoordinator(
            history,composerJournal,
            KotlinNativeSavedCorrectionFileStore(temp.root.toPath(),composerJournal),
            generatedAuthority,fence
        ).complete("owner-1","athlete-1","generated-1",
            KotlinJournalOperation.Finish("finish-generated",1))
        assertTrue(result.toString(),result is NativeHistoryTransactionResult.Durable)
    }

    @Test fun mixedTimelineIncludesCustomAndGeneratedInExactFinishOrder() {
        logged()
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-2",finish())
            is NativeCustomFinishResult.Committed)
        finishGenerated()
        val result=mixedReader().read("owner-1","athlete-1")
        assertTrue(result.toString(),result is NativeKotlinMixedHistoryReadResult.Review)
        val view=(result as NativeKotlinMixedHistoryReadResult.Review).timeline
        assertEquals(listOf("workout-2","generated-1"),
            view.completions.map{it.manifest.workoutId})
        val custom=view.completions[0] as NativeKotlinMixedHistoryCompletion.Custom
        assertEquals(16,custom.evidence.visits.single().actual?.reps)
        assertEquals(1,custom.evidence.visits.single().actual?.actualRir)
        val generated=view.completions[1] as NativeKotlinMixedHistoryCompletion.Composer
        assertEquals(8,generated.evidence.sets.single().actual?.reps)
        assertEquals("generated-set-event",
            generated.evidence.sets.single().effectiveEventId)
        assertFalse(view.canPublishPr)
        assertFalse(view.canApplyProgression)
        // The old Composer-only Last/PR path stays blocked, not half-correct.
        assertNull(NativeKotlinHistoryManifestFileAuthority(
            history,{_,_->true}
        ).currentHistoryManifest("owner-1","athlete-1"))
    }

    @Test fun mixedHistoryRecomputesGeneratedSavedCorrectionsWithoutInventingCustomPr() {
        logged()
        finishGenerated()
        val corrections=KotlinNativeSavedCorrectionFileStore(
            temp.root.toPath(),composerJournal)
        val result=NativeKotlinHistoryTransactionCoordinator(
            history,composerJournal,corrections,generatedAuthority,fence
        ).correct("owner-1","athlete-1","generated-1",
            KotlinSavedSetCorrection(
                "generated-correction",0,"generated-set",
                "generated-set-event",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("195"),LoadUnit.LB,4,null)))
        assertTrue(result.toString(),result is NativeHistoryTransactionResult.Durable)
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-2",finish())
            is NativeCustomFinishResult.Committed)
        val timeline=(mixedReader().read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review).timeline
        assertEquals(listOf("generated-1","workout-2"),
            timeline.completions.map{it.manifest.workoutId})
        val generated=timeline.completions.first()
            as NativeKotlinMixedHistoryCompletion.Composer
        assertEquals(4,generated.evidence.sets.single().actual?.reps)
        assertNull(generated.evidence.sets.single().actual?.actualRir)
        assertEquals("generated-correction",
            generated.evidence.sets.single().effectiveEventId)
    }

    @Test fun mixedReaderFailsClosedForPendingRedoRevocationAndCorruption() {
        logged()
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-2",finish())
            is NativeCustomFinishResult.Committed)
        assertTrue(mixedReader().read("owner-1","athlete-1")
            is NativeKotlinMixedHistoryReadResult.Review)
        current=NativeKotlinOwnerIdentity("other","athlete-1")
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED),
            mixedReader().read("owner-1","athlete-1"))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val ledger=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        val pending=NativeHistoryWriteIntent.Finish(
            "missing",KotlinJournalOperation.Finish("missing-finish",0))
        assertTrue(history.prepare("owner-1","athlete-1",ledger.revision,pending)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION),
            mixedReader().read("owner-1","athlete-1"))
        // A forged/copied or deleted custom file must never yield partial history.
    }

    @Test fun mixedReaderRejectsTamperedCustomJournalInsteadOfDroppingWorkout() {
        logged()
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-2",finish())
            is NativeCustomFinishResult.Committed)
        finishGenerated()
        val file=java.nio.file.Files.list(temp.root.toPath()).use{paths->
            paths.filter{it.fileName.toString().startsWith("issued-custom-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.UNVERIFIED_CUSTOM),
            mixedReader().read("owner-1","athlete-1"))
    }

    @Test fun mixedReaderDistinguishesMissingEnrollmentFromCorruptLedger() {
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.NOT_ENROLLED),
            mixedReader().read("owner-1","athlete-1"))
        enroll()
        val ledgerFile=java.nio.file.Files.list(temp.root.toPath()).use{paths->
            paths.filter{it.fileName.toString().startsWith("history-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(ledgerFile)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(ledgerFile,bytes)
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.INVALID_MANIFEST),
            mixedReader().read("owner-1","athlete-1"))
    }

    @Test fun customManifestReceiptChangesWhenActualPerformanceChanges() {
        ready()
        val issued=issueWeek().issued.journal
        val base=CustomWorkoutJournalDomain.begin(
            issued.workoutId,issued.snapshot,issued.preparedConsentFingerprint)
        fun finished(reps:Int):CustomWorkoutJournalDraft {
            val recorded=CustomWorkoutJournalDomain.reduce(
                base,recordSet("identical-event",0,reps))
                as CustomWorkoutJournalDecision.Proposed
            return (CustomWorkoutJournalDomain.reduce(
                recorded.draft,
                CustomWorkoutJournalOperation.Finish("identical-finish",1))
                as CustomWorkoutJournalDecision.Proposed).draft
        }
        val one=finished(16)
        val two=finished(17)
        assertEquals(one.snapshot,two.snapshot)
        assertEquals(one.finishedByEventId,two.finishedByEventId)
        assertEquals(one.revision,two.revision)
        assertNotEquals(NativeCustomFinishReceipt.compute(
            "owner-1","athlete-1",one),
            NativeCustomFinishReceipt.compute("owner-1","athlete-1",two))
    }

    @Test fun savedCustomEditCommitsExactHistoryAndColdReopensInMixedReader() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        val original=(issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal
        val initial=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.revision
        val op=correction()
        val result=customEdits.correct("owner-1","athlete-1","workout-2",op)
        assertTrue(result.toString(),
            result is NativeCustomCorrectionTransactionResult.Committed)
        val committed=result as NativeCustomCorrectionTransactionResult.Committed
        assertFalse(committed.replayed)
        assertEquals(1L,committed.correctionRevision)
        assertEquals(initial+1,committed.manifestRevision)
        assertEquals(15,committed.evidence.evidence.visits.single().actual?.reps)
        assertNull(committed.evidence.evidence.visits.single().actual?.actualRir)
        assertEquals(original,(issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal)
        val ledger=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertNull(ledger.pending)
        assertEquals(1L,ledger.completions.single().savedCorrectionRevision)
        assertEquals(NativeCustomFinishReceipt.compute(
            "owner-1","athlete-1",original),
            ledger.completions.single().storedReceiptId)

        val reopened=NativeKotlinCustomSavedCorrectionFileStore(
            temp.root.toPath(),NativeKotlinApprovalRevisionFence(
                temp.root.toPath()),identity,issued,history)
        val edits=reopened.open("owner-1","workout-2")
            as NativeCustomSavedCorrectionResult.Opened
        assertEquals(1L,edits.state.correctionRevision)
        assertEquals(1,edits.state.receipts.size)
        assertTrue(edits.hasCorrectionFile)
        val timeline=mixedReader().read("owner-1","athlete-1")
        assertTrue(timeline.toString(),timeline is NativeKotlinMixedHistoryReadResult.Review)
        val custom=((timeline as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.single()
            as NativeKotlinMixedHistoryCompletion.Custom)
        assertEquals(15,custom.evidence.visits.single().actual?.reps)
        assertEquals("correct-1",custom.evidence.visits.single().effectiveEventId)
        val retry=customEdits.correct("owner-1","athlete-1","workout-2",op)
        assertEquals(committed.copy(replayed=true),retry)
        assertEquals(ledger,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger)
    }

    @Test fun stagedMyoCorrectionsRecoverBothCrashWindowsWithoutChangingAnotherVisit() {
        stagedMyoFixture=true
        ready()
        fun cold()=NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),NativeKotlinApprovalRevisionFence(temp.root.toPath()),
            identity,prepared,preflight,history,composerJournal)
        fun currentJournal()=(cold().open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal
        val setKey="cycle-1:w2:d0:slot-0:s1"
        fun stage(i:Int)=CustomWorkoutVisitKey(setKey,CustomWorkoutVisitPart.FOLLOW_UP,i)
        val visits=listOf(CustomWorkoutVisitKey(setKey,CustomWorkoutVisitPart.MAIN),
            stage(1),stage(2),stage(3))
        visits.forEachIndexed{index,visit->
            val journal=currentJournal()
            assertEquals(visit,CustomWorkoutJournalDomain.nextUnresolvedVisit(journal))
            val saved=cold().record("owner-1","workout-2",
                CustomWorkoutJournalOperation.Record("myo-$index",journal.revision,
                    visit,KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(null,null,if(index==0)16 else 4,1)))
            assertTrue(saved.toString(),saved is NativeCustomJournalResult.Durable)
        }
        val finish=CustomWorkoutJournalOperation.Finish("myo-finish",currentJournal().revision)
        val completed=transactions.complete("owner-1","athlete-1","workout-2",finish)
        assertTrue(completed.toString(),completed is NativeCustomFinishResult.Committed)
        val original=currentJournal()
        fun ledger()=(NativeKotlinHistoryManifestFileStore(temp.root.toPath())
            .open("owner-1","athlete-1") as NativeHistoryLedgerResult.Durable).ledger
        fun coldCorrections()=NativeKotlinCustomSavedCorrectionFileStore(
            temp.root.toPath(),NativeKotlinApprovalRevisionFence(temp.root.toPath()),
            identity,cold(),NativeKotlinHistoryManifestFileStore(temp.root.toPath()))
        fun coldTransaction()=NativeKotlinCustomHistoryCorrectionTransaction(
            NativeKotlinHistoryManifestFileStore(temp.root.toPath()),cold(),
            coldCorrections(),NativeKotlinApprovalRevisionFence(temp.root.toPath()),identity)
        fun recover(op:CustomSavedVisitCorrection,writeFirst:Boolean) {
            val before=ledger()
            val intent=NativeHistoryWriteIntent.CustomCorrect("workout-2",op)
            assertTrue(history.prepare("owner-1","athlete-1",before.revision,intent)
                is NativeHistoryLedgerResult.Durable)
            assertEquals(intent,ledger().pending)
            if(writeFirst) {
                val applied=coldCorrections().appendPending("owner-1","workout-2",op)
                assertTrue(applied.toString(),applied is NativeCustomSavedCorrectionResult.Durable)
            }
            assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
                NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION),
                mixedReader().read("owner-1","athlete-1"))
            val recovered=coldTransaction().recover("owner-1","athlete-1")
            assertTrue(recovered.toString(),recovered is NativeCustomCorrectionTransactionResult.Committed)
            val result=recovered as NativeCustomCorrectionTransactionResult.Committed
            assertEquals(op.expectedCorrectionRevision+1,result.correctionRevision)
            assertEquals(before.revision+1,result.manifestRevision)
            assertEquals(result.copy(replayed=true),coldTransaction().correct(
                "owner-1","athlete-1","workout-2",op))
            assertEquals(original,currentJournal())
            assertNull(ledger().pending)
        }
        // Correct stage 2 without suppressing the still-reachable stage 3.
        val second=CustomSavedVisitCorrection("edit-myo-2",0,stage(2),
            "myo-2",KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(null,null,5,1))
        recover(second,false)
        // Last stage can reach the floor; no later optional visit is invented.
        val third=CustomSavedVisitCorrection("edit-myo-3",1,stage(3),
            "myo-3",KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(null,null,3,1))
        recover(third,true)
        val saved=coldCorrections().open("owner-1","workout-2")
            as NativeCustomSavedCorrectionResult.Opened
        assertEquals(listOf(second,third),saved.state.receipts.map{it.correction})
        val review=mixedReader().read("owner-1","athlete-1")
        assertTrue(review.toString(),review is NativeKotlinMixedHistoryReadResult.Review)
        val facts=((review as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.single() as NativeKotlinMixedHistoryCompletion.Custom)
            .evidence.visits
        assertEquals(listOf(16,4,5,3),facts.map{it.actual?.reps})
        assertEquals(listOf("myo-0","myo-1","edit-myo-2","edit-myo-3"),
            facts.map{it.effectiveEventId})
        assertEquals(listOf(1,2,3),facts.filter{
            it.part==CustomWorkoutVisitPart.FOLLOW_UP}.map{it.stage})
        assertEquals(2L,ledger().completions.single().savedCorrectionRevision)
    }

    @Test fun customCorrectionEncodingPreflightChecksWholeEnvelopeWithoutWriting() {
        logged()
        val completed=transactions.complete("owner-1","athlete-1","workout-2",finish())
            as NativeCustomFinishResult.Committed
        val original=(customSaved.open("owner-1","workout-2")
            as NativeCustomSavedCorrectionResult.Opened).state
        val ordinary=correction()
        val proposal=(CustomSavedHistoryCorrectionDomain.reduce(original,ordinary)
            as CustomSavedCorrectionDecision.Proposed).state
        val receipt=completed.completion.storedReceiptId
        assertTrue(customSaved.canEncodeProposal("owner-1","workout-2",receipt,proposal))
        // Synthetic oversized envelope to isolate the codec budget from
        // domain validation. No bodyweight performance is modified.
        val large=KotlinPerformedSet(BigDecimal("200").setScale(60000),LoadUnit.LB,15,1)
        val receipts=List(5){i->CustomSavedVisitCorrectionReceipt(i.toLong()+1,
            ordinary.copy(eventId="large-$i",expectedCorrectionRevision=i.toLong(),
                correctsEventId=if(i==0)"set-1" else "large-"+(i-1),performed=large))}
        assertFalse(customSaved.canEncodeProposal("owner-1","workout-2",receipt,
            proposal.copy(correctionRevision=5,receipts=receipts)))
        val unchanged=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertTrue(unchanged.readyForHistoryReads)
        assertEquals(original,(customSaved.open("owner-1","workout-2")
            as NativeCustomSavedCorrectionResult.Opened).state)
        assertTrue(customEdits.correct("owner-1","athlete-1","workout-2",ordinary)
            is NativeCustomCorrectionTransactionResult.Committed)
    }

    @Test fun interruptionBeforeCustomCorrectionFileIsRecoverable() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        val ledger=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        val op=correction()
        val intent=NativeHistoryWriteIntent.CustomCorrect("workout-2",op)
        assertTrue(history.prepare("owner-1","athlete-1",ledger.revision,intent)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION),
            mixedReader().read("owner-1","athlete-1"))
        val recovered=NativeKotlinCustomHistoryCorrectionTransaction(
            NativeKotlinHistoryManifestFileStore(temp.root.toPath()),
            issued,customSaved,NativeKotlinApprovalRevisionFence(
                temp.root.toPath()),identity).recover("owner-1","athlete-1")
        assertTrue(recovered.toString(),
            recovered is NativeCustomCorrectionTransactionResult.Committed)
        val after=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertNull(after.pending)
        assertEquals(1L,after.completions.single().savedCorrectionRevision)
    }

    @Test fun interruptionAfterSavedFileBeforeManifestFinalizeReplaysSameReceipt() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        val base=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        val op=correction()
        val intent=NativeHistoryWriteIntent.CustomCorrect("workout-2",op)
        assertTrue(history.prepare("owner-1","athlete-1",base.revision,intent)
            is NativeHistoryLedgerResult.Durable)
        val written=customSaved.appendPending("owner-1","workout-2",op)
        assertTrue(written.toString(),
            written is NativeCustomSavedCorrectionResult.Durable)
        assertFalse((written as NativeCustomSavedCorrectionResult.Durable).replayed)
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION),
            mixedReader().read("owner-1","athlete-1"))
        val recovered=customEdits.recover("owner-1","athlete-1")
        assertTrue(recovered.toString(),
            recovered is NativeCustomCorrectionTransactionResult.Committed)
        val after=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertEquals(1L,after.completions.single().savedCorrectionRevision)
        assertEquals(null,after.pending)
        assertEquals(1,(customSaved.open("owner-1","workout-2")
            as NativeCustomSavedCorrectionResult.Opened).state.receipts.size)
    }

    @Test fun corruptCustomSavedFileBlocksEntireHistoryInsteadOfOldLast() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        assertTrue(customEdits.correct("owner-1","athlete-1",
            "workout-2",correction())
            is NativeCustomCorrectionTransactionResult.Committed)
        val file=java.nio.file.Files.list(temp.root.toPath()).use { paths ->
            paths.filter { it.fileName.toString().startsWith("corrected-custom-") &&
                it.fileName.toString().endsWith(".bin") }.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeCustomSavedCorrectionResult.Rejected(
            NativeCustomSavedCorrectionRefusal.CORRUPT_FILE),
            customSaved.open("owner-1","workout-2"))
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.UNVERIFIED_CUSTOM),
            mixedReader().read("owner-1","athlete-1"))
        assertEquals(NativeCustomCorrectionTransactionResult.Rejected(
            NativeCustomCorrectionTransactionRefusal.HISTORY_RECEIPT_MISMATCH),
            customEdits.correct("owner-1","athlete-1","workout-2",
                correction(event="other",revision=1,
                    predecessor="correct-1",reps=16)))
    }

    @Test fun customCorrectionRefusesWrongRevisionAndForeignPendingWriter() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        assertEquals(NativeCustomCorrectionTransactionResult.Rejected(
            NativeCustomCorrectionTransactionRefusal.CORRECTION_REJECTED,
            CustomSavedCorrectionRefusal.REVISION_CONFLICT),
            customEdits.correct("owner-1","athlete-1",
                "workout-2",correction(revision=8)))
        val ledger=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertTrue(history.prepare("owner-1","athlete-1",ledger.revision,
            NativeHistoryWriteIntent.Finish("other",
                KotlinJournalOperation.Finish("other-finish",0)))
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeCustomCorrectionTransactionResult.Rejected(
            NativeCustomCorrectionTransactionRefusal.FOREIGN_PENDING_INTENT),
            customEdits.correct("owner-1","athlete-1",
                "workout-2",correction()))
        current=NativeKotlinOwnerIdentity("foreign-owner","athlete-1")
        assertEquals(NativeCustomCorrectionTransactionResult.Rejected(
            NativeCustomCorrectionTransactionRefusal.NOT_AUTHORIZED),
            customEdits.recover("owner-1","athlete-1"))
    }
    private fun independentReceiptVerifier() =
        NativeKotlinIndependentHistoryReceiptVerifier(
            composerJournal,
            KotlinNativeSavedCorrectionFileStore(temp.root.toPath(), composerJournal),
            issued, customSaved, generatedAuthority, fence, identity)

    @Test fun independentCustomFinishVerifierReplaysOriginalDurableJournal() {
        logged()
        val completed = transactions.complete(
            "owner-1", "athlete-1", "workout-2", finish())
        assertTrue(completed.toString(), completed is NativeCustomFinishResult.Committed)
        val entry = (completed as NativeCustomFinishResult.Committed).completion
        val intent = NativeHistoryWriteIntent.CustomFinish("workout-2", finish())
        val verified = independentReceiptVerifier().verify(
            "owner-1", "athlete-1", intent, entry)
        assertTrue(verified.toString(),
            verified is NativeIndependentReceiptResult.Verified)
        val evidence = (verified as NativeIndependentReceiptResult.Verified).evidence
        assertEquals(entry, evidence.entry)
        assertEquals("finish-1", evidence.intentEventId)
        assertTrue(evidence.isCustom)
        assertFalse(evidence.provesCompleteHistory)
        assertFalse(evidence.canPublishHistory)
        assertFalse(evidence.canIssueProgression)
        assertEquals(NativeIndependentReceiptResult.Rejected(
            NativeIndependentReceiptRefusal.FINISH_MISMATCH),
            independentReceiptVerifier().verify(
                "owner-1", "athlete-1", intent,
                entry.copy(storedReceiptId = "custom-v2-forged")))
    }

    @Test fun independentCustomCorrectionVerifierRequiresExactLatestEdit() {
        logged()
        val finished = transactions.complete(
            "owner-1", "athlete-1", "workout-2", finish())
        assertTrue(finished is NativeCustomFinishResult.Committed)
        val original = (finished as NativeCustomFinishResult.Committed).completion
        val change = correction()
        val correctionIntent = NativeHistoryWriteIntent.CustomCorrect(
            "workout-2", change)
        val proposedEntry = original.copy(savedCorrectionRevision = 1)
        assertEquals(NativeIndependentReceiptResult.Rejected(
            NativeIndependentReceiptRefusal.CORRECTION_MISMATCH),
            independentReceiptVerifier().verify(
                "owner-1", "athlete-1", correctionIntent, proposedEntry))
        assertTrue(customEdits.correct(
            "owner-1", "athlete-1", "workout-2", change)
            is NativeCustomCorrectionTransactionResult.Committed)
        val approved = independentReceiptVerifier().verify(
            "owner-1", "athlete-1", correctionIntent, proposedEntry)
        assertTrue(approved.toString(), approved is NativeIndependentReceiptResult.Verified)
        assertEquals("correct-1",
            (approved as NativeIndependentReceiptResult.Verified)
                .evidence.intentEventId)
        assertEquals(NativeIndependentReceiptResult.Rejected(
            NativeIndependentReceiptRefusal.CORRECTION_MISMATCH),
            independentReceiptVerifier().verify(
                "owner-1", "athlete-1",
                correctionIntent.copy(operation = change.copy(eventId = "fabricated")),
                proposedEntry))
        assertEquals(NativeIndependentReceiptResult.Rejected(
            NativeIndependentReceiptRefusal.CORRECTION_MISMATCH),
            independentReceiptVerifier().verify(
                "owner-1", "athlete-1",
                NativeHistoryWriteIntent.CustomFinish("workout-2", finish()),
                original))
    }

}
