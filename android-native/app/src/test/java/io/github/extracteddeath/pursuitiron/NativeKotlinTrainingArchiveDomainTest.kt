package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinTrainingArchiveDomainTest {
    private val id="athlete-A"
    private fun weight(day:String,value:String,unit:LoadUnit=LoadUnit.LB)=
        NativeBodyweightEntry(LocalDate.parse(day),BigDecimal(value),unit)
    private fun ledger(vararg rows:NativeBodyweightEntry)=
        NativeBodyweightLedger(2,rows.toList())
    private val source=KotlinTrainingSourceRevision(
        id,1,"program-A",1,"gym-A",1,"catalog-A","rules-A")
    private val load=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("55"))
    private val manifest=KotlinHistoryManifestEntry(
        1,"workout-A","stored-A","finish-A",3,0)
    private val fact=KotlinHistoricalSetFact(
        "incline_press","set-1",DraftSetType.WORKING,0,
        load,RepBand(5,10),2,KotlinJournalSetOutcome.COMPLETED,
        KotlinPerformedSet(BigDecimal("55"),LoadUnit.LB,8,null,"physical-A"),
        "confirmed","unrevised")
    private fun history(athlete:String=id)=
        NativeKotlinMixedHistoryTimeline(athlete,3,listOf(
            NativeKotlinMixedHistoryCompletion.Composer(
                manifest,KotlinWorkoutHistoryEvidence(
                    source,"workout-A","finish-A",3,listOf(fact),
                    emptyList()))))

    private fun unzip(bytes:ByteArray):Map<String,String> {
        val entries=mutableMapOf<String,String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use {zip->
            while(true) {
                val item=zip.nextEntry ?:break
                assertFalse(item.name.contains(".."))
                assertFalse(item.name.startsWith("/"))
                entries[item.name]=zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return entries
    }

    @Test fun archivedHistoryAndBodyweightAreSeparatePortableFactualCsvs() {
        val data=ledger(
            weight("2026-11-14","84.1",LoadUnit.KG),
            weight("2026-11-17","185.75"))
        val result=NativeKotlinTrainingArchiveDomain.prepare(
            id,history(),data) as NativeTrainingArchiveResult.Ready
        assertEquals(1,result.sessions)
        assertEquals(1,result.setRows)
        assertEquals(2,result.bodyweightRows)
        val entries=unzip(result.bytes)
        assertEquals(setOf("README.txt","training-history.csv",
            "bodyweight-history.csv"),entries.keys)
        val train=entries.getValue("training-history.csv")
        assertTrue(train.contains("incline_press"))
        assertTrue(train.contains("DUMBBELL_PER_HAND"))
        assertTrue(train.contains("physical-A"))
        assertTrue(train.contains("\"55\",\"LB\",\"8\",\"\""))
        assertFalse(train.contains("session-notes"))
        val weighIns=entries.getValue("bodyweight-history.csv")
        assertEquals("measurement_date,bodyweight,unit\n"+
            "2026-11-14,84.1,KG\n2026-11-17,185.75,LB\n",weighIns)
        assertFalse(weighIns.contains("physical-A"))
        assertFalse(weighIns.contains(id))
        assertTrue(entries.getValue("README.txt").contains(
            "not a restorable app backup"))
        assertTrue(entries.getValue("README.txt").contains("unencrypted"))
    }

    @Test fun crossAthleteAndUnverifiedHistoryAreRejectedWithoutOutput() {
        assertEquals(NativeTrainingArchiveRefusal.OWNER_MISMATCH,
            (NativeKotlinTrainingArchiveDomain.prepare(
                "other",history(),ledger())
                as NativeTrainingArchiveResult.Rejected).reason)
        val forged=history().copy(
            completions=listOf(
                (history().completions.single() as
                    NativeKotlinMixedHistoryCompletion.Composer).copy(
                    evidence=KotlinWorkoutHistoryEvidence(
                        source,"workout-A","different-finish",3,
                        listOf(fact),emptyList()))))
        assertEquals(NativeTrainingArchiveRefusal.HISTORY_UNVERIFIED,
            (NativeKotlinTrainingArchiveDomain.prepare(id,forged,ledger())
                as NativeTrainingArchiveResult.Rejected).reason)
    }

    @Test fun malformedOrOverPreciseBodyweightRefusesEntireExport() {
        val bad=ledger(weight("2026-11-17","185.999"))
        assertNull(NativeKotlinTrainingArchiveDomain.bodyweightCsv(bad))
        assertEquals(NativeTrainingArchiveRefusal.MEASUREMENTS_UNVERIFIED,
            (NativeKotlinTrainingArchiveDomain.prepare(id,history(),bad)
                as NativeTrainingArchiveResult.Rejected).reason)
        assertNull(NativeKotlinTrainingArchiveDomain.bodyweightCsv(
            ledger(weight("2026-11-17","180"),
                weight("2026-11-17","185"))))
        assertNull(NativeKotlinTrainingArchiveDomain.bodyweightCsv(
            ledger(weight("2026-11-17","180"),
                weight("2026-11-16","185"))))
        assertNull(NativeKotlinTrainingArchiveDomain.bodyweightCsv(
            NativeBodyweightLedger(-1,emptyList())))
    }

    @Test fun emptyWeighInsStillProduceExplicitEmptyHistoryFile() {
        val ready=NativeKotlinTrainingArchiveDomain.prepare(
            id,history(),NativeBodyweightLedger(0,emptyList()))
            as NativeTrainingArchiveResult.Ready
        assertEquals(0,ready.bodyweightRows)
        assertEquals("measurement_date,bodyweight,unit\n",
            unzip(ready.bytes).getValue("bodyweight-history.csv"))
    }

    @Test fun identicalSourceProducesStablePortableBytes() {
        val one=NativeKotlinTrainingArchiveDomain.prepare(
            id,history(),ledger(weight("2026-11-17","185")))
            as NativeTrainingArchiveResult.Ready
        val again=NativeKotlinTrainingArchiveDomain.prepare(
            id,history(),ledger(weight("2026-11-17","185")))
            as NativeTrainingArchiveResult.Ready
        assertArrayEquals(one.bytes,again.bytes)
    }
}
