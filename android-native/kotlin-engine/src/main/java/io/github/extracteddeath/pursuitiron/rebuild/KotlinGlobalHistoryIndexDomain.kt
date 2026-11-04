package io.github.extracteddeath.pursuitiron.rebuild

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Collections

/**
 * A source-independent global *read projection*. Every manifest item has to
 * agree with the exact completed workout + correction receipt stream.
 * The native owner MUST authenticate and maintain the manifest, serialize
 * updates with correction/Finish commits, and supply the COMPLETE read.
 * This class cannot prove that a list came from disk or publish a PR.
 */
data class KotlinHistoryManifestEntry(
    val completionOrder: Long,
    val workoutId: String,
    val storedReceiptId: String,
    val finishEventId: String,
    val originalJournalRevision: Long,
    /** A deleted or missing correction file must NOT silently return zero. */
    val savedCorrectionRevision: Long
)

data class KotlinGlobalHistoryRead(
    val athleteId: String,
    val manifestRevision: Long,
    val currentManifestRevision: Long,
    val manifest: List<KotlinHistoryManifestEntry>,
    val completedWorkouts: List<KotlinChronologicalWorkout>
)

data class KotlinHistoryLastExercise(
    val exerciseId: String,
    val workoutId: String,
    val completionOrder: Long,
    /** Includes skipped and failed working sets; never backtracks past them. */
    val workingSets: List<KotlinHistoricalSetFact>
)

data class KotlinHistoryCompletedLoadRecord(
    val exerciseId: String,
    val unit: LoadUnit,
    val basis: LoadBasis,
    val actualLoad: BigDecimal,
    val actualReps: Int,
    val workoutId: String,
    val setKey: String,
    val completionOrder: Long,
    val effectiveEventId: String
)

data class KotlinHistoryLoadKey(
    val exerciseId: String,
    val unit: LoadUnit,
    val basis: LoadBasis
)

data class KotlinGlobalHistoryIndex(
    val athleteId: String,
    val manifestRevision: Long,
    /** Fingerprint of all verified effective facts AND audit receipt streams. */
    val contentFingerprint: String,
    val completedWorkoutIds: List<String>,
    val lastByExercise: Map<String, KotlinHistoryLastExercise>,
    val highestCompletedLoad: Map<KotlinHistoryLoadKey, KotlinHistoryCompletedLoadRecord>,
    /** Separate records per physical load; no best-set-only DP shortcut. */
    val mostRepsAtLoad: List<KotlinHistoryCompletedLoadRecord>
) {
    val canPublishPr: Boolean get() = false
    val canIssueProgression: Boolean get() = false
}

enum class KotlinGlobalHistoryRefusal {
    INVALID_READ,
    STALE_MANIFEST,
    MANIFEST_ORDER_MISMATCH,
    DUPLICATE_WORKOUT,
    CROSS_ATHLETE,
    MANIFEST_RECEIPT_MISMATCH,
    UNVERIFIED_WORKOUT_OR_CORRECTIONS
}

sealed interface KotlinGlobalHistoryResult {
    data class Candidate(val index: KotlinGlobalHistoryIndex) : KotlinGlobalHistoryResult
    data class Rejected(val reason: KotlinGlobalHistoryRefusal) : KotlinGlobalHistoryResult
}

object KotlinGlobalHistoryIndexDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_SESSIONS = 128
    private const val MAX_REVISION = 9_007_199_254_740_991L

    private fun canonical(value: BigDecimal): BigDecimal =
        BigDecimal(value.stripTrailingZeros().toPlainString())

    /** Only concrete successful weighted working sets qualify. */
    private fun physicalRecords(
        history: KotlinWorkoutHistoryEvidence,
        completionOrder: Long
    ): List<KotlinHistoryCompletedLoadRecord> =
        history.sets.mapNotNull { fact ->
            if (fact.type != DraftSetType.WORKING ||
                fact.outcome != KotlinJournalSetOutcome.COMPLETED)
                return@mapNotNull null
            val target = fact.issuedLoad as? DraftSetLoad.Verified
                ?: return@mapNotNull null
            val performed = fact.actual ?: return@mapNotNull null
            val load = performed.load ?: return@mapNotNull null
            if (performed.unit != target.unit || performed.reps <= 0 ||
                load <= BigDecimal.ZERO)
                return@mapNotNull null
            KotlinHistoryCompletedLoadRecord(
                fact.exerciseId, target.unit, target.basis, canonical(load),
                performed.reps, history.workoutId, fact.setKey, completionOrder,
                fact.effectiveEventId
            )
        }

    /**
     * This digest protects a transient projection against *accidental stale
     * cache reuse*. It is NOT a cryptographic signature or ownership proof.
     * Revision equality must be guaranteed by the native transactional owner.
     */
    private fun fingerprint(
        read: KotlinGlobalHistoryRead,
        histories: List<KotlinWorkoutHistoryEvidence>
    ): String {
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { out ->
            out.writeUTF(read.athleteId)
            out.writeLong(read.manifestRevision)
            for ((index, history) in histories.withIndex()) {
                val manifest = read.manifest[index]
                val journal = read.completedWorkouts[index].journal
                val edits = read.completedWorkouts[index].savedCorrections
                out.writeLong(manifest.completionOrder)
                out.writeUTF(manifest.workoutId)
                out.writeUTF(manifest.storedReceiptId)
                out.writeUTF(manifest.finishEventId)
                out.writeLong(manifest.originalJournalRevision)
                out.writeLong(manifest.savedCorrectionRevision)
                out.writeUTF(history.source.programId)
                out.writeLong(history.source.programRevision)
                out.writeUTF(history.source.gymId)
                out.writeLong(history.source.gymRevision)
                out.writeUTF(history.source.catalogRevision)
                out.writeUTF(history.source.coachingRulesRevision)
                out.writeUTF(journal.snapshot.purpose.name)
                out.writeInt(journal.snapshot.calendarDay)
                out.writeInt(journal.snapshot.dayIndex)
                out.writeInt(journal.receipts.size)
                for (receipt in journal.receipts) {
                    out.writeLong(receipt.appliedRevision)
                    out.writeUTF(receipt.operation.eventId)
                }
                out.writeInt(edits?.receipts?.size ?: 0)
                for (receipt in edits?.receipts.orEmpty()) {
                    out.writeLong(receipt.appliedCorrectionRevision)
                    out.writeUTF(receipt.correction.eventId)
                    out.writeUTF(receipt.correction.correctsEventId)
                }
                out.writeInt(history.sets.size)
                for (fact in history.sets) {
                    out.writeUTF(fact.exerciseId)
                    out.writeUTF(fact.setKey)
                    out.writeUTF(fact.type.name)
                    out.writeInt(fact.originalOrdinal)
                    when (val load = fact.issuedLoad) {
                        is DraftSetLoad.Verified -> {
                            out.writeBoolean(true)
                            out.writeUTF(load.unit.name)
                            out.writeUTF(load.basis.name)
                            out.writeUTF(load.value.toPlainString())
                        }
                        DraftSetLoad.ExplicitBodyweight -> out.writeBoolean(false)
                    }
                    out.writeInt(fact.issuedReps.minimum)
                    out.writeInt(fact.issuedReps.maximum)
                    val prescribedRir = fact.issuedRir
                    out.writeBoolean(prescribedRir != null)
                    if (prescribedRir != null) out.writeInt(prescribedRir)
                    out.writeUTF(fact.outcome.name)
                    out.writeUTF(fact.effectiveEventId)
                    val predecessor = fact.correctsEventId
                    out.writeBoolean(predecessor != null)
                    if (predecessor != null) out.writeUTF(predecessor)
                    val actual = fact.actual
                    out.writeBoolean(actual != null)
                    if (actual != null) {
                        out.writeBoolean(actual.load != null)
                        if (actual.load != null) out.writeUTF(actual.load.toPlainString())
                        out.writeBoolean(actual.unit != null)
                        if (actual.unit != null) out.writeUTF(actual.unit.name)
                        out.writeInt(actual.reps)
                        val rir = actual.actualRir
                        out.writeBoolean(rir != null)
                        if (rir != null) out.writeInt(rir)
                    }
                }
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray()).joinToString("") {
                "%02x".format(it.toInt() and 255)
            }
    }

    fun project(read: KotlinGlobalHistoryRead): KotlinGlobalHistoryResult {
        fun rejected(reason: KotlinGlobalHistoryRefusal) =
            KotlinGlobalHistoryResult.Rejected(reason)
        if (!id.matches(read.athleteId) ||
            read.manifestRevision !in 1..MAX_REVISION ||
            read.currentManifestRevision !in 1..MAX_REVISION ||
            read.manifest.size > MAX_SESSIONS ||
            read.manifest.size != read.completedWorkouts.size ||
            read.manifest.any {
                it.completionOrder !in 1..MAX_REVISION ||
                    it.originalJournalRevision < 1 ||
                    it.savedCorrectionRevision < 0 ||
                    !id.matches(it.workoutId) ||
                    !id.matches(it.storedReceiptId) ||
                    !id.matches(it.finishEventId)
            })
            return rejected(KotlinGlobalHistoryRefusal.INVALID_READ)

        if (read.manifestRevision != read.currentManifestRevision)
            return rejected(KotlinGlobalHistoryRefusal.STALE_MANIFEST)
        if (read.manifest.zipWithNext().any { (a, b) ->
                a.completionOrder >= b.completionOrder
            } || read.manifest.indices.any { index ->
                val expected = read.manifest[index]
                val actual = read.completedWorkouts[index]
                expected.completionOrder != actual.completionOrder
            })
            return rejected(KotlinGlobalHistoryRefusal.MANIFEST_ORDER_MISMATCH)

        if (read.manifest.map { it.workoutId }.distinct().size != read.manifest.size ||
            read.manifest.map { it.storedReceiptId }.distinct().size != read.manifest.size ||
            read.completedWorkouts.map { it.journal.snapshot.workoutId }.distinct().size !=
            read.completedWorkouts.size)
            return rejected(KotlinGlobalHistoryRefusal.DUPLICATE_WORKOUT)
        if (read.completedWorkouts.any {
                it.journal.snapshot.source.athleteId != read.athleteId
            })
            return rejected(KotlinGlobalHistoryRefusal.CROSS_ATHLETE)

        val histories = ArrayList<KotlinWorkoutHistoryEvidence>(read.manifest.size)
        for ((entry, session) in read.manifest.zip(read.completedWorkouts)) {
            val journal = session.journal
            val edits = session.savedCorrections
            if (entry.workoutId != journal.snapshot.workoutId ||
                entry.storedReceiptId != journal.storedIdentity.storageReceiptId ||
                entry.finishEventId != journal.finishedByEventId ||
                entry.originalJournalRevision != journal.revision ||
                entry.savedCorrectionRevision != (edits?.correctionRevision ?: 0L))
                return rejected(KotlinGlobalHistoryRefusal.MANIFEST_RECEIPT_MISMATCH)
            if (edits != null && edits.originalFinishedJournal != journal)
                return rejected(KotlinGlobalHistoryRefusal.UNVERIFIED_WORKOUT_OR_CORRECTIONS)
            val evidence = if (edits == null)
                KotlinWorkoutHistoryEvidenceDomain.project(journal)
            else KotlinWorkoutHistoryEvidenceDomain.projectSaved(edits)
            if (evidence !is KotlinHistoryProjectionResult.Candidate)
                return rejected(KotlinGlobalHistoryRefusal.UNVERIFIED_WORKOUT_OR_CORRECTIONS)
            histories.add(evidence.evidence)
        }

        val last = linkedMapOf<String, KotlinHistoryLastExercise>()
        val heaviest = linkedMapOf<KotlinHistoryLoadKey, KotlinHistoryCompletedLoadRecord>()
        val maxRepsAtLoad = linkedMapOf<Pair<KotlinHistoryLoadKey, BigDecimal>,
            KotlinHistoryCompletedLoadRecord>()
        for ((index, history) in histories.withIndex()) {
            val order = read.manifest[index].completionOrder
            for (group in history.exerciseReviews) {
                // A skipped/failed last session must appear as Last.
                last[group.exerciseId] = KotlinHistoryLastExercise(
                    group.exerciseId, history.workoutId, order,
                    Collections.unmodifiableList(group.workingSets.toList())
                )
            }
            for (record in physicalRecords(history, order)) {
                val key = KotlinHistoryLoadKey(
                    record.exerciseId, record.unit, record.basis)
                if (heaviest[key] == null ||
                    record.actualLoad > checkNotNull(heaviest[key]).actualLoad ||
                    (record.actualLoad.compareTo(checkNotNull(heaviest[key]).actualLoad) == 0 &&
                        record.actualReps >= checkNotNull(heaviest[key]).actualReps))
                    heaviest[key] = record
                val repsKey = key to record.actualLoad
                if (maxRepsAtLoad[repsKey] == null ||
                    record.actualReps >= checkNotNull(maxRepsAtLoad[repsKey]).actualReps)
                    maxRepsAtLoad[repsKey] = record
            }
        }

        return KotlinGlobalHistoryResult.Candidate(KotlinGlobalHistoryIndex(
            read.athleteId, read.manifestRevision,
            fingerprint(read, histories),
            Collections.unmodifiableList(read.manifest.map { it.workoutId }),
            Collections.unmodifiableMap(last),
            Collections.unmodifiableMap(heaviest),
            Collections.unmodifiableList(maxRepsAtLoad.values.toList())
        ))
    }
}
