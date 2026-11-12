package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * The public CSV is *only* corrected factual History, never a future-workout
 * prescription, PR computation, automatic backup or cross-athlete projection.
 */
class NativeKotlinVerifiedHistoryCsvExportTest {
    private fun n(value:String)=BigDecimal(value)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",1,"program-1",1,"gym-1",1,"catalog-1","rules-1")
    private fun manifest(i:Int)=KotlinHistoryManifestEntry(
        i.toLong(),"workout-$i","stored-$i","finish-$i",3,0)
    private val heavy=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.SMITH_TOTAL,n("215"))
    private val dumbbell=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("40"))

    private fun generated()=NativeKotlinMixedHistoryCompletion.Composer(
        manifest(1),KotlinWorkoutHistoryEvidence(
            source,"workout-1","finish-1",3,
            listOf(
                KotlinHistoricalSetFact(
                    "machine_press","set-1",DraftSetType.WORKING,0,
                    heavy,RepBand(5,8),2,KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(n("210"),LoadUnit.LB,2,null,"A"),
                    "corrected-1","first-record-1"),
                KotlinHistoricalSetFact(
                    "machine_press","set-2",DraftSetType.WORKING,1,
                    heavy,RepBand(5,8),2,KotlinJournalSetOutcome.SKIPPED,
                    null,"skip-2",null)
            ),emptyList()))

    private fun custom(
        athleteId:String="athlete-1",
        actual:KotlinPerformedSet?=KotlinPerformedSet(
            n("30"),LoadUnit.LB,8,2,"B")
    )=NativeKotlinMixedHistoryCompletion.Custom(
        manifest(2),CustomWorkoutHistoricalEvidence(
            athleteId,"workout-2","cycle-1",1,"finish-2",3,
            listOf(
                CustomHistoricalVisitFact(
                    "slot-a","incline_press","custom-1",
                    CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                    dumbbell,RepBand(8,12),2,null,
                    KotlinJournalSetOutcome.COMPLETED,actual,"custom-saved-1"),
                CustomHistoricalVisitFact(
                    "slot-a","incline_press","custom-2",
                    CustomWorkoutVisitPart.FOLLOW_UP,DraftSetType.WORKING,
                    dumbbell,RepBand(8,12),null,CustomAmplifierKind.DROP_SET,
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(n("15"),LoadUnit.LB,12,null,"B"),
                    "custom-follow-2"),
                CustomHistoricalVisitFact(
                    "slot-b","bodyweight_crunch","custom-3",
                    CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                    DraftSetLoad.ExplicitBodyweight,RepBand(10,15),null,null,
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(null,null,12,null),
                    "custom-bodyweight-3")
            )))

    private fun timeline(
        vararg entries:NativeKotlinMixedHistoryCompletion
    )=NativeKotlinMixedHistoryTimeline("athlete-1",5,entries.toList())

    @Test fun exportsBothJournalKindsWithUnchangedTargetsAndCorrectedActuals() {
        val result=NativeKotlinVerifiedHistoryCsvExport.prepare(
            timeline(generated(),custom()))
        assertTrue(result.toString(),result is NativeKotlinHistoryCsvResult.Ready)
        val ready=result as NativeKotlinHistoryCsvResult.Ready
        assertEquals(2,ready.sessionCount)
        assertEquals(5,ready.rowCount)
        val rows=ready.csv.trimEnd().lines()
        assertEquals(6,rows.size)
        assertTrue(rows.first().contains("\"target_load\""))
        assertTrue(rows.first().contains("\"actual_rir\""))
        assertTrue(rows[1].contains("\"GENERATED\""))
        assertTrue(rows[1].contains("\"215\",\"LB\",\"SMITH_TOTAL\""))
        assertTrue(rows[1].contains("\"210\",\"LB\",\"2\",\"\",\"A\""))
        assertTrue(rows[2].contains("\"SKIPPED\""))
        assertTrue(rows[2].contains("\"215\",\"LB\",\"SMITH_TOTAL\""))
        assertTrue(rows[2].contains("\"\",\"\",\"\",\"\",\"\""))
        assertTrue(rows[3].contains("\"CUSTOM\""))
        assertTrue(rows[3].contains("\"40\",\"LB\",\"DUMBBELL_PER_HAND\""))
        assertTrue(rows[3].contains("\"30\",\"LB\",\"8\",\"2\",\"B\""))
        assertTrue(rows[4].contains("\"FOLLOW_UP\""))
        assertTrue(rows[4].contains("\"DROP_SET\""))
        assertTrue(rows[5].contains("\"BODYWEIGHT\""))
        assertTrue(rows[5].contains("\"\",\"\",\"12\",\"\",\"\""))
        // No invented date, inferred RIR, automatic progression, or notes.
        assertFalse(rows[0].contains("date"))
        assertFalse(ready.csv.contains("session-note"))
    }

    @Test fun rejectsCrossAthleteDuplicateAndUnorderedManifestInsteadOfExporting() {
        assertEquals(NativeKotlinHistoryCsvResult.InvalidHistory,
            NativeKotlinVerifiedHistoryCsvExport.prepare(
                timeline(generated(),custom(athleteId="other-athlete"))))
        assertEquals(NativeKotlinHistoryCsvResult.InvalidHistory,
            NativeKotlinVerifiedHistoryCsvExport.prepare(
                timeline(generated(),generated())))
        assertEquals(NativeKotlinHistoryCsvResult.InvalidHistory,
            NativeKotlinVerifiedHistoryCsvExport.prepare(
                timeline(custom(),generated())))
        val unfinished=timetableWithForeignFinish()
        assertEquals(NativeKotlinHistoryCsvResult.InvalidHistory,
            NativeKotlinVerifiedHistoryCsvExport.prepare(unfinished))
    }

    private fun timetableWithForeignFinish()=timeline(
        generated().copy(evidence=generated().evidence.copy(
            finishEventId="different-finish")))

    @Test fun blocksSpreadsheetFormulasInUntrustedExerciseIds() {
        val injected=generated().copy(evidence=generated().evidence.copy(
            sets=generated().evidence.sets.take(1).map{
                it.copy(exerciseId="=HYPERLINK(\"bad\")")
            }))
        val ready=NativeKotlinVerifiedHistoryCsvExport.prepare(
            timeline(injected)) as NativeKotlinHistoryCsvResult.Ready
        assertTrue(ready.csv.contains("\"'=HYPERLINK(\"\"bad\"\")\""))
        assertFalse(ready.csv.contains(",\"=HYPERLINK"))
    }

    @Test fun rejectsSkippedSetWithInventedActual() {
        val original=generated()
        val forged=original.copy(evidence=original.evidence.copy(
            sets=original.evidence.sets.map {
                if(it.outcome==KotlinJournalSetOutcome.SKIPPED)
                    it.copy(actual=KotlinPerformedSet(n("215"),LoadUnit.LB,0,null))
                else it
            }))
        assertEquals(NativeKotlinHistoryCsvResult.InvalidHistory,
            NativeKotlinVerifiedHistoryCsvExport.prepare(timeline(forged)))
    }
}
