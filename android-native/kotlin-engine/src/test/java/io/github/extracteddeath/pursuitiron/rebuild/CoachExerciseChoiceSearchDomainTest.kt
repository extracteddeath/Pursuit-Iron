package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class CoachExerciseChoiceSearchDomainTest {
    private val approved=listOf(
        "barbell_bench_press", "seated_cable_row", "incline_dumbbell_press",
        "barbell_back_squat", "seated_cable_row"
    )
    private val labels=mapOf(
        "barbell_bench_press" to "Bench Press",
        "seated_cable_row" to "Seated Cable Row",
        "incline_dumbbell_press" to "Incline Dumbbell Press",
        "barbell_back_squat" to "Back Squat"
    )
    private fun search(query:String)=CoachExerciseChoiceSearchDomain.filter(
        approved,query){labels[it] ?: it}

    @Test fun noSearchKeepsOnlyApprovedChoicesInOriginalOrder() {
        assertEquals(approved.distinct(),search(""))
        assertEquals(approved.distinct(),search("   "))
    }
    @Test fun orderIndependentWordsMatchAcrossVisibleNameAndId() {
        assertEquals(listOf("incline_dumbbell_press"),search("press incline"))
        assertEquals(listOf("incline_dumbbell_press"),search("DUMBBELL press"))
        assertEquals(listOf("seated_cable_row"),search("row cable"))
        assertEquals(listOf("barbell_back_squat"),search("back-squat"))
    }
    @Test fun cannotInventUnapprovedMovementWhenSearchMatchesText() {
        assertTrue(search("machine chest press").isEmpty())
        assertTrue(search("unapproved exercise").isEmpty())
        assertEquals(listOf("barbell_bench_press"),
            search("barbell_bench"))
    }
    @Test fun filteringNeverMutatesSourceOrReordersAuthoredChoices() {
        val copy=approved.toList()
        assertEquals(listOf("barbell_bench_press","incline_dumbbell_press"),
            search("press"))
        assertEquals(copy,approved)
        assertEquals(approved.distinct(),search(""))
    }
    @Test fun historicalAliasesFindOnlyAlreadyAuthorizedPhysicalCandidates() {
        val source=HistoricalExerciseIdentityDomain.PINNED_SHELL_COMMIT
        val approved=listOf("barbell_bench","weighted-pullup","pullup",
            "chest_supported_row","chest-supported-machine-row",
            "preacher_curl","ez-bar-preacher-curl","cable_curl","plank")
        fun find(query:String,ids:List<String> = approved)=
            CoachExerciseChoiceSearchDomain.filter(ids,query){it}
        for((old,current) in listOf("bb-bench" to "barbell_bench",
            "bw-pullup" to "pullup","chest-row" to "chest-supported-machine-row",
            "preacher" to "ez-bar-preacher-curl","cable-curl" to "cable_curl",
            "plank" to "plank")) {
            assertEquals(HistoricalExerciseIdentityDomain.Candidate(old,current),
                HistoricalExerciseIdentityDomain.candidateForPinnedShell(source,old))
            assertNull(HistoricalExerciseIdentityDomain.candidateForPinnedShell("unverified",old))
            assertTrue(find(old).contains(current))
            assertTrue(find(old,emptyList()).isEmpty())
        }
        assertEquals(listOf("barbell_bench"),find("bb-bench"))
        assertEquals(listOf("pullup"),find("bw-pullup"))
        assertEquals("weighted-pullup",HistoricalExerciseIdentityDomain
            .candidateForPinnedShell(source,"pullup")?.nativeExerciseId)
        assertNull(HistoricalExerciseIdentityDomain.candidateForPinnedShell(source,"PULLUP"))
        assertNull(HistoricalExerciseIdentityDomain.candidateForPinnedShell(source,"Bench Press"))
        assertNull(HistoricalExerciseIdentityDomain.candidateForPinnedShell(source,"barbell_bench"))
        assertEquals(approved,find(""))
        assertTrue(find("bb-bench",listOf("pullup")).isEmpty())
    }

    @Test fun metadataSearchIsIndependentOfTheDevicesCaseConversionLocale() {
        val before=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("curl"),CoachExerciseChoiceSearchDomain.filter(
                listOf("curl","raise"),"biceps") {if(it=="curl")"BICEPS" else "DELTOIDS"})
        } finally { java.util.Locale.setDefault(before) }
    }

}
