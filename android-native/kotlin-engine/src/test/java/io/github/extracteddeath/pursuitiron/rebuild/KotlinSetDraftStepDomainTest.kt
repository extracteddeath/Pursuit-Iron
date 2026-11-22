package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class KotlinSetDraftStepDomainTest {
    @Test fun ordinaryRepsRemainInsideFactualCompletionBounds() {
        val p=KotlinSetDraftStepDomain
        assertEquals("1",p.stepReps("",1,false))
        assertEquals("3",p.stepRepsBy("",1,3,false))
        assertNull(p.stepReps("1",-1,false))
        assertEquals("100",p.stepRepsBy("99",1,3,false))
        assertNull(p.stepReps("100",1,false))
        assertNull(p.stepReps("unknown",1,false))
        assertNull(p.stepReps("8",2,false))
        assertNull(p.stepRepsBy("8",1,4,false))
        assertEquals("5",p.stepRepsBy("8",-1,3,false))
    }

    @Test fun failedAttemptsMayStartAtZeroWithoutInventingCompletion() {
        val p=KotlinSetDraftStepDomain
        assertEquals("0",p.stepReps("",1,true))
        assertEquals("0",p.stepReps("1",-1,true))
        assertNull(p.stepReps("0",-1,true))
        assertEquals("2",p.stepRepsBy("",1,3,true))
        assertNull(p.stepRepsBy("10",1,0,true))
    }

    @Test fun unreportedRirIsNotConvertedToEffortByDecrement() {
        val p=KotlinSetDraftStepDomain
        assertNull(p.stepRir("",-1))
        assertEquals("0",p.stepRir("",1))
        assertEquals("",p.stepRir("0",-1))
        assertEquals("1",p.stepRirBy("",1,2))
        assertEquals("",p.stepRirBy("1",-1,2))
        assertEquals("10",p.stepRirBy("9",1,2))
        assertNull(p.stepRir("10",1))
        assertNull(p.stepRir("11",-1))
        assertNull(p.stepRirBy("8",1,3))
        assertNull(p.stepRir("8",0))
    }

    @Test fun gesturesDoNotCreateAJournalOrExerciseLoadRule() {
        assertEquals("12",KotlinSetDraftStepDomain.stepReps("11",1,false))
        assertEquals("2",KotlinSetDraftStepDomain.stepRir("1",1))
        // The domain returns draft text only. Issuance and performed actual
        // validation remain in KotlinSetNumberAuthorityDomain.
    }
}
