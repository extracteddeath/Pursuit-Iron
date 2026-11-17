package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

/** Generated Composer swaps must have the same factual variant discovery
 * as custom Train, without a new engine alternative or loading authority. */
class NativeKotlinComposerVariantFacetTest {
    private fun candidate(id:String)=KotlinComposerSwapChoice(
        ComposerExerciseOption(
            exerciseId=id,pattern=ComposerPattern.ISOLATION,
            allowedFocus=setOf(ComposerFocus.UPPER),
            equipment=ComposerEquipment.Bodyweight,
            credits=emptyList(),workSeconds=45,restSeconds=90,
            minimumSetsPerVisit=1,maximumSetsPerVisit=3,
            maximumVisits=3,requiredVisits=0,minimumCalendarDayGap=1
        ),samePattern=true,sharedReviewedMuscleCount=1,
        explanation="Owner-verified choice")
    private val options=listOf(
        candidate("high-inc-bb-bench"),
        candidate("low-inc-bb-bench"),
        candidate("v-bar-pushdown"),
        candidate("dual-rope-pushdown"),
        candidate("an-athlete-custom-unmapped")
    )
    private val catalog=nativeComposerVariantMetadata(options)

    @Test fun exactInclineFilterNeverSubstitutesLowForHighOrUnknownCustom() {
        assertEquals(listOf("high-inc-bb-bench"),filterNativeComposerSwapVariants(
            options,catalog,NativeBenchAngle.HIGH_INCLINE)
            .map{it.option.exerciseId})
        assertEquals(listOf("low-inc-bb-bench"),filterNativeComposerSwapVariants(
            options,catalog,NativeBenchAngle.LOW_INCLINE)
            .map{it.option.exerciseId})
        assertFalse(catalog.containsKey("an-athlete-custom-unmapped"))
        assertNull(catalog["v-bar-pushdown"]?.let{
            nativeBenchAngle(it.variantId,it.requiredFacilityIds)})
    }

    @Test fun exactAttachmentsPreserveRankedOrderAndDoNotExpandApprovedChoices() {
        assertEquals(listOf("dual-rope-pushdown"),filterNativeComposerSwapVariants(
            options,catalog,attachmentId="dual-rope")
            .map{it.option.exerciseId})
        assertEquals(listOf("v-bar-pushdown"),filterNativeComposerSwapVariants(
            options,catalog,attachmentId="v-bar")
            .map{it.option.exerciseId})
        assertTrue(filterNativeComposerSwapVariants(options,catalog,
            NativeBenchAngle.HIGH_INCLINE,"v-bar").isEmpty())
        assertEquals(options,filterNativeComposerSwapVariants(options,catalog))
        assertEquals(options,options.toList())
        assertTrue(catalog.keys.all{key->options.any{it.option.exerciseId==key}})
    }

    @Test fun sourceDisplayNeverFabricatesPhysicalDegreesOrTransfersLoad() {
        val low=checkNotNull(catalog["low-inc-bb-bench"])
        val high=checkNotNull(catalog["high-inc-bb-bench"])
        val rope=checkNotNull(catalog["dual-rope-pushdown"])
        assertTrue(nativeExerciseVariantCategory(low).contains("Low incline"))
        assertTrue(nativeExerciseVariantCategory(high).contains("High incline"))
        assertTrue(nativeExerciseVariantCategory(rope).contains("Dual rope"))
        assertFalse(nativeExerciseVariantCategory(low).contains("15°"))
        assertFalse(nativeExerciseVariantCategory(high).contains("45°"))
        assertEquals("dual-rope",rope.attachmentId)
    }
}
