package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Synthetic owned inputs; native storage separately authenticates them. */
class NativeKotlinFinishedComposerHandoffTest {
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
    private val load=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("30"))
    private fun finish():KotlinWorkoutJournalState {
        val target=KotlinWorkoutTarget("press",ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY,"set-1",
            DraftSetType.WORKING,0,load,RepBand(8,12),2,60,120)
        val snapshot=KotlinStagedWorkout(source,"grant-1","workout-1",
            0,0,ComposerSessionPurpose.HYPERTROPHY,45,listOf(target))
        var draft=KotlinWorkoutJournalDomain.begin(snapshot,
            KotlinStoredWorkoutIdentity("stored-1","workout-1",source,1))
        draft=(KotlinWorkoutJournalDomain.reduce(draft,
            KotlinJournalOperation.RecordSet("event-1",draft.revision,
                "set-1",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,10,null)))
            as KotlinJournalDecision.Proposed).state
        return (KotlinWorkoutJournalDomain.reduce(draft,
            KotlinJournalOperation.Finish("finish-1",draft.revision))
            as KotlinJournalDecision.Proposed).state
    }
    private fun timeline(
        journal:KotlinWorkoutJournalState,
        revision:Long=2,
        sourceRevision:Long=8
    ):NativeKotlinMixedHistoryReadResult.Review {
        val facts=(KotlinWorkoutHistoryEvidenceDomain.project(journal)
            as KotlinHistoryProjectionResult.Candidate).evidence
        val original=KotlinHistoryManifestEntry(
            1,"workout-1","stored-1","finish-1",journal.revision,0)
        val selected=NativeKotlinMixedHistoryCompletion.Composer(
            original,facts,ComposerSessionPurpose.HYPERTROPHY,0)
        return NativeKotlinMixedHistoryReadResult.Review(
            NativeKotlinMixedHistoryTimeline(
                "athlete-1",revision,listOf(selected.copy(
                    evidence=facts.copy(source=facts.source.copy(
                        programRevision=sourceRevision))))))
    }
    @Test fun onlyExactCommittedGeneratedHistoryOpensHandoff() {
        val j=finish()
        val result=NativeKotlinFinishedComposerHandoff.inspect(j,timeline(j))
        assertEquals(NativeComposerFinishHandoffResult.Verified(
            "workout-1",2,1),result)
        assertFalse((result as NativeComposerFinishHandoffResult.Verified)
            .canIssueNextWorkout)
        assertNull(j.effectiveSets.getValue("set-1")
            .operation.let{it as KotlinJournalOperation.RecordSet}.performed?.actualRir)
    }
    @Test fun cannotAdvanceFromUnfinishedOrUnavailableHistory() {
        val j=finish()
        assertEquals(NativeComposerFinishHandoffResult.Rejected(
            NativeComposerFinishHandoffRefusal.NOT_FINISHED),
            NativeKotlinFinishedComposerHandoff.inspect(
                j.copy(finishedByEventId=null),timeline(j)))
        assertEquals(NativeComposerFinishHandoffResult.Rejected(
            NativeComposerFinishHandoffRefusal.VERIFIED_HISTORY_UNAVAILABLE),
            NativeKotlinFinishedComposerHandoff.inspect(j,
                NativeKotlinMixedHistoryReadResult.Rejected(
                    NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION)))
        val empty=NativeKotlinMixedHistoryReadResult.Review(
            NativeKotlinMixedHistoryTimeline("athlete-1",2,emptyList()))
        assertEquals(NativeComposerFinishHandoffResult.Rejected(
            NativeComposerFinishHandoffRefusal.COMPLETION_NOT_IN_HISTORY),
            NativeKotlinFinishedComposerHandoff.inspect(j,empty))
    }
    @Test fun mismatchedIdentityFinishReceiptAndSourceNeverAuthorizeHandoff() {
        val j=finish()
        val ready=timeline(j)
        val original=ready.timeline.completions.single()
            as NativeKotlinMixedHistoryCompletion.Composer
        fun changed(entry:NativeKotlinMixedHistoryCompletion)=
            NativeKotlinMixedHistoryReadResult.Review(
                ready.timeline.copy(completions=listOf(entry)))
        val mismatches=listOf(
            changed(original.copy(manifest=original.manifest.copy(
                finishEventId="wrong-finish"))),
            changed(original.copy(manifest=original.manifest.copy(
                storedReceiptId="other-storage"))),
            changed(original.copy(manifest=original.manifest.copy(
                originalJournalRevision=j.revision+1))),
            changed(original.copy(dayIndex=2)),
            changed(original.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)),
            changed(original.copy(evidence=original.evidence.copy(
                source=source.copy(programRevision=9)))),
            ready.copy(timeline=ready.timeline.copy(athleteId="another-athlete"))
        )
        mismatches.forEach{case->
            assertEquals(NativeComposerFinishHandoffResult.Rejected(
                NativeComposerFinishHandoffRefusal.FINISH_RECEIPTS_MISMATCH),
                NativeKotlinFinishedComposerHandoff.inspect(j,case))
        }
    }
    @Test fun laterSavedCorrectionsDoNotEraseOriginalFinishProvenance() {
        val j=finish()
        val ready=timeline(j)
        val completion=ready.timeline.completions.single()
            as NativeKotlinMixedHistoryCompletion.Composer
        val withCorrection=ready.copy(timeline=ready.timeline.copy(
            manifestRevision=3,completions=listOf(completion.copy(
                manifest=completion.manifest.copy(savedCorrectionRevision=1)))))
        assertEquals(NativeComposerFinishHandoffResult.Verified(
            "workout-1",3,1),
            NativeKotlinFinishedComposerHandoff.inspect(j,withCorrection))
    }
}
