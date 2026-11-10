package com.pursuitiron.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

/**
 * Android's Health Connect: finished workouts go in as strength-training sessions and logged bodyweight as weight,
 * so they appear in the phone's health app and anything else the person has connected; each record carries this app's
 * own id, so saving the same workout again replaces it. Separately opt-in, it reads last night's sleep, resting heart
 * rate and HRV (shown on Today's check-in against the person's own two-week usual) and weigh-ins from other apps.
 */
class HealthSync(private val context: Context) {
    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
        )
        val READ = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
        )
        private val AWAKE = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
    }

    /** Last night and the person's usual. Null fields: nothing recorded (no watch, or that permission not given). */
    data class Recovery(
        val sleepMin: Int?, val sleepUsualMin: Int?,
        val restingHr: Int?, val restingHrUsual: Int?,
        val hrv: Int?, val hrvUsual: Int?,
    ) {
        val any get() = sleepMin != null || restingHr != null || hrv != null
    }

    /** Installed and usable on this phone (built into Android 14+, an app from the Play Store before that). */
    val available: Boolean get() = runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> =
        if (!available) emptySet() else runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    private fun offset(i: Instant) = ZoneId.systemDefault().rules.getOffset(i)

    /** One workout: [endMs] is when it was saved and [minutes] its length, so it lands on the hours it was trained. */
    suspend fun writeWorkout(id: String, endMs: Long, minutes: Int, title: String, notes: String): Boolean {
        if (!available || HealthPermission.getWritePermission(ExerciseSessionRecord::class) !in granted()) return false
        val end = Instant.ofEpochMilli(endMs)
        val start = end.minusSeconds(60L * minutes.coerceAtLeast(1))
        return runCatching {
            client.insertRecords(listOf(ExerciseSessionRecord(
                startTime = start, startZoneOffset = offset(start), endTime = end, endZoneOffset = offset(end),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = title, notes = notes,
                metadata = Metadata.manualEntry(clientRecordId = "pi-workout-$id"),
            )))
            true
        }.getOrDefault(false)
    }

    /** One day's bodyweight, in kg (Health Connect converts for display). Re-logging the same day replaces it. */
    suspend fun writeWeight(kg: Double, atMs: Long): Boolean {
        if (!available || HealthPermission.getWritePermission(WeightRecord::class) !in granted() || !(kg > 0)) return false
        val t = Instant.ofEpochMilli(atMs)
        val day = java.time.LocalDate.ofInstant(t, ZoneId.systemDefault())
        return runCatching {
            client.insertRecords(listOf(WeightRecord(time = t, zoneOffset = offset(t), weight = Mass.kilograms(kg),
                metadata = Metadata.manualEntry(clientRecordId = "pi-weight-$day"))))
            true
        }.getOrDefault(false)
    }

    private suspend fun <T : Record> read(type: kotlin.reflect.KClass<T>, from: Instant, to: Instant): List<T> =
        runCatching { client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to))).records }.getOrDefault(emptyList())

    private fun median(v: List<Double>): Double? = if (v.size < 3) null else v.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }

    /** Minutes asleep per night (awake stages left out), keyed by the day the sleep ended. */
    private fun nights(rs: List<SleepSessionRecord>): Map<java.time.LocalDate, Int> = rs.groupBy { java.time.LocalDate.ofInstant(it.endTime, ZoneId.systemDefault()) }
        .mapValues { (_, list) -> list.sumOf { r ->
            val total = java.time.Duration.between(r.startTime, r.endTime).toMinutes()
            val awake = r.stages.filter { it.stage in AWAKE }.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
            (total - awake).coerceAtLeast(0)
        }.toInt() }

    /** Last night's sleep, today's resting heart rate and HRV, each beside the median of the 14 days before. */
    suspend fun recovery(): Recovery? {
        if (!available) return null
        val g = granted()
        if (READ.none { it in g }) return null
        val now = Instant.now(); val zone = ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val from = today.minusDays(15).atStartOfDay(zone).toInstant()
        val dayStart = today.atStartOfDay(zone).toInstant()
        val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in g) nights(read(SleepSessionRecord::class, from, now)) else emptyMap()
        val rhr = if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in g) read(RestingHeartRateRecord::class, from, now) else emptyList()
        val hrv = if (HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in g) read(HeartRateVariabilityRmssdRecord::class, from, now) else emptyList()
        fun <T> split(rs: List<T>, at: (T) -> Instant, v: (T) -> Double): Pair<Double?, Double?> {
            val todays = rs.filter { !at(it).isBefore(dayStart) }.maxByOrNull { at(it) }?.let(v)
            val byDay = rs.filter { at(it).isBefore(dayStart) }.groupBy { java.time.LocalDate.ofInstant(at(it), zone) }.mapValues { (_, l) -> l.map(v).average() }
            return todays to median(byDay.values.toList())
        }
        val (rNow, rUsual) = split(rhr, { it.time }, { it.beatsPerMinute.toDouble() })
        val (hNow, hUsual) = split(hrv, { it.time }, { it.heartRateVariabilityMillis })
        val r = Recovery(
            sleepMin = sleep[today], sleepUsualMin = median(sleep.filterKeys { it != today }.values.map { it.toDouble() })?.toInt(),
            restingHr = rNow?.let { Math.round(it).toInt() }, restingHrUsual = rUsual?.let { Math.round(it).toInt() },
            hrv = hNow?.let { Math.round(it).toInt() }, hrvUsual = hUsual?.let { Math.round(it).toInt() },
        )
        return r.takeIf { it.any }
    }

    /** Weigh-ins from other apps in the last 60 days, in kg; this app's own (sent from here) are left out. */
    suspend fun weighIns(): List<Pair<Long, Double>> {
        if (!available || HealthPermission.getReadPermission(WeightRecord::class) !in granted()) return emptyList()
        val now = Instant.now()
        return read(WeightRecord::class, now.minusSeconds(60L * 86400), now)
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
            .map { it.time.toEpochMilli() to it.weight.inKilograms }
    }
}
