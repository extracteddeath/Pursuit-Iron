package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Read-only portable evidence. This is explicitly NOT a restorable backup:
 * app preferences, signed program approvals, unfinished workouts and imported
 * web history are intentionally absent. The Android caller re-verifies the
 * owner and revisions before writing the returned bytes.
 */
internal sealed interface NativeTrainingArchiveResult {
    data class Ready(
        val bytes:ByteArray,
        val sessions:Int,
        val setRows:Int,
        val bodyweightRows:Int
    ):NativeTrainingArchiveResult
    data class Rejected(val reason:NativeTrainingArchiveRefusal):
        NativeTrainingArchiveResult
}
internal enum class NativeTrainingArchiveRefusal {
    OWNER_MISMATCH, HISTORY_UNVERIFIED, MEASUREMENTS_UNVERIFIED,
    TOO_LARGE, ENCODING_FAILED
}

internal object NativeKotlinTrainingArchiveDomain {
    private const val MAX_ARCHIVE_BYTES=24*1024*1024
    private const val MAX_WEIGH_INS=4096
    private const val MAX_MEASURE_CSV=384*1024

    /** All cells in this ledger are dates, numeric decimal strings and a
     * strict enum; no user-controlled formula or arbitrary delimiter.
     * Preserve the exact original unit and numeric amount.
     */
    fun bodyweightCsv(ledger:NativeBodyweightLedger):String? {
        if(ledger.revision<0 || ledger.entries.size>MAX_WEIGH_INS ||
            ledger.entries.map{it.date}.distinct().size!=ledger.entries.size ||
            ledger.entries!=ledger.entries.sortedBy{it.date} ||
            ledger.entries.any{
                NativeBodyweightLedgerDomain.validEntry(it,LocalDate.MAX)!=null
            })return null
        val sb=StringBuilder("measurement_date,bodyweight,unit\n")
        for(entry in ledger.entries) {
            sb.append(entry.date).append(',')
                .append(entry.amount.stripTrailingZeros().toPlainString())
                .append(',').append(entry.unit.name).append('\n')
            if(sb.length>MAX_MEASURE_CSV)return null
        }
        return sb.toString()
    }

    fun prepare(
        athleteId:String,
        history:NativeKotlinMixedHistoryTimeline,
        ledger:NativeBodyweightLedger
    ):NativeTrainingArchiveResult {
        if(!NativeBodyweightLedgerDomain.validOwner(athleteId) ||
            history.athleteId!=athleteId)
            return NativeTrainingArchiveResult.Rejected(
                NativeTrainingArchiveRefusal.OWNER_MISMATCH)
        val csv=when(val result=NativeKotlinVerifiedHistoryCsvExport.prepare(history)) {
            is NativeKotlinHistoryCsvResult.Ready ->result
            is NativeKotlinHistoryCsvResult.InvalidHistory ->
                return NativeTrainingArchiveResult.Rejected(
                    NativeTrainingArchiveRefusal.HISTORY_UNVERIFIED)
            NativeKotlinHistoryCsvResult.TooLarge ->
                return NativeTrainingArchiveResult.Rejected(
                    NativeTrainingArchiveRefusal.TOO_LARGE)
        }
        val measurements=bodyweightCsv(ledger) ?:return
            NativeTrainingArchiveResult.Rejected(
                NativeTrainingArchiveRefusal.MEASUREMENTS_UNVERIFIED)
        val readme=(
            "Pursuit Iron — portable records, not a restorable app backup\n"+
            "Contents:\n"+
            "- training-history.csv: verified completed native workouts, corrected "+
              "set facts, original targets, machine identity and original "+
              "observed loads. Session notes are excluded.\n"+
            "- bodyweight-history.csv: your explicitly entered dated "+
              "bodyweight measurements, retaining original units.\n"+
            "This archive DOES NOT contain your signed programs, current "+
              "unfinished workout, coach settings, gym stock, unsent drafts, "+
              "other app preferences, or historical web records. Do not delete "+
              "the app or reset its storage relying on this archive alone.\n"+
            "Files are unencrypted. Keep the archive private and share it "+
              "only with someone you trust.\n"
        )
        return runCatching {
            val sink=ByteArrayOutputStream()
            ZipOutputStream(sink,StandardCharsets.UTF_8).use {zip->
                fun put(name:String,value:String) {
                    val entry=ZipEntry(name)
                    // Stable names and timestamps make it possible to
                    // compare repeated exports without injecting hidden IDs.
                    entry.time=0L
                    zip.putNextEntry(entry)
                    zip.write(value.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
                put("README.txt",readme)
                put("training-history.csv",csv.csv)
                put("bodyweight-history.csv",measurements)
            }
            val bytes=sink.toByteArray()
            if(bytes.isEmpty() || bytes.size>MAX_ARCHIVE_BYTES)
                NativeTrainingArchiveResult.Rejected(
                    NativeTrainingArchiveRefusal.TOO_LARGE)
            else NativeTrainingArchiveResult.Ready(
                bytes,csv.sessionCount,csv.rowCount,ledger.entries.size)
        }.getOrElse {
            NativeTrainingArchiveResult.Rejected(
                NativeTrainingArchiveRefusal.ENCODING_FAILED)
        }
    }
}
