package dev.luma.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** User-provided, app-verified deep links only; no invented streaming catalogue. */
data class TopShelfItem(val title:String,val description:String,val openUri:String,val infoUri:String="")

object TopShelfContent {
    private fun key(appId:String)="topItems:$appId"
    fun list(prefs:Preferences,appId:String):List<TopShelfItem> = runCatching {
        val data=JSONArray(prefs.text(key(appId),"[]"))
        (0 until minOf(data.length(),5)).map{data.getJSONObject(it)}.map{
            TopShelfItem(it.optString("title"),it.optString("description"),it.optString("open"),it.optString("info"))
        }.filter{it.title.isNotBlank() && it.openUri.isNotBlank()}
    }.getOrDefault(emptyList())
    fun save(prefs:Preferences,appId:String,items:List<TopShelfItem>){
        val data=JSONArray()
        items.take(5).forEach{data.put(JSONObject().put("title",it.title).put("description",it.description).put("open",it.openUri).put("info",it.infoUri))}
        prefs.put(key(appId),data.toString())
    }
    fun intent(context:Context,app:AppEntry,uriText:String):Intent? = runCatching{
        val uri=Uri.parse(uriText.trim())
        if(uri.scheme.isNullOrBlank() || uri.scheme in listOf("file","content","javascript"))return null
        Intent(Intent.ACTION_VIEW,uri).setPackage(app.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).takeIf{
            it.resolveActivity(context.packageManager)!=null
        }
    }.getOrNull()
}
