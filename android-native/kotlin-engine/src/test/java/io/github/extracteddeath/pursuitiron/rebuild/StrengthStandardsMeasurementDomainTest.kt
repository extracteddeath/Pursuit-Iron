package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class StrengthStandardsMeasurementDomainTest {
    @Test fun oneRepEstimateUsesActualWeightAndLimitedRecordedReps() {
        val measure=StrengthStandardsMeasurementDomain
        assertEquals(0,measure.epley(BigDecimal("100"),1)!!
            .compareTo(BigDecimal("100")))
        assertEquals(0,measure.epley(BigDecimal("100"),6)!!
            .compareTo(BigDecimal("120")))
        assertNull(measure.epley(BigDecimal("100"),11))
        assertNull(measure.epley(BigDecimal("100"),0))
        assertNull(measure.epley(BigDecimal.ZERO,5))
    }

    @Test fun changingWeightUnitsNeverChangesThePhysicalBodyweightRatio() {
        val measure=StrengthStandardsMeasurementDomain
        val kilograms=measure.convert(BigDecimal("220.462262"),LoadUnit.LB,LoadUnit.KG)
        assertTrue(kilograms.subtract(BigDecimal("100")).abs()<BigDecimal("0.0001"))
        val pounds=measure.convert(BigDecimal("100"),LoadUnit.KG,LoadUnit.LB)
        assertTrue(pounds.subtract(BigDecimal("220.462262")).abs()<BigDecimal("0.0001"))
        assertEquals(0,measure.relativeStrength(
            BigDecimal("220"),BigDecimal("110"))!!.compareTo(BigDecimal("2.00000")))
        assertNull(measure.relativeStrength(BigDecimal("200"),BigDecimal.ZERO))
    }

    @Test fun kgAndLbEstimatesAreComparableOnlyAfterConversion() {
        val measure=StrengthStandardsMeasurementDomain
        val oneHundredKg=measure.epley(BigDecimal("100"),5)!!
        val twoHundredLb=measure.epley(BigDecimal("200"),5)!!
        val normalized=measure.convert(oneHundredKg,LoadUnit.KG,LoadUnit.LB)
        assertTrue(normalized>twoHundredLb)
    }
}
