package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.*
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import java.util.Collections

enum class NativeBlockLoadConsentKind { CONSERVATIVE_REDUCTION, EARNED_BASELINE_INCREASE }

data class NativeBlockCalibrationReceipt(
    val ownerKey:String,val athleteId:String,val workoutId:String,
    val cycleId:String,val absoluteWeek:Int,val weekday:Int,val slotId:String,
    val fingerprint:String,val historyRevision:Long,val consentRevision:Long,
    val settingsRevision:Long,val permissionRevision:Long,val sourceRevision:Long,
    val readiness:CoachReadiness,val movementRestricted:Boolean,
    val maximumIncrease:BigDecimal,val unit:LoadUnit,val basis:LoadBasis,
    val setKeys:List<String>,val authoredLoads:List<BigDecimal>,
    val proposedLoads:List<BigDecimal>,
    val kind:NativeBlockLoadConsentKind=NativeBlockLoadConsentKind.CONSERVATIVE_REDUCTION,
    val baselineLoads:List<BigDecimal> = emptyList(),
    val comparedWorkoutIds:List<String> = emptyList(),
    val assumedGoalRirSetCount:Int=0
) {
    val mayIssueWorkout:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
}
enum class NativeCalibrationRefusal {
    INVALID_SELECTION,NOT_AUTHORIZED,REVIEW_NOT_READY,PERMISSION_REQUIRED,
    PROTECTED_WEEK,HISTORY_UNAVAILABLE,ALREADY_PREPARED_OR_ISSUED,
    FULL_CYCLE_AUDIT_FAILED,CONFIRMATION_REQUIRED,REVIEW_CHANGED,
    CONFLICTING_APPROVAL,CORRUPT_RECORD,STORAGE_UNAVAILABLE
}
sealed interface NativeBlockCalibrationPreview {
    data class Ready(val receipt:NativeBlockCalibrationReceipt,
        val original:CustomWorkoutDaySnapshot,val projected:CustomWorkoutDaySnapshot,
        val auditedWeeks:Int):NativeBlockCalibrationPreview
    data class Rejected(val reason:NativeCalibrationRefusal):NativeBlockCalibrationPreview
}
sealed interface NativeBlockCalibrationRead {
    data object Missing:NativeBlockCalibrationRead
    data class Current(val review:NativeBlockCalibrationPreview.Ready):NativeBlockCalibrationRead
    data class Rejected(val reason:NativeCalibrationRefusal):NativeBlockCalibrationRead
}
sealed interface NativeBlockCalibrationWrite {
    data class Accepted(val receipt:NativeBlockCalibrationReceipt,val replayed:Boolean):
        NativeBlockCalibrationWrite
    data class Rejected(val reason:NativeCalibrationRefusal):NativeBlockCalibrationWrite
}
sealed interface NativePendingCalibrationRead {
    data class Ready(val workouts:List<NativeCustomPreparedWorkout>,
        /** Stored acceptance facts; revalidation is required before preparation. */
        val acceptances:List<NativeBlockCalibrationReceipt> = emptyList()):NativePendingCalibrationRead
    data class Rejected(val reason:NativeCalibrationRefusal):NativePendingCalibrationRead
}

/** Ordinary authored preparation is never allowed to hide a previously issued lower weight. */
internal enum class NativeAuthoredLoadPreparationGate {
    CLEAR, LOWER_ISSUED_LOAD_NEEDS_REVIEW, UNVERIFIED_HISTORY
}

private data class CalibrationLoadChange(
    val setKey:String,val authoredLoad:BigDecimal,val proposedLoad:BigDecimal,
    val unit:LoadUnit,val basis:LoadBasis
)

/** One-workout consent. Neither the authored cycle nor its signoff is rewritten. */
class NativeKotlinBlockCalibrationConsentStore(
    private val root:Path,private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val reviewer:NativeKotlinCustomNextWorkoutReview,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val permissions:NativeKotlinAdaptiveCyclePermissionFileStore,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val inventory:NativeKotlinCustomOutstandingWorkoutInventory,
    private val composer:KotlinNativeJournalFileStore,
    private val prepared:NativeKotlinCustomWorkoutPreparedStore
) {
    companion object {
        private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun hash(bytes:ByteArray)=digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        private fun stem(owner:String,workout:String)="block-calibration-"+
            hash("$owner\u0000$workout".toByteArray(Charsets.UTF_8))
        internal fun hasRecord(root:Path,owner:String,workout:String):Boolean =
            Files.exists(root.resolve(stem(owner,workout)+".bin"),LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(root.resolve(stem(owner,workout)+".pending"),LinkOption.NOFOLLOW_LINKS)
    }
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val hex=Regex("^[a-f0-9]{64}$")
    private fun owner(key:String)=identity.signedInIdentity()?.takeIf{
        id.matches(key)&&it.ownerKey==key&&id.matches(it.athleteId)}
    private fun path(key:String,w:String)=root.resolve(stem(key,w)+".bin")
    private fun pending(key:String,w:String)=root.resolve(stem(key,w)+".pending")
    private fun norm(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())
    private fun validLoad(v:BigDecimal)=v>BigDecimal.ZERO&&v<=BigDecimal("100000")&&
        v.stripTrailingZeros().scale()<=3
    private fun encode(r:NativeBlockCalibrationReceipt):ByteArray {
        require(listOf(r.ownerKey,r.athleteId,r.workoutId,r.cycleId,r.slotId).all(id::matches))
        require(hex.matches(r.fingerprint)&&r.absoluteWeek in 2..104&&r.weekday in 0..6)
        require(listOf(r.historyRevision,r.consentRevision,r.permissionRevision,
            r.sourceRevision).all{it in 1..9_007_199_254_740_991L}&&
            r.settingsRevision in 0..9_007_199_254_740_991L)
        require(validLoad(r.maximumIncrease)&&r.maximumIncrease<=BigDecimal("10000"))
        require(r.setKeys.size in 1..32&&r.setKeys.distinct().size==r.setKeys.size&&
            r.setKeys.all{it.length in 1..240}&&
            r.authoredLoads.size==r.setKeys.size&&r.proposedLoads.size==r.setKeys.size&&
            (r.authoredLoads+r.proposedLoads).all(::validLoad)&&
            r.proposedLoads.zip(r.authoredLoads).all{it.first<=it.second})
        val earned=r.kind==NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE
        if(earned)require(r.baselineLoads.size==r.setKeys.size&&
            r.baselineLoads.all(::validLoad)&&
            r.baselineLoads.zip(r.proposedLoads).all{(base,next)->
                next>base&&next.subtract(base)<=r.maximumIncrease}&&
            r.comparedWorkoutIds.size==2&&r.comparedWorkoutIds.distinct().size==2&&
            r.comparedWorkoutIds.all(id::matches)&&
            r.assumedGoalRirSetCount in 0..r.setKeys.size*2)
        else require(r.proposedLoads.zip(r.authoredLoads).any{it.first<it.second}&&
            r.baselineLoads.isEmpty()&&r.comparedWorkoutIds.isEmpty()&&r.assumedGoalRirSetCount==0)
        val body=ByteArrayOutputStream().also{buffer->DataOutputStream(buffer).use{out->
            out.writeInt(0x50494341);out.writeInt(if(earned)2 else 1)
            listOf(r.ownerKey,r.athleteId,r.workoutId,r.cycleId,r.slotId,r.fingerprint)
                .forEach(out::writeUTF)
            out.writeInt(r.absoluteWeek);out.writeInt(r.weekday)
            listOf(r.historyRevision,r.consentRevision,r.settingsRevision,
                r.permissionRevision,r.sourceRevision).forEach(out::writeLong)
            out.writeUTF(r.readiness.name);out.writeBoolean(r.movementRestricted)
            out.writeUTF(r.maximumIncrease.toPlainString())
            out.writeUTF(r.unit.name);out.writeUTF(r.basis.name)
            out.writeInt(r.setKeys.size)
            r.setKeys.indices.forEach{i->
                out.writeUTF(r.setKeys[i]);out.writeUTF(r.authoredLoads[i].toPlainString())
                out.writeUTF(r.proposedLoads[i].toPlainString())
            }
            if(earned) {
                out.writeUTF(r.kind.name)
                r.baselineLoads.forEach{out.writeUTF(it.toPlainString())}
                r.comparedWorkoutIds.forEach(out::writeUTF)
                out.writeInt(r.assumedGoalRirSetCount)
            }
        }}.toByteArray()
        require(body.size+36<=24_576)
        return ByteArrayOutputStream().also{buffer->DataOutputStream(buffer).use{out->
            out.writeInt(body.size);out.write(body);out.write(digest(body))
        }}.toByteArray()
    }
    private fun decode(key:String,w:String):NativeBlockCalibrationReceipt? {
        if(Files.isSymbolicLink(root)||Files.exists(pending(key,w),LinkOption.NOFOLLOW_LINKS))
            throw IOException("unsafe or interrupted calibration approval")
        val file=path(key,w)
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||Files.size(file) !in 44L..24_576L)
            throw IOException("invalid calibration approval")
        val bytes=Files.readAllBytes(file)
        return DataInputStream(ByteArrayInputStream(bytes)).use{input->
            val size=input.readInt()
            if(size !in 16..24_540||size+36!=bytes.size)throw IOException("invalid approval length")
            val body=ByteArray(size);input.readFully(body)
            val sum=ByteArray(32);input.readFully(sum)
            if(!MessageDigest.isEqual(digest(body),sum))throw IOException("approval checksum")
            DataInputStream(ByteArrayInputStream(body)).use{data->
                if(data.readInt()!=0x50494341)throw IOException("approval magic")
                val version=data.readInt()
                if(version !in 1..2)throw IOException("approval version")
                val names=List(6){data.readUTF()}
                val week=data.readInt();val day=data.readInt()
                val revisions=List(5){data.readLong()}
                val readiness=CoachReadiness.valueOf(data.readUTF());val restricted=data.readBoolean()
                val increase=BigDecimal(data.readUTF())
                val unit=LoadUnit.valueOf(data.readUTF());val basis=LoadBasis.valueOf(data.readUTF())
                val count=data.readInt()
                if(count !in 1..32)throw IOException("approval set count")
                val keys=ArrayList<String>();val old=ArrayList<BigDecimal>();val next=ArrayList<BigDecimal>()
                repeat(count){keys+=data.readUTF();old+=BigDecimal(data.readUTF());next+=BigDecimal(data.readUTF())}
                val kind=if(version==2)NativeBlockLoadConsentKind.valueOf(data.readUTF())
                    else NativeBlockLoadConsentKind.CONSERVATIVE_REDUCTION
                if(version==2&&kind!=NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE)
                    throw IOException("approval kind")
                val baseline=if(version==2)List(count){BigDecimal(data.readUTF())} else emptyList()
                val compared=if(version==2)List(2){data.readUTF()} else emptyList()
                val assumed=if(version==2)data.readInt() else 0
                if(data.available()!=0)throw IOException("trailing approval data")
                NativeBlockCalibrationReceipt(names[0],names[1],names[2],names[3],week,day,
                    names[4],names[5],revisions[0],revisions[1],revisions[2],revisions[3],
                    revisions[4],readiness,restricted,increase,unit,basis,
                    Collections.unmodifiableList(keys),Collections.unmodifiableList(old),
                    Collections.unmodifiableList(next),kind,Collections.unmodifiableList(baseline),
                    Collections.unmodifiableList(compared),assumed).also{
                    encode(it)
                    if(it.ownerKey!=key||it.workoutId!=w)throw IOException("approval scope")
                }
            }
        }
    }
    private fun write(r:NativeBlockCalibrationReceipt) {
        Files.createDirectories(root)
        if(Files.isSymbolicLink(root)||Files.exists(path(r.ownerKey,r.workoutId)))
            throw IOException("approval collision")
        val stage=pending(r.ownerKey,r.workoutId)
        FileChannel.open(stage,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE).use{ch->
            val bytes=ByteBuffer.wrap(encode(r));while(bytes.hasRemaining())ch.write(bytes)
            ch.force(true)
        }
        Files.move(stage,path(r.ownerKey,r.workoutId),StandardCopyOption.ATOMIC_MOVE)
    }
    private fun indexedApprovals():List<NativeBlockCalibrationReceipt> {
        if(!Files.exists(root))return emptyList()
        if(Files.isSymbolicLink(root)||!Files.isDirectory(root))throw IOException("unsafe approval directory")
        val files=Files.list(root).use{s->s.filter{it.fileName.toString().startsWith("block-calibration-")}
            .limit(4097).collect(java.util.stream.Collectors.toList())}
        if(files.size>4096)throw IOException("approval inventory limit")
        return files.map{file->
            if(!file.fileName.toString().endsWith(".bin")||Files.isSymbolicLink(file)||
                !Files.isRegularFile(file)||Files.size(file) !in 44L..24_576L)
                throw IOException("unresolved approval inventory")
            val bytes=Files.readAllBytes(file)
            val names=DataInputStream(ByteArrayInputStream(bytes)).use{input->
                val size=input.readInt()
                if(size !in 16..24_540||size+36!=bytes.size)throw IOException("approval inventory length")
                val body=ByteArray(size);input.readFully(body)
                val sum=ByteArray(32);input.readFully(sum)
                if(!MessageDigest.isEqual(digest(body),sum))throw IOException("approval inventory checksum")
                DataInputStream(ByteArrayInputStream(body)).use{data->
                    if(data.readInt()!=0x50494341||data.readInt() !in 1..2)throw IOException("approval inventory version")
                    List(3){data.readUTF()}
                }
            }
            if(path(names[0],names[2])!=file)throw IOException("approval filename binding")
            decode(names[0],names[2])?:throw IOException("missing indexed approval")
        }
    }
    private fun derive(key:String,w:String,cycle:String,inputs:CustomCoachAuditInputs,
        week:Int,day:Int,slot:String,readiness:CoachReadiness,restricted:Boolean,
        increase:BigDecimal,allowPrepared:Boolean,
        kind:NativeBlockLoadConsentKind):NativeBlockCalibrationPreview {
        fun no(r:NativeCalibrationRefusal)=NativeBlockCalibrationPreview.Rejected(r)
        if(!listOf(key,w,cycle,slot).all(id::matches)||week !in 2..104||day !in 0..6||
            !validLoad(increase)||increase>BigDecimal("10000"))return no(NativeCalibrationRefusal.INVALID_SELECTION)
        val signed=owner(key)?:return no(NativeCalibrationRefusal.NOT_AUTHORIZED)
        val reviewed=reviewer.reviewAuthored(key,cycle,inputs,week,day,slot,readiness,restricted,increase)
            as? NativeCustomNextCoachResult.Reviewed ?:return no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        val entry=reviewed.transitionStartingLoads
        val continuation=reviewed.sameBlockLoadContinuity
        val earned=reviewed.reducedBaselineProgression
        val earning=kind==NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE
        val enteringBlock=!earning && entry?.status==CustomBlockTransitionLoadStatus.READY_FOR_REVIEW &&
            entry.changesAuthoredLoads && entry.proposedWorkout!=null
        val repeatingWeek=!earning && !enteringBlock &&
            continuation?.status==CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT &&
            continuation.changesAuthoredLoads && continuation.proposedWorkout!=null
        val original=reviewed.nextWorkout
        val projected=when {
            earning && earned?.status==CustomReducedBaselineProgressionStatus.READY_FOR_REVIEW ->
                earned.amendment?.amended
            enteringBlock -> entry?.proposedWorkout
            repeatingWeek -> continuation?.proposedWorkout
            else -> null
        }
        if(original==null||projected==null)
            return no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        // Earned return is a separate explicitly requested consent kind;
        // existing conservative receipts never silently become an increase.
        val changes=when {
            earning -> earned!!.sets.map{
                CalibrationLoadChange(it.setKey,it.authoredLoad,it.proposedLoad,it.unit,it.basis)
            }
            enteringBlock -> entry!!.sets.map{
                CalibrationLoadChange(it.setKey,it.authoredLoad,it.proposedLoad,it.unit,it.basis)
            }
            repeatingWeek -> continuation!!.sets.map{
                CalibrationLoadChange(it.setKey,it.authoredLoad,it.proposedLoad,it.unit,it.basis)
            }
            else -> emptyList()
        }
        if(changes.isEmpty()||changes.map{it.unit to it.basis}.distinct().size!=1)
            return no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        val draft=(drafts.read(key,cycle) as? NativeCustomDraftReadResult.Found)?.draft
            ?:return no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        val source=(sources.read(key) as? NativeAuthoringSourceRead.Found)?.source
            ?:return no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        if(draft.athleteId!=signed.athleteId||source.athleteId!=signed.athleteId||
            draft.revision!=original.stamp.cycleRevision||source.revision!=original.stamp.gymCatalogSourceRevision)
            return no(NativeCalibrationRefusal.REVIEW_CHANGED)
        val rights=(permissions.read(key,cycle) as? NativeAdaptivePermissionRead.Found)?.receipt
            ?:return no(NativeCalibrationRefusal.PERMISSION_REQUIRED)
        if(rights.athleteId!=signed.athleteId||!rights.allowedKinds.containsAll(
                setOf(CustomCycleChangeKind.LOADS,CustomCycleChangeKind.WEEK_OVERRIDES)))
            return no(NativeCalibrationRefusal.PERMISSION_REQUIRED)
        val oldOverrides=draft.weekOverrides[original.blockId].orEmpty()
        if(oldOverrides.any{it.weekInBlock==original.weekInBlock&&it.weekday==day&&it.slotId==slot})
            return no(NativeCalibrationRefusal.PROTECTED_WEEK)
        val change=CustomWeekSlotOverride(original.weekInBlock,day,slot,
            projected.slots.single{it.slotId==slot}.sets.map{it.prescription})
        val virtual=draft.copy(weekOverrides=draft.weekOverrides+
            (original.blockId to (oldOverrides+change)))
        val audit=CustomCycleCoachAuditDomain.review(virtual,source.setup,source.catalog,inputs)
        val rebuilt=CustomWorkoutSnapshotDomain.reviewForConsent(virtual,source.setup,
            source.catalog,source.revision,inputs,week,day) as? CustomWorkoutSnapshotResult.Preview
        if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW||
            audit.issues.isNotEmpty()||rebuilt?.snapshot!=projected)
            return no(NativeCalibrationRefusal.FULL_CYCLE_AUDIT_FAILED)
        val ledger=(history.read(key,signed.athleteId) as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
            ?:return no(NativeCalibrationRefusal.HISTORY_UNAVAILABLE)
        if(ledger.manifestRevision!=reviewed.historyRevision)
            return no(NativeCalibrationRefusal.REVIEW_CHANGED)
        val completed=ledger.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
            .map{it.manifest.workoutId}.toSet()
        val owned=inventory.inspect(key,signed.athleteId,completed) as? NativeCustomInventoryReview.Reviewed
            ?:return no(NativeCalibrationRefusal.HISTORY_UNAVAILABLE)
        val target=owned.sessions.filter{it.cycleId==cycle&&it.absoluteWeek==week&&it.weekday==day}
        if(target.isNotEmpty()&&!(allowPrepared&&target.size==1&&target.single().workoutId==w&&
                !target.single().issued&&!target.single().completed))
            return no(NativeCalibrationRefusal.ALREADY_PREPARED_OR_ISSUED)
        val composerId=composer.openStored(key,w){o,s->o==key&&s.athleteId==signed.athleteId}
        if(ledger.completions.any{it.manifest.workoutId==w}||owned.sessions.any{it.workoutId==w&&
                (it.cycleId!=cycle||it.absoluteWeek!=week||it.weekday!=day)}||
            composerId !is NativeJournalFileResult.Rejected||composerId.reason!=NativeJournalFileRefusal.NOT_ISSUED)
            return no(NativeCalibrationRefusal.ALREADY_PREPARED_OR_ISSUED)
        val proof=(listOf(if(earning)"earned-baseline-increase-v1"
                else if(repeatingWeek)"same-block-repeat-v1" else "block-calibration-v1",
            key,signed.athleteId,w,cycle,slot,
            week.toString(),day.toString(),reviewed.historyRevision.toString(),
            reviewed.approvedConsentRevision.toString(),reviewed.assistanceRevision.toString(),
            rights.revision.toString(),readiness.name,restricted.toString(),norm(increase).toPlainString(),
            NativeCustomCycleSignoffFingerprint.calculate(draft,source,inputs),
            NativeCustomCycleSignoffFingerprint.calculate(virtual,source,inputs),
            hash(NativeKotlinCustomWorkoutSnapshotCodec.encode(original)),
            hash(NativeKotlinCustomWorkoutSnapshotCodec.encode(projected)))+
            if(earning)listOf(earned!!.sets.joinToString(","){norm(it.baselineLoad).toPlainString()},
                earned.progression!!.comparedWorkoutIds.joinToString(","),
                earned.progression!!.assumedGoalRirSetCount.toString()) else emptyList()).joinToString("\u0000")
        if(owner(key)!=signed)return no(NativeCalibrationRefusal.NOT_AUTHORIZED)
        val receipt=NativeBlockCalibrationReceipt(key,signed.athleteId,w,cycle,week,day,slot,
            hash(proof.toByteArray(Charsets.UTF_8)),reviewed.historyRevision,
            reviewed.approvedConsentRevision,reviewed.assistanceRevision,rights.revision,
            source.revision,readiness,restricted,norm(increase),changes.first().unit,changes.first().basis,
            Collections.unmodifiableList(changes.map{it.setKey}),
            Collections.unmodifiableList(changes.map{norm(it.authoredLoad)}),
            Collections.unmodifiableList(changes.map{norm(it.proposedLoad)}),kind,
            if(earning)Collections.unmodifiableList(earned!!.sets.map{norm(it.baselineLoad)}) else emptyList(),
            if(earning)earned!!.progression!!.comparedWorkoutIds else emptyList(),
            if(earning)earned!!.progression!!.assumedGoalRirSetCount else 0)
        encode(receipt)
        return NativeBlockCalibrationPreview.Ready(receipt,original,projected,audit.weeks.size)
    }
    /**
     * Called by ordinary preparation under the same owner OS fence. Evaluate
     * EVERY slot in the approved day against authenticated corrected history;
     * a previous failed set is still a previous LOWER issued prescription.
     * Coaching OFF is an explicit choice to follow the authored plan.
     */
    internal fun authoredPreparationGate(key:String,cycle:String,
        inputs:CustomCoachAuditInputs,next:CustomWorkoutDaySnapshot
    ):NativeAuthoredLoadPreparationGate {
        if(owner(key)?.athleteId!=next.stamp.athleteId ||
            next.stamp.cycleId!=cycle)return NativeAuthoredLoadPreparationGate.UNVERIFIED_HISTORY
        if(next.weekInBlock<2)return NativeAuthoredLoadPreparationGate.CLEAR
        var higher=false
        for(slot in next.slots) {
            val result=reviewer.reviewAuthored(key,cycle,inputs,next.absoluteWeek,
                next.weekday,slot.slotId,CoachReadiness.NORMAL,false,BigDecimal.ONE)
            if(result is NativeCustomNextCoachResult.Rejected &&
                result.reason==NativeCustomNextCoachRefusal.ASSISTANCE_DISABLED)
                return NativeAuthoredLoadPreparationGate.CLEAR
            val reviewed=result as? NativeCustomNextCoachResult.Reviewed
                ?:return NativeAuthoredLoadPreparationGate.UNVERIFIED_HISTORY
            if(reviewed.nextWorkout!=next)
                return NativeAuthoredLoadPreparationGate.UNVERIFIED_HISTORY
            when(reviewed.authoredLoadReversion?.status) {
                CustomAuthoredLoadReversionStatus.INVALID_HISTORY,
                CustomAuthoredLoadReversionStatus.TARGET_ALREADY_TRAINED,
                null -> return NativeAuthoredLoadPreparationGate.UNVERIFIED_HISTORY
                CustomAuthoredLoadReversionStatus.CONFIRM_HIGHER_AUTHORED_LOAD ->
                    higher=true
                else -> Unit
            }
        }
        return if(higher)NativeAuthoredLoadPreparationGate.LOWER_ISSUED_LOAD_NEEDS_REVIEW
            else NativeAuthoredLoadPreparationGate.CLEAR
    }

    /**
     * Review ALL heavier slots before the athlete chooses the exact signed
     * authored day. The acceptance will live in the prepared file itself;
     * no second receipt/write can leave a half-accepted workout after restart.
     * Re-run this under the same owner fence at acceptance AND first issuance.
     */
    fun previewAuthoredReturn(key:String,w:String,cycle:String,
        inputs:CustomCoachAuditInputs,week:Int,day:Int
    ):NativeAuthoredLoadReturnPreview =runCatching{fence.withOwnerTransaction(key){
        fun no(r:NativeCalibrationRefusal)=NativeAuthoredLoadReturnPreview.Rejected(r)
        if(!listOf(key,w,cycle).all(id::matches)||week !in 2..104||day !in 0..6)
            return@withOwnerTransaction no(NativeCalibrationRefusal.INVALID_SELECTION)
        val signed=owner(key)?:return@withOwnerTransaction no(NativeCalibrationRefusal.NOT_AUTHORIZED)
        if(hasRecord(root,key,w))
            return@withOwnerTransaction no(NativeCalibrationRefusal.CONFLICTING_APPROVAL)
        val draft=(drafts.read(key,cycle) as? NativeCustomDraftReadResult.Found)?.draft
            ?:return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        val source=(sources.read(key) as? NativeAuthoringSourceRead.Found)?.source
            ?:return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        if(draft.athleteId!=signed.athleteId||source.athleteId!=signed.athleteId)
            return@withOwnerTransaction no(NativeCalibrationRefusal.NOT_AUTHORIZED)
        val audit=CustomCycleCoachAuditDomain.review(draft,source.setup,source.catalog,inputs)
        val next=(CustomWorkoutSnapshotDomain.reviewForConsent(draft,source.setup,
            source.catalog,source.revision,inputs,week,day)
            as? CustomWorkoutSnapshotResult.Preview)?.snapshot
        if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW||
            audit.issues.isNotEmpty()||next==null)
            return@withOwnerTransaction no(NativeCalibrationRefusal.FULL_CYCLE_AUDIT_FAILED)
        val reviews=next.slots.map{slot->
            reviewer.reviewAuthored(key,cycle,inputs,week,day,slot.slotId,
                CoachReadiness.NORMAL,false,BigDecimal.ONE)
                as? NativeCustomNextCoachResult.Reviewed
                ?:return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        }
        val first=reviews.firstOrNull()
            ?:return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        if(reviews.any{it.nextWorkout!=next||it.historyRevision!=first.historyRevision||
                it.assistanceRevision!=first.assistanceRevision||
                it.approvedConsentRevision!=first.approvedConsentRevision||
                it.authoredLoadReversion==null||it.authoredLoadReversion.status in setOf(
                    CustomAuthoredLoadReversionStatus.INVALID_HISTORY,
                    CustomAuthoredLoadReversionStatus.TARGET_ALREADY_TRAINED)})
            return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_CHANGED)
        val higher=reviews.mapNotNull{review->
            val change=checkNotNull(review.authoredLoadReversion)
            if(!change.requiresAthleteReview)null else NativeAuthoredLoadReturnSlot(
                checkNotNull(review.selectedSlotId),
                next.slots.single{it.slotId==review.selectedSlotId}.exerciseId,
                checkNotNull(change.previousWorkoutId),change.higherSets)
        }
        if(higher.isEmpty())return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_NOT_READY)
        val ledger=(history.read(key,signed.athleteId) as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
            ?:return@withOwnerTransaction no(NativeCalibrationRefusal.HISTORY_UNAVAILABLE)
        if(ledger.manifestRevision!=first.historyRevision)
            return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_CHANGED)
        val completed=ledger.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
            .map{it.manifest.workoutId}.toSet()
        val owned=inventory.inspect(key,signed.athleteId,completed) as? NativeCustomInventoryReview.Reviewed
            ?:return@withOwnerTransaction no(NativeCalibrationRefusal.HISTORY_UNAVAILABLE)
        val targets=owned.sessions.filter{it.cycleId==cycle&&it.absoluteWeek==week&&it.weekday==day}
        if(targets.any{it.workoutId!=w||it.issued||it.completed}||targets.size>1||
            ledger.completions.any{it.manifest.workoutId==w}||
            owned.sessions.any{it.workoutId==w&&it !in targets})
            return@withOwnerTransaction no(NativeCalibrationRefusal.ALREADY_PREPARED_OR_ISSUED)
        val other=composer.openStored(key,w){o,s->o==key&&s.athleteId==signed.athleteId}
        if(other !is NativeJournalFileResult.Rejected||other.reason!=NativeJournalFileRefusal.NOT_ISSUED)
            return@withOwnerTransaction no(NativeCalibrationRefusal.ALREADY_PREPARED_OR_ISSUED)
        val saved=prepared.open(key,w)
        if(saved is NativeCustomPreparedRead.Rejected)
            return@withOwnerTransaction no(NativeCalibrationRefusal.CORRUPT_RECORD)
        val old=(saved as? NativeCustomPreparedRead.Found)?.workout
        if(old!=null&&(old.authority!=NativeCustomPreparationAuthority.CUSTOM_CYCLE||
                old.snapshot!=next||old.resetToAuthored||old.calibrationApprovalFingerprint!=null))
            return@withOwnerTransaction no(NativeCalibrationRefusal.CONFLICTING_APPROVAL)
        val signoff=NativeCustomCycleSignoffFingerprint.calculate(draft,source,inputs)
        val proof=listOf("authored-load-return-v1",key,signed.athleteId,w,
            first.historyRevision.toString(),first.approvedConsentRevision.toString(),
            first.assistanceRevision.toString(),signoff,
            hash(NativeKotlinCustomWorkoutSnapshotCodec.encode(next))).joinToString("\u0000")
        if(owner(key)!=signed)return@withOwnerTransaction no(NativeCalibrationRefusal.NOT_AUTHORIZED)
        NativeAuthoredLoadReturnPreview.Ready(key,w,next,first.approvedConsentRevision,
            signoff,first.historyRevision,first.assistanceRevision,
            Collections.unmodifiableList(higher),hash(proof.toByteArray(Charsets.UTF_8)),audit.weeks.size)
    }}.getOrElse{NativeAuthoredLoadReturnPreview.Rejected(NativeCalibrationRefusal.STORAGE_UNAVAILABLE)}

    fun preview(key:String,w:String,cycle:String,inputs:CustomCoachAuditInputs,
        week:Int,day:Int,slot:String,readiness:CoachReadiness,restricted:Boolean,
        increase:BigDecimal,
        kind:NativeBlockLoadConsentKind=NativeBlockLoadConsentKind.CONSERVATIVE_REDUCTION
    ):NativeBlockCalibrationPreview =runCatching{
        fence.withOwnerTransaction(key){
            if(owner(key)==null)return@withOwnerTransaction NativeBlockCalibrationPreview.Rejected(
                NativeCalibrationRefusal.NOT_AUTHORIZED)
            try{decode(key,w)}catch(_:Exception){return@withOwnerTransaction NativeBlockCalibrationPreview.Rejected(
                NativeCalibrationRefusal.CORRUPT_RECORD)}
            derive(key,w,cycle,inputs,week,day,slot,readiness,restricted,increase,false,kind)
        }
    }.getOrElse{NativeBlockCalibrationPreview.Rejected(NativeCalibrationRefusal.STORAGE_UNAVAILABLE)}

    fun accept(shown:NativeBlockCalibrationReceipt,inputs:CustomCoachAuditInputs,
        athleteConfirmed:Boolean):NativeBlockCalibrationWrite {
        fun no(r:NativeCalibrationRefusal)=NativeBlockCalibrationWrite.Rejected(r)
        if(!athleteConfirmed)return no(NativeCalibrationRefusal.CONFIRMATION_REQUIRED)
        return runCatching{fence.withOwnerTransaction(shown.ownerKey){
            if(owner(shown.ownerKey)?.athleteId!=shown.athleteId)
                return@withOwnerTransaction no(NativeCalibrationRefusal.NOT_AUTHORIZED)
            val stored=try{decode(shown.ownerKey,shown.workoutId)}catch(_:Exception){
                return@withOwnerTransaction no(NativeCalibrationRefusal.CORRUPT_RECORD)}
            if(stored!=null)return@withOwnerTransaction if(stored==shown)
                NativeBlockCalibrationWrite.Accepted(stored,true)
                else no(NativeCalibrationRefusal.CONFLICTING_APPROVAL)
            val fresh=derive(shown.ownerKey,shown.workoutId,shown.cycleId,inputs,shown.absoluteWeek,
                shown.weekday,shown.slotId,shown.readiness,shown.movementRestricted,shown.maximumIncrease,false,shown.kind)
                as? NativeBlockCalibrationPreview.Ready
                ?:return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_NOT_READY)
            if(fresh.receipt!=shown)return@withOwnerTransaction no(NativeCalibrationRefusal.REVIEW_CHANGED)
            write(fresh.receipt)
            NativeBlockCalibrationWrite.Accepted(fresh.receipt,false)
        }}.getOrElse{no(NativeCalibrationRefusal.STORAGE_UNAVAILABLE)}
    }
    fun readCurrent(key:String,w:String,cycle:String,week:Int,day:Int,
        inputs:CustomCoachAuditInputs):NativeBlockCalibrationRead =runCatching{
        fence.withOwnerTransaction(key){
            val signed=owner(key)?:return@withOwnerTransaction NativeBlockCalibrationRead.Rejected(
                NativeCalibrationRefusal.NOT_AUTHORIZED)
            val saved=try{decode(key,w)}catch(_:Exception){return@withOwnerTransaction NativeBlockCalibrationRead.Rejected(
                NativeCalibrationRefusal.CORRUPT_RECORD)}
                ?:return@withOwnerTransaction NativeBlockCalibrationRead.Missing
            if(saved.athleteId!=signed.athleteId||saved.cycleId!=cycle||saved.absoluteWeek!=week||saved.weekday!=day)
                return@withOwnerTransaction NativeBlockCalibrationRead.Rejected(NativeCalibrationRefusal.REVIEW_CHANGED)
            val fresh=derive(key,w,cycle,inputs,week,day,saved.slotId,saved.readiness,
                saved.movementRestricted,saved.maximumIncrease,true,saved.kind) as? NativeBlockCalibrationPreview.Ready
                ?:return@withOwnerTransaction NativeBlockCalibrationRead.Rejected(NativeCalibrationRefusal.REVIEW_CHANGED)
            if(fresh.receipt!=saved)return@withOwnerTransaction NativeBlockCalibrationRead.Rejected(
                NativeCalibrationRefusal.REVIEW_CHANGED)
            NativeBlockCalibrationRead.Current(fresh)
        }
    }.getOrElse{NativeBlockCalibrationRead.Rejected(NativeCalibrationRefusal.STORAGE_UNAVAILABLE)}

    /** Discover durable preparations after process death, without issuing them. */
    fun pendingPreparations(key:String,cycle:String):NativePendingCalibrationRead =runCatching{
        fence.withOwnerTransaction(key){
            val signed=owner(key)?:return@withOwnerTransaction NativePendingCalibrationRead.Rejected(
                NativeCalibrationRefusal.NOT_AUTHORIZED)
            if(!id.matches(cycle))return@withOwnerTransaction NativePendingCalibrationRead.Rejected(
                NativeCalibrationRefusal.INVALID_SELECTION)
            val ledger=(history.read(key,signed.athleteId) as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                ?:return@withOwnerTransaction NativePendingCalibrationRead.Rejected(NativeCalibrationRefusal.HISTORY_UNAVAILABLE)
            val completed=ledger.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                .map{it.manifest.workoutId}.toSet()
            val owned=inventory.inspect(key,signed.athleteId,completed) as? NativeCustomInventoryReview.Reviewed
                ?:return@withOwnerTransaction NativePendingCalibrationRead.Rejected(NativeCalibrationRefusal.HISTORY_UNAVAILABLE)
            val found=ArrayList<NativeCustomPreparedWorkout>()
            for(item in owned.outstanding.filter{it.cycleId==cycle&&!it.issued}) {
                val stored=(prepared.open(key,item.workoutId) as? NativeCustomPreparedRead.Found)?.workout
                    ?:return@withOwnerTransaction NativePendingCalibrationRead.Rejected(NativeCalibrationRefusal.CORRUPT_RECORD)
                if(stored.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
                    (stored.calibrationApprovalFingerprint!=null||stored.resetToAuthored||
                        stored.authoredReturnFingerprint!=null))found+=stored
            }
            val usedIds=owned.sessions.map{it.workoutId}.toSet()+ledger.completions.map{it.manifest.workoutId}
            val accepted=indexedApprovals().filter{it.ownerKey==key&&it.athleteId==signed.athleteId&&
                it.cycleId==cycle&&it.workoutId !in usedIds}
            if(owner(key)!=signed)return@withOwnerTransaction NativePendingCalibrationRead.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED)
            NativePendingCalibrationRead.Ready(Collections.unmodifiableList(found),Collections.unmodifiableList(accepted))
        }
    }.getOrElse{NativePendingCalibrationRead.Rejected(NativeCalibrationRefusal.STORAGE_UNAVAILABLE)}
}
