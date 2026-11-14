package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinPlanEntryRequestsTest {
    @Test fun returningDuringAnEditReadsTheStateOnlyAfterTheEditHasSaved() {
        val requests=NativeKotlinPlanEntryRequests()
        var savedRevision=1
        var displayedRevision=0
        requests.resume {displayedRevision=savedRevision}
        assertEquals(0,displayedRevision)
        savedRevision=2
        checkNotNull(requests.takeNext()).invoke()
        assertEquals(2,displayedRevision)
        assertFalse(requests.hasPending)
        assertNull(requests.takeNext())
    }

    @Test fun passiveBackCannotLoseANewProgramHandoffWaitingForAnEarlierSave() {
        val requests=NativeKotlinPlanEntryRequests()
        var opened="previous-program"
        requests.select {opened="newly-created-program"}
        repeat(3){requests.resume {opened="previous-program"}}
        checkNotNull(requests.takeNext()).invoke()
        assertEquals("newly-created-program",opened)
        assertFalse(requests.hasPending)
    }

    @Test fun latestExplicitProgramChoiceWinsBeforeEntryIsServiced() {
        val requests=NativeKotlinPlanEntryRequests()
        val opened=mutableListOf<String>()
        requests.resume {opened+="old-return"}
        requests.select {opened+="program-b"}
        requests.select {opened+="program-c"}
        checkNotNull(requests.takeNext()).invoke()
        assertEquals(listOf("program-c"),opened)
        assertNull(requests.takeNext())
    }

    @Test fun repeatedPassiveReturnsCoalesceWithoutReplayingOldReads() {
        val requests=NativeKotlinPlanEntryRequests()
        val reads=mutableListOf<Int>()
        repeat(5){index->requests.resume {reads+=index}}
        checkNotNull(requests.takeNext()).invoke()
        assertEquals(listOf(4),reads)
        assertFalse(requests.hasPending)
    }

    @Test fun requestArrivingDuringEntryRunsAfterwardsWithoutLosingItsTarget() {
        val requests=NativeKotlinPlanEntryRequests()
        val opened=mutableListOf<String>()
        requests.select {
            opened+="program-b"
            requests.select {opened+="program-c"}
        }
        checkNotNull(requests.takeNext()).invoke()
        assertTrue(requests.hasPending)
        checkNotNull(requests.takeNext()).invoke()
        assertEquals(listOf("program-b","program-c"),opened)
        assertFalse(requests.hasPending)
    }

    @Test fun closingEntryWorkDropsPendingReadRequests() {
        val requests=NativeKotlinPlanEntryRequests()
        var called=false
        requests.select {called=true}
        requests.clear()
        assertNull(requests.takeNext())
        assertFalse(called)
    }

    @Test fun lateSaveRefreshCannotReplaceAnExplicitProgramChoice() {
        val requests=NativeKotlinPlanEntryRequests()
        val opened=mutableListOf<String>()
        requests.select {opened+="program-b"}
        requests.refreshSaved("program-a",{"program-a"}) {opened+="program-a"}
        checkNotNull(requests.takeNext()).invoke()
        assertEquals(listOf("program-b"),opened)
    }

    @Test fun passiveReturnCannotDiscardAConfirmedSaveReaudit() {
        val requests=NativeKotlinPlanEntryRequests()
        val read=mutableListOf<String>()
        requests.refreshSaved("program-a",{"program-a"}) {read+="saved-review"}
        requests.resume {read+="ordinary-return"}
        checkNotNull(requests.takeNext()).invoke()
        assertEquals(listOf("saved-review"),read)
    }

    @Test fun saveRefreshChecksTheSelectedProgramAfterThePreviousOperationCompletes() {
        val requests=NativeKotlinPlanEntryRequests()
        var current="program-a"
        var oldProgramOpened=false
        requests.refreshSaved("program-a",{current}) {oldProgramOpened=true}
        current="program-b"
        checkNotNull(requests.takeNext()).invoke()
        assertFalse(oldProgramOpened)
        assertFalse(requests.hasPending)
    }

    @Test fun obsoleteSaveRefreshStillRunsThePendingReturnForTheNewlySelectedProgram() {
        val requests=NativeKotlinPlanEntryRequests()
        var current="program-a"
        val opened=mutableListOf<String>()
        requests.refreshSaved("program-a",{current}) {opened+="old-program-refresh"}
        requests.resume {opened+="return-to-$current"}
        current="program-b"
        checkNotNull(requests.takeNext()).invoke()
        assertEquals(listOf("return-to-program-b"),opened)
        assertFalse(requests.hasPending)
    }
}
