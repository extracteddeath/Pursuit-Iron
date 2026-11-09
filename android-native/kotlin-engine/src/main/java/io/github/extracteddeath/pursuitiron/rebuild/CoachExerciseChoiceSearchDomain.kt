package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Presentation-only filter. The native host owns which exercises have a
 * current athlete/program/gym authorization; filtering NEVER adds options.
 */
object CoachExerciseChoiceSearchDomain {
    fun filter(
        approved:List<String>,query:String,label:(String)->String
    ):List<String> {
        val tokens=query.trim().take(80).replace('_',' ').replace('-',' ')
            .lowercase().split(Regex("\\s+")).filter {it.isNotBlank()}
        return approved.distinct().filter { id ->
            val searchable=(label(id)+" "+id).replace('_',' ').replace('-',' ')
                .lowercase()
            tokens.all { searchable.contains(it) }
        }
    }
}
