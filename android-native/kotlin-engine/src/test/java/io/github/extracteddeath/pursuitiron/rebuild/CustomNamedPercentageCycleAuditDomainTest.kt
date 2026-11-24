package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomNamedPercentageCycleAuditDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private fun set(value:String,type:DraftSetType=DraftSetType.WORKING)=
        CustomAuthoredSet(type,DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n(value)),
            RepBand(5,8),2,45,120)
    private val slot=CustomExerciseSlot("press-slot","press",ComposerRole.PRIMARY,
        listOf(set("50",DraftSetType.WARMUP),set("100"),set("100"),set("100")),
        machineInstanceId="machine-A")
    private val day=CustomTrainingDay(0,"Upper A",ComposerSessionPurpose.STRENGTH_SKILL,listOf(slot))
    private val base=CustomTrainingCycle("cycle",1,"athlete",AthleteTrainingGoal.STRENGTH,
        "Plan",listOf(CustomTrainingBlock("strength","Strength",TrainingPhase.STRENGTH,
            CustomBlockPurpose.TRAINING,4,CustomSplitIdentity.Freeform("Upper"),
            listOf(day,day.copy(weekday=2)))))
    private fun stamp(cycle:CustomTrainingCycle)=
        CustomWorkoutSourceStamp("athlete","profile-1","cycle",cycle.revision,"gym",2)
    private fun proof(week:Int,weekday:Int=0)=CustomNamedPercentageWeekEvidence(
        CustomNamedPercentageWeekKey("strength",week,weekday,"press-slot"),
        NamedPercentageRequest(BasicProgressionScope("athlete","cycle","gym","strength",
            "strength:d"+weekday,"press","machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK),
            NamedPercentageScheme.FIVE_THREE_ONE,PercentageTier.PRIMARY,PercentageDayRole.STANDARD,
            PercentageReferenceKind.TRAINING_MAX,n("200"),true,week,4,3,
            VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,n("5"),n("5"),n("300"))))
    private fun draft(cycle:CustomTrainingCycle,p:CustomNamedPercentageWeekEvidence):CustomTrainingCycle {
        val key=p.key
        return (CustomPercentageDraftDomain.review(CustomPercentageDraftSource(cycle,stamp(cycle),
            key.blockId,key.weekInBlock,key.weekday,key.slotId),p.request)
            as CustomPercentageDraftResult.Proposed).proposal.draft
    }
    private val a=proof(1)
    private val b=proof(3,2)
    private val candidate=draft(draft(base,a),b)
    private fun audit(c:CustomTrainingCycle=candidate,
        evidence:List<CustomNamedPercentageWeekEvidence> = listOf(a,b))=
        CustomNamedPercentageCycleAuditDomain.review(c,stamp(c),evidence)
    private fun changeSecond(f:(CustomAuthoredSet)->CustomAuthoredSet):CustomTrainingCycle {
        val list=candidate.weekOverrides.getValue("strength")
        return candidate.copy(weekOverrides=mapOf("strength" to list.map{week->
            if(week.weekInBlock!=3)week else week.copy(sets=week.sets.mapIndexed{i,s->
                if(i==week.sets.lastIndex)f(s) else s})
        }))
    }

    @Test fun everyNamedWeekRequiresIndependentEvidenceAndRetainsNoWriteAuthority() {
        val valid=audit() as CustomNamedPercentageCycleAudit.Verified
        assertEquals(2,valid.reviewedWeeks)
        assertFalse(valid.maySaveProgram)
        assertFalse(valid.maySignoff)
        assertFalse(valid.mayIssueWorkout)
        for(evidence in listOf(listOf(a),listOf(b),listOf(a,b,proof(2)))) {
            assertEquals(CustomNamedPercentageCycleAudit.Refused(
                CustomNamedPercentageCycleRefusal.MISSING_OR_EXTRA_EVIDENCE),audit(evidence=evidence))
        }
        assertEquals(CustomNamedPercentageCycleAudit.Refused(
            CustomNamedPercentageCycleRefusal.DUPLICATE_WEEK),audit(evidence=listOf(a,a,b)))
        assertEquals(CustomNamedPercentageCycleAudit.Refused(
            CustomNamedPercentageCycleRefusal.INVALID_SOURCE),
            CustomNamedPercentageCycleAuditDomain.review(candidate,stamp(base),listOf(a,b)))
    }

    @Test fun verifiedNamedStructureRetainsExactMetadataAndDoesNotEnableOrdinaryReview() {
        val inventory=listOf(CustomExerciseAvailability("press",a.request.verifiedStock,false))
        val ordinary=CustomTrainingCycleDomain.review(candidate,inventory)
        assertEquals(CustomCycleReviewStatus.UNRESOLVED,ordinary.status)
        assertNull(ordinary.verifiedDraft)
        val reviewed=CustomTrainingCycleDomain.reviewNamedForAudit(candidate,inventory,
            stamp(candidate),listOf(a,b))
        assertEquals(reviewed.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,reviewed.status)
        assertEquals(candidate,reviewed.verifiedDraft)
        val third=CustomTrainingCycleDomain.week(reviewed,3)!!
        val issuedIntent=third.days.single{it.weekday==2}.slots.single().sets.last()
        assertEquals(n("190"),(issuedIntent.load as DraftSetLoad.Verified).value)
        assertTrue(issuedIntent.percentageIntent!!.repOut)
        assertEquals(1,issuedIntent.exactRepTarget)
        assertFalse(reviewed.canIssueWorkout)
        val missing=CustomTrainingCycleDomain.reviewNamedForAudit(candidate,inventory,
            stamp(candidate),listOf(a))
        assertNull(missing.verifiedDraft)
        val unavailable=CustomTrainingCycleDomain.reviewNamedForAudit(candidate,
            emptyList(),stamp(candidate),listOf(a,b))
        assertNull(unavailable.verifiedDraft)
        assertTrue(unavailable.issues.any{it.kind==CustomCycleIssueKind.UNAVAILABLE_EXERCISE})
    }

    @Test fun structurallyValidWrongLoadRepOutOrReferenceCannotBecomeConsent() {
        val lowered=changeSecond{s->s.copy(load=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.MACHINE_STACK,n("185")))}
        assertTrue(lowered.weekOverrides.getValue("strength").last().sets.last().let{
            it.percentageIntent!!.validForSet(it)})
        val noRepOut=changeSecond{s->s.copy(percentageIntent=s.percentageIntent!!.copy(repOut=false))}
        val wrongReference=changeSecond{s->s.copy(percentageIntent=s.percentageIntent!!.copy(
            referenceLoad=n("205")))}
        for(changed in listOf(lowered,noRepOut,wrongReference)) {
            assertEquals(CustomNamedPercentageCycleAudit.Refused(
                CustomNamedPercentageCycleRefusal.RECIPE_MISMATCH,b.key),audit(changed))
        }
        assertEquals(CustomNamedPercentageCycleAudit.Refused(
            CustomNamedPercentageCycleRefusal.RECIPE_MISMATCH,b.key),
            audit(evidence=listOf(a,b.copy(request=b.request.copy(confirmedReference=n("205"))))))
        assertEquals(CustomNamedPercentageCycleAudit.Refused(
            CustomNamedPercentageCycleRefusal.RECIPE_REFUSED,b.key),
            audit(evidence=listOf(a,b.copy(request=b.request.copy(referenceConfirmed=false)))))
    }

    @Test fun ordinaryBaseAndTieredMetadataCannotBypassSeparateAuthorities() {
        val named=candidate.weekOverrides.getValue("strength").first().sets.last()
        val modified=base.copy(blocks=base.blocks.map{block->block.copy(days=block.days.map{d->
            d.copy(slots=d.slots.map{it.copy(sets=listOf(named))})
        })})
        assertEquals(CustomNamedPercentageCycleAudit.Refused(
            CustomNamedPercentageCycleRefusal.UNREVIEWED_BASE_SET),audit(modified,emptyList()))
        val tiered=changeSecond{s->s.copy(percentageIntent=s.percentageIntent!!.copy(
            scheme=NamedPercentageScheme.GZCLP,tierStage=0))}
        assertEquals(CustomNamedPercentageCycleAudit.Refused(
            CustomNamedPercentageCycleRefusal.TIER_HISTORY_REQUIRED,b.key),audit(tiered))
    }
}
