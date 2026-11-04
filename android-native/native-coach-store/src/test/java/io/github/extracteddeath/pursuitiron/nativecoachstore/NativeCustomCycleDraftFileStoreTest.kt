package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeCustomCycleDraftFileStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun number(v: Int) = BigDecimal(v)
    private val stock = VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.SMITH_TOTAL, listOf(number(135), number(155),
            number(185), number(200)))

    private fun working(weight: Int, kind: CustomAmplifierKind? = null):
        CustomAuthoredSet = CustomAuthoredSet(
            DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                number(weight)),
            RepBand(6, 10), 2, 55, 120,
            if (kind == null) null else CustomSetAmplifier(
                kind, 8, 20,
                if (kind == CustomAmplifierKind.DROP_SET)
                    DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                        number(135))
                else null)
        )

    private fun original(): CustomTrainingCycle {
        val bench = CustomExerciseSlot("bench-1", "bench",
            ComposerRole.PRIMARY,
            listOf(working(185), working(185, CustomAmplifierKind.DROP_SET)),
            attachmentId = "straight-bar", supersetGroup = "pair",
            pinned = true, coachNote = "Keep a controlled eccentric")
        val pullup = CustomExerciseSlot("pull-1", "pullup",
            ComposerRole.SECONDARY, listOf(CustomAuthoredSet(
                DraftSetType.WORKING, DraftSetLoad.ExplicitBodyweight,
                RepBand(5, 8), null, 60, 150)),
            supersetGroup = "pair", pinned = false)
        val base = CustomTrainingBlock(
            "base", "My hypertrophy", TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING, 4,
            CustomSplitIdentity.Freeform("Chest / Back"),
            listOf(CustomTrainingDay(0, "Custom upper",
                ComposerSessionPurpose.HYPERTROPHY, listOf(bench, pullup))))
        val peak = CustomTrainingBlock(
            "strength", "My strength phase", TrainingPhase.STRENGTH,
            CustomBlockPurpose.TRAINING, 3,
            CustomSplitIdentity.Freeform("Strength A"),
            listOf(CustomTrainingDay(2, "Strength",
                ComposerSessionPurpose.STRENGTH_SKILL,
                listOf(bench.copy(slotId = "strength-bench",
                    supersetGroup = null, sets = listOf(working(200)))))))
        return CustomTrainingCycle("cycle-1", 1, "athlete-1",
            AthleteTrainingGoal.POWERBUILDING, "My actual program",
            listOf(base, peak),
            mapOf("base" to listOf(CustomWeekSlotOverride(
                3, 0, "bench-1", listOf(working(200))))),
            "My authored notes must survive cold launch")
    }

    private var signedIn: NativeKotlinOwnerIdentity? =
        NativeKotlinOwnerIdentity("owner-1", "athlete-1")
    private fun repository() = NativeCustomCycleDraftFileStore(
        temp.root.toPath(),
        NativeKotlinApprovalRevisionFence(temp.root.toPath()),
        NativeKotlinCurrentIdentity { signedIn })
    private fun saved(revision: Long) = NativeCustomDraftWriteResult.Committed(revision)

    @Test fun codecRoundTripPreservesFullCustomCycleAndPartialBlankDraft() {
        val original = original()
        val roundtrip = NativeCustomCycleDraftCodec.decode(
            NativeCustomCycleDraftCodec.encode(original))
        assertEquals(original, roundtrip)
        assertEquals(CustomAmplifierKind.DROP_SET, roundtrip.blocks.first()
            .days.first().slots.first().sets.last().amplifier?.kind)
        assertEquals(3, roundtrip.weekOverrides.getValue("base").single().weekInBlock)
        assertFalse(CustomTrainingCycleDomain.review(roundtrip, listOf(
            CustomExerciseAvailability("bench", stock, false,
                setOf("straight-bar")),
            CustomExerciseAvailability("pullup", null, true)
        )).canIssueWorkout)
        assertThrows(UnsupportedOperationException::class.java) {
            (roundtrip.blocks as MutableList).clear()
        }
        val blank = CustomCycleBuilderDomain.blank(
            "empty", "athlete-1", AthleteTrainingGoal.STRENGTH,
            "My draft", "start", 4, TrainingPhase.STRENGTH,
            CustomSplitIdentity.Freeform("Custom rotation"))!!
        assertEquals(blank, NativeCustomCycleDraftCodec.decode(
            NativeCustomCycleDraftCodec.encode(blank)))
        assertNull(CustomTrainingCycleDomain.review(blank, emptyList()).verifiedDraft)
    }

    @Test fun nativeOwnedDraftSurvivesColdOpenAndRevisionedEdits() {
        val draft = original()
        assertEquals(NativeCustomDraftReadResult.Missing,
            repository().read("owner-1", "cycle-1"))
        assertEquals(saved(1), repository().save("owner-1", "cycle-1", 0, draft))
        assertEquals(NativeCustomDraftReadResult.Found(draft),
            repository().read("owner-1", "cycle-1"))
        val change = draft.copy(revision = 2,
            title = "Edited on my phone",
            authorNote = "New authored coaching priorities")
        assertEquals(saved(2), repository().save("owner-1", "cycle-1", 1, change))
        assertEquals(NativeCustomDraftReadResult.Found(change),
            repository().read("owner-1", "cycle-1"))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            repository().save("owner-1", "cycle-1", 1, change))
        assertEquals(NativeCustomDraftReadResult.Found(change),
            repository().read("owner-1", "cycle-1"))
    }

    @Test fun deletionIsTombstonedAndStaleAutosaveCannotResurrect() {
        val draft = original()
        assertEquals(saved(1), repository().save("owner-1", "cycle-1", 0, draft))
        assertEquals(saved(2), repository().delete("owner-1", "cycle-1", 1))
        assertEquals(NativeCustomDraftReadResult.Deleted(2),
            repository().read("owner-1", "cycle-1"))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            repository().save("owner-1", "cycle-1", 1,
                draft.copy(revision = 2)))
        assertEquals(NativeCustomDraftReadResult.Deleted(2),
            repository().read("owner-1", "cycle-1"))
    }

    @Test fun accountAndAthleteBoundariesCannotReadOrOverwriteDraft() {
        val draft = original()
        assertEquals(saved(1), repository().save("owner-1", "cycle-1", 0, draft))
        signedIn = NativeKotlinOwnerIdentity("owner-2", "athlete-1")
        assertEquals(NativeCustomDraftReadResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            repository().read("owner-1", "cycle-1"))
        signedIn = NativeKotlinOwnerIdentity("owner-1", "other-athlete")
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            repository().save("owner-1", "cycle-1", 1,
                draft.copy(revision = 2)))
        assertEquals(NativeCustomDraftReadResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            repository().read("owner-1", "cycle-1"))
        signedIn = null
        assertEquals(NativeCustomDraftReadResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            repository().read("owner-1", "cycle-1"))
        signedIn = NativeKotlinOwnerIdentity("owner-1", "athlete-1")
        assertEquals(NativeCustomDraftReadResult.Found(draft),
            repository().read("owner-1", "cycle-1"))
    }

    @Test fun corruptDraftFailsClosedWithoutDiscardingHistoricalData() {
        assertEquals(saved(1), repository().save("owner-1", "cycle-1", 0, original()))
        val paths = Files.list(temp.root.toPath()).use { stream ->
            stream.filter { it.fileName.toString().startsWith("draft-") &&
                it.fileName.toString().endsWith(".bin") }
                .toArray().map { it as java.nio.file.Path }
        }
        assertEquals(1, paths.size)
        val path = paths.single()
        val data = Files.readAllBytes(path)
        data[data.size / 2] = (data[data.size / 2].toInt() xor 1).toByte()
        Files.write(path, data)
        assertEquals(NativeCustomDraftReadResult.Rejected(
            NativeCustomDraftRefusal.CORRUPT_RECORD),
            repository().read("owner-1", "cycle-1"))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.CORRUPT_RECORD),
            repository().save("owner-1", "cycle-1", 1,
                original().copy(revision = 2)))
        assertArrayEquals(data, Files.readAllBytes(path))
    }

    @Test fun malformedOrCrossCycleDraftCannotBeWritten() {
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.INVALID_DRAFT),
            repository().save("owner-1", "other", 0, original()))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.INVALID_DRAFT),
            repository().save("owner-1", "cycle-1", 0,
                original().copy(revision = 2)))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            repository().delete("owner-1", "cycle-1", 0))
        assertEquals(NativeCustomDraftReadResult.Missing,
            repository().read("owner-1", "cycle-1"))
    }
}
