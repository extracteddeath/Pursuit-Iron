package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class CustomShorterSessionReviewDomainTest {
    private val stamp=CustomWorkoutSourceStamp(
        "athlete-1","profile-1","cycle-1",3,"gym-1",2)
    private fun slot(
        id:String,count:Int,role:ComposerRole=ComposerRole.ACCESSORY,
        pinned:Boolean=false,group:String?=null,
        specialLast:Boolean=false,warmups:Int=0,
        amplified:Boolean=false
    ):CustomWorkoutSlotSnapshot {
        val sets=buildList {
            repeat(warmups){i ->
                add(CustomWorkoutSetSnapshot(
                    "cycle-1:w1:d0:$id:s${i+1}",id,i+1,
                    CustomAuthoredSet(DraftSetType.WARMUP,
                        DraftSetLoad.ExplicitBodyweight,RepBand(6,6),
                        null,60,90)))
            }
            repeat(count){i ->
                val index=i+1+warmups
                add(CustomWorkoutSetSnapshot(
                    "cycle-1:w1:d0:$id:s$index",id,index,
                    CustomAuthoredSet(
                        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
                        if(specialLast && i==count-1)RepBand(8,8)
                            else RepBand(10,12),
                        2,60,90,
                        if(amplified && i==count-1)CustomSetAmplifier(
                            CustomAmplifierKind.DROP_SET,5,15)
                            else null
                    )))
            }
        }
        return CustomWorkoutSlotSnapshot(
            id,"exercise-$id","family-$id","variant-$id",role,
            null,group,pinned,"Athlete-authored $id",sets)
    }
    private fun snapshot(
        slots:List<CustomWorkoutSlotSnapshot>,
        overhead:Int?=300,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        purpose:CustomBlockPurpose=CustomBlockPurpose.TRAINING
    ):CustomWorkoutDaySnapshot=CustomWorkoutDaySnapshot(
        stamp,1,1,"block-1",phase,purpose,
        CustomSplitIdentity.Freeform("Athlete plan"),0,
        "My workout",ComposerSessionPurpose.HYPERTROPHY,
        slots,slots.flatMap{it.sets}.sumOf{
            it.prescription.activeSeconds.toLong()+
                it.prescription.restSeconds+
                (it.prescription.amplifier?.followUpRestSeconds?:0)+
                (it.prescription.amplifier?.followUpActiveSeconds?:0)
        },overhead)
    private fun review(
        source:CustomWorkoutDaySnapshot,minutes:Int
    )=CustomShorterSessionReviewDomain.inspect(source,minutes)

    @Test fun optionalAccessoryExtraSetsAreCutBeforeSecondaryOrKeyLift() {
        val key=slot("press",4,ComposerRole.PRIMARY,pinned=true)
        val second=slot("row",4,ComposerRole.SECONDARY)
        val optional=slot("curl",6)
        val source=snapshot(listOf(key,second,optional))
        val result=review(source,30)
        assertEquals(CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW,
            result.status)
        assertEquals(2400L,result.originalEstimatedSeconds)
        assertEquals(1800L,result.reviewedEstimatedSeconds)
        assertEquals(4,result.omittedWorkingSets)
        assertTrue(result.omittedSetKeys.all {it.contains(":curl:")})
        assertTrue(result.omittedSlotIds.isEmpty())
        assertEquals(key.sets.map{it.setKey},result.retainedSetKeys.take(4))
        assertTrue(result.retainedSetKeys.containsAll(
            second.sets.map{it.setKey}))
        assertEquals(2,result.retainedSetKeys.count{
            it.contains(":curl:")
        })
        assertEquals(source.slots.flatMap{it.sets}.size,
            result.retainedSetKeys.size+result.omittedSetKeys.size)
        assertFalse(result.canIssueWorkout)
        assertFalse(result.canSaveCustomCycle)
        assertFalse(result.canAppendJournal)
        assertFalse(result.canConfirmAthleteConsent)
        assertEquals(6,source.slots.last().sets.size)
    }

    @Test fun twentyMinuteChoiceNeverTouchesThePrimaryAndMayOmitWholeOptionalSlot() {
        val key=slot("press",4,ComposerRole.PRIMARY,pinned=true)
        val second=slot("row",4,ComposerRole.SECONDARY)
        val optional=slot("curl",6)
        val result=review(snapshot(listOf(key,second,optional)),20)
        assertEquals(CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW,
            result.status)
        assertEquals(1200L,result.reviewedEstimatedSeconds)
        assertEquals(listOf("curl"),result.omittedSlotIds)
        assertEquals(key.sets.map{it.setKey},
            result.retainedSetKeys.take(4))
        assertEquals(2,result.retainedSetKeys.count{
            it.contains(":row:")
        })
        assertEquals(0,result.retainedSetKeys.count{
            it.contains(":curl:")
        })
    }

    @Test fun supersetsAreNeverSplitOrGivenFakeRestOverlapDiscounts() {
        val key=slot("primary",4,ComposerRole.PRIMARY,true)
        val a=slot("sup-a",3,group="ss-1")
        val b=slot("sup-b",3,group="ss-1")
        val source=snapshot(listOf(key,a,b))
        val result=review(source,20)
        assertEquals(CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW,
            result.status)
        assertEquals(1800L,result.originalEstimatedSeconds)
        assertEquals(900L,result.reviewedEstimatedSeconds)
        assertEquals(listOf("sup-a","sup-b"),result.omittedSlotIds)
        assertEquals(6,result.omittedWorkingSets)
        assertEquals(key.sets.map {it.setKey},result.retainedSetKeys)
        assertTrue(result.retainedSupersetGroups.isEmpty())
        val retained=review(source,30)
        assertEquals(CustomShortSessionStatus.ALREADY_FITS,retained.status)
        assertEquals(listOf("ss-1"),retained.retainedSupersetGroups)
        assertTrue(retained.omittedSetKeys.isEmpty())
        assertEquals(1800L,retained.reviewedEstimatedSeconds)
    }

    @Test fun pinnedMemberProtectsItsEntireSuperset() {
        val key=slot("primary",4,ComposerRole.PRIMARY,true)
        val a=slot("sup-a",3,group="ss-1",pinned=true)
        val b=slot("sup-b",3,group="ss-1")
        val result=review(snapshot(listOf(key,a,b)),20)
        assertEquals(CustomShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT,
            result.status)
        assertEquals(1800L,result.reviewedEstimatedSeconds)
        assertEquals(0,result.omittedWorkingSets)
        assertTrue(result.omittedSlotIds.isEmpty())
    }

    @Test fun warmupsAndVariableBackoffRemainAttachedOrAreDroppedAsOneSlot() {
        val key=slot("primary",4,ComposerRole.PRIMARY,pinned=true)
        val backoff=slot("optional-backoff",4,specialLast=true,warmups=1)
        val source=snapshot(listOf(key,backoff))
        val review=review(source,20)
        assertEquals(CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW,
            review.status)
        assertEquals(listOf("optional-backoff"),review.omittedSlotIds)
        assertEquals(backoff.sets.map{it.setKey},review.omittedSetKeys)
        assertEquals(key.sets.map{it.setKey},review.retainedSetKeys)
        assertEquals(4,review.omittedWorkingSets)
        assertEquals(1,review.omittedSetKeys.count{
            it==backoff.sets.first().setKey
        })
        assertEquals(10,backoff.sets[1].prescription.reps.minimum)
        assertEquals(8,backoff.sets.last().prescription.reps.minimum)
    }

    @Test fun strengthAndPerformanceNeverTrimRequiredOrPurposefulWork() {
        val key=slot("main",6,ComposerRole.PRIMARY,pinned=true)
        val second=slot("secondary",5,ComposerRole.SECONDARY)
        val src=snapshot(listOf(key,second),phase=TrainingPhase.STRENGTH)
        val review=review(src,20)
        assertEquals(CustomShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT,
            review.status)
        assertTrue(review.omittedSetKeys.isEmpty())
        assertEquals(src.knownSetAndRestSeconds+300L,
            review.reviewedEstimatedSeconds)
        val performance=review(snapshot(
            listOf(key,second.copy(role=ComposerRole.ACCESSORY)),
            purpose=CustomBlockPurpose.PERFORMANCE_TEST),20)
        assertEquals(CustomShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT,
            performance.status)
        assertTrue(performance.omittedSetKeys.isEmpty())
    }

    @Test fun advancedSetTimingUnknownNeverClaimsBudgetOrRemovesFollowup() {
        val key=slot("main",4,ComposerRole.PRIMARY,true)
        val advanced=slot("myo",4,amplified=true)
        val src=snapshot(listOf(key,advanced))
        val review=review(src,20)
        assertEquals(CustomShortSessionStatus.ADVANCED_SET_TIME_UNDECLARED,
            review.status)
        assertNull(review.originalEstimatedSeconds)
        assertNull(review.reviewedEstimatedSeconds)
        assertTrue(review.omittedSetKeys.isEmpty())
        assertTrue(review.retainedSetKeys.isEmpty())
        assertEquals(15,
            advanced.sets.last().prescription.amplifier?.followUpRestSeconds)
    }

    @Test fun missingSetupTimeOrForgedDurationCannotYieldFalseFit() {
        val key=slot("main",4,ComposerRole.PRIMARY,true)
        val src=snapshot(listOf(key))
        assertEquals(CustomShortSessionStatus.NON_SET_TIME_UNDECLARED,
            review(src.copy(declaredNonSetSeconds=null),20).status)
        assertEquals(CustomShortSessionStatus.INVALID_SNAPSHOT,
            review(src.copy(knownSetAndRestSeconds=1L),20).status)
        assertEquals(CustomShortSessionStatus.INVALID_LIMIT,
            review(src,29).status)
        assertEquals(CustomShortSessionStatus.INVALID_SNAPSHOT,
            review(src.copy(slots=listOf(key,key)),20).status)
        assertEquals(CustomShortSessionStatus.ALREADY_FITS,
            review(src,20).status)
        assertTrue(review(src,20).omittedSetKeys.isEmpty())
    }
    @Test fun projectedShorterSnapshotRetainsExactPrefixAndNeverAltersAuthoredProgram() {
        val primary=slot("press",5,ComposerRole.PRIMARY,pinned=true)
        val accessory=slot("curl",6)
        val full=snapshot(listOf(primary,accessory))
        val cut=CustomShorterSessionReviewDomain.project(full,20)
        assertNotNull(cut)
        val selected=checkNotNull(cut)
        assertEquals(20,selected.shortSessionLimitMinutes)
        assertTrue(selected.prescriptionEditedForTime)
        assertEquals(listOf("press"),selected.slots.map{it.slotId})
        assertEquals(primary.sets,selected.slots.single().sets)
        assertEquals(accessory.sets.map{it.setKey},
            selected.shortSessionOmittedSetKeys)
        assertEquals(primary.sets.sumOf{
            it.prescription.activeSeconds+it.prescription.restSeconds
        }.toLong(),selected.knownSetAndRestSeconds)
        assertEquals(full.slots,listOf(primary,accessory))
        assertNull(full.shortSessionLimitMinutes)
        assertTrue(full.shortSessionOmittedSetKeys.isEmpty())
        assertNull(CustomShorterSessionReviewDomain.project(
            full.copy(declaredNonSetSeconds=null),20))
        assertNull(CustomShorterSessionReviewDomain.project(
            full.copy(phase=TrainingPhase.STRENGTH,
                slots=listOf(primary,accessory.copy(role=ComposerRole.SECONDARY))),20))
        assertNull(CustomShorterSessionReviewDomain.project(
            full.copy(slots=listOf(primary,
                accessory.copy(sets=accessory.sets.map {
                    it.copy(prescription=it.prescription.copy(amplifier=
                        CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,12)))
                }))),20))
    }

    @Test fun fullyTimedAdvancedProtocolCountsRestAndWorkWithoutPartialAmplifierCuts() {
        val primary=slot("main",4,ComposerRole.PRIMARY,pinned=true)
        val advanced=slot("myo",4,amplified=true).let{slot->
            slot.copy(sets=slot.sets.map{set->
                val amp=set.prescription.amplifier
                if(amp==null)set else set.copy(
                    prescription=set.prescription.copy(
                        amplifier=amp.copy(followUpActiveSeconds=60)))
            })
        }
        val full=snapshot(listOf(primary,advanced))
        assertEquals(1575L,full.knownSetAndRestSeconds+300L)
        val short=review(full,20)
        assertEquals(CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW,
            short.status)
        assertEquals(1575L,short.originalEstimatedSeconds)
        assertEquals(900L,short.reviewedEstimatedSeconds)
        assertEquals(4,short.omittedWorkingSets)
        assertEquals(advanced.sets.map{it.setKey},short.omittedSetKeys)
        assertEquals(listOf("myo"),short.omittedSlotIds)
        assertEquals(primary.sets.map{it.setKey},short.retainedSetKeys)
        val saved=CustomShorterSessionReviewDomain.project(full,20)
        assertEquals(primary.sets,saved?.slots?.single()?.sets)
        assertEquals(20,saved?.shortSessionLimitMinutes)
        assertEquals(600L,saved?.knownSetAndRestSeconds)
        assertEquals(60,advanced.sets.last().prescription.amplifier
            ?.followUpActiveSeconds)
        val unchanged=review(full,30)
        assertEquals(CustomShortSessionStatus.ALREADY_FITS,unchanged.status)
        assertTrue(unchanged.omittedSetKeys.isEmpty())
        val pinned=review(snapshot(listOf(primary,advanced.copy(pinned=true))),20)
        assertEquals(CustomShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT,
            pinned.status)
        assertTrue(pinned.omittedSetKeys.isEmpty())
        val unknown=review(snapshot(listOf(primary,advanced.copy(
            sets=advanced.sets.map{set->set.copy(
                prescription=set.prescription.copy(
                    amplifier=set.prescription.amplifier?.copy(
                        followUpActiveSeconds=null)))
            })
        ))),20)
        assertEquals(CustomShortSessionStatus.ADVANCED_SET_TIME_UNDECLARED,
            unknown.status)
        assertNull(unknown.reviewedEstimatedSeconds)
        assertFalse(short.canIssueWorkout)
    }

}
