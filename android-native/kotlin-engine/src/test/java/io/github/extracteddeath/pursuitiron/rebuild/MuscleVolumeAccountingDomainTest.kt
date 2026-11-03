package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MuscleVolumeAccountingDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private fun c(m:TrainingMuscle,v:String,role:MuscleCreditRole=MuscleCreditRole.DIRECT)=
        DeclaredMuscleCredit(m,role,n(v))
    private val biceps=c(TrainingMuscle.BICEPS,"1")
    private val brachialis=c(TrainingMuscle.BRACHIALIS,"0.5",MuscleCreditRole.INDIRECT)
    private val wristFlex=c(TrainingMuscle.WRIST_FLEXORS,"1")
    private val wristExtend=c(TrainingMuscle.WRIST_EXTENSORS,"0.5",
        MuscleCreditRole.INDIRECT)
    private fun row(id:String,kind:ExposureSetKind=ExposureSetKind.WORKING,
        outcome:SetOutcome=SetOutcome.COMPLETED,
        credits:List<DeclaredMuscleCredit>=listOf(biceps,brachialis))=
        ObservedMuscleSet(id,kind,outcome,credits)

    @Test fun bicepsBrachialisAndWristFunctionsNeverMerge() {
        val summary=MuscleVolumeAccountingDomain.summarize(listOf(
            row("bench:0:0"),row("bench:0:1"),row("bench:0:2"),
            row("wrist:0:0",credits=listOf(wristFlex,wristExtend))
        ))
        val b=checkNotNull(summary.totals[TrainingMuscle.BICEPS])
        val br=checkNotNull(summary.totals[TrainingMuscle.BRACHIALIS])
        val flex=checkNotNull(summary.totals[TrainingMuscle.WRIST_FLEXORS])
        val ext=checkNotNull(summary.totals[TrainingMuscle.WRIST_EXTENSORS])
        assertEquals(n("3"),b.workingDirect)
        assertEquals(n("0"),b.workingIndirect)
        assertEquals(n("0"),br.workingDirect)
        assertEquals(n("1.5"),br.workingIndirect)
        assertEquals(n("1"),flex.workingDirect)
        assertEquals(n("0.5"),ext.workingIndirect)
        assertEquals(n("0"),ext.workingDirect)
        assertEquals(4,summary.completedWorking)
    }

    @Test fun skippedFailedWarmupAndExtensionsNeverForgeWorkingSetVolume() {
        val summary=MuscleVolumeAccountingDomain.summarize(listOf(
            row("set-1"),row("set-2",outcome=SetOutcome.SKIPPED),
            row("set-3",outcome=SetOutcome.FAILED_ATTEMPT),
            row("warmup-1",kind=ExposureSetKind.WARMUP),
            row("myo-1",kind=ExposureSetKind.EXTENSION,
                credits=listOf(c(TrainingMuscle.BICEPS,"0.25")))
        ))
        val b=checkNotNull(summary.totals[TrainingMuscle.BICEPS])
        assertEquals(n("1"),b.workingDirect)
        assertEquals(n("0.25"),b.extensionDirect)
        assertEquals(n("1"),b.workingEquivalent)
        assertEquals(1,summary.completedWorking)
        assertEquals(1,summary.skippedWorking)
        assertEquals(1,summary.failedWorking)
        assertEquals(1,summary.completedWarmups)
        assertEquals(1,summary.completedExtensions)
    }

    @Test fun repeatedExerciseOccurrencesCountOnlyTheirDistinctCompletedSets() {
        val summary=MuscleVolumeAccountingDomain.summarize(listOf(
            row("slot-a:0"),row("slot-a:1"),row("slot-b:0"),
            row("slot-b:1",outcome=SetOutcome.SKIPPED)
        ))
        assertEquals(n("3"),summary.totals[TrainingMuscle.BICEPS]?.workingDirect)
        assertEquals(3,summary.completedWorking)
    }

    @Test fun coefficientsUseExactDecimalSumsWithPlainStableRepresentation() {
        val fractional=c(TrainingMuscle.BICEPS,"0.125")
        val summary=MuscleVolumeAccountingDomain.summarize(
            (0..7).map {row("credit-$it",credits=listOf(fractional))})
        assertEquals(n("1"),summary.totals[TrainingMuscle.BICEPS]?.workingDirect)
        assertEquals("1",summary.totals[TrainingMuscle.BICEPS]?.workingDirect?.toPlainString())
        assertEquals(TrainingMuscle.entries.size,summary.totals.size)
        assertTrue(summary.totals.values.none {it.workingDirect< n("0")})
    }

    @Test fun malformedDuplicateOrUnknownAllocationFailsClosed() {
        val bad=listOf(
            listOf(row("same"),row("same")),
            listOf(row("bad key!")),
            listOf(row("missing",credits=emptyList())),
            listOf(row("duplicate",credits=listOf(biceps,biceps))),
            listOf(row("both-roles",credits=listOf(biceps,
                c(TrainingMuscle.BICEPS,"0.5",MuscleCreditRole.INDIRECT)))),
            listOf(row("over-credit",credits=listOf(c(TrainingMuscle.BICEPS,"1.1")))),
            listOf(row("negative",credits=listOf(c(TrainingMuscle.BICEPS,"-0.5")))),
            listOf(row("zero",credits=listOf(c(TrainingMuscle.BICEPS,"0")))),
            listOf(row("too-precise",credits=listOf(c(TrainingMuscle.BICEPS,"0.1251"))))
        )
        for(rows in bad)assertThrows(IllegalArgumentException::class.java) {
            MuscleVolumeAccountingDomain.summarize(rows)
        }
    }

    @Test fun emptyHistoryIsActuallyZeroWithoutInventedProgress() {
        val summary=MuscleVolumeAccountingDomain.summarize(emptyList())
        assertEquals(0,summary.completedWorking)
        assertTrue(summary.totals.values.all {
            it.workingDirect==n("0") && it.workingIndirect==n("0") &&
                it.extensionDirect==n("0") && it.extensionIndirect==n("0")
        })
    }

    @Test fun resultCannotBeMutatedToRewriteVolume() {
        val summary=MuscleVolumeAccountingDomain.summarize(listOf(row("finished:0")))
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (summary.totals as MutableMap<TrainingMuscle,MuscleVolumeTotals>)
                [TrainingMuscle.BICEPS]=MuscleVolumeTotals(n("0"),n("0"),n("0"),n("0"))
        }
    }
}
