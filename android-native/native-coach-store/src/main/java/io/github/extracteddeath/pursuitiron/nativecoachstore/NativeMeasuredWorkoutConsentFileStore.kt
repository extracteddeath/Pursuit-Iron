package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/** Durable, explicitly athlete-confirmed typed-session authorization. */
enum class NativeMeasuredConsentRefusal {
    INVALID_REQUEST, NOT_AUTHORIZED, SOURCE_UNAVAILABLE, SOURCE_CHANGED,
    UNVERIFIED_GYM_OR_EXERCISE, HISTORY_NOT_READY, WORKOUT_ID_CONFLICT,
    ALREADY_APPROVED, CONFIRMATION_REQUIRED, CORRUPT_RECORD,
    STORAGE_UNAVAILABLE
}
sealed interface NativeMeasuredConsentResult {
    /** Source-revalidated ONLY. No journal or issuance rights. */
    data class Preview(
        val plan:MeasuredWorkoutPlan,
        val sourceRevision:Long
    ):NativeMeasuredConsentResult {
        val mayIssue:Boolean get()=false
    }
    data class Approved(
        val approval:NativeMeasuredApprovedWorkout,
        val replayed:Boolean
    ):NativeMeasuredConsentResult
    data class Rejected(val reason:NativeMeasuredConsentRefusal):
        NativeMeasuredConsentResult
}

/** Reopenable athlete confirmation, not an issued working-set journal. */
data class NativeMeasuredConfirmedConsent(
    val workoutId:String,
    val exerciseIds:List<String>,
    val plannedSets:Int
)

enum class NativeMeasuredConsentInventoryRefusal {
    INVALID_OWNER, NOT_AUTHORIZED, SOURCE_UNAVAILABLE, HISTORY_NOT_READY,
    CORRUPT_RECORD
}

sealed interface NativeMeasuredConsentInventoryResult {
    data class Ready(val confirmed:List<NativeMeasuredConfirmedConsent>):
        NativeMeasuredConsentInventoryResult
    data class Rejected(val reason:NativeMeasuredConsentInventoryRefusal):
        NativeMeasuredConsentInventoryResult
}

/**
 * Independent, owner-fenced source for typed measured workouts.
 *
 * A UI-submitted plan is NEVER an issuance grant. The athlete must first
 * explicitly confirm source-reviewed seconds/cm/banded-rep quantities; the
 * resulting immutable consent is saved atomically. Parent.issue() independently
 * reopens this exact owner-approved consent against the CURRENT signed-in
 * source, before creating any durable measured journal. A gym/source change
 * invalidates an unissued consent. Issued measured journals retain historical
 * source facts without rewriting older results.
 *
 * This is one explicitly authored measured *session*, not automatic migration
 * of JS practical-reps fields or a claim that a custom cycle includes it.
 */
class NativeMeasuredWorkoutConsentFileStore(
    private val root:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val history:NativeKotlinHistoryManifestFileStore,
    private val composer:KotlinNativeJournalFileStore,
    private val custom:NativeKotlinCustomWorkoutJournalFileStore
):NativeMeasuredApprovedWorkoutSource {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val maxBytes=NativeMeasuredWorkoutParentCodec.MAX_BYTES
    private fun reject(reason:NativeMeasuredConsentRefusal)=
        NativeMeasuredConsentResult.Rejected(reason)

    private fun active(owner:String,athlete:String?=null):NativeKotlinOwnerIdentity? =
        runCatching{identity.signedInIdentity()}.getOrNull()?.takeIf{
            id.matches(owner) && it.ownerKey==owner && id.matches(it.athleteId) &&
                (athlete==null || it.athleteId==athlete)
        }

    private fun path(owner:String,workout:String):Path {
        val value=(owner+"\u0000"+workout).toByteArray(Charsets.UTF_8)
        val hash=MessageDigest.getInstance("SHA-256").digest(value)
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return root.resolve("measured-consent-$hash.bin")
    }

    private fun readFile(owner:String,athlete:String,workout:String):
        NativeMeasuredWorkoutStored? {
        val file=path(owner,workout)
        if(Files.isSymbolicLink(file))throw IOException("linked measured consent")
        if(!Files.exists(file))return null
        if(!Files.isRegularFile(file) || Files.size(file) !in 44L..maxBytes.toLong())
            throw IOException("invalid measured consent")
        val saved=NativeMeasuredWorkoutParentCodec.decode(
            Files.readAllBytes(file),owner,athlete)
        if(saved.journal.approvedPlan.workoutId!=workout ||
            saved.journal.receipts.isNotEmpty() ||
            saved.journal.revision!=0L ||
            saved.journal.finishedByEventId!=null ||
            saved.approvalRevision!=saved.journal.approvedPlan.gymRevision)
            throw IOException("measured consent is not a frozen zero-event plan")
        return saved
    }

    private fun atomic(file:Path,saved:NativeMeasuredWorkoutStored) {
        val bytes=NativeMeasuredWorkoutParentCodec.encode(saved)
        if(Files.isSymbolicLink(root))throw IOException("linked root")
        Files.createDirectories(root)
        if(!Files.isDirectory(root) || Files.isSymbolicLink(root) ||
            Files.isSymbolicLink(file) || Files.exists(file))
            throw IOException("unsafe or already approved measured consent")
        val temp=Files.createTempFile(root,"measured-consent-",".pending")
        try {
            FileChannel.open(temp,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            // The owner revision lock serializes competing consent writes.
            try {
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE)
            } catch(ex:AtomicMoveNotSupportedException) {
                throw IOException("atomic consent creation unavailable",ex)
            }
        } finally { Files.deleteIfExists(temp) }
    }

    private fun source(owner:String,athlete:String):NativeCustomAuthoringSnapshot? {
        val read=sources.read(owner) as? NativeAuthoringSourceRead.Found
            ?:return null
        return read.source.takeIf{
            it.ownerKey==owner && it.athleteId==athlete &&
                it.revision>=1 &&
                it.setup.mayReviewProgramCandidate &&
                it.setup.reviewed?.athleteProfileId==athlete
        }
    }

    /** Every physical claim is checked against the athlete's CURRENT gym. */
    private fun validated(
        plan:MeasuredWorkoutPlan,s:NativeCustomAuthoringSnapshot
    ):Boolean {
        val reviewed=s.setup.reviewed ?:return false
        val gym=reviewed.gym
        if(plan.gymId!=gym.gymId || plan.gymRevision!=s.revision ||
            plan.sets.isEmpty() || plan.sets.size>64)return false
        val fixtures=gym.availableFacilities+gym.availableAttachments
        // Existing native onboarding labels have historically used these
        // different stable IDs. An alias is only true if its selected source
        // fixture is actually present; never manufacture new equipment.
        val witnessed=fixtures+
            (if("pullup-bar" in fixtures)setOf("pull-up-bar") else emptySet())+
            (if("wall-support" in fixtures)setOf("stable-wall") else emptySet())
        for(planSet in plan.sets) {
            val definition=MeasuredExerciseCatalogDomain.find(planSet.exerciseId)
                ?:return false
            // The recorded station must be an actual selected physical
            // fixture, not an arbitrary UI-entered label masquerading as
            // a rack, wall, hanging station or verified band setup.
            val sourceSetups=definition.requiredFixtures+
                (if("pull-up-bar" in definition.requiredFixtures)
                    setOf("pullup-bar") else emptySet())+
                (if("stable-wall" in definition.requiredFixtures)
                    setOf("wall-support") else emptySet())
            if(sourceSetups.isNotEmpty()) {
                if(planSet.physicalSetupId !in sourceSetups)return false
            } else if(definition.kind==MeasuredExerciseKind.CARRY_CENTIMETERS) {
                // A carry happens with real held bells, not on a fictitious
                // "floor" station. Match the same verified physical rack.
                if(planSet.physicalSetupId!=
                    MeasuredPhysicalStockSource.equipmentIdFor(planSet.exerciseId))
                    return false
            } else if(planSet.physicalSetupId !in witnessed &&
                planSet.physicalSetupId !in setOf("floor","mat-floor"))return false
            if(planSet.exerciseId in reviewed.excludedExerciseIds ||
                planSet.confirmedFixtures.any{it !in witnessed} ||
                planSet.gymConfirmedBandIds.any{it !in gym.availableAttachments} ||
                MeasuredExerciseDomain.inspect(planSet)
                    !is MeasurementPlanResult.Candidate)
                return false
            // The general attachment inventory also includes ropes, handles
            // and straps. Never mistake one of those for a resistance band.
            // "band-..." is an explicit physical band setup ID saved by the
            // athlete in their verified gym, not a mass-equivalent load.
            if(definition.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT) {
                val band=planSet.bandId
                if(band==null || !band.startsWith("band-") ||
                    band.length<=5 || planSet.gymConfirmedBandIds!=setOf(band))
                    return false
            }
            if((definition.loadKind in setOf(MeasuredLoadKind.UNWEIGHTED,
                    MeasuredLoadKind.BANDED_BODYWEIGHT) ||
                    planSet.exerciseId=="weighted-plank") &&
                !reviewed.allowBodyweight)
                return false
            if(definition.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT &&
                "bands" !in gym.availableFacilities)return false
            if(definition.loadKind in setOf(
                    MeasuredLoadKind.VERIFIED_ADDED_LOAD,
                    MeasuredLoadKind.VERIFIED_PER_HAND)) {
                // Source the physical implement directly from THIS athlete's
                // reviewed gym; the repetition catalog intentionally contains
                // none of these typed measured exercises. Never forge a fake
                // rep-catalog entry just to make a measured carry issuable.
                val equipment=MeasuredPhysicalStockSource.equipmentIdFor(
                    planSet.exerciseId) ?:return false
                val stock=gym.confirmedStock[equipment] ?:return false
                if(stock.basis!=definition.expectedBasis ||
                    stock!=planSet.physicalStock || stock.unit!=planSet.unit ||
                    gym.unit!=planSet.unit)return false
                if(definition.kind==MeasuredExerciseKind.CARRY_CENTIMETERS &&
                    "dumbbells" !in gym.availableFacilities)return false
                if(planSet.exerciseId=="farmers" &&
                    !planSet.matchedPairConfirmed)return false
            }
        }
        return MeasuredWorkoutJournalDomain.begin(plan)!=null
    }

    private fun available(owner:String,athlete:String,workout:String):
        NativeMeasuredConsentRefusal? {
        val ledger=(history.open(owner,athlete) as?
            NativeHistoryLedgerResult.Durable)?.ledger
            ?:return NativeMeasuredConsentRefusal.HISTORY_NOT_READY
        if(!ledger.readyForHistoryReads || !ledger.hasCompletionCapacity ||
            ledger.ownerKey!=owner || ledger.athleteId!=athlete ||
            ledger.completions.any{it.workoutId==workout})
            return NativeMeasuredConsentRefusal.HISTORY_NOT_READY
        val ordinary=composer.openStored(owner,workout){o,src->
            o==owner && src.athleteId==athlete}
        if(ordinary !is NativeJournalFileResult.Rejected ||
            ordinary.reason!=NativeJournalFileRefusal.NOT_ISSUED)
            return NativeMeasuredConsentRefusal.WORKOUT_ID_CONFLICT
        val customRead=custom.open(owner,workout)
        if(customRead !is NativeCustomJournalResult.Rejected ||
            customRead.reason!=NativeCustomJournalRefusal.NOT_ISSUED)
            return NativeMeasuredConsentRefusal.WORKOUT_ID_CONFLICT
        if(NativeMeasuredWorkoutIdClaim.claimed(root,owner,workout))
            return NativeMeasuredConsentRefusal.WORKOUT_ID_CONFLICT
        return null
    }

    fun preview(owner:String,plan:MeasuredWorkoutPlan,
        expectedSourceRevision:Long):NativeMeasuredConsentResult {
        if(!id.matches(owner) || !id.matches(plan.workoutId) ||
            expectedSourceRevision<1 || plan.gymRevision!=expectedSourceRevision)
            return reject(NativeMeasuredConsentRefusal.INVALID_REQUEST)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                val signed=active(owner)
                    ?:return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.NOT_AUTHORIZED)
                val s=source(owner,signed.athleteId)
                    ?:return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.SOURCE_UNAVAILABLE)
                if(s.revision!=expectedSourceRevision)
                    return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.SOURCE_CHANGED)
                if(!validated(plan,s))
                    return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.UNVERIFIED_GYM_OR_EXERCISE)
                val existing=readFile(owner,signed.athleteId,plan.workoutId)
                if(existing!=null)
                    return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.ALREADY_APPROVED)
                val blocked=available(owner,signed.athleteId,plan.workoutId)
                if(blocked!=null)return@withOwnerTransaction reject(blocked)
                if(active(owner)!=signed)
                    return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.NOT_AUTHORIZED)
                NativeMeasuredConsentResult.Preview(plan,s.revision)
            }
        }.getOrElse{reject(NativeMeasuredConsentRefusal.STORAGE_UNAVAILABLE)}
    }

    /** A second complete source+history re-read occurs INSIDE this write. */
    fun confirm(owner:String,plan:MeasuredWorkoutPlan,
        expectedSourceRevision:Long,athleteConfirmed:Boolean
    ):NativeMeasuredConsentResult {
        if(!athleteConfirmed)
            return reject(NativeMeasuredConsentRefusal.CONFIRMATION_REQUIRED)
        if(!id.matches(owner) || !id.matches(plan.workoutId) ||
            expectedSourceRevision<1 || plan.gymRevision!=expectedSourceRevision)
            return reject(NativeMeasuredConsentRefusal.INVALID_REQUEST)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                val signed=active(owner)
                    ?:return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.NOT_AUTHORIZED)
                val s=source(owner,signed.athleteId)
                    ?:return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.SOURCE_UNAVAILABLE)
                if(s.revision!=expectedSourceRevision)
                    return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.SOURCE_CHANGED)
                if(!validated(plan,s))
                    return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.UNVERIFIED_GYM_OR_EXERCISE)
                val existing=readFile(owner,signed.athleteId,plan.workoutId)
                if(existing!=null) {
                    return@withOwnerTransaction if(existing.journal.approvedPlan==plan)
                        NativeMeasuredConsentResult.Approved(
                            NativeMeasuredApprovedWorkout(plan,existing.approvalId,
                                existing.approvalRevision),true)
                    else reject(NativeMeasuredConsentRefusal.ALREADY_APPROVED)
                }
                val blocked=available(owner,signed.athleteId,plan.workoutId)
                if(blocked!=null)return@withOwnerTransaction reject(blocked)
                val journal=MeasuredWorkoutJournalDomain.begin(plan)
                    ?:return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.UNVERIFIED_GYM_OR_EXERCISE)
                val record=NativeMeasuredWorkoutStored(owner,signed.athleteId,
                    "measured-approved-"+UUID.randomUUID().toString().replace("-",""),
                    s.revision,journal)
                if(active(owner)!=signed)
                    return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.NOT_AUTHORIZED)
                atomic(path(owner,plan.workoutId),record)
                if(readFile(owner,signed.athleteId,plan.workoutId)!=record)
                    return@withOwnerTransaction reject(
                        NativeMeasuredConsentRefusal.STORAGE_UNAVAILABLE)
                NativeMeasuredConsentResult.Approved(
                    NativeMeasuredApprovedWorkout(plan,record.approvalId,
                        record.approvalRevision),false)
            }
        }.getOrElse{reject(NativeMeasuredConsentRefusal.STORAGE_UNAVAILABLE)}
    }


    /**
     * Find athlete-confirmed sessions that survived a crash BEFORE issuance.
     * Only the immutable zero-event consent is listed. A live source, History,
     * equipment and absence of a competing journal must ALL still match.
     * Issuance subsequently rechecks the exact consent under the owner lock.
     * Scanning is read-only; malformed archives block the entire inventory.
     */
    fun reviewConfirmedUnissued(owner:String):NativeMeasuredConsentInventoryResult {
        if(!id.matches(owner))
            return NativeMeasuredConsentInventoryResult.Rejected(
                NativeMeasuredConsentInventoryRefusal.INVALID_OWNER)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                val signed=active(owner)
                    ?:return@withOwnerTransaction NativeMeasuredConsentInventoryResult.Rejected(
                        NativeMeasuredConsentInventoryRefusal.NOT_AUTHORIZED)
                val current=source(owner,signed.athleteId)
                    ?:return@withOwnerTransaction NativeMeasuredConsentInventoryResult.Rejected(
                        NativeMeasuredConsentInventoryRefusal.SOURCE_UNAVAILABLE)
                val ledger=(history.open(owner,signed.athleteId) as?
                    NativeHistoryLedgerResult.Durable)?.ledger
                    ?:return@withOwnerTransaction NativeMeasuredConsentInventoryResult.Rejected(
                        NativeMeasuredConsentInventoryRefusal.HISTORY_NOT_READY)
                if(!ledger.readyForHistoryReads)
                    return@withOwnerTransaction NativeMeasuredConsentInventoryResult.Rejected(
                        NativeMeasuredConsentInventoryRefusal.HISTORY_NOT_READY)
                if(Files.isSymbolicLink(root))
                    throw IOException("unsafe measured consent inventory root")
                if(!Files.exists(root))
                    return@withOwnerTransaction NativeMeasuredConsentInventoryResult.Ready(
                        emptyList())
                if(!Files.isDirectory(root))
                    throw IOException("invalid measured consent directory")
                val records=Files.list(root).use{stream->
                    stream.filter{
                        val name=it.fileName.toString()
                        name.startsWith("measured-consent-") && name.endsWith(".bin")
                    }.sorted().limit(2049).toArray().map{it as Path}
                }
                if(records.size>2048)
                    throw IOException("too many measured consent records")
                val outstanding=records.mapNotNull{file->
                    if(Files.isSymbolicLink(file) || !Files.isRegularFile(file) ||
                        Files.size(file) !in 44L..maxBytes.toLong())
                        throw IOException("unsafe measured consent")
                    val bytes=Files.readAllBytes(file)
                    val hinted=NativeMeasuredWorkoutParentCodec.storedIdentity(bytes)
                    val saved=NativeMeasuredWorkoutParentCodec.decode(
                        bytes,hinted.first,hinted.second)
                    val approved=saved.journal.approvedPlan
                    if(file!=path(saved.ownerKey,approved.workoutId) ||
                        saved.journal.revision!=0L ||
                        saved.journal.receipts.isNotEmpty() ||
                        saved.journal.finishedByEventId!=null ||
                        saved.approvalRevision!=approved.gymRevision)
                        throw IOException("invalid or misnamed measured consent")
                    if(saved.ownerKey!=owner ||
                        saved.athleteId!=signed.athleteId)
                        null
                    else if(saved.approvalRevision!=current.revision ||
                        !validated(approved,current) ||
                        available(owner,signed.athleteId,approved.workoutId)!=null)
                        // Expired source or already-issued/finished session:
                        // never present it as an eligible recovery grant.
                        null
                    else NativeMeasuredConfirmedConsent(approved.workoutId,
                        approved.sets.map{it.exerciseId}.distinct(),approved.sets.size)
                }
                if(active(owner)!=signed)
                    return@withOwnerTransaction NativeMeasuredConsentInventoryResult.Rejected(
                        NativeMeasuredConsentInventoryRefusal.NOT_AUTHORIZED)
                NativeMeasuredConsentInventoryResult.Ready(
                    outstanding.sortedBy{it.workoutId})
            }
        }.getOrElse{NativeMeasuredConsentInventoryResult.Rejected(
            NativeMeasuredConsentInventoryRefusal.CORRUPT_RECORD)}
    }

    /** Called only by the native measured parent issuer inside owner lock. */
    override fun currentApprovedWorkout(
        ownerKey:String,athleteId:String,workoutId:String
    ):NativeMeasuredApprovedWorkout? {
        if(!id.matches(ownerKey) || !id.matches(athleteId) ||
            !id.matches(workoutId))return null
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val signed=active(ownerKey,athleteId)
                    ?:return@withOwnerTransaction null
                val s=source(ownerKey,athleteId)
                    ?:return@withOwnerTransaction null
                val stored=readFile(ownerKey,athleteId,workoutId)
                    ?:return@withOwnerTransaction null
                if(stored.approvalRevision!=s.revision ||
                    !validated(stored.journal.approvedPlan,s) ||
                    available(ownerKey,athleteId,workoutId)!=null ||
                    active(ownerKey,athleteId)!=signed)
                    return@withOwnerTransaction null
                NativeMeasuredApprovedWorkout(stored.journal.approvedPlan,
                    stored.approvalId,stored.approvalRevision)
            }
        }.getOrNull()
    }
}
