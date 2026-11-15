package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinSupersetEditRequestTest {
    private val request=NativeKotlinSupersetEditRequest("owner","athlete","cycle",3,7,"base",0,"pair")
    private fun draft()=CustomTrainingCycle("cycle",3,"athlete",AthleteTrainingGoal.HYPERTROPHY,"Custom",emptyList())
    private fun source()=NativeCustomAuthoringSnapshot("owner","athlete",7,
        CoachAthleteSetupDomain.review(AthleteSetupDraft()),emptyList())
    @Test fun primitiveSavedCoordinatesRestoreWithoutPersistingTrainingOrAnAuthorization() {
        assertEquals(request,NativeKotlinSupersetEditRequest.restore(request.savedState()))
        assertEquals(9,request.savedState().size)
        assertTrue(request.savedState().all{it is String || it is Long || it is Int})
    }
    @Test fun ownerAthleteAndSourceRevisionMustAllMatchTheFreshNativeSource() {
        val source=source()
        assertTrue(request.matchesSource(source))
        assertFalse(request.matchesSource(source.copy(ownerKey="other")))
        assertFalse(request.matchesSource(source.copy(athleteId="other")))
        assertFalse(request.matchesSource(source.copy(revision=8)))
    }
    @Test fun cycleAthleteAndRevisionMustAllMatchTheDisplayedProgram() {
        val draft=draft()
        assertTrue(request.matches(draft))
        assertFalse(request.matches(draft.copy(cycleId="other")))
        assertFalse(request.matches(draft.copy(athleteId="other")))
        assertFalse(request.matches(draft.copy(revision=4)))
    }
    @Test fun missingUnknownVersionAndWrongPrimitiveTypesFailClosed() {
        val state=request.savedState()
        assertNull(NativeKotlinSupersetEditRequest.restore(emptyList()))
        assertNull(NativeKotlinSupersetEditRequest.restore(state.dropLast(1)))
        assertNull(NativeKotlinSupersetEditRequest.restore(state+"extra"))
        for((index,value) in listOf(0 to "v2",4 to "3",5 to 7,7 to 0L)) {
            val bad=state.toMutableList();bad[index]=value
            assertNull(NativeKotlinSupersetEditRequest.restore(bad))
        }
    }
    @Test fun invalidIdentifiersAndOutOfRangeCoordinatesCannotRestoreAnEditor() {
        val state=request.savedState()
        for((index,value) in listOf(1 to "\nowner",2 to "",3 to "bad/cycle",4 to 0L,
            5 to -1L,5 to Long.MAX_VALUE,6 to "bad/block",7 to 7,8 to "bad/group")) {
            val bad=state.toMutableList();bad[index]=value
            assertNull(NativeKotlinSupersetEditRequest.restore(bad))
        }
    }
}
