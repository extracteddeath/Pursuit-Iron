package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeFutureWeekLoadSessionProjectionTest {
    private fun n(v:String)=BigDecimal(v)
    private val physical=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(n("195"),n("200"),n("205"),n("210")))
    private val startingLoad=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,n("200"))
    private fun set(ordinal:Int,load:DraftSetLoad=startingLoad,
        type:DraftSetType=DraftSetType.WORKING,
        amplifier:CustomSetAmplifier?=null)=CustomWorkoutSetSnapshot(
        "cycle-1:w9:d1:bench:s$ordinal","bench",ordinal,
        CustomAuthoredSet(type,load,RepBand(5,8),2,42,105,amplifier))
    private fun snapshot()=CustomWorkoutDaySnapshot(
        stamp=CustomWorkoutSourceStamp(
            "athlete-1","setup-7","cycle-1",8,"gym-1",4),
        absoluteWeek=9,weekInBlock=2,blockId="block-2",
        phase=TrainingPhase.HYPERTROPHY,
        blockPurpose=CustomBlockPurpose.TRAINING,
        split=CustomSplitIdentity.Freeform("Personal upper"),
        weekday=1,dayLabel="Upper A",
        sessionPurpose=ComposerSessionPurpose.HYPERTROPHY,
        slots=listOf(
            CustomWorkoutSlotSnapshot(
                slotId="bench",exerciseId="chest-press",
                familyId="chest",variantId="machine",
                role=ComposerRole.PRIMARY,attachmentId=null,
                supersetGroup=null,pinned=true,
                coachNote="Pause at the bottom",
                sets=listOf(set(1),set(2),set(3))),
            CustomWorkoutSlotSnapshot(
                slotId="crunch",exerciseId="bodyweight_crunch",
                familyId="crunch",variantId="floor",
                role=ComposerRole.ACCESSORY,attachmentId=null,
                supersetGroup=null,pinned=false,
                coachNote="No momentum",
                sets=listOf(CustomWorkoutSetSnapshot(
                    "cycle-1:w9:d1:crunch:s1","crunch",1,
                    CustomAuthoredSet(DraftSetType.WORKING,
                        DraftSetLoad.ExplicitBodyweight,RepBand(12,15),
                        2,40,90))))),
        knownSetAndRestSeconds=621,declaredNonSetSeconds=180)

    private fun receipt()=NativeFutureWeekLoadConsentReceipt(
        ownerKey="owner-1",athleteId="athlete-1",
        cycleId="cycle-1",blockId="block-2",recordRevision=1,
        absoluteWeek=9,weekday=1,slotId="bench",
        historyRevision=31,draftRevision=8,sourceRevision=4,
        settingsRevision=2,permissionRevision=3,
        reviewFingerprint="a".repeat(64),
        previousLoad=n("200"),proposedLoad=n("205"),
        readiness=CoachReadiness.NORMAL,movementRestricted=false,
        maximumAuthorizedIncrease=n("5"))
    private fun run(source:CustomWorkoutDaySnapshot=snapshot(),
        grant:NativeFutureWeekConsentRead=
            NativeFutureWeekConsentRead.Current(receipt()),
        stock:VerifiedLoadStock?=physical)=
        NativeFutureWeekLoadSessionProjection.preview(source,grant,stock)

    @Test fun previewChangesOnlyUniformWorkingWeightAndRetainsOriginal() {
        val source=snapshot()
        val result=run(source) as NativeFutureWeekLoadProjection.Ready
        assertEquals("a".repeat(64),result.fingerprint)
        assertEquals("bench",result.changedSlotId)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayWriteTrainingCycle)
        assertFalse(result.mayChangeHistory)
        assertEquals(source,result.original)
        assertEquals(source.slots[1],result.preview.slots[1])
        assertEquals(source.stamp,result.preview.stamp)
        assertEquals(source.knownSetAndRestSeconds,
            result.preview.knownSetAndRestSeconds)
        assertEquals(source.declaredNonSetSeconds,
            result.preview.declaredNonSetSeconds)
        val before=source.slots[0]
        val after=result.preview.slots[0]
        assertEquals(before.copy(sets=after.sets),after)
        assertEquals(before.sets.map{it.setKey},after.sets.map{it.setKey})
        assertEquals(before.sets.map{it.prescription.reps},
            after.sets.map{it.prescription.reps})
        assertEquals(before.sets.map{it.prescription.targetRir},
            after.sets.map{it.prescription.targetRir})
        assertEquals(listOf(n("200"),n("200"),n("200")),
            before.sets.map{(it.prescription.load as DraftSetLoad.Verified).value})
        assertEquals(listOf(n("205"),n("205"),n("205")),
            after.sets.map{(it.prescription.load as DraftSetLoad.Verified).value})
    }

    @Test fun staleOrRevokedConsentAndUnrelatedWorkoutFailClosed() {
        assertEquals(NativeFutureWeekLoadProjection.Rejected(
            NativeFutureWeekLoadProjectionRefusal.CONSENT_NOT_CURRENT),
            run(grant=NativeFutureWeekConsentRead.Missing))
        assertEquals(NativeFutureWeekLoadProjection.Rejected(
            NativeFutureWeekLoadProjectionRefusal.CONSENT_NOT_CURRENT),
            run(grant=NativeFutureWeekConsentRead.Revoked(2)))
        assertEquals(NativeFutureWeekLoadProjection.Rejected(
            NativeFutureWeekLoadProjectionRefusal.WRONG_SESSION),
            run(source=snapshot().copy(absoluteWeek=10)))
        assertEquals(NativeFutureWeekLoadProjection.Rejected(
            NativeFutureWeekLoadProjectionRefusal.WRONG_SESSION),
            run(source=snapshot().copy(stamp=snapshot().stamp.copy(
                gymCatalogSourceRevision=5))))
        assertEquals(NativeFutureWeekLoadProjection.Rejected(
            NativeFutureWeekLoadProjectionRefusal.SLOT_MISSING),
            run(source=snapshot().copy(slots=snapshot().slots.drop(1))))
    }

    @Test fun manualSetEditsBackoffsAndAmplifiersAreNotSilentlyFlattened() {
        val old=snapshot()
        val changed=old.copy(slots=listOf(old.slots.first().copy(
            sets=listOf(set(1),set(2,load=DraftSetLoad.Verified(
                LoadUnit.LB,LoadBasis.MACHINE_STACK,n("195"))),set(3))))+
            old.slots.last())
        val warmup=old.copy(slots=listOf(old.slots.first().copy(
            sets=listOf(set(1,type=DraftSetType.WARMUP),set(2),set(3))))+
            old.slots.last())
        val amplified=old.copy(slots=listOf(old.slots.first().copy(
            sets=listOf(set(1,amplifier=CustomSetAmplifier(
                CustomAmplifierKind.MYO_REPS,3,15)),set(2),set(3))))+
            old.slots.last())
        for(session in listOf(changed,warmup,amplified)){
            assertEquals(NativeFutureWeekLoadProjection.Rejected(
                NativeFutureWeekLoadProjectionRefusal.PRESCRIPTION_CHANGED),
                run(source=session))
        }
    }

    @Test fun candidateMustBeNextRealPhysicalIncrement() {
        val invalid=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.MACHINE_STACK,listOf(
                n("200"),n("210")))
        val wrongBasis=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.CABLE_STACK,listOf(
                n("200"),n("205")))
        for(stock in listOf(null,invalid,wrongBasis)){
            assertEquals(NativeFutureWeekLoadProjection.Rejected(
                NativeFutureWeekLoadProjectionRefusal.UNVERIFIED_EQUIPMENT),
                run(stock=stock))
        }
    }
}
