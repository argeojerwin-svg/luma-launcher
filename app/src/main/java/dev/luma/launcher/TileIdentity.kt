package dev.luma.launcher

import android.content.Context
import org.json.JSONObject

/** Display identity only. Package IDs and launch components always come from PackageManager. */
data class TileIdentity(val name:String,val motif:String,val hue:Float,val accent:Float)
class TileIdentities(context:Context) {
    private val catalog=JSONObject(context.assets.open("tile-identities.json").bufferedReader().use{it.readText()})
    fun get(app:AppEntry):TileIdentity {
        val spec=catalog.optJSONObject(app.id)
        if(spec!=null)return TileIdentity(spec.optString("name",app.label),spec.getString("motif"),spec.getDouble("hue").toFloat(),spec.optDouble("accent",spec.getDouble("hue")+35).toFloat())
        // New apps receive an original, stable identity; never replace their underlying app mapping.
        val hash=app.id.hashCode().toLong().let{if(it<0)-it else it}
        return TileIdentity(app.label.ifBlank{app.id.substringAfterLast('.')},"monogram",(hash%360).toFloat(),((hash%360+36)%360).toFloat())
    }
}
