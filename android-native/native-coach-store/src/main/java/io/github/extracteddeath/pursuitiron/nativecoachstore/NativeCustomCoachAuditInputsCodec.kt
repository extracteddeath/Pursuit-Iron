package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.util.Collections

/**
 * Exact typed review inputs INSIDE the existing consent transaction.
 * Order, explicit zeroes, decimal scale and absent continuity ceilings survive.
 * This is not a policy generator or an approval. Full-cycle review and the
 * unchanged consent fingerprint remain authoritative after decoding.
 */
internal object NativeCustomCoachAuditInputsCodec {
    const val MAX_BYTES=32_768
    private const val MAGIC=0x50494350 // PICP
    private const val VERSION=1
    private fun DataOutputStream.word(s:String,maximum:Int=80) {
        require(s.isNotBlank() && s.length<=maximum)
        writeUTF(s)
    }
    private fun DataInputStream.word(maximum:Int=80):String = readUTF().also {
        require(it.isNotBlank() && it.length<=maximum)
    }
    private fun DataOutputStream.decimal(n:BigDecimal) {
        require(n.scale() in -128..128 && n>=BigDecimal.ZERO && n<=BigDecimal("200") &&
            n.stripTrailingZeros().scale()<=3)
        word(n.toPlainString(),128)
    }
    private fun DataInputStream.decimal():BigDecimal = BigDecimal(word(128)).also {
        require(it.scale() in -128..128 && it>=BigDecimal.ZERO && it<=BigDecimal("200") &&
            it.stripTrailingZeros().scale()<=3)
    }
    private inline fun <reified T:Enum<T>> DataInputStream.enum():T {
        val name=word()
        return enumValues<T>().firstOrNull {it.name==name}
            ?:throw IOException("Unknown review input enum")
    }
    private fun DataInputStream.count(maximum:Int):Int=readInt().also {
        require(it in 0..maximum)
    }
    private fun DataOutputStream.bounds(rows:List<DeclaredMuscleDoseBounds>) {
        require(TrainingMuscleCoverage.isComplete(rows.map{it.muscle}))
        writeInt(rows.size)
        rows.forEach {b->
            word(b.muscle.name);decimal(b.mev);decimal(b.mav);decimal(b.mrv)
            require(b.minimumDirectDays in 0..7)
            writeInt(b.minimumDirectDays)
        }
    }
    private fun DataInputStream.bounds():List<DeclaredMuscleDoseBounds> {
        val rows=List(count(TrainingMuscle.entries.size)) {
            DeclaredMuscleDoseBounds(enum(),decimal(),decimal(),decimal(),readInt())
        }
        require(TrainingMuscleCoverage.isComplete(rows.map{it.muscle}) &&
            rows.all{it.minimumDirectDays in 0..7})
        return Collections.unmodifiableList(rows)
    }
    private fun DataOutputStream.policy(p:AuthoredGoalProgramPolicy) {
        word(p.policyId,64);word(p.revision,64);word(p.rationale,400)
        word(p.athleteId,64);word(p.athleteRevision,64)
        word(p.goal.name);word(p.experience.name);word(p.split.name);word(p.phase.name)
        require(p.trainingDays in 1..7);writeInt(p.trainingDays)
        bounds(p.baselineBounds);bounds(p.selectedBounds)
        require(p.minimumPurposeDays.size<=ComposerSessionPurpose.entries.size)
        writeInt(p.minimumPurposeDays.size)
        p.minimumPurposeDays.forEach {(purpose,days)->
            require(days in 0..7);word(purpose.name);writeInt(days)
        }
        require(p.primaryLiftMinimumDays.size<=16)
        writeInt(p.primaryLiftMinimumDays.size)
        p.primaryLiftMinimumDays.forEach {(exercise,days)->
            require(days in 0..7);word(exercise,64);writeInt(days)
        }
    }
    private fun DataInputStream.policy():AuthoredGoalProgramPolicy {
        val policyId=word(64);val revision=word(64);val rationale=word(400)
        val athleteId=word(64);val athleteRevision=word(64)
        val goal=enum<AthleteTrainingGoal>();val experience=enum<AthleteExperience>()
        val split=enum<ComposerSplit>();val phase=enum<TrainingPhase>()
        val days=readInt().also{require(it in 1..7)}
        val baseline=bounds();val selected=bounds()
        val purposes=linkedMapOf<ComposerSessionPurpose,Int>()
        repeat(count(ComposerSessionPurpose.entries.size)) {
            val purpose=enum<ComposerSessionPurpose>()
            val count=readInt().also{require(it in 0..7)}
            require(purposes.put(purpose,count)==null)
        }
        val lifts=linkedMapOf<String,Int>()
        repeat(count(16)) {
            val exercise=word(64);val count=readInt().also{require(it in 0..7)}
            require(lifts.put(exercise,count)==null)
        }
        return AuthoredGoalProgramPolicy(policyId,revision,rationale,athleteId,
            athleteRevision,goal,experience,split,phase,days,baseline,selected,
            Collections.unmodifiableMap(purposes),Collections.unmodifiableMap(lifts))
    }
    fun encode(inputs:CustomCoachAuditInputs):ByteArray {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {out->
            out.writeInt(MAGIC);out.writeInt(VERSION)
            require(inputs.policiesByPhase.size<=TrainingPhase.entries.size)
            out.writeInt(inputs.policiesByPhase.size)
            inputs.policiesByPhase.forEach {(phase,policy)->
                out.word(phase.name);out.policy(policy)
            }
            require(inputs.nonSetSecondsByWeekday.size<=7)
            out.writeInt(inputs.nonSetSecondsByWeekday.size)
            inputs.nonSetSecondsByWeekday.forEach {(day,seconds)->
                require(day in 0..6 && seconds in 0..21600)
                out.writeInt(day);out.writeInt(seconds)
            }
            out.writeBoolean(inputs.continuityPolicy!=null)
            inputs.continuityPolicy?.let {c->
                out.decimal(c.maximumTotalWorkingSetDropFraction)
                out.decimal(c.maximumDirectMuscleDropFraction)
                out.writeBoolean(c.maximumTotalWorkingSetRiseFraction!=null)
                c.maximumTotalWorkingSetRiseFraction?.let{out.decimal(it)}
                out.writeBoolean(c.maximumDirectMuscleRiseFraction!=null)
                c.maximumDirectMuscleRiseFraction?.let{out.decimal(it)}
            }
        }
        return bytes.toByteArray().also {require(it.size<=MAX_BYTES)}
    }
    fun decode(bytes:ByteArray):CustomCoachAuditInputs {
        require(bytes.size in 17..MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use {input->
            require(input.readInt()==MAGIC && input.readInt()==VERSION)
            val policies=linkedMapOf<TrainingPhase,AuthoredGoalProgramPolicy>()
            repeat(input.count(TrainingPhase.entries.size)) {
                val phase=input.enum<TrainingPhase>()
                require(policies.put(phase,input.policy())==null)
            }
            val overhead=linkedMapOf<Int,Int>()
            repeat(input.count(7)) {
                val day=input.readInt();val seconds=input.readInt()
                require(day in 0..6 && seconds in 0..21600 &&
                    overhead.put(day,seconds)==null)
            }
            val continuity=if(input.readBoolean())CustomCoachVolumeContinuityPolicy(
                input.decimal(),input.decimal(),
                if(input.readBoolean())input.decimal() else null,
                if(input.readBoolean())input.decimal() else null) else null
            require(input.available()==0)
            CustomCoachAuditInputs(Collections.unmodifiableMap(policies),
                Collections.unmodifiableMap(overhead),continuity)
        }
    }
}
