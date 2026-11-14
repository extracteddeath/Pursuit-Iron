package io.github.extracteddeath.pursuitiron

/** Presentation routes only. No workout, history or approval authority. */
internal enum class KotlinCandidateDestination(val label:String) {
    ONBOARD("My setup"),GYM("My gym"),STARTER("Build first"),PROGRAM("My program"),
    COACH("Coach"),FUTURE("Future weeks"),HISTORY("History"),PROGRESS("Progress"),
    PROFILE("Profile"),SETTINGS("Settings"),TRAIN("Train");

    val primaryHome:KotlinCandidateDestination? get()=when(this) {
        PROGRAM,STARTER,FUTURE -> PROGRAM
        COACH,HISTORY,PROGRESS,TRAIN -> this
        ONBOARD,GYM,PROFILE,SETTINGS -> null
    }
    val isPrimary:Boolean get()=this==primaryHome
}

internal enum class NativeKotlinRouteDetail {
    NONE, COACH_EXERCISE, PROGRESS_EXERCISE, PROGRESS_TREND
}

/** A contextual exercise request belongs to its route, never a global tab. */
internal data class NativeKotlinRoute(
    val destination:KotlinCandidateDestination,
    val detail:NativeKotlinRouteDetail=NativeKotlinRouteDetail.NONE,
    val exerciseId:String?=null
) {
    // Ordinary screen keys preserve their existing saved drafts. Contextual
    // screens keep independent UI state for each kind of exercise request.
    val saveableKey:String get()=if(detail==NativeKotlinRouteDetail.NONE)destination.name
        else "${destination.name}:${detail.name}:$exerciseId"

    init {
        require(if(detail==NativeKotlinRouteDetail.NONE)exerciseId==null
            else !exerciseId.isNullOrBlank() && exerciseId.length<=512)
        require(when(detail) {
            NativeKotlinRouteDetail.NONE -> true
            NativeKotlinRouteDetail.COACH_EXERCISE -> destination==KotlinCandidateDestination.COACH
            NativeKotlinRouteDetail.PROGRESS_EXERCISE,
            NativeKotlinRouteDetail.PROGRESS_TREND -> destination==KotlinCandidateDestination.PROGRESS
        })
    }
}

/**
 * One immutable, saveable navigation history shared by phone and rail.
 * Primary tabs begin their ordinary overview. Contextual links retain the
 * complete originating route, including any selected exercise. Revisiting
 * the same secondary route returns to it instead of creating a Back loop.
 * IDs are display requests; each destination still authenticates its data.
 */
internal class NativeKotlinNavigationState private constructor(
    private val routes:List<NativeKotlinRoute>
) {
    val current:NativeKotlinRoute get()=routes.last()
    val canGoBack:Boolean get()=routes.size>1
    val contextStateKeys:Set<String> get()=routes
        .filter{it.detail!=NativeKotlinRouteDetail.NONE}.map{it.saveableKey}.toSet()
    val selectedPrimary:KotlinCandidateDestination? get()=
        routes.asReversed().firstNotNullOfOrNull {it.destination.primaryHome}

    fun open(destination:KotlinCandidateDestination):NativeKotlinNavigationState =
        if(destination.isPrimary)start(destination)
        else openContext(NativeKotlinRoute(destination))

    fun openCoach(exerciseId:String?):NativeKotlinNavigationState =
        if(exerciseId==null)open(KotlinCandidateDestination.COACH)
        else openContext(NativeKotlinRoute(KotlinCandidateDestination.COACH,
            NativeKotlinRouteDetail.COACH_EXERCISE,exerciseId))

    fun openProgressExercise(exerciseId:String)=openContext(NativeKotlinRoute(
        KotlinCandidateDestination.PROGRESS,NativeKotlinRouteDetail.PROGRESS_EXERCISE,exerciseId))

    fun openProgressTrend(exerciseId:String)=openContext(NativeKotlinRoute(
        KotlinCandidateDestination.PROGRESS,NativeKotlinRouteDetail.PROGRESS_TREND,exerciseId))

    private fun openContext(route:NativeKotlinRoute):NativeKotlinNavigationState {
        val existing=routes.indexOfLast {it==route}
        if(existing==routes.lastIndex)return this
        return NativeKotlinNavigationState(if(existing>=0)routes.take(existing+1)
            else routes+route)
    }

    fun back():NativeKotlinNavigationState=
        if(canGoBack)NativeKotlinNavigationState(routes.dropLast(1)) else this

    /** Fixed-width fields avoid delimiter ambiguity in saved exercise IDs. */
    fun snapshot():List<String> = listOf(VERSION)+routes.flatMap {
        listOf(it.destination.name,it.detail.name,it.exerciseId.orEmpty())
    }

    companion object {
        private const val VERSION="native-navigation-v1"
        fun start(destination:KotlinCandidateDestination=KotlinCandidateDestination.ONBOARD)=
            NativeKotlinNavigationState(listOf(NativeKotlinRoute(destination)))

        fun restore(saved:Any?):NativeKotlinNavigationState? {
            // Old versions saved one enum here. An incompatible UI bundle
            // must safely reinitialize, not crash before durable recovery.
            if(saved !is List<*> || saved.size !in 4..193 ||
                saved.any{it !is String})return null
            val fields=saved.filterIsInstance<String>()
            if(fields.first()!=VERSION || (fields.size-1)%3!=0)return null
            return runCatching {
                val routes=fields.drop(1).chunked(3).map {row->NativeKotlinRoute(
                    KotlinCandidateDestination.valueOf(row[0]),
                    NativeKotlinRouteDetail.valueOf(row[1]),row[2].takeIf{it.isNotEmpty()})}
                require(routes.distinct().size==routes.size)
                require(routes.first().detail==NativeKotlinRouteDetail.NONE)
                require(routes.drop(1).none {
                    it.destination.isPrimary && it.detail==NativeKotlinRouteDetail.NONE
                })
                NativeKotlinNavigationState(routes)
            }.getOrNull()
        }
    }
}
