package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinPendingWorkoutDraftsTest {
    private fun identity(
        athlete:String="athlete",workout:String="workout",gym:String="gym",
        exercise:String="machine_press",set:String="working-1",
        part:String="MAIN",machine:String?="machine-A",
        load:DraftSetLoad=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.MACHINE_STACK,BigDecimal("200")),
        reps:RepBand=RepBand(8,12),rir:Int?=2
    )=NativePendingWorkoutDraftIdentity.make(
        athlete,workout,gym,exercise,set,part,machine,load,reps,rir)

    @Test fun exactIssuedIdentityIsStableAndMachineChangesNeverAlias() {
        val original=identity()
        assertEquals(original,identity())
        assertTrue(original.value.startsWith("input:"))
        assertEquals(70,original.value.length)
        assertNotEquals(original,identity(machine="machine-B"))
        assertNotEquals(original,identity(machine=null))
        assertNotEquals(original,identity(gym="other-gym"))
        assertNotEquals(original,identity(workout="new-workout"))
        assertNotEquals(original,identity(athlete="new-athlete"))
    }

    @Test fun swappedExerciseDifferentVisitOrChangedPrescriptionCannotResurrectDraft() {
        val first=identity()
        assertNotEquals(first,identity(exercise="incline_press"))
        assertNotEquals(first,identity(set="working-2"))
        assertNotEquals(first,identity(part="FOLLOW_UP"))
        assertNotEquals(first,identity(load=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.MACHINE_STACK,BigDecimal("190"))))
        assertNotEquals(first,identity(load=DraftSetLoad.Verified(
            LoadUnit.KG,LoadBasis.MACHINE_STACK,BigDecimal("200"))))
        assertNotEquals(first,identity(load=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("200"))))
        assertNotEquals(first,identity(reps=RepBand(6,10)))
        assertNotEquals(first,identity(rir=null))
        assertNotEquals(first,identity(load=DraftSetLoad.ExplicitBodyweight))
    }

    @Test fun decimalScaleChangesDuringJournalReplayDoNotLoseTypedDraft() {
        assertEquals(identity(),identity(load=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.MACHINE_STACK,BigDecimal("200.000"))))
    }

    @Test fun lengthPrefixSeparatesUntrustedSourceParts() {
        assertNotEquals(identity(athlete="a:b",workout="c"),
            identity(athlete="a",workout="b:c"))
        assertNotEquals(identity(set="a:b",part="c"),
            identity(set="a",part="b:c"))
    }

    @Test fun savedEditorFieldsCannotOverrideNewerDurableReceiptsOrMachineScope() {
        val original=NativePendingWorkoutDraftRestoreScope(identity(),null,false)
        val fields=original.save("38.5")
        assertEquals("38.5",original.restore(fields))
        assertNull(original.copy(receiptRevision="1",committed=true).restore(fields))
        assertNull(original.copy(identity=identity(machine="machine-B")).restore(fields))
        assertNull(original.copy(identity=identity(part="FOLLOW_UP")).restore(fields))
        assertNull(original.restore(listOf("38.5")))
        val correction=original.copy(receiptRevision="2",committed=true)
        val editing=correction.save("150")
        assertEquals("150",correction.restore(editing))
        assertNull(correction.copy(receiptRevision="3").restore(editing))
        assertNull(correction.copy(committed=false).restore(editing))
        assertEquals("",original.restore(original.save("")))
    }

    @Test fun codecRoundTripsPartialTypingAndUnknownActualRir() {
        for(draft in listOf(
            NativePendingWorkoutDraft("200","8",""),
            NativePendingWorkoutDraft("2.","1",""),
            NativePendingWorkoutDraft("","", ""),
            NativePendingWorkoutDraft("140.5","12","3"),
            NativePendingWorkoutDraft("120","8","unreported"))) {
            val encoded=NativePendingWorkoutDraftCodec.encode(draft)
            assertNotNull(encoded)
            assertEquals(draft,NativePendingWorkoutDraftCodec.decode(encoded))
        }
    }

    @Test fun corruptOversizeAndForeignVersionDraftsFailClosed() {
        assertNull(NativePendingWorkoutDraftCodec.decode(null))
        assertNull(NativePendingWorkoutDraftCodec.decode(""))
        assertNull(NativePendingWorkoutDraftCodec.decode("v2:MTIz:OAo=:"))
        assertNull(NativePendingWorkoutDraftCodec.decode("v1:%%%%:Mg==:"))
        assertNull(NativePendingWorkoutDraftCodec.decode("v1:MTIz:NA=="))
        assertNull(NativePendingWorkoutDraftCodec.decode("v1:MTIz:NA==:NA==:extra"))
        assertNull(NativePendingWorkoutDraftCodec.decode("z".repeat(513)))
        assertNull(NativePendingWorkoutDraftCodec.encode(
            NativePendingWorkoutDraft("1".repeat(65),"8","")))
    }

    @Test fun draftEncodingMustAlwaysBeRecoverableWithoutTextReplacement() {
        val unicode=NativePendingWorkoutDraft("45.5","10","💪")
        val encoded=NativePendingWorkoutDraftCodec.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode,NativePendingWorkoutDraftCodec.decode(encoded))
        // A corrupted, non-UTF-8 field is not a usable saved input.
        assertNull(NativePendingWorkoutDraftCodec.decode("v1:/w==:OA==:"))
        // Java/Kotlin UTF-8 encoders otherwise silently replace lone surrogates.
        assertNull(NativePendingWorkoutDraftCodec.encode(
            NativePendingWorkoutDraft("\uD800","8","")))
        // The old encoder accepted text longer than its own 512-char decoder
        // limit, returning save success for a draft that could never recover.
        val extended="é".repeat(64)
        assertNull(NativePendingWorkoutDraftCodec.encode(
            NativePendingWorkoutDraft(extended,extended,extended)))
    }

    @Test fun codecDoesNotPretendToBeJournalOrAutoFillActuals() {
        val draft=NativePendingWorkoutDraft("135.5","8","")
        val encoded=NativePendingWorkoutDraftCodec.encode(draft)
        assertNotNull(encoded)
        // These are raw UI strings, NOT the domain's KotlinPerformedSet.
        assertEquals("",NativePendingWorkoutDraftCodec.decode(encoded)?.rir)
        assertFalse(encoded!!.contains("machine-A"))
    }

    @Test fun myoDraftFromPriorActivationRevisionCannotOverrideCorrectedLastSet() {
        val before=identity(set="last-working-4",rir=null,
            part="FOLLOW_UP:stage:1:activation:9")
        val corrected=identity(set="last-working-4",rir=null,
            part="FOLLOW_UP:stage:1:activation:10")
        val laterMini=identity(set="last-working-4",rir=null,
            part="FOLLOW_UP:stage:2:activation:10")
        val firstSet=identity(set="working-1",rir=null,
            part="FOLLOW_UP:stage:1:activation:10")
        assertNotEquals(before,corrected)
        assertNotEquals(corrected,laterMini)
        assertNotEquals(firstSet,corrected)
        assertEquals(corrected,identity(set="last-working-4",rir=null,
            part="FOLLOW_UP:stage:1:activation:10"))
        // Original main-set inputs retain their preexisting identity.
        assertEquals(identity(part="MAIN:stage:1"),identity(part="MAIN:stage:1"))
    }
}
