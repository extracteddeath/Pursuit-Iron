package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeSetEntryPolicyTest {
    @Test fun validObservationsRemainConsistentAcrossAllSetEditors() {
        assertTrue(NativeSetEntryPolicy.accepts("0","12","",false))
        assertTrue(NativeSetEntryPolicy.accepts("185.125","5","2",false))
        assertTrue(NativeSetEntryPolicy.accepts("200","0","",true))
        for((load,reps,rir,allow) in listOf(
            listOf("0","0","","true"),
            listOf("200","0","","false"),
            listOf("-10","5","","false"),
            listOf("05","5","","false"),
            listOf("100.1234","5","","false"),
            listOf("100","101","","false"),
            listOf("100","3","11","false"),
            listOf("100","","","false"),
            listOf("100","5","-","false")
        )) {
            assertFalse("Rejected: $load $reps $rir",NativeSetEntryPolicy.accepts(
                load,reps,rir,allow=="true"))
        }
    }

    @Test fun repetitionSteppersPreserveZeroRepSpecialCaseAndBoundaries() {
        assertEquals("1",NativeSetEntryPolicy.stepReps("",1,false))
        assertEquals("0",NativeSetEntryPolicy.stepReps("",1,true))
        assertNull(NativeSetEntryPolicy.stepReps("1",-1,false))
        assertEquals("0",NativeSetEntryPolicy.stepReps("1",-1,true))
        assertEquals("100",NativeSetEntryPolicy.stepReps("99",1,false))
        assertNull(NativeSetEntryPolicy.stepReps("100",1,false))
        assertNull(NativeSetEntryPolicy.stepReps("0",-1,true))
        assertNull(NativeSetEntryPolicy.stepReps("bad",1,false))
        assertNull(NativeSetEntryPolicy.stepReps("5",2,false))
    }

    @Test fun rirSteppersNeverAssumeNonzeroEffort() {
        assertEquals("0",NativeSetEntryPolicy.stepRir("",1))
        assertNull(NativeSetEntryPolicy.stepRir("",-1))
        assertEquals("",NativeSetEntryPolicy.stepRir("0",-1))
        assertEquals("1",NativeSetEntryPolicy.stepRir("0",1))
        assertEquals("10",NativeSetEntryPolicy.stepRir("9",1))
        assertNull(NativeSetEntryPolicy.stepRir("10",1))
        assertNull(NativeSetEntryPolicy.stepRir("11",-1))
        assertNull(NativeSetEntryPolicy.stepRir("abc",1))
        assertNull(NativeSetEntryPolicy.stepRir("2",0))
    }
    @Test fun longPressRepsOnlyMakesSingleBoundedDraftChange() {
        assertEquals("3",NativeSetEntryPolicy.stepRepsBy("",1,3,false))
        assertEquals("2",NativeSetEntryPolicy.stepRepsBy("",1,3,true))
        assertEquals("100",NativeSetEntryPolicy.stepRepsBy("99",1,3,false))
        assertEquals("1",NativeSetEntryPolicy.stepRepsBy("2",-1,3,false))
        assertEquals("0",NativeSetEntryPolicy.stepRepsBy("1",-1,3,true))
        assertNull(NativeSetEntryPolicy.stepRepsBy("1",-1,3,false))
        assertNull(NativeSetEntryPolicy.stepRepsBy("100",1,3,false))
        assertNull(NativeSetEntryPolicy.stepRepsBy("bad",1,3,false))
        assertNull(NativeSetEntryPolicy.stepRepsBy("8",0,3,false))
        assertNull(NativeSetEntryPolicy.stepRepsBy("8",1,4,false))
        assertNull(NativeSetEntryPolicy.stepRepsBy("8",1,0,false))
        assertEquals("9",NativeSetEntryPolicy.stepRepsBy("8",1,1,false))
    }

    @Test fun longPressRirRetainsOptionalBlankAndAvoidsInventedEffort() {
        assertEquals("1",NativeSetEntryPolicy.stepRirBy("",1,2))
        assertEquals("",NativeSetEntryPolicy.stepRirBy("0",-1,2))
        assertEquals("10",NativeSetEntryPolicy.stepRirBy("9",1,2))
        assertEquals("",NativeSetEntryPolicy.stepRirBy("1",-1,2))
        assertEquals("0",NativeSetEntryPolicy.stepRirBy("2",-1,2))
        assertNull(NativeSetEntryPolicy.stepRirBy("",-1,2))
        assertNull(NativeSetEntryPolicy.stepRirBy("10",1,2))
        assertNull(NativeSetEntryPolicy.stepRirBy("bad",1,2))
        assertNull(NativeSetEntryPolicy.stepRirBy("5",1,3))
        assertNull(NativeSetEntryPolicy.stepRirBy("5",1,0))
        assertEquals("4",NativeSetEntryPolicy.stepRirBy("3",1,1))
    }
}
