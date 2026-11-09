package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.StarterExerciseTranche
import org.junit.Assert.*
import org.junit.Test

class NativePinnedSetupFieldDomainTest {
    private fun movement(id:String)=StarterExerciseTranche.movements()
        .first {it.exerciseId==id}

    @Test fun sourceAuthoredEquipmentProducesSpecificFieldsNotNameGuesswork() {
        val extension=NativePinnedSetupFieldDomain.fields(
            movement("leg_extension"),emptyMap())
        assertEquals(listOf("seat","roller_position","range_stop","other"),
            extension.map{it.key})
        assertTrue(extension.none{it.savedEarlier})
        val curl=NativePinnedSetupFieldDomain.fields(
            movement("seated_leg_curl"),emptyMap())
        assertEquals(listOf("seat","thigh_pad","ankle_pad","other"),
            curl.map{it.key})
        assertFalse(curl.any{it.key=="roller_position"})
        val incline=NativePinnedSetupFieldDomain.fields(
            movement("incline_db_press"),emptyMap())
        assertTrue(incline.any{it.key=="bench_angle" && !it.savedEarlier})
        val bench=NativePinnedSetupFieldDomain.fields(
            movement("barbell_bench"),emptyMap())
        assertTrue(bench.any{it.key=="safeties"})
        assertTrue(bench.any{it.key=="grip"})
        assertFalse(bench.any{it.key=="bench_angle"})
    }

    @Test fun oldOrphanKeysRemainVisibleEvenWhenCurrentMachineDoesNotUseThem() {
        val saved=linkedMapOf(
            "grip" to "Pinky on ring",
            "oldPadSetting" to "3",
            "legacyRackHeight" to "7"
        )
        val fields=NativePinnedSetupFieldDomain.fields(
            movement("leg_extension"),saved)
        // Grip was saved on a different exercise/equipment context. It too
        // becomes a preserved orphan rather than a silent deletion.
        assertEquals(listOf("grip","legacyRackHeight","oldPadSetting"),
            fields.filter{it.savedEarlier}.map{it.key})
        assertTrue(fields.filter{it.savedEarlier}.all{
            it.label.startsWith("Saved earlier")
        })
        assertTrue(fields.any{it.key=="seat" && !it.savedEarlier})
        // The builder NEVER silently mutates or filters stored keys.
        assertEquals(3,saved.size)
        assertEquals("3",saved["oldPadSetting"])
        val narrowed=NativePinnedSetupFieldDomain.fields(
            movement("leg_extension"),saved-"oldPadSetting")
        assertFalse(narrowed.any{it.key=="oldPadSetting"})
    }

    @Test fun noVerifiedCatalogUsesOriginalGenericFieldsAndFlagsOrphans() {
        val fields=NativePinnedSetupFieldDomain.fields(null,mapOf(
            "grip" to "Normal","legacySetting" to "6"))
        assertEquals(listOf("attachment","seat","grip","other"),
            fields.filterNot{it.savedEarlier}.map{it.key})
        assertEquals(listOf("legacySetting"),
            fields.filter{it.savedEarlier}.map{it.key})
        val notAccepted=NativePinnedSetupFieldDomain.fields(null,mapOf(
            "__proto__" to "unsafe"))
        assertFalse(notAccepted.any{it.key=="__proto__"})
    }

    @Test fun cableAndAttachmentDescriptorsDeriveFromActualReviewedFieldsOnly() {
        val cable=movement("barbell_bench").copy(
            equipmentId="cable-machine",attachmentId="rope",
            requiredFacilityIds=setOf("incline-bench"))
        val source=NativePinnedSetupFieldDomain.fields(cable,emptyMap())
        assertTrue(source.any{it.key=="pulley_position"})
        assertTrue(source.any{it.key=="attachment"})
        assertTrue(source.any{it.key=="bench_angle"})
        assertFalse(source.any{it.key=="roller_position"})
    }
}
