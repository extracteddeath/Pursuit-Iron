package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Shared presentation-only exercise search. Callers supply already authorized
 * choices and verified metadata; filtering never grants exercise or load access
 * and never changes ranking. Parse the bounded query once per search.
 */
object CoachExerciseChoiceSearchDomain {
    private fun normalize(value:String)=value.replace('_',' ').replace('-',' ')
        .lowercase(java.util.Locale.ROOT)

    fun tokens(query:String):List<String> = normalize(query.trim().take(80))
        .split(Regex("\\s+")).filter{it.isNotBlank()}

    fun matches(tokens:List<String>,fields:()->List<String>):Boolean {
        if(tokens.isEmpty())return true
        val searchable=normalize(fields().joinToString(" "))
        return tokens.all{searchable.contains(it)}
    }

    fun filter(
        approved:List<String>,query:String,label:(String)->String
    ):List<String> {
        val terms=tokens(query)
        return approved.distinct().filter {id->
            matches(terms){listOf(label(id),id)}
        }
    }
}
