package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

enum class NativeOwnerTieredLedgerRefusal {
    INVALID_SCOPE, INVALID_HISTORY, TARGET_ALREADY_TRAINED, NONCHRONOLOGICAL_HISTORY,
    INCOMPATIBLE_EXPOSURE, STORAGE_UNAVAILABLE
}

sealed interface NativeOwnerTieredLedgerResult {
    data class Rejected(val reason: NativeOwnerTieredLedgerRefusal,
        val workoutId: String? = null,
        val exposureReason: CustomTieredJournalRefusal? = null): NativeOwnerTieredLedgerResult
    data class HistoryRejected(val reason: NativeKotlinMixedHistoryRefusal): NativeOwnerTieredLedgerResult
    data class Review(val manifestRevision: Long, val exposures: List<TieredLinearExposure>):
        NativeOwnerTieredLedgerResult {
        val mayIssueWorkout: Boolean get() = false
        val maySaveProgram: Boolean get() = false
    }
}

internal data class NativeTieredHistorySession(
    val entry: KotlinHistoryManifestEntry,
    val original: CustomWorkoutDaySnapshot,
    val effective: CustomWorkoutDaySnapshot,
    val evidence: CustomWorkoutHistoricalEvidence
)

/** Called only from the verified mixed-history scan while its owner fence is held. */
internal object NativeTieredHistoryLedgerDomain {
    fun project(
        timeline: NativeKotlinMixedHistoryTimeline,
        sessions: List<NativeTieredHistorySession>,
        scope: BasicProgressionScope,
        slotId: String,
        tier: PercentageTier,
        nextAbsoluteWeek: Int
    ): NativeOwnerTieredLedgerResult {
        fun deny(reason: NativeOwnerTieredLedgerRefusal, workoutId: String? = null,
            detail: CustomTieredJournalRefusal? = null) =
            NativeOwnerTieredLedgerResult.Rejected(reason, workoutId, detail)
        if (!scope.valid() || timeline.athleteId != scope.athleteId ||
            nextAbsoluteWeek !in 1..104 || slotId.isBlank() ||
            (0..6).none { scope.trainingDayId == scope.blockId + ":d" + it })
            return deny(NativeOwnerTieredLedgerRefusal.INVALID_SCOPE)
        val expected = timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
        if (timeline.manifestRevision < 1 ||
            timeline.completions.map { it.manifest.completionOrder } !=
                timeline.completions.indices.map { it.toLong() + 1 } ||
            timeline.completions.map { it.manifest.workoutId }.distinct().size != timeline.completions.size ||
            expected.size != sessions.size ||
            sessions.zip(expected).any { (session, completed) ->
                session.entry != completed.manifest || session.evidence != completed.evidence ||
                    session.original.stamp.athleteId != timeline.athleteId ||
                    session.original.stamp.gymId != completed.gymId
            })
            return deny(NativeOwnerTieredLedgerRefusal.INVALID_HISTORY)
        val exposures = mutableListOf<TieredLinearExposure>()
        var lastWeek = 0
        for (session in sessions) {
            val snap = session.original
            val entry = session.entry
            if (snap.stamp.cycleId != scope.programId || snap.blockId != scope.blockId ||
                snap.blockId + ":d" + snap.weekday != scope.trainingDayId) continue
            // Calendar ownership is independent of gym or equipment choice.
            if (snap.absoluteWeek >= nextAbsoluteWeek)
                return deny(NativeOwnerTieredLedgerRefusal.TARGET_ALREADY_TRAINED, entry.workoutId)
            if (snap.absoluteWeek <= lastWeek)
                return deny(NativeOwnerTieredLedgerRefusal.NONCHRONOLOGICAL_HISTORY, entry.workoutId)
            lastWeek = snap.absoluteWeek
            if (snap.stamp.gymId != scope.gymId) continue
            // A different station is independent only when every issued
            // working receipt proves that physical station was used.
            if (CustomTieredJournalEvidenceDomain.isVerifiedOtherStation(
                    snap, session.effective, session.evidence, entry.completionOrder,
                    scope, slotId)) continue
            // Missing, swapped or ambiguously attributed visits still block.
            when (val projected = CustomTieredJournalEvidenceDomain.projectVerifiedFacts(
                snap, session.effective, entry.workoutId, session.evidence,
                entry.completionOrder, scope, slotId, tier)) {
                is CustomTieredJournalResult.Refused ->
                    return deny(NativeOwnerTieredLedgerRefusal.INCOMPATIBLE_EXPOSURE,
                        entry.workoutId, projected.reason)
                is CustomTieredJournalResult.Candidate -> exposures += projected.exposure
            }
        }
        return NativeOwnerTieredLedgerResult.Review(timeline.manifestRevision,
            Collections.unmodifiableList(exposures.toList()))
    }
}
