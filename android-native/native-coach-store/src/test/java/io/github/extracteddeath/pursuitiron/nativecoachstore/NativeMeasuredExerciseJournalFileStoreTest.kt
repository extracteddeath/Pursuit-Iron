package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Real temporary files, owner-fence and fresh-file reopen tests, not merely
 * a fake in-memory journal. Issuance authority is test-owned; production
 * still needs an approved, source-verified native parent workout integration.
 */
class NativeMeasuredExerciseJournalFileStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private val root by lazy{temp.root.toPath()}
    private val fence by lazy{NativeKotlinApprovalRevisionFence(root)}
    private var current:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-one","athlete-one")
    private val identity=NativeKotlinCurrentIdentity{current}
    private val authoritative=linkedMapOf<String,NativeMeasuredApprovedSet>()
    private var approvalsRead=0
    private val source=NativeMeasuredApprovedSetSource{owner,athlete,workout,set->
        approvalsRead++
        authoritative[set]?.takeIf{
            owner=="owner-one" && athlete=="athlete-one" &&
                workout==it.plan.workoutId && set==it.plan.setKey
        }
    }
    private fun store()=NativeMeasuredExerciseJournalFileStore(
        root,fence,identity,source)
    private fun n(v:String)=BigDecimal(v)
    private fun plan(id:String):MeasuredExerciseSetPlan {
        val d=MeasuredExerciseCatalogDomain.find(id)!!
        val sides=when(d.sideRule) {
            MeasuredSideRule.BOTH ->setOf(MeasuredSide.BOTH)
            MeasuredSideRule.LEFT_AND_RIGHT ->
                setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE ->setOf(MeasuredSide.LEFT)
        }
        val loaded=d.loadKind in setOf(MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND)
        val band=d.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT
        return MeasuredExerciseSetPlan(
            "workout-one","set-"+id,id,"gym-one",1,
            "physical-"+id,when(d.kind) {
                MeasuredExerciseKind.HOLD_SECONDS ->30
                MeasuredExerciseKind.CARRY_CENTIMETERS ->1500
                MeasuredExerciseKind.BANDED_REPETITIONS ->12
            },sides,d.requiredFixtures,
            if(loaded)VerifiedLoadStock.Totals(LoadUnit.LB,
                checkNotNull(d.expectedBasis),listOf(n("10"),n("20"),n("30")))
            else null,
            if(loaded)n("20") else null,
            if(loaded)LoadUnit.LB else null,
            id=="farmers",
            if(band)"blue-band" else null,
            if(band)setOf("blue-band") else emptySet()
        )
    }
    private fun approve(p:MeasuredExerciseSetPlan):MeasuredExerciseSetPlan {
        authoritative[p.setKey]=NativeMeasuredApprovedSet(
            p,"approval-"+p.exerciseId,1)
        return p
    }
    private fun actual(p:MeasuredExerciseSetPlan,amount:Int=24,
        mass:String="20.000"):MeasuredExerciseActual {
        val def=MeasuredExerciseCatalogDomain.find(p.exerciseId)!!
        val loaded=def.loadKind==MeasuredLoadKind.VERIFIED_ADDED_LOAD ||
            def.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND
        return MeasuredExerciseActual(p.physicalSetupId,
            p.sides.sortedBy{it.ordinal}.map{MeasuredSideResult(it,amount)},
            if(loaded)n(mass) else null,
            if(loaded)LoadUnit.LB else null,
            p.bandId,
            if(def.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)42 else null)
    }
    private fun issued(result:NativeMeasuredStoreResult):NativeMeasuredStoreResult.Durable {
        assertTrue(result.toString(),result is NativeMeasuredStoreResult.Durable)
        return result as NativeMeasuredStoreResult.Durable
    }
    private fun denied(
        result:NativeMeasuredStoreResult,reason:NativeMeasuredStoreRefusal
    ) {
        assertTrue(result.toString(),result is NativeMeasuredStoreResult.Rejected)
        assertEquals(reason,(result as NativeMeasuredStoreResult.Rejected).reason)
    }
    private fun record(p:MeasuredExerciseSetPlan,actual:MeasuredExerciseActual,
        event:String="event-one",rev:Long=0,
        corrects:String?=null)=MeasuredJournalOperation.Record(event,rev,
        MeasuredSetOutcome.COMPLETED,actual,corrects)

    @Test fun all14SourceMeasurementsIssueFromAuthorityAndColdReplayIndividualFacts() {
        val ids=MeasuredExerciseCatalogDomain.all.map{it.exerciseId}
        assertEquals(14,ids.size)
        val saved=store()
        for(id in ids) {
            val p=approve(plan(id))
            val beginning=issued(saved.issue("owner-one",p.workoutId,p.setKey))
            assertFalse(beginning.replayed)
            assertEquals(p,beginning.stored.journal.immutablePlan)
            assertEquals("approval-"+id,beginning.stored.approvalId)
            assertEquals(1L,beginning.stored.approvalRevision)
            assertTrue(beginning.stored.isIssued)
            assertFalse(beginning.stored.canFinishWorkout)
            assertFalse(beginning.stored.mayPublishHistory)
            assertNull(beginning.stored.journal.finishedByEventId)
            val op=record(p,actual(p))
            val logged=issued(saved.record("owner-one",p.workoutId,p.setKey,op))
            assertFalse(logged.replayed)
            assertEquals(1L,logged.stored.journal.revision)
            val reopened=issued(store().open("owner-one",p.workoutId,p.setKey))
            assertEquals(logged.stored,reopened.stored)
            assertEquals(op,(reopened.stored.journal.effectiveRecord!!.operation))
            val retry=issued(store().record("owner-one",p.workoutId,p.setKey,op))
            assertTrue(retry.replayed)
            assertEquals(1L,retry.stored.journal.revision)
            val corrected=record(p,actual(p,30),event="corrected",rev=1,
                corrects="event-one")
            val changed=issued(store().record("owner-one",
                p.workoutId,p.setKey,corrected))
            assertEquals(2L,changed.stored.journal.revision)
            assertEquals(p,changed.stored.journal.immutablePlan)
            assertEquals(corrected,
                issued(store().open("owner-one",p.workoutId,p.setKey))
                    .stored.journal.effectiveRecord!!.operation)
            assertNull(changed.stored.journal.finishedByEventId)
        }
        assertEquals(14,approvalsRead)
        val issuedFiles=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("measured-issued-")}.count()
        }
        assertEquals(14L,issuedFiles)
    }

    @Test fun absentSourceAndChangedOrUnapprovedSourceNeverIssueOrOverwrite() {
        val p=plan("side-plank")
        val s=store()
        denied(s.open("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.NOT_ISSUED)
        denied(s.issue("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.APPROVAL_UNAVAILABLE)
        authoritative[p.setKey]=NativeMeasuredApprovedSet(
            p.copy(exerciseId="wall-sit"),"grant",1)
        denied(s.issue("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.APPROVAL_UNAVAILABLE)
        approve(p)
        val first=issued(s.issue("owner-one",p.workoutId,p.setKey))
        authoritative[p.setKey]=NativeMeasuredApprovedSet(
            p.copy(targetAmount=45),"different-approval",2)
        assertTrue(issued(s.issue("owner-one",p.workoutId,p.setKey)).replayed)
        assertEquals(first.stored,issued(s.open(
            "owner-one",p.workoutId,p.setKey)).stored)
        authoritative.remove(p.setKey)
        val op=record(p,actual(p))
        assertEquals(1L,issued(s.record("owner-one",
            p.workoutId,p.setKey,op)).stored.journal.revision)
        assertEquals(first.stored.journal.immutablePlan,
            issued(s.open("owner-one",p.workoutId,p.setKey))
                .stored.journal.immutablePlan)
    }

    @Test fun signedOutDifferentAthleteAndUnknownOwnerNeverOpenOrAppend() {
        val p=approve(plan("copenhagen"))
        val s=store()
        issued(s.issue("owner-one",p.workoutId,p.setKey))
        current=null
        denied(s.open("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.NOT_AUTHORIZED)
        denied(s.record("owner-one",p.workoutId,p.setKey,
            record(p,actual(p))),NativeMeasuredStoreRefusal.NOT_AUTHORIZED)
        current=NativeKotlinOwnerIdentity("owner-one","another-athlete")
        denied(s.open("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.CORRUPT_RECORD)
        current=NativeKotlinOwnerIdentity("owner-two","athlete-two")
        denied(s.open("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.NOT_AUTHORIZED)
        current=NativeKotlinOwnerIdentity("owner-one","athlete-one")
        assertEquals(0L,issued(store().open(
            "owner-one",p.workoutId,p.setKey)).stored.journal.revision)
    }

    @Test fun staleCollisionInvalidLoadAndInvalidCorrectionsDoNotMutateDisk() {
        val p=approve(plan("weighted-plank"))
        val s=store()
        issued(s.issue("owner-one",p.workoutId,p.setKey))
        val first=record(p,actual(p))
        val once=issued(s.record("owner-one",p.workoutId,p.setKey,first))
        val reject=s.record("owner-one",p.workoutId,p.setKey,
            first.copy(actual=actual(p,22)))
        denied(reject,NativeMeasuredStoreRefusal.STALE_OR_INVALID_RECEIPT)
        assertEquals(MeasurementRefusal.EVENT_COLLISION,
            (reject as NativeMeasuredStoreResult.Rejected).journalReason)
        val stale=s.record("owner-one",p.workoutId,p.setKey,
            record(p,actual(p),event="late",rev=0))
        denied(stale,NativeMeasuredStoreRefusal.STALE_OR_INVALID_RECEIPT)
        assertEquals(MeasurementRefusal.STALE_REVISION,
            (stale as NativeMeasuredStoreResult.Rejected).journalReason)
        val badLoad=s.record("owner-one",p.workoutId,p.setKey,
            record(p,actual(p,mass="17"),event="bad-mass",rev=1,
                corrects="event-one"))
        denied(badLoad,NativeMeasuredStoreRefusal.STALE_OR_INVALID_RECEIPT)
        assertEquals(MeasurementRefusal.WRONG_MEASUREMENT_OR_LOAD,
            (badLoad as NativeMeasuredStoreResult.Rejected).journalReason)
        denied(s.record("owner-one",p.workoutId,p.setKey,
            record(p,actual(p,25),event="no-corrected-id",rev=1)),
            NativeMeasuredStoreRefusal.STALE_OR_INVALID_RECEIPT)
        assertEquals(once.stored,issued(store().open(
            "owner-one",p.workoutId,p.setKey)).stored)
    }

    private fun journalFile(p:MeasuredExerciseSetPlan):Path {
        val hash=MessageDigest.getInstance("SHA-256").digest(
            ("owner-one\u0000"+p.workoutId+"\u0000"+p.setKey)
                .toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
        return root.resolve("measured-issued-$hash.bin")
    }

    @Test fun brokenOrSymlinkedFileRefusesWithoutReissuingAsEmpty() {
        val p=approve(plan("plank"))
        val s=store()
        issued(s.issue("owner-one",p.workoutId,p.setKey))
        val path=journalFile(p)
        val bytes=Files.readAllBytes(path)
        val altered=bytes.copyOf().also{
            it[it.size/2]=(it[it.size/2].toInt() xor 1).toByte()
        }
        Files.write(path,altered)
        denied(store().open("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.CORRUPT_RECORD)
        denied(store().issue("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.CORRUPT_RECORD)
        assertArrayEquals(altered,Files.readAllBytes(path))
        Files.delete(path)
        val elsewhere=temp.newFile("not-a-journal").toPath()
        Files.createSymbolicLink(path,elsewhere)
        denied(store().issue("owner-one",p.workoutId,p.setKey),
            NativeMeasuredStoreRefusal.CORRUPT_RECORD)
        assertTrue(Files.isSymbolicLink(path))
    }

    @Test fun unknownParentOrForgedSetDoesNotCreateWorkoutOrHistory() {
        val p=approve(plan("farmers"))
        val s=store()
        denied(s.issue("owner-one","different-workout",p.setKey),
            NativeMeasuredStoreRefusal.APPROVAL_UNAVAILABLE)
        denied(s.issue("owner-one",p.workoutId,"different-set"),
            NativeMeasuredStoreRefusal.APPROVAL_UNAVAILABLE)
        denied(s.open("owner-one","not/valid",p.setKey),
            NativeMeasuredStoreRefusal.INVALID_ID)
        denied(s.record("owner-one",p.workoutId,p.setKey,
            record(p,actual(p))),NativeMeasuredStoreRefusal.NOT_ISSUED)
        assertEquals(0,Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("measured-issued-")}
                .count()
        }.toInt())
        assertFalse(issued(s.issue("owner-one",p.workoutId,p.setKey))
            .stored.mayPublishHistory)
    }
}
