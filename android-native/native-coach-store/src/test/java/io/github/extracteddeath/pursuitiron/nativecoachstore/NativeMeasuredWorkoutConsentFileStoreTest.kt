package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real files and owner locks, not a fabricated UI "approved" flag. */
class NativeMeasuredWorkoutConsentFileStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private val root by lazy{temp.root.toPath()}
    private val fence by lazy{NativeKotlinApprovalRevisionFence(root)}
    private var signed:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{signed}
    private val sources by lazy{
        NativeKotlinCustomAuthoringSourceFileStore(root,fence,identity)}
    private val history by lazy{NativeKotlinHistoryManifestFileStore(root)}
    private val composer by lazy{KotlinNativeJournalFileStore(root)}
    private val drafts by lazy{NativeCustomCycleDraftFileStore(root,fence,identity)}
    private val signoffs by lazy{
        NativeKotlinCustomCycleSignoffFileStore(root,fence,identity,drafts,sources)}
    private val preflight by lazy{
        NativeKotlinCustomWorkoutPreflight(fence,identity,drafts,sources,signoffs)}
    private val prepared by lazy{
        NativeKotlinCustomWorkoutPreparedStore(root,fence,identity,preflight)}
    private val custom by lazy{
        NativeKotlinCustomWorkoutJournalFileStore(root,fence,identity,
            prepared,preflight,history,composer)}
    private fun approvals()=NativeMeasuredWorkoutConsentFileStore(
        root,fence,identity,sources,history,composer,custom)
    private fun parent()=NativeMeasuredWorkoutParentFileStore(
        root,fence,identity,approvals(),history,composer,custom)
    private fun source(version:Long=1):NativeCustomAuthoringSnapshot {
        val setup=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,emptyMap(),
                setOf("pullup-bar","wall-support"),emptySet()),
            allowBodyweight=true,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup.status)
        return NativeCustomAuthoringSnapshot("owner-1","athlete-1",version,
            setup,StarterExerciseTranche.movements())
    }
    private fun enroll() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
    }
    private fun plan(id:String="plank")=MeasuredWorkoutPlan("session-1",
        "gym-1",1,listOf(MeasuredExerciseSetPlan("session-1","set-1",
            id,"gym-1",1,"floor",35,setOf(MeasuredSide.BOTH),
            MeasuredExerciseCatalogDomain.find(id)?.requiredFixtures.orEmpty())))

    @Test fun previewRequiresSourceAndHistoryAndCannotIssue() {
        val before=approvals().preview("owner-1",plan(),1)
        assertEquals(NativeMeasuredConsentRefusal.SOURCE_UNAVAILABLE,
            (before as NativeMeasuredConsentResult.Rejected).reason)
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        val noHistory=approvals().preview("owner-1",plan(),1)
        assertEquals(NativeMeasuredConsentRefusal.HISTORY_NOT_READY,
            (noHistory as NativeMeasuredConsentResult.Rejected).reason)
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
        val candidate=approvals().preview("owner-1",plan(),1)
        assertTrue(candidate.toString(),candidate is NativeMeasuredConsentResult.Preview)
        assertFalse((candidate as NativeMeasuredConsentResult.Preview).mayIssue)
        assertEquals(NativeMeasuredConsentRefusal.CONFIRMATION_REQUIRED,
            (approvals().confirm("owner-1",plan(),1,false)
                as NativeMeasuredConsentResult.Rejected).reason)
    }

    @Test fun consentColdIssueLogAndCanonicalFinishPreserveSeconds() {
        enroll()
        val p=plan()
        val approved=approvals().confirm("owner-1",p,1,true)
        assertTrue(approved.toString(),approved is NativeMeasuredConsentResult.Approved)
        val prior=(approved as NativeMeasuredConsentResult.Approved).approval
        assertEquals(prior,approvals().currentApprovedWorkout(
            "owner-1","athlete-1","session-1"))
        assertTrue((approvals().confirm("owner-1",p,1,true)
            as NativeMeasuredConsentResult.Approved).replayed)
        assertTrue(parent().issue("owner-1","session-1")
            is NativeMeasuredWorkoutStoreResult.Durable)
        val actual=MeasuredExerciseActual("floor",
            listOf(MeasuredSideResult(MeasuredSide.BOTH,43)))
        val recorded=parent().record("owner-1","session-1",
            MeasuredWorkoutOperation.Record("record-1",0,"set-1",
                MeasuredSetOutcome.COMPLETED,actual))
        assertTrue(recorded.toString(),recorded is NativeMeasuredWorkoutStoreResult.Durable)
        val done=NativeKotlinMeasuredHistoryTransaction(
            history,parent(),fence,identity).complete("owner-1","athlete-1",
            "session-1",MeasuredWorkoutOperation.Finish("finish-1",1))
        assertTrue(done.toString(),done is NativeMeasuredFinishResult.Committed)
        val read=NativeKotlinMeasuredHistoryReadCoordinator(
            history,parent(),fence,identity).read("owner-1","athlete-1")
        assertTrue(read.toString(),read is NativeMeasuredHistoryReadResult.Review)
        val stored=(read as NativeMeasuredHistoryReadResult.Review)
            .history.verifiedMeasured.single().stored
        assertEquals(actual,(stored.journal.bySetKey.getValue("set-1")
            .effectiveRecord?.operation as MeasuredJournalOperation.Record).actual)
        assertEquals("finish-1",stored.journal.finishedByEventId)
    }

    @Test fun unissuedConsentInvalidatedByIdentityAndChangedGymSource() {
        enroll()
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Approved)
        signed=NativeKotlinOwnerIdentity("owner-1","other-athlete")
        assertNull(approvals().currentApprovedWorkout(
            "owner-1","athlete-1","session-1"))
        signed=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertNull(approvals().currentApprovedWorkout(
            "owner-1","athlete-1","session-1"))
        assertEquals(NativeMeasuredConsentRefusal.SOURCE_CHANGED,
            (approvals().preview("owner-1",plan(),1)
                as NativeMeasuredConsentResult.Rejected).reason)
        assertEquals(NativeMeasuredWorkoutStoreRefusal.APPROVAL_UNAVAILABLE,
            (parent().issue("owner-1","session-1")
                as NativeMeasuredWorkoutStoreResult.Rejected).reason)
    }

    @Test fun physicalFixtureAndCorruptConsentMustFailClosed() {
        enroll()
        val fake=plan("dead-hang").copy(sets=listOf(
            plan("dead-hang").sets.single().copy(confirmedFixtures=emptySet())))
        assertEquals(NativeMeasuredConsentRefusal.UNVERIFIED_GYM_OR_EXERCISE,
            (approvals().preview("owner-1",fake,1)
                as NativeMeasuredConsentResult.Rejected).reason)
        val honest=plan("dead-hang").copy(sets=listOf(
            plan("dead-hang").sets.single().copy(
                physicalSetupId="pullup-bar")))
        assertTrue(approvals().preview("owner-1",honest,1)
            is NativeMeasuredConsentResult.Preview)
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Approved)
        val file=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("measured-consent-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=Files.readAllBytes(file)
        bytes[20]=(bytes[20].toInt() xor 1).toByte()
        Files.write(file,bytes)
        assertNull(approvals().currentApprovedWorkout(
            "owner-1","athlete-1","session-1"))
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Rejected)
        assertArrayEquals(bytes,Files.readAllBytes(file))
    }
}
