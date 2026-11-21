package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real files and cold-source replay; NO in-memory mock persistence. */
class NativeMeasuredWorkoutParentFileStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private val root by lazy{temp.root.toPath()}
    private val fence by lazy{NativeKotlinApprovalRevisionFence(root)}
    private var current:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-one","athlete-one")
    private val identity=NativeKotlinCurrentIdentity{current}
    private var consent:NativeMeasuredApprovedWorkout?=null
    private var sourceReads=0
    private val source=NativeMeasuredApprovedWorkoutSource{owner,athlete,workout->
        sourceReads++
        consent?.takeIf{owner=="owner-one" && athlete=="athlete-one" &&
            workout==it.plan.workoutId}
    }
    private fun store()=NativeMeasuredWorkoutParentFileStore(
        root,fence,identity,source)
    private fun n(v:String)=BigDecimal(v)
    private fun plan(id:String,index:Int):MeasuredExerciseSetPlan {
        val d=checkNotNull(MeasuredExerciseCatalogDomain.find(id))
        val sides=when(d.sideRule) {
            MeasuredSideRule.BOTH ->setOf(MeasuredSide.BOTH)
            MeasuredSideRule.LEFT_AND_RIGHT ->setOf(
                MeasuredSide.LEFT,MeasuredSide.RIGHT)
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE ->setOf(MeasuredSide.LEFT)
        }
        val loaded=d.loadKind in setOf(
            MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND)
        val band=d.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT
        return MeasuredExerciseSetPlan("workout-one","set-$index",
            id,"gym-one",3,"station-$index",when(d.kind){
                MeasuredExerciseKind.HOLD_SECONDS->30
                MeasuredExerciseKind.CARRY_CENTIMETERS->1000
                MeasuredExerciseKind.BANDED_REPETITIONS->12
            },sides,d.requiredFixtures,
            if(loaded)VerifiedLoadStock.Totals(LoadUnit.LB,
                checkNotNull(d.expectedBasis),listOf(n("10"),n("20"),n("30")))
            else null,
            if(loaded)n("20")else null,
            if(loaded)LoadUnit.LB else null,
            id=="farmers",
            if(band)"blue-band"else null,
            if(band)setOf("blue-band")else emptySet())
    }
    private fun approved(all:Boolean=false):MeasuredWorkoutPlan {
        val ids=MeasuredExerciseCatalogDomain.all.map{it.exerciseId}
        val used=if(all)ids else listOf("plank","side-plank","farmers","band-pushup")
        val p=MeasuredWorkoutPlan("workout-one","gym-one",3,
            used.mapIndexed{i,id->plan(id,i+1)})
        consent=NativeMeasuredApprovedWorkout(p,"approval-one",7)
        return p
    }
    private fun actual(plan:MeasuredExerciseSetPlan,
        amount:Int=25):MeasuredExerciseActual {
        val definition=checkNotNull(
            MeasuredExerciseCatalogDomain.find(plan.exerciseId))
        val loaded=definition.loadKind in setOf(
            MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND)
        return MeasuredExerciseActual(plan.physicalSetupId,
            plan.sides.sortedBy{it.ordinal}.map{
                MeasuredSideResult(it,amount)},
            if(loaded)n("20")else null,
            if(loaded)LoadUnit.LB else null,plan.bandId,
            if(definition.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)31
            else null)
    }
    private fun saved(result:NativeMeasuredWorkoutStoreResult):
        NativeMeasuredWorkoutStoreResult.Durable {
        assertTrue(result.toString(),result is NativeMeasuredWorkoutStoreResult.Durable)
        return result as NativeMeasuredWorkoutStoreResult.Durable
    }
    private fun denied(result:NativeMeasuredWorkoutStoreResult,
        kind:NativeMeasuredWorkoutStoreRefusal) {
        assertTrue(result.toString(),result is NativeMeasuredWorkoutStoreResult.Rejected)
        assertEquals(kind,(result as NativeMeasuredWorkoutStoreResult.Rejected).reason)
    }
    private fun op(plan:MeasuredWorkoutPlan,index:Int,rev:Long,
        event:String="event-$index",corrects:String?=null,
        amount:Int=25)=MeasuredWorkoutOperation.Record(event,rev,
        "set-$index",MeasuredSetOutcome.COMPLETED,
        actual(plan.sets[index-1],amount),corrects)

    @Test fun exhaustedCorrectionBudgetStillAllowsRemainingSetsAndFinishAfterColdReplay() {
        val sets=(1..9).map { plan("plank",it) }
        val workout=MeasuredWorkoutPlan("workout-one","gym-one",3,sets)
        var journal=checkNotNull(MeasuredWorkoutJournalDomain.begin(workout))
        fun append(operation:MeasuredWorkoutOperation) {
            journal=(MeasuredWorkoutJournalDomain.reduce(journal,operation)
                as MeasuredWorkoutDecision.Proposed).journal
        }
        fun reopen() {
            val stored=NativeMeasuredWorkoutStored("owner-one","athlete-one","approval",1,journal)
            val bytes=NativeMeasuredWorkoutParentCodec.encode(stored)
            val cold=NativeMeasuredWorkoutParentCodec.decode(bytes,"owner-one","athlete-one")
            assertEquals(stored,cold)
            journal=cold.journal
        }
        for(round in 0 until 128) for(index in 1..8) {
            append(op(workout,index,journal.revision,"r-$round-$index",
                if(round==0)null else "r-${round-1}-$index"))
        }
        reopen()
        val child=journal.bySetKey.getValue("set-1")
        val closedChild=(MeasuredExerciseJournalDomain.reduce(child,
            MeasuredJournalOperation.Finish("child-finish",128))
            as MeasuredJournalDecision.Proposed).journal
        assertEquals(closedChild,NativeMeasuredExerciseJournalCodec.decode(
            NativeMeasuredExerciseJournalCodec.encode("owner-one","athlete-one",closedChild),
            "owner-one","athlete-one"))
        assertEquals(MeasuredWorkoutRefusal.TOO_MANY_RECEIPTS,
            (MeasuredWorkoutJournalDomain.reduce(journal,
                op(workout,1,1024,"extra","r-127-1")) as MeasuredWorkoutDecision.Rejected).reason)
        append(op(workout,9,1024))
        val finish=MeasuredWorkoutOperation.Finish("finish",1025)
        append(finish)
        reopen()
        assertTrue((MeasuredWorkoutJournalDomain.reduce(journal,finish)
            as MeasuredWorkoutDecision.Proposed).replayed)
    }

    @Test fun coldReopenEveryTypedSetAndOwnerFenceBeforeHistoryIntegration() {
        val p=approved(all=true)
        val store=store()
        val start=saved(store.issue("owner-one","workout-one"))
        assertFalse(start.replayed)
        assertEquals(p,start.stored.journal.approvedPlan)
        assertFalse(start.stored.canFinishWorkout)
        assertFalse(start.stored.mayPublishHistory)
        var state=start.stored.journal
        for(i in 1..p.sets.size) {
            val next=saved(store.record("owner-one","workout-one",
                op(p,i,state.revision)))
            assertFalse(next.replayed)
            state=next.stored.journal
            val cold=saved(store().open("owner-one","workout-one"))
            assertEquals(state,cold.stored.journal)
            assertEquals(7L,cold.stored.approvalRevision)
        }
        assertEquals(14L,state.revision)
        assertTrue(state.canProposeFinish)
        denied(store.finish("owner-one","workout-one",
            MeasuredWorkoutOperation.Finish("finish",state.revision)),
            NativeMeasuredWorkoutStoreRefusal.HISTORY_TRANSACTION_REQUIRED)
        assertNull(saved(store().open("owner-one","workout-one"))
            .stored.journal.finishedByEventId)
        assertEquals(1,sourceReads)
    }

    @Test fun changingSourceDoesNotRewriteAlreadyIssuedWorkout() {
        val p=approved()
        val issued=saved(store().issue("owner-one","workout-one")).stored
        consent=NativeMeasuredApprovedWorkout(
            p.copy(gymRevision=4),"changed",8)
        assertTrue(saved(store().issue("owner-one","workout-one")).replayed)
        assertEquals(issued,saved(store().open("owner-one","workout-one")).stored)
        val one=op(p,1,0)
        val logged=saved(store().record("owner-one","workout-one",one))
        assertEquals(1L,logged.stored.journal.revision)
        assertTrue(saved(store().record("owner-one","workout-one",one)).replayed)
        val collision=store().record("owner-one","workout-one",
            one.copy(actual=actual(p.sets.first(),30)))
        denied(collision,NativeMeasuredWorkoutStoreRefusal.STALE_OR_INVALID_RECEIPT)
        assertEquals(logged.stored,saved(store().open(
            "owner-one","workout-one")).stored)
        val corrected=op(p,1,1,event="correction",
            corrects="event-1",amount=30)
        assertEquals(2L,saved(store().record("owner-one",
            "workout-one",corrected)).stored.journal.revision)
    }

    @Test fun missingApprovalWrongOwnerAndCorruptedDiskCannotBecomeEmpty() {
        val store=store()
        denied(store.open("owner-one","workout-one"),
            NativeMeasuredWorkoutStoreRefusal.NOT_ISSUED)
        denied(store.issue("owner-one","workout-one"),
            NativeMeasuredWorkoutStoreRefusal.APPROVAL_UNAVAILABLE)
        approved()
        saved(store.issue("owner-one","workout-one"))
        current=null
        denied(store.open("owner-one","workout-one"),
            NativeMeasuredWorkoutStoreRefusal.NOT_AUTHORIZED)
        current=NativeKotlinOwnerIdentity("owner-one","other-athlete")
        denied(store.open("owner-one","workout-one"),
            NativeMeasuredWorkoutStoreRefusal.CORRUPT_RECORD)
        current=NativeKotlinOwnerIdentity("owner-one","athlete-one")
        val file=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith(
                "measured-parent-") && it.fileName.toString().endsWith(".bin")}
                .findFirst().orElseThrow()
        }
        val original=Files.readAllBytes(file)
        val corrupted=original.copyOf().also {
            it[it.size/2]=(it[it.size/2].toInt() xor 8).toByte()
        }
        Files.write(file,corrupted)
        denied(store.open("owner-one","workout-one"),
            NativeMeasuredWorkoutStoreRefusal.CORRUPT_RECORD)
        denied(store.issue("owner-one","workout-one"),
            NativeMeasuredWorkoutStoreRefusal.CORRUPT_RECORD)
        assertArrayEquals(corrupted,Files.readAllBytes(file))
    }

    @Test fun parentArchiveRefusesForgedGlobalReceiptOrderAfterChecksum() {
        val p=approved()
        val initial=saved(store().issue("owner-one","workout-one")).stored
        val one=saved(store().record("owner-one","workout-one",
            op(p,1,0))).stored
        val body=NativeMeasuredWorkoutParentCodec.encode(one)
        assertEquals(one,NativeMeasuredWorkoutParentCodec.decode(
            body,"owner-one","athlete-one"))
        try {
            NativeMeasuredWorkoutParentCodec.decode(body,
                "owner-one","other-athlete")
            fail("A different athlete cannot decode the parent")
        } catch(_:java.io.IOException) {}
        val invalid=one.copy(journal=one.journal.copy(
            receipts=one.journal.receipts.map{
                it.copy(revision=4)
            }))
        try {
            NativeMeasuredWorkoutParentCodec.encode(invalid)
            fail("Unreplayable parent receipts cannot be serialized")
        } catch(_:IllegalArgumentException) {}
        assertNull(initial.journal.finishedByEventId)
    }

    private val canonicalHistory by lazy {
        NativeKotlinHistoryManifestFileStore(root)
    }
    private fun enrollHistory() {
        val enrolled=canonicalHistory.enroll("owner-one","athlete-one") {o,a->
            current==NativeKotlinOwnerIdentity(o,a) &&
                o=="owner-one" && a=="athlete-one"
        }
        assertTrue(enrolled.toString(),enrolled is NativeHistoryLedgerResult.Durable)
    }
    private fun indexedStore()=NativeMeasuredWorkoutParentFileStore(
        root,fence,identity,source,canonicalHistory)
    private fun historyTransaction(
        clock:NativeHistoryCompletionClock=NativeHistoryCompletionClock.Undated
    )=NativeKotlinMeasuredHistoryTransaction(canonicalHistory,indexedStore(),
        fence,identity,clock)

    private fun recordAll(p:MeasuredWorkoutPlan) {
        var revision=0L
        p.sets.forEachIndexed{index,_->
            val result=saved(indexedStore().record("owner-one","workout-one",
                op(p,index+1,revision)))
            revision=result.stored.journal.revision
        }
    }
    private fun durableFinish(result:NativeMeasuredFinishResult):
        NativeMeasuredFinishResult.Committed {
        assertTrue(result.toString(),result is NativeMeasuredFinishResult.Committed)
        return result as NativeMeasuredFinishResult.Committed
    }

    @Test fun measuredFinishSharesEnrolledCanonicalHistoryAndColdRetryDoesNotDuplicate() {
        val p=approved(all=true)
        enrollHistory()
        val pre=canonicalHistory.open("owner-one","athlete-one")
            as NativeHistoryLedgerResult.Durable
        assertTrue(pre.ledger.readyForHistoryReads)
        assertFalse(pre.ledger.completions.any{
            it.storedReceiptId.startsWith("measured-v1-")})
        saved(indexedStore().issue("owner-one","workout-one"))
        recordAll(p)
        var clocks=0
        val tx=historyTransaction(NativeHistoryCompletionClock {
            clocks++;1_796_000_000_000L
        })
        val finish=MeasuredWorkoutOperation.Finish("finish-14",14)
        denied(indexedStore().finish("owner-one","workout-one",finish),
            NativeMeasuredWorkoutStoreRefusal.HISTORY_TRANSACTION_REQUIRED)
        val committed=durableFinish(tx.complete("owner-one","athlete-one",
            "workout-one",finish))
        assertEquals(1,clocks)
        assertEquals("finish-14",committed.completion.finishEventId)
        assertTrue(committed.completion.storedReceiptId.startsWith("measured-v1-"))
        assertEquals(15L,committed.completion.originalJournalRevision)
        assertEquals(0L,committed.completion.savedCorrectionRevision)
        assertEquals(1_796_000_000_000L,
            committed.completion.completedAtEpochMillis)
        assertEquals(1L,committed.completion.completionOrder)
        assertEquals(15L,committed.stored.journal.revision)
        assertEquals("finish-14",committed.stored.journal.finishedByEventId)
        assertFalse(committed.canChangeProgram)
        assertFalse(committed.canIssueProgression)
        val cold=(canonicalHistory.open("owner-one","athlete-one")
            as NativeHistoryLedgerResult.Durable).ledger
        assertTrue(cold.readyForHistoryReads)
        assertEquals(listOf(committed.completion),cold.completions)
        val reopened=saved(indexedStore().open("owner-one","workout-one"))
        assertEquals(committed.stored,reopened.stored)
        assertEquals(committed.completion, durableFinish(historyTransaction(
            NativeHistoryCompletionClock {error("Cannot replace original Finish time")}
        ).complete("owner-one","athlete-one","workout-one",finish)).completion)
        assertEquals(1,cold.completions.size)
        denied(indexedStore().record("owner-one","workout-one",
            op(p,1,15,event="post-finish")),
            NativeMeasuredWorkoutStoreRefusal.HISTORY_TRANSACTION_REQUIRED)
    }

    @Test fun canonicalRedoWorksBeforeAndAfterParentFinishFileCommit() {
        for(afterJournal in listOf(false,true)) {
            // Each scenario has independent on-disk authority and receipt
            // source. Never clear a pending ledger to repair interrupted IO.
            val folder=TemporaryFolder()
            folder.create()
            val dir=folder.root.toPath()
            val lock=NativeKotlinApprovalRevisionFence(dir)
            val ledger=NativeKotlinHistoryManifestFileStore(dir)
            val signed=NativeKotlinCurrentIdentity{
                NativeKotlinOwnerIdentity("owner-one","athlete-one")
            }
            val p=approved()
            val measured=NativeMeasuredWorkoutParentFileStore(dir,lock,
                signed,source,ledger)
            val txn=NativeKotlinMeasuredHistoryTransaction(ledger,measured,
                lock,signed,NativeHistoryCompletionClock {
                    error("A recovered intent must keep the original timestamp")
                })
            assertTrue(ledger.enroll("owner-one","athlete-one"){_,_->true}
                is NativeHistoryLedgerResult.Durable)
            assertTrue(measured.issue("owner-one","workout-one")
                is NativeMeasuredWorkoutStoreResult.Durable)
            var revision=0L
            p.sets.forEachIndexed{i,_->
                val old=measured.record("owner-one","workout-one",
                    op(p,i+1,revision)) as NativeMeasuredWorkoutStoreResult.Durable
                revision=old.stored.journal.revision
            }
            val event=MeasuredWorkoutOperation.Finish("redo-finish",revision)
            val old=(ledger.open("owner-one","athlete-one")
                as NativeHistoryLedgerResult.Durable).ledger
            val intent=NativeHistoryWriteIntent.MeasuredFinish(
                "workout-one",event,1_796_000_007_000L)
            assertTrue(ledger.prepare("owner-one","athlete-one",old.revision,
                intent) is NativeHistoryLedgerResult.Durable)
            if(afterJournal) {
                val applied=measured.applyPendingFinish("owner-one",
                    "workout-one",event)
                assertTrue(applied.toString(),
                    applied is NativeMeasuredWorkoutStoreResult.Durable)
            }
            assertEquals(intent,(ledger.open("owner-one","athlete-one")
                as NativeHistoryLedgerResult.Durable).ledger.pending)
            denied(measured.record("owner-one","workout-one",
                MeasuredWorkoutOperation.Record("during-pending",revision,
                    "set-1",MeasuredSetOutcome.SKIPPED,null)),
                NativeMeasuredWorkoutStoreRefusal.HISTORY_TRANSACTION_REQUIRED)
            val done=txn.recover("owner-one","athlete-one")
            val committed=durableFinish(done)
            assertEquals("redo-finish",committed.completion.finishEventId)
            assertEquals(1_796_000_007_000L,
                committed.completion.completedAtEpochMillis)
            assertNull((ledger.open("owner-one","athlete-one")
                as NativeHistoryLedgerResult.Durable).ledger.pending)
            assertTrue(durableFinish(txn.complete("owner-one","athlete-one",
                "workout-one",event)).replayed)
            folder.delete()
        }
    }

    @Test fun measuredCompletionDoesNotConsumeAnotherJournalIntent() {
        val p=approved()
        enrollHistory()
        saved(indexedStore().issue("owner-one","workout-one"))
        recordAll(p)
        val before=(canonicalHistory.open("owner-one","athlete-one")
            as NativeHistoryLedgerResult.Durable).ledger
        val foreign=NativeHistoryWriteIntent.Finish("other-workout",
            KotlinJournalOperation.Finish("composer-event",0))
        assertTrue(canonicalHistory.prepare("owner-one","athlete-one",
            before.revision,foreign) is NativeHistoryLedgerResult.Durable)
        val operation=MeasuredWorkoutOperation.Finish("measured-event",
            p.sets.size.toLong())
        val result=historyTransaction().complete("owner-one","athlete-one",
            "workout-one",operation)
        assertEquals(NativeMeasuredFinishRefusal.FOREIGN_PENDING_INTENT,
            (result as NativeMeasuredFinishResult.Rejected).reason)
        assertEquals(foreign,(canonicalHistory.open("owner-one","athlete-one")
            as NativeHistoryLedgerResult.Durable).ledger.pending)
        assertNull(saved(indexedStore().open("owner-one","workout-one"))
            .stored.journal.finishedByEventId)
    }

    @Test fun corruptedParentAfterPreparedHistoryRemainsPendingAndNeverFabricatesFinish() {
        val p=approved()
        enrollHistory()
        saved(indexedStore().issue("owner-one","workout-one"))
        recordAll(p)
        val open=(canonicalHistory.open("owner-one","athlete-one")
            as NativeHistoryLedgerResult.Durable).ledger
        val intent=NativeHistoryWriteIntent.MeasuredFinish("workout-one",
            MeasuredWorkoutOperation.Finish("corrupt-finish",
                p.sets.size.toLong()),null)
        assertTrue(canonicalHistory.prepare("owner-one","athlete-one",
            open.revision,intent) is NativeHistoryLedgerResult.Durable)
        val file=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("measured-parent-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        Files.write(file,Files.readAllBytes(file).also{
            it[20]=(it[20].toInt() xor 1).toByte()
        })
        val result=historyTransaction().recover("owner-one","athlete-one")
        assertEquals(NativeMeasuredFinishRefusal.JOURNAL_UNAVAILABLE,
            (result as NativeMeasuredFinishResult.Rejected).reason)
        val after=(canonicalHistory.open("owner-one","athlete-one")
            as NativeHistoryLedgerResult.Durable).ledger
        assertEquals(intent,after.pending)
        assertFalse(after.readyForHistoryReads)
        assertTrue(after.completions.isEmpty())
    }

    private fun typedRead()=NativeKotlinMeasuredHistoryReadCoordinator(
        canonicalHistory,indexedStore(),fence,identity)

    @Test fun typedHistoryUsesSameCanonicalFinishAndRetainsExactHoldCarryBandFacts() {
        val p=approved(all=true)
        enrollHistory()
        val before=typedRead().read("owner-one","athlete-one")
            as NativeMeasuredHistoryReadResult.Review
        assertTrue(before.history.verifiedMeasured.isEmpty())
        assertFalse(before.history.provesAllHistory)
        saved(indexedStore().issue("owner-one","workout-one"))
        recordAll(p)
        val done=durableFinish(historyTransaction().complete(
            "owner-one","athlete-one","workout-one",
            MeasuredWorkoutOperation.Finish("typed-history-finish",14)))
        val fact=typedRead().read("owner-one","athlete-one")
        assertTrue(fact.toString(),fact is NativeMeasuredHistoryReadResult.Review)
        val report=(fact as NativeMeasuredHistoryReadResult.Review).history
        assertEquals(1,report.verifiedMeasured.size)
        val entry=report.verifiedMeasured.single()
        assertEquals(done.completion,entry.manifest)
        assertEquals(p,entry.stored.journal.approvedPlan)
        assertEquals(14,entry.actualSets.size)
        assertEquals("gym-one",entry.gymId)
        assertFalse(report.mayPublishPr)
        assertFalse(report.mayApplyProgression)
        assertFalse(entry.mayPublishPr)
        assertFalse(entry.mayApplyProgression)
        assertEquals(report, (typedRead().read("owner-one","athlete-one")
            as NativeMeasuredHistoryReadResult.Review).history)
    }

    @Test fun typedHistoryRefusesPendingOrCorruptedMeasuredFinishInsteadOfDroppingIt() {
        val p=approved()
        enrollHistory()
        saved(indexedStore().issue("owner-one","workout-one"))
        recordAll(p)
        val old=(canonicalHistory.open("owner-one","athlete-one")
            as NativeHistoryLedgerResult.Durable).ledger
        val operation=MeasuredWorkoutOperation.Finish("typed-recover",4)
        val intent=NativeHistoryWriteIntent.MeasuredFinish(
            "workout-one",operation)
        assertTrue(canonicalHistory.prepare("owner-one","athlete-one",
            old.revision,intent) is NativeHistoryLedgerResult.Durable)
        val pending=typedRead().read("owner-one","athlete-one")
        assertEquals(NativeMeasuredHistoryRefusal.PENDING_TRANSACTION,
            (pending as NativeMeasuredHistoryReadResult.Rejected).reason)
        assertTrue(historyTransaction().recover("owner-one","athlete-one")
            is NativeMeasuredFinishResult.Committed)
        current=NativeKotlinOwnerIdentity("owner-one","other-athlete")
        val unauthorized=typedRead().read("owner-one","athlete-one")
        assertEquals(NativeMeasuredHistoryRefusal.NOT_AUTHORIZED,
            (unauthorized as NativeMeasuredHistoryReadResult.Rejected).reason)
        current=NativeKotlinOwnerIdentity("owner-one","athlete-one")
        val file=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("measured-parent-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        Files.write(file,Files.readAllBytes(file).also{
            it[21]=(it[21].toInt() xor 7).toByte()
        })
        val corrupted=typedRead().read("owner-one","athlete-one")
        assertEquals(NativeMeasuredHistoryRefusal.UNVERIFIED_MEASUREMENT,
            (corrupted as NativeMeasuredHistoryReadResult.Rejected).reason)
        assertEquals(1,(canonicalHistory.open("owner-one","athlete-one")
            as NativeHistoryLedgerResult.Durable).ledger.completions.size)
    }

    @Test fun typedHistoryNeverInventsCompletionsBeforeExplicitEnrollment() {
        val absent=typedRead().read("owner-one","athlete-one")
        assertEquals(NativeMeasuredHistoryRefusal.NOT_ENROLLED,
            (absent as NativeMeasuredHistoryReadResult.Rejected).reason)
        enrollHistory()
        val empty=typedRead().read("owner-one","athlete-one")
        assertTrue(empty is NativeMeasuredHistoryReadResult.Review)
        assertEquals(0,(empty as NativeMeasuredHistoryReadResult.Review)
            .history.verifiedMeasured.size)
        assertFalse(empty.history.provesAllHistory)
        assertFalse(empty.history.mayPublishPr)
        current=null
        assertEquals(NativeMeasuredHistoryRefusal.NOT_AUTHORIZED,
            (typedRead().read("owner-one","athlete-one")
                as NativeMeasuredHistoryReadResult.Rejected).reason)
    }

    @Test fun measuredChronologyExportsRealUnitsWithoutInventingRepetitionPrs() {
        val p=approved(all=true)
        enrollHistory()
        saved(indexedStore().issue("owner-one","workout-one"))
        recordAll(p)
        val committed=durableFinish(historyTransaction().complete(
            "owner-one","athlete-one","workout-one",
            MeasuredWorkoutOperation.Finish("mixed-typed-finish",14)))
        val timeline=NativeKotlinMixedHistoryTimeline("athlete-one",
            committed.manifestRevision,listOf(
                NativeKotlinMixedHistoryCompletion.Measured(
                    committed.completion,committed.stored)))
        val csv=NativeKotlinVerifiedHistoryCsvExport.prepare(timeline)
        assertTrue(csv.toString(),csv is NativeKotlinHistoryCsvResult.Ready)
        val output=csv as NativeKotlinHistoryCsvResult.Ready
        assertEquals(1,output.sessionCount)
        assertEquals(14,output.rowCount)
        for(expected in listOf("pursuit-iron-history-v4",
            "measurement_kind","measurement_unit","measured_sides",
            "physical_setup_id","band_id","target_measurement",
            "actual_carry_seconds","HOLD_SECONDS","CARRY_CENTIMETERS",
            "BANDED_REPETITIONS","SECONDS","CENTIMETERS","REPETITIONS",
            "LEFT:25|RIGHT:25","blue-band")) {
            assertTrue("Missing exact typed History export column or fact: "+
                expected,output.csv.contains(expected))
        }
        val facts=NativeKotlinMixedHistoryFactsDomain.project(timeline)
        assertTrue(facts.toString(),facts is NativeMixedFactsResult.Review)
        val verified=(facts as NativeMixedFactsResult.Review).facts
        assertEquals(listOf("workout-one"),verified.completedWorkoutIds)
        assertTrue(verified.lastByExercise.isEmpty())
        assertTrue(verified.highestCompletedLoad.isEmpty())
        assertTrue(verified.bestLoggedSingleByLoad.isEmpty())
        assertFalse(verified.canPublishPr)
        assertFalse(verified.canIssueProgression)
    }
}
