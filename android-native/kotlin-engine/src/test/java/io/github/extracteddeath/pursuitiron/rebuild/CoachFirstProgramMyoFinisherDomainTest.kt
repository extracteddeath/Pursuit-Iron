package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachFirstProgramMyoFinisherDomainTest {
    private val load=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.CABLE_STACK,
        BigDecimal("35"))
    private fun set(index:Int,weight:DraftSetLoad=load,rir:Int?=2,
        band:RepBand=RepBand(12,15),type:DraftSetType=DraftSetType.WORKING)=
        DraftPrescribedSet("curl-set-$index",type,index,weight,band,rir,45,90)
    private fun exercise(
        role:ComposerRole=ComposerRole.ACCESSORY,
        sets:List<DraftPrescribedSet> = listOf(set(1),set(2))
    )=DraftPrescribedExercise("cable-curl",role,
        ComposerSessionPurpose.HYPERTROPHY,sets,"Reviewed")
    private fun session(vararg exercises:DraftPrescribedExercise)=
        DraftPrescribedSession(0,ComposerSessionPurpose.HYPERTROPHY,
            exercises.toList(),55)
    private fun movement(
        role:ComposerRole=ComposerRole.ACCESSORY,
        pattern:ComposerPattern=ComposerPattern.ISOLATION,
        implement:String?="cable-curl-machine",
        reviewed:CatalogCreditStatus=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
        basis:LoadBasis?=LoadBasis.CABLE_STACK
    )=CatalogMovement("cable-curl","elbow_flexion","cable",null,
        pattern,role,setOf(ComposerFocus.PULL),implement,basis,
        listOf(DeclaredMuscleCredit(TrainingMuscle.BICEPS,
            MuscleCreditRole.DIRECT,BigDecimal.ONE)),
        reviewed,"reviewed-machine-isolation")

    private fun pick(s:DraftPrescribedSession=session(exercise()),
        catalog:CatalogMovement=movement(),
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        experience:AthleteExperience=AthleteExperience.INTERMEDIATE
    )=CoachFirstProgramMyoFinisherDomain.recommend(
        listOf(s),listOf(catalog),phase,experience)

    @Test fun optInCandidateSelectsExactlyFinalVerifiedWorkingSet() {
        val first=session(exercise(sets=listOf(
            set(0,type=DraftSetType.WARMUP),set(1),set(2))))
        val candidate=pick(first)!!
        assertEquals(0,candidate.dayIndex)
        assertEquals(0,candidate.exerciseIndex)
        assertEquals(2,candidate.setIndex)
        assertEquals("curl-set-2",candidate.setKey)
        assertEquals("cable-curl",candidate.exerciseId)
        val amp=CoachFirstProgramMyoFinisherDomain.prescribedTechnique()
        assertEquals(CustomAmplifierKind.MYO_REPS,amp.kind)
        assertEquals(4,amp.followUpRounds)
        assertEquals(4,amp.followUpReps)
        assertEquals(20,amp.followUpRestSeconds)
        assertNull(amp.followUpLoad)
        assertNull(amp.secondDropLoad)
        assertTrue(AdvancedAmplifierProtocolDomain.validStagedFollowUps(amp))
    }

    @Test fun noviceStrengthOrUnreviewedEquipmentNeverReceivesAutomaticTechnique() {
        assertNull(pick(experience=AthleteExperience.BEGINNER))
        assertNull(pick(phase=TrainingPhase.STRENGTH))
        assertNull(pick(phase=TrainingPhase.PEAK))
        assertNull(pick(catalog=movement(reviewed=CatalogCreditStatus.UNREVIEWED)))
        assertNull(pick(catalog=movement(implement="dumbbell-rack")))
        assertNull(pick(catalog=movement(implement="rated-resistance-band-set")))
        assertNull(pick(catalog=movement(implement=null,basis=null)))
        assertNull(pick(catalog=movement(role=ComposerRole.PRIMARY)))
        assertNull(pick(catalog=movement(pattern=ComposerPattern.HINGE)))
        assertNull(pick(s=session(exercise(role=ComposerRole.PRIMARY))))
        assertNull(pick(catalog=movement(basis=null)))
    }

    @Test fun actualPrescriptionMustBeSuitableBeforeOptInIsEvenShown() {
        assertNull(pick(s=session(exercise(sets=listOf(set(1))))))
        assertNull(pick(s=session(exercise(sets=listOf(
            set(1,rir=null),set(2,rir=null))))))
        assertNull(pick(s=session(exercise(sets=listOf(
            set(1,rir=0),set(2,rir=0))))))
        assertNull(pick(s=session(exercise(sets=listOf(
            set(1,band=RepBand(8,12)),set(2,band=RepBand(8,12)))))))
        assertNull(pick(s=session(exercise(sets=listOf(
            set(1,band=RepBand(12,25)),set(2,band=RepBand(12,25)))))))
        assertNull(pick(s=session(exercise(sets=listOf(
            set(1),set(2,weight=load.copy(value=BigDecimal("30"))))))))
        assertNull(pick(s=session(exercise(sets=listOf(
            set(1),set(2,weight=DraftSetLoad.ExplicitBodyweight))))))
    }

    @Test fun onlyOneCandidateAcrossDaysAndNoWorkingLoadsAreEdited() {
        val ex=exercise()
        val days=listOf(session(ex),
            session(ex).copy(dayIndex=1))
        val before=days.toList()
        val pick=CoachFirstProgramMyoFinisherDomain.recommend(days,
            listOf(movement()),TrainingPhase.HYPERTROPHY,
            AthleteExperience.ADVANCED)!!
        assertEquals(0,pick.dayIndex)
        assertEquals(before,days)
    }
    @Test fun identicalPhysicalLoadsAcrossDecimalScalesAreOneSameLoadFinisher() {
        val first=set(1,weight=load.copy(value=BigDecimal("35.000")))
        val last=set(2,weight=load.copy(value=BigDecimal("35.0")))
        val baseline=pick(s=session(exercise(sets=listOf(first,last))))
        assertEquals("curl-set-2",baseline?.setKey)
        // Never rewrite the actual authored decimal representations.
        assertEquals(BigDecimal("35.000"),
            (first.load as DraftSetLoad.Verified).value)
        assertEquals(BigDecimal("35.0"),
            (last.load as DraftSetLoad.Verified).value)
        val differentBasis=first.copy(
            load=load.copy(basis=LoadBasis.MACHINE_STACK))
        assertNull(pick(s=session(exercise(sets=listOf(
            differentBasis,last)))))
        val differentUnit=first.copy(
            load=load.copy(unit=LoadUnit.KG))
        assertNull(pick(s=session(exercise(sets=listOf(
            differentUnit,last)))))
        val differentWeight=first.copy(
            load=load.copy(value=BigDecimal("34.999")))
        assertNull(pick(s=session(exercise(sets=listOf(
            differentWeight,last)))))
    }

}
