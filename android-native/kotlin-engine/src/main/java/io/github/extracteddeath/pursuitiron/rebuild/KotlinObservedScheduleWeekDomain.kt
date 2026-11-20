package io.github.extracteddeath.pursuitiron.rebuild

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections

/**
 * Dated OBSERVATIONS of workouts from one native owner-read source epoch.
 * A manifest Finish timestamp is not a scheduled workout start time. Old
 * undated receipts, clock disagreements, changed days and missing workouts
 * remain visible rather than being manufactured into compliant training.
 *
 * Never treat this read-only week summary as consent or a prescription rule.
 * A complete program calendar/activation epoch and athlete acceptance are
 * independent, still-unimplemented requirements for automatic evolution.
 */
enum class KotlinObservedWeekCoverage {
    FULL_SCHEDULE_OBSERVED_FOR_REVIEW_ONLY,
    PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY,
    CHRONOLOGY_UNVERIFIED
}

data class KotlinObservedScheduledSession(
    val workoutId:String,
    val dayIndex:Int,
    val issuedWeekday:Int,
    val localCompletionDate:LocalDate?,
    val observedWeekday:Int?,
    val fullIssuedWorkRecorded:Boolean,
    val matchesScheduledWeekday:Boolean
)

data class KotlinObservedScheduleWeek(
    val monday:LocalDate,
    val sessions:List<KotlinObservedScheduledSession>,
    val expectedWeekdays:Set<Int>,
    val fullyRecordedWeekdays:Set<Int>,
    val duplicateWeekdays:Set<Int>,
    val coverage:KotlinObservedWeekCoverage
) {
    val canProveProgramAdherence:Boolean get()=false
    val canChangeFutureSets:Boolean get()=false
}

data class KotlinObservedScheduleWeeks(
    val sourceReplayDigest:String,
    val timezoneId:String,
    val scheduledWeekdays:Set<Int>,
    val weeks:List<KotlinObservedScheduleWeek>,
    val undatedWorkoutIds:List<String>,
    val hasConflictingCompletionOrder:Boolean
) {
    val canApproveVolumeAdaptation:Boolean get()=false
    val canPromotePhase:Boolean get()=false
    val canAssumeMissedWeeks:Boolean get()=false
}

enum class KotlinObservedWeekRefusal {
    INVALID_SOURCE_OR_SCHEDULE,
    INVALID_TIME_MAPPING
}

sealed interface KotlinObservedWeekResult {
    data class Reviewed(val evidence:KotlinObservedScheduleWeeks):
        KotlinObservedWeekResult
    data class Rejected(val reason:KotlinObservedWeekRefusal):
        KotlinObservedWeekResult
}

object KotlinObservedScheduleWeekDomain {
    fun review(
        ledger:KotlinProgramHistoryReadout,
        completedAtMillisByWorkout:Map<String,Long?>,
        completionOrderByWorkout:Map<String,Long>,
        scheduledWeekdays:Set<Int>,
        timezoneId:String
    ):KotlinObservedWeekResult {
        fun refused(reason:KotlinObservedWeekRefusal)=
            KotlinObservedWeekResult.Rejected(reason)
        val zone=runCatching{ZoneId.of(timezoneId)}.getOrNull()
            ?:return refused(KotlinObservedWeekRefusal.INVALID_SOURCE_OR_SCHEDULE)
        val expected=scheduledWeekdays.sorted()
        val byWorkout=ledger.workouts.associateBy{it.workoutId}
        if(ledger.schemaVersion!=2 || ledger.exactReplayDigest.length!=64 ||
            byWorkout.size!=ledger.workouts.size ||
            expected.size !in 3..6 || expected.any{it !in 0..6} ||
            ledger.workouts.any{
                it.dayIndex !in expected.indices ||
                    expected[it.dayIndex]!=it.calendarDay
            })
            return refused(KotlinObservedWeekRefusal.INVALID_SOURCE_OR_SCHEDULE)
        if(completedAtMillisByWorkout.keys!=byWorkout.keys ||
            completionOrderByWorkout.keys!=byWorkout.keys ||
            completionOrderByWorkout.values.toSet().size!=byWorkout.size ||
            completionOrderByWorkout.values.any{it<=0L} ||
            completedAtMillisByWorkout.values.any{
                !KotlinHistoryCompletionTime.valid(it)
            })
            return refused(KotlinObservedWeekRefusal.INVALID_TIME_MAPPING)
        val working=ledger.workingSets.groupBy{it.workoutId}
        if(working.keys.any{it !in byWorkout.keys})
            return refused(KotlinObservedWeekRefusal.INVALID_SOURCE_OR_SCHEDULE)

        val ordered=ledger.workouts.sortedBy{
            completionOrderByWorkout.getValue(it.workoutId)
        }
        var lastKnownTime:Long?=null
        var contradictoryOrder=false
        val sessions=mutableListOf<KotlinObservedScheduledSession>()
        for(workout in ordered) {
            val dateTime=completedAtMillisByWorkout.getValue(workout.workoutId)
            if(dateTime!=null) {
                if(lastKnownTime!=null && dateTime<lastKnownTime)
                    contradictoryOrder=true
                lastKnownTime=dateTime
            }
            val observedDate=dateTime?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            }
            val actualWeekday=observedDate?.dayOfWeek?.value?.minus(1)
            val facts=working[workout.workoutId].orEmpty()
            val full=facts.isNotEmpty() &&
                workout.completedWorkingSets==facts.size &&
                workout.skippedWorkingSets==0 &&
                workout.failedWorkingSets==0 &&
                !workout.shortenedForTime &&
                facts.all{it.outcome==KotlinJournalSetOutcome.COMPLETED}
            sessions+=KotlinObservedScheduledSession(
                workout.workoutId,workout.dayIndex,workout.calendarDay,
                observedDate,actualWeekday,full,
                actualWeekday!=null && actualWeekday==workout.calendarDay)
        }
        val undated=Collections.unmodifiableList(sessions.filter{
            it.localCompletionDate==null
        }.map{it.workoutId})
        val incompleteChronology=undated.isNotEmpty() || contradictoryOrder
        val grouped=sessions.filter{it.localCompletionDate!=null}
            .groupBy {session->
                val date=checkNotNull(session.localCompletionDate)
                date.minusDays((date.dayOfWeek.value-1).toLong())
            }
        val weeks=grouped.entries.sortedBy{it.key}.map{(monday,visits)->
            val byDay=visits.groupBy{it.issuedWeekday}
            val duplicates=byDay.filterValues{it.size>1}.keys
            val complete=byDay.filter{(weekday,dayVisits)->
                weekday in expected && dayVisits.size==1 &&
                    dayVisits.single().fullIssuedWorkRecorded &&
                    dayVisits.single().matchesScheduledWeekday
            }.keys
            val coverage=when {
                incompleteChronology ->
                    KotlinObservedWeekCoverage.CHRONOLOGY_UNVERIFIED
                complete.size==expected.size &&
                    duplicates.isEmpty() && visits.size==expected.size ->
                    KotlinObservedWeekCoverage.FULL_SCHEDULE_OBSERVED_FOR_REVIEW_ONLY
                else ->
                    KotlinObservedWeekCoverage.PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY
            }
            KotlinObservedScheduleWeek(
                monday,Collections.unmodifiableList(visits.toList()),
                Collections.unmodifiableSet(expected.toSet()),
                Collections.unmodifiableSet(complete.toSet()),
                Collections.unmodifiableSet(duplicates.toSet()),
                coverage)
        }
        return KotlinObservedWeekResult.Reviewed(
            KotlinObservedScheduleWeeks(
                ledger.exactReplayDigest,timezoneId,
                Collections.unmodifiableSet(expected.toSet()),
                Collections.unmodifiableList(weeks),undated,
                contradictoryOrder))
    }
}
