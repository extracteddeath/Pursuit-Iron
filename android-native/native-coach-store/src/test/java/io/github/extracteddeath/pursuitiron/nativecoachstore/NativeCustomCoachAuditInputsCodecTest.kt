package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class NativeCustomCoachAuditInputsCodecTest {
    private fun sample():CustomCoachAuditInputs {
        val phases=TrainingPhase.entries.reversed().associateWith {phase->
            val bounds=TrainingMuscle.entries.reversed().mapIndexed {i,muscle->
                DeclaredMuscleDoseBounds(muscle,BigDecimal("0.000"),
                    BigDecimal(i+1).setScale(2),BigDecimal(i+2),i%3)
            }
            AuthoredGoalProgramPolicy("policy-${phase.ordinal}","r1",
                "Reviewed athlete choices · résumé", "athlete-1","profile-1",
                AthleteTrainingGoal.HYPERTROPHY,AthleteExperience.ADVANCED,
                ComposerSplit.FULL_BODY,phase,3,bounds,bounds.reversed(),
                linkedMapOf(ComposerSessionPurpose.HYPERTROPHY to 2,
                    ComposerSessionPurpose.STRENGTH_SKILL to 1),
                linkedMapOf("squat" to 2,"bench" to 1))
        }
        return CustomCoachAuditInputs(phases,linkedMapOf(4 to 900,0 to 0,2 to 123),
            CustomCoachVolumeContinuityPolicy(BigDecimal("0.200"),
                BigDecimal("0.10"),BigDecimal("0.250"),null))
    }
    @Test fun allPhasesIndependentMusclesOrderScaleAndExplicitZeroSurvive() {
        val original=sample()
        val restored=NativeCustomCoachAuditInputsCodec.decode(
            NativeCustomCoachAuditInputsCodec.encode(original))
        assertEquals(original,restored)
        assertEquals(original.policiesByPhase.keys.toList(),restored.policiesByPhase.keys.toList())
        assertEquals(listOf(4,0,2),restored.nonSetSecondsByWeekday.keys.toList())
        for(phase in TrainingPhase.entries) {
            val bounds=restored.policiesByPhase.getValue(phase).selectedBounds
            val byMuscle=bounds.associateBy{it.muscle}
            assertNotEquals(byMuscle[TrainingMuscle.BICEPS]?.mav,
                byMuscle[TrainingMuscle.BRACHIALIS]?.mav)
            assertNotEquals(byMuscle[TrainingMuscle.WRIST_FLEXORS]?.mav,
                byMuscle[TrainingMuscle.WRIST_EXTENSORS]?.mav)
        }
        assertNull(restored.continuityPolicy?.maximumDirectMuscleRiseFraction)
        val absent=original.copy(continuityPolicy=null)
        assertEquals(absent,NativeCustomCoachAuditInputsCodec.decode(
            NativeCustomCoachAuditInputsCodec.encode(absent)))
        val both=original.copy(continuityPolicy=original.continuityPolicy?.copy(
            maximumDirectMuscleRiseFraction=BigDecimal("0.000")))
        assertEquals(both,NativeCustomCoachAuditInputsCodec.decode(
            NativeCustomCoachAuditInputsCodec.encode(both)))
    }

    @Test fun invalidVersionEnumsCountsAndTrailingBytesNeverBecomeRules() {
        val valid=NativeCustomCoachAuditInputsCodec.encode(sample())
        val badVersion=valid.copyOf().also{ByteBuffer.wrap(it).putInt(4,99)}
        val hugeCount=valid.copyOf().also{ByteBuffer.wrap(it).putInt(8,Int.MAX_VALUE)}
        val badEnum=valid.copyOf().also {bytes->
            // First phase name immediately follows the bounded map count.
            bytes[14]='X'.code.toByte()
        }
        for(bytes in listOf(badVersion,hugeCount,badEnum,valid+byteArrayOf(0),valid.copyOf(16)))
            assertThrows(Exception::class.java){NativeCustomCoachAuditInputsCodec.decode(bytes)}
    }

    @Test fun excessiveDecimalExpansionIsRefusedBeforeMaterializingIt() {
        val original=sample()
        val changed=original.copy(continuityPolicy=original.continuityPolicy?.copy(
            maximumDirectMuscleRiseFraction=BigDecimal("0E-2147483647")))
        assertThrows(IllegalArgumentException::class.java) {
            NativeCustomCoachAuditInputsCodec.encode(changed)
        }
    }
}
