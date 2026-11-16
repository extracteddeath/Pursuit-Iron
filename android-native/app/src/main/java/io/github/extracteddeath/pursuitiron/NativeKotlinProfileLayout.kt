package io.github.extracteddeath.pursuitiron

/** Profile-only display controls; none has authority over training decisions. */
internal enum class NativeProfileSection(val label:String) {
    TRAINING("Training preferences"),
    PROGRESS("Milestones and strength club"),
    SHARE("Training share card"),
    STRENGTH_FAVORITES("Pinned strength charts"),
    EXERCISE_FAVORITES("Pinned exercise histories")
}

internal data class NativeProfileLayout(
    val order:List<NativeProfileSection>,
    val hidden:Set<NativeProfileSection>
) {
    companion object {
        val DEFAULT=NativeProfileLayout(NativeProfileSection.values().toList(),emptySet())

        /** Unknown/corrupt settings fail to the complete, accessible default. */
        fun decode(raw:String?):NativeProfileLayout {
            if(raw.isNullOrBlank())return DEFAULT
            val fields=raw.split('|')
            if(fields.size!=3 || fields[0]!="v1")return DEFAULT
            val all=NativeProfileSection.values().toSet()
            fun parse(value:String):List<NativeProfileSection>? {
                if(value.isBlank())return emptyList()
                return value.split(',').map {token->
                    NativeProfileSection.values().firstOrNull{it.name==token}
                        ?:return null
                }
            }
            val arranged=parse(fields[1])?:return DEFAULT
            val concealed=parse(fields[2])?:return DEFAULT
            if(arranged.size!=all.size || arranged.toSet()!=all ||
                concealed.size!=concealed.toSet().size || concealed.any{it !in all})
                return DEFAULT
            return NativeProfileLayout(arranged.toList(),concealed.toSet())
        }
    }

    fun encode():String="v1|"+order.joinToString(","){it.name}+"|"+
        hidden.sortedBy{it.name}.joinToString(","){it.name}

    fun setVisible(section:NativeProfileSection,visible:Boolean):NativeProfileLayout =
        copy(hidden=if(visible)hidden-section else hidden+section)

    fun move(section:NativeProfileSection,offset:Int):NativeProfileLayout {
        if(offset !in -1..1)return this
        val i=order.indexOf(section)
        val j=i+offset
        if(i<0 || j !in order.indices)return this
        return copy(order=order.toMutableList().also {
            val old=it[i];it[i]=it[j];it[j]=old
        })
    }
}
