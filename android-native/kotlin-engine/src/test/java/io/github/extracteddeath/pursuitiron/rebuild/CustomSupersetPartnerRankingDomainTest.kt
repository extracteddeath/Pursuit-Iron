package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomSupersetPartnerRankingDomainTest {
    private fun credit(m:TrainingMuscle,role:MuscleCreditRole=MuscleCreditRole.DIRECT)=
        DeclaredMuscleCredit(m,role,if(role==MuscleCreditRole.DIRECT)BigDecimal.ONE else BigDecimal("0.25"))
    private fun option(id:String,muscle:TrainingMuscle,family:String=id,
        role:ComposerRole=ComposerRole.ACCESSORY,pattern:ComposerPattern=ComposerPattern.ISOLATION,
        indirect:List<TrainingMuscle> =emptyList(),attachment:String?=null)=CustomExercisePickerOption(
        id,family,"variant-$id",attachment,null,null,true,
        CustomExerciseAvailability(id,null,true,attachment?.let{setOf(it)} ?:emptySet()),
        pattern,role,listOf(credit(muscle))+indirect.map{credit(it,MuscleCreditRole.INDIRECT)})
    private fun slot(id:String,exercise:String=id,sets:Int=3,role:ComposerRole=ComposerRole.ACCESSORY,
        group:String?=null,attachment:String?=null)=CustomExerciseSlot(id,exercise,role,
        List(sets){CustomAuthoredSet(DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
            RepBand(10,15),2,45,90)},attachmentId=attachment,supersetGroup=group)
    private fun day(vararg slots:CustomExerciseSlot)=CustomTrainingDay(0,"Accessories",
        ComposerSessionPurpose.HYPERTROPHY,slots.toList())
    private val anchor=option("curl",TrainingMuscle.BICEPS)
    private fun rank(day:CustomTrainingDay,available:List<CustomExercisePickerOption>,
        source:String="curl",group:String?=null,allow:Boolean=true,coach:Boolean=true)=
        CustomSupersetPartnerRankingDomain.rank(day,available,source,group,allow,coach)
    private fun ids(result:CustomSupersetPartnerIdeas)=result.partners.map{it.slotId}

    @Test fun ranksDifferentReviewedTargetsByMatchingSetsThenDayPosition() {
        val available=listOf(anchor,option("calf",TrainingMuscle.CALVES),option("abs",TrainingMuscle.ABS))
        val result=rank(day(slot("curl"),slot("calf",sets=2),slot("abs")),available)
        assertEquals(CustomSupersetIdeasStatus.READY,result.status)
        assertEquals(listOf("abs","calf"),ids(result))
        assertEquals(listOf(3,2),result.partners.map{it.workingSets})
        assertTrue(result.partners.all{it.anchorWorkingSets==3 && !it.mayApplyWithoutReview && !it.mayTransferWeight})
    }
    @Test fun neitherSameDirectTargetNorCrossDirectIndirectOverlapIsAnIdea() {
        val available=listOf(anchor,option("curl-other",TrainingMuscle.BICEPS),
            option("row",TrainingMuscle.UPPER_BACK,indirect=listOf(TrainingMuscle.BICEPS)),
            option("calf",TrainingMuscle.CALVES))
        assertEquals(listOf("calf"),ids(rank(day(slot("curl"),slot("curl-other"),slot("row"),slot("calf")),available)))
        val source=anchor.copy(muscleCredits=anchor.muscleCredits+credit(TrainingMuscle.BRACHIALIS,MuscleCreditRole.INDIRECT))
        assertTrue(rank(day(slot("curl"),slot("hammer")),listOf(source,option("hammer",TrainingMuscle.BRACHIALIS))).partners.isEmpty())
    }
    @Test fun sharedSupportingTargetsAreDisclosedAndRankBehindNoRecordedOverlap() {
        val source=anchor.copy(muscleCredits=anchor.muscleCredits+credit(TrainingMuscle.WRIST_FLEXORS,MuscleCreditRole.INDIRECT))
        val available=listOf(source,option("triceps",TrainingMuscle.TRICEPS,indirect=listOf(TrainingMuscle.WRIST_FLEXORS)),
            option("calf",TrainingMuscle.CALVES))
        val result=rank(day(slot("curl"),slot("triceps"),slot("calf")),available)
        assertEquals(listOf("calf","triceps"),ids(result))
        assertEquals(setOf(TrainingMuscle.WRIST_FLEXORS),result.partners.last().sharedSupportingMuscles)
    }
    @Test fun mainAndSupportingLiftsStayManualOnBothSidesOfThePair() {
        for(role in listOf(ComposerRole.PRIMARY,ComposerRole.SECONDARY)) {
            val options=listOf(anchor.copy(role=role),option("calf",TrainingMuscle.CALVES))
            assertEquals(CustomSupersetIdeasStatus.ANCHOR_PROTECTED,
                rank(day(slot("curl",role=role),slot("calf")),options).status)
            assertTrue(rank(day(slot("curl"),slot("calf",role=role)),
                listOf(anchor,option("calf",TrainingMuscle.CALVES,role=role))).partners.isEmpty())
        }
    }
    @Test fun accessoryRoleAloneDoesNotTurnACompoundPatternIntoAnIdea() {
        val options=listOf(anchor,option("hinge",TrainingMuscle.HAMSTRINGS,pattern=ComposerPattern.HINGE))
        assertTrue(rank(day(slot("curl"),slot("hinge")),options).partners.isEmpty())
    }
    @Test fun doesNotStealAnotherGroupButCanHelpEditTheRequestedGroup() {
        val available=listOf(anchor,option("calf",TrainingMuscle.CALVES),option("abs",TrainingMuscle.ABS))
        val d=day(slot("curl",group="mine"),slot("calf",group="other"),slot("abs",group="mine"))
        assertEquals(CustomSupersetIdeasStatus.ANCHOR_UNAVAILABLE,rank(d,available).status)
        assertEquals(listOf("abs"),ids(rank(d,available,group="mine")))
    }
    @Test fun duplicateIdsAndUnavailableAnchorCannotProduceGuessedAdvice() {
        val d=day(slot("curl"),slot("calf"))
        val available=listOf(anchor,option("calf",TrainingMuscle.CALVES))
        assertEquals(CustomSupersetIdeasStatus.SOURCE_UNRESOLVED,rank(d,available+anchor).status)
        assertEquals(CustomSupersetIdeasStatus.SOURCE_UNRESOLVED,rank(d,emptyList()).status)
        assertEquals(CustomSupersetIdeasStatus.ANCHOR_UNAVAILABLE,rank(d,available,source="missing").status)
        assertEquals(CustomSupersetIdeasStatus.SOURCE_UNRESOLVED,rank(day(slot("curl"),slot("curl")),available).status)
    }
    @Test fun missingOrInvalidCatalogCreditsAreNotInferredFromNames() {
        val d=day(slot("curl"),slot("calf"))
        val calf=option("calf",TrainingMuscle.CALVES)
        for(bad in listOf(calf.copy(pattern=null),calf.copy(role=null),calf.copy(familyId=""),
            calf.copy(muscleCredits=emptyList()),calf.copy(muscleCredits=listOf(credit(TrainingMuscle.CALVES),credit(TrainingMuscle.CALVES))),
            calf.copy(muscleCredits=listOf(DeclaredMuscleCredit(TrainingMuscle.CALVES,MuscleCreditRole.DIRECT,BigDecimal.ZERO)))))
            assertTrue(rank(d,listOf(anchor,bad)).partners.isEmpty())
        assertEquals(CustomSupersetIdeasStatus.SOURCE_UNRESOLVED,
            rank(d,listOf(anchor.copy(muscleCredits=emptyList()),calf)).status)
    }
    @Test fun permissionAndCoachingOffSwitchesSuppressIdeasIndependently() {
        val d=day(slot("curl"),slot("calf"));val available=listOf(anchor,option("calf",TrainingMuscle.CALVES))
        assertEquals(CustomSupersetIdeasStatus.COACHING_OFF,rank(d,available,coach=false).status)
        assertEquals(CustomSupersetIdeasStatus.SUPERSETS_OFF,rank(d,available,allow=false).status)
        assertEquals(CustomSupersetIdeasStatus.READY,rank(d,available,
            coach=CoachAssistancePolicy.PROGRESSION_ONLY.coachingSuggestionsEnabled).status)
    }
    @Test fun onlyCurrentDayMovementsWithWorkingSetsCanBecomeIdeas() {
        val warm=slot("calf").copy(sets=slot("calf").sets.map{it.copy(type=DraftSetType.WARMUP)})
        val available=listOf(anchor,option("calf",TrainingMuscle.CALVES),option("abs",TrainingMuscle.ABS))
        assertTrue(rank(day(slot("curl"),warm),available).partners.isEmpty())
        assertEquals(CustomSupersetIdeasStatus.ANCHOR_UNAVAILABLE,rank(day(slot("curl",sets=0)),available).status)
        assertTrue(rank(day(slot("curl")),available).partners.isEmpty())
    }
    @Test fun attachmentMismatchAndSameFamilyRemainManualChoices() {
        val available=listOf(anchor,option("calf",TrainingMuscle.CALVES,attachment="rope"),
            option("abs",TrainingMuscle.ABS,family=anchor.familyId))
        assertTrue(rank(day(slot("curl"),slot("calf",attachment="bar"),slot("abs")),available).partners.isEmpty())
    }
    @Test fun warmupsAndAmplifiersDoNotInflateWorkingSetCount() {
        val working=slot("curl").sets.map{it.copy(amplifier=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20))}
        val warm=working.first().copy(type=DraftSetType.WARMUP,amplifier=null)
        val d=day(slot("curl").copy(sets=listOf(warm)+working),slot("calf"))
        val result=rank(d,listOf(anchor,option("calf",TrainingMuscle.CALVES)))
        assertEquals(3,result.partners.single().anchorWorkingSets)
    }
    @Test fun tiesFavorNearbyThenFollowingMovementsAndStayDeterministic() {
        val d=day(slot("before"),slot("curl"),slot("after"))
        val options=listOf(anchor,option("before",TrainingMuscle.CALVES),option("after",TrainingMuscle.ABS))
        val first=rank(d,options)
        assertEquals(listOf("after","before"),ids(first))
        assertEquals(first,rank(d,options.reversed()))
    }
    @Test fun everyEligibleDayPartnerRemainsInTheRankedUniverse() {
        val candidates=(1..31).map{"calf-$it"}
        val d=day(*(listOf(slot("curl"))+candidates.map{slot(it)}).toTypedArray())
        val result=rank(d,listOf(anchor)+candidates.map{option(it,TrainingMuscle.CALVES)})
        assertEquals(31,result.partners.size)
        assertEquals(candidates,ids(result))
    }
    @Test fun readingIdeasLeavesEveryAuthoredFieldAndTheInputOrderUnchanged() {
        val a=slot("curl").copy(pinned=true,coachNote="Keep this",machineInstanceId="A")
        val d=day(a,slot("calf"));val before=d.copy(slots=d.slots.toList())
        val options=listOf(anchor,option("calf",TrainingMuscle.CALVES))
        val result=rank(d,options)
        assertEquals(before,d)
        assertEquals(listOf("curl","calf"),d.slots.map{it.slotId})
        assertThrows(UnsupportedOperationException::class.java){
            (result.partners as MutableList).clear()
        }
    }
    @Test fun separateBicepsBrachialisAndWristChannelsAreNeverCollapsedIntoGenericArms() {
        val options=listOf(option("curl",TrainingMuscle.BICEPS),option("brachialis",TrainingMuscle.BRACHIALIS),
            option("flexion",TrainingMuscle.WRIST_FLEXORS),option("extension",TrainingMuscle.WRIST_EXTENSORS))
        val result=rank(day(slot("curl"),slot("brachialis"),slot("flexion"),slot("extension")),options)
        assertEquals(listOf("brachialis","flexion","extension"),ids(result))
        assertTrue(result.partners.all{it.sharedSupportingMuscles.isEmpty()})
    }
}
