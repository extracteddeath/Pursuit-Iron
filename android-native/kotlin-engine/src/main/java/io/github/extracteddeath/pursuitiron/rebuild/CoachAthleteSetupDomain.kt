package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.ZoneId
import java.util.Collections

/**
 * Athlete and gym input gate, not a program generator or persistence owner.
 *
 * Null means not confirmed. False, zero and an explicit empty preference have
 * different meanings. Missing fields are returned to the native UI as
 * actionable issues; no athlete attributes, loading stock, schedule, or
 * implicit time ceiling are invented by this headless domain.
 */
enum class AthleteTrainingGoal { HYPERTROPHY, STRENGTH, POWERBUILDING }
enum class AthleteExperience { BEGINNER, INTERMEDIATE, ADVANCED }
enum class SessionTimeBucket(val minimumMinutes:Int,val preferredMaximumMinutes:Int?) {
    FORTY_TO_SIXTY(40,60),
    SIXTY_TO_NINETY(60,90),
    TWO_HOURS_PLUS(120,null)
}
sealed interface HardSessionLimit {
    data object NO_HARD_LIMIT:HardSessionLimit
    data class Minutes(val maximum:Int):HardSessionLimit
}
data class GymProfileDraft(
    val gymId:String?=null,
    val units:LoadUnit?=null,
    /** Exact stock by stable implement ID; zero entries is allowed for a bodyweight-only gym. */
    val confirmedStock:Map<String,VerifiedLoadStock>?=null,
    val availableFacilities:Set<String>?=null,
    val availableAttachments:Set<String>?=null
)
data class AthleteSetupDraft(
    val profileRevision:String?=null,
    val athleteProfileId:String?=null,
    val goal:AthleteTrainingGoal?=null,
    val experience:AthleteExperience?=null,
    val split:ComposerSplit?=null,
    /** Monday=0 through Sunday=6, as in CoachWeekComposer calendar days. */
    val trainingWeekdays:Set<Int>?=null,
    val timezoneId:String?=null,
    val sessionTimeBucket:SessionTimeBucket?=null,
    /** Explicit NO_HARD_LIMIT is different from unknown (null). */
    val hardSessionLimit:HardSessionLimit?=null,
    val gym:GymProfileDraft?=null,
    val allowBodyweight:Boolean?=null,
    val allowSupersets:Boolean?=null,
    val allowTricepsOnLower:Boolean?=null,
    /** Zero means the athlete explicitly permits no barbell exercises. */
    val maxBarbellExercisesPerDay:Int?=null,
    val focusedMuscles:Set<TrainingMuscle>?=null,
    val reducedMuscles:Set<TrainingMuscle>?=null,
    val excludedExerciseIds:Set<String>?=null,
    val lockedTrainingDays:Set<Int>?=null,
    val hardTrainingDays:Set<Int>?=null,
    val longTrainingDays:Set<Int>?=null,
    val declaredRestDays:Set<Int>?=null
)
enum class AthleteSetupIssueKind { MISSING, INVALID, CONFLICT, INVENTORY_INVALID }
data class AthleteSetupIssue(
    val field:String,
    val kind:AthleteSetupIssueKind,
    val explanation:String
)
enum class AthleteSetupStatus { NEEDS_INPUT, CONSTRAINT_CONFLICT, READY_FOR_PROGRAM_REVIEW }
/** Source-safe, unmodifiable snapshot. It grants no write or issuance permission. */
data class ReviewedGymProfile(
    val gymId:String,
    val unit:LoadUnit,
    val confirmedStock:Map<String,VerifiedLoadStock>,
    val availableFacilities:Set<String>,
    val availableAttachments:Set<String>
)
data class ReviewedAthleteSetup(
    val profileRevision:String,
    val athleteProfileId:String,
    val goal:AthleteTrainingGoal,
    val experience:AthleteExperience,
    val split:ComposerSplit,
    val trainingWeekdays:Set<Int>,
    val timezoneId:String,
    val sessionTimeBucket:SessionTimeBucket,
    val hardSessionLimit:HardSessionLimit,
    val gym:ReviewedGymProfile,
    val allowBodyweight:Boolean,
    val allowSupersets:Boolean,
    val allowTricepsOnLower:Boolean,
    val maxBarbellExercisesPerDay:Int,
    val focusedMuscles:Set<TrainingMuscle>,
    val reducedMuscles:Set<TrainingMuscle>,
    val excludedExerciseIds:Set<String>,
    val lockedTrainingDays:Set<Int>,
    val hardTrainingDays:Set<Int>,
    val longTrainingDays:Set<Int>,
    val declaredRestDays:Set<Int>
) {
    val preferredMinimumMinutes:Int get()=sessionTimeBucket.minimumMinutes
    val preferredMaximumMinutes:Int? get()=sessionTimeBucket.preferredMaximumMinutes
    val explicitHardMaximumMinutes:Int? get()=
        (hardSessionLimit as? HardSessionLimit.Minutes)?.maximum
}
data class AthleteSetupReview(
    val status:AthleteSetupStatus,
    val issues:List<AthleteSetupIssue>,
    val reviewed:ReviewedAthleteSetup?
) {
    /** Suitable as *input* to independent candidate composition only. */
    val mayReviewProgramCandidate:Boolean get()=
        status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW && reviewed!=null
    val mayIssueWorkout:Boolean get()=false
    val maySaveProfile:Boolean get()=false
}

object CoachAthleteSetupDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
    private val zero=BigDecimal.ZERO
    private fun <T> frozenSet(s:Set<T>):Set<T> =
        Collections.unmodifiableSet(LinkedHashSet(s))
    private fun <K,V> frozenMap(m:Map<K,V>):Map<K,V> =
        Collections.unmodifiableMap(LinkedHashMap(m))
    private fun normalized(x:BigDecimal)=BigDecimal(x.stripTrailingZeros().toPlainString())

    /** Revalidate and freeze load provenance; caller can mutate its input afterward. */
    private fun snapshot(stock:VerifiedLoadStock):VerifiedLoadStock=when(stock) {
        is VerifiedLoadStock.Totals ->
            stock.copy(loads=Collections.unmodifiableList(stock.loads.map(::normalized)))
        is VerifiedLoadStock.Stack ->
            stock.copy(minimum=normalized(stock.minimum),
                increment=normalized(stock.increment),maximum=normalized(stock.maximum))
        is VerifiedLoadStock.Barbell ->
            stock.copy(barWeight=normalized(stock.barWeight),
                pairedPlates=frozenMap(stock.pairedPlates.mapKeys {normalized(it.key)}))
    }

    fun review(draft:AthleteSetupDraft):AthleteSetupReview {
        val issues=mutableListOf<AthleteSetupIssue>()
        fun problem(field:String,kind:AthleteSetupIssueKind,explanation:String) {
            issues+=AthleteSetupIssue(field,kind,explanation)
        }
        fun missing(field:String)=problem(field,AthleteSetupIssueKind.MISSING,
            "Confirm this setting before reviewing a training program.")
        fun checkId(field:String,value:String?) {
            if(value==null)missing(field)
            else if(!id.matches(value))
                problem(field,AthleteSetupIssueKind.INVALID,
                    "Use a stable, nonempty identifier without whitespace.")
        }
        checkId("profileRevision",draft.profileRevision)
        checkId("athleteProfileId",draft.athleteProfileId)
        if(draft.goal==null)missing("goal")
        if(draft.experience==null)missing("experience")
        if(draft.split==null)missing("split")
        if(draft.sessionTimeBucket==null)missing("sessionTimeBucket")
        if(draft.hardSessionLimit==null)missing("hardSessionLimit")
        if(draft.allowBodyweight==null)missing("allowBodyweight")
        if(draft.allowSupersets==null)missing("allowSupersets")
        if(draft.allowTricepsOnLower==null)missing("allowTricepsOnLower")
        if(draft.maxBarbellExercisesPerDay==null)missing("maxBarbellExercisesPerDay")
        else if(draft.maxBarbellExercisesPerDay !in 0..12)
            problem("maxBarbellExercisesPerDay",AthleteSetupIssueKind.INVALID,
                "The barbell limit must be a confirmed count between zero and twelve.")
        val hardMinutes=(draft.hardSessionLimit as? HardSessionLimit.Minutes)?.maximum
        if(hardMinutes!=null && hardMinutes !in 1..360)
            problem("hardSessionLimit",AthleteSetupIssueKind.INVALID,
                "The explicit hard limit must be between one and 360 minutes.")
        if(hardMinutes!=null && draft.sessionTimeBucket!=null &&
            hardMinutes<draft.sessionTimeBucket.minimumMinutes)
            problem("hardSessionLimit",AthleteSetupIssueKind.CONFLICT,
                "The hard stop is shorter than the stated preferred session minimum.")
        if(draft.timezoneId==null)missing("timezoneId")
        else if(runCatching {ZoneId.of(draft.timezoneId)}.isFailure)
            problem("timezoneId",AthleteSetupIssueKind.INVALID,
                "Choose a real IANA time zone; calendar days must not be guessed.")

        val days=draft.trainingWeekdays
        if(days==null)missing("trainingWeekdays")
        else if(days.size !in 3..6 || days.any {it !in 0..6})
            problem("trainingWeekdays",AthleteSetupIssueKind.INVALID,
                "Choose three to six distinct Monday-to-Sunday training days.")
        if(days!=null && draft.split==ComposerSplit.ULPPL && days.size !in 5..6)
            problem("split",AthleteSetupIssueKind.CONFLICT,
                "Upper/Lower/Push/Pull/Legs requires five or six training days.")
        fun checkDays(field:String,value:Set<Int>?) {
            if(value==null)missing(field)
            else if(value.any {it !in 0..6})
                problem(field,AthleteSetupIssueKind.INVALID,
                    "Weekday values must use Monday=0 through Sunday=6.")
        }
        checkDays("lockedTrainingDays",draft.lockedTrainingDays)
        checkDays("hardTrainingDays",draft.hardTrainingDays)
        checkDays("longTrainingDays",draft.longTrainingDays)
        checkDays("declaredRestDays",draft.declaredRestDays)
        if(days!=null) {
            for((field,tags) in listOf(
                "lockedTrainingDays" to draft.lockedTrainingDays,
                "hardTrainingDays" to draft.hardTrainingDays,
                "longTrainingDays" to draft.longTrainingDays)) {
                if(tags!=null && !days.containsAll(tags))
                    problem(field,AthleteSetupIssueKind.CONFLICT,
                        "Training-day preferences must belong to selected training days.")
            }
            if(draft.declaredRestDays!=null &&
                days.any {it in draft.declaredRestDays})
                problem("declaredRestDays",AthleteSetupIssueKind.CONFLICT,
                    "A declared rest day cannot also be a training day.")
        }
        if(draft.hardTrainingDays!=null && draft.longTrainingDays!=null &&
            draft.hardTrainingDays.intersect(draft.longTrainingDays).isNotEmpty())
            problem("hardTrainingDays",AthleteSetupIssueKind.CONFLICT,
                "Review overlapping hard-day and long-day designations.")
        for((field,muscles) in listOf(
            "focusedMuscles" to draft.focusedMuscles,
            "reducedMuscles" to draft.reducedMuscles)) {
            if(muscles==null)missing(field)
        }
        if(draft.focusedMuscles!=null && draft.reducedMuscles!=null &&
            draft.focusedMuscles.intersect(draft.reducedMuscles).isNotEmpty())
            problem("focusedMuscles",AthleteSetupIssueKind.CONFLICT,
                "A muscle cannot simultaneously be prioritized and reduced.")
        if(draft.excludedExerciseIds==null)missing("excludedExerciseIds")
        else if(draft.excludedExerciseIds.size>256 ||
            draft.excludedExerciseIds.any {!id.matches(it)})
            problem("excludedExerciseIds",AthleteSetupIssueKind.INVALID,
                "All excluded exercises need stable IDs.")

        val gym=draft.gym
        if(gym==null)missing("gym")
        else {
            checkId("gym.gymId",gym.gymId)
            if(gym.units==null)missing("gym.units")
            if(gym.confirmedStock==null)missing("gym.confirmedStock")
            if(gym.availableFacilities==null)missing("gym.availableFacilities")
            if(gym.availableAttachments==null)missing("gym.availableAttachments")
            for((field,available) in listOf(
                "gym.availableFacilities" to gym.availableFacilities,
                "gym.availableAttachments" to gym.availableAttachments)) {
                if(available!=null && (available.size>128 ||
                    available.any {!id.matches(it)}))
                    problem(field,AthleteSetupIssueKind.INVALID,
                        "Use bounded, verified fixture and attachment IDs.")
            }
            val stocks=gym.confirmedStock
            if(stocks!=null) {
                if(stocks.size>128 || stocks.keys.any {!id.matches(it)})
                    problem("gym.confirmedStock",AthleteSetupIssueKind.INVALID,
                        "The inventory is too large or uses an invalid implement ID.")
                if(stocks.isEmpty() && draft.allowBodyweight==false)
                    problem("gym.confirmedStock",AthleteSetupIssueKind.CONFLICT,
                        "No physical working implement is confirmed and bodyweight is disabled.")
                for((implement,stock) in stocks) {
                    if(gym.units!=null && stock.unit!=gym.units) {
                        problem("gym.confirmedStock.$implement",
                            AthleteSetupIssueKind.CONFLICT,
                            "An implement's load unit differs from the gym's chosen unit.")
                    } else if(EquipmentLoadDomain.inspect(stock,zero,stock.unit)
                        is LoadInspection.Unavailable) {
                        problem("gym.confirmedStock.$implement",
                            AthleteSetupIssueKind.INVENTORY_INVALID,
                            "The exercise-specific load inventory has not been validated.")
                    }
                }
            }
        }

        val frozenIssues=Collections.unmodifiableList(issues.toList())
        if(frozenIssues.isNotEmpty())return AthleteSetupReview(
            if(issues.any {it.kind!=AthleteSetupIssueKind.MISSING})
                AthleteSetupStatus.CONSTRAINT_CONFLICT
            else AthleteSetupStatus.NEEDS_INPUT,
            frozenIssues,null)

        // All required facts were checked above. Do not silently fill blanks.
        val approvedGym=checkNotNull(gym)
        val safeGym=ReviewedGymProfile(
            checkNotNull(approvedGym.gymId),checkNotNull(approvedGym.units),
            frozenMap(checkNotNull(approvedGym.confirmedStock)
                .mapValues {snapshot(it.value)}),
            frozenSet(checkNotNull(approvedGym.availableFacilities)),
            frozenSet(checkNotNull(approvedGym.availableAttachments)))
        val snapshot=ReviewedAthleteSetup(
            checkNotNull(draft.profileRevision),checkNotNull(draft.athleteProfileId),
            checkNotNull(draft.goal),checkNotNull(draft.experience),
            checkNotNull(draft.split),frozenSet(checkNotNull(days)),
            checkNotNull(draft.timezoneId),checkNotNull(draft.sessionTimeBucket),
            checkNotNull(draft.hardSessionLimit),safeGym,
            checkNotNull(draft.allowBodyweight),checkNotNull(draft.allowSupersets),
            checkNotNull(draft.allowTricepsOnLower),
            checkNotNull(draft.maxBarbellExercisesPerDay),
            frozenSet(checkNotNull(draft.focusedMuscles)),
            frozenSet(checkNotNull(draft.reducedMuscles)),
            frozenSet(checkNotNull(draft.excludedExerciseIds)),
            frozenSet(checkNotNull(draft.lockedTrainingDays)),
            frozenSet(checkNotNull(draft.hardTrainingDays)),
            frozenSet(checkNotNull(draft.longTrainingDays)),
            frozenSet(checkNotNull(draft.declaredRestDays)))
        return AthleteSetupReview(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,
            frozenIssues,snapshot)
    }
}
