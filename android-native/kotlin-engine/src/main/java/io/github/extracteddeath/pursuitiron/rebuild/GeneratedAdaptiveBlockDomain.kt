package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections
import java.security.MessageDigest

/**
 * G2: convert one source-backed, fully prescribed generated week into an
 * independently reviewable FUTURE block of an existing training cycle.
 *
 * This is NOT a competing composer or an approval path. Inputs still need
 * a reviewed goal/phase policy, exact equipment stock, and distinct authored
 * rep/RIR/rest prescriptions. The caller must route the returned option
 * through AdaptiveCycleOptionReviewDomain and native owner-fenced acceptance.
 */
data class GeneratedAdaptiveBlockRequest(
    val currentCycle:CustomTrainingCycle,
    val targetBlockId:String,
    val optionId:String,
    val program:AthleteProgramGenerationRequest,
    val prescriptions:List<ExercisePrescriptionPolicy>
)

enum class GeneratedAdaptiveBlockStatus {
    READY_FOR_CYCLE_REVIEW,
    INVALID_SOURCE,
    PROTECTED_CUSTOM_WORK,
    PROGRAM_UNRESOLVED,
    PRESCRIPTION_UNRESOLVED,
    CYCLE_UNRESOLVED
}

/**
 * Identity continuity within a proposed future block, NOT permission to
 * reuse earlier workout history. The native coach still validates exact
 * source revisions and completed exercise receipts.
 */
enum class GeneratedAdaptiveSlotRelationship {
    UNCHANGED_EXPOSURE, CHANGED_SESSION_PURPOSE, CHANGED_EXERCISE_ROLE,
    NEW_DAY_OR_EXERCISE, AMBIGUOUS_PREVIOUS_SLOT
}
data class GeneratedAdaptiveSlotContinuity(
    val weekday:Int,
    val exerciseId:String,
    val priorSlotId:String?,
    val proposedSlotId:String,
    val relationship:GeneratedAdaptiveSlotRelationship
)

data class GeneratedAdaptiveBlockResult(
    val status:GeneratedAdaptiveBlockStatus,
    val generatedWeek:AthleteProgramGenerationReview?,
    val prescribedWeek:SessionPrescriptionDraft?,
    val proposedOption:AdaptiveCycleOption?,
    val explanation:String,
    val slotContinuity:List<GeneratedAdaptiveSlotContinuity> = emptyList()
) {
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

object GeneratedAdaptiveBlockDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    /** Athlete-owned work cannot be silently regenerated. */
    private fun CustomExerciseSlot.isProtected():Boolean =
        pinned || attachmentId!=null || supersetGroup!=null ||
            coachNote.isNotBlank() || sets.any {it.amplifier!=null}

    // A new exposure gets a deterministic, short, namespace-scoped identity.
    // Including the source revision ensures that a changed day cannot
    // accidentally inherit a retired slot from a prior approved revision.
    // IDs are labels, NOT evidence of historical performance.
    private fun freshSlotId(
        cycle:CustomTrainingCycle,blockId:String,weekday:Int,
        purpose:ComposerSessionPurpose,exercise:DraftPrescribedExercise
    ):String {
        val seed=listOf(cycle.cycleId,cycle.revision.toString(),blockId,
            weekday.toString(),purpose.name,exercise.exerciseId,
            exercise.role.name).joinToString("|")
        val suffix=MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") {
                (it.toInt() and 255).toString(16).padStart(2,'0')
            }
        return "coach_d"+weekday+"_"+suffix
    }

    fun propose(input:GeneratedAdaptiveBlockRequest):GeneratedAdaptiveBlockResult {
        fun refuse(
            status:GeneratedAdaptiveBlockStatus,
            explanation:String,
            week:AthleteProgramGenerationReview?=null,
            prescribed:SessionPrescriptionDraft?=null
        )=GeneratedAdaptiveBlockResult(status,week,prescribed,null,explanation)

        val cycle=input.currentCycle
        val source=cycle.blocks.singleOrNull {it.blockId==input.targetBlockId}
        val athlete=input.program.setup.reviewed
        if(!id.matches(input.optionId) || source==null ||
            cycle.revision>=9_007_199_254_740_990L ||
            athlete==null ||
            input.program.setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            athlete.athleteProfileId!=cycle.athleteId ||
            athlete.goal!=cycle.goal ||
            source.purpose!=CustomBlockPurpose.TRAINING ||
            input.program.phase!=source.phase)
            return refuse(GeneratedAdaptiveBlockStatus.INVALID_SOURCE,
                "The target training block, athlete, goal, phase or source revision is incompatible.")

        // Week-specific edits need a separately verified slot-key migration.
        // Until that exists, do not erase effective authored prescriptions.
        if(cycle.weekOverrides[input.targetBlockId].orEmpty().isNotEmpty())
            return refuse(GeneratedAdaptiveBlockStatus.PROTECTED_CUSTOM_WORK,
                "Week-specific custom prescriptions require a separate lossless migration.")

        val generation=CoachAthleteProgramGenerator.generate(input.program)
        val selected=generation.candidate?.catalogReview
        val original=selected?.draft
        val composed=selected?.composedRequest
        if(generation.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW ||
            composed==null || original==null ||
            original.status!=ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW)
            return refuse(GeneratedAdaptiveBlockStatus.PROGRAM_UNRESOLVED,
                "The existing generator did not produce an audited, feasible complete week.",
                generation)

        val prescribed=runCatching {
            CoachSessionPrescriptionDomain.draft(composed,input.prescriptions)
        }.getOrNull()
        if(prescribed==null ||
            prescribed.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            prescribed.composer.visits!=original.visits ||
            prescribed.sessions.size!=composed.days.size ||
            prescribed.sessions.any {it.exercises.isEmpty() ||
                it.exercises.any {e->e.sets.none {
                    set->set.type==DraftSetType.WORKING
                }} })
            return refuse(GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED,
                "The same generated week lacks complete, exact loads, sets, reps, RIR or duration.",
                generation,prescribed)

        // Exact preservation gate for pinned work, notes, attachments,
        // supersets and techniques. The generated visit must occupy the SAME
        // index, weekday, session purpose, exercise and role. If any protected
        // exposure would move or disappear, refuse the ENTIRE candidate.
        // The canonical full-cycle audit still verifies actual equipment,
        // set credits, session duration and technique loads afterward.
        val proposedByWeekday=prescribed.sessions.mapNotNull {session ->
            composed.days.getOrNull(session.dayIndex)?.let {
                it.calendarDay to session
            }
        }.toMap()
        val protectedMatches=source.days.all {day ->
            val protected=day.slots.withIndex().filter {
                it.value.isProtected()
            }
            if(protected.isEmpty())true else {
                val nextDay=proposedByWeekday[day.weekday]
                nextDay!=null && nextDay.purpose==day.purpose &&
                    protected.all { (index,slot) ->
                        day.slots.count {it.exerciseId==slot.exerciseId}==1 &&
                            nextDay.exercises.count {
                                it.exerciseId==slot.exerciseId
                            }==1 &&
                            nextDay.exercises.getOrNull(index)?.let {
                                it.exerciseId==slot.exerciseId &&
                                    it.role==slot.role
                            }==true
                    }
            }
        }
        if(!protectedMatches)
            return refuse(GeneratedAdaptiveBlockStatus.PROTECTED_CUSTOM_WORK,
                "A pinned visit, coach note, attachment, superset or advanced set would move or be changed. Preserve the exact visit or edit it explicitly.",
                generation,prescribed)

        val originalByDay=source.days.associateBy {it.weekday}
        val identities=mutableListOf<GeneratedAdaptiveSlotContinuity>()
        val days=prescribed.sessions.map {session ->
            val composerDay=composed.days.getOrNull(session.dayIndex)
                ?:return refuse(GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED,
                    "A prescribed session has no matching weekday.",generation,prescribed)
            val weekday=composerDay.calendarDay
            val prior=originalByDay[weekday]
            val slots=session.exercises.map {exercise ->
                val previous=prior?.slots.orEmpty().filter {
                    it.exerciseId==exercise.exerciseId
                }
                val matching=previous.singleOrNull()
                val relationship=when {
                    previous.size>1 ->
                        GeneratedAdaptiveSlotRelationship.AMBIGUOUS_PREVIOUS_SLOT
                    matching==null ->
                        GeneratedAdaptiveSlotRelationship.NEW_DAY_OR_EXERCISE
                    prior?.purpose!=session.purpose ->
                        GeneratedAdaptiveSlotRelationship.CHANGED_SESSION_PURPOSE
                    matching.role!=exercise.role ->
                        GeneratedAdaptiveSlotRelationship.CHANGED_EXERCISE_ROLE
                    else ->GeneratedAdaptiveSlotRelationship.UNCHANGED_EXPOSURE
                }
                // Reuse identity only for the very SAME training exposure.
                // Purpose and role changes must not impersonate old work,
                // even when the movement stays on the same calendar weekday.
                val stableId=if(relationship==
                    GeneratedAdaptiveSlotRelationship.UNCHANGED_EXPOSURE)
                    checkNotNull(matching).slotId
                    else freshSlotId(cycle,input.targetBlockId,weekday,
                        session.purpose,exercise)
                identities+=GeneratedAdaptiveSlotContinuity(
                    weekday,exercise.exerciseId,matching?.slotId,
                    stableId,relationship)
                // Keep the exact athlete-authored slot and every set in it.
                // Unprotected visits alone may receive generated prescriptions.
                if(matching?.isProtected()==true)checkNotNull(matching)
                else CustomExerciseSlot(stableId,exercise.exerciseId,exercise.role,
                    Collections.unmodifiableList(exercise.sets.map {set ->
                        CustomAuthoredSet(set.type,set.load,set.targetReps,
                            set.targetRir,set.activeSeconds,set.restSeconds)
                    }),pinned=false)
            }
            CustomTrainingDay(weekday,
                prior?.label ?: composerDay.focus.name,
                session.purpose,Collections.unmodifiableList(slots))
        }
        val proposed=cycle.copy(
            revision=cycle.revision+1,
            blocks=Collections.unmodifiableList(cycle.blocks.map {block ->
                if(block.blockId!=input.targetBlockId)block
                else block.copy(split=CustomSplitIdentity.Named(
                    input.program.setup.reviewed!!.split),
                    days=Collections.unmodifiableList(days))
            }))
        val catalog=CustomExerciseAuthoringDomain.picker(
            input.program.setup,input.program.catalog)
        val structure=CustomTrainingCycleDomain.review(proposed,
            catalog.map {it.availability})
        if(structure.verifiedDraft==null)
            return refuse(GeneratedAdaptiveBlockStatus.CYCLE_UNRESOLVED,
                "The converted complete cycle failed the existing exact inventory and slot review: "+
                    structure.issues.take(3).joinToString(";") {it.kind.name},
                generation,prescribed)
        return GeneratedAdaptiveBlockResult(
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            generation,prescribed,
            AdaptiveCycleOption(input.optionId,structure.verifiedDraft),
            "Complete candidate block; still requires full-cycle quality, permission and athlete review.",
            Collections.unmodifiableList(identities.toList()))
    }
}
