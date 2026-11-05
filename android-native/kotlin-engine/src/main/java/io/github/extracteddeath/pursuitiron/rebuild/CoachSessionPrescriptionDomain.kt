package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * A PURPOSE-SPECIFIC prescription draft built over the independently audited
 * week composer. The caller supplies all reps, effort, rest and warmup
 * decisions. Neither approval nor workout issuance happens in this module.
 */
enum class SetLoadScheme { STRAIGHT, LAST_SET_BACKOFF }
enum class DraftSetType { WARMUP, WORKING }
enum class PrescriptionDraftStatus { COMPLETE_FOR_REVIEW, UNRESOLVED }

sealed interface DraftSetLoad {
    data class Verified(val unit: LoadUnit,val basis: LoadBasis,
        val value: BigDecimal): DraftSetLoad
    data object ExplicitBodyweight: DraftSetLoad
}

data class DeclaredWarmupStep(
    val load:BigDecimal,
    val reps:Int,
    val activeSeconds:Int,
    val restSeconds:Int
)

data class ExercisePrescriptionPolicy(
    val exerciseId:String,
    val purpose:ComposerSessionPurpose,
    val workingReps:RepBand,
    /** Prescribed RIR is NEVER a logged/actual RIR observation. */
    val targetRir:Int?,
    val activeSecondsPerSet:Int,
    val restSecondsPerSet:Int,
    val scheme:SetLoadScheme,
    val maximumAuthorizedBackoff:BigDecimal?=null,
    val warmups:List<DeclaredWarmupStep> = emptyList(),
    /** Backoff targets are authored separately; never copy top-set targets. */
    val backoffReps:RepBand?=null,
    val backoffRir:Int?=null,
    /** Explicit, verified PURPOSE-specific load. Null uses original Composer load. */
    val purposeWorkingLoad:BigDecimal?=null
)

data class DraftPrescribedSet(
    val stableSetKey:String,
    val type:DraftSetType,
    val ordinal:Int,
    val load:DraftSetLoad,
    val targetReps:RepBand,
    val targetRir:Int?,
    val activeSeconds:Int,
    val restSeconds:Int
)

data class DraftPrescribedExercise(
    val exerciseId:String,
    val role:ComposerRole,
    val purpose:ComposerSessionPurpose,
    val sets:List<DraftPrescribedSet>,
    val shortReason:String
)

data class DraftPrescribedSession(
    val dayIndex:Int,
    val purpose:ComposerSessionPurpose,
    val exercises:List<DraftPrescribedExercise>,
    val estimatedMinutes:Long?
)

data class SessionPrescriptionDraft(
    val status:PrescriptionDraftStatus,
    val composer:ComposerDraft,
    val sessions:List<DraftPrescribedSession>,
    /** Null if any policy/load is unresolved: never treat partial as complete. */
    val finalAudit:WeeklyDoseFeasibilityReport?,
    val unresolvedExerciseVisits:Set<String>,
    val reviewNotes:List<String>
) {
    val canIssueWorkout:Boolean get()=false
}

object CoachSessionPrescriptionDomain {
    private val zero=BigDecimal.ZERO
    private val maxLoad=BigDecimal("100000")
    private val smallStep=BigDecimal("0.001")

    private fun canonical(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())
    private fun validLoad(v:BigDecimal)=v>=zero && v<=maxLoad &&
        v.stripTrailingZeros().scale()<=3

    private fun validatePolicies(
        policies:List<ExercisePrescriptionPolicy>,request:ComposerRequest
    ) {
        require(policies.size<=128)
        require(policies.map {it.exerciseId to it.purpose}.distinct().size==policies.size)
        val ids=request.exercises.map {it.exerciseId}.toSet()
        require(policies.all {p->
            p.exerciseId in ids &&
                (p.targetRir==null || p.targetRir in 0..10) &&
                (p.backoffRir==null || p.backoffRir in 0..10) &&
                (p.purposeWorkingLoad==null ||
                    (p.purposeWorkingLoad>zero && validLoad(p.purposeWorkingLoad))) &&
                p.activeSecondsPerSet in 1..3600 && p.restSecondsPerSet in 0..3600 &&
                p.warmups.size<=12 &&
                (p.maximumAuthorizedBackoff==null ||
                    (p.maximumAuthorizedBackoff>zero &&
                        validLoad(p.maximumAuthorizedBackoff))) &&
                (p.scheme!=SetLoadScheme.LAST_SET_BACKOFF ||
                    (p.maximumAuthorizedBackoff!=null && p.backoffReps!=null)) &&
                (p.scheme!=SetLoadScheme.STRAIGHT ||
                    (p.backoffReps==null && p.backoffRir==null &&
                        p.maximumAuthorizedBackoff==null)) &&
                p.warmups.all {w->
                    validLoad(w.load) && w.reps in 1..100 &&
                        w.activeSeconds in 1..3600 && w.restSeconds in 0..3600
                }
        })
    }

    /** Every numeric load needs exact unit/basis/exercise stock verification. */
    private fun load(option:ComposerExerciseOption,
        value:BigDecimal):DraftSetLoad.Verified? {
        val eq=option.equipment as? ComposerEquipment.Verified ?:return null
        val result=EquipmentLoadDomain.inspect(eq.inventory,value,eq.unit)
        if(result !is LoadInspection.Confirmed || !result.exact)return null
        return DraftSetLoad.Verified(eq.unit,result.basis,canonical(value))
    }

    /** Find one actual attainable lower load, not a guessed percentage. */
    private fun backoff(option:ComposerExerciseOption,
        workingValue:BigDecimal,maxDecrease:BigDecimal):DraftSetLoad.Verified? {
        val eq=option.equipment as? ComposerEquipment.Verified ?:return null
        if(workingValue<=zero)return null
        val result=EquipmentLoadDomain.inspect(eq.inventory,
            workingValue.subtract(smallStep),eq.unit)
        val lower=(result as? LoadInspection.Confirmed)?.atOrBelow ?:return null
        if(lower<=zero || lower>=workingValue ||
            workingValue.subtract(lower)>maxDecrease)return null
        return load(option,lower)
    }

    private fun plannedSets(option:ComposerExerciseOption,
        visit:ComposerVisit,p:ExercisePrescriptionPolicy):List<DraftPrescribedSet>? {
        val workingValue=when(val e=option.equipment) {
            ComposerEquipment.Bodyweight -> {
                if(p.purposeWorkingLoad!=null)return null
                null
            }
            is ComposerEquipment.Verified -> p.purposeWorkingLoad ?: e.plannedLoad
        }
        val workLoad=if(workingValue==null)
            DraftSetLoad.ExplicitBodyweight
        else load(option,workingValue) ?:return null
        if(option.equipment==ComposerEquipment.Bodyweight &&
            (p.warmups.isNotEmpty() || p.scheme==SetLoadScheme.LAST_SET_BACKOFF))
            return null
        if(p.scheme==SetLoadScheme.LAST_SET_BACKOFF && visit.sets<2)return null

        val warmups=p.warmups.mapIndexed {i,w->
            val verified=load(option,w.load) ?:return null
            if(workingValue==null || w.load>=workingValue)return null
            DraftPrescribedSet(option.exerciseId+":d"+visit.dayIndex+":w"+i,
                DraftSetType.WARMUP,i,verified,RepBand(w.reps,w.reps),null,
                w.activeSeconds,w.restSeconds)
        }
        val smaller=if(p.scheme==SetLoadScheme.LAST_SET_BACKOFF)
            backoff(option,checkNotNull(workingValue),
                checkNotNull(p.maximumAuthorizedBackoff)) ?:return null
        else null
        val working=(0 until visit.sets).map {i->
            DraftPrescribedSet(option.exerciseId+":d"+visit.dayIndex+":s"+i,
                DraftSetType.WORKING,i,
                if(i==visit.sets-1 && smaller!=null)smaller else workLoad,
                if(i==visit.sets-1 && smaller!=null)
                    checkNotNull(p.backoffReps) else p.workingReps,
                if(i==visit.sets-1 && smaller!=null)p.backoffRir else p.targetRir,
                p.activeSecondsPerSet,p.restSecondsPerSet)
        }
        return warmups+working
    }

    /** Recompute from the request; never trust externally fabricated drafts. */
    fun draft(request:ComposerRequest,
        policies:List<ExercisePrescriptionPolicy>):SessionPrescriptionDraft {
        validatePolicies(policies,request)
        val composed=CoachWeekComposer.compose(request)
        val options=request.exercises.associateBy {it.exerciseId}
        val lookup=policies.associateBy {it.exerciseId to it.purpose}
        val conflicts=linkedSetOf<String>()
        val sessions=composed.sessions.map {session->
            val exercises=session.orderedVisits.mapNotNull {visit->
                val option=checkNotNull(options[visit.exerciseId])
                val policy=lookup[visit.exerciseId to session.purpose]
                if(policy==null) {
                    conflicts+=visit.exerciseId+":d"+session.dayIndex+":missing-policy"
                    return@mapNotNull null
                }
                val sourceBand=option.sourcePracticalReps
                fun outsideSourceReps(band:RepBand?)=band!=null &&
                    sourceBand!=null &&
                    (band.minimum<sourceBand.minimum ||
                        band.maximum>sourceBand.maximum)
                if(outsideSourceReps(policy.workingReps) ||
                    outsideSourceReps(policy.backoffReps)) {
                    conflicts+=visit.exerciseId+":d"+session.dayIndex+
                        ":source-rep-envelope"
                    return@mapNotNull null
                }
                val sets=plannedSets(option,visit,policy)
                if(sets==null) {
                    conflicts+=visit.exerciseId+":d"+session.dayIndex+":unverified-scheme"
                    return@mapNotNull null
                }
                val reason=when(option.role) {
                    ComposerRole.PRIMARY ->"Primary lift first; review performance across all working sets."
                    ComposerRole.SECONDARY ->"Secondary training: follow the main movement."
                    ComposerRole.ACCESSORY ->"Targeted accessory volume; no filler sets."
                }
                DraftPrescribedExercise(option.exerciseId,option.role,
                    session.purpose,Collections.unmodifiableList(sets),reason)
            }
            DraftPrescribedSession(session.dayIndex,session.purpose,
                Collections.unmodifiableList(exercises),null)
        }
        val audit=if(conflicts.isEmpty()) {
            val exposures=sessions.flatMap {session->
                session.exercises.flatMap {exercise->
                    val credits=checkNotNull(options[exercise.exerciseId]).credits
                    val finalSetKey=session.exercises.lastOrNull()
                        ?.sets?.lastOrNull()?.stableSetKey
                    exercise.sets.map {set->
                        // The issued target remains untouched. Only the
                        // session-duration estimate excludes post-finish rest.
                        val estimatedRest=if(set.stableSetKey==finalSetKey)0
                            else set.restSeconds
                        PlannedWorkingExposure(set.stableSetKey,session.dayIndex,
                            if(set.type==DraftSetType.WARMUP)
                                ExposureSetKind.WARMUP else ExposureSetKind.WORKING,
                            if(set.type==DraftSetType.WARMUP)emptyList() else credits,
                            set.activeSeconds,estimatedRest)
                    }
                }
            }
            WeeklyDoseFeasibilityDomain.audit(composed.week.copy(exposures=exposures))
        } else null
        val timed=sessions.map {s->
            s.copy(estimatedMinutes=audit?.sessions?.firstOrNull {
                it.dayIndex==s.dayIndex
            }?.roundedMinutes)
        }
        val notes=mutableListOf<String>()
        if(conflicts.isNotEmpty())
            notes+="Missing policies or unavailable loads prevent a complete set plan."
        if(audit!=null && !audit.satisfiesDeclaredHardConstraints)
            notes+="The full prescription violates dose, frequency or a user hard time limit."
        if(audit!=null && audit.daysOutsidePreferredWindow.isNotEmpty())
            notes+="Warmups, sets and rest differ from the preferred session duration."
        if(composed.status!=ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW)
            notes+="The underlying week still has unresolved coaching constraints."
        if(notes.isEmpty())
            notes+="The full prescription is auditable for review; no targets are issued."
        val complete=conflicts.isEmpty() && audit!=null &&
            audit.satisfiesDeclaredHardConstraints &&
            audit.daysOutsidePreferredWindow.isEmpty() &&
            composed.status==ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW
        return SessionPrescriptionDraft(
            if(complete)PrescriptionDraftStatus.COMPLETE_FOR_REVIEW
            else PrescriptionDraftStatus.UNRESOLVED,
            composed,Collections.unmodifiableList(timed),
            audit,Collections.unmodifiableSet(conflicts),
            Collections.unmodifiableList(notes))
    }
}
