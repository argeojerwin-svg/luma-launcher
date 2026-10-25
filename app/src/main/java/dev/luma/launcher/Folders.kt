package dev.luma.launcher

import org.json.JSONArray
import org.json.JSONObject

data class AppFolder(val id:String, val name:String, val members:List<String>) {
    val tileId get()="@folder:$id"
}

/** Small, profile-scoped layout record. A missing app remains in the folder until reinstalled. */
object Folders {
    fun read(prefs:Preferences):List<AppFolder> = runCatching {
        val array=JSONArray(prefs.text("folders","[]"))
        (0 until array.length()).map { index ->
            val item=array.getJSONObject(index)
            val members=item.getJSONArray("members")
            AppFolder(item.getString("id"),item.optString("name","Folder"),
                (0 until members.length()).map{members.getString(it)}.distinct())
        }
    }.getOrDefault(emptyList())
    fun write(prefs:Preferences,folders:List<AppFolder>) {
        val array=JSONArray()
        folders.forEach { folder ->
            array.put(JSONObject().put("id",folder.id).put("name",folder.name)
                .put("members",JSONArray(folder.members.distinct())))
        }
        prefs.put("folders",array.toString())
    }
    fun find(prefs:Preferences,tileId:String)=read(prefs).firstOrNull{it.tileId==tileId}
    fun containing(prefs:Preferences,appId:String)=read(prefs).firstOrNull{appId in it.members}
}
