package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class NativeMeasuredExerciseJournalCodecTest {
    private fun n(v:String)=BigDecimal(v)
    private fun plan(exerciseId:String):MeasuredExerciseSetPlan {
        val definition=MeasuredExerciseCatalogDomain.find(exerciseId)!!
        val sides=when(definition.sideRule) {
            MeasuredSideRule.BOTH ->setOf(MeasuredSide.BOTH)
            MeasuredSideRule.LEFT_AND_RIGHT ->setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE ->setOf(MeasuredSide.LEFT)
        }
        val loaded=definition.loadKind in setOf(
            MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND)
        val band=definition.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT
        return MeasuredExerciseSetPlan(
            "measured-workout","measured-set",exerciseId,"gym-one",3,
            "setup-one",when(definition.kind) {
                MeasuredExerciseKind.HOLD_SECONDS ->30
                MeasuredExerciseKind.CARRY_CENTIMETERS ->3000
                MeasuredExerciseKind.BANDED_REPETITIONS ->12
            },sides,definition.requiredFixtures,
            if(loaded)VerifiedLoadStock.Totals(LoadUnit.LB,
                checkNotNull(definition.expectedBasis),
                listOf(n("10"),n("20"),n("30"))) else null,
            if(loaded)n("20") else null,
            if(loaded)LoadUnit.LB else null,
            exerciseId=="farmers",
            if(band)"band-verified" else null,
            if(band)setOf("band-verified") else emptySet()
        )
    }
    private fun actual(p:MeasuredExerciseSetPlan,quantity:Int=20,
        mass:String="20.000"):MeasuredExerciseActual {
        val def=MeasuredExerciseCatalogDomain.find(p.exerciseId)!!
        val loaded=def.loadKind in setOf(MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND)
        return MeasuredExerciseActual(p.physicalSetupId,
            p.sides.sortedBy{it.ordinal}.map{MeasuredSideResult(it,quantity)},
            if(loaded)n(mass) else null,
            if(loaded)LoadUnit.LB else null,
            p.bandId,
            if(def.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)36 else null)
    }
    private fun completed(id:String):MeasuredExerciseJournal {
        val p=plan(id)
        var j=MeasuredExerciseJournalDomain.begin(p)!!
        val first=MeasuredJournalOperation.Record("set-one",0,
            MeasuredSetOutcome.COMPLETED,actual(p))
        j=(MeasuredExerciseJournalDomain.reduce(j,first)
            as MeasuredJournalDecision.Proposed).journal
        val revised=MeasuredJournalOperation.Record("set-corrected",j.revision,
            MeasuredSetOutcome.COMPLETED,actual(p,quantity=25),"set-one")
        j=(MeasuredExerciseJournalDomain.reduce(j,revised)
            as MeasuredJournalDecision.Proposed).journal
        j=(MeasuredExerciseJournalDomain.reduce(j,
            MeasuredJournalOperation.Finish("finish",j.revision))
            as MeasuredJournalDecision.Proposed).journal
        return j
    }
    private fun denied(block:()->Unit):Boolean=runCatching{block()}.isFailure

    @Test fun callerMutationsCannotChangeArchiveBytesOrColdReplayedActuals() {
        for(definition in MeasuredExerciseCatalogDomain.all) {
            val source=plan(definition.exerciseId)
            val sides=source.sides.toMutableSet()
            val fixtures=source.confirmedFixtures.toMutableSet()
            val bands=source.gymConfirmedBandIds.toMutableSet()
            val stock=source.physicalStock as? VerifiedLoadStock.Totals
            val loads=stock?.loads?.toMutableList()
            val input=source.copy(sides=sides,confirmedFixtures=fixtures,
                gymConfirmedBandIds=bands,
                physicalStock=stock?.copy(loads=checkNotNull(loads)))
            val issued=checkNotNull(MeasuredExerciseJournalDomain.begin(input))
            val original=NativeMeasuredExerciseJournalCodec.encode(
                "owner-one","athlete-one",issued)
            sides.clear();fixtures.clear();bands.clear();loads?.clear()
            assertArrayEquals(definition.exerciseId,original,
                NativeMeasuredExerciseJournalCodec.encode("owner-one","athlete-one",issued))
            val measured=actual(issued.immutablePlan)
            val observed=measured.sides.toMutableList()
            val recorded=(MeasuredExerciseJournalDomain.reduce(issued,
                MeasuredJournalOperation.Record("record",0,MeasuredSetOutcome.COMPLETED,
                    measured.copy(sides=observed)))
                as MeasuredJournalDecision.Proposed).journal
            val recordedBytes=NativeMeasuredExerciseJournalCodec.encode(
                "owner-one","athlete-one",recorded)
            observed.clear()
            assertArrayEquals(recordedBytes,NativeMeasuredExerciseJournalCodec.encode(
                "owner-one","athlete-one",recorded))
            val corrected=(MeasuredExerciseJournalDomain.reduce(recorded,
                MeasuredJournalOperation.Record("correct",1,MeasuredSetOutcome.COMPLETED,
                    actual(issued.immutablePlan,quantity=25),"record"))
                as MeasuredJournalDecision.Proposed).journal
            val finished=(MeasuredExerciseJournalDomain.reduce(corrected,
                MeasuredJournalOperation.Finish("finish",2))
                as MeasuredJournalDecision.Proposed).journal
            val reopened=NativeMeasuredExerciseJournalCodec.decode(
                NativeMeasuredExerciseJournalCodec.encode("owner-one","athlete-one",finished),
                "owner-one","athlete-one")
            assertEquals(finished,reopened)
            assertEquals("finish",reopened.finishedByEventId)
            assertTrue((reopened.effectiveRecord!!.operation as
                MeasuredJournalOperation.Record).actual!!.sides.all{it.amount==25})
            assertEquals(MeasuredSetReviewStatus.COMPLETED,
                MeasuredWorkoutResultReviewDomain.review(reopened.immutablePlan,reopened).status)
        }
    }

    @Test fun all14SourceExactMeasuredJournalsEncodeDecodeAndPreserveFacts() {
        val ids=MeasuredExerciseCatalogDomain.all.map{it.exerciseId}
        assertEquals(14,ids.size)
        for(id in ids) {
            val journal=completed(id)
            val bytes=NativeMeasuredExerciseJournalCodec.encode(
                "owner-one","athlete-one",journal)
            val reopened=NativeMeasuredExerciseJournalCodec.decode(
                bytes,"owner-one","athlete-one")
            assertEquals(id,journal,reopened)
            assertEquals(journal.immutablePlan,reopened.immutablePlan)
            assertEquals(3,reopened.receipts.size)
            assertEquals("finish",reopened.finishedByEventId)
            val fact=(reopened.effectiveRecord!!.operation
                as MeasuredJournalOperation.Record).actual!!
            assertTrue(fact.sides.all{it.amount==25})
            assertEquals(actual(journal.immutablePlan,25),fact)
            assertFalse(reopened.persisted)
            assertFalse(reopened.mayChangeTrainingProgram)
            assertTrue(id,denied{
                NativeMeasuredExerciseJournalCodec.decode(
                    bytes,"different-owner","athlete-one")
            })
            assertTrue(id,denied{
                NativeMeasuredExerciseJournalCodec.decode(
                    bytes,"owner-one","different-athlete")
            })
            val corrupted=bytes.copyOf()
            corrupted[corrupted.size/2]=(corrupted[corrupted.size/2].toInt() xor 1).toByte()
            assertTrue(id,denied{
                NativeMeasuredExerciseJournalCodec.decode(
                    corrupted,"owner-one","athlete-one")
            })
            assertTrue(id,denied{
                NativeMeasuredExerciseJournalCodec.decode(
                    bytes.copyOf(bytes.size-1),"owner-one","athlete-one")
            })
        }
    }

    @Test fun unknownCodecVersionFailsEvenWithARecomputedChecksum() {
        val bytes=NativeMeasuredExerciseJournalCodec.encode(
            "owner-one","athlete-one",completed("plank"))
        val forged=bytes.copyOf()
        // Header: four-byte body size, four-byte magic, four-byte VERSION.
        forged[11]=2
        val body=forged.copyOfRange(4,forged.size-32)
        val checksum=MessageDigest.getInstance("SHA-256").digest(body)
        checksum.copyInto(forged,forged.size-32)
        assertTrue(denied{
            NativeMeasuredExerciseJournalCodec.decode(forged,
                "owner-one","athlete-one")
        })
    }

    @Test fun replayAndCodecRefuseCorruptEventSequenceAndForgedFinish() {
        val journal=completed("farmers")
        assertTrue(denied{
            NativeMeasuredExerciseJournalCodec.encode("owner-one","athlete-one",
                journal.copy(receipts=journal.receipts.reversed()))
        })
        assertTrue(denied{
            NativeMeasuredExerciseJournalCodec.encode("owner-one","athlete-one",
                journal.copy(finishedByEventId="forged-finish"))
        })
        assertTrue(denied{
            NativeMeasuredExerciseJournalCodec.encode("owner-one","athlete-one",
                journal.copy(revision=99))
        })
        assertTrue(denied{
            NativeMeasuredExerciseJournalCodec.encode("wrong owner","athlete-one",
                journal)
        })
    }

    @Test fun stackStockLoadsAndSkippedFactsRemainUnissuedCandidates() {
        val original=plan("weighted-plank")
        val p=original.copy(
            physicalStock=VerifiedLoadStock.Stack(LoadUnit.LB,
                LoadBasis.EXERCISE_OVERRIDE,n("10"),n("5"),n("30")))
        var j=MeasuredExerciseJournalDomain.begin(p)!!
        j=(MeasuredExerciseJournalDomain.reduce(j,
            MeasuredJournalOperation.Record("skip-one",0,
                MeasuredSetOutcome.SKIPPED,null))
            as MeasuredJournalDecision.Proposed).journal
        val bytes=NativeMeasuredExerciseJournalCodec.encode(
            "owner-one","athlete-one",j)
        val recovered=NativeMeasuredExerciseJournalCodec.decode(
            bytes,"owner-one","athlete-one")
        assertEquals(j,recovered)
        assertEquals(MeasuredSetOutcome.SKIPPED,
            (recovered.effectiveRecord!!.operation
                as MeasuredJournalOperation.Record).outcome)
        assertNull((recovered.effectiveRecord!!.operation
            as MeasuredJournalOperation.Record).actual)
        assertNull(recovered.finishedByEventId)
    }
}
