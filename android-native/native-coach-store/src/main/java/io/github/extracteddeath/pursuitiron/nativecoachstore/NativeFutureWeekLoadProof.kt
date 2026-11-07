package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Canonical, domain-separated SHA-256 of a future-week load proposal.
 * A successful full-cycle audit is NOT an approval. This digest is an
 * immutable screen/consent compare token, not an authorization capability.
 *
 * Do not hash only the proposed load or the UI label: equivalent numbers
 * may be presented against entirely different issued history, policy,
 * permissions, gym or activated block context.
 */
internal object NativeFutureWeekLoadProof {
    private fun normal(value:BigDecimal)=
        value.stripTrailingZeros().toPlainString()

    fun fingerprint(
        ownerKey:String,athleteId:String,cycleId:String,blockId:String,
        optionId:String,proposedBytes:ByteArray,
        activationFingerprint:String,
        historyRevision:Long,draftRevision:Long,sourceRevision:Long,
        settingsRevision:Long,permissionRevision:Long,
        absoluteWeek:Int,weekday:Int,slotId:String,
        previousLoad:BigDecimal,candidateLoad:BigDecimal,
        evidenceWorkoutIds:List<String>
    ):String {
        require(ownerKey.isNotBlank() && athleteId.isNotBlank() &&
            cycleId.isNotBlank() && blockId.isNotBlank() &&
            optionId.isNotBlank() && slotId.isNotBlank())
        require(Regex("^[a-f0-9]{64}$").matches(activationFingerprint))
        require(proposedBytes.isNotEmpty())
        require(historyRevision>=0 && draftRevision>0 && sourceRevision>0 &&
            settingsRevision>=0 && permissionRevision>0)
        require(absoluteWeek in 1..104 && weekday in 0..6)
        require(previousLoad>=BigDecimal.ZERO && candidateLoad>previousLoad)
        require(evidenceWorkoutIds.size==2 &&
            evidenceWorkoutIds.distinct().size==2 &&
            evidenceWorkoutIds.all{it.isNotBlank()})
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {out->
            out.writeUTF("pursuit-native-future-week-load-review-v1")
            listOf(ownerKey,athleteId,cycleId,blockId,optionId,
                activationFingerprint,slotId).forEach(out::writeUTF)
            out.writeLong(historyRevision)
            out.writeLong(draftRevision)
            out.writeLong(sourceRevision)
            out.writeLong(settingsRevision)
            out.writeLong(permissionRevision)
            out.writeInt(absoluteWeek)
            out.writeInt(weekday)
            out.writeUTF(normal(previousLoad))
            out.writeUTF(normal(candidateLoad))
            out.writeInt(evidenceWorkoutIds.size)
            evidenceWorkoutIds.forEach(out::writeUTF)
            out.writeInt(proposedBytes.size)
            out.write(proposedBytes)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes.toByteArray())
            .joinToString(""){"%02x".format(it.toInt() and 255)}
    }
}
