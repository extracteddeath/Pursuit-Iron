package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** Explicit single-session edit of ordinary working rows. This is a review,
 * not an issuance, a journal write, a future-program approval or a new
 * progression calculator. Both native workout types use the same policy. */
data class MidSessionWorkingEdit(
    val workoutId:String,
    val expectedRevision:Long,
    /** Generated exercise ID or exact custom slot ID (never a display ordinal). */
    val occurrenceId:String,
    /** Total working rows, including already resolved working rows. */
    val workingSetCount:Int,
    val reps:RepBand?=null,
    val restSeconds:Int?=null
)
enum class MidSessionWorkingEditRefusal {
    STALE_WORKOUT, FINISHED, UNKNOWN_OCCURRENCE, INVALID_REQUEST,
    PROTECTED_PROTOCOL, RESOLVED_WORK, NO_PENDING_WORK, SESSION_LIMIT, NO_CHANGE
}
sealed interface MidSessionWorkingEditReview<out T> {
    data class Rejected(val reason:MidSessionWorkingEditRefusal):
        MidSessionWorkingEditReview<Nothing>
    data class Reviewed<T>(
        val request:MidSessionWorkingEdit,
        val before:List<T>,
        val after:List<T>,
        val addedSetKeys:List<String>,
        val omittedSetKeys:List<String>,
        /** Existing factual work is untouched; added rows have no actuals. */
        val resolvedSetKeys:List<String>
    ):MidSessionWorkingEditReview<T> {
        val mayAppendJournal:Boolean get()=false
        val maySaveProgram:Boolean get()=false
    }
}
object MidSessionWorkingEditDomain {
    private fun refused(reason:MidSessionWorkingEditRefusal)=MidSessionWorkingEditReview.Rejected(reason)
    private data class Row<T>(
        val value:T,val key:String,val type:DraftSetType,val ordinal:Int,
        val load:DraftSetLoad,val reps:RepBand,val rir:Int?,val active:Int,val rest:Int
    )
    private fun <T> review(
        request:MidSessionWorkingEdit,
        rows:List<Row<T>>,allKeys:Set<String>,resolved:Set<String>,
        protected:Boolean,allowReduction:Boolean,maxKeyLength:Int,
        copy:(T,String,Int,RepBand,Int)->T
    ):MidSessionWorkingEditReview<T> {
        if(rows.isEmpty())return refused(MidSessionWorkingEditRefusal.UNKNOWN_OCCURRENCE)
        if(protected)return refused(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL)
        val band=request.reps
        if(request.workingSetCount !in 1..20 ||
            band?.let{!KotlinSetNumberAuthorityDomain.validCompletedReps(it.minimum) ||
                !KotlinSetNumberAuthorityDomain.validCompletedReps(it.maximum) ||
                it.minimum>it.maximum}==true ||
            request.restSeconds?.let{it !in 0..3600}==true)
            return refused(MidSessionWorkingEditRefusal.INVALID_REQUEST)
        val working=rows.filter{it.type==DraftSetType.WORKING}
        if(working.isEmpty() || rows.any{!KotlinSetNumberAuthorityDomain.validIssuedLoad(it.load)})
            return refused(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL)
        // Only ordinary uniform working prescriptions can be edited by this
        // path. Do not flatten a backoff, pyramid, or specialist protocol.
        val template=working.last()
        if(working.any{!KotlinSetNumberAuthorityDomain.sameIssuedTarget(it.load,template.load) ||
                it.reps!=template.reps || it.rir!=template.rir ||
                it.active!=template.active || it.rest!=template.rest})
            return refused(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL)
        val pending=working.filter{it.key !in resolved}
        if(pending.isEmpty())return refused(MidSessionWorkingEditRefusal.NO_PENDING_WORK)
        val difference=request.workingSetCount-working.size
        if(difference<0 && !allowReduction)return refused(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL)
        if(difference<0 && -difference>pending.size)
            return refused(MidSessionWorkingEditRefusal.RESOLVED_WORK)
        if(allKeys.size+difference>256 || rows.size+difference>20)
            return refused(MidSessionWorkingEditRefusal.SESSION_LIMIT)
        // Remove only a suffix of working rows. A later resolved row (even
        // a skip or failed attempt) prevents removing a row ahead of it.
        val omitted=if(difference<0)working.takeLast(-difference) else emptyList()
        if(omitted.any{it.key in resolved})return refused(MidSessionWorkingEditRefusal.RESOLVED_WORK)
        val omittedKeys=omitted.map{it.key}
        val retained=rows.filter{it.key !in omittedKeys}
        val next=retained.map{row->
            if(row.type!=DraftSetType.WORKING || row.key in resolved)row.value
            else copy(row.value,row.key,row.ordinal,band?:row.reps,request.restSeconds?:row.rest)
        }.toMutableList()
        val added=mutableListOf<String>()
        if(difference>0) {
            // Revision-scoped keys never replace an existing actual or
            // original ordinal. They are not set facts until actually logged.
            val lastOrdinal=rows.maxOf{it.ordinal}
            if(lastOrdinal>Int.MAX_VALUE-difference)return refused(MidSessionWorkingEditRefusal.INVALID_REQUEST)
            repeat(difference){i->
                val key="mid-${request.expectedRevision+1}-${i+1}"
                if(key.length>maxKeyLength || key in allKeys)
                    return refused(MidSessionWorkingEditRefusal.INVALID_REQUEST)
                added.add(key)
                next.add(copy(template.value,key,lastOrdinal+i+1,band?:template.reps,
                    request.restSeconds?:template.rest))
            }
        }
        if(next==rows.map{it.value})return refused(MidSessionWorkingEditRefusal.NO_CHANGE)
        return MidSessionWorkingEditReview.Reviewed(request,
            Collections.unmodifiableList(rows.map{it.value}),Collections.unmodifiableList(next),
            Collections.unmodifiableList(added),Collections.unmodifiableList(omittedKeys),
            Collections.unmodifiableList(rows.filter{it.key in resolved}.map{it.key}))
    }

    fun generated(state:KotlinWorkoutJournalState,request:MidSessionWorkingEdit):
        MidSessionWorkingEditReview<KotlinWorkoutTarget> {
        if(state.snapshot.workoutId!=request.workoutId || state.revision!=request.expectedRevision ||
            request.expectedRevision !in 0 until 9_007_199_254_740_991L)
            return refused(MidSessionWorkingEditRefusal.STALE_WORKOUT)
        if(state.finishedByEventId!=null)return refused(MidSessionWorkingEditRefusal.FINISHED)
        val rows=state.snapshot.targets.filter{it.exerciseId==request.occurrenceId}
        return review(request,rows.map{Row(it,it.setKey,it.type,it.ordinal,it.load,
            it.reps,it.targetRir,it.activeSeconds,it.restSeconds)},
            (state.originalSnapshot.targets+state.snapshot.targets).map{it.setKey}.toSet(),
            state.effectiveSets.keys,
            protected=state.snapshot.purpose!=ComposerSessionPurpose.HYPERTROPHY,
            allowReduction=rows.all{it.role==ComposerRole.ACCESSORY},maxKeyLength=100
        ){row,key,ordinal,reps,rest->row.copy(setKey=key,ordinal=ordinal,reps=reps,restSeconds=rest)}
    }

    fun custom(state:CustomWorkoutJournalDraft,request:MidSessionWorkingEdit):
        MidSessionWorkingEditReview<CustomWorkoutSetSnapshot> {
        if(state.workoutId!=request.workoutId || state.revision!=request.expectedRevision ||
            request.expectedRevision !in 0 until 9_007_199_254_740_991L)
            return refused(MidSessionWorkingEditRefusal.STALE_WORKOUT)
        if(state.finishedByEventId!=null)return refused(MidSessionWorkingEditRefusal.FINISHED)
        val slot=state.snapshot.slots.singleOrNull{it.slotId==request.occurrenceId}
            ?:return refused(MidSessionWorkingEditRefusal.UNKNOWN_OCCURRENCE)
        val rows=slot.sets.map{set->with(set.prescription){
            Row(set,set.setKey,type,set.ordinalInSlot,load,reps,targetRir,activeSeconds,restSeconds)
        }}
        return review(request,rows,
            (state.originalSnapshot.slots+state.snapshot.slots).flatMap{it.sets}.map{it.setKey}.toSet(),
            state.effectiveVisits.keys.map{it.setKey}.toSet(),
            protected=state.snapshot.blockPurpose!=CustomBlockPurpose.TRAINING ||
                state.snapshot.sessionPurpose!=ComposerSessionPurpose.HYPERTROPHY ||
                slot.supersetGroup!=null || slot.sets.any{
                    it.prescription.amplifier!=null || it.prescription.percentageIntent!=null ||
                        it.prescription.exactRepTarget!=null
                },
            allowReduction=!slot.pinned && slot.role==ComposerRole.ACCESSORY,maxKeyLength=160
        ){row,key,ordinal,reps,rest->row.copy(setKey=key,ordinalInSlot=ordinal,
            prescription=row.prescription.copy(reps=reps,restSeconds=rest))}
    }
}
