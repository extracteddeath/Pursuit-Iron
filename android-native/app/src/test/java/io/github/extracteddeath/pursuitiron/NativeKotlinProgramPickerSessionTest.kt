package io.github.extracteddeath.pursuitiron

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinProgramPickerSessionTest {
    @Test fun cancelledBrowseCannotPublishLateResultsOrReplaceTheCurrentTask() {
        val session=NativeKotlinProgramPickerSession()
        var task="current program with Undo"
        var sheet:String?=null
        val token=session.begin{sheet="loading"}
        session.dismiss{sheet=null}
        assertFalse(session.publish(token){sheet="late rows"})
        assertFalse(session.claim(token){task="different program"})
        assertNull(sheet)
        assertEquals("current program with Undo",task)
    }

    @Test fun reopeningAndRefreshingRejectAllOlderResults() {
        val session=NativeKotlinProgramPickerSession()
        var rows=""
        val old=session.begin{rows="old loading"}
        val fresh=session.begin{rows="new loading"}
        assertTrue(fresh>old)
        assertFalse(session.publish(old){rows="old rows"})
        assertTrue(session.publish(fresh){rows="fresh rows"})
        assertFalse(session.claim(old){rows="old program"})
        assertEquals("fresh rows",rows)
    }

    @Test fun acceptedSelectionCanPublishExactlyOnceAndCannotBeRevived() {
        val session=NativeKotlinProgramPickerSession()
        val opened=AtomicInteger()
        val token=session.begin{}
        assertTrue(session.claim(token){opened.incrementAndGet()})
        assertFalse(session.claim(token){opened.incrementAndGet()})
        assertFalse(session.publish(token){opened.incrementAndGet()})
        assertEquals(1,opened.get())
    }

    @Test fun failedSelectionKeepsTheChooserOpenForAnotherChoiceOrCancel() {
        val session=NativeKotlinProgramPickerSession()
        var message="loading"
        val token=session.begin{}
        assertTrue(session.publish(token){message="record missing; choose again"})
        assertTrue(session.publish(token){message="another selection"})
        session.dismiss{message="cancelled"}
        assertFalse(session.claim(token){message="late open"})
        assertEquals("cancelled",message)
    }

    @Test fun cancelCompletesWhileAReadIsBlockedAndPreventsItsLateCommit() {
        val session=NativeKotlinProgramPickerSession()
        val started=CountDownLatch(1)
        val release=CountDownLatch(1)
        val executor=Executors.newFixedThreadPool(2)
        val opened=AtomicInteger()
        val token=session.begin{}
        try {
            val read=executor.submit<Boolean> {
                started.countDown()
                check(release.await(5,TimeUnit.SECONDS))
                session.claim(token){opened.incrementAndGet()}
            }
            assertTrue(started.await(5,TimeUnit.SECONDS))
            executor.submit{session.dismiss{}}.get(5,TimeUnit.SECONDS)
            release.countDown()
            assertFalse(read.get(5,TimeUnit.SECONDS))
            assertEquals(0,opened.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun simultaneousClaimsCannotOpenTwoPrograms() {
        val session=NativeKotlinProgramPickerSession()
        val token=session.begin{}
        val release=CountDownLatch(1)
        val opened=AtomicInteger()
        val executor=Executors.newFixedThreadPool(2)
        try {
            val attempts=(1..2).map{executor.submit<Boolean> {
                check(release.await(5,TimeUnit.SECONDS))
                session.claim(token){opened.incrementAndGet()}
            }}
            release.countDown()
            assertEquals(1,attempts.count{it.get(5,TimeUnit.SECONDS)})
            assertEquals(1,opened.get())
        } finally {executor.shutdownNow()}
    }
}
