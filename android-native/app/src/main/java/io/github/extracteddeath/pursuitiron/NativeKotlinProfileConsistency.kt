package io.github.extracteddeath.pursuitiron

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A week of recorded, owner-verified Finished workouts. No missing date is
 * interpreted as a rest day, and two completed workouts in one day remain
 * two sessions but only one active training day. */
internal data class NativeProfileWeekActivity(
    val weekStartingMonday:LocalDate,
    val sessions:Int,
    val activeDays:Int
)

internal data class NativeProfileConsistencyReport(
    val weeks:List<NativeProfileWeekActivity>,
    val undatedOrInvalidSessions:Int,
    val futureDatedSessions:Int
) {
    val sessionsInWindow:Int get()=weeks.sumOf{it.sessions}
    val activeWeeks:Int get()=weeks.count{it.sessions>0}
    val activeDaysInWindow:Int get()=weeks.sumOf{it.activeDays}
}

/** Display-only consistency derived from the recorded Finish clock in the
 * complete owner-verified Kotlin history. Never estimate timestamps from
 * file modification dates, set receipts or training-plan weekdays.
 *
 * The current week is anchored in the user's local time zone. Future-dated
 * records are left in History but excluded from this past-activity chart.
 * This code has no path to a program, progression, XP or journal writer. */
internal object NativeKotlinProfileConsistency {
    const val DEFAULT_WEEKS=8
    const val MAX_WEEKS=12

    fun fromVerifiedFinishTimes(
        completedAtEpochMillis:List<Long?>,
        today:LocalDate,
        zone:ZoneId,
        numberOfWeeks:Int=DEFAULT_WEEKS
    ):NativeProfileConsistencyReport {
        require(numberOfWeeks in 1..MAX_WEEKS)
        val currentMonday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday=currentMonday.minusWeeks(numberOfWeeks.toLong()-1L)
        val byWeek=mutableMapOf<LocalDate,MutableList<LocalDate>>()
        var undated=0
        var future=0

        completedAtEpochMillis.forEach { timestamp ->
            val date=timestamp?.let {
                runCatching { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.getOrNull()
            }
            if(date==null) {
                undated++
                return@forEach
            }
            if(date.isAfter(today)) {
                future++
                return@forEach
            }
            val monday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if(!monday.isBefore(firstMonday) && !monday.isAfter(currentMonday)) {
                byWeek.getOrPut(monday){mutableListOf()}.add(date)
            }
        }

        val weeks=(0 until numberOfWeeks).map { index ->
            val monday=firstMonday.plusWeeks(index.toLong())
            val entries=byWeek[monday].orEmpty()
            NativeProfileWeekActivity(monday,entries.size,entries.distinct().size)
        }
        return NativeProfileConsistencyReport(weeks,undated,future)
    }
}
