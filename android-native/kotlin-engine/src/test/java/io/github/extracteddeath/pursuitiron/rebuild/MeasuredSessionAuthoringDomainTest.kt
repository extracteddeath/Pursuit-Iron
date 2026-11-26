package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MeasuredSessionAuthoringDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private fun profile():ReviewedAthleteSetup {
        val setup=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                mapOf(
                    "dumbbell-rack" to VerifiedLoadStock.Totals(LoadUnit.LB,
                        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"))),
                    "single-weight-plate" to VerifiedLoadStock.Totals(LoadUnit.LB,
                        LoadBasis.EXERCISE_OVERRIDE,listOf(n("10"),n("20")))),
                setOf("dumbbells","bands","pullup-bar",
                    "pinchable-plates","secure-plank-load-placement",
                    "verified-band-pushup-setup"),
                setOf("band-blue","band-red","rope-attachment")),
            allowBodyweight=true,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),
            lockedTrainingDays=emptySet(),hardTrainingDays=emptySet(),
            longTrainingDays=emptySet(),declaredRestDays=setOf(1,3,5,6)))
        assertEquals(setup.issues.toString(),
            AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup.status)
        return checkNotNull(setup.reviewed)
    }
    private fun selections():List<MeasuredSessionSelection> =listOf(
        MeasuredSessionSelection("plank",35,2),
        MeasuredSessionSelection("farmers",2000,1,
            targetExternalLoad=n("25"),matchedPairConfirmed=true),
        MeasuredSessionSelection("band-pushup",12,1,bandId="band-blue"),
        MeasuredSessionSelection("plate-pinch",40,1,
            singleSide=MeasuredSide.RIGHT,targetExternalLoad=n("20")),
        MeasuredSessionSelection("side-plank",30,1))

    @Test fun everySetCountShownByTheStepperComposesThatManySets() {
        val athlete=profile()
        assertEquals(1..8,MeasuredSessionAuthoringDomain.setsPerExercise)
        for(count in MeasuredSessionAuthoringDomain.setsPerExercise) {
            val plan=MeasuredSessionAuthoringDomain.compose("measured-count",3,athlete,
                listOf(MeasuredSessionSelection("plank",30,count)))
            assertEquals(count,plan?.sets?.size)
        }
    }

    @Test fun multiExerciseSingleSessionRetainsMeasurementTypesAndUniqueSetOrder() {
        val entries=selections()
        val plan=MeasuredSessionAuthoringDomain.compose(
            "measured-mixed-1",3,profile(),entries)
        assertNotNull(plan)
        val real=checkNotNull(plan)
        assertFalse(real.mayIssueWorkout)
        assertEquals((1..6).map{"set-$it"},real.sets.map{it.setKey})
        assertEquals(listOf("plank","plank","farmers","band-pushup",
            "plate-pinch","side-plank"),real.sets.map{it.exerciseId})
        assertEquals(listOf(35,35,2000,12,40,30),
            real.sets.map{it.targetAmount})
        assertEquals("dumbbell-rack",real.sets[2].physicalSetupId)
        assertEquals("band-blue",real.sets[3].bandId)
        assertEquals(setOf(MeasuredSide.RIGHT),real.sets[4].sides)
        assertEquals(setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT),
            real.sets[5].sides)
        for(entry in entries)
            assertEquals(entry,MeasuredSessionAuthoringDomain.restoreDraft(
                MeasuredSessionAuthoringDomain.saveDraft(entry)))
        val initial=MeasuredWorkoutJournalDomain.begin(real)
        assertNotNull(initial)
        assertFalse(checkNotNull(initial).mayPublishHistory)
    }

    @Test fun measuredSessionBuilderDoesNotPromoteZeroSelectorToIssuedWeight() {
        val reviewed=profile()
        val stockWithZero=reviewed.gym.confirmedStock.toMutableMap()
        stockWithZero["dumbbell-rack"]=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,listOf(n("0"),n("20"),n("25")))
        stockWithZero["single-weight-plate"]=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("0"),n("10"),n("20")))
        val athlete=reviewed.copy(gym=reviewed.gym.copy(
            confirmedStock=stockWithZero))
        for(id in listOf("farmers","suitcase-carry","plate-pinch",
                "weighted-plank")) {
            val side=if(id=="plate-pinch" || id=="suitcase-carry")
                MeasuredSide.RIGHT else null
            val selection=MeasuredSessionSelection(
                id,if(id.endsWith("carry") || id=="farmers")2000 else 30,1,
                singleSide=side,targetExternalLoad=n("0.000"),
                matchedPairConfirmed=id=="farmers")
            assertNull(id,MeasuredSessionAuthoringDomain.compose(
                "zero-source-$id",6,athlete,listOf(selection)))
            val positive=selection.copy(targetExternalLoad=n("20"))
            assertNotNull(id,MeasuredSessionAuthoringDomain.compose(
                "positive-source-$id",6,athlete,listOf(positive)))
        }
    }

    @Test fun measuredPickerRequiresARealPrescribableWorkingSetting() {
        val reviewed=profile()
        val loadedIds=setOf("farmers","suitcase-carry","plate-pinch","weighted-plank")
        fun choices(loads:List<BigDecimal>,unit:LoadUnit=LoadUnit.LB):Set<String> {
            val gym=reviewed.gym.copy(confirmedStock=mapOf(
                "dumbbell-rack" to VerifiedLoadStock.Totals(unit,
                    LoadBasis.DUMBBELL_PER_HAND,loads),
                "single-weight-plate" to VerifiedLoadStock.Totals(unit,
                    LoadBasis.EXERCISE_OVERRIDE,loads)))
            return MeasuredSessionAuthoringDomain.available(reviewed.copy(gym=gym))
                .map{it.exerciseId}.toSet()
        }
        for(loads in listOf(emptyList(),listOf(n("0")),listOf(n("0"),n("0.0"),n("20")),
            listOf(n("-5"),n("20")))) {
            val ids=choices(loads)
            assertTrue(loads.toString(),ids.intersect(loadedIds).isEmpty())
            assertTrue(ids.containsAll(setOf("plank","band-pushup")))
        }
        assertTrue(choices(listOf(n("20")),LoadUnit.KG).intersect(loadedIds).isEmpty())
        assertTrue(choices(listOf(n("20"),n("0"),n("10"))).containsAll(loadedIds))
        assertTrue(choices(listOf(n("20"))).containsAll(loadedIds))
    }

    @Test fun mixedWorkoutRecordsExactFactsAndCanColdReplayOneFinish() {
        val plan=checkNotNull(MeasuredSessionAuthoringDomain.compose(
            "measured-mixed-1",3,profile(),selections()))
        var journal=checkNotNull(MeasuredWorkoutJournalDomain.begin(plan))
        for((index,set) in plan.sets.withIndex()) {
            val actual=MeasuredExerciseActual(set.physicalSetupId,
                set.sides.sortedBy{it.ordinal}.map{side->
                    MeasuredSideResult(side,when(set.exerciseId) {
                        "farmers"->2400
                        "band-pushup"->14
                        else->37
                    })
                },
                actualExternalLoad=set.intendedExternalLoad,
                unit=set.unit,bandId=set.bandId,
                elapsedSeconds=if(set.exerciseId=="farmers")42 else null)
            val decision=MeasuredWorkoutJournalDomain.reduce(journal,
                MeasuredWorkoutOperation.Record("actual-${index+1}",
                    journal.revision,set.setKey,
                    MeasuredSetOutcome.COMPLETED,actual))
            assertTrue(decision.toString(),
                decision is MeasuredWorkoutDecision.Proposed)
            journal=(decision as MeasuredWorkoutDecision.Proposed).journal
        }
        assertTrue(journal.canProposeFinish)
        val ended=MeasuredWorkoutJournalDomain.reduce(journal,
            MeasuredWorkoutOperation.Finish("finish-mixed",journal.revision))
        assertTrue(ended is MeasuredWorkoutDecision.Proposed)
        val finished=(ended as MeasuredWorkoutDecision.Proposed).journal
        assertEquals("finish-mixed",finished.finishedByEventId)
        assertEquals(finished,MeasuredWorkoutJournalDomain.replay(
            plan,finished.receipts))
        assertEquals(6,finished.bySetKey.size)
    }

    @Test fun gymAndAthleteSelectionsCannotSmugglePhantomEquipmentOrMetrics() {
        val profile=profile()
        val real=selections()
        assertTrue(MeasuredSessionAuthoringDomain.available(profile)
            .map{it.exerciseId}.containsAll(listOf("plank","farmers",
                "band-pushup","plate-pinch","side-plank")))
        fun invalid(list:List<MeasuredSessionSelection>):Boolean =
            MeasuredSessionAuthoringDomain.compose(
                "measured-mixed-1",3,profile,list)==null
        assertTrue(invalid(real.map {
            if(it.exerciseId=="farmers")
                it.copy(targetExternalLoad=n("23")) else it
        }))
        assertTrue(invalid(real.map {
            if(it.exerciseId=="farmers")
                it.copy(matchedPairConfirmed=false) else it
        }))
        assertTrue(invalid(real.map {
            if(it.exerciseId=="plate-pinch")
                it.copy(singleSide=null) else it
        }))
        assertTrue(invalid(real.map {
            if(it.exerciseId=="band-pushup")
                it.copy(bandId="rope-attachment") else it
        }))
        assertTrue(invalid(real.map {
            if(it.exerciseId=="plank")
                it.copy(targetExternalLoad=n("10")) else it
        }))
        assertTrue(invalid(real.map {
            if(it.exerciseId=="side-plank")
                it.copy(singleSide=MeasuredSide.LEFT) else it
        }))
        assertTrue(invalid(real.map{
            if(it.exerciseId=="farmers")it.copy(sets=64) else it
        }))
        assertNull(MeasuredSessionAuthoringDomain.compose(
            "measured-mixed-1",3,profile,emptyList()))
        // 32 otherwise valid selections can still exceed the 64-set
        // journal bound. The composer must refuse, not silently truncate.
        assertNull(MeasuredSessionAuthoringDomain.compose(
            "measured-mixed-1",3,profile,
            List(32){MeasuredSessionSelection("plank",35,3)}))
        // A separate authoring cap forbids an unbounded number of
        // individually valid entries even when the set count is small.
        assertNull(MeasuredSessionAuthoringDomain.compose(
            "measured-mixed-1",3,profile,
            List(33){MeasuredSessionSelection("plank",35,1)}))
        val gymWithoutBands=profile.copy(gym=profile.gym.copy(
            availableAttachments=emptySet()))
        assertTrue(MeasuredSessionAuthoringDomain.available(gymWithoutBands)
            .none{it.exerciseId=="band-pushup"})
        assertNull(MeasuredSessionAuthoringDomain.compose(
            "measured-mixed-1",3,gymWithoutBands,real))
        val noStock=profile.copy(gym=profile.gym.copy(
            confirmedStock=emptyMap()))
        assertTrue(MeasuredSessionAuthoringDomain.available(noStock)
            .none{it.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND ||
                it.loadKind==MeasuredLoadKind.VERIFIED_ADDED_LOAD})
        assertNull(MeasuredSessionAuthoringDomain.compose(
            "measured-mixed-1",3,noStock,real))
    }

    @Test fun corruptSavedIntentIsNeverDecodedAsAValidEquipmentGrant() {
        val valid=MeasuredSessionAuthoringDomain.saveDraft(
            MeasuredSessionSelection("farmers",3000,2,
                targetExternalLoad=n("25"),matchedPairConfirmed=true))
        assertNotNull(MeasuredSessionAuthoringDomain.restoreDraft(valid))
        assertNull(MeasuredSessionAuthoringDomain.restoreDraft(valid+"|new"))
        assertNull(MeasuredSessionAuthoringDomain.restoreDraft(
            "farmers|oops|2||25||true"))
        assertNull(MeasuredSessionAuthoringDomain.restoreDraft(
            "farmers|3000|2|FAKE_SIDE|25||true"))
        assertNull(MeasuredSessionAuthoringDomain.restoreDraft(
            "farmers|3000|2||NaN||true"))
        assertNull(MeasuredSessionAuthoringDomain.restoreDraft(
            "farmers|3000|2||25|band-not-a-carry|yes"))
    }
}
