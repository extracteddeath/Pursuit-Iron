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

    @Test fun measuredEntriesShareSearchPagingAndExactSourceExclusionsWithoutFakeRepMetadata() {
        val saved=seed()
        val source=saved.source
        fun all(value:NativeCustomAuthoringSnapshot)=nativeExerciseLibraryEntries(
            value,::nativeLibraryWords,::nativeLibraryWords,::nativeLibraryWords)
        val typed=MeasuredExerciseCatalogDomain.all.map{it.exerciseId}.toSet()
        val entries=all(source)
        assertEquals(source.catalog.size+14,entries.size)
        assertEquals(typed,entries.filterIsInstance<NativeMeasuredLibraryRecord>().map{it.exerciseId}.toSet())
        assertTrue(entries.filterIsInstance<NativeMeasuredLibraryRecord>().all{it.standard==null})
        assertEquals(MeasuredSessionAuthoringDomain.available(checkNotNull(source.setup.reviewed))
            .map{it.exerciseId}.toSet(),entries.filterIsInstance<NativeMeasuredLibraryRecord>()
            .filter{it.eligible}.map{it.exerciseId}.toSet())
        assertTrue(filterNativeExerciseLibrary(entries,"timed hold",null,null,
            NativeLibraryAvailability.ALL,emptySet()).any{it.exerciseId=="plank"})
        assertTrue(filterNativeExerciseLibrary(entries,"plank",TrainingMuscle.ABS,null,
            NativeLibraryAvailability.ALL,emptySet()).none{it.exerciseId in typed})
        assertTrue(all(source.copy(catalog=source.catalog+source.catalog.first().copy(
            exerciseId="plank",variantId="reserved-typed-id"))).isEmpty())
        val request=NativeGymExerciseExclusionChange(source.ownerKey,source.athleteId,
            source.revision,source.setup.reviewed!!.excludedExerciseIds,
            source.setup.reviewed!!.excludedExerciseIds+"plank")
        val proposal=NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(source,request)
            as NativeGymCatalogEdit.Proposed
        assertEquals(NativeAuthoringSourceWrite.Committed(2),sources().publish("owner-1",1,proposal.source))
        val cold=(sources().read("owner-1") as NativeAuthoringSourceRead.Found).source
        assertTrue(all(cold).single{it.exerciseId=="plank"}.savedExcluded)
        assertFalse(all(cold).single{it.exerciseId=="plank"}.eligible)
        val undo=NativeKotlinExerciseExclusionCommit(request,cold).undoRequest()
        val restored=NativeKotlinGymCatalogEditingDomain.setExerciseExclusions(cold,undo)
            as NativeGymCatalogEdit.Proposed
        assertEquals(NativeAuthoringSourceWrite.Committed(3),sources().publish("owner-1",2,restored.source))
        assertEquals(source.copy(revision=3),(sources().read("owner-1") as NativeAuthoringSourceRead.Found).source)
        assertEquals(NativeCustomDraftReadResult.Found(saved.change.draft),drafts().read("owner-1","cycle-b"))
    }

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
    @Test fun angleFacetKeepsLowRegularHighAndDeclineSeparateFromPhysicalStack() {
        val all=records()
        val low=filterNativeExerciseLibrary(all,"",null,null,
            NativeLibraryAvailability.ALL,emptySet(),
            angle=NativeBenchAngle.LOW_INCLINE)
        val high=filterNativeExerciseLibrary(all,"",null,null,
            NativeLibraryAvailability.ALL,emptySet(),
            angle=NativeBenchAngle.HIGH_INCLINE)
        val regular=filterNativeExerciseLibrary(all,"",null,null,
            NativeLibraryAvailability.ALL,emptySet(),
            angle=NativeBenchAngle.INCLINE)
        val decline=filterNativeExerciseLibrary(all,"",null,null,
            NativeLibraryAvailability.ALL,emptySet(),
            angle=NativeBenchAngle.DECLINE)
        assertTrue(low.any{it.movement.exerciseId=="low-inc-bb-bench"})
        assertTrue(high.any{it.movement.exerciseId=="high-inc-bb-bench"})
        assertTrue(regular.any{it.movement.exerciseId=="incline_db_press"})
        assertTrue(decline.any{it.movement.exerciseId=="decline-bench"})
        assertTrue(low.none{it.angle==NativeBenchAngle.HIGH_INCLINE})
        assertFalse("low-to-high fly should not be treated as an incline bench",
            all.single{it.movement.exerciseId=="inc-cable-fly"}.angle!=null)
        assertNull(nativeBenchAngle("rope-cable-row"))
    }

    @Test fun benchAngleRequiresVerifiedBenchFixtureAndExcludesBodyPosition() {
        val movements=StarterExerciseTranche.movements()
        val byId=movements.associateBy { it.exerciseId }
        val records=records()
        assertEquals(NativeBenchAngle.FLAT,
            records.single { it.movement.exerciseId=="barbell_bench" }.angle)
        assertEquals(NativeBenchAngle.HIGH_INCLINE,
            records.single { it.movement.exerciseId=="high-inc-bb-bench" }.angle)
        assertEquals(NativeBenchAngle.LOW_INCLINE,
            records.single { it.movement.exerciseId=="low-inc-bb-bench" }.angle)
        assertNull("An incline push-up is NOT a bench setting",
            records.single { it.movement.exerciseId=="incline-pushup" }.angle)
        assertNull("A decline push-up is NOT a decline bench setting",
            records.single { it.movement.exerciseId=="decline-pushup" }.angle)
        assertNull("A cable lateral raise only uses a seated support",
            records.single { it.movement.exerciseId=="seated-cable-lat-raise" }.angle)
        assertNull(nativeBenchAngle("high-incline-free-text",emptySet(),
            "bench_press"))
        assertNull(nativeBenchAngle("decline-bodyweight",
            setOf("flat-bench"),"pushup"))
        assertNull(nativeReviewedChoiceAngle("unknown-custom-incline",byId))
        assertEquals(NativeBenchAngle.INCLINE,
            nativeReviewedChoiceAngle("incline_db_press",byId))
        assertEquals(NativeBenchAngle.FLAT,
            nativeReviewedChoiceAngle("barbell_bench",byId))
    }

    @Test fun attachmentAndFamilyFiltersPreserveExactMovementIdentity() {
        val all=records()
        val id="v-bar-pushdown"
        val original=all.single{it.movement.exerciseId==id}
        val attachment=checkNotNull(original.movement.attachmentId)
        val family=original.movement.familyId
        val matches=filterNativeExerciseLibrary(all,"",null,null,
            NativeLibraryAvailability.ALL,emptySet(),
            familyId=family,attachmentId=attachment)
        assertTrue(matches.any{it.movement.exerciseId==id})
        assertTrue(matches.all{it.movement.familyId==family &&
            it.movement.attachmentId==attachment})
        assertTrue(matches.all{it.movement.exerciseId!=
            "dual-rope-pushdown"})
        assertTrue(nativeExerciseVariantCategory(original.movement)
            .contains(kotlinGymAttachmentLabel(attachment)))
        assertTrue(all.single{it.movement.exerciseId=="db_curl"}
            .variantDescription.contains("Accessory"))
    }

    @Test fun siblingBrowserIncludesUnverifiedVariantsButDoesNotAuthorizeThem() {
        val saved=seed()
        val before=hashes()
        val source=records(saved.source)
        val siblings=nativeExerciseFamilyVariants(source,"barbell_bench")
        assertTrue(siblings.any{it.movement.exerciseId=="low-inc-bb-bench"})
        assertTrue(siblings.any{it.movement.exerciseId=="high-inc-bb-bench"})
        assertTrue(siblings.none{it.movement.exerciseId=="barbell_bench"})
        assertTrue(siblings.any{!it.eligible})
        assertTrue(nativeExerciseFamilyVariants(source,"does-not-exist").isEmpty())
        assertTrue(nativeExerciseFamilyVariants(source+source.first(),"barbell_bench").isEmpty())
        assertEquals(before,hashes())
    }

}
