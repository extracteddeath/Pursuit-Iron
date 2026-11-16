package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import org.junit.Assert.*
import org.junit.Test

internal class NativeKotlinExerciseLibraryPresentationTest:NativeKotlinProgramFilesFixture() {
    private fun records(value:NativeCustomAuthoringSnapshot=source())=
        nativeExerciseLibraryRecords(value,::nativeLibraryWords,::nativeLibraryWords,::nativeLibraryWords)
    private fun find(query:String="",muscle:TrainingMuscle?=null,gear:String?=null,
        mode:NativeLibraryAvailability=NativeLibraryAvailability.ALL,excluded:Set<String> = emptySet(),
        pattern:ComposerPattern?=null,role:ComposerRole?=null)=
        filterNativeExerciseLibrary(records(),query,muscle,gear,mode,excluded,pattern,role)

    @Test fun entireCatalogIsSearchableBeforePagingAndPhysicalEligibilityIsNotInferred() {
        val all=records()
        val expected=StarterExerciseTranche.entries().size
        assertEquals(expected,all.size)
        assertEquals(expected,all.map{it.movement.exerciseId}.distinct().size)
        assertTrue(find("landmine").map{it.movement.exerciseId}.containsAll(setOf("meadows-row","landmine-press","lm-squat")))
        assertTrue(find("landmine").none{it.eligible})
        assertTrue(find("landmine",mode=NativeLibraryAvailability.ELIGIBLE).isEmpty())
        assertTrue(all.single{it.movement.exerciseId=="db_curl"}.eligible)
        assertFalse(all.single{it.movement.exerciseId=="tibialis-raise"}.eligible)
        assertTrue(find("definitely-no-such-movement").isEmpty())
    }

    @Test fun namesMusclesEquipmentFixturesAndVerifiedAttachmentIdsShareTokenSearch() {
        val dual=find("dual rope")
        assertTrue(dual.isNotEmpty())
        assertTrue(dual.all{it.movement.attachmentId=="dual-rope"})
        val cable=find("cable biceps")
        assertTrue(cable.isNotEmpty())
        assertTrue(cable.all{it.movement.equipmentId=="cable-station" && it.movement.credits.any{c->c.muscle==TrainingMuscle.BICEPS}})
        assertTrue(find("incline bench").any{"incline-bench" in it.movement.requiredFacilityIds})
        assertTrue(find(gear="incline-bench").all{"incline-bench" in it.movement.requiredFacilityIds})
        assertTrue(find(gear=nativeLibraryBodyweight).all{it.movement.equipmentId==null})
        assertTrue(find(gear="dual-rope").all{it.movement.attachmentId=="dual-rope"})
    }

    @Test fun separateMusclesAndDraftExclusionsAreNeverCombinedIntoGenericGroups() {
        val flex=find(muscle=TrainingMuscle.WRIST_FLEXORS).map{it.movement.exerciseId}.toSet()
        val ext=find(muscle=TrainingMuscle.WRIST_EXTENSORS).map{it.movement.exerciseId}.toSet()
        assertTrue("wrist-curl" in flex);assertFalse("reverse-wrist-curl" in flex)
        assertTrue("reverse-wrist-curl" in ext);assertFalse("wrist-curl" in ext)
        assertTrue(find(muscle=TrainingMuscle.BRACHIALIS).none{it.movement.exerciseId=="db_curl"})
        val excluded=setOf("db_curl","retired-lift")
        assertEquals(listOf("db_curl"),find(mode=NativeLibraryAvailability.EXCLUDED,excluded=excluded).map{it.movement.exerciseId})
        assertTrue(find(mode=NativeLibraryAvailability.ELIGIBLE,excluded=excluded).none{it.movement.exerciseId=="db_curl"})
        assertTrue(records().single{it.movement.exerciseId=="db_curl"}.eligible)
    }

    @Test fun patternAndRoleNarrowSourceResultsWithoutChangingEligibility() {
        val presses=find(pattern=ComposerPattern.HORIZONTAL_PRESS)
        assertTrue(presses.isNotEmpty())
        assertTrue(presses.all{it.movement.pattern==ComposerPattern.HORIZONTAL_PRESS})
        val supporting=find(pattern=ComposerPattern.HORIZONTAL_PRESS,role=ComposerRole.SECONDARY)
        assertTrue(supporting.isNotEmpty())
        assertTrue(supporting.all{it.movement.role==ComposerRole.SECONDARY &&
            it.movement.pattern==ComposerPattern.HORIZONTAL_PRESS})
        val incline=find("incline",pattern=ComposerPattern.HORIZONTAL_PRESS,
            role=ComposerRole.SECONDARY)
        assertTrue(incline.isNotEmpty())
        assertTrue(incline.all{it.searchText.contains("incline",ignoreCase=true)})
        assertTrue(find(pattern=ComposerPattern.HORIZONTAL_PRESS,gear="dual-rope").isEmpty())
        val excluded=setOf("db_curl")
        assertTrue(find(pattern=ComposerPattern.HORIZONTAL_PRESS,
            mode=NativeLibraryAvailability.EXCLUDED,excluded=excluded).isEmpty())
        assertTrue(find(pattern=ComposerPattern.ISOLATION,
            mode=NativeLibraryAvailability.ELIGIBLE,excluded=excluded)
            .none{it.movement.exerciseId in excluded})
        assertTrue(presses.all {record->
            record.eligible==records().single{it.movement.exerciseId==record.movement.exerciseId}.eligible
        })
    }

    @Test fun corruptOrUnresolvedCatalogsCannotAcquireAnEligibilityClaim() {
        val original=source()
        val duplicate=original.copy(catalog=original.catalog+original.catalog.first())
        assertTrue(records(duplicate).isEmpty())
        assertTrue(records(original.copy(athleteId="someone-else")).isEmpty())
        assertTrue(filterNativeExerciseLibrary(records()+records().first(),"",null,null,
            NativeLibraryAvailability.ALL,emptySet()).isEmpty())
    }

    @Test fun actualSavedExclusionAndUndoChangeOnlyTheNativeSourceAndSurviveColdReads() {
        val saved=seed()
        val before=hashes()
        val source=saved.source
        val request=NativeGymExerciseExclusionChange("owner-1","athlete-1",source.revision,
            emptySet(),setOf("db_curl"))
        val proposal=NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(source,request) as NativeGymCatalogEdit.Proposed
        assertEquals(NativeAuthoringSourceWrite.Committed(2),sources().publish("owner-1",1,proposal.source))
        val cold=(sources().read("owner-1") as NativeAuthoringSourceRead.Found).source
        assertFalse(records(cold).single{it.movement.exerciseId=="db_curl"}.eligible)
        val receipt=NativeKotlinExerciseExclusionCommit(request,cold)
        assertTrue(receipt.matches(cold))
        assertFalse(receipt.matches(source))
        assertFalse(receipt.matches(cold.copy(setup=source.setup)))
        assertFalse(receipt.matches(cold.copy(catalog=cold.catalog.reversed())))
        val undo=NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(cold,receipt.undoRequest()) as NativeGymCatalogEdit.Proposed
        assertEquals(NativeAuthoringSourceWrite.Committed(3),sources().publish("owner-1",2,undo.source))
        val restored=(sources().read("owner-1") as NativeAuthoringSourceRead.Found).source
        assertEquals(source.copy(revision=3),restored)
        assertTrue(records(restored).single{it.movement.exerciseId=="db_curl"}.eligible)
        assertEquals(1,before.keys.count{before[it]!=hashes()[it]})
        assertEquals(NativeCustomDraftReadResult.Found(saved.change.draft),drafts().read("owner-1","cycle-b"))
        assertFalse(receipt.matches(restored))
    }

    @Test fun anActualCompetingSourceWriteRefusesAStaleLibrarySaveWithoutTouchingPrograms() {
        val saved=seed()
        val source=saved.source
        val intent=NativeGymExerciseExclusionChange("owner-1","athlete-1",1,emptySet(),setOf("db_curl"))
        val pending=(NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(source,intent) as NativeGymCatalogEdit.Proposed).source
        val other=(NativeKotlinGymCatalogEditingDomain.setFacilitiesAndAttachments(source,1,setOf("incline-bench"),emptySet()) as NativeGymCatalogEdit.Proposed).source
        assertEquals(NativeAuthoringSourceWrite.Committed(2),sources().publish("owner-1",1,other))
        val before=hashes()
        assertEquals(NativeAuthoringSourceWrite.Rejected(NativeAuthoringSourceRefusal.STALE_REVISION),
            sources().publish("owner-1",1,pending))
        assertEquals(NativeAuthoringSourceRead.Found(other),sources().read("owner-1"))
        assertEquals(before,hashes())
        assertEquals(NativeCustomDraftReadResult.Found(saved.change.draft),drafts().read("owner-1","cycle-b"))
    }

    @Test fun browsingAndFilteringAllSourceFactsNeverWritesNativeFiles() {
        val saved=seed()
        val before=hashes()
        val all=records(saved.source)
        for(muscle in TrainingMuscle.entries)
            filterNativeExerciseLibrary(all,"",muscle,null,NativeLibraryAvailability.ALL,emptySet())
        for(mode in NativeLibraryAvailability.entries)
            filterNativeExerciseLibrary(all,"rope",null,null,mode,setOf("db_curl"))
        assertEquals(before,hashes())
    }
}
