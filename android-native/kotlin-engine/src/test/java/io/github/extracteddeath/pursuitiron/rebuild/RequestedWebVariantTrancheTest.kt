package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Exact active-web IDs: load, fixture and history boundaries are independent. */
class RequestedWebVariantTrancheTest {
    private fun n(value:String)=BigDecimal(value)
    private val expected=listOf(
        "high-inc-db-press",
        "bottom-half-db-fly",
        "deficit-pendlay-row",
        "low-to-high-face-pull",
        "leaning-cable-ohext",
        "ad-press-barbell",
        "ad-press-smith"
    )
    private val byId=StarterExerciseTranche.entries()
        .associateBy{it.legacyCuratedId}

    private val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
        mapOf(n("25") to 2,n("10") to 2,n("5") to 2))
    private val dumbbells=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("15"),n("20"),n("25")))
    private val cable=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.CABLE_STACK,listOf(n("15"),n("20"),n("25")))
    private val smith=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.SMITH_TOTAL,listOf(n("65"),n("95"),n("115")))
    private val stock=mapOf(
        "straight-bar" to bar,
        "dumbbell-rack" to dumbbells,
        "cable-station" to cable,
        "incline-smith-station" to smith
    )
    private fun template()=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,90,null,60))},
        TrainingMuscle.entries.map{
            DeclaredMuscleDoseBounds(it,n("0"),n("20"),n("30"),0)
        },emptyList(),4,1
    )
    private fun review(id:String,load:String="20",
        equipment:Map<String,VerifiedLoadStock> =stock,
        fixtures:Set<String> =setOf("flat-bench","incline-bench","rack"),
        attachments:Set<String> =emptySet()
    )=CoachExerciseCatalogDomain.review(
        StarterExerciseTranche.forGym(template(),listOf(
            CatalogExerciseChoice(id,n(load),90,150,2,3,3,1,1,5)
        ),CatalogGym(equipment,attachments,fixtures))
    )

    @Test fun exactWebIdentifiersKeepSeparateExerciseAndVariantHistory() {
        assertEquals(372,byId.size)
        assertEquals(expected,
            StarterExerciseTranche.entries().drop(330).take(7)
                .map{it.legacyCuratedId})
        assertTrue(expected.all{it in byId})
        assertTrue(expected.all{!byId.getValue(it).mayTransferPreviousLoad})
        assertEquals(expected.size,expected.map{
            val m=byId.getValue(it).movement
            m.familyId to m.variantId
        }.distinct().size)
        assertEquals(listOf(RepBand(8,12),RepBand(12,18),
            RepBand(5,8),RepBand(12,20),RepBand(10,15),
            RepBand(6,12),RepBand(6,12)),
            expected.map{byId.getValue(it).practicalRepBand})
    }

    @Test fun allPinnedShellDiscoveryCandidatesResolveToDistinctSupportedNativeIdentities() {
        // The independent frozen shell audit enumerates the exact 372 source
        // rows and checks the Kotlin declarations against them. Here exercise
        // the real runtime lookup over the existing native standard/typed IDs
        // plus the reverse discovery aliases; no second alias table.
        val standard=StarterExerciseTranche.movements().associateBy{it.exerciseId}
        val measured=MeasuredExerciseCatalogDomain.all.associateBy{it.exerciseId}
        assertTrue(standard.keys.intersect(measured.keys).isEmpty())
        val native=standard.keys+measured.keys
        val pin=HistoricalExerciseIdentityDomain.PINNED_SHELL_COMMIT
        val aliases=native.flatMap{HistoricalExerciseIdentityDomain.searchAliases(it)}
        assertEquals(27,aliases.size)
        assertEquals(aliases.size,aliases.toSet().size)
        val candidates=(native+aliases).mapNotNull{
            HistoricalExerciseIdentityDomain.candidateForPinnedShell(pin,it)
        }
        assertEquals(372,candidates.size)
        assertEquals(372,candidates.map{it.originalShellId}.distinct().size)
        assertEquals(372,candidates.map{it.nativeExerciseId}.distinct().size)
        val mapped=candidates.associateBy{it.originalShellId}
        for(candidate in candidates){
            val id=candidate.originalShellId
            val target=candidate.nativeExerciseId
            assertTrue("$id -> $target",target in native)
            assertEquals(candidate,HistoricalExerciseIdentityDomain.candidateForPinnedShell(pin,id))
            assertNull(HistoricalExerciseIdentityDomain.candidateForPinnedShell("unknown",id))
            assertNull(HistoricalExerciseIdentityDomain.candidateForPinnedShell(pin," $id"))
            if(id!=target)assertTrue(id in HistoricalExerciseIdentityDomain.searchAliases(target))
        }
        assertNull(HistoricalExerciseIdentityDomain.candidateForPinnedShell(pin,"not-a-real-exercise"))
        assertEquals("weighted-pullup",mapped.getValue("pullup").nativeExerciseId)
        assertEquals("pullup",mapped.getValue("bw-pullup").nativeExerciseId)
        assertEquals("chest-supported-machine-row",mapped.getValue("chest-row").nativeExerciseId)
        assertEquals("ez-bar-preacher-curl",mapped.getValue("preacher").nativeExerciseId)
        assertNull(standard.getValue("pullup").expectedLoadBasis)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,standard.getValue("weighted-pullup").expectedLoadBasis)
        assertEquals("cable_curl",mapped.getValue("cable-curl").nativeExerciseId)
        assertEquals("straight-bar",standard.getValue("cable_curl").attachmentId)
        assertEquals("rope",standard.getValue("cable_crunch").attachmentId)
        for(id in listOf("plank","farmers","band-pushup")){
            assertEquals(id,mapped.getValue(id).nativeExerciseId)
            assertTrue(id in measured)
            assertFalse(id in standard)
        }
        // Discovery is NOT authority to transfer a previous load or issue a
        // workout. The typed or standard candidate must still pass its own
        // gym/stock/attachment review, and import remains separately owned.
        assertTrue(StarterExerciseTranche.entries().all{!it.mayTransferPreviousLoad})
    }

    @Test fun physicalLoadingAndBenchesMustBeExactNotGuessed() {
        val fixtures=mapOf(
            "high-inc-db-press" to setOf("incline-bench"),
            "bottom-half-db-fly" to setOf("flat-bench"),
            "ad-press-barbell" to setOf("rack","incline-bench"),
            "ad-press-smith" to setOf("incline-bench"))
        fixtures.forEach{(id,required)->
            val entry=byId.getValue(id).movement
            assertEquals(required,entry.requiredFacilityIds)
            assertTrue(review(id,equipment=stock,fixtures=emptySet()).issues.any{
                it.exerciseId==id &&
                    it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
            })
        }
        val cases=mapOf(
            "high-inc-db-press" to ("dumbbell-rack" to LoadBasis.DUMBBELL_PER_HAND),
            "bottom-half-db-fly" to ("dumbbell-rack" to LoadBasis.DUMBBELL_PER_HAND),
            "deficit-pendlay-row" to ("straight-bar" to LoadBasis.BARBELL_TOTAL),
            "low-to-high-face-pull" to ("cable-station" to LoadBasis.CABLE_STACK),
            "leaning-cable-ohext" to ("cable-station" to LoadBasis.CABLE_STACK),
            "ad-press-barbell" to ("straight-bar" to LoadBasis.BARBELL_TOTAL),
            "ad-press-smith" to ("incline-smith-station" to LoadBasis.SMITH_TOTAL)
        )
        cases.forEach{(id,expected)->
            val m=byId.getValue(id).movement
            assertEquals(expected.first,m.equipmentId)
            assertEquals(expected.second,m.expectedLoadBasis)
            assertTrue(review(id,equipment=stock-expected.first).issues.any{
                it.exerciseId==id &&
                    it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
            })
            assertTrue(review(id,equipment=stock+
                (expected.first to cable)).issues.any{
                it.exerciseId==id &&
                    it.kind==CatalogIssueType.EQUIPMENT_BASIS_MISMATCH
            } || expected.second==LoadBasis.CABLE_STACK)
        }
        assertFalse(review("ad-press-smith","95").issues.any{
            it.kind==CatalogIssueType.LOAD_UNVERIFIED ||
                it.kind==CatalogIssueType.EQUIPMENT_BASIS_MISMATCH
        })
        assertTrue(review("ad-press-smith","105").issues.any{
            it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        assertEquals(emptySet<String>(),byId.getValue("deficit-pendlay-row")
            .movement.requiredFacilityIds)
        assertNull(byId.getValue("low-to-high-face-pull").movement.attachmentId)
        assertNull(byId.getValue("leaning-cable-ohext").movement.attachmentId)
    }
}
