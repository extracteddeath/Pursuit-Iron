package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Convert a FULLY audited first-program prescription to the EXISTING custom
 * training-cycle draft format. It does not create another program owner and
 * it does not approve or issue workouts. The native host must re-review the
 * current athlete/gym source, verify a shown preview and perform a fenced CAS
 * save after explicit athlete confirmation.
 */
enum class FirstProgramDraftStatus {
    READY_FOR_SAVING_DRAFT, INCOMPLETE_SOURCE, UNRESOLVED_PRESCRIPTION,
    CYCLE_REVIEW_FAILED
}
data class FirstProgramDraftReview(
    val status:FirstProgramDraftStatus,
    val cycle:CustomTrainingCycle?,
    val reason:String,
    /** Complete reviewed per-slot baseline/specialist progression assignments.
     * Not persisted or activated until a separate native owner handoff. */
    val progressionPlan:ProgramProgressionPlan.Ready?=null,
    /** Full-cycle audited optional-finisher estimates keyed by the original
     * generated day index. Empty when no optional full-cycle review ran. */
    val auditedMinutesByDayIndex:Map<Int,Long> = emptyMap()
) {
    val mayIssueWorkout:Boolean get()=false
    val mayApproveProgram:Boolean get()=false
}
object CoachFirstProgramDraftConversionDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    /**
     * Review inputs from the EXACT generated policy and already audited
     * per-weekday non-set time. This is not a newly inferred coach threshold.
     * A custom-program edit invalidates this handoff; it must be re-audited.
     */
    fun policyForInitialFullCycleReview(
        reviewed:FirstProgramStartReview,
        setup:AthleteSetupReview
    ):CustomCoachAuditInputs? {
        val athlete=setup.reviewed ?:return null
        val generated=reviewed.generation ?:return null
        val policy=reviewed.policy ?:return null
        val draft=reviewed.prescription ?:return null
        val request=generated.candidate?.catalogReview?.composedRequest
            ?:return null
        if(!setup.mayReviewProgramCandidate ||
            reviewed.status!=FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW ||
            generated.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW ||
            draft.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            draft.composer.visits!=generated.candidate?.catalogReview?.draft?.visits ||
            policy.athleteId!=athlete.athleteProfileId ||
            policy.athleteRevision!=athlete.profileRevision ||
            policy.goal!=athlete.goal ||
            policy.experience!=athlete.experience ||
            policy.split!=athlete.split ||
            policy.trainingDays!=athlete.trainingWeekdays.size ||
            request.days.map{it.calendarDay}.toSet()!=athlete.trainingWeekdays)
            return null
        val time=request.days.associate{day->
            day.calendarDay to day.window.declaredNonSetSeconds
        }
        if(time.size!=athlete.trainingWeekdays.size)
            return null
        return CustomCoachAuditInputs(
            policiesByPhase=mapOf(policy.phase to policy),
            nonSetSecondsByWeekday=Collections.unmodifiableMap(time))
    }

    /** Present the EXISTING full-cycle audit, never independently add mini-set
     * or rest estimates in Compose. A missing audited day is not a zero. */
    internal fun auditedFirstWeekMinutes(
        audit:CustomCoachAudit,dayIndexToWeekday:Map<Int,Int>
    ):Map<Int,Long>? {
        if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
            audit.issues.isNotEmpty() || dayIndexToWeekday.isEmpty() ||
            dayIndexToWeekday.keys.any{it<0} ||
            dayIndexToWeekday.values.toSet().size!=dayIndexToWeekday.size)return null
        val week=audit.weeks.singleOrNull{it.absoluteWeek==1} ?:return null
        if(week.estimatedSecondsByDay.keys!=dayIndexToWeekday.values.toSet())return null
        val result=linkedMapOf<Int,Long>()
        for((index,weekday) in dayIndexToWeekday) {
            val seconds=week.estimatedSecondsByDay[weekday] ?:return null
            if(seconds !in 1..10_000_000L)return null
            result[index]=(seconds+59L)/60L
        }
        return Collections.unmodifiableMap(result)
    }

    fun propose(
        reviewed:FirstProgramStartReview,
        setup:AthleteSetupReview,
        curatedCatalog:List<CatalogMovement>,
        cycleId:String,
        title:String,
        firstBlockWeeks:Int,
        includeMyoFinisher:Boolean=false
    ):FirstProgramDraftReview {
        fun reject(status:FirstProgramDraftStatus,message:String)=
            FirstProgramDraftReview(status,null,message)
        val athlete=setup.reviewed
        val generated=reviewed.generation
        val prescribed=reviewed.prescription
        val composed=generated?.candidate?.catalogReview?.composedRequest
        val selected=generated?.candidate?.catalogReview?.draft
        val policy=reviewed.policy
        if(athlete==null || !setup.mayReviewProgramCandidate ||
            !id.matches(cycleId) || title.isBlank() || title.length>120 ||
            firstBlockWeeks !in 1..52 || policy==null ||
            policy.athleteId!=athlete.athleteProfileId ||
            policy.athleteRevision!=athlete.profileRevision ||
            policy.goal!=athlete.goal || policy.split!=athlete.split)
            return reject(FirstProgramDraftStatus.INCOMPLETE_SOURCE,
                "The verified athlete, program identity, or starting policy is missing.")
        if(reviewed.status!=FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW ||
            generated==null ||
            generated.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW ||
            prescribed==null || composed==null || selected==null ||
            prescribed.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            prescribed.composer.visits!=selected.visits ||
            prescribed.sessions.size!=athlete.trainingWeekdays.size ||
            prescribed.finalAudit?.satisfiesDeclaredHardConstraints!=true ||
            prescribed.finalAudit?.daysOutsidePreferredWindow?.isNotEmpty()!=false ||
            prescribed.sessions.any {it.exercises.isEmpty() ||
                it.exercises.any {e->e.sets.none {s->
                    s.type==DraftSetType.WORKING
                }} })
            return reject(FirstProgramDraftStatus.UNRESOLVED_PRESCRIPTION,
                "The original generated exercise, muscle-dose, time or set review is incomplete.")
        val finisher=if(includeMyoFinisher)
            CoachFirstProgramMyoFinisherDomain.recommend(
                prescribed.sessions,curatedCatalog,policy.phase,athlete.experience)
                ?:return reject(FirstProgramDraftStatus.UNRESOLVED_PRESCRIPTION,
                    "No reviewed stable isolation accessory with 12–20 reps and suitable effort is available for optional myo-reps.")
        else null
        // A source-authored required PRIMARY visit may be a companion
        // skill sharing a session with the named main lift (e.g. ULPPL
        // squat + conventional deadlift). It must survive conversion into
        // four-month custom/adaptive blocks as an athlete-protected slot,
        // not become removable just because it isn't the day header.
        val hardPinnedPrimaryIds=composed.exercises.filter {
            it.role==ComposerRole.PRIMARY && it.requiredVisits>0
        }.map{it.exerciseId}.toSet()
        val days=prescribed.sessions.map {session->
            val day=composed.days.getOrNull(session.dayIndex)
                ?:return reject(FirstProgramDraftStatus.UNRESOLVED_PRESCRIPTION,
                    "A workout day is missing from the generated calendar.")
            CustomTrainingDay(
                day.calendarDay,
                day.focus.name.replace('_',' ').lowercase()
                    .replaceFirstChar{it.uppercase()},
                session.purpose,
                Collections.unmodifiableList(
                    session.exercises.mapIndexed {index,exercise ->
                        CustomExerciseSlot(
                            "first_${day.calendarDay}_${index}",
                            exercise.exerciseId,exercise.role,
                            Collections.unmodifiableList(exercise.sets.mapIndexed {setIndex,set ->
                                CustomAuthoredSet(
                                    set.type,set.load,set.targetReps,
                                    set.targetRir,set.activeSeconds,
                                    set.restSeconds,
                                    amplifier=if(finisher?.dayIndex==session.dayIndex &&
                                        finisher.exerciseIndex==index &&
                                        finisher.exerciseId==exercise.exerciseId &&
                                        finisher.setIndex==setIndex &&
                                        finisher.setKey==set.stableSetKey)
                                        CoachFirstProgramMyoFinisherDomain.prescribedTechnique()
                                    else null)
                            }),
                            pinned=exercise.role==ComposerRole.PRIMARY &&
                                (exercise.exerciseId in hardPinnedPrimaryIds ||
                                    generated.intents.any { intent ->
                                        intent.weekday==day.calendarDay &&
                                            intent.requiredPrimaryExerciseId==exercise.exerciseId
                                    }))
                    }))
        }
        val next=CustomTrainingCycle(
            cycleId,1,athlete.athleteProfileId,athlete.goal,title,
            listOf(CustomTrainingBlock(
                "first-block","Starting block",policy.phase,
                CustomBlockPurpose.TRAINING,firstBlockWeeks,
                CustomSplitIdentity.Named(athlete.split),
                Collections.unmodifiableList(days))),
            authorNote="Provisional ${policy.policyId} plan. Review training responses and actual fatigue before advancing."
        )
        val available=CustomExerciseAuthoringDomain.picker(setup,curatedCatalog)
            .map{it.availability}
        val complete=runCatching {
            CustomTrainingCycleDomain.review(next,available)
        }.getOrNull()
        if(complete?.verifiedDraft==null)
            return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                "The generated week did not pass the independent native custom-cycle inventory/structure review.")
        val auditedMinutes=if(includeMyoFinisher) {
            val inputs=policyForInitialFullCycleReview(reviewed,setup)
                ?:return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                    "The original first-program coaching policy is unavailable.")
            val audit=runCatching {
                CustomCycleCoachAuditDomain.review(next,setup,curatedCatalog,inputs)
            }.getOrNull()
            if(audit?.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                audit.issues.isNotEmpty())
                return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                    "Optional myo-reps exceed the current full-cycle coaching, fatigue or session-time review; use straight sets instead.")
            auditedFirstWeekMinutes(audit,prescribed.sessions.associate {
                it.dayIndex to composed.days[it.dayIndex].calendarDay
            }) ?:return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                "The optional finisher's complete reviewed day durations are unavailable.")
        } else emptyMap()
        // A generator must account for the progression route of EVERY saved
        // slot. Do this only after the fully-authored cycle passed inventory
        // and source review; never guess a weight from an unreviewed draft.
        val progressionPlan=BasicProgramProgressionPlanDomain.review(
            next,reviewedCatalog=curatedCatalog.associateBy{it.exerciseId},
            experience=athlete.experience)
            as? ProgramProgressionPlan.Ready
            ?:return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                "A complete source-bound progression plan could not be assigned.")
        if(progressionPlan.assignments.size !=
            next.blocks.sumOf{block->block.days.sumOf{day->day.slots.size}})
            return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                "A generated exercise was left without an explicit progression route.")

        // Freeze the selected *method* in the very same cycle DTO saved by
        // the authenticated native draft store. A transient preview-only
        // map would disappear after cold launch, letting Auto silently pick
        // a different progression. Special/peak/periodized visits remain
        // null: these are authored source schemes, not basic overload.
        // Historical v1–v11 generated programs are frozen source receipts.
        // Their authored custom-cycle bytes and approval fingerprints must
        // remain identical on cold replay. Only explicitly versioned v12+
        // generation persists methods into its actual saved slot records.
        val persistChoices=policy.policyId==
            CoachFirstProgramStartDomain.POLICY_VERSION
        val selectedByKey=progressionPlan.assignments.associateBy{it.key}
        val persistedBlocks=next.blocks.map{block->
            val savedDays=block.days.map{day->
                val savedSlots=day.slots.map{slot->
                    val key=ProgramProgressionSlotKey(
                        block.blockId,day.weekday,slot.slotId)
                    val decision=selectedByKey[key]
                        ?:return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                            "A generated slot has no source-linked progression decision.")
                    if(persistChoices) {
                        val selectedMethod=BasicProgramProgressionPlanDomain.savedChoice(decision)
                        // Newly generated source AUTHORIZES the starting rung
                        // explicitly. The rep floor is a conservative, reviewed
                        // initial prescription chosen at program construction,
                        // never an inferred historical target on cold replay.
                        val initializesReps=selectedMethod in setOf(
                            SavedProgressionMethod.REP_TARGETS,
                            SavedProgressionMethod.LADDER,
                            SavedProgressionMethod.BODYWEIGHT_REPS)
                        slot.copy(progressionMethod=selectedMethod,
                            sets=if(initializesReps)Collections.unmodifiableList(
                                slot.sets.map{set->
                                    if(set.type==DraftSetType.WORKING && set.exactRepTarget==null)
                                        set.copy(exactRepTarget=set.reps.minimum)
                                    else set
                                }) else slot.sets)
                    } else slot
                }
                day.copy(slots=Collections.unmodifiableList(savedSlots))
            }
            block.copy(days=Collections.unmodifiableList(savedDays))
        }
        val persisted=next.copy(blocks=Collections.unmodifiableList(persistedBlocks))
        // Re-review the identical cycle structure AFTER freezing choices,
        // using the same equipment and catalog sources. This refuses a
        // selection that could not be replayed on app restart.
        val persistedReview=runCatching{
            CustomTrainingCycleDomain.review(persisted,available)
        }.getOrNull()
        val replay=BasicProgramProgressionPlanDomain.review(
            persisted,reviewedCatalog=curatedCatalog.associateBy{it.exerciseId},
            experience=athlete.experience) as? ProgramProgressionPlan.Ready
        if(persistedReview?.verifiedDraft==null || replay==null ||
            replay.assignments.size!=progressionPlan.assignments.size ||
            replay.assignments.zip(progressionPlan.assignments).any{(actual,planned)->
                actual.key!=planned.key ||
                    actual.route!=planned.route ||
                    actual.basicStrategy!=planned.basicStrategy ||
                    (persistChoices && actual.savedMethod != BasicProgramProgressionPlanDomain.savedChoice(planned))
            })
            return reject(FirstProgramDraftStatus.CYCLE_REVIEW_FAILED,
                "Saved progression choices could not reproduce the original reviewed program.")
        return FirstProgramDraftReview(
            FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,
            persisted,
            if(includeMyoFinisher)
                "Optional myo-reps on one reviewed accessory have passed the full-cycle audit. Explicit program approval and workout issuance are still required."
            else "Reviewed native draft; still requires full-cycle coaching audit, athlete consent and separate workout approval.",
            replay,auditedMinutes)
    }
}
