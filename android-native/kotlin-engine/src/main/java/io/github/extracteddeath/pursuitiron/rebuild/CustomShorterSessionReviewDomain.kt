package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Safe, read-only 20/30/45-minute custom-session preview.
 *
 * This is NOT a change to an athlete-authored custom program, its approved
 * weekly dose, issued journal, supersets or advanced-set protocol. Its source
 * must come from the current, owner-fenced native custom consent preflight.
 * A future issued short custom workout will need its OWN durable, versioned
 * grant, history provenance and recovery tests, not this preview object.
 *
 * Unlike generated sessions, custom follow-ups have declared follow-up rest
 * but no separately declared follow-up ACTIVE duration. Never claim a total
 * time, or drop only part of a myo/drop/rest-pause/cluster set, when an
 * advanced-set prescription is present. Ask the author to choose Full or
 * supply separately reviewed full timing instead.
 */
enum class CustomShortSessionStatus {
    INVALID_LIMIT,
    INVALID_SNAPSHOT,
    NON_SET_TIME_UNDECLARED,
    ADVANCED_SET_TIME_UNDECLARED,
    ALREADY_FITS,
    READY_FOR_ATHLETE_REVIEW,
    PROTECTED_WORK_EXCEEDS_LIMIT
}

data class CustomShortSessionReview(
    val status: CustomShortSessionStatus,
    val requestedMinutes: Int,
    val source: CustomWorkoutSourceStamp?,
    val originalEstimatedSeconds: Long?,
    val reviewedEstimatedSeconds: Long?,
    /** Stable, original set keys in original slot/ordinal order. */
    val retainedSetKeys: List<String>,
    val omittedSetKeys: List<String>,
    val omittedSlotIds: List<String>,
    /** Supersets are never split, and their members keep original order. */
    val retainedSupersetGroups: List<String>,
    val omittedWorkingSets: Int,
    val explanation: String
) {
    val changesPrescription: Boolean get() = omittedSetKeys.isNotEmpty()
    val canIssueWorkout: Boolean get() = false
    val canSaveCustomCycle: Boolean get() = false
    val canAppendJournal: Boolean get() = false
    val canConfirmAthleteConsent: Boolean get() = false
}

object CustomShorterSessionReviewDomain {
    private val allowed = setOf(20,30,45)
    private const val MAX_SLOTS = 32
    private const val MAX_SETS = 256

    private fun invalid(
        kind: CustomShortSessionStatus, minutes: Int, message: String,
        sourceStamp: CustomWorkoutSourceStamp? = null
    ) = CustomShortSessionReview(kind,minutes,sourceStamp,null,null,
        emptyList(),emptyList(),emptyList(),emptyList(),0,message)

    private data class UnitOfWork(
        val slots: List<CustomWorkoutSlotSnapshot>,
        val originalIndex: Int
    ) {
        val removable: Boolean get() = slots.all {
            !it.pinned && it.role!=ComposerRole.PRIMARY
        }
        val accessoryOnly: Boolean get() =
            slots.all {it.role==ComposerRole.ACCESSORY}
    }

    /**
     * Not an authorization: caller MUST provide the fully re-reviewed
     * CustomWorkoutDaySnapshot from a native owner/consent preflight.
     */
    fun inspect(
        source: CustomWorkoutDaySnapshot,
        requestedMinutes: Int
    ): CustomShortSessionReview {
        if(requestedMinutes !in allowed)
            return invalid(CustomShortSessionStatus.INVALID_LIMIT,
                requestedMinutes,"Choose 20, 30 or 45 minutes, or keep the full custom workout.",source.stamp)
        val ordered=source.slots.flatMap {it.sets}
        val keySet=ordered.map{it.setKey}
        val sourceValid=source.slots.size in 1..MAX_SLOTS &&
            ordered.size in 1..MAX_SETS &&
            source.slots.map{it.slotId}.distinct().size==source.slots.size &&
            keySet.distinct().size==keySet.size &&
            ordered.all{it.setKey.isNotBlank()} &&
            source.slots.all {slot ->
                slot.sets.isNotEmpty() && slot.sets.size<=20 &&
                slot.sets.withIndex().all {(i,set)->
                    set.slotId==slot.slotId && set.ordinalInSlot==i+1 &&
                    set.prescription.activeSeconds>0 &&
                    set.prescription.restSeconds>=0 &&
                    (set.prescription.amplifier?.followUpRestSeconds?:0)>=0
                } &&
                slot.sets.any {it.prescription.type==DraftSetType.WORKING}
            }
        if(!sourceValid)
            return invalid(CustomShortSessionStatus.INVALID_SNAPSHOT,
                requestedMinutes,"The full custom session has invalid slot or set identities.",source.stamp)
        val knownSetSeconds=ordered.sumOf {
            it.prescription.activeSeconds.toLong()+
                it.prescription.restSeconds+
                (it.prescription.amplifier?.followUpRestSeconds?:0)
        }
        if(knownSetSeconds!=source.knownSetAndRestSeconds)
            return invalid(CustomShortSessionStatus.INVALID_SNAPSHOT,
                requestedMinutes,"The current set timing no longer matches the approved snapshot.",source.stamp)
        val overhead=source.declaredNonSetSeconds
            ?:return invalid(CustomShortSessionStatus.NON_SET_TIME_UNDECLARED,
                requestedMinutes,"Declare and review session setup and transition time before calculating a shorter custom workout.",source.stamp)
        if(overhead !in 0..21600)
            return invalid(CustomShortSessionStatus.INVALID_SNAPSHOT,
                requestedMinutes,"The declared preparation time is invalid.",source.stamp)
        if(ordered.any {it.prescription.amplifier!=null})
            return invalid(CustomShortSessionStatus.ADVANCED_SET_TIME_UNDECLARED,
                requestedMinutes,"Advanced-set follow-up WORK time has not been declared. No myo-rep, drop, cluster or rest-pause follow-up will be removed or assigned an invented duration.",source.stamp)

        // Sum every declared rest and active duration, even where rest might
        // overlap inside a superset. This is deliberately conservative and
        // NEVER subtracts an invented rest-overlap or transition saving.
        val original=knownSetSeconds+overhead
        val limit=requestedMinutes*60L
        val anchor=source.slots.firstOrNull {
            it.role==ComposerRole.PRIMARY
        }?.slotId ?: source.slots.first().slotId
        val protectAll=source.blockPurpose!=CustomBlockPurpose.TRAINING
        val protectCompound=source.phase in setOf(
            TrainingPhase.STRENGTH,TrainingPhase.PEAK)
        val protectedIds=source.slots.filter {
            it.slotId==anchor || it.pinned ||
                it.role==ComposerRole.PRIMARY ||
                protectAll || (protectCompound &&
                    it.role!=ComposerRole.ACCESSORY)
        }.map{it.slotId}.toSet()

        // A superset is one indivisible unit: never orphan its partner,
        // mismatch round count or cut its members independently.
        val units=linkedMapOf<String,MutableList<CustomWorkoutSlotSnapshot>>()
        for(slot in source.slots) {
            val key=slot.supersetGroup?.let {"group:$it"}
                ?: "slot:${slot.slotId}"
            units.getOrPut(key){mutableListOf()}.add(slot)
        }
        val grouped=units.values.map {slots->
            UnitOfWork(slots.toList(),
                source.slots.indexOf(slots.first()))
        }
        val keys=keySet.toMutableSet()
        fun selected()=ordered.filter{it.setKey in keys}
        fun seconds()=overhead+selected().sumOf {
            it.prescription.activeSeconds.toLong()+
                it.prescription.restSeconds
        }
        fun remainingWorking()=selected().count{
            it.prescription.type==DraftSetType.WORKING
        }

        // Optional accessories are reduced before secondary movements.
        // Later optional slots lose extra sets before earlier ones.
        val cutOrder=grouped.sortedWith(
            compareBy<UnitOfWork> {
                if(it.accessoryOnly)0 else 1
            }.thenByDescending{it.originalIndex}
        )
        if(original>limit) {
            // In an ungrouped, uniform optional slot, cut excess working
            // sets above TWO. Never cut a warmup, change a prescribed set,
            // or disturb authored top/backoff/variable-RIR schemes.
            for(unit in cutOrder) {
                if(seconds()<=limit)break
                if(unit.slots.size!=1)continue
                val slot=unit.slots.single()
                if(slot.slotId in protectedIds || slot.supersetGroup!=null)
                    continue
                val work=slot.sets.filter {
                    it.prescription.type==DraftSetType.WORKING
                }
                val signatures=work.map {
                    Triple(it.prescription.load,
                        it.prescription.reps,
                        it.prescription.targetRir)
                }.distinct()
                if(signatures.size!=1)continue
                for(set in work.asReversed()) {
                    if(seconds()<=limit)break
                    if(work.count{it.setKey in keys}<=2)break
                    keys.remove(set.setKey)
                }
            }
            // Remove optional slots ENTIRELY, with their warmups, only
            // after optional excess sets. A grouped superset goes as one.
            for(unit in cutOrder) {
                if(seconds()<=limit)break
                if(!unit.removable ||
                    unit.slots.any{it.slotId in protectedIds})
                    continue
                val removal=unit.slots.flatMap{it.sets}
                val workingRemoved=removal.count {
                    it.prescription.type==DraftSetType.WORKING &&
                        it.setKey in keys
                }
                if(remainingWorking()-workingRemoved<1)continue
                removal.forEach {keys.remove(it.setKey)}
            }
        }
        val retained=ordered.filter {it.setKey in keys}
        val omitted=ordered.filterNot{it.setKey in keys}
        val after=seconds()
        val status=when {
            omitted.isEmpty() && original<=limit ->
                CustomShortSessionStatus.ALREADY_FITS
            after<=limit -> CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW
            else -> CustomShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT
        }
        val explain=when(status) {
            CustomShortSessionStatus.ALREADY_FITS ->
                "The unchanged custom session already fits the conservative declared-time estimate."
            CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW ->
                "Only explicitly unpinned, optional training work was reduced. Supersets stay intact, warmups stay with their working slot, and every retained load, rep band, RIR and rest remains authored. Review the reduced dose before any separate consent."
            else ->
                "The protected custom work exceeds this time limit. Keep Full or choose a longer limit; the coach cannot silently change pinned exercises, strength work, supersets or rest."
        }
        val retainedKeys=retained.map{it.setKey}
        return CustomShortSessionReview(
            status,requestedMinutes,source.stamp,original,after,
            Collections.unmodifiableList(retainedKeys),
            Collections.unmodifiableList(omitted.map{it.setKey}),
            Collections.unmodifiableList(source.slots.filter{slot->
                slot.sets.none{it.setKey in keys}
            }.map{it.slotId}),
            Collections.unmodifiableList(source.slots.filter{slot->
                slot.supersetGroup!=null && slot.sets.any{it.setKey in keys}
            }.mapNotNull{it.supersetGroup}.distinct()),
            omitted.count{it.prescription.type==DraftSetType.WORKING},
            explain
        )
    }
}
