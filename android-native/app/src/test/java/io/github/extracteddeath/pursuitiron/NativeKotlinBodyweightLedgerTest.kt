package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.LoadUnit
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinBodyweightLedgerTest {
    private val today=LocalDate.parse("2026-11-17")
    private val empty=NativeBodyweightLedger(0,emptyList())
    private fun entry(date:String,weight:String,unit:LoadUnit=LoadUnit.LB)=
        NativeBodyweightEntry(LocalDate.parse(date),BigDecimal(weight),unit)
    private fun saved(result:NativeBodyweightWrite)=
        (result as NativeBodyweightWrite.Saved).ledger
    private fun decoded(owner:String,raw:String):NativeBodyweightLedger=
        (NativeBodyweightLedgerDomain.decode(owner,raw)
            as NativeBodyweightRead.Ready).ledger

    @Test fun emptyAndMultiDateRoundTripAcrossColdRead() {
        assertEquals(empty,
            (NativeBodyweightLedgerDomain.decode("athlete-A",null)
                as NativeBodyweightRead.Ready).ledger)
        val first=saved(NativeBodyweightLedgerDomain.replace(
            empty,entry("2026-11-17","185.75"),today,false))
        val second=saved(NativeBodyweightLedgerDomain.replace(
            first,entry("2026-11-14","84.1",LoadUnit.KG),today,false))
        assertEquals(2L,second.revision)
        assertEquals(listOf("2026-11-14","2026-11-17"),
            second.entries.map{it.date.toString()})
        val raw=NativeBodyweightLedgerDomain.encode("athlete-A",second)
        assertEquals(second,decoded("athlete-A",raw))
        assertTrue(raw.contains("sha256:"))
        assertFalse(raw.contains("athlete-A")) // owner key is a digest
        assertEquals(2,decoded("athlete-A",raw).entries.size)
    }

    @Test fun sameDayCorrectionRequiresExplicitApprovalAndKeepsOtherDates() {
        val two=saved(NativeBodyweightLedgerDomain.replace(
            saved(NativeBodyweightLedgerDomain.replace(
                empty,entry("2026-11-15","185"),today,false)),
            entry("2026-11-17","184"),today,false))
        val refusal=NativeBodyweightLedgerDomain.replace(
            two,entry("2026-11-17","183.25"),today,false)
        assertEquals(NativeBodyweightRefusal.REPLACEMENT_CONFIRMATION_REQUIRED,
            (refusal as NativeBodyweightWrite.Rejected).reason)
        assertEquals(2,two.entries.size)
        val corrected=saved(NativeBodyweightLedgerDomain.replace(
            two,entry("2026-11-17","183.25"),today,true))
        assertEquals(3L,corrected.revision)
        assertEquals(2,corrected.entries.size)
        assertEquals(BigDecimal("185"),corrected.entries.first().amount)
        assertEquals(BigDecimal("183.25"),corrected.entries.last().amount)
        assertEquals(corrected,decoded("athlete-A",
            NativeBodyweightLedgerDomain.encode("athlete-A",corrected)))
    }

    @Test fun deleteIsExplicitExactDateAndCannotSilentlyEraseOtherDays() {
        val original=saved(NativeBodyweightLedgerDomain.replace(
            saved(NativeBodyweightLedgerDomain.replace(
                empty,entry("2026-11-16","185"),today,false)),
            entry("2026-11-17","184.5"),today,false))
        val deleted=saved(NativeBodyweightLedgerDomain.delete(
            original,LocalDate.parse("2026-11-16")))
        assertEquals(listOf("2026-11-17"),
            deleted.entries.map{it.date.toString()})
        assertEquals(3L,deleted.revision)
        assertEquals(NativeBodyweightRefusal.NO_MATCHING_DATE,
            (NativeBodyweightLedgerDomain.delete(
                deleted,LocalDate.parse("2026-11-16"))
                as NativeBodyweightWrite.Rejected).reason)
    }

    @Test fun noFutureOrUnrealisticOrOverpreciseWeightsAreEverSaved() {
        val examples=listOf(
            entry("2026-11-18","185"),
            entry("2026-11-16","0"),
            entry("2026-11-16","-1"),
            entry("2026-11-16","1000.01"),
            entry("2026-11-16","185.123"),
            entry("1899-12-31","180"))
        for(input in examples) {
            assertTrue(NativeBodyweightLedgerDomain.replace(
                empty,input,today,false) is NativeBodyweightWrite.Rejected)
            assertEquals(empty.revision,0L)
            assertTrue(empty.entries.isEmpty())
        }
        assertEquals(null,NativeBodyweightLedgerDomain.validEntry(
            entry("2026-11-17","190.00",LoadUnit.LB),today))
        assertEquals(null,NativeBodyweightLedgerDomain.validEntry(
            entry("2026-11-17","86.2",LoadUnit.KG),today))
    }

    @Test fun tamperingWrongOwnerAndUnsupportedSchemaNeverResetMeasurements() {
        val first=saved(NativeBodyweightLedgerDomain.replace(
            empty,entry("2026-11-17","185"),today,false))
        val raw=NativeBodyweightLedgerDomain.encode("athlete-A",first)
        assertEquals(NativeBodyweightRefusal.CORRUPT,
            (NativeBodyweightLedgerDomain.decode("athlete-B",raw)
                as NativeBodyweightRead.Rejected).reason)
        assertEquals(NativeBodyweightRefusal.CORRUPT,
            (NativeBodyweightLedgerDomain.decode("athlete-A",
                raw.replace("|185|","|150|"))
                as NativeBodyweightRead.Rejected).reason)
        assertEquals(NativeBodyweightRefusal.CORRUPT,
            (NativeBodyweightLedgerDomain.decode("athlete-A",
                raw.replace("bodyweight:1","bodyweight:2"))
                as NativeBodyweightRead.Rejected).reason)
        assertEquals(NativeBodyweightRefusal.CORRUPT,
            (NativeBodyweightLedgerDomain.decode("athlete-A","garbled")
                as NativeBodyweightRead.Rejected).reason)
        assertEquals(NativeBodyweightRefusal.OWNER_NOT_VERIFIED,
            (NativeBodyweightLedgerDomain.decode("",raw)
                as NativeBodyweightRead.Rejected).reason)
    }

    @Test fun normalizedNumericEquivalentsArePreservedButNeverFabricated() {
        val record=entry("2026-11-17","185.00")
        val snapshot=saved(NativeBodyweightLedgerDomain.replace(
            empty,record,today,false))
        assertEquals(record.amount,snapshot.entries.single().amount)
        val read=decoded("current",NativeBodyweightLedgerDomain.encode(
            "current",snapshot))
        assertEquals(0,read.entries.single().amount.compareTo(record.amount))
        assertEquals(LoadUnit.LB,read.entries.single().unit)
        assertEquals("2026-11-17",read.entries.single().date.toString())
    }

    @Test fun duplicateDatesCannotBeEncodedAndUnrelatedAthletesCannotAlias() {
        val first=entry("2026-11-17","185")
        try {
            NativeBodyweightLedgerDomain.encode("athlete-A",
                NativeBodyweightLedger(1,listOf(first,first)))
            fail("Should reject duplicate dates")
        } catch(expected:IllegalArgumentException) { }
        val raw=NativeBodyweightLedgerDomain.encode("athlete-A",
            NativeBodyweightLedger(1,listOf(first)))
        assertNotEquals(NativeBodyweightLedgerDomain.hash("athlete-A"),
            NativeBodyweightLedgerDomain.hash("athlete-B"))
        assertTrue(NativeBodyweightLedgerDomain.decode("athlete-A",raw)
            is NativeBodyweightRead.Ready)
    }
}
