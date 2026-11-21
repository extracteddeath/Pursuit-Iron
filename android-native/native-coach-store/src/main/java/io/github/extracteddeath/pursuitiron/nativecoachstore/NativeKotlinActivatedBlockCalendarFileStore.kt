package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/**
 * Independently approved start Monday for an ALREADY activated FUTURE block.
 *
 * This is an immutable, owner-fenced date selection, not a reconstructed
 * historical program activation time. It cannot approve a load/rep/volume
 * edit, release a pending handoff, issue a session or prove week adherence.
 * The underlying source CAS and entire future-block consent must have
 * happened before this calendar can be approved.
 */
data class NativeActivatedBlockCalendar(
    val ownerKey:String,
    val athleteId:String,
    val cycleId:String,
    val blockId:String,
    val sourceRevision:Long,
    val sourceSha256:String,
    val draftRevision:Long,
    val draftSha256:String,
    val firstAbsoluteWeek:Int,
    val firstWeekMonday:LocalDate,
    val timezoneId:String,
    val scheduledWeekdays:Set<Int>,
    /** Observed by the durable native acceptance transaction, not the UI. */
    val acceptedAtEpochMillis:Long,
    val approvalFingerprint:String
) {
    val canIssueWorkout:Boolean get()=false
    val canChangeProgram:Boolean get()=false
    val canInferPriorBlockActivation:Boolean get()=false
    val canClaimAdherence:Boolean get()=false
}

enum class NativeActivatedBlockCalendarRefusal {
    NOT_AUTHORIZED, NO_APPLIED_ACTIVATION, STALE_SOURCE_OR_DRAFT,
    INVALID_LOCAL_MONDAY, CONFIRMATION_REQUIRED, PREVIEW_CHANGED,
    ALREADY_APPROVED_DIFFERENT_DATE, CALENDAR_UNAVAILABLE,
    CORRUPT_RECORD, STORAGE_UNAVAILABLE
}

sealed interface NativeActivatedBlockCalendarPreview {
    data class Ready(
        val proposed:NativeActivatedBlockCalendar,
        val fingerprint:String
    ):NativeActivatedBlockCalendarPreview {
        val canIssueWorkout:Boolean get()=false
    }
    data class Rejected(val reason:NativeActivatedBlockCalendarRefusal):
        NativeActivatedBlockCalendarPreview
}
sealed interface NativeActivatedBlockCalendarRead {
    data object Missing:NativeActivatedBlockCalendarRead
    data class Found(val calendar:NativeActivatedBlockCalendar):
        NativeActivatedBlockCalendarRead
    data class Rejected(val reason:NativeActivatedBlockCalendarRefusal):
        NativeActivatedBlockCalendarRead
}
sealed interface NativeActivatedBlockCalendarWrite {
    data class Committed(
        val calendar:NativeActivatedBlockCalendar,
        val replayed:Boolean
    ):NativeActivatedBlockCalendarWrite
    data class Rejected(val reason:NativeActivatedBlockCalendarRefusal):
        NativeActivatedBlockCalendarWrite
}

/**
 * Restore inventory fact only. An archived valid calendar is preserved even
 * when its original program is no longer the active one. A stale calendar
 * must NEVER be accepted as fresh program approval.
 */
internal data class NativeActivatedBlockCalendarInventoryFact(
    val calendar:NativeActivatedBlockCalendar,
    val sourceStillCurrent:Boolean
)
internal sealed interface NativeActivatedBlockCalendarInventory {
    data class Verified(val records:List<NativeActivatedBlockCalendarInventoryFact>):
        NativeActivatedBlockCalendarInventory
    data object InvalidOrForeign:NativeActivatedBlockCalendarInventory
}

private data class ActivatedBlockCalendarSource(
    val ownerKey:String,
    val athleteId:String,
    val cycleId:String,
    val blockId:String,
    val sourceRevision:Long,
    val sourceSha256:String,
    val draftRevision:Long,
    val draftSha256:String,
    val firstWeek:Int,
    val timezoneId:String,
    val schedule:Set<Int>
)

/**
 * Crash-safe one-time calendar approval, bound to exact activated source
 * and saved draft revision. A SHA envelope detects accidental/physical
 * corruption; it is not a signature or proof of device clock integrity.
 */
class NativeKotlinActivatedBlockCalendarFileStore(
    private val privateDirectory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val activations:NativeKotlinFutureActivationCommitTransaction,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val clock:NativeHistoryCompletionClock=NativeHistoryCompletionClock.System
) {
    private val validId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val validSha=Regex("^[a-f0-9]{64}$")
    private val magic=0x50494241 // PIBA
    private val version=1
    private val maxSize=8192

    private fun sha(bytes:ByteArray):String=MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun active(ownerKey:String)=runCatching {identity.signedInIdentity()}
        .getOrNull()?.takeIf {
            validId.matches(ownerKey) && it.ownerKey==ownerKey &&
                validId.matches(it.athleteId)
        }
    private fun path(owner:String,cycle:String,block:String):Path =
        privateDirectory.resolve("activated-block-calendar-"+
            sha((owner+"\u0000"+cycle+"\u0000"+block).toByteArray(Charsets.UTF_8))+
            ".bin")

    private fun source(ownerKey:String,cycleId:String,blockId:String):
        ActivatedBlockCalendarSource? {
        val signed=active(ownerKey)?:return null
        val activation=(activations.read(ownerKey) as?
            NativeFutureCommitReview.Applied)?.context ?:return null
        if(activation.cycleId!=cycleId || activation.blockId!=blockId ||
            activation.firstWeek !in 2..104)return null
        val current=(sources.read(ownerKey) as?
            NativeAuthoringSourceRead.Found)?.source ?:return null
        val cycle=(drafts.read(ownerKey,cycleId) as?
            NativeCustomDraftReadResult.Found)?.draft ?:return null
        val profile=current.setup.reviewed ?:return null
        val index=cycle.blocks.indexOfFirst{it.blockId==blockId}
        if(index<=0 || cycle.revision<1 || index>=cycle.blocks.size ||
            cycle.athleteId!=signed.athleteId || current.athleteId!=signed.athleteId ||
            current.ownerKey!=ownerKey || profile.athleteProfileId!=signed.athleteId ||
            current.setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            current.revision!=activation.sourceRevision ||
            cycle.blocks.map{it.blockId}.distinct().size!=cycle.blocks.size ||
            cycle.blocks.any{it.weeks<=0} ||
            1+cycle.blocks.take(index).sumOf{it.weeks}!=activation.firstWeek)
            return null
        val block=cycle.blocks[index]
        val scheduled=block.days.map{it.weekday}
        if(block.purpose!=CustomBlockPurpose.TRAINING ||
            block.phase!=activation.policy.phase ||
            profile.split!=activation.policy.split ||
            profile.trainingWeekdays.size !in 3..6 ||
            scheduled.size!=scheduled.distinct().size ||
            scheduled.toSet()!=profile.trainingWeekdays ||
            scheduled.any{it !in 0..6} ||
            activation.policy.trainingDays!=scheduled.size)
            return null
        val zone=runCatching{ZoneId.of(profile.timezoneId)}.getOrNull()
            ?:return null
        if(zone.id!=profile.timezoneId || active(ownerKey)!=signed)return null
        return ActivatedBlockCalendarSource(
            ownerKey,signed.athleteId,cycleId,blockId,
            current.revision,
            sha(NativeKotlinCustomAuthoringSourceCodec.encode(current)),
            cycle.revision,sha(NativeCustomCycleDraftCodec.encode(cycle)),
            activation.firstWeek,profile.timezoneId,
            scheduled.toSortedSet().toSet())
    }

    private fun fingerprint(
        source:ActivatedBlockCalendarSource, monday:LocalDate
    ):String {
        val data=ByteArrayOutputStream()
        DataOutputStream(data).use {out->
            listOf(source.ownerKey,source.athleteId,source.cycleId,
                source.blockId,source.sourceSha256,source.draftSha256,
                source.timezoneId,monday.toString()).forEach(out::writeUTF)
            out.writeLong(source.sourceRevision);out.writeLong(source.draftRevision)
            out.writeInt(source.firstWeek)
            out.writeInt(source.schedule.size)
            source.schedule.sorted().forEach(out::writeInt)
        }
        return sha(data.toByteArray())
    }

    private fun bind(
        source:ActivatedBlockCalendarSource,
        date:LocalDate,at:Long
    ):NativeActivatedBlockCalendar=NativeActivatedBlockCalendar(
        source.ownerKey,source.athleteId,source.cycleId,source.blockId,
        source.sourceRevision,source.sourceSha256,
        source.draftRevision,source.draftSha256,source.firstWeek,
        date,source.timezoneId,source.schedule,at,fingerprint(source,date))

    private fun sameSource(
        saved:NativeActivatedBlockCalendar,
        current:ActivatedBlockCalendarSource
    ):Boolean {
        val matched=bind(current,saved.firstWeekMonday,saved.acceptedAtEpochMillis)
        return matched==saved
    }

    private fun encode(record:NativeActivatedBlockCalendar):ByteArray {
        require(listOf(record.ownerKey,record.athleteId,record.cycleId,
            record.blockId).all(validId::matches))
        require(listOf(record.sourceSha256,record.draftSha256,
            record.approvalFingerprint).all(validSha::matches))
        require(record.sourceRevision>0 && record.draftRevision>0 &&
            record.firstAbsoluteWeek in 2..104 &&
            record.firstWeekMonday.dayOfWeek==DayOfWeek.MONDAY &&
            record.scheduledWeekdays.size in 3..6 &&
            record.scheduledWeekdays.all{it in 0..6} &&
            NativeHistoryCompletionClockValidity.valid(record.acceptedAtEpochMillis))
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {out->
            out.writeInt(magic);out.writeInt(version)
            listOf(record.ownerKey,record.athleteId,record.cycleId,
                record.blockId).forEach(out::writeUTF)
            out.writeLong(record.sourceRevision);out.writeUTF(record.sourceSha256)
            out.writeLong(record.draftRevision);out.writeUTF(record.draftSha256)
            out.writeInt(record.firstAbsoluteWeek)
            out.writeUTF(record.firstWeekMonday.toString())
            out.writeUTF(record.timezoneId)
            out.writeInt(record.scheduledWeekdays.size)
            record.scheduledWeekdays.sorted().forEach(out::writeInt)
            out.writeLong(record.acceptedAtEpochMillis)
            out.writeUTF(record.approvalFingerprint)
        }
        val payload=bytes.toByteArray()
        require(payload.size+36<=maxSize)
        return ByteArrayOutputStream().also{buffer->
            DataOutputStream(buffer).use{out->
                out.writeInt(payload.size);out.write(payload)
                out.write(MessageDigest.getInstance("SHA-256").digest(payload))
            }
        }.toByteArray()
    }

    private fun decode(file:Path):NativeActivatedBlockCalendar? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||
            Files.size(file) !in 44L..maxSize.toLong())
            throw IOException("Invalid private calendar file")
        val bytes=Files.readAllBytes(file)
        val input=DataInputStream(ByteArrayInputStream(bytes))
        val count=input.readInt()
        if(count !in 8..(maxSize-36)||count+36!=bytes.size)
            throw IOException("Invalid calendar envelope")
        val body=ByteArray(count);input.readFully(body)
        val digest=ByteArray(32);input.readFully(digest)
        if(!MessageDigest.isEqual(digest,
            MessageDigest.getInstance("SHA-256").digest(body)))
            throw IOException("Calendar checksum mismatch")
        return try {
            DataInputStream(ByteArrayInputStream(body)).use{data->
                if(data.readInt()!=magic||data.readInt()!=version)
                    throw IOException("Calendar version mismatch")
                val owner=data.readUTF();val athlete=data.readUTF()
                val cycle=data.readUTF();val block=data.readUTF()
                val sourceRevision=data.readLong();val sourceSha=data.readUTF()
                val draftRevision=data.readLong();val draftSha=data.readUTF()
                val firstWeek=data.readInt()
                val monday=LocalDate.parse(data.readUTF())
                val zone=data.readUTF()
                val countDays=data.readInt()
                if(countDays !in 3..6)throw IOException("Invalid scheduled day count")
                val days=(0 until countDays).map{data.readInt()}
                val at=data.readLong();val fp=data.readUTF()
                if(data.available()!=0||days.distinct().size!=days.size ||
                    days.any{it !in 0..6} ||
                    !validSha.matches(fp))
                    throw IOException("Invalid calendar metadata")
                val record=NativeActivatedBlockCalendar(
                    owner,athlete,cycle,block,sourceRevision,sourceSha,
                    draftRevision,draftSha,firstWeek,monday,zone,
                    days.toSet(),at,fp)
                encode(record) // schema/field validation, not trust in checksum
                val candidate=ActivatedBlockCalendarSource(
                    owner,athlete,cycle,block,sourceRevision,sourceSha,
                    draftRevision,draftSha,firstWeek,zone,days.toSet())
                if(fingerprint(candidate,monday)!=fp)
                    throw IOException("Calendar fingerprint mismatch")
                record
            }
        }catch(error:Exception) {
            throw IOException("Invalid calendar record",error)
        }
    }

    private fun writeAtomically(file:Path,bytes:ByteArray) {
        Files.createDirectories(privateDirectory)
        if(Files.isSymbolicLink(privateDirectory)||
            !Files.isDirectory(privateDirectory))
            throw IOException("Unsafe calendar directory")
        val pending=Files.createTempFile(privateDirectory,
            "activated-block-calendar-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE)
            }catch(error:AtomicMoveNotSupportedException) {
                throw IOException("Atomic calendar rename unavailable",error)
            }
        }finally {Files.deleteIfExists(pending)}
    }

    private fun localMondayAllowed(
        monday:LocalDate,zoneId:String,now:Long
    ):Boolean=runCatching {
        if(monday.dayOfWeek!=DayOfWeek.MONDAY ||
            monday.year !in 2000..2100)return false
        val local=Instant.ofEpochMilli(now).atZone(ZoneId.of(zoneId)).toLocalDate()
        val thisMonday=local.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        !monday.isBefore(thisMonday) &&
            !monday.isAfter(thisMonday.plusWeeks(12))
    }.getOrDefault(false)

    fun preview(ownerKey:String,cycleId:String,blockId:String,
        firstWeekMonday:LocalDate):NativeActivatedBlockCalendarPreview {
        if(!listOf(ownerKey,cycleId,blockId).all(validId::matches) ||
            active(ownerKey)==null)
            return NativeActivatedBlockCalendarPreview.Rejected(
                NativeActivatedBlockCalendarRefusal.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey) {
            val owner=active(ownerKey) ?:return@withOwnerTransaction NativeActivatedBlockCalendarPreview.Rejected(
                    NativeActivatedBlockCalendarRefusal.NOT_AUTHORIZED)
            val current=source(ownerKey,cycleId,blockId)
                ?:return@withOwnerTransaction NativeActivatedBlockCalendarPreview.Rejected(
                    NativeActivatedBlockCalendarRefusal.NO_APPLIED_ACTIVATION)
            val now=clock.observe()?:return@withOwnerTransaction NativeActivatedBlockCalendarPreview.Rejected(
                    NativeActivatedBlockCalendarRefusal.CALENDAR_UNAVAILABLE)
            if(!localMondayAllowed(firstWeekMonday,current.timezoneId,now))
                return@withOwnerTransaction NativeActivatedBlockCalendarPreview.Rejected(
                    NativeActivatedBlockCalendarRefusal.INVALID_LOCAL_MONDAY)
            val old=try{decode(path(ownerKey,cycleId,blockId))}
                catch(_:Exception){return@withOwnerTransaction NativeActivatedBlockCalendarPreview.Rejected(
                        NativeActivatedBlockCalendarRefusal.CORRUPT_RECORD)}
            if(old!=null && !sameSource(old,current))
                return@withOwnerTransaction NativeActivatedBlockCalendarPreview.Rejected(
                    NativeActivatedBlockCalendarRefusal.STALE_SOURCE_OR_DRAFT)
            if(old!=null && old.firstWeekMonday!=firstWeekMonday)
                return@withOwnerTransaction NativeActivatedBlockCalendarPreview.Rejected(
                    NativeActivatedBlockCalendarRefusal.ALREADY_APPROVED_DIFFERENT_DATE)
            if(active(ownerKey)!=owner)return@withOwnerTransaction NativeActivatedBlockCalendarPreview.Rejected(
                    NativeActivatedBlockCalendarRefusal.NOT_AUTHORIZED)
            val record=old?:bind(current,firstWeekMonday,now)
            NativeActivatedBlockCalendarPreview.Ready(
                record,record.approvalFingerprint)
        }}.getOrElse{NativeActivatedBlockCalendarPreview.Rejected(
            NativeActivatedBlockCalendarRefusal.STORAGE_UNAVAILABLE)}
    }

    fun confirm(ownerKey:String,cycleId:String,blockId:String,
        firstWeekMonday:LocalDate,expectedFingerprint:String,
        athleteConfirmed:Boolean):NativeActivatedBlockCalendarWrite {
        if(!athleteConfirmed)return NativeActivatedBlockCalendarWrite.Rejected(
            NativeActivatedBlockCalendarRefusal.CONFIRMATION_REQUIRED)
        if(!validSha.matches(expectedFingerprint))
            return NativeActivatedBlockCalendarWrite.Rejected(
                NativeActivatedBlockCalendarRefusal.PREVIEW_CHANGED)
        return runCatching{fence.withOwnerTransaction(ownerKey) {
            val offered=preview(ownerKey,cycleId,blockId,firstWeekMonday)
            if(offered !is NativeActivatedBlockCalendarPreview.Ready)
                return@withOwnerTransaction NativeActivatedBlockCalendarWrite.Rejected(
                    (offered as NativeActivatedBlockCalendarPreview.Rejected).reason)
            if(offered.fingerprint!=expectedFingerprint)
                return@withOwnerTransaction NativeActivatedBlockCalendarWrite.Rejected(
                    NativeActivatedBlockCalendarRefusal.PREVIEW_CHANGED)
            val path=path(ownerKey,cycleId,blockId)
            val existing=decode(path)
            if(existing!=null) {
                if(existing!=offered.proposed)
                    return@withOwnerTransaction NativeActivatedBlockCalendarWrite.Rejected(
                        NativeActivatedBlockCalendarRefusal.ALREADY_APPROVED_DIFFERENT_DATE)
                return@withOwnerTransaction NativeActivatedBlockCalendarWrite.Committed(
                    existing,true)
            }
            // The source, current owner, clock, and exact preview content were
            // all replayed under the owner's transaction lock.
            writeAtomically(path,encode(offered.proposed))
            NativeActivatedBlockCalendarWrite.Committed(offered.proposed,false)
        }}.getOrElse{NativeActivatedBlockCalendarWrite.Rejected(
            NativeActivatedBlockCalendarRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * PHYSICAL restore inventory. Walk every native calendar record, including
     * retired blocks: a checksum-valid foreign/renamed/symlinked/unfinished
     * file cannot disappear merely because read() only looks up active IDs.
     * The current flag is advisory, never an activation or issuance grant.
     */
    internal fun inventoryOwner(ownerKey:String):
        NativeActivatedBlockCalendarInventory =
        runCatching{fence.withOwnerTransaction(ownerKey) {
            val signed=active(ownerKey)
                ?:return@withOwnerTransaction NativeActivatedBlockCalendarInventory.InvalidOrForeign
            val noFollow=java.nio.file.LinkOption.NOFOLLOW_LINKS
            if(!Files.exists(privateDirectory,noFollow))
                return@withOwnerTransaction NativeActivatedBlockCalendarInventory.Verified(emptyList())
            require(!Files.isSymbolicLink(privateDirectory) &&
                Files.isDirectory(privateDirectory,noFollow))
            val physical=Files.list(privateDirectory).use {stream->
                stream.filter{it.fileName.toString().startsWith(
                    "activated-block-calendar-")}
                    .limit(4097).toList()
            }
            require(physical.size<=4096)
            val name=Regex("^activated-block-calendar-[a-f0-9]{64}\\.bin$")
            val seen=mutableSetOf<Pair<String,String>>()
            val entries=physical.sortedBy{it.fileName.toString()}.map{file->
                require(name.matches(file.fileName.toString()) &&
                    !Files.isSymbolicLink(file) &&
                    Files.isRegularFile(file,noFollow))
                val record=decode(file)
                    ?:throw IOException("Missing calendar record")
                require(record.ownerKey==ownerKey &&
                    record.athleteId==signed.athleteId &&
                    file==path(record.ownerKey,record.cycleId,record.blockId) &&
                    seen.add(record.cycleId to record.blockId))
                val current=source(ownerKey,record.cycleId,record.blockId)
                NativeActivatedBlockCalendarInventoryFact(record,
                    current!=null && sameSource(record,current))
            }
            require(active(ownerKey)==signed)
            NativeActivatedBlockCalendarInventory.Verified(entries)
        }}.getOrElse{NativeActivatedBlockCalendarInventory.InvalidOrForeign}

    fun read(ownerKey:String,cycleId:String,blockId:String):
        NativeActivatedBlockCalendarRead {
        if(!listOf(ownerKey,cycleId,blockId).all(validId::matches) ||
            active(ownerKey)==null)
            return NativeActivatedBlockCalendarRead.Rejected(
                NativeActivatedBlockCalendarRefusal.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey) {
            val who=active(ownerKey)?:return@withOwnerTransaction NativeActivatedBlockCalendarRead.Rejected(
                    NativeActivatedBlockCalendarRefusal.NOT_AUTHORIZED)
            val saved=try{decode(path(ownerKey,cycleId,blockId))}
                catch(_:Exception){return@withOwnerTransaction NativeActivatedBlockCalendarRead.Rejected(
                        NativeActivatedBlockCalendarRefusal.CORRUPT_RECORD)}
                ?:return@withOwnerTransaction NativeActivatedBlockCalendarRead.Missing
            if(saved.ownerKey!=who.ownerKey ||
                saved.athleteId!=who.athleteId ||
                saved.cycleId!=cycleId || saved.blockId!=blockId)
                return@withOwnerTransaction NativeActivatedBlockCalendarRead.Rejected(
                    NativeActivatedBlockCalendarRefusal.NOT_AUTHORIZED)
            val current=source(ownerKey,cycleId,blockId)
                ?:return@withOwnerTransaction NativeActivatedBlockCalendarRead.Rejected(
                    NativeActivatedBlockCalendarRefusal.STALE_SOURCE_OR_DRAFT)
            if(!sameSource(saved,current))
                return@withOwnerTransaction NativeActivatedBlockCalendarRead.Rejected(
                    NativeActivatedBlockCalendarRefusal.STALE_SOURCE_OR_DRAFT)
            NativeActivatedBlockCalendarRead.Found(saved)
        }}.getOrElse{NativeActivatedBlockCalendarRead.Rejected(
            NativeActivatedBlockCalendarRefusal.STORAGE_UNAVAILABLE)}
    }
}

/** Reuse the *same* accepted Finish timestamp range: no independent date
 * threshold can quietly turn an invalid device clock into an approved epoch. */
private object NativeHistoryCompletionClockValidity {
    fun valid(value:Long):Boolean=KotlinHistoryCompletionTime.valid(value)
}
