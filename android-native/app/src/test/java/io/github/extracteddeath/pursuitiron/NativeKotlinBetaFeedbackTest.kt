package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinBetaFeedbackTest {
    private val f=NativeKotlinBetaFeedbackFormat

    @Test fun generalFeedbackIncludesOnlyTypedMessageAndInstalledVersion() {
        val draft=NativeBetaFeedbackDraft(
            NativeBetaFeedbackKind.GENERAL,message="  Better workout navigation  ")
        assertTrue(f.canShare(draft))
        val report=checkNotNull(f.format(draft,"0.1.0-kotlin-lab.104"))
        assertEquals(
            "Pursuit Iron early beta feedback\n"+
                "App version: 0.1.0-kotlin-lab.104\n\n"+
                "My feedback:\nBetter workout navigation",report)
        assertFalse(report.contains("athleteId"))
        assertFalse(report.contains("journal"))
        assertFalse(report.contains("diagnostic"))
        assertFalse(report.contains("Android device"))
        assertEquals("Pursuit Iron early beta feedback",
            f.subject(NativeBetaFeedbackKind.GENERAL))
    }

    @Test fun structuredProblemReportPreservesAllAndOnlyUserTypedEvidence() {
        val draft=NativeBetaFeedbackDraft(
            NativeBetaFeedbackKind.PROBLEM,
            workflow="Train > Focus",
            steps="1. Open today's workout\n2. Record a set",
            expected="Next set shows 120 lb",
            actual="It displayed 100 lb")
        val report=checkNotNull(f.format(draft,"0.1.0-kotlin-lab.105"))
        assertTrue(report.contains("Pursuit Iron beta problem report"))
        assertTrue(report.contains("Screen or workflow:\nTrain > Focus"))
        assertTrue(report.contains("Steps to reproduce:\n1. Open today's workout"))
        assertTrue(report.contains("Expected behavior:\nNext set shows 120 lb"))
        assertTrue(report.contains("Actual behavior:\nIt displayed 100 lb"))
        assertFalse(report.contains("My feedback:"))
        assertFalse(report.contains("ownerKey"))
        assertEquals("Pursuit Iron beta problem report",
            f.subject(NativeBetaFeedbackKind.PROBLEM))
        assertEquals(report,f.format(draft,"0.1.0-kotlin-lab.105"))
    }

    @Test fun partialReproductionDetailsAreOptionalButActualProblemIsRequired() {
        val blank=NativeBetaFeedbackDraft(NativeBetaFeedbackKind.PROBLEM,
            steps="I cannot reproduce this reliably")
        assertFalse(f.canShare(blank))
        assertNull(f.format(blank,"v1"))
        val partial=blank.copy(actual="App closed when I tapped Finish")
        assertTrue(f.canShare(partial))
        val formatted=checkNotNull(f.format(partial,""))
        assertTrue(formatted.contains("App version: unknown"))
        assertTrue(formatted.contains("Steps to reproduce:"))
        assertTrue(formatted.contains("Actual behavior:"))
        assertFalse(formatted.contains("Expected behavior:"))
        assertFalse(f.canShare(NativeBetaFeedbackDraft(
            NativeBetaFeedbackKind.GENERAL,message="  \n  ")))
    }

    @Test fun invalidControlCharactersAndExcessiveTextCannotBeShared() {
        val invalid=NativeBetaFeedbackDraft(NativeBetaFeedbackKind.PROBLEM,
            actual="Bad\u0000event")
        assertFalse(f.canShare(invalid))
        assertNull(f.format(invalid,"v1"))
        val huge="x".repeat(f.MAX_FIELD_CHARS+1)
        assertFalse(f.canShare(invalid.copy(actual=huge)))
        assertFalse(f.canShare(NativeBetaFeedbackDraft(
            NativeBetaFeedbackKind.GENERAL,message=huge)))
        assertEquals("v".repeat(f.MAX_VERSION_CHARS),
            checkNotNull(f.format(
                NativeBetaFeedbackDraft(NativeBetaFeedbackKind.GENERAL,
                    message="Hello"),"v".repeat(100))).substringAfter("App version: ")
                .substringBefore("\n"))
    }

    @Test fun lineEndingsNormalizedWithoutAddingTelemetry() {
        val actual="App showed wrong exercise\r\nThen I closed it"
        val report=checkNotNull(f.format(NativeBetaFeedbackDraft(
            NativeBetaFeedbackKind.PROBLEM,actual=actual),"v2"))
        assertTrue(report.contains("exercise\nThen"))
        assertFalse(report.contains('\r'))
    }
}
