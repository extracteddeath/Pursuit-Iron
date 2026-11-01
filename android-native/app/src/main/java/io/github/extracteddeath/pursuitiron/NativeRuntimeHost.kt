package io.github.extracteddeath.pursuitiron

import android.content.Context
import io.github.extracteddeath.pursuitiron.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

internal data class NativeRuntimeState(
    val loading: Boolean=true,val ready: Boolean=false,val busy: Boolean=false,val blocked: Boolean=false,
    val verifier: PackagedReadOnlySchemaVerifier?=null,val options: NativeProgramOptions?=null,
    val proposal: NativeProgramProposal?=null,val saved: NativeSavedProgram?=null,val error: String?=null,
    val planSource: CurrentReadOnlyPlanSource=UnconnectedCurrentReadOnlyPlanSource,val storeChangeKey: Long=0
)

/** Process-scoped first-program flow. No workout logger or backup import is activated. */
internal class NativeRuntimeHost(context: Context) : AutoCloseable {
    private val context=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val started=AtomicBoolean(false)
    private val lifetime=Any()
    private var closed=false
    private val actionVersion=AtomicLong(0)
    private val mutable=MutableStateFlow(NativeRuntimeState())
    val state: StateFlow<NativeRuntimeState> = mutable.asStateFlow()
    private var store: CanonicalWorkoutStorePersistence?=null
    private var subscription: AutoCloseable?=null
    private var action: Job?=null

    fun start() {
        if (!started.compareAndSet(false,true)) return
        scope.launch {
            val loaded=PackagedReadOnlySchemaVerifier.openIfPackaged(context)
            synchronized(lifetime) {
                if (closed || !isActive) { loaded?.close();return@launch }
                if (loaded!=null) mutable.update { it.copy(verifier=loaded) }
            }
            if (loaded==null) {
                mutable.update { it.copy(loading=false,error="Program creation is unavailable in this preview.") }
                return@launch
            }
            try {
                val engine=loaded.programEngine
                val options=checkNotNull(engine.programOptions())
                val owner=CanonicalWorkoutStorePersistence(AppPrivateCanonicalWorkoutStoreBytes(context),engine)
                store=owner
                val watching=owner.subscribeCommittedChanges {
                    mutable.update { it.copy(storeChangeKey=it.storeChangeKey+1) }
                }
                synchronized(lifetime) {
                    if(closed) { watching.close();throw CancellationException("Runtime closed") }
                    subscription=watching
                }
                val source=CanonicalCurrentPlanSourceAdapter(
                    ExactCurrentSnapshotProjectionPort(owner,loaded.currentPlanProjector))
                val saved=readSaved(owner,loaded)
                ensureActive()
                mutable.update { it.copy(loading=false,ready=true,options=options,saved=saved,
                    planSource=source,error=null) }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                // Unreadable existing bytes are not a fresh onboarding store.
                mutable.update { it.copy(loading=false,blocked=true,
                    error="Program setup could not be opened. Any saved training data has been kept on this device.") }
            }
        }
    }
    private suspend fun readSaved(owner: CanonicalWorkoutStorePersistence,
        loaded: PackagedReadOnlySchemaVerifier): NativeSavedProgram? {
        val bytes=owner.readOwnedCurrentStoreBytes() ?: return null
        return try {
            checkNotNull(loaded.programEngine.inspectProgram(String(bytes,Charsets.UTF_8)))
        } finally { bytes.fill(0) }
    }
    fun review(profile: NativeProgramProfile) {
        val current=mutable.value
        if (!current.ready || current.busy || current.blocked || current.saved!=null) return
        val version=actionVersion.incrementAndGet()
        mutable.update { it.copy(busy=true,error=null,proposal=null) }
        action=scope.launch {
            try {
                val request=NativeProgramSetupCodec.setupJson(profile,"create-"+UUID.randomUUID(),
                    "program-"+UUID.randomUUID(),System.currentTimeMillis(),Random.nextInt(1,Int.MAX_VALUE))
                val proposal=checkNotNull(current.verifier?.programEngine?.prepareProgram(request))
                ensureActive()
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,proposal=proposal,error=null) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,error="A program could not be built with these choices. Review your equipment and schedule, then try again.") else it }
            }
        }
    }
    fun edit() {
        actionVersion.incrementAndGet()
        action?.cancel()
        mutable.update { it.copy(proposal=null,busy=false,error=null) }
    }
    fun save() {
        val current=mutable.value;val proposal=current.proposal ?: return
        if (current.busy || current.blocked || current.saved!=null) return
        val owner=store ?: return;val loaded=current.verifier ?: return
        val version=actionVersion.incrementAndGet()
        mutable.update { it.copy(busy=true,error=null) }
        action=scope.launch {
            try {
                check(owner.initialize(proposal.snapshot)) // exact retry only; never replaces existing data
                val saved=checkNotNull(readSaved(owner,loaded))
                check(saved.creationId==proposal.creationId)
                ensureActive()
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,saved=saved,proposal=null,error=null) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,error="The save could not be confirmed. Your choices are still here. Try saving again.") else it }
            }
        }
    }
    override fun close() {
        synchronized(lifetime) {
            if(closed)return
            closed=true;actionVersion.incrementAndGet()
            scope.cancel();subscription?.close();mutable.value.verifier?.close()
        }
    }
}
