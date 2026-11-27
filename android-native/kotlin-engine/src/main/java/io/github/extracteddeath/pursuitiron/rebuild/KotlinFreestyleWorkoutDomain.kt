package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** Unplanned training has physical context and observed sets, not a program,
 * cycle, issued weight, rep band or target RIR. Owner authentication, durable
 * source review, storage CAS and canonical History Finish remain host duties. */
data class KotlinFreestyleSource(
    val athleteId:String,val gymId:String,val profileRevision:String,val gymRevision:Long
)
data class KotlinFreestyleExercise(
    val slotId:String,val exerciseId:String,val familyId:String,val variantId:String,
    val attachmentId:String?,val unit:LoadUnit?,val basis:LoadBasis?,
    /** Optional reusable row shape, not a prescription or recorded result. */
    val routineSetTypes:List<DraftSetType> = emptyList()
)
sealed interface KotlinFreestyleOperation {
    val eventId:String
    val expectedRevision:Long
    data class Add(override val eventId:String,override val expectedRevision:Long,
        val exercise:KotlinFreestyleExercise):KotlinFreestyleOperation
    data class Reorder(override val eventId:String,override val expectedRevision:Long,
        val slotIds:List<String>):KotlinFreestyleOperation
    /** Set keys belong to stable slots, never their current display position.
     * An explicit correction names the current effective receipt. */
    data class Record(override val eventId:String,override val expectedRevision:Long,
        val slotId:String,val setKey:String,val type:DraftSetType,
        val outcome:KotlinJournalSetOutcome,val actual:KotlinPerformedSet?,
        val correctsEventId:String?=null):KotlinFreestyleOperation
    data class Finish(override val eventId:String,override val expectedRevision:Long):
        KotlinFreestyleOperation
}
data class KotlinFreestyleReceipt(val revision:Long,val operation:KotlinFreestyleOperation)
data class KotlinFreestyleWorkout(
    val workoutId:String,val source:KotlinFreestyleSource,val revision:Long,
    val exercises:List<KotlinFreestyleExercise>,
    val receipts:List<KotlinFreestyleReceipt>,
    val effectiveSets:Map<String,KotlinFreestyleOperation.Record>,
    val finishedByEventId:String?
) {
    val mayPublishHistory:Boolean get()=false
    val mayChangeProgram:Boolean get()=false
}
/** Factual view in exercise order, with exact stable slot and effective event. */
data class KotlinFreestyleObservation(
    val exercise:KotlinFreestyleExercise,
    val record:KotlinFreestyleOperation.Record
)
fun KotlinFreestyleWorkout.observations():List<KotlinFreestyleObservation> =
    exercises.flatMap{exercise->
        effectiveSets.values.filter{it.slotId==exercise.slotId}.map{
            KotlinFreestyleObservation(exercise,it)
        }
    }

data class KotlinFreestyleRoutineSlot(val exerciseId:String,val setTypes:List<DraftSetType>)
data class KotlinFreestyleRoutine(
    val routineId:String,val athleteId:String,val name:String,
    val slots:List<KotlinFreestyleRoutineSlot>
)
enum class KotlinFreestyleRefusal {
    INVALID_EVENT, STALE_REVISION, EVENT_COLLISION, FINISHED, INVALID_EXERCISE,
    INVALID_ORDER, UNKNOWN_SLOT, INVALID_SET, INVALID_CORRECTION, EMPTY_WORKOUT, LIMIT
}
sealed interface KotlinFreestyleDecision {
    data class Proposed(val workout:KotlinFreestyleWorkout,val replayed:Boolean=false):
        KotlinFreestyleDecision
    data class Rejected(val reason:KotlinFreestyleRefusal):KotlinFreestyleDecision
}

object KotlinFreestyleWorkoutDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_EXERCISES=32
    private const val MAX_SETS=256
    private const val MAX_SETS_PER_SLOT=20

    fun source(setup:AthleteSetupReview,gymRevision:Long):KotlinFreestyleSource? {
        val athlete=setup.reviewed?.takeIf{
            setup.status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW
        }?:return null
        return KotlinFreestyleSource(athlete.athleteProfileId,athlete.gym.gymId,
            athlete.profileRevision,gymRevision).takeIf(::validSource)
    }

    private fun validSource(source:KotlinFreestyleSource):Boolean =
        id.matches(source.athleteId) && id.matches(source.gymId) &&
            source.profileRevision.isNotBlank() && source.gymRevision in 1..9_007_199_254_740_991L

    fun begin(workoutId:String,source:KotlinFreestyleSource):KotlinFreestyleWorkout? =
        if(id.matches(workoutId) && validSource(source))
            KotlinFreestyleWorkout(workoutId,source,0,emptyList(),emptyList(),emptyMap(),null)
        else null

    /** Reuse the current native picker. A previously saved routine or a raw
     * event is not permission to add an unavailable exercise or attachment.
     * The owner must call this again against authenticated current source
     * before committing an Add; replay uses historical exact context. */
    fun selectedExercise(workout:KotlinFreestyleWorkout,setup:AthleteSetupReview,
        gymRevision:Long,catalog:List<CatalogMovement>,slotId:String,exerciseId:String
    ):KotlinFreestyleExercise? {
        if(source(setup,gymRevision)!=workout.source || !id.matches(slotId))return null
        val option=CustomExerciseAuthoringDomain.picker(setup,catalog)
            .singleOrNull{it.exerciseId==exerciseId}?:return null
        return KotlinFreestyleExercise(slotId,option.exerciseId,option.familyId,
            option.variantId,option.attachmentId,option.loadUnit,option.loadBasis)
    }

    private fun validExercise(exercise:KotlinFreestyleExercise):Boolean =
        listOf(exercise.slotId,exercise.exerciseId,exercise.familyId,exercise.variantId)
            .all(id::matches) && (exercise.attachmentId==null || id.matches(exercise.attachmentId)) &&
            ((exercise.unit==null)==(exercise.basis==null)) &&
            exercise.routineSetTypes.size<=MAX_SETS_PER_SLOT

    fun reduce(prior:KotlinFreestyleWorkout,input:KotlinFreestyleOperation):KotlinFreestyleDecision {
        fun reject(reason:KotlinFreestyleRefusal)=KotlinFreestyleDecision.Rejected(reason)
        val op=when(input) {
            is KotlinFreestyleOperation.Reorder ->input.copy(
                slotIds=Collections.unmodifiableList(input.slotIds.toList()))
            is KotlinFreestyleOperation.Add ->input.copy(exercise=input.exercise.copy(
                routineSetTypes=Collections.unmodifiableList(input.exercise.routineSetTypes.toList())))
            else ->input
        }
        if(!id.matches(op.eventId))return reject(KotlinFreestyleRefusal.INVALID_EVENT)
        val old=prior.receipts.firstOrNull{it.operation.eventId==op.eventId}
        if(old!=null)return if(old.operation==op)KotlinFreestyleDecision.Proposed(prior,true)
            else reject(KotlinFreestyleRefusal.EVENT_COLLISION)
        if(prior.revision!=op.expectedRevision)return reject(KotlinFreestyleRefusal.STALE_REVISION)
        if(prior.finishedByEventId!=null)return reject(KotlinFreestyleRefusal.FINISHED)
        // Reserve room for Finish even when the athlete has exhausted edits.
        if(prior.receipts.size>=KotlinWorkoutJournalDomain.MAX_RECEIPTS ||
            (prior.receipts.size>=KotlinWorkoutJournalDomain.EDIT_LIMIT &&
                op !is KotlinFreestyleOperation.Finish &&
                !(op is KotlinFreestyleOperation.Record && op.setKey !in prior.effectiveSets)))
            return reject(KotlinFreestyleRefusal.LIMIT)
        var exercises=prior.exercises
        var sets=prior.effectiveSets
        var finish:String?=null
        when(op) {
            is KotlinFreestyleOperation.Add -> {
                if(!validExercise(op.exercise) || exercises.any{it.slotId==op.exercise.slotId})
                    return reject(KotlinFreestyleRefusal.INVALID_EXERCISE)
                if(exercises.size>=MAX_EXERCISES ||
                    exercises.sumOf{it.routineSetTypes.size}+op.exercise.routineSetTypes.size>MAX_SETS)
                    return reject(KotlinFreestyleRefusal.LIMIT)
                exercises=Collections.unmodifiableList(exercises+op.exercise)
            }
            is KotlinFreestyleOperation.Reorder -> {
                val byId=exercises.associateBy{it.slotId}
                if(op.slotIds.size!=exercises.size || op.slotIds.toSet()!=byId.keys)
                    return reject(KotlinFreestyleRefusal.INVALID_ORDER)
                exercises=Collections.unmodifiableList(op.slotIds.map(byId::getValue))
            }
            is KotlinFreestyleOperation.Record -> {
                val exercise=exercises.singleOrNull{it.slotId==op.slotId}
                    ?:return reject(KotlinFreestyleRefusal.UNKNOWN_SLOT)
                if(!id.matches(op.setKey) ||
                    KotlinSetNumberAuthorityDomain.actualRefusal(exercise.unit,op.outcome,op.actual)!=null)
                    return reject(KotlinFreestyleRefusal.INVALID_SET)
                val previous=sets[op.setKey]
                if(previous==null) {
                    if(op.correctsEventId!=null)return reject(KotlinFreestyleRefusal.INVALID_CORRECTION)
                    if(sets.size>=MAX_SETS || sets.values.count{it.slotId==op.slotId}>=MAX_SETS_PER_SLOT)
                        return reject(KotlinFreestyleRefusal.LIMIT)
                } else if(previous.eventId!=op.correctsEventId || previous.slotId!=op.slotId ||
                    previous.type!=op.type)return reject(KotlinFreestyleRefusal.INVALID_CORRECTION)
                sets=Collections.unmodifiableMap(LinkedHashMap(sets).apply{put(op.setKey,op)})
            }
            is KotlinFreestyleOperation.Finish -> {
                // No unperformed planned rows to resolve. A fully empty or
                // all-skipped workout never becomes completed training.
                if(sets.values.none{it.actual!=null})
                    return reject(KotlinFreestyleRefusal.EMPTY_WORKOUT)
                finish=op.eventId
            }
        }
        val revision=prior.revision+1
        return KotlinFreestyleDecision.Proposed(prior.copy(revision=revision,
            exercises=exercises,effectiveSets=sets,finishedByEventId=finish,
            receipts=Collections.unmodifiableList(prior.receipts+KotlinFreestyleReceipt(revision,op))))
    }

    fun replay(workoutId:String,source:KotlinFreestyleSource,receipts:List<KotlinFreestyleReceipt>)
        :KotlinFreestyleWorkout? {
        if(receipts.size>KotlinWorkoutJournalDomain.MAX_RECEIPTS)return null
        var state=begin(workoutId,source)?:return null
        for(receipt in receipts) {
            val next=reduce(state,receipt.operation) as? KotlinFreestyleDecision.Proposed?:return null
            if(next.replayed || next.workout.revision!=receipt.revision)return null
            state=next.workout
        }
        return state
    }

    /** Save only reusable shape; no old facts, machine IDs, loads, RIR,
     * gym approval or fictional program targets are carried into a routine. */
    fun routine(workout:KotlinFreestyleWorkout,routineId:String,name:String):KotlinFreestyleRoutine? {
        val label=name.trim()
        if(!id.matches(routineId) || label.isEmpty() || label.length>80 ||
            label.any{it.code<32} || workout.finishedByEventId==null ||
            replay(workout.workoutId,workout.source,workout.receipts)!=workout)return null
        val slots=workout.exercises.mapNotNull{exercise->
            val types=workout.effectiveSets.values.filter{
                it.slotId==exercise.slotId && it.actual!=null
            }.map{it.type}
            if(types.isEmpty())null else KotlinFreestyleRoutineSlot(exercise.exerciseId,
                Collections.unmodifiableList(types))
        }
        return KotlinFreestyleRoutine(routineId,workout.source.athleteId,label,
            Collections.unmodifiableList(slots))
    }

    /** Single structural rule for saved/replayed routines. No gym approval
     * is inferred from a structurally valid routine. */
    fun validRoutine(routine:KotlinFreestyleRoutine):Boolean =
        id.matches(routine.routineId) && id.matches(routine.athleteId) &&
            routine.name.isNotEmpty() && routine.name.length<=80 &&
            routine.name==routine.name.trim() && routine.name.none{it.code<32} &&
            routine.slots.size in 1..MAX_EXERCISES &&
            routine.slots.all{id.matches(it.exerciseId) && it.setTypes.size in 1..MAX_SETS_PER_SLOT} &&
            routine.slots.sumOf{it.setTypes.size}<=MAX_SETS

    /** Re-select each saved exercise in today's gym. Refuse the entire
     * routine if any exercise is unavailable; never silently substitute.
     * Returned workout has no facts; saved set types are only routine shape. */
    fun startRoutine(workoutId:String,routine:KotlinFreestyleRoutine,setup:AthleteSetupReview,
        gymRevision:Long,catalog:List<CatalogMovement>):KotlinFreestyleWorkout? {
        val current=source(setup,gymRevision)?:return null
        if(routine.athleteId!=current.athleteId || !validRoutine(routine))return null
        var workout=begin(workoutId,current)?:return null
        for((index,slot) in routine.slots.withIndex()) {
            val exercise=selectedExercise(workout,setup,gymRevision,catalog,
                "slot-${index+1}",slot.exerciseId)?:return null
            val next=reduce(workout,KotlinFreestyleOperation.Add(
                "routine-add-${index+1}",workout.revision,
                exercise.copy(routineSetTypes=slot.setTypes)))
                as? KotlinFreestyleDecision.Proposed?:return null
            workout=next.workout
        }
        return workout
    }
}
