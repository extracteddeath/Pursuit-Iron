package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArraySet

/** Single process owner, atomic replacement, detached read bytes. No backup URI. */
interface CanonicalWorkoutStoreBytes {
    fun read(): ByteArray?
    fun commitAtomically(bytes: ByteArray)
}

/**
 * MUST be implemented by the pinned canonical JS runtime, never a Kotlin schema
 * replica or document-picker metadata adapter. Native first-program creation
 * uses the pinned implementation; live workout execution is a separate gate.
 */
interface PinnedCanonicalWorkoutStoreEngine {
    suspend fun validateInitialSnapshot(exactSnapshot: String): Boolean
    suspend fun prepareCompletion(origin: String, current: String, finishedJournal: String): String?
}

class CanonicalWorkoutHistoryReceipt internal constructor(
    val session: JournalSessionKey,
    val journalRevision: Long,
    val handoffSha256: String,
    val savedSnapshotSha256: String
)

data class CanonicalWorkoutCompletionOutcome(
    val receipt: CanonicalWorkoutHistoryReceipt,
    val recoveryCleared: Boolean
)

/**
 * Atomic canonical snapshot + pinned workout origin + history acknowledgement.
 * Canonical JS owns validation, prescriptions, history and merge policy. This
 * owner only binds exact inputs, serializes writes and orders crash recovery.
 * All I/O runs off the UI thread, with ONE owner per process. Native execution
 * uses pinned issued targets; backup selection never acquires this owner.
 */
class CanonicalWorkoutStorePersistence(
    private val bytes: CanonicalWorkoutStoreBytes,
    private val engine: PinnedCanonicalWorkoutStoreEngine
) : CanonicalCurrentSnapshotOwner {
    companion object {
        const val MAX_STORE_BYTES = 16 * 1024 * 1024
        private const val MAX_SNAPSHOT_BYTES = ReadOnlyBackupLimits.MAX_SOURCE_BYTES
    }
    private data class Pending(val session: JournalSessionKey, val origin: String,
        val startRevision: Long, val slots: List<JournalSetSlot>,val issuedWorkout: String?=null)
    private data class State(val snapshot: String, val pending: Pending?,
        val receipt: CanonicalWorkoutHistoryReceipt?)
    private val guard=Any()
    private val listeners=CopyOnWriteArraySet<() -> Unit>()
    private val hashPattern=Regex("^[0-9a-f]{64}$")

    private fun sha(raw: String): String {
        val input=raw.toByteArray(Charsets.UTF_8)
        return try {
            val alphabet="0123456789abcdef"
            buildString(64) {
                MessageDigest.getInstance("SHA-256").digest(input).forEach {
                    val n=it.toInt() and 255
                    append(alphabet[n ushr 4]); append(alphabet[n and 15])
                }
            }
        } finally { input.fill(0) }
    }
    private fun exact(v: JSONObject, vararg keys: String) {
        require(v.keys().asSequence().toSet() == keys.toSet())
    }
    private fun number(v: JSONObject, key: String): Long {
        val n=v.get(key)
        require(n is Number && n.toString().matches(Regex("^(0|[1-9][0-9]*)$")))
        return requireNotNull(n.toString().toLongOrNull()).also {
            require(it <= 9_007_199_254_740_991L)
        }
    }
    private fun text(v: JSONObject, key: String): String {
        val raw=v.get(key); require(raw is String); return raw
    }
    private fun boundedSnapshot(raw: String): String {
        val utf8=raw.toByteArray(Charsets.UTF_8)
        try {
            require(utf8.size in 1..MAX_SNAPSHOT_BYTES && !raw.startsWith('\uFEFF'))
            // Reject lone surrogate replacement, not just excessive byte size.
            val encoder=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            encoder.encode(java.nio.CharBuffer.wrap(raw))
        } finally { utf8.fill(0) }
        return raw
    }
    private fun sessionJson(s: JournalSessionKey)=JSONObject()
        .put("programId",s.programRevision.programId)
        .put("programUpdatedAt",s.programRevision.programUpdatedAt)
        .put("storeSavedAt",s.programRevision.storeSavedAt)
        .put("storeVersion",s.programRevision.storeVersion)
        .put("snapshotSha256",s.programRevision.snapshotSha256)
        .put("workoutId",s.workoutId).put("dayId",s.dayId).put("weekIndex",s.weekIndex)
        .put("unit",s.unit).put("startedAt",s.startedAt)
    private fun readSession(v: JSONObject): JournalSessionKey {
        exact(v,"programId","programUpdatedAt","storeSavedAt","storeVersion","snapshotSha256",
            "workoutId","dayId","weekIndex","unit","startedAt")
        val version=number(v,"storeVersion"); val week=number(v,"weekIndex")
        require(version==13L && week in 1..104)
        return JournalSessionKey(ReadOnlyProgramReference(text(v,"programId"),
            number(v,"programUpdatedAt"),number(v,"storeSavedAt"),version.toInt(),
            text(v,"snapshotSha256")),text(v,"workoutId"),text(v,"dayId"),week.toInt(),
            text(v,"unit"),number(v,"startedAt"))
    }
    private fun sameReceipt(a: CanonicalWorkoutHistoryReceipt?, b: CanonicalWorkoutHistoryReceipt): Boolean =
        a != null && a.session==b.session && a.journalRevision==b.journalRevision &&
            a.handoffSha256==b.handoffSha256 && a.savedSnapshotSha256==b.savedSnapshotSha256
    private fun receiptJson(r: CanonicalWorkoutHistoryReceipt?): Any = if (r==null) JSONObject.NULL
        else JSONObject().put("session",sessionJson(r.session)).put("journalRevision",r.journalRevision)
            .put("handoffSha256",r.handoffSha256).put("savedSnapshotSha256",r.savedSnapshotSha256)
    private fun readReceipt(v: JSONObject): CanonicalWorkoutHistoryReceipt {
        exact(v,"session","journalRevision","handoffSha256","savedSnapshotSha256")
        return CanonicalWorkoutHistoryReceipt(readSession(v.getJSONObject("session")),
            number(v,"journalRevision"),text(v,"handoffSha256"),text(v,"savedSnapshotSha256"))
            .also { require(hashPattern.matches(it.handoffSha256) &&
                hashPattern.matches(it.savedSnapshotSha256)) }
    }
    private fun save(state: State) {
        val pending=state.pending?.let { p -> JSONObject().put("session",sessionJson(p.session))
            .put("origin",p.origin).put("startRevision",p.startRevision)
            .put("slots",JSONArray().apply { p.slots.forEach { s -> put(JSONObject()
                .put("exerciseIndex",s.exerciseIndex).put("setIndex",s.setIndex).put("exerciseId",s.exerciseId)) } })
            .apply { if(p.issuedWorkout!=null)put("issuedWorkout",p.issuedWorkout) } }
            ?: JSONObject.NULL
        val payload=JSONObject().put("snapshot",state.snapshot).put("pending",pending)
            .put("receipt",receiptJson(state.receipt)).toString()
        val encoded=JSONObject().put("format",1).put("payload",payload).put("sha256",sha(payload))
            .toString().toByteArray(Charsets.UTF_8)
        try {
            require(encoded.size in 1..MAX_STORE_BYTES)
            bytes.commitAtomically(encoded)
        } finally { encoded.fill(0) }
    }
    private fun read(): State? {
        val raw=bytes.read() ?: return null
        try {
            require(raw.size in 1..MAX_STORE_BYTES)
            val decoded=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
            require(!decoded.startsWith('\uFEFF'))
            val envelope=JSONObject(decoded)
            exact(envelope,"format","payload","sha256")
            require(number(envelope,"format")==1L)
            val payload=text(envelope,"payload")
            require(text(envelope,"sha256")==sha(payload))
            val data=JSONObject(payload); exact(data,"snapshot","pending","receipt")
            val snapshot=boundedSnapshot(text(data,"snapshot"))
            val receipt=if (data.isNull("receipt")) null else readReceipt(data.getJSONObject("receipt"))
            require(receipt==null || receipt.savedSnapshotSha256==sha(snapshot))
            val pending=if (data.isNull("pending")) null else {
                val p=data.getJSONObject("pending")
                if(p.has("issuedWorkout"))exact(p,"session","origin","startRevision","slots","issuedWorkout")
                else exact(p,"session","origin","startRevision","slots")
                val s=readSession(p.getJSONObject("session"))
                val origin=boundedSnapshot(text(p,"origin"))
                require(sha(origin)==s.programRevision.snapshotSha256)
                val list=p.getJSONArray("slots"); require(list.length() in 1..480)
                val slots=(0 until list.length()).map { i ->
                    val slot=list.getJSONObject(i); exact(slot,"exerciseIndex","setIndex","exerciseId")
                    val ex=number(slot,"exerciseIndex"); val set=number(slot,"setIndex")
                    require(ex in 0..47 && set in 0..19)
                    JournalSetSlot(ex.toInt(),set.toInt(),text(slot,"exerciseId"))
                }
                val revision=number(p,"startRevision")
                requireNotNull(WorkoutJournalPolicy.open(s,slots,revision))
                require(receipt?.session==s || snapshot==origin)
                val issued=if(p.has("issuedWorkout"))text(p,"issuedWorkout") else null
                if(issued!=null) {
                    val plan=NativeWorkoutCodec.readIssued(issued)
                    require(plan.session==s && plan.slots==slots)
                }
                Pending(s,origin,revision,slots,issued)
            }
            return State(snapshot,pending,receipt)
        } finally { raw.fill(0) }
    }
    private fun changed() { listeners.forEach { listener -> runCatching { listener() } } }
    override suspend fun readOwnedCurrentStoreBytes(): ByteArray? = synchronized(guard) {
        read()?.snapshot?.toByteArray(Charsets.UTF_8)
    }
    override fun subscribeCommittedChanges(onChanged: () -> Unit): AutoCloseable {
        listeners.add(onChanged); return AutoCloseable { listeners.remove(onChanged) }
    }

    /** Future canonical onboarding/program creation only; never a picker import. */
    suspend fun initialize(exactSnapshot: String): Boolean {
        val snapshot=boundedSnapshot(exactSnapshot)
        synchronized(guard) { read()?.let { return it.snapshot==snapshot } }
        if (!engine.validateInitialSnapshot(snapshot)) return false
        var wrote=false
        val created=synchronized(guard) {
            val current=read()
            if (current!=null) return@synchronized current.snapshot==snapshot
            save(State(snapshot,null,null)); wrote=true; true
        }
        if (wrote) changed()
        return created
    }

    /** Pin origin before starting the journal; retries cannot replace another workout. */
    fun beginSession(session: JournalSessionKey, slots: List<JournalSetSlot>, revision: Long,
        journal: WorkoutJournalPersistence): WorkoutJournalState? = beginPinned(session,slots,revision,journal,null)

    /** Pin the exact issued targets alongside their origin BEFORE opening logging. */
    fun beginIssuedSession(plan: NativeWorkoutPlan,journal: WorkoutJournalPersistence): NativeWorkoutProgress? =
        beginPinned(plan.session,plan.slots,0,journal,plan.issuedJson)?.let { NativeWorkoutProgress(plan,it) }

    private fun beginPinned(session: JournalSessionKey, slots: List<JournalSetSlot>, revision: Long,
        journal: WorkoutJournalPersistence,issued: String?): WorkoutJournalState? {
        val proposed=WorkoutJournalPolicy.open(session,slots,revision) ?: return null
        val pinned=synchronized(guard) {
            val current=read() ?: return@synchronized false
            val old=current.pending
            if (old!=null) return@synchronized old.session==session && old.slots==proposed.slots &&
                old.startRevision==revision && old.issuedWorkout==issued
            if (sha(current.snapshot)!=session.programRevision.snapshotSha256 ||
                journal.recover()!=null || current.receipt?.session?.workoutId==session.workoutId)
                return@synchronized false
            save(current.copy(pending=Pending(session,current.snapshot,revision,proposed.slots,issued))); true
        }
        return if (pinned) journal.begin(session,proposed.slots,revision) else null
    }

    /** Restores issued bytes, never fresh prescriptions, including an interrupted begin. */
    fun recoverIssuedSession(journal: WorkoutJournalPersistence): NativeWorkoutProgress? = synchronized(guard) {
        val current=read()
        val pending=current?.pending
        if(pending==null) {
            require(journal.recover()==null) { "Unattached workout journal preserved" }
            return@synchronized null
        }
        val plan=NativeWorkoutCodec.readIssued(requireNotNull(pending.issuedWorkout))
        require(plan.session==pending.session && plan.slots==pending.slots)
        val existing=journal.recover()
        val receipt=checkNotNull(current).receipt
        if(existing==null && receipt?.session==pending.session) {
            checkNotNull(retire(receipt,journal))
            return@synchronized null
        }
        val restored=checkNotNull(journal.begin(pending.session,pending.slots,pending.startRevision))
        require(restored.session==plan.session && restored.slots==plan.slots)
        NativeWorkoutProgress(plan,restored)
    }

    fun lastCompletionReceipt(): CanonicalWorkoutHistoryReceipt? = synchronized(guard) { read()?.receipt }

    /** Exact revision correction. A pending workout pins its original store until completion. */
    suspend fun correctHistory(correctionJson: String): NativeWorkoutHistory? {
        val initial=synchronized(guard) { read() } ?: return null
        if(initial.pending!=null)return null
        val historyEngine=engine as? NativeHistoryEngine ?: return null
        val raw=historyEngine.prepareHistoryCorrection(initial.snapshot,correctionJson) ?: return null
        val preparation=NativeHistoryCodec.readPreparation(raw,initial.snapshot,correctionJson)
        var wrote=false
        val result=try { synchronized(guard) {
            val current=read() ?: return@synchronized null
            val sameCompletion=initial.receipt?.let { sameReceipt(current.receipt,it) } ?: (current.receipt==null)
            if(current.pending!=null || current.snapshot!=initial.snapshot || !sameCompletion)
                return@synchronized null
            if(preparation.alreadyPresent)return@synchronized preparation.history
            val receipt=current.receipt?.let { CanonicalWorkoutHistoryReceipt(it.session,it.journalRevision,
                it.handoffSha256,sha(preparation.candidate)) }
            save(State(preparation.candidate,null,receipt))
            val confirmed=read() ?: return@synchronized null
            require(confirmed.snapshot==preparation.candidate)
            wrote=true
            preparation.history
        } } catch(failure: Exception) {
            val advanced=runCatching { synchronized(guard) { read()?.snapshot!=initial.snapshot } }.getOrDefault(false)
            if(advanced)changed()
            throw failure
        }
        if(wrote)changed()
        return result
    }

    private fun equivalent(a: Any, b: Any, depth: Int=0): Boolean {
        require(depth<=64)
        return when {
            a==JSONObject.NULL || b==JSONObject.NULL -> a==JSONObject.NULL && b==JSONObject.NULL
            a is JSONObject && b is JSONObject -> {
                val keys=a.keys().asSequence().toSet()
                keys==b.keys().asSequence().toSet() && keys.all { equivalent(a.get(it),b.get(it),depth+1) }
            }
            a is JSONArray && b is JSONArray -> a.length()==b.length() &&
                (0 until a.length()).all { equivalent(a.get(it),b.get(it),depth+1) }
            a is Number && b is Number -> java.math.BigDecimal(a.toString())
                .compareTo(java.math.BigDecimal(b.toString()))==0
            else -> a==b
        }
    }

    /** Location evidence is checked against pinned targets and durable receipts.
     * This checks identity and observed values, not canonical summary policy. */
    private fun checkSetLocations(entry: JSONObject,plan: NativeWorkoutPlan,journal: WorkoutJournalState) {
        val locations=entry.getJSONObject("nativeSetLocations")
        exact(locations,"format","sets");require(number(locations,"format")==1L)
        val list=locations.getJSONArray("sets");require(list.length() in 0..480)
        val perf=entry.getJSONObject("perf")
        val expected=perf.keys().asSequence().flatMap { id->
            val sets=perf.getJSONObject(id).getJSONArray("sets");require(sets.length() in 1..480)
            (0 until sets.length()).asSequence().map { id to it }
        }.toSet()
        require(expected.size==list.length())
        val issued=plan.exercises.flatMap { it.sets }.associateBy { it.slot }
        val observed=journal.completed.associateBy { it.intent.slot }
        val seen=mutableSetOf<Pair<String,Int>>();val used=mutableSetOf<JournalSetSlot>()
        val previous=mutableMapOf<String,Int>()
        for(i in 0 until list.length()) {
            val location=list.getJSONObject(i);exact(location,"exerciseId","setIndex","slot")
            val id=text(location,"exerciseId");val index=number(location,"setIndex")
            require(index in 0..479)
            val key=id to index.toInt();require(key in expected && seen.add(key))
            val rawSlot=location.getJSONObject("slot");exact(rawSlot,"exerciseIndex","setIndex","exerciseId")
            val exerciseIndex=number(rawSlot,"exerciseIndex");val setIndex=number(rawSlot,"setIndex")
            require(exerciseIndex in 0..47 && setIndex in 0..19 && text(rawSlot,"exerciseId")==id)
            val slot=JournalSetSlot(exerciseIndex.toInt(),setIndex.toInt(),id)
            require(used.add(slot))
            val position=exerciseIndex.toInt()*20+setIndex.toInt()
            require(position>(previous[id] ?: -1));previous[id]=position
            val target=checkNotNull(issued[slot]);val commit=checkNotNull(observed[slot]).intent
            require(commit.disposition==JournalSetDisposition.COMPLETED)
            val value=checkNotNull(commit.values)
            val set=perf.getJSONObject(id).getJSONArray("sets").getJSONObject(index.toInt())
            val weight=set.get("w");require(weight is Number && value.load!=null &&
                java.math.BigDecimal(weight.toString()).compareTo(java.math.BigDecimal(value.load))==0 &&
                number(set,"r")==value.reps.toLong())
            require(!set.has("sub") || set.get("sub") is Boolean)
            require(!set.has("warm") || set.get("warm") is Boolean)
            require(!set.has("rirReported") || set.get("rirReported") is Boolean)
            require(set.optBoolean("sub",false)==target.isExtension && !set.optBoolean("warm",false))
            if(target.isExtension)require(set.get("kind")==target.kind)
            else require(number(set,"workingSetIndex")==setIndex &&
                number(set.getJSONObject("decisionTrace"),"slot")==exerciseIndex)
            if(set.optBoolean("rirReported",false))require(value.rir!=null && number(set,"rir")==value.rir.toLong())
            else require(value.rir==null)
        }
    }

    private fun checkPreparation(raw: String, current: State, journal: WorkoutJournalState,
        handoff: String): Pair<String,CanonicalWorkoutHistoryReceipt> {
        require(raw.toByteArray(Charsets.UTF_8).size in 1..(8*1024*1024))
        val p=JSONObject(raw)
        exact(p,"source","preparedOnly","persisted","approvedForWorkoutExecution",
            "sourceSnapshotSha256","inputHandoffSha256","expectedSnapshotSha256",
            "candidateSnapshotSha256","candidateSnapshot","alreadyPresent","historyEntry")
        val pending=requireNotNull(current.pending)
        require(p.get("source")=="modules/engine-api.js:loggedWorkoutPerformance+mergeStores" &&
            p.get("preparedOnly")==true && p.get("persisted")==false &&
            p.get("approvedForWorkoutExecution")==false && p.get("alreadyPresent") is Boolean)
        require(text(p,"sourceSnapshotSha256")==sha(pending.origin) &&
            text(p,"inputHandoffSha256")==sha(handoff) &&
            text(p,"expectedSnapshotSha256")==sha(current.snapshot))
        val candidate=boundedSnapshot(text(p,"candidateSnapshot"))
        require(text(p,"candidateSnapshotSha256")==sha(candidate))
        if (p.getBoolean("alreadyPresent")) require(candidate==current.snapshot)
        val entry=p.getJSONObject("historyEntry"); val s=journal.session
        val finish=requireNotNull(journal.finish)
        require(entry.get("id")==s.workoutId && entry.get("programId")==s.programRevision.programId &&
            entry.get("dayId")==s.dayId && number(entry,"weekIndex")==s.weekIndex.toLong() &&
            entry.get("unit")==s.unit && number(entry,"date")==finish.intent.completedAt &&
            number(entry,"updatedAt")==finish.intent.completedAt)
        if(pending.issuedWorkout!=null) {
            val observations=entry.getJSONObject("nativeSetObservations")
            exact(observations,"format","events");require(number(observations,"format")==1L)
            val expected=JSONArray().apply { journal.completed.forEach { commit ->
                val intent=commit.intent;val value=intent.values
                put(JSONObject().put("slot",JSONObject().put("exerciseIndex",intent.slot.exerciseIndex)
                    .put("setIndex",intent.slot.setIndex).put("exerciseId",intent.slot.exerciseId))
                    .put("disposition",intent.disposition.name).put("values",value?.let {
                        JSONObject().put("load",it.load ?: JSONObject.NULL).put("unit",it.unit ?: JSONObject.NULL)
                            .put("reps",it.reps).put("rir",it.rir ?: JSONObject.NULL)
                    } ?: JSONObject.NULL))
            } }
            require(equivalent(observations.getJSONArray("events"),expected))
        }
        if(entry.has("nativeSetLocations"))checkSetLocations(entry,
            NativeWorkoutCodec.readIssued(requireNotNull(pending.issuedWorkout)),journal)
        val annotated=journal.events.any { it.intent.adjustment!=null }
        if(annotated) {
            val data=entry.getJSONObject("nativeSetAdjustments")
            exact(data,"format","events");require(number(data,"format")==1L)
            val expected=JSONArray().apply {
                journal.completed.forEach { commit ->
                    val a=commit.intent.adjustment
                    if(a!=null)put(JSONObject()
                        .put("slot",JSONObject().put("exerciseIndex",commit.intent.slot.exerciseIndex)
                            .put("setIndex",commit.intent.slot.setIndex)
                            .put("exerciseId",commit.intent.slot.exerciseId))
                        .put("action",a.action).put("part",a.part))
                }
            }
            require(equivalent(data.getJSONArray("events"),expected))
        } else require(!entry.has("nativeSetAdjustments"))
        if(journal.events.size!=journal.completed.size || annotated) {
            val audit=entry.getJSONObject("nativeSetRevisions");val input=JSONObject(handoff)
            exact(audit,"format","startRevision","events","finish")
            require(number(audit,"format")==1L && number(audit,"startRevision")==number(input,"startRevision") &&
                equivalent(audit.getJSONArray("events"),input.getJSONArray("events")) &&
                equivalent(audit.getJSONObject("finish"),input.getJSONObject("finish")))
        } else require(!entry.has("nativeSetRevisions"))
        val identity=entry.getJSONObject("nativeCompletion")
        exact(identity,"format","handoffSha256","sourceSnapshotSha256","journalRevision")
        require(number(identity,"format")==2L && number(identity,"journalRevision")==journal.revision &&
            text(identity,"sourceSnapshotSha256")==s.programRevision.snapshotSha256)
        val handoffSha=text(identity,"handoffSha256"); require(hashPattern.matches(handoffSha))
        // This is transaction identity verification, NOT a Kotlin v13 schema or merge policy.
        val snapshot=JSONObject(candidate); require(number(snapshot,"v")==13L)
        val history=snapshot.getJSONArray("history")
        val entries=(0 until history.length()).map { history.getJSONObject(it) }
            .filter { it.opt("id")==s.workoutId }
        require(entries.size==1 && equivalent(entries.single(),entry))
        require(!snapshot.getJSONObject("tombs").has(s.workoutId))
        return candidate to CanonicalWorkoutHistoryReceipt(s,journal.revision,handoffSha,sha(candidate))
    }

    /**
     * No set or recovery deletion until canonical history + receipt are durable
     * in ONE atomic write. Re-read under the owner lock after the engine await.
     * Cancellation/errors leave origin and journal available for an exact retry.
     */
    suspend fun complete(session: JournalSessionKey, revision: Long,
        journal: WorkoutJournalPersistence): CanonicalWorkoutCompletionOutcome? {
        val initial=synchronized(guard) { read() } ?: return null
        val prior=initial.receipt
        val recovered=journal.recover()
        // The previous completion can be acknowledged after the next workout
        // has begun. It must never clear or replace that newer journal.
        if (prior?.session==session && prior.journalRevision==revision &&
            initial.pending!=null && initial.pending.session!=session)
            return CanonicalWorkoutCompletionOutcome(prior,true)
        if (recovered==null) {
            if (prior?.session!=session || prior.journalRevision!=revision) return null
            return retire(prior,journal)
        }
        val pending=initial.pending ?: return null
        if (pending.session!=session || recovered.session!=session || recovered.revision!=revision ||
            !recovered.isFinished || recovered.slots!=pending.slots) return null
        val handoff=journal.finishedHandoff(session,revision) ?: return null
        val raw=if(pending.issuedWorkout==null)engine.prepareCompletion(pending.origin,initial.snapshot,handoff)
            else (engine as? NativeWorkoutEngine)?.prepareIssuedCompletion(pending.origin,initial.snapshot,handoff,pending.issuedWorkout)
        if(raw==null)return null
        val (candidate,proposed)=checkPreparation(raw,initial,recovered,handoff)
        var canonicalChanged=false
        val receipt=try { synchronized(guard) {
            val current=read() ?: return@synchronized null
            if (sameReceipt(current.receipt,proposed)) return@synchronized current.receipt
            // Concurrent completion: acknowledge only the exact already-committed
            // receipt, never apply this stale candidate over its newer snapshot.
            val already=current.receipt
            if (already?.session==proposed.session && already.journalRevision==revision &&
                already.handoffSha256==proposed.handoffSha256) return@synchronized already
            if (current.pending!=pending) return@synchronized null
            if (current.snapshot!=initial.snapshot || current.receipt?.session==session)
                return@synchronized null
            save(State(candidate,pending,proposed))
            // Confirm the exact committed bytes before permitting cleanup.
            val verified=read() ?: return@synchronized null
            if (!sameReceipt(verified.receipt,proposed)) return@synchronized null
            canonicalChanged=candidate!=initial.snapshot
            verified.receipt
        } } catch (failure: Exception) {
            // A write can land just before its acknowledgement is lost. Notify
            // current-store observers if the committed snapshot advanced, while
            // leaving both the pending origin and recovery journal for retry.
            val advanced=try { synchronized(guard) { read()?.snapshot!=initial.snapshot } }
                catch (_: Exception) { false }
            if (advanced) changed()
            throw failure
        } ?: return null
        if (canonicalChanged) changed()
        return retire(receipt,journal)
    }

    private fun retire(receipt: CanonicalWorkoutHistoryReceipt,
        journal: WorkoutJournalPersistence): CanonicalWorkoutCompletionOutcome? = synchronized(guard) {
        val current=read() ?: return@synchronized null
        if (!sameReceipt(current.receipt,receipt)) return@synchronized null
        if (current.pending==null) return@synchronized CanonicalWorkoutCompletionOutcome(receipt,true)
        if (current.pending.session!=receipt.session)
            return@synchronized CanonicalWorkoutCompletionOutcome(receipt,true)
        if (!journal.retireAfterHistory(receipt))
            return@synchronized CanonicalWorkoutCompletionOutcome(receipt,false)
        save(current.copy(pending=null))
        CanonicalWorkoutCompletionOutcome(receipt,true)
    }
}
