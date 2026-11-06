package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinCoachAssistanceSettingsStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private val root get()=temp.root.toPath()
    private var active:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{active}
    private val fence by lazy{NativeKotlinApprovalRevisionFence(root)}
    private fun store()=NativeKotlinCoachAssistanceSettingsStore(root,fence,identity)
    private fun read()=store().read("owner-1")
    private fun saved(revision:Long,policy:CoachAssistancePolicy)=
        NativeCoachSettingsWrite.Saved(NativeCoachAssistanceSettings(
            "owner-1","athlete-1",revision,policy))

    @Test fun defaultAndManualModeSurviveColdStartWithOptimisticRevision() {
        assertEquals(NativeCoachSettingsRead.Current(NativeCoachAssistanceSettings(
            "owner-1","athlete-1",0,CoachAssistancePolicy.STANDARD)),read())
        assertEquals(saved(1,CoachAssistancePolicy.MANUAL),
            store().save("owner-1",0,CoachAssistancePolicy.MANUAL))
        val recovered=read() as NativeCoachSettingsRead.Current
        assertEquals(1,recovered.settings.revision)
        assertTrue(recovered.settings.policy.manualOnly)
        assertFalse(recovered.settings.policy.offersProgression)
        assertFalse(recovered.settings.policy.offersFatigueAdvice)
        assertEquals(NativeCoachSettingsWrite.Rejected(
            NativeCoachSettingsRefusal.REVISION_CONFLICT),
            store().save("owner-1",0,CoachAssistancePolicy.STANDARD))
        assertEquals(saved(2,CoachAssistancePolicy.PROGRESSION_ONLY),
            store().save("owner-1",1,CoachAssistancePolicy.PROGRESSION_ONLY))
        assertTrue((read() as NativeCoachSettingsRead.Current)
            .settings.policy.offersProgression)
        assertFalse((read() as NativeCoachSettingsRead.Current)
            .settings.policy.offersFatigueAdvice)
        assertEquals(saved(3,CoachAssistancePolicy.STANDARD),
            store().save("owner-1",2,CoachAssistancePolicy.STANDARD))
    }

    @Test fun togglesAreIndependentAndManualModeCannotOverrideMovementRestrictions() {
        val manual=CoachAssistancePolicy.MANUAL
        val progression=CoachAssistancePolicy.PROGRESSION_ONLY
        assertFalse(manual.coachingSuggestionsEnabled)
        assertEquals(CoachReadiness.NORMAL,
            progression.coachingReadiness(CoachReadiness.LOW))
        assertEquals(CoachReadiness.LOW,
            CoachAssistancePolicy.STANDARD.coachingReadiness(CoachReadiness.LOW))
        assertFalse(progression.offersFatigueAdvice)
        assertTrue(progression.offersProgression)
        assertFalse(CoachAssistancePolicy(
            coachingSuggestionsEnabled=true,fatigueAdaptationEnabled=true,
            progressionSuggestionsEnabled=false).offersProgression)
        assertFalse(CoachAssistancePolicy(
            coachingSuggestionsEnabled=false,fatigueAdaptationEnabled=true,
            progressionSuggestionsEnabled=true).offersFatigueAdvice)
    }

    @Test fun ownerMismatchAndCorruptFileCannotSilentlyRestoreCoach() {
        assertEquals(saved(1,CoachAssistancePolicy.MANUAL),
            store().save("owner-1",0,CoachAssistancePolicy.MANUAL))
        active=NativeKotlinOwnerIdentity("owner-1","another-athlete")
        assertEquals(NativeCoachSettingsRead.Rejected(
            NativeCoachSettingsRefusal.CORRUPT_OR_PENDING),read())
        active=null
        assertEquals(NativeCoachSettingsRead.Rejected(
            NativeCoachSettingsRefusal.NOT_AUTHORIZED),read())
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val path=Files.list(root).use{stream->stream.filter{
            it.fileName.toString().startsWith("coach-assistance-") &&
                it.fileName.toString().endsWith(".bin")
        }.findFirst().orElseThrow()}
        val bytes=Files.readAllBytes(path)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        Files.write(path,bytes)
        assertEquals(NativeCoachSettingsRead.Rejected(
            NativeCoachSettingsRefusal.CORRUPT_OR_PENDING),read())
        assertEquals(NativeCoachSettingsWrite.Rejected(
            NativeCoachSettingsRefusal.CORRUPT_OR_PENDING),
            store().save("owner-1",1,CoachAssistancePolicy.STANDARD))
    }

    @Test fun unresolvedAtomicWriteAndWrongOwnerNeverBecomeDefaultOn() {
        assertEquals(saved(1,CoachAssistancePolicy.MANUAL),
            store().save("owner-1",0,CoachAssistancePolicy.MANUAL))
        val file=Files.list(root).use{stream->stream.filter{
            it.fileName.toString().startsWith("coach-assistance-") &&
                it.fileName.toString().endsWith(".bin")
        }.findFirst().orElseThrow()}
        val pending=root.resolve(file.fileName.toString().removeSuffix(".bin")+
            ".pending")
        Files.write(pending,byteArrayOf(1))
        assertEquals(NativeCoachSettingsRead.Rejected(
            NativeCoachSettingsRefusal.CORRUPT_OR_PENDING),read())
        assertEquals(NativeCoachSettingsWrite.Rejected(
            NativeCoachSettingsRefusal.CORRUPT_OR_PENDING),
            store().save("owner-1",1,CoachAssistancePolicy.STANDARD))
        assertEquals(NativeCoachSettingsRead.Rejected(
            NativeCoachSettingsRefusal.NOT_AUTHORIZED),
            store().read("owner-2"))
    }
}
