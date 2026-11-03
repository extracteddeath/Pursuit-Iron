package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class WeeklyDoseFeasibilityDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val chest=TrainingMuscle.CHEST
    private val triceps=TrainingMuscle.TRICEPS
    private val biceps=TrainingMuscle.BICEPS
    private val brachialis=TrainingMuscle.BRACHIALIS
    private val flex=TrainingMuscle.WRIST_FLEXORS
    private val ext=TrainingMuscle.WRIST_EXTENSORS

    private fun band(m:TrainingMuscle,mev:String="0",mav:String=mev,
        mrv:String=mav,days:Int=0)=
        DeclaredMuscleDoseBounds(m,n(mev),n(mav),n(mrv),days)
    private fun bands(vararg selected:DeclaredMuscleDoseBounds)=
        TrainingMuscle.entries.map {m->selected.firstOrNull {it.muscle==m} ?:band(m)}
    private fun windows(days:Int=3,min:Int=40,max:Int?=60,hard:Int?=null,
        extra:Int=0)=List(days){DeclaredSessionWindow(min,max,hard,extra)}
    private fun c(m:TrainingMuscle,value:String="1",
        role:MuscleCreditRole=MuscleCreditRole.DIRECT)=
        DeclaredMuscleCredit(m,role,n(value))
    private fun row(id:String,day:Int,credits:List<DeclaredMuscleCredit>,
        kind:ExposureSetKind=ExposureSetKind.WORKING,
        active:Int=600,rest:Int=0)=
        PlannedWorkingExposure(id,day,kind,credits,active,rest)
    private fun week(sets:List<PlannedWorkingExposure>,
        thresholds:List<DeclaredMuscleDoseBounds>,days:Int=3,
        time:List<DeclaredSessionWindow> = windows(days))=
        DeclaredTrainingWeek(TrainingPhase.HYPERTROPHY,days,time,sets,thresholds)

    @Test fun everyMuscleHasExplicitTargetsAndFractionalIndirectCredit() {
        val sets=(0..3).map {i->
            row("press-$i",i/2,listOf(c(chest),c(triceps,"0.5",MuscleCreditRole.INDIRECT)))
        }
        val report=WeeklyDoseFeasibilityDomain.audit(week(sets,
            bands(band(chest,"4","5","8",2),band(triceps,"2","3","4"))))
        assertTrue(report.satisfiesDeclaredHardConstraints)
        assertEquals(n("4"),report.muscles[chest]?.directWorkingSets)
        assertEquals(n("2"),report.muscles[triceps]?.indirectWorkingSets)
        assertEquals(n("1"),report.muscles[chest]?.gapToMav)
        assertEquals(2,report.muscles[chest]?.directTrainingDays)
        assertEquals(DoseFinding.WITHIN_BOUNDS,report.muscles[chest]?.finding)
        assertEquals(TimeWindowFinding.BELOW_PREFERENCE,report.sessions[2].finding)
    }

    @Test fun customPlanBelowMevAndFrequencyMustSurfaceBothGaps() {
        val report=WeeklyDoseFeasibilityDomain.audit(week(
            listOf(row("single",0,listOf(c(chest)))),
            bands(band(chest,"4","6","8",2))))
        assertFalse(report.satisfiesDeclaredHardConstraints)
        assertEquals(DoseFinding.BELOW_MEV,report.muscles[chest]?.finding)
        assertEquals(n("3"),report.muscles[chest]?.gapToMev)
        assertEquals(n("5"),report.muscles[chest]?.gapToMav)
        assertEquals(1,report.muscles[chest]?.missingDirectDays)
    }

    @Test fun phaseChangesDoNotSilentlyReuseHypertrophyThresholds() {
        val sets=listOf(row("day0",0,listOf(c(chest))),
            row("day1",1,listOf(c(chest))))
        val peak=week(sets,bands(band(chest,"1","2","3",2)))
            .copy(phase=TrainingPhase.PEAK)
        val hypertrophy=week(sets,bands(band(chest,"4","6","8",2)))
        assertTrue(WeeklyDoseFeasibilityDomain.audit(peak).satisfiesDeclaredHardConstraints)
        assertFalse(WeeklyDoseFeasibilityDomain.audit(hypertrophy).satisfiesDeclaredHardConstraints)
        assertEquals(TrainingPhase.PEAK,WeeklyDoseFeasibilityDomain.audit(peak).phase)
    }

    @Test fun overMrvBlocksAndMyoExtensionsDoNotConcealMissingSets() {
        val heavy=(0..4).map {row("heavy-$it",it%3,listOf(c(chest)))}
        val mini=(0..9).map {row("myo-$it",it%3,listOf(c(biceps,"0.25")),
            ExposureSetKind.EXTENSION)}
        val report=WeeklyDoseFeasibilityDomain.audit(week(heavy+mini,
            bands(band(chest,"1","2","3",1),band(biceps,"2","3","4",1))))
        assertFalse(report.satisfiesDeclaredHardConstraints)
        assertEquals(DoseFinding.ABOVE_MRV,report.muscles[chest]?.finding)
        assertEquals(n("2"),report.muscles[chest]?.excessAboveMrv)
        assertEquals(n("2.5"),report.muscles[biceps]?.extensionDirect)
        assertEquals(n("0"),report.muscles[biceps]?.effectiveWorkingSets)
        assertEquals(DoseFinding.BELOW_MEV,report.muscles[biceps]?.finding)
    }

    @Test fun wristFlexionExtensionAndBicepsBrachialisRemainSeparate() {
        val sets=listOf(
            row("curl",0,listOf(c(biceps),c(brachialis,"0.5",MuscleCreditRole.INDIRECT))),
            row("wrist",1,listOf(c(flex),c(ext,"0.25",MuscleCreditRole.INDIRECT))))
        val report=WeeklyDoseFeasibilityDomain.audit(week(sets,
            bands(band(biceps,"1","1","2",1),
                band(brachialis,"0.5","1","2"),
                band(flex,"1","1","2",1),
                band(ext,"0.25","0.5","1"))))
        assertTrue(report.satisfiesDeclaredHardConstraints)
        assertEquals(n("1"),report.muscles[biceps]?.directWorkingSets)
        assertEquals(n("0.5"),report.muscles[brachialis]?.indirectWorkingSets)
        assertEquals(n("1"),report.muscles[flex]?.directWorkingSets)
        assertEquals(n("0.25"),report.muscles[ext]?.indirectWorkingSets)
    }

    @Test fun timeBucketUpperBoundIsPreferenceNotAutomaticHardCap() {
        val sets=listOf(row("long",0,listOf(c(chest)),active=35*60))
        val soft=week(sets,bands(band(chest,"1","1","2",1)),
            time=windows(extra=40*60))
        val report=WeeklyDoseFeasibilityDomain.audit(soft)
        assertTrue(report.satisfiesDeclaredHardConstraints)
        assertEquals(75L,report.sessions[0].roundedMinutes)
        assertEquals(TimeWindowFinding.ABOVE_PREFERENCE,report.sessions[0].finding)
        val hard=soft.copy(windows=windows(hard=60,extra=40*60))
        assertFalse(WeeklyDoseFeasibilityDomain.audit(hard).satisfiesDeclaredHardConstraints)
        assertEquals(TimeWindowFinding.ABOVE_USER_HARD_LIMIT,
            WeeklyDoseFeasibilityDomain.audit(hard).sessions[0].finding)
    }

    @Test fun openEndedTwoHourSessionsStayValidWithNoInventedCeiling() {
        val report=WeeklyDoseFeasibilityDomain.audit(week(emptyList(),bands(),
            time=windows(min=120,max=null,extra=130*60)))
        assertTrue(report.satisfiesDeclaredHardConstraints)
        assertEquals(130L,report.sessions[0].roundedMinutes)
        assertEquals(TimeWindowFinding.WITHIN_PREFERENCE,report.sessions[0].finding)
    }

    @Test fun warmupsHaveTimeButNeverEarnWorkingSetCredit() {
        val warm=row("warm",0,listOf(c(chest)),ExposureSetKind.WARMUP,active=120,rest=60)
        val report=WeeklyDoseFeasibilityDomain.audit(week(listOf(warm),
            bands(band(chest,"1","1","2",1))))
        assertEquals(n("0"),report.muscles[chest]?.effectiveWorkingSets)
        assertEquals(180L,report.sessions[0].declaredSeconds)
        assertFalse(report.satisfiesDeclaredHardConstraints)
    }

    @Test fun duplicateKeysMissingMusclesOrInvalidBoundsFailClosed() {
        val original=week(emptyList(),bands())
        val invalid=listOf(
            original.copy(bounds=original.bounds.drop(1)),
            original.copy(bounds=original.bounds.dropLast(1)+original.bounds.first()),
            original.copy(bounds=bands(band(chest,"8","6","10"))),
            original.copy(bounds=bands(band(chest,"1","4","3"))),
            original.copy(bounds=bands(band(chest,"1","2","3",4))),
            original.copy(days=2),original.copy(days=7),
            original.copy(windows=original.windows.drop(1)),
            original.copy(exposures=listOf(
                row("x",0,listOf(c(chest))),row("x",1,listOf(c(chest)))))
        )
        invalid.forEach {assertThrows(IllegalArgumentException::class.java) {
            WeeklyDoseFeasibilityDomain.audit(it)
        }}
    }

    @Test fun brokenSetCreditsOrSessionBoundsCannotBeAccepted() {
        val bad=listOf(
            row("x",0,listOf(c(chest,"0"))),
            row("x",0,listOf(c(chest,"1.1"))),
            row("x",0,listOf(c(chest,"0.1251"))),
            row("x",0,listOf(c(chest),c(chest))),
            row("x",0,emptyList()),
            row("x",3,listOf(c(chest))),
            row("x",0,listOf(c(chest)),active=3601)
        )
        bad.forEach {assertThrows(IllegalArgumentException::class.java) {
            WeeklyDoseFeasibilityDomain.audit(week(listOf(it),bands()))
        }}
        val wrong=windows().toMutableList()
        wrong[0]=wrong[0].copy(userHardLimitMinutes=0)
        assertThrows(IllegalArgumentException::class.java) {
            WeeklyDoseFeasibilityDomain.audit(week(emptyList(),bands(),time=wrong))
        }
    }

    @Test fun theReviewIsImmutableAndNeverEditsAnyAuthoredSets() {
        val authored=listOf(row("same",0,listOf(c(chest))))
        val result=WeeklyDoseFeasibilityDomain.audit(week(authored,
            bands(band(chest,"1","1","2",1))))
        assertEquals(1,authored.size)
        assertEquals("same",authored.single().setKey)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val writable=result.muscles as MutableMap<TrainingMuscle, WeeklyMuscleDoseFinding>
            writable.clear()
        }
    }

    @Test fun historicalThirtyToElevenSetCollapseIsFlaggedAgainstDeclaredPhaseDose() {
        // This independently checks the user's custom-cycle regression.
        // An 11-set strength week is NOT universally wrong: it is wrong
        // here because this explicitly authored phase requests >=8 sets
        // for three independently tracked muscle groups.
        val quads=TrainingMuscle.QUADS
        val lats=TrainingMuscle.LATS
        val limits=bands(
            band(chest,"8","10","12",2),
            band(quads,"8","10","12",2),
            band(lats,"8","10","12",2))
        val old=(0 until 30).map {i->
            val muscle=when {
                i<10 -> chest
                i<20 -> quads
                else -> lats
            }
            row("hypertrophy-$i",i%3,listOf(c(muscle)))
        }
        val replacement=(0 until 11).map {i->
            val muscle=when {
                i<4 -> chest
                i<8 -> quads
                else -> lats
            }
            row("strength-$i",i%3,listOf(c(muscle)))
        }
        val previous=WeeklyDoseFeasibilityDomain.audit(week(old,limits))
        val short=WeeklyDoseFeasibilityDomain.audit(week(replacement,limits)
            .copy(phase=TrainingPhase.STRENGTH))
        assertTrue(previous.satisfiesDeclaredHardConstraints)
        assertFalse(short.satisfiesDeclaredHardConstraints)
        assertTrue(short.preliminaryReviewNeeded)
        assertEquals(TrainingPhase.STRENGTH,short.phase)
        assertEquals(n("4"),short.muscles[chest]?.gapToMev)
        assertEquals(n("4"),short.muscles[quads]?.gapToMev)
        assertEquals(n("5"),short.muscles[lats]?.gapToMev)
    }

    @Test fun directFrequencyMustBeMetEvenIfIndirectSetsReachMev() {
        val report=WeeklyDoseFeasibilityDomain.audit(week(listOf(
            row("pull-0",0,listOf(c(biceps,"0.5",MuscleCreditRole.INDIRECT))),
            row("pull-1",1,listOf(c(biceps,"0.5",MuscleCreditRole.INDIRECT)))
        ),bands(band(biceps,"1","1","2",1))))
        assertEquals(DoseFinding.WITHIN_BOUNDS,report.muscles[biceps]?.finding)
        assertEquals(1,report.muscles[biceps]?.missingDirectDays)
        assertFalse(report.satisfiesDeclaredHardConstraints)
    }

    @Test fun extensionAndPreferredTimeWarningsCannotBeMistakenForQualityApproval() {
        val proposed=week(listOf(
            row("bench",0,listOf(c(chest))),
            row("drop-1",0,listOf(c(chest,"0.25")),ExposureSetKind.EXTENSION)
        ),bands(band(chest,"1","1","2",1)))
        val report=WeeklyDoseFeasibilityDomain.audit(proposed)
        assertTrue(report.satisfiesDeclaredHardConstraints)
        assertTrue(report.preliminaryReviewNeeded)
        assertTrue(report.requiresExtensionDosePolicy)
        assertEquals(setOf(0,1,2),report.daysOutsidePreferredWindow)
        // Meeting preferred duration alone does not certify amplifier
        // credit; extension policy remains separately unresolved.
        val filled=proposed.copy(windows=windows(min=1,max=60,extra=60))
        val filledReview=WeeklyDoseFeasibilityDomain.audit(filled)
        assertTrue(filledReview.daysOutsidePreferredWindow.isEmpty())
        assertTrue(filledReview.preliminaryReviewNeeded)
    }


}
