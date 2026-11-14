package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ReviewedTrapBarCatalogTest {
    private fun n(value:String)=BigDecimal(value)
    private val ids=listOf("deadlift","snatch-deadlift","paused-deadlift","rack-pull",
        "trap-bar-deadlift","bb-shrug","db-shrug","cable-shrug","smith-shrug",
        "machine-shrug","behind-shrug","kelso-shrug","trap-bar-shrug",
        "power-shrug","incline-shrug","cable-face-shrug","db-incline-shrug")
    private val all=StarterExerciseTranche.entries()
    private val added=all.drop(301).take(ids.size)
    private val template=ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,120,null,60))},
        TrainingMuscleCoverage.current.map{DeclaredMuscleDoseBounds(it,
            BigDecimal.ZERO,n("20"),n("40"),0)},emptyList(),4,1)
    private fun physical(entry:StarterCatalogEntry):Pair<BigDecimal,VerifiedLoadStock> {
        val movement=entry.movement
        return when(movement.expectedLoadBasis) {
            LoadBasis.BARBELL_TOTAL -> {
                val mass=if(movement.equipmentId=="trap-hex-bar")"55" else "45"
                (n(mass)+n("90")) to VerifiedLoadStock.Barbell(LoadUnit.LB,n(mass),mapOf(n("45") to 1))
            }
            LoadBasis.DUMBBELL_PER_HAND -> n("30") to VerifiedLoadStock.Totals(
                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("30")))
            else -> n("40") to VerifiedLoadStock.Totals(LoadUnit.LB,
                checkNotNull(movement.expectedLoadBasis),listOf(n("20"),n("40"),n("60")))
        }
    }
    private fun review(id:String,stock:Boolean=true,fixtures:Boolean=true,
        attachment:Boolean=true,scope:List<TrainingMuscle> = TrainingMuscleCoverage.current,
        load:BigDecimal?=null,otherStock:Map<String,VerifiedLoadStock> = emptyMap()):CatalogWeekReview {
        val entry=added.single{it.legacyCuratedId==id}
        val movement=entry.movement
        val (chosen,physical)=physical(entry)
        val gym=CatalogGym(otherStock+(if(stock)mapOf(checkNotNull(movement.equipmentId) to physical)
            else emptyMap()),if(attachment)setOfNotNull(movement.attachmentId) else emptySet(),
            if(fixtures)movement.requiredFacilityIds else emptySet())
        return CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
            template.copy(bounds=template.bounds.filter{it.muscle in scope}),
            listOf(CatalogExerciseChoice(id,load ?:chosen,90,120,2,3,2,1,1,5)),gym))
    }

    @Test fun seventeenExactIdentitiesAppendWithoutAliasesOrLoadTransfers() {
        assertEquals(ids,added.map{it.legacyCuratedId})
        assertEquals(318,all.size)
        assertEquals(all.size,all.map{it.movement.exerciseId}.distinct().size)
        assertEquals(all.size,all.map{it.movement.familyId to it.movement.variantId}.distinct().size)
        assertTrue(added.none{it.mayTransferPreviousLoad})
        assertTrue(added.all{it.practicalRepBand==it.movement.sourcePracticalReps})
        assertEquals(RepBand(4,8),added.single{it.legacyCuratedId=="trap-bar-deadlift"}.practicalRepBand)
        assertEquals(RepBand(3,6),added.single{it.legacyCuratedId=="paused-deadlift"}.practicalRepBand)
        assertNotEquals(all.single{it.legacyCuratedId=="conventional_deadlift"}.movement.variantId,
            added.single{it.legacyCuratedId=="deadlift"}.movement.variantId)
    }

    @Test fun everyNewMovementRequiresItsExactPhysicalStock() {
        for(id in ids) {
            assertTrue("$id: ${review(id).issues}",review(id).issues.isEmpty())
            assertTrue(id,review(id,stock=false).issues.any{it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE})
            assertTrue(id,review(id,load=n("999")).issues.any{it.kind==CatalogIssueType.LOAD_UNVERIFIED})
        }
    }

    @Test fun exactSupportsAndCableAttachmentsCannotBeGuessed() {
        for(entry in added) {
            val id=entry.legacyCuratedId
            if(entry.movement.requiredFacilityIds.isNotEmpty())assertTrue(id,
                review(id,fixtures=false).issues.any{it.kind==CatalogIssueType.FACILITY_UNAVAILABLE})
            if(entry.movement.attachmentId!=null)assertTrue(id,
                review(id,attachment=false).issues.any{it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE})
        }
        assertEquals(setOf("chest-support"),added.single{it.legacyCuratedId=="kelso-shrug"}.movement.requiredFacilityIds)
    }

    @Test fun aStraightBarCannotStandInForTheHeavierTrapBarOrSupplyItsCalibration() {
        val straight=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),mapOf(n("45") to 1))
        assertTrue(review("trap-bar-deadlift",stock=false,otherStock=mapOf("straight-bar" to straight))
            .issues.any{it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE})
        assertTrue(review("trap-bar-deadlift",load=n("135")).issues.any{it.kind==CatalogIssueType.LOAD_UNVERIFIED})
        assertTrue(review("trap-bar-deadlift",load=n("145")).issues.isEmpty())
        assertTrue(review("bb-shrug",load=n("135")).issues.isEmpty())
        assertTrue(review("trap-bar-shrug",load=n("135")).issues.any{it.kind==CatalogIssueType.LOAD_UNVERIFIED})
    }

    @Test fun oldMuscleScopesRefuseNewWorkInsteadOfSilentlyDroppingItsCredits() {
        for(scope in listOf(TrainingMuscleCoverage.legacyV1,TrainingMuscleCoverage.legacyV2))
            for(id in ids)assertTrue(id,review(id,scope=scope).issues.any{
                it.kind==CatalogIssueType.MUSCLE_TARGETS_UNDECLARED})
        assertTrue(added.take(5).all{it.movement.credits.single{c->c.role==MuscleCreditRole.DIRECT}
            .muscle==TrainingMuscle.SPINAL_ERECTORS})
        assertTrue(added.drop(5).all{it.movement.credits.single{c->c.role==MuscleCreditRole.DIRECT}
            .muscle==TrainingMuscle.TRAPS})
        assertTrue(added.none{it.movement.credits.any{c->c.muscle in setOf(TrainingMuscle.LATS,TrainingMuscle.UPPER_BACK)}})
    }

    @Test fun heavyHingeVariantsShareRecoveryBudgetButKeepDifferentIdentities() {
        for(entry in added.take(5)) {
            assertEquals("heavy-barbell-hinge",entry.movement.recoveryGroupId)
            assertEquals(2,entry.movement.minimumRecoveryGroupDayGap)
        }
        assertNotEquals(added.single{it.legacyCuratedId=="trap-bar-deadlift"}.movement.exerciseId,
            added.single{it.legacyCuratedId=="trap-bar-shrug"}.movement.exerciseId)
    }

    @Test fun anActualSwapRequiresFreshTrapBarTargetsAndDoesNotCopyStraightBarLoading() {
        val source=KotlinTrainingSourceRevision("athlete-1",1,"program-1",1,"gym-1",1,"catalog-1","rules-1")
        val targets=(0..1).map{ordinal->KotlinWorkoutTarget("conventional_deadlift",ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY,"old-$ordinal",DraftSetType.WORKING,ordinal,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,n("135")),RepBand(4,8),2,60,150)}
        val snapshot=KotlinStagedWorkout(source,"grant-1","workout-1",0,0,
            ComposerSessionPurpose.HYPERTROPHY,45,targets)
        val journal=KotlinWorkoutJournalDomain.begin(snapshot,
            KotlinStoredWorkoutIdentity("receipt-1","workout-1",source,1))
        val selected=checkNotNull(review("trap-bar-deadlift").composedRequest).exercises.single()
        fun intents(load:String)=targets.map{KotlinComposerSwapSetIntent(it.type,it.ordinal,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,n(load)),RepBand(4,8),2)}
        fun swap(intents:List<KotlinComposerSwapSetIntent>)=KotlinComposerSwapReviewDomain.review(
            journal,"trap-swap",journal.revision,"conventional_deadlift",selected,ComposerFocus.FULL_BODY,intents)
        assertTrue(swap(emptyList()) is KotlinComposerSwapReview.Rejected)
        assertTrue(swap(intents("135")) is KotlinComposerSwapReview.Rejected)
        val accepted=swap(intents("145")) as KotlinComposerSwapReview.Ready
        val changed=KotlinWorkoutJournalDomain.reduce(journal,accepted.operation) as KotlinJournalDecision.Proposed
        assertTrue(changed.state.snapshot.targets.all{it.exerciseId=="trap-bar-deadlift" &&
            (it.load as? DraftSetLoad.Verified)?.value==n("145")})
        assertEquals(snapshot,journal.snapshot)
        assertTrue(journal.snapshot.targets.all{(it.load as DraftSetLoad.Verified).value==n("135")})
    }

    @Test fun currentPolicyTracksNewGroupsWhileV9ReplaysItsExactEighteenRows() {
        val base=StarterProgramReleaseFixture.request(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val old=CoachFirstProgramStartDomain.review(base.copy(policyVersion=CoachFirstProgramStartDomain.V9_POLICY_VERSION))
        val fresh=CoachFirstProgramStartDomain.review(base)
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,old.status)
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,fresh.status)
        assertEquals("v9",old.policy?.revision)
        assertEquals("v10",fresh.policy?.revision)
        assertEquals(TrainingMuscleCoverage.legacyV2,old.policy?.selectedBounds?.map{it.muscle})
        assertEquals(TrainingMuscleCoverage.current,fresh.policy?.selectedBounds?.map{it.muscle})
        assertEquals(old.prescription?.sessions,fresh.prescription?.sessions)
        assertEquals(old.policy?.selectedBounds,fresh.policy?.selectedBounds?.filter{it.muscle.introducedIn<=2})
    }

    @Test fun generatedProgramAndEveryConvertedWeekContainRealTrapBarAndTrapeziusWork() {
        val base=StarterProgramReleaseFixture.request(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val athlete=checkNotNull(base.setup.reviewed)
        val trap=VerifiedLoadStock.Barbell(LoadUnit.LB,n("55"),mapOf(n("45") to 1))
        val input=base.copy(setup=base.setup.copy(reviewed=athlete.copy(
            sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,maxBarbellExercisesPerDay=3,
            gym=athlete.gym.copy(confirmedStock=athlete.gym.confirmedStock+mapOf("trap-hex-bar" to trap)))),
            startingLoads=base.startingLoads+mapOf("trap-bar-deadlift" to n("145"),"db-shrug" to n("30")))
        val result=CoachFirstProgramStartDomain.review(input)
        assertEquals(result.issues.toString(),FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
        for(muscle in listOf(TrainingMuscle.SPINAL_ERECTORS,TrainingMuscle.TRAPS)) {
            val dose=checkNotNull(result.prescription?.finalAudit).muscles.getValue(muscle)
            assertEquals(DoseFinding.WITHIN_BOUNDS,dose.finding)
            assertTrue(dose.directWorkingSets>=n("4"))
        }
        val conversion=CoachFirstProgramDraftConversionDomain.propose(result,input.setup,input.catalog,
            "trap-cycle","Verified trap bar program",4)
        assertEquals(conversion.reason,FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,conversion.status)
        val cycle=checkNotNull(conversion.cycle)
        val policy=checkNotNull(CoachFirstProgramDraftConversionDomain.policyForInitialFullCycleReview(result,input.setup))
        val audit=CustomCycleCoachAuditDomain.review(cycle,input.setup,input.catalog,policy)
        assertEquals(audit.issues.toString(),CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        for(number in 1..4) {
            val week=checkNotNull(CustomTrainingCycleDomain.week(checkNotNull(audit.structuralReview),number))
            for((id,load) in mapOf("trap-bar-deadlift" to n("145"),"db-shrug" to n("30"))) {
                val sets=week.days.flatMap{it.slots}.filter{it.exerciseId==id}.flatMap{it.sets}.filter{it.type==DraftSetType.WORKING}
                assertTrue("$number/$id",sets.isNotEmpty())
                assertTrue(sets.all{(it.load as? DraftSetLoad.Verified)?.value==load})
            }
        }
        assertFalse(result.mayIssueWorkout)
        assertFalse(audit.mayIssueWorkout)
    }
}
