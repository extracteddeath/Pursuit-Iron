package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class NativeKotlinExerciseExclusionEditingTest {
    private fun source():NativeCustomAuthoringSnapshot {
        val bytes=checkNotNull(javaClass.getResourceAsStream("/legacy-smith-totals-source.base64")).use{it.readBytes()}
        return NativeKotlinCustomAuthoringSourceCodec.decode(Base64.getMimeDecoder().decode(bytes))
    }
    private fun request(source:NativeCustomAuthoringSnapshot,next:Set<String>)=
        NativeGymExerciseExclusionChange(source.ownerKey,source.athleteId,source.revision,
            checkNotNull(source.setup.reviewed).excludedExerciseIds,next)
    private fun refusal(source:NativeCustomAuthoringSnapshot,change:NativeGymExerciseExclusionChange)=
        (NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(source,change) as NativeGymCatalogEdit.Rejected).reason

    @Test fun onePreferenceEditRetainsEveryOtherProfileFieldAllStockAndTheExactCatalog() {
        val before=source()
        val profile=checkNotNull(before.setup.reviewed)
        val id=before.catalog.first().exerciseId
        val intent=request(before,profile.excludedExerciseIds+id)
        val changed=(NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(before,intent) as NativeGymCatalogEdit.Proposed).source
        assertEquals(before.revision+1,changed.revision)
        assertEquals(before.ownerKey,changed.ownerKey)
        assertEquals(before.athleteId,changed.athleteId)
        assertEquals(before.catalog,changed.catalog)
        assertEquals(profile.copy(excludedExerciseIds=profile.excludedExerciseIds+id),changed.setup.reviewed)
        assertEquals(before,NativeKotlinCustomAuthoringSourceCodec.decode(NativeKotlinCustomAuthoringSourceCodec.encode(before)))
        assertEquals(changed,NativeKotlinCustomAuthoringSourceCodec.decode(NativeKotlinCustomAuthoringSourceCodec.encode(changed)))
    }

    @Test fun staleForeignNoOpAndNewUnknownExclusionsNeverProduceAProposal() {
        val source=source()
        val intent=request(source,checkNotNull(source.setup.reviewed).excludedExerciseIds+source.catalog.first().exerciseId)
        assertEquals(NativeGymCatalogEditRefusal.STALE_SOURCE,refusal(source,intent.copy(sourceRevision=source.revision+1)))
        assertEquals(NativeGymCatalogEditRefusal.STALE_SOURCE,refusal(source,intent.copy(previous=setOf("forged"))))
        assertEquals(NativeGymCatalogEditRefusal.ATHLETE_CONFLICT,refusal(source,intent.copy(ownerKey="other-owner")))
        assertEquals(NativeGymCatalogEditRefusal.ATHLETE_CONFLICT,refusal(source,intent.copy(athleteId="other-athlete")))
        assertEquals(NativeGymCatalogEditRefusal.INVALID_EXCLUSIONS,refusal(source,intent.copy(next=intent.previous)))
        assertEquals(NativeGymCatalogEditRefusal.INVALID_EXCLUSIONS,refusal(source,intent.copy(next=intent.next+"not-a-catalog-entry")))
    }

    @Test fun existingUnmappedExclusionsAreKeptAndProfileLimitsRemainCanonical() {
        val initial=source()
        val profile=checkNotNull(initial.setup.reviewed)
        val before=initial.copy(setup=initial.setup.copy(reviewed=profile.copy(excludedExerciseIds=setOf("retired-exercise"))))
        val request=request(before,setOf("retired-exercise",before.catalog.first().exerciseId))
        assertTrue(NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(before,request) is NativeGymCatalogEdit.Proposed)
        assertEquals(NativeGymCatalogEditRefusal.INVALID_EXCLUSIONS,refusal(before,request.copy(next=request.next-"retired-exercise")))
        val full=initial.copy(catalog=StarterExerciseTranche.movements())
        val tooMany=full.catalog.take(CoachAthleteSetupDomain.MAX_EXCLUDED_EXERCISES+1).map{it.exerciseId}.toSet()
        assertEquals(NativeGymCatalogEditRefusal.INVALID_EXCLUSIONS,refusal(full,request(full,tooMany)))
    }
}
