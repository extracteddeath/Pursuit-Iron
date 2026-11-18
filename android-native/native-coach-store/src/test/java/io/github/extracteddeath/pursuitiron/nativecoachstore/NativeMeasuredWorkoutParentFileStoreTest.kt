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
}
