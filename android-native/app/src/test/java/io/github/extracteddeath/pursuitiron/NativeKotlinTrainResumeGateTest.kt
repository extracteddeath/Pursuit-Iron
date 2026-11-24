package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

/** The generated journal has its own authentication; unrelated previews
 * are not prerequisites to its durable read. */
class NativeKotlinTrainResumeGateTest {
    private fun canResume(
        resumeChecking:Boolean=false,
        lookupBusy:Boolean=false,
        anotherOpen:Boolean=false,
        acceptingTimeChoice:Boolean=false
    )=NativeKotlinTrainResumeGate.mayVerifyGenerated(
        resumeChecking,lookupBusy,anotherOpen,acceptingTimeChoice)

    @Test fun idleGeneratedResumeIsAvailableRegardlessOfOptionalReviews() {
        assertTrue(canResume())
        // Optional custom/measured inventory and time-budget reads do not
        // participate in this gate, so neither a slow query nor a failed
        // preview can suppress the signed generated journal's own verification.
        assertTrue(canResume(resumeChecking=false))
    }

    @Test fun aSecondResumeTapCannotRaceTheIndependentReverification() {
        assertFalse(canResume(resumeChecking=true))
        assertFalse(canResume(lookupBusy=true))
        assertFalse(canResume(anotherOpen=true))
        assertFalse(canResume(acceptingTimeChoice=true))
        assertFalse(canResume(resumeChecking=true,lookupBusy=true,anotherOpen=true))
        assertTrue(canResume())
    }
}
