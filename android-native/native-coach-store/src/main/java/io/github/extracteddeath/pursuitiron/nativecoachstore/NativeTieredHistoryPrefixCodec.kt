package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Collections

/**
 * Bounded canonical evidence representation for durable transition receipts.
 * The scope/tier/slot are supplied by the independently authenticated initial
 * receipt. Every actual, including absent RIR, failed zero reps, and recovery
 * reference, is preserved. Never infer prior decisions from today's history
 * when reopening an already-issued target.
 *
 * This is not an approval, a storage service or an alternative progression
 * engine. The eventual owner-fenced receipt transaction must rederive the
 * live prefix, compare these exact bytes, and bind them to signed consent.
 */
internal object NativeTieredHistoryPrefixCodec {
    const val MAX_BYTES=512_000
    private const val MAX_EXPOSURES=104
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun validNumber(n:BigDecimal):Boolean =
        n.toString().length<=64 && KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(n)

    private fun validate(scope:BasicProgressionScope,tier:PercentageTier,slotId:String,
        prefix:List<TieredLinearExposure>) {
        require(scope.valid() && id.matches(slotId))
        require(prefix.size in 1..MAX_EXPOSURES)
        require(prefix.map{it.completionOrder}==prefix.map{it.completionOrder}.distinct().sorted())
        require(prefix.map{it.workoutId}.distinct().size==prefix.size)
        for(e in prefix) {
            require(id.matches(e.workoutId) && e.completionOrder in 1..9_007_199_254_740_991L)
            require(e.scope==scope && e.tier==tier && e.sourceSlotId==slotId)
            require(e.issuedStage in 0..2 && e.issuedTargets.size in 1..20)
            require(e.comparableTraining==(e.recoveryReference==null))
            e.recoveryReference?.let{require(validNumber(it) && it.signum()>0)}
            require(e.issuedTargets.map{it.ordinal}==e.issuedTargets.indices.toList())
            require(e.observations.map{it.workingOrdinal}==e.issuedTargets.indices.toList())
            for((target,fact) in e.issuedTargets.zip(e.observations)) {
                val reps=if(tier==PercentageTier.PRIMARY)listOf(3,2,1)[e.issuedStage]
                    else listOf(10,8,6)[e.issuedStage]
                require(validNumber(target.load) && target.load.signum()>0 && target.minimumReps==reps)
                require(target.repOut==(e.recoveryReference==null && target.ordinal==e.issuedTargets.lastIndex))
                val actual=checkNotNull(fact.actualLoad)
                val issued=checkNotNull(fact.issuedLoad)
                require(validNumber(actual) && validNumber(issued))
                require(actual.compareTo(target.load)==0 && issued.compareTo(target.load)==0)
                require(fact.actualRir==null || fact.actualRir in 0..10)
                require(fact.issuedRir==null || fact.issuedRir in 0..10)
                require(when(fact.outcome) {
                    SetOutcome.COMPLETED -> fact.actualReps in 1..100
                    SetOutcome.FAILED_ATTEMPT -> fact.actualReps==0 && fact.actualRir==null
                    SetOutcome.SKIPPED -> false
                })
            }
        }
    }

    private fun DataOutputStream.number(value:BigDecimal) {
        require(validNumber(value));writeUTF(value.toString())
    }
    private fun DataInputStream.number():BigDecimal {
        val raw=readUTF()
        require(raw.length in 1..64)
        return BigDecimal(raw).also{require(validNumber(it) && raw==it.toString())}
    }
    private fun DataOutputStream.optionalInt(value:Int?) {
        writeBoolean(value!=null);value?.let(::writeInt)
    }
    private fun DataInputStream.optionalInt():Int?=if(readBoolean())readInt() else null

    fun encode(scope:BasicProgressionScope,tier:PercentageTier,slotId:String,
        prefix:List<TieredLinearExposure>):ByteArray {
        validate(scope,tier,slotId,prefix)
        return ByteArrayOutputStream().also{buffer->
            DataOutputStream(buffer).use{out->
                out.writeInt(1)
                // Explicitly bind all physical scope fields; no reuse of a
                // byte-identical prefix under another gym/day/slot.
                listOf(scope.athleteId,scope.programId,scope.gymId,scope.blockId,
                    scope.trainingDayId,scope.exerciseId,scope.unit.name,scope.basis.name,
                    tier.name,slotId).forEach{require(id.matches(it));out.writeUTF(it)}
                out.writeBoolean(scope.machineInstanceId!=null)
                scope.machineInstanceId?.let{require(it.length in 1..80);out.writeUTF(it)}
                out.writeInt(prefix.size)
                for(e in prefix) {
                    out.writeUTF(e.workoutId);out.writeLong(e.completionOrder)
                    out.writeInt(e.issuedStage);out.writeBoolean(e.comparableTraining)
                    out.writeBoolean(e.recoveryReference!=null)
                    e.recoveryReference?.let{out.number(it)}
                    out.writeInt(e.issuedTargets.size)
                    for((target,fact) in e.issuedTargets.zip(e.observations)) {
                        out.writeInt(target.ordinal);out.number(target.load)
                        out.writeInt(target.minimumReps);out.writeBoolean(target.repOut)
                        out.writeInt(fact.workingOrdinal);out.writeUTF(fact.outcome.name)
                        out.number(checkNotNull(fact.actualLoad))
                        out.optionalInt(fact.actualReps);out.optionalInt(fact.actualRir)
                        out.number(checkNotNull(fact.issuedLoad));out.optionalInt(fact.issuedRir)
                    }
                }
            }
        }.toByteArray().also{require(it.size in 1..MAX_BYTES)}
    }

    fun decode(bytes:ByteArray,scope:BasicProgressionScope,tier:PercentageTier,
        slotId:String):List<TieredLinearExposure> {
        require(bytes.size in 1..MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use{input->
            require(input.readInt()==1)
            val expected=listOf(scope.athleteId,scope.programId,scope.gymId,scope.blockId,
                scope.trainingDayId,scope.exerciseId,scope.unit.name,scope.basis.name,tier.name,slotId)
            require(List(expected.size){input.readUTF()}==expected)
            require((if(input.readBoolean())input.readUTF() else null)==scope.machineInstanceId)
            val count=input.readInt()
            require(count in 1..MAX_EXPOSURES)
            val prefix=List(count){
                val workout=input.readUTF();val order=input.readLong()
                val stage=input.readInt();val comparable=input.readBoolean()
                val recovery=if(input.readBoolean())input.number() else null
                val sets=input.readInt();require(sets in 1..20)
                val targets=ArrayList<TieredLinearSetTarget>(sets)
                val facts=ArrayList<WorkingSetFact>(sets)
                repeat(sets){
                    targets+=TieredLinearSetTarget(input.readInt(),input.number(),input.readInt(),input.readBoolean())
                    facts+=WorkingSetFact(input.readInt(),SetOutcome.valueOf(input.readUTF()),
                        input.number(),input.optionalInt(),input.optionalInt(),input.number(),input.optionalInt())
                }
                TieredLinearExposure(workout,order,scope,tier,stage,
                    Collections.unmodifiableList(targets),Collections.unmodifiableList(facts),comparable,slotId,recovery)
            }
            require(input.available()==0)
            validate(scope,tier,slotId,prefix)
            // Reject noncanonical boolean encodings and any alternative
            // representation that might produce the same in-memory proof.
            require(encode(scope,tier,slotId,prefix).contentEquals(bytes))
            Collections.unmodifiableList(prefix)
        }
    }

    fun fingerprint(scope:BasicProgressionScope,tier:PercentageTier,slotId:String,
        prefix:List<TieredLinearExposure>):String =
        MessageDigest.getInstance("SHA-256").digest(encode(scope,tier,slotId,prefix))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
}
