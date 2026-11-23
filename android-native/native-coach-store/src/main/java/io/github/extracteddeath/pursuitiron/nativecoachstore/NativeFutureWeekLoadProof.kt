package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import io.github.extracteddeath.pursuitiron.rebuild.*

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
        readiness:CoachReadiness,movementRestricted:Boolean,
        maximumAuthorizedIncrease:BigDecimal,
        evidenceWorkoutIds:List<String>,
        /** Same accepted physical number fact returned by the source owner.
         * It is evidence for consent comparison, never write authority. */
        numberFact:KotlinFutureLoadFact,
        /** Exact selected machine variant. Null is a distinct no-tag scope. */
        machineInstanceId:String?
    ):String {
        require(ownerKey.isNotBlank() && athleteId.isNotBlank() &&
            cycleId.isNotBlank() && blockId.isNotBlank() &&
            optionId.isNotBlank() && slotId.isNotBlank())
        require(Regex("^[a-f0-9]{64}$").matches(activationFingerprint))
        require(proposedBytes.isNotEmpty())
        require(historyRevision>=0 && draftRevision>0 && sourceRevision>0 &&
            settingsRevision>=0 && permissionRevision>0)
        require(absoluteWeek in 1..104 && weekday in 0..6)
        require(previousLoad>=BigDecimal.ZERO && candidateLoad>previousLoad &&
            maximumAuthorizedIncrease>BigDecimal.ZERO &&
            candidateLoad.subtract(previousLoad)<=maximumAuthorizedIncrease)
        require(evidenceWorkoutIds.size==2 &&
            evidenceWorkoutIds.distinct().size==2 &&
            evidenceWorkoutIds.all{it.isNotBlank()})
        require(machineInstanceId==null ||
            KotlinMachineAlternativesDomain.validInstanceId(machineInstanceId))
        val physical=numberFact.physicalProof
        require(numberFact.source==KotlinFutureLoadSource.EARNED_NEXT_INCREMENT &&
            numberFact.basis!=LoadBasis.ASSISTANCE_COUNTERWEIGHT &&
            physical.reason==
                KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_HIGHER_SETTING &&
            numberFact.issuedLoad.compareTo(previousLoad)==0 &&
            numberFact.proposedLoad.compareTo(candidateLoad)==0 &&
            numberFact.authorizedIncrease.compareTo(maximumAuthorizedIncrease)==0 &&
            physical.verifiedIssued.compareTo(numberFact.issuedLoad)==0 &&
            physical.verifiedCandidate.compareTo(numberFact.proposedLoad)==0 &&
            physical.nextHigher?.compareTo(candidateLoad)==0 &&
            physical.absoluteChange.compareTo(
                candidateLoad.subtract(previousLoad))==0 &&
            physical.requiresIndependentPerformanceEvidence &&
            !physical.requiresPreviouslyDemonstratedLoad &&
            !physical.mayIssueWorkout && !physical.maySaveProgram &&
            !numberFact.mayChangeIssuedWorkout &&
            !numberFact.mayModifyHistory &&
            !numberFact.maySaveFutureProgram)
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {out->
            out.writeUTF("pursuit-native-future-week-load-review-v2")
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
            out.writeUTF(readiness.name)
            out.writeBoolean(movementRestricted)
            out.writeUTF(normal(maximumAuthorizedIncrease))
            out.writeInt(evidenceWorkoutIds.size)
            evidenceWorkoutIds.forEach(out::writeUTF)
            // Persist neither editable UI labels nor a stock guess. Bind the
            // current owner's exact physical scope and canonical accepted
            // proof to the opaque consent token. An A/B machine change or
            // neighbor inventory change must invalidate the old consent
            // even when the displayed 200 -> 205 stays unchanged.
            out.writeBoolean(machineInstanceId!=null)
            if(machineInstanceId!=null)out.writeUTF(machineInstanceId)
            out.writeUTF(numberFact.unit.name)
            out.writeUTF(numberFact.basis.name)
            out.writeUTF(numberFact.source.name)
            out.writeUTF(physical.reason.name)
            out.writeUTF(normal(numberFact.issuedLoad))
            out.writeUTF(normal(numberFact.proposedLoad))
            out.writeUTF(normal(numberFact.authorizedIncrease))
            out.writeUTF(normal(physical.verifiedIssued))
            out.writeUTF(normal(physical.verifiedCandidate))
            out.writeUTF(normal(physical.absoluteChange))
            out.writeBoolean(physical.nextHigher!=null)
            physical.nextHigher?.let{out.writeUTF(normal(it))}
            out.writeBoolean(physical.nextLower!=null)
            physical.nextLower?.let{out.writeUTF(normal(it))}
            out.writeInt(proposedBytes.size)
            out.write(proposedBytes)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes.toByteArray())
            .joinToString(""){"%02x".format(it.toInt() and 255)}
    }
}
