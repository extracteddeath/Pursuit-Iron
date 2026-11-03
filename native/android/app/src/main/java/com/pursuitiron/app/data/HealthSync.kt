package com.pursuitiron.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

/**
 * Android's Health Connect: finished workouts go in as strength-training sessions and logged bodyweight as weight,
 * so they appear in the phone's health app and anything else the person has connected. Write-only, opt-in from
 * Data & storage, and each record carries this app's own id, so saving the same workout again replaces it.
 */
class HealthSync(private val context: Context) {
    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
        )
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
}
