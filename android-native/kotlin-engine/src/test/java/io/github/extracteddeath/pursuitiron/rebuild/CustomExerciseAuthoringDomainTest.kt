package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomExerciseAuthoringDomainTest {
    private fun n(value: String) = BigDecimal(value)
    private val cable = VerifiedLoadStock.Totals(
        LoadUnit.LB, LoadBasis.CABLE_STACK, listOf(n("20"),n("25"),n("30")))
    private val gym = ReviewedGymProfile("gym-1",LoadUnit.LB,
        mapOf("cable-1" to cable),
        setOf("cable-station"),setOf("rope","straight-bar"))

    private fun setup(
        attachments: Set<String> = setOf("rope","straight-bar"),
        allowBodyweight: Boolean = true,
        excluded: Set<String> = emptySet(),
        allowSupersets: Boolean = true
    ): AthleteSetupReview {
        val reviewed = ReviewedAthleteSetup(
            "profile-1","athlete-1",AthleteTrainingGoal.POWERBUILDING,
            AthleteExperience.ADVANCED, ComposerSplit.FULL_BODY,
            setOf(0,2,4),"America/New_York",
            SessionTimeBucket.SIXTY_TO_NINETY,
            HardSessionLimit.NO_HARD_LIMIT,
            gym.copy(availableAttachments = attachments),
            allowBodyweight,allowSupersets,true,2,
            emptySet(),emptySet(),excluded,
            emptySet(),emptySet(),emptySet(),setOf(1,3,5,6))
        return AthleteSetupReview(
            AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,emptyList(),reviewed)
    }
    private fun credit() = listOf(DeclaredMuscleCredit(
        TrainingMuscle.TRICEPS,MuscleCreditRole.DIRECT,BigDecimal.ONE))
    private fun movement(
        id: String, attachment: String? = "rope",
        equipment: String? = "cable-1",
        creditStatus: CatalogCreditStatus =
            CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
        variant: String = id,
        facilities: Set<String> = setOf("cable-station")
    ) = CatalogMovement(
        id,"triceps-family",variant,attachment,
        ComposerPattern.ISOLATION,ComposerRole.SECONDARY,
        setOf(ComposerFocus.FULL_BODY),
        equipment,if(equipment == null) null else LoadBasis.CABLE_STACK,
        credit(),creditStatus,"review-1",facilities
    )
    private fun catalog() = listOf(
        movement("rope-pushdown"),
        movement("straight-pushdown",attachment="straight-bar"),
        movement("bodyweight-extension",attachment=null,equipment=null,
            facilities=emptySet())
    )
    private fun draft():CustomTrainingCycle {
        val blank = CustomCycleBuilderDomain.blank(
            "cycle-1","athlete-1",AthleteTrainingGoal.POWERBUILDING,
            "Fully custom","base",4,TrainingPhase.HYPERTROPHY,
            CustomSplitIdentity.Freeform("Arms / Upper"))!!
        return (CustomCycleBuilderDomain.addDay(
            blank,1,"base",CustomTrainingDay(0,"Arms",
                ComposerSessionPurpose.HYPERTROPHY,emptyList()))
            as CustomCycleBuilderResult.Proposed).draft
    }
    private fun working(
        value: String = "25",
        amp: CustomSetAmplifier? = null
    ) = CustomAuthoredSet(
        DraftSetType.WORKING,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.CABLE_STACK,n(value)),
        RepBand(10,15),2,50,105,amp)
    private fun slot(sets: List<CustomAuthoredSet> = listOf(working())) =
        CustomExerciseSlot("slot-1","rope-pushdown",
            ComposerRole.SECONDARY,sets,attachmentId="rope",pinned=true,
            coachNote="Avoid elbow flare")
    private fun refused(r:CustomAuthoringResult) =
        (r as CustomAuthoringResult.Rejected).reason

    @Test fun pickerKeepsOnlyRealGymCompatibleReviewedCatalogVariants() {
        val items = CustomExerciseAuthoringDomain.picker(setup(),catalog())
        assertEquals(listOf("rope-pushdown","straight-pushdown",
            "bodyweight-extension"),items.map{it.exerciseId})
        assertEquals(LoadBasis.CABLE_STACK,items.first().loadBasis)
        assertEquals(cable,items.first().availability.stock)
        assertEquals(setOf("rope"),items.first().availability.attachmentIds)
        val withoutRope=CustomExerciseAuthoringDomain.picker(
            setup(attachments=setOf("straight-bar")),catalog())
        assertEquals(listOf("straight-pushdown","bodyweight-extension"),
            withoutRope.map {it.exerciseId})
        val withoutBodyweight=CustomExerciseAuthoringDomain.picker(
            setup(allowBodyweight=false),catalog())
        assertFalse(withoutBodyweight.any{it.exerciseId=="bodyweight-extension"})
        val excluded=CustomExerciseAuthoringDomain.picker(
            setup(excluded=setOf("rope-pushdown")),catalog())
        assertFalse(excluded.any{it.exerciseId=="rope-pushdown"})
    }

    @Test fun invalidOrUnreviewedCatalogNeverCreatesAnExerciseChoice() {
        val unreviewed=movement("unreviewed",
            creditStatus=CatalogCreditStatus.UNREVIEWED)
        assertTrue(CustomExerciseAuthoringDomain.picker(
            setup(),listOf(unreviewed)).isEmpty())
        assertTrue(CustomExerciseAuthoringDomain.picker(
            setup(),listOf(movement("same"),movement("same"))).isEmpty())
        assertTrue(CustomExerciseAuthoringDomain.picker(
            AthleteSetupReview(AthleteSetupStatus.NEEDS_INPUT,emptyList(),null),
            catalog()).isEmpty())
        assertTrue(CustomExerciseAuthoringDomain.picker(
            setup(),listOf(movement("unavailable",
                equipment="machine-unconfirmed"))).isEmpty())
        assertTrue(CustomExerciseAuthoringDomain.picker(
            setup(),listOf(movement("missing-facility",
                facilities=setOf("unavailable-rack")))).isEmpty())
    }

    @Test fun acceptedAuthoredSlotDoesNotGenerateAnySetOrOverride() {
        val original=draft()
        val candidate=slot(listOf(working("25"),
            working("30",CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
                8,15,DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.CABLE_STACK,n("20"))))))
        val result=CustomExerciseAuthoringDomain.addSelected(
            original,2,"base",0,candidate,setup(),catalog())
        assertTrue(result.toString(),result is CustomAuthoringResult.Accepted)
        val proposed=(result as CustomAuthoringResult.Accepted).proposal
        assertEquals(3L,proposed.draft.revision)
        assertEquals(candidate,proposed.draft.blocks.single()
            .days.single().slots.single())
        assertEquals(original.weekOverrides,proposed.draft.weekOverrides)
        assertFalse(CustomTrainingCycleDomain.review(proposed.draft,
            CustomExerciseAuthoringDomain.picker(setup(),catalog())
                .map{it.availability}).canIssueWorkout)
        assertEquals(CustomAuthoringRefusal.AUTHORING_CONFLICT,
            refused(CustomExerciseAuthoringDomain.addSelected(
                proposed.draft,2,"base",0,
                candidate.copy(slotId="slot-2"),setup(),catalog())))
    }

    @Test fun typedImpossibleWeightsAndUnverifiedAttachmentsFailClosed() {
        val before=draft()
        assertEquals(CustomAuthoringRefusal.INATTAINABLE_LOAD,
            refused(CustomExerciseAuthoringDomain.addSelected(
                before,2,"base",0,slot(listOf(working("27.5"))),
                setup(),catalog())))
        assertEquals(CustomAuthoringRefusal.ATTACHMENT_UNAVAILABLE,
            refused(CustomExerciseAuthoringDomain.addSelected(
                before,2,"base",0,slot().copy(attachmentId="v-bar"),
                setup(),catalog())))
        assertEquals(CustomAuthoringRefusal.EXERCISE_UNAVAILABLE,
            refused(CustomExerciseAuthoringDomain.addSelected(
                before,2,"base",0,slot(),setup(attachments=emptySet()),
                catalog())))
        assertEquals(CustomAuthoringRefusal.INVALID_SETS,
            refused(CustomExerciseAuthoringDomain.addSelected(
                before,2,"base",0,slot(listOf(working("30",
                    CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,15,
                        DraftSetLoad.Verified(LoadUnit.LB,
                            LoadBasis.CABLE_STACK,n("30")))))),
                setup(),catalog())))
        assertEquals(CustomAuthoringRefusal.INVALID_SETS,
            refused(CustomExerciseAuthoringDomain.addSelected(
                before,2,"base",0,slot(emptyList()),setup(),catalog())))
    }

    @Test fun scopedSetEditPreservesPinAttachmentNotesAndSeparateWeekOverrides() {
        val base=draft()
        val chosen=(CustomExerciseAuthoringDomain.addSelected(
            base,2,"base",0,slot(),setup(),catalog())
            as CustomAuthoringResult.Accepted).proposal.draft
        val overridden=(CustomCycleBuilderDomain.editWeek(
            chosen,3,"base",2,0,"slot-1",listOf(working("30")))
            as CustomCycleBuilderResult.Proposed).draft
        val updated=(CustomExerciseAuthoringDomain.replaceSelectedSets(
            overridden,4,"base",0,"slot-1",listOf(working("20")),
            setup(),catalog()) as CustomAuthoringResult.Accepted).proposal.draft
        val chosenSlot=updated.blocks.single().days.single().slots.single()
        assertEquals(listOf(working("20")),chosenSlot.sets)
        assertTrue(chosenSlot.pinned)
        assertEquals("rope",chosenSlot.attachmentId)
        assertEquals("Avoid elbow flare",chosenSlot.coachNote)
        assertEquals(listOf(working("30")),
            updated.weekOverrides.getValue("base").single().sets)
        assertEquals(CustomAuthoringRefusal.INATTAINABLE_LOAD,
            refused(CustomExerciseAuthoringDomain.replaceSelectedSets(
                updated,5,"base",0,"slot-1",listOf(working("27.5")),
                setup(),catalog())))
    }

    @Test fun supersetsAndDifferentAthletesRequireExplicitPermission() {
        val before=draft()
        assertEquals(CustomAuthoringRefusal.AUTHORING_CONFLICT,
            refused(CustomExerciseAuthoringDomain.addSelected(
                before,2,"base",0,slot().copy(supersetGroup="pair"),
                setup(allowSupersets=false),catalog())))
        assertEquals(CustomAuthoringRefusal.SETUP_UNRESOLVED,
            refused(CustomExerciseAuthoringDomain.addSelected(
                before.copy(athleteId="someone-else"),2,"base",0,
                slot(),setup(),catalog())))
    }
}
