package io.github.extracteddeath.pursuitiron

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class NativeKotlinTrendAxis(
    val fractions:List<Float>,
    val calendarScaled:Boolean
)

/**
 * Read-only axis policy shared by Strength and exercise performance.
 * Equal session spacing is used ONLY with a clear disclosure when recorded
 * Finish times are absent, tied or contradict the confirmed session order.
 * Display data never feeds a load, PR, target or training decision.
 */
internal object NativeKotlinTrendAxisPolicy {
    fun project(timestamps:List<Long?>):NativeKotlinTrendAxis {
        val count=timestamps.size
        if(count==0)return NativeKotlinTrendAxis(emptyList(),false)
        if(count==1)return NativeKotlinTrendAxis(listOf(0.5f),false)
        val sequence=(0 until count).map {it.toFloat()/(count-1).toFloat()}
        val known=timestamps.filterNotNull()
        if(known.size!=count || known.any{it<=0L} ||
            known.zipWithNext().any{(before,after)->after<=before})
            return NativeKotlinTrendAxis(sequence,false)
        val first=known.first().toDouble()
        val span=known.last().toDouble()-first
        if(span<=0.0 || !span.isFinite())
            return NativeKotlinTrendAxis(sequence,false)
        val positions=known.map {
            ((it.toDouble()-first)/span).toFloat().coerceIn(0f,1f)
        }
        if(positions.zipWithNext().any{(before,after)->after<=before})
            return NativeKotlinTrendAxis(sequence,false)
        return NativeKotlinTrendAxis(positions,true)
    }

    // These are completed OBSERVED loads, not rounded strength estimates.
    fun exactMetric(value:BigDecimal,unit:String):String {
        require(unit.isNotBlank())
        return "${value.stripTrailingZeros().toPlainString()} $unit"
    }

    fun sessionLabel(
        completionOrder:Long,
        recordedAtEpochMillis:Long?,
        zone:ZoneId,
        locale:Locale
    ):String {
        val recorded=recordedAtEpochMillis?.let{time->
            runCatching {
                DateTimeFormatter.ofLocalizedDateTime(
                    FormatStyle.MEDIUM,FormatStyle.SHORT)
                    .withLocale(locale).withZone(zone)
                    .format(Instant.ofEpochMilli(time))
            }.getOrNull()
        }
        return "Session $completionOrder · ${recorded ?: "Date not recorded"}"
    }
}
