package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.content.SharedPreferences
import io.github.extracteddeath.pursuitiron.rebuild.LoadUnit
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

/**
 * Athlete-entered bodyweight observations, separate from certified workout
 * facts. Nothing here alters progression, program generation, PRs or exercise
 * histories. Never infer bodyweight from a working-set load.
 *
 * This is a versioned private on-device measurement ledger. It is NOT a
 * legacy-history migration and does not enter the training journal.
 */
internal data class NativeBodyweightEntry(
    val date:LocalDate,
    val amount:BigDecimal,
    val unit:LoadUnit
)
internal data class NativeBodyweightLedger(
    val revision:Long,
    val entries:List<NativeBodyweightEntry>
)
internal enum class NativeBodyweightRefusal {
    OWNER_NOT_VERIFIED, INVALID_ENTRY, DATE_IN_FUTURE, REVISION_CONFLICT,
    NO_MATCHING_DATE, REPLACEMENT_CONFIRMATION_REQUIRED, FULL,
    CORRUPT, STORAGE_UNAVAILABLE
}
internal sealed interface NativeBodyweightRead {
    data class Ready(val ledger:NativeBodyweightLedger):NativeBodyweightRead
    data class Rejected(val reason:NativeBodyweightRefusal):NativeBodyweightRead
}
internal sealed interface NativeBodyweightWrite {
    data class Saved(val ledger:NativeBodyweightLedger):NativeBodyweightWrite
    data class Rejected(val reason:NativeBodyweightRefusal):NativeBodyweightWrite
}

internal object NativeBodyweightLedgerDomain {
    private const val MAX_RECORDS=4096
    private val maxAmount=BigDecimal("1000")
    private val earliest=LocalDate.parse("1900-01-01")

    fun validOwner(owner:String)=owner.isNotBlank() && owner.length<=128 &&
        owner.none{it.code<32 || it.code==127}

    fun validEntry(entry:NativeBodyweightEntry,today:LocalDate):NativeBodyweightRefusal? {
        if(entry.date.isAfter(today))return NativeBodyweightRefusal.DATE_IN_FUTURE
        if(entry.date.isBefore(earliest) ||
            entry.amount<=BigDecimal.ZERO ||
            entry.amount>maxAmount ||
            entry.amount.stripTrailingZeros().scale()>2 ||
            entry.unit !in setOf(LoadUnit.LB,LoadUnit.KG))
            return NativeBodyweightRefusal.INVALID_ENTRY
        return null
    }

    fun replace(
        source:NativeBodyweightLedger,
        newEntry:NativeBodyweightEntry,
        today:LocalDate,
        replaceExisting:Boolean
    ):NativeBodyweightWrite {
        validEntry(newEntry,today)?.let{return NativeBodyweightWrite.Rejected(it)}
        if(source.revision==Long.MAX_VALUE)
            return NativeBodyweightWrite.Rejected(NativeBodyweightRefusal.FULL)
        val exists=source.entries.any{it.date==newEntry.date}
        if(exists && !replaceExisting)
            return NativeBodyweightWrite.Rejected(
                NativeBodyweightRefusal.REPLACEMENT_CONFIRMATION_REQUIRED)
        if(!exists && source.entries.size>=MAX_RECORDS)
            return NativeBodyweightWrite.Rejected(NativeBodyweightRefusal.FULL)
        val sorted=(source.entries.filterNot{it.date==newEntry.date}+newEntry)
            .sortedBy{it.date}
        return NativeBodyweightWrite.Saved(
            NativeBodyweightLedger(source.revision+1,sorted))
    }

    fun delete(
        source:NativeBodyweightLedger,date:LocalDate
    ):NativeBodyweightWrite {
        if(source.revision==Long.MAX_VALUE)
            return NativeBodyweightWrite.Rejected(NativeBodyweightRefusal.FULL)
        if(source.entries.none{it.date==date})
            return NativeBodyweightWrite.Rejected(NativeBodyweightRefusal.NO_MATCHING_DATE)
        return NativeBodyweightWrite.Saved(
            NativeBodyweightLedger(source.revision+1,
                source.entries.filterNot{it.date==date}))
    }

    /** Full snapshot with owner fence and SHA-256 corruption check. */
    fun encode(owner:String,ledger:NativeBodyweightLedger):String {
        require(validOwner(owner) && ledger.revision>=0)
        require(ledger.entries.size<=MAX_RECORDS)
        require(ledger.entries.distinctBy{it.date}.size==ledger.entries.size)
        require(ledger.entries.all{validEntry(it,LocalDate.MAX)==null})
        val rows=ledger.entries.sortedBy{it.date}.joinToString("\n") {entry->
            "${entry.date}|${entry.amount.stripTrailingZeros().toPlainString()}|${entry.unit.name}"
        }
        val ownerStamp=hash(owner)
        val payload=listOf("bodyweight:1",ownerStamp,
            ledger.revision.toString(),ledger.entries.size.toString(),rows)
            .joinToString("\n")
        return payload+"\nsha256:"+hash(payload)
    }

    fun decode(owner:String,raw:String?):NativeBodyweightRead {
        if(!validOwner(owner))
            return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.OWNER_NOT_VERIFIED)
        if(raw==null)return NativeBodyweightRead.Ready(
            NativeBodyweightLedger(0,emptyList()))
        if(raw.length>210_000)
            return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
        val parts=raw.split("\nsha256:")
        if(parts.size!=2 || parts[1]!=hash(parts[0]))
            return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
        val lines=parts[0].split('\n')
        if(lines.size<5 || lines[0]!="bodyweight:1" ||
            lines[1]!=hash(owner))
            return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
        val revision=lines[2].toLongOrNull()
        val count=lines[3].toIntOrNull()
        if(revision==null || revision<0 || count==null ||
            count !in 0..MAX_RECORDS || lines.size!=5+maxOf(0,count-1))
            return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
        val rowLines=if(count==0)emptyList() else lines.drop(4)
        if(count==0 && lines[4].isNotEmpty())
            return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
        val records=rowLines.map {line->
            val fields=line.split('|')
            if(fields.size!=3)return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
            val date=runCatching{LocalDate.parse(fields[0])}.getOrNull()
                ?:return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
            val amount=fields[1].toBigDecimalOrNull()
                ?:return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
            val unit=LoadUnit.entries.firstOrNull{it.name==fields[2]}
                ?:return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
            NativeBodyweightEntry(date,amount,unit)
        }
        if(records.size!=count ||
            records.map{it.date}.distinct().size!=count ||
            records!=records.sortedBy{it.date} ||
            records.any{validEntry(it,LocalDate.MAX)!=null})
            return NativeBodyweightRead.Rejected(NativeBodyweightRefusal.CORRUPT)
        return NativeBodyweightRead.Ready(NativeBodyweightLedger(revision,records))
    }

    fun hash(text:String):String=MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString(""){"%02x".format(Locale.ROOT,it)}
}

/**
 * Transactional read/compare/commit. A stale screen must refresh rather than
 * replace a newer measurement revision. Corrupt data is NEVER reset on write.
 * The athlete ID is supplied only from the host's verified current setup.
 */
internal class NativeKotlinBodyweightStore(context:Context) {
    private val prefs:SharedPreferences=context.applicationContext
        .getSharedPreferences("pursuit-bodyweight-ledger-v1",Context.MODE_PRIVATE)
    private fun key(owner:String)="ledger:"+NativeBodyweightLedgerDomain.hash(owner)

    fun read(owner:String):NativeBodyweightRead =
        if(!NativeBodyweightLedgerDomain.validOwner(owner))
            NativeBodyweightRead.Rejected(NativeBodyweightRefusal.OWNER_NOT_VERIFIED)
        else runCatching {
            NativeBodyweightLedgerDomain.decode(owner,prefs.getString(key(owner),null))
        }.getOrElse {
            NativeBodyweightRead.Rejected(NativeBodyweightRefusal.STORAGE_UNAVAILABLE)
        }

    @Synchronized
    fun save(owner:String,expectedRevision:Long,record:NativeBodyweightEntry,
             today:LocalDate,replaceExisting:Boolean):NativeBodyweightWrite =
        write(owner,expectedRevision) {
            NativeBodyweightLedgerDomain.replace(it,record,today,replaceExisting)
        }

    @Synchronized
    fun delete(owner:String,expectedRevision:Long,date:LocalDate):NativeBodyweightWrite =
        write(owner,expectedRevision) {
            NativeBodyweightLedgerDomain.delete(it,date)
        }

    private fun write(
        owner:String,expectedRevision:Long,
        change:(NativeBodyweightLedger)->NativeBodyweightWrite
    ):NativeBodyweightWrite {
        val prior=when(val snapshot=read(owner)) {
            is NativeBodyweightRead.Rejected ->
                return NativeBodyweightWrite.Rejected(snapshot.reason)
            is NativeBodyweightRead.Ready ->snapshot.ledger
        }
        if(prior.revision!=expectedRevision)
            return NativeBodyweightWrite.Rejected(NativeBodyweightRefusal.REVISION_CONFLICT)
        return when(val next=change(prior)) {
            is NativeBodyweightWrite.Rejected ->next
            is NativeBodyweightWrite.Saved -> {
                val encoded=runCatching {
                    NativeBodyweightLedgerDomain.encode(owner,next.ledger)
                }.getOrNull() ?:return NativeBodyweightWrite.Rejected(
                    NativeBodyweightRefusal.STORAGE_UNAVAILABLE)
                if(prefs.edit().putString(key(owner),encoded).commit()) next
                else NativeBodyweightWrite.Rejected(NativeBodyweightRefusal.STORAGE_UNAVAILABLE)
            }
        }
    }
}
