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
    val prescriptions:List<ExercisePrescriptionPolicy>,
    /** Athlete opted into exercise adaptation; a candidate source restore
     * remains unsigned until separate full-cycle and athlete review. */
    val allowSourceMenuRecovery:Boolean=false,
    /** The athlete must separately opt into changing working set counts.
     * Generated Strength sets may only grow within authored phase ceilings. */
    val allowSourceDoseRecovery:Boolean=false,
    /** Optional read-only replay of finished journal receipts for numerical
     * provenance. This is NOT native-storage authentication or automatic
     * permission to change the generated program. */
    val historyReadoutRequest:KotlinProgramHistoryReadoutRequest?=null
)

enum class GeneratedAdaptiveBlockStatus {
    READY_FOR_CYCLE_REVIEW,
    INVALID_SOURCE,
    PROTECTED_CUSTOM_WORK,
    PROGRAM_UNRESOLVED,
    PRESCRIPTION_UNRESOLVED,
    CYCLE_UNRESOLVED,
    STRENGTH_EXERCISE_MENU_LOSS,
    STRENGTH_WORKING_DOSE_LOSS
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
    val slotContinuity:List<GeneratedAdaptiveSlotContinuity> = emptyList(),
    /** Exact source-backed exit/entry roster warning, not a coach approval. */
    val strengthRosterBoundary:StrengthRosterBoundaryReview?=null,
    /** Working-set totals only; not generic amplifier/warmup or dose advice. */
    val strengthDoseBoundary:StrengthDoseBoundaryReview?=null,
    /** Evidence of exact, previously authored source visits restored to an
     * UNSIGNED candidate, not new athlete results or permission to issue. */
    val restoredSourceExercises:List<StrengthMenuRestoredExercise> = emptyList(),
    val addedStrengthWorkingSets:List<GeneratedStrengthDoseAddedSet> = emptyList(),
    /** Strength exit -> Peak entry measurements only; a taper remains a
     * phase-specific athlete/coach decision, never an automatic set quota. */
    val peakTaperBoundary:PeakTaperBoundaryReview?=null,
    /** Independently reconciled, immutable source-bound numerical decisions. */
    val upstreamNumberTrace:GeneratedUpstreamNumberTrace?=null
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

        // The generated week must be composed using the actual authored
        // purpose/day-specific active, rest and warmup timings, not generic
        // offer timings that can fit while the eventual Peak/Strength
        // prescription no longer fits the athlete's session range.
        //
        // An explicitly supplied timing projection must be exactly the
        // prescription projection. Do not silently overwrite a conflicting
        // saved prospective timing or forge a shorter session.
        val authoredTimings=CoachSessionPrescriptionDomain.timingsForPolicies(
            input.prescriptions)
        if(input.prescriptions.size>128 ||
            input.prescriptions.map {
                Triple(it.exerciseId,it.purpose,it.dayIndex)
            }.distinct().size!=input.prescriptions.size ||
            (input.program.visitTimings.isNotEmpty() &&
                input.program.visitTimings.toSet()!=authoredTimings.toSet()))
            return refuse(GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED,
                "The selected program timing and the exact authored purpose-specific prescriptions disagree or duplicate an exercise visit.")
        val timedProgram=input.program.copy(visitTimings=authoredTimings)
        val generation=CoachAthleteProgramGenerator.generate(timedProgram)
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
        // Refuse an 8 -> 3 Strength-day collapse unless the athlete has
        // explicitly allowed exercise adaptation and every lost source visit
        // is recoverable without inventing a new load, set, movement or gym.
        // The original source is the final RESOLVED Hypertrophy week, even
        // across recovery and performance-test bridge blocks.
        var reviewedStructure=structure
        var restoredSourceExercises:List<StrengthMenuRestoredExercise> =
            emptyList()
        val initialRoster=StrengthRosterBoundaryGuardDomain.review(
            structure,input.targetBlockId)
        if(input.allowSourceMenuRecovery &&
            input.program.selectFromOfferedCatalog &&
            source.phase==TrainingPhase.STRENGTH &&
            initialRoster.status==
                StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW) {
            val exit=initialRoster.sourceExitWeek?.let {
                CustomTrainingCycleDomain.week(structure,it)
            }
            val sourceDays=exit?.days?.associateBy{it.weekday}.orEmpty()
            val targetBlock=proposed.blocks.single{it.blockId==input.targetBlockId}
            val specs=input.program.catalog.associateBy{it.exerciseId}
            val restored=mutableListOf<StrengthMenuRestoredExercise>()
            val newDays=targetBlock.days.map{day ->
                val missing=initialRoster.affectedDays
                    .singleOrNull{it.weekday==day.weekday}
                    ?.missingExerciseIds.orEmpty()
                val room=(input.program.maximumExercisesPerDay-day.slots.size)
                    .coerceAtLeast(0)
                val usedIds=day.slots.map{it.slotId}.toMutableSet()
                val additions=sourceDays[day.weekday]?.slots.orEmpty()
                    .filter{old ->
                        val spec=specs[old.exerciseId]
                        old.exerciseId in missing &&
                            day.slots.none{it.exerciseId==old.exerciseId} &&
                            old.role in setOf(ComposerRole.SECONDARY,
                                ComposerRole.ACCESSORY) &&
                            spec!=null && spec.role==old.role &&
                            spec.creditStatus==
                                CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL &&
                            !spec.creditReviewReference.isNullOrBlank() &&
                            !old.pinned && old.coachNote.isBlank() &&
                            old.supersetGroup==null &&
                            old.machineInstanceId==null &&
                            old.sets.isNotEmpty() &&
                            old.sets.all{set->
                                set.type==DraftSetType.WORKING &&
                                    set.amplifier==null}
                    }.distinctBy{it.exerciseId}.take(room)
                    .map{old ->
                        val seed=listOf(proposed.cycleId,
                            proposed.revision.toString(),targetBlock.blockId,
                            day.weekday.toString(),old.slotId,
                            old.exerciseId).joinToString("|")
                        val suffix=MessageDigest.getInstance("SHA-256")
                            .digest(seed.toByteArray(Charsets.UTF_8))
                            .take(12).joinToString("") {
                                (it.toInt() and 255).toString(16).padStart(2,'0')
                            }
                        var id="coach_source_d"+day.weekday+"_"+suffix
                        var ordinal=1
                        while(id in usedIds) {
                            id="coach_source_d"+day.weekday+"_"+suffix+
                                "_"+ordinal++
                        }
                        usedIds+=id
                        restored+=StrengthMenuRestoredExercise(day.weekday,
                            old.slotId,id,old.exerciseId,old.sets.size)
                        old.copy(slotId=id,pinned=false)
                    }
                day.copy(slots=Collections.unmodifiableList(day.slots+additions))
            }
            // Partial restoration cannot conceal an 8 -> 3 defect by
            // raising it to five: require EVERY lost same-day source ID.
            val complete=initialRoster.affectedDays.all{loss ->
                restored.filter{it.weekday==loss.weekday}
                    .map{it.exerciseId}.toSet()==loss.missingExerciseIds
            }
            if(complete && restored.isNotEmpty()) {
                val recovered=proposed.copy(blocks=proposed.blocks.map{block ->
                    if(block.blockId==targetBlock.blockId)
                        block.copy(days=Collections.unmodifiableList(newDays))
                    else block
                })
                val inspected=CustomTrainingCycleDomain.review(recovered,
                    catalog.map{it.availability})
                // A restored exercise roster can still carry a real
                // half-dose deficit. If the athlete separately enabled
                // Adapt Sets, stage that verified but UNSIGNED interim
                // roster so the ordinary dose recovery can evaluate it.
                // Never accept unverified or incomparable dose sources.
                // Without both permissions, retain the original refusal.
                val restoredRoster=StrengthRosterBoundaryGuardDomain.review(
                    inspected,input.targetBlockId)
                val restoredDose=StrengthDoseBoundaryGuardDomain.review(
                    inspected,input.targetBlockId)
                val mayStageDose=restoredDose.status==
                    StrengthDoseBoundaryStatus.CONTINUOUS ||
                    (input.allowSourceDoseRecovery && restoredDose.status==
                        StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW)
                if(inspected.verifiedDraft!=null &&
                    restoredRoster.status==StrengthRosterBoundaryStatus.CONTINUOUS &&
                    mayStageDose) {
                    reviewedStructure=inspected
                    restoredSourceExercises=Collections.unmodifiableList(
                        restored.toList())
                }
            }
        }
        val roster=StrengthRosterBoundaryGuardDomain.review(
            reviewedStructure,input.targetBlockId)
        var dose=StrengthDoseBoundaryGuardDomain.review(
            reviewedStructure,input.targetBlockId)
        var addedStrengthSets:List<GeneratedStrengthDoseAddedSet> =
            emptyList()
        if(input.allowSourceDoseRecovery &&
            source.phase==TrainingPhase.STRENGTH &&
            roster.status==StrengthRosterBoundaryStatus.CONTINUOUS &&
            dose.status==
                StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW) {
            val reviewedAthlete=checkNotNull(input.program.setup.reviewed)
            val policy=checkNotNull(input.program.policy)
            val limit=listOfNotNull(reviewedAthlete.preferredMaximumMinutes,
                reviewedAthlete.explicitHardMaximumMinutes).minOrNull()
            val trial=GeneratedStrengthDoseRecoveryDomain.preview(
                reviewedStructure,input.targetBlockId,input.program.choices,
                input.program.catalog,policy.selectedBounds,
                catalog.map{it.availability},
                input.program.nonSetSecondsByWeekday,limit,
                restoredSourceExercises.map{it.proposedSlotId}.toSet())
            if(trial.structure?.verifiedDraft!=null) {
                reviewedStructure=trial.structure
                dose=StrengthDoseBoundaryGuardDomain.review(
                    reviewedStructure,input.targetBlockId)
                addedStrengthSets=trial.added
            }
        }
        val halfDose=dose.status==
            StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW
        val doseDetail=if(dose.affectedDays.isNotEmpty())
            "day-specific working-set losses: "+
                dose.affectedDays.joinToString("; ") {
                    "day ${it.weekday}: ${it.beforeWorkingSets} to ${it.afterWorkingSets}"
                }+" (weekly totals ${dose.hypertrophyWorkingSets} to "+
                "${dose.strengthWorkingSets})"
        else "weekly working sets ${dose.hypertrophyWorkingSets} to "+
            "${dose.strengthWorkingSets}"
        if(roster.status==
            StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW)
            return GeneratedAdaptiveBlockResult(
                GeneratedAdaptiveBlockStatus.STRENGTH_EXERCISE_MENU_LOSS,
                generation,prescribed,null,
                "Generated Strength loses most working exercises on "+
                    roster.affectedDays.joinToString("; ") {
                        "day ${it.weekday}: ${it.beforeWorkingExercises} to "+
                            "${it.afterWorkingExercises}"
                    }+
                    (if(halfDose)"; $doseDetail." else ".")+
                    " Review missing movements and the full-cycle dose, "+
                    "recovery and equipment constraints; do not auto-remove them.",
                strengthRosterBoundary=roster,
                strengthDoseBoundary=dose)
        if(halfDose)
            return GeneratedAdaptiveBlockResult(
                GeneratedAdaptiveBlockStatus.STRENGTH_WORKING_DOSE_LOSS,
                generation,prescribed,null,
                "Generated Strength retains the exercise menu but has "+
                    "$doseDetail. A source-backed full-cycle dose repair "+
                    "and individual review are required before replacing this block.",
                strengthRosterBoundary=roster,
                strengthDoseBoundary=dose)
        // The saved Strength menu may include source-backed accessories
        // restored after generation. They are part of the proposed cycle,
        // so every restored visit needs the same one-to-one provenance as
        // generated visits. Its new Strength slot ID must never masquerade
        // as an old Hypertrophy workout/history identity.
        val fullContinuity=identities+restoredSourceExercises.map { restored ->
            GeneratedAdaptiveSlotContinuity(
                restored.weekday,restored.exerciseId,restored.sourceSlotId,
                restored.proposedSlotId,
                GeneratedAdaptiveSlotRelationship.NEW_DAY_OR_EXERCISE)
        }
        val accepted=checkNotNull(reviewedStructure.verifiedDraft)
        val expectedSlots=accepted.blocks.single {
            it.blockId==input.targetBlockId
        }.days.flatMap { day ->
            day.slots.map { day.weekday to it.slotId }
        }
        val reportedSlots=fullContinuity.map {
            it.weekday to it.proposedSlotId
        }
        if(reportedSlots.size!=expectedSlots.size ||
            reportedSlots.toSet()!=expectedSlots.toSet())
            return refuse(GeneratedAdaptiveBlockStatus.CYCLE_UNRESOLVED,
                "The proposed block has incomplete slot continuity evidence.",
                generation,prescribed)
        val numbers=GeneratedUpstreamNumberTraceDomain.review(
            input,reviewedStructure,generation,prescribed,fullContinuity,
            restoredSourceExercises,addedStrengthSets)
            ?:return refuse(GeneratedAdaptiveBlockStatus.CYCLE_UNRESOLVED,
                "A generated set, rep band, authored source or phase dose has no reproducible numerical lineage.",
                generation,prescribed)
        return GeneratedAdaptiveBlockResult(
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            generation,prescribed,
            AdaptiveCycleOption(input.optionId,reviewedStructure.verifiedDraft!!),
            "Complete generated candidate; recovered ${restoredSourceExercises.size} source exercise visits and ${addedStrengthSets.size} verified Strength working sets. Full-cycle dose, time, fatigue, stock, permissions and athlete review remain mandatory.",
            Collections.unmodifiableList(fullContinuity),
            roster,dose,restoredSourceExercises,addedStrengthSets,
            if(source.phase==TrainingPhase.PEAK)
                PeakTaperBoundaryDomain.review(
                    reviewedStructure,input.targetBlockId)
            else null,numbers)
    }
}
