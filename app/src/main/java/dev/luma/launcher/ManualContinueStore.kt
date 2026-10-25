package dev.luma.launcher

import org.json.JSONArray
import org.json.JSONObject

/** Explicitly user-managed titles. No playback position is inferred from launching an app. */
object ManualContinueStore {
    private const val KEY="manualContinue"
    fun list(prefs:Preferences):List<ContinueItem> = runCatching {
        val array=JSONArray(prefs.text(KEY,"[]"))
        (0 until array.length()).map { array.getJSONObject(it) }.map { item ->
            ContinueItem(item.optString("id"),item.optString("title"),item.optString("subtitle"),null,
                item.optLong("added").toString(),item.optString("image"),"",
                item.optString("package"),item.optString("provider"),item.optString("link"),"Added by you")
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() && it.sourcePackage.isNotBlank() }
    }.getOrDefault(emptyList())
    fun add(prefs:Preferences,provider:String,label:String,title:String){
        val next=list(prefs)+ContinueItem("manual:${System.currentTimeMillis()}",title,"",null,
            System.currentTimeMillis().toString(),"","",provider,label,"","Added by you")
        save(prefs,next)
    }
    fun update(prefs:Preferences,entry:ContinueItem){save(prefs,list(prefs).map{if(it.id==entry.id)entry else it})}
    fun remove(prefs:Preferences,id:String){save(prefs,list(prefs).filterNot{it.id==id})}
    private fun save(prefs:Preferences,items:List<ContinueItem>){
        val array=JSONArray()
        items.forEach { item -> array.put(JSONObject().put("id",item.id).put("title",item.title)
            .put("subtitle",item.subtitle).put("added",item.pausedAt.toLongOrNull()?:0)
            .put("image",item.imageUrl).put("package",item.sourcePackage)
            .put("provider",item.sourceLabel).put("link",item.openUri)) }
        prefs.put(KEY,array.toString())
    }
}
