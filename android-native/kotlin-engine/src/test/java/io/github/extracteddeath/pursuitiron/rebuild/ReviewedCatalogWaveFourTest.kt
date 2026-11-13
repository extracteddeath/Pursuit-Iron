package io.github.extracteddeath.pursuitiron.rebuild
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Source-linked catalog/stock gate; never claims physiology or superiority. */
class ReviewedCatalogWaveFourTest {
    private val all=StarterExerciseTranche.entries()
    private val wave=all.drop(146).take(18)
    private val expected=listOf(
        "rope-hammer-curl",
        "v-bar-pushdown",
        "straight-bar-pushdown",
        "dual-rope-pushdown",
        "single-pushdown",
        "cable-rear-fly",
        "single-cable-rear-fly",
        "cable-front-raise",
        "pull-through",
        "pallof",
        "woodchopper",
        "zottman-curl",
        "tate-press",
        "tricep-kickback",
        "renegade-row",
        "reverse-lunge",
        "curtsy-lunge",
        "db-upright-row"
    )
    private fun n(s:String)=BigDecimal(s)

    @Test fun preserveSeparateAttachmentsAndMuscleCredits() {
        assertEquals(178,all.size)
        assertEquals(expected,wave.map{it.legacyCuratedId})
        assertEquals(all.size,all.map{it.legacyCuratedId}.distinct().size)
        assertEquals(all.size,all.map{
            it.movement.familyId to it.movement.variantId}.distinct().size)
        assertTrue(wave.all{!it.mayTransferPreviousLoad &&
            it.movement.creditStatus==CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL})
        val byId=wave.associateBy{it.legacyCuratedId}
        assertEquals("dual-rope",byId.getValue("dual-rope-pushdown").movement.attachmentId)
        assertEquals("v-bar",byId.getValue("v-bar-pushdown").movement.attachmentId)
        assertEquals("single-handle",byId.getValue("single-pushdown").movement.attachmentId)
        assertEquals(setOf(TrainingMuscle.BRACHIALIS),
            byId.getValue("rope-hammer-curl").movement.credits.filter {
                it.role==MuscleCreditRole.DIRECT}.map{it.muscle}.toSet())
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,
            byId.getValue("zottman-curl").movement.expectedLoadBasis)
    }

    @Test fun missingStockOrAttachmentBlocksCableLoads() {
        val template=ComposerRequest(ComposerSplit.FULL_BODY,
            TrainingPhase.HYPERTROPHY,listOf(0,2,4).map{
                ComposerDay(it,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,90,null,60))
            },TrainingMuscle.entries.map{
                DeclaredMuscleDoseBounds(it,BigDecimal.ZERO,n("12"),n("20"),0)
            },emptyList(),4,1)
        val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.CABLE_STACK,
            listOf(n("20"),n("25"),n("30")))
        for(entry in wave.filter{it.movement.attachmentId!=null}) {
            val choice=CatalogExerciseChoice(entry.legacyCuratedId,
                n("25"),90,120,2,3,2,1,1,5)
            val absent=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(template,listOf(choice),
                    CatalogGym(emptyMap(),emptySet())))
            assertTrue(entry.legacyCuratedId,absent.issues.any{
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE})
            val wrong=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(template,listOf(choice),
                    CatalogGym(mapOf("cable-station" to stock),
                        setOf("wrong-handle"))))
            assertTrue(entry.legacyCuratedId,wrong.issues.any{
                it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE})
            assertFalse(wrong.mayIssueWorkout)
            val correct=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(template,listOf(choice),
                    CatalogGym(mapOf("cable-station" to stock),
                        setOf(checkNotNull(entry.movement.attachmentId)))))
            assertTrue(entry.legacyCuratedId+": "+correct.issues,correct.issues.isEmpty())
            assertFalse(correct.mayIssueWorkout)
        }
    }
}
