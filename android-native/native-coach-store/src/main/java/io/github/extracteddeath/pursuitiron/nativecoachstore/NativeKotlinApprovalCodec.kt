package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal

/**
 * Explicit versioned binary encoding for the COMPLETE Kotlin-approved program:
 * every day, muscle bound, exercise/confirmed equipment stock, intent and
 * warmup/working-set policy. No JavaScript parser, Java object serialization,
 * caller-supplied set targets or lossy decimal conversion.
 *
 * This codec is transport only; it is NOT a signature or athlete approval.
 * NativeKotlinApprovalFileAuthority uses a checksum and a real signed-in
 * identity provider in addition to decoding.
 */
internal object NativeKotlinApprovalCodec {
    private const val MAGIC=0x50494131 // PIA1
    private const val VERSION=8
    const val MAX_BYTES=512_000

    private fun DataOutputStream.string(value:String)=writeUTF(value)
    private fun DataOutputStream.decimal(value:BigDecimal)=
        string(value.toPlainString())
    private fun DataInputStream.decimal():BigDecimal=BigDecimal(readUTF())
    private fun DataOutputStream.optionalString(value:String?) {
        writeBoolean(value!=null);if(value!=null)string(value)
    }
    private fun DataInputStream.optionalString():String?=
        if(readBoolean())readUTF() else null
    private fun DataOutputStream.optionalInt(value:Int?) {
        writeBoolean(value!=null);if(value!=null)writeInt(value)
    }
    private fun DataInputStream.optionalInt():Int?=
        if(readBoolean())readInt() else null
    private fun DataOutputStream.optionalDecimal(value:BigDecimal?) {
        writeBoolean(value!=null);if(value!=null)decimal(value)
    }
    private fun DataInputStream.optionalDecimal():BigDecimal?=
        if(readBoolean())decimal() else null
    private fun DataOutputStream.optionalBand(value:RepBand?) {
        writeBoolean(value!=null)
        if(value!=null) {writeInt(value.minimum);writeInt(value.maximum)}
    }
    private fun DataInputStream.optionalBand():RepBand?=
        if(readBoolean())RepBand(readInt(),readInt()) else null

    private fun DataOutputStream.writeEnum(value:Enum<*>)=string(value.name)
    private inline fun <reified T:Enum<T>> DataInputStream.readEnum():T {
        val raw=readUTF()
        return enumValues<T>().firstOrNull{it.name==raw}
            ?:throw IOException("unknown approval enum")
    }
    private fun <T> DataOutputStream.items(values:Collection<T>,
        writer:(T)->Unit) {
        writeInt(values.size)
        values.forEach(writer)
    }
    private fun <T> DataInputStream.items(max:Int,reader:()->T):List<T> {
        val count=readInt()
        if(count !in 0..max)throw IOException("approval collection too large")
        return List(count){reader()}
    }
    private fun DataOutputStream.source(s:KotlinTrainingSourceRevision) {
        string(s.athleteId);writeLong(s.athleteRevision)
        string(s.programId);writeLong(s.programRevision)
        string(s.gymId);writeLong(s.gymRevision)
        string(s.catalogRevision);string(s.coachingRulesRevision)
    }
    private fun DataInputStream.source()=KotlinTrainingSourceRevision(
        readUTF(),readLong(),readUTF(),readLong(),readUTF(),readLong(),
        readUTF(),readUTF()
    )

    private fun DataOutputStream.stock(stock:VerifiedLoadStock?) {
        when(stock) {
            null -> writeByte(0)
            is VerifiedLoadStock.Totals -> {
                writeByte(1);writeEnum(stock.unit);writeEnum(stock.basis)
                items(stock.loads){decimal(it)}
            }
            is VerifiedLoadStock.Barbell -> {
                writeByte(2);writeEnum(stock.unit);decimal(stock.barWeight)
                items(stock.pairedPlates.entries){(plate,count)->
                    decimal(plate);writeInt(count)
                }
            }
            is VerifiedLoadStock.Stack -> {
                writeByte(3);writeEnum(stock.unit);writeEnum(stock.basis)
                decimal(stock.minimum);decimal(stock.increment)
                decimal(stock.maximum)
            }
        }
    }
    private fun DataInputStream.stock():VerifiedLoadStock?=when(readUnsignedByte()) {
        0 -> null
        1 -> VerifiedLoadStock.Totals(readEnum<LoadUnit>(),readEnum<LoadBasis>(),
            items(512){decimal()})
        2 -> {
            val unit=readEnum<LoadUnit>();val bar=decimal()
            val entries=items(32){decimal() to readInt()}
            if(entries.map{it.first}.distinct().size!=entries.size)
                throw IOException("duplicate plate denomination")
            VerifiedLoadStock.Barbell(unit,bar,entries.toMap())
        }
        3 -> VerifiedLoadStock.Stack(readEnum<LoadUnit>(),readEnum<LoadBasis>(),
            decimal(),decimal(),decimal())
        else -> throw IOException("unknown equipment inventory")
    }

    private fun DataOutputStream.equipment(e:ComposerEquipment) {
        when(e) {
            ComposerEquipment.Bodyweight -> writeByte(0)
            is ComposerEquipment.Verified -> {
                writeByte(1);writeEnum(e.unit);decimal(e.plannedLoad);stock(e.inventory)
            }
        }
    }
    private fun DataInputStream.equipment():ComposerEquipment=when(readUnsignedByte()) {
        0 -> ComposerEquipment.Bodyweight
        1 -> ComposerEquipment.Verified(readEnum<LoadUnit>(),decimal(),stock())
        else -> throw IOException("unknown equipment")
    }

    private fun DataOutputStream.exercise(e:ComposerExerciseOption) {
        string(e.exerciseId);writeEnum(e.pattern)
        items(e.allowedFocus.sortedBy{it.ordinal}){writeEnum(it)}
        equipment(e.equipment)
        items(e.credits){c->
            writeEnum(c.muscle);writeEnum(c.role);decimal(c.fractionalSet)
        }
        writeInt(e.workSeconds);writeInt(e.restSeconds)
        writeInt(e.minimumSetsPerVisit);writeInt(e.maximumSetsPerVisit)
        writeInt(e.maximumVisits);writeInt(e.requiredVisits)
        writeInt(e.minimumCalendarDayGap);writeInt(e.priority)
        optionalString(e.recoveryGroup)
        writeInt(e.minimumGroupCalendarDayGap)
        writeEnum(e.role)
        optionalBand(e.sourcePracticalReps)
    }
    private fun DataInputStream.exercise():ComposerExerciseOption {
        val exerciseId=readUTF()
        val pattern=readEnum<ComposerPattern>()
        val allowedFocus=items(6){readEnum<ComposerFocus>()}
        if(allowedFocus.distinct().size!=allowedFocus.size)
            throw IOException("duplicate allowed focus")
        val equipment=equipment()
        val credits=items(16){
            DeclaredMuscleCredit(readEnum<TrainingMuscle>(),
                readEnum<MuscleCreditRole>(),decimal())
        }
        return ComposerExerciseOption(
            exerciseId,pattern,allowedFocus.toSet(),equipment,credits,
            readInt(),readInt(),readInt(),readInt(),readInt(),readInt(),
            readInt(),readInt(),optionalString(),readInt(),readEnum<ComposerRole>(),
            optionalBand()
        )
    }
    private fun DataOutputStream.program(p:ComposerRequest,version:Int) {
        writeEnum(p.split);writeEnum(p.phase)
        items(p.days){day->
            writeInt(day.calendarDay);writeEnum(day.focus)
            val window=day.window
            writeInt(window.preferredMinimumMinutes)
            optionalInt(window.preferredMaximumMinutes)
            optionalInt(window.userHardLimitMinutes)
            writeInt(window.declaredNonSetSeconds)
        }
        items(p.bounds){b->
            writeEnum(b.muscle);decimal(b.mev);decimal(b.mav);decimal(b.mrv)
            writeInt(b.minimumDirectDays)
        }
        items(p.exercises){exercise(it)}
        writeInt(p.maximumDifferentExercisesPerDay)
        writeInt(p.preferredPatternsPerDay)
        items(p.sessionIntents){i->
            writeInt(i.dayIndex);writeEnum(i.purpose)
            optionalString(i.requiredPrimaryExerciseId)
        }
        optionalInt(p.maximumBarbellExercisesPerDay)
        writeBoolean(p.allowDirectTricepsOnLower)
        if(version>=7)items(p.visitTimings){t->
            string(t.exerciseId);writeEnum(t.purpose);optionalInt(t.dayIndex)
            writeInt(t.activeSecondsPerSet);writeInt(t.restSecondsPerSet)
            items(t.warmups){w->writeInt(w.activeSeconds);writeInt(w.restSeconds)}
        }
    }
    private fun DataInputStream.program(version:Int):ComposerRequest {
        val split=readEnum<ComposerSplit>();val phase=readEnum<TrainingPhase>()
        val days=items(6){
            ComposerDay(readInt(),readEnum<ComposerFocus>(),
                DeclaredSessionWindow(readInt(),optionalInt(),
                    optionalInt(),readInt()))
        }
        val bounds=items(16){
            DeclaredMuscleDoseBounds(readEnum<TrainingMuscle>(),decimal(),
                decimal(),decimal(),readInt())
        }
        val exercises=items(32){exercise()}
        val maxDifferent=readInt()
        val preferred=readInt()
        val intents=items(6){
            ComposerSessionIntent(readInt(),readEnum<ComposerSessionPurpose>(),
                optionalString())
        }
        val barbellCap=optionalInt()
        val triceps=readBoolean()
        val timings=if(version>=7)items(128){
            val id=readUTF();val purpose=readEnum<ComposerSessionPurpose>()
            val day=optionalInt();val active=readInt();val rest=readInt()
            ComposerVisitTiming(id,purpose,active,rest,
                items(12){ComposerWarmupTiming(readInt(),readInt())},day)
        } else emptyList()
        return ComposerRequest(split,phase,days,bounds,exercises,maxDifferent,
            preferred,intents,barbellCap,triceps,timings)
    }

    private fun DataOutputStream.policies(policies:List<ExercisePrescriptionPolicy>) {
        items(policies){p->
            string(p.exerciseId);writeEnum(p.purpose)
            writeInt(p.workingReps.minimum);writeInt(p.workingReps.maximum)
            optionalInt(p.targetRir)
            writeInt(p.activeSecondsPerSet);writeInt(p.restSecondsPerSet)
            writeEnum(p.scheme);optionalDecimal(p.maximumAuthorizedBackoff)
            items(p.warmups){w->
                decimal(w.load);writeInt(w.reps)
                writeInt(w.activeSeconds);writeInt(w.restSeconds)
            }
            optionalBand(p.backoffReps);optionalInt(p.backoffRir)
            // Version 3: preserve verified strength/hypertrophy working loads.
            optionalDecimal(p.purposeWorkingLoad)
            // v4: day slot is distinct from session purpose; no lossy merge.
            optionalInt(p.dayIndex)
        }
    }
    private fun DataInputStream.policies(version:Int):List<ExercisePrescriptionPolicy> =
        items(128){
            ExercisePrescriptionPolicy(readUTF(),readEnum<ComposerSessionPurpose>(),
                RepBand(readInt(),readInt()),optionalInt(),
                readInt(),readInt(),readEnum<SetLoadScheme>(),optionalDecimal(),
                items(12){
                    DeclaredWarmupStep(decimal(),readInt(),readInt(),readInt())
                },optionalBand(),optionalInt(),
                if(version>=3)optionalDecimal() else null,
                if(version>=4)optionalInt() else null)
        }

    private fun verifiedRepProof(
        proof:NativeKotlinAcceptedRepTargets,
        base:KotlinStagedWorkout
    ) {
        val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
        require(id.matches(proof.historicalWorkoutId) &&
            proof.historicalWorkoutId!=base.workoutId &&
            id.matches(proof.exerciseId) &&
            id.matches(proof.changedSetKey) &&
            proof.historyRevision in 1..9_007_199_254_740_991L &&
            proof.reviewedReadiness!=CoachReadiness.LOW &&
            proof.observedReps.size in 1..32 &&
            proof.newRepTargets.size==proof.observedReps.size)
        val working=base.targets.filter {
            it.exerciseId==proof.exerciseId && it.type==DraftSetType.WORKING
        }.sortedBy {it.ordinal}
        require(working.size==proof.observedReps.size &&
            working.map {it.ordinal}==(0 until working.size).toList() &&
            proof.changedSetKey in working.map {it.setKey})
        val differences=proof.newRepTargets.zip(proof.observedReps)
            .mapIndexedNotNull {i,(next,was)->
                require(was in 1..100 && next in 1..100 &&
                    next in working[i].reps.minimum..working[i].reps.maximum)
                if(proof.decisionKind==AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP)
                    require(next==was || next==was+1)
                if(next!=was)i else null
            }
        when(proof.decisionKind) {
            AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP ->
                require(differences.size==1 &&
                    working[differences.single()].setKey==proof.changedSetKey)
            AdaptiveRepDecisionKind.TARGET_RIR_ALIGNED_REPS ->
                require(differences.isNotEmpty() &&
                    working[differences.first()].setKey==proof.changedSetKey)
            AdaptiveRepDecisionKind.REPEAT_PREVIOUSLY_ISSUED_TARGETS,
            AdaptiveRepDecisionKind.REPEAT_TARGET_RIR_ALIGNED_REPS -> {
                // Repeated goals may equal observed reps OR exceed several
                // missed exact goals. The owner-fenced history re-derivation
                // must prove these are exactly the previously issued targets.
            }
        }
        require(working.all {it.load is DraftSetLoad.Verified})
    }

    fun encode(value:NativeKotlinWorkoutApproval):ByteArray {
        // An approval must be a COMPLETE independently reviewed week.
        require(value.ownerKey.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")))
        require(value.ownerKey.isNotBlank() &&
            value.athleteId==value.approvedSource.athleteId &&
            value.approvedSource==value.currentSource &&
            value.grant.approvedSource==value.approvedSource &&
            value.storageRevision>=1)
        val staged=KotlinWorkoutSnapshotDomain.stage(
            KotlinWorkoutSnapshotRequest(value.approvedSource,
                value.currentSource,value.grant,value.program,value.policies))
        require(staged is KotlinWorkoutSnapshotResult.Staged) {
            "Cannot persist a partially reviewed or infeasible approval"
        }
        value.acceptedRepTargets?.let {verifiedRepProof(it,staged.snapshot)}
        if(value.shortSessionLimitMinutes!=null) {
            require(value.acceptedRepTargets==null) {
                "A shortened session cannot silently discard an approved per-set rep change"
            }
            val review=KotlinShorterSessionReviewDomain.review(
                KotlinWorkoutSnapshotRequest(value.approvedSource,
                    value.currentSource,value.grant,value.program,value.policies),
                value.shortSessionLimitMinutes)
            require(review.status in setOf(
                KotlinShortSessionStatus.ALREADY_FITS,
                KotlinShortSessionStatus.READY_FOR_ATHLETE_REVIEW)) {
                "Time-limited approval must preserve the protected workload"
            }
        }
        val bytes=ByteArrayOutputStream()
        // Older v6/v7 approvals remain byte-identical; only an explicit
        // time-limited approval introduces the v8 trailing selection.
        val version=if(value.shortSessionLimitMinutes!=null)VERSION
            else if(value.program.visitTimings.isEmpty())6 else 7
        DataOutputStream(bytes).use {out->
            with(out){
                writeInt(MAGIC);writeInt(version)
                string(value.ownerKey);string(value.athleteId)
                source(value.approvedSource);source(value.currentSource)
                string(value.grant.grantId);string(value.grant.workoutId)
                source(value.grant.approvedSource);writeInt(value.grant.dayIndex)
                program(value.program,version);policies(value.policies)
                string(value.storageReceiptId);writeLong(value.storageRevision)
                val proof=value.acceptedRepTargets
                writeBoolean(proof!=null)
                if(proof!=null) {
                    string(proof.historicalWorkoutId);writeLong(proof.historyRevision)
                    string(proof.exerciseId);string(proof.changedSetKey)
                    items(proof.observedReps){writeInt(it)}
                    items(proof.newRepTargets){writeInt(it)}
                    writeEnum(proof.reviewedReadiness)
                    // v6 adds target-RIR adjustments; v1 single-set receipts stay immutable.
                    writeEnum(proof.decisionKind)
                }
                if(version>=8)optionalInt(value.shortSessionLimitMinutes)
            }
        }
        return bytes.toByteArray().also {require(it.size<=MAX_BYTES)}
    }

    fun decode(bytes:ByteArray):NativeKotlinWorkoutApproval {
        if(bytes.size !in 8..MAX_BYTES)throw IOException("invalid approval size")
        try {
            val value=DataInputStream(ByteArrayInputStream(bytes)).use {inp->
                with(inp){
                    if(readInt()!=MAGIC)
                        throw IOException("unrecognized approval encoding")
                    val version=readInt()
                    if(version !in 1..VERSION)
                        throw IOException("unsupported approval encoding")
                    val owner=readUTF();val athlete=readUTF()
                    val approved=source();val current=source()
                    val grant=KotlinWorkoutSelectionGrant(
                        readUTF(),readUTF(),source(),readInt())
                    val program=program(version)
                    val policies=policies(version)
                    val receipt=readUTF();val revision=readLong()
                    val proof=if(version>=2 && readBoolean()) {
                        NativeKotlinAcceptedRepTargets(
                            readUTF(),readLong(),readUTF(),readUTF(),
                            items(32){readInt()},items(32){readInt()},
                            readEnum<CoachReadiness>(),
                            if(version>=5)readEnum<AdaptiveRepDecisionKind>()
                                else AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP)
                    } else null
                    if(version<6 && proof!=null &&
                        proof.decisionKind.policyVersion!=AdaptiveRepPolicyVersion.LEGACY_SINGLE_SET_V1)
                        throw IOException("target-RIR decisions require approval encoding v6")
                    if(available()!=0)throw IOException("trailing approval bytes")
                    val timeLimit=if(version>=8)optionalInt() else null
                    NativeKotlinWorkoutApproval(owner,athlete,approved,current,
                        grant,program,policies,receipt,revision,proof,timeLimit)
                }
            }
            // Reject malformed but checksum-valid contents too.
            encode(value)
            return value
        } catch(e:Exception) {
            throw IOException("invalid Kotlin approval",e)
        }
    }
}
