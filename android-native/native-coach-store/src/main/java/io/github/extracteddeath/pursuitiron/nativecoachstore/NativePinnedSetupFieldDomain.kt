package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CatalogMovement

/** A UI suggestion for the verified movement's physical setup, not a
 * machine capability or an authorized training prescription. */
data class NativePinnedSetupField(
    val key:String,
    val label:String,
    val savedEarlier:Boolean
)

/**
 * Suggestions are keyed by exact source-authored equipment / fixture facts,
 * NEVER by human display names, similar movements, or guessed gym stock.
 * Source may be absent/stale: the generic v1 fields remain available, while
 * every safe saved key is still exposed for inspection, edit and explicit
 * removal. Unknown stored keys are NEVER silently dropped on Save.
 */
object NativePinnedSetupFieldDomain {
    private val generic=listOf(
        "attachment" to "Attachment / handle",
        "seat" to "Seat / bench setting",
        "grip" to "Grip / stance",
        "other" to "Other setup"
    )
    fun fields(
        movement:CatalogMovement?,
        saved:Map<String,String>
    ):List<NativePinnedSetupField> {
        val selected=LinkedHashMap<String,String>()
        fun add(key:String,label:String) {if(key !in selected)selected[key]=label}
        val equipment=movement?.equipmentId
        val facilities=movement?.requiredFacilityIds.orEmpty()
        when {
            movement==null -> generic.forEach{(key,label)->add(key,label)}
            equipment?.contains("cable",ignoreCase=true)==true -> {
                add("attachment","Cable attachment / handle")
                add("pulley_position","Pulley height / position")
                add("grip","Grip / stance")
                if("bench" in facilities || facilities.any{it.contains("bench")})
                    add("seat","Seat / bench position")
            }
            equipment=="leg-extension-machine" -> {
                add("seat","Seat / back pad position")
                add("roller_position","Ankle roller setting")
                add("range_stop","Range / stop setting")
            }
            equipment=="seated-leg-curl-machine" -> {
                add("seat","Seat / back pad position")
                add("thigh_pad","Thigh pad setting")
                add("ankle_pad","Ankle pad setting")
            }
            equipment?.contains("machine",ignoreCase=true)==true -> {
                add("seat","Seat / bench position")
                add("handle_position","Handle position")
            }
            equipment?.contains("bar",ignoreCase=true)==true -> {
                add("grip","Hand placement / grip")
                if("rack" in facilities)add("safeties","Rack safety / pin position")
            }
            equipment?.contains("dumbbell",ignoreCase=true)==true -> {
                add("grip","Grip / stance")
            }
            else -> add("grip","Grip / stance")
        }
        if(facilities.any{it.contains("incline-bench") ||
            it.contains("decline-bench")})
            add("bench_angle","Bench angle / notch")
        else if(facilities.any{it=="flat-bench"})
            add("bench_position","Bench position")
        if(movement?.attachmentId!=null)
            add("attachment","Verified attachment setup notes")
        add("other","Other setup")

        // Preserve supported legacy values even when field definitions have
        // since changed. Do not mistake orphan keys for current catalog facts.
        return buildList {
            selected.forEach{(key,label)->
                add(NativePinnedSetupField(key,label,false))
            }
            saved.keys.sorted().filter{
                it !in selected &&
                    NativePinnedExerciseNotePolicy.validSetupKey(it)
            }.forEach{key->
                add(NativePinnedSetupField(
                    key,"Saved earlier · "+key.replace('_',' ').replace('-',' '),
                    true))
            }
        }
    }
}
