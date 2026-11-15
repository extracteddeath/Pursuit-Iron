package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomAuthoringSnapshot
import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingCycle

/** Saved editor coordinates only. A request never authorizes a native write. */
internal data class NativeKotlinSupersetEditRequest(
    val ownerKey:String,val athleteId:String,val cycleId:String,val cycleRevision:Long,
    val sourceRevision:Long,val blockId:String,val weekday:Int,val groupId:String
) {
    fun matches(draft:CustomTrainingCycle)=draft.athleteId==athleteId &&
        draft.cycleId==cycleId && draft.revision==cycleRevision
    fun matchesSource(source:NativeCustomAuthoringSnapshot)=source.ownerKey==ownerKey &&
        source.athleteId==athleteId && source.revision==sourceRevision
    fun savedState():List<Any> = listOf("superset-edit-v1",ownerKey,athleteId,cycleId,
        cycleRevision,sourceRevision,blockId,weekday,groupId)
    companion object {
        fun restore(values:List<Any>):NativeKotlinSupersetEditRequest? {
            if(values.size!=9 || values[0]!="superset-edit-v1")return null
            val request=NativeKotlinSupersetEditRequest(
                values[1] as? String ?:return null,values[2] as? String ?:return null,
                values[3] as? String ?:return null,values[4] as? Long ?:return null,
                values[5] as? Long ?:return null,values[6] as? String ?:return null,
                values[7] as? Int ?:return null,values[8] as? String ?:return null)
            val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
            return request.takeIf {it.ownerKey.isNotBlank() && it.ownerKey.length<=200 &&
                it.ownerKey.none(Char::isISOControl) &&
                listOf(it.athleteId,it.cycleId,it.blockId,it.groupId).all(id::matches) &&
                it.cycleRevision in 1..9_007_199_254_740_990L &&
                it.sourceRevision in 1..9_007_199_254_740_991L && it.weekday in 0..6}
        }
    }
}
