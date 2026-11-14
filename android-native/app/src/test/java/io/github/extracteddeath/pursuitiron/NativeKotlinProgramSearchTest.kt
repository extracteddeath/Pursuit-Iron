package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomDraftSummary
import io.github.extracteddeath.pursuitiron.rebuild.AthleteTrainingGoal
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinProgramSearchTest {
    private fun program(id:String,title:String=id)=NativeCustomDraftSummary(id,title,2,7,
        AthleteTrainingGoal.STRENGTH,8,3,4)

    @Test fun searchInspectsTheEntire512RecordCensusBeforePaging() {
        val programs=(1..512).map{program("id-$it","Program $it")}
        val matches=matchingPrograms(programs,"program 512",null)
        assertEquals(listOf("id-512"),matches.take(20).map{it.cycleId})
        assertEquals(512,matchingPrograms(programs,"",null).size)
    }

    @Test fun currentProgramComesFirstAndEqualNamesRemainDistinctChoices() {
        val programs=listOf(program("b","Same name"),program("c","Zulu"),program("a","Same name"))
        assertEquals(listOf("c","a","b"),matchingPrograms(programs,"","c").map{it.cycleId})
        assertEquals(listOf("a","b"),matchingPrograms(programs,"same",null).map{it.cycleId})
        assertEquals(listOf("c"),matchingPrograms(programs,"zulu","a").map{it.cycleId})
    }

    @Test fun searchUsesAllTypedWordsAndRealProgramMetadata() {
        val programs=listOf(program("a","Morning program"),program("b","Evening program"))
        assertEquals(listOf("a"),matchingPrograms(programs,"  STRENGTH\tMorning  8  ",null).map{it.cycleId})
        assertEquals(2,matchingPrograms(programs,"days/week",null).size)
        assertTrue(matchingPrograms(programs,"morning evening",null).isEmpty())
        assertTrue(matchingPrograms(programs,"hypertrophy",null).isEmpty())
    }

    @Test fun internalIdsAndRevisionNumbersAreNotSearchableProgramDetails() {
        val programs=listOf(program("internal-secret-id","Visible name"))
        assertTrue(matchingPrograms(programs,"internal-secret-id",null).isEmpty())
        assertTrue(matchingPrograms(programs,"revision 7",null).isEmpty())
        assertFalse(programs.single().programDescription().contains("revision"))
    }

    @Test fun matchingAndOrderingDoNotDependOnTheDeviceLocale() {
        val previous=Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val programs=listOf(program("b","IRON"),program("a","İstanbul"))
            assertEquals(listOf("b"),matchingPrograms(programs,"iron",null).map{it.cycleId})
            assertEquals(listOf("b","a"),matchingPrograms(programs,"",null).map{it.cycleId})
        } finally {Locale.setDefault(previous)}
    }

    @Test fun absentMetadataIsOmittedAndAuthoredZeroDaysAreExplained() {
        assertEquals("1 block",NativeCustomDraftSummary("id","Blank",1,1).programDescription())
        assertEquals("Hypertrophy · 1 week · Add training days · 1 block",
            NativeCustomDraftSummary("id","Blank",1,1,AthleteTrainingGoal.HYPERTROPHY,1,0,0).programDescription())
        assertEquals("Strength · 8 weeks · 3–4 days/week · 2 blocks",program("id").programDescription())
    }
}
