package dev.luma.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache

data class AppEntry(val id: String, val label: String, val component: ComponentName, val isHome: Boolean, val systemApp:Boolean=false, val applicationCategory:Int=-1, val tvLaunchable:Boolean=false)
data class AppArtwork(val bitmap: Bitmap, val banner: Boolean, val middle: Bitmap?=null, val foreground: Bitmap?=null) {
    val bytes get()=bitmap.allocationByteCount+(middle?.allocationByteCount?:0)+(foreground?.allocationByteCount?:0)
}

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val identities=TileIdentities(context)
    private val preferences=Preferences(context)
    fun displayName(app:AppEntry)=preferences.text("tileName:${app.id}").ifBlank{if(app.id=="com.android.vending")"Apps" else app.label.ifBlank{identities.get(app).name}}
    fun identity(app:AppEntry)=identities.get(app)
    private val cache = object : LruCache<String, AppArtwork>(DeviceBudget.appCacheBytes(context)) {
        override fun sizeOf(key: String, value: AppArtwork) = value.bytes
    }
    @Suppress("DEPRECATION")
    fun discover(): List<AppEntry> {
        val homeComponents = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toSet()
        val result = linkedMapOf<String, AppEntry>()
        // TV entries take precedence, conventional launcher activities provide compatibility with projector firmware.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_HOME)) {
            val activities = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            for (info in activities) {
                val ai = info.activityInfo
                if (ai.packageName == context.packageName || !ai.enabled || !ai.exported) continue
                val component = ComponentName(ai.packageName, ai.name)
                if (result.containsKey(ai.packageName)) continue
                result[ai.packageName] = AppEntry(ai.packageName, info.loadLabel(pm).toString(), component,
                    homeComponents.any { it.packageName == ai.packageName },
                    ai.applicationInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0,
                    if(android.os.Build.VERSION.SDK_INT>=26)ai.applicationInfo.category else -1,
                    category==Intent.CATEGORY_LEANBACK_LAUNCHER)
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }
    // LruCache itself is thread-safe. A main-thread cache hit never waits for a decode lock.
    fun cachedArtwork(app:AppEntry):AppArtwork?=cache.get("${app.id}:${preferences.text("tileArt:${app.id}")}:${preferences.text("tileStyle:${app.id}")}")
    @Synchronized fun artwork(app: AppEntry): AppArtwork {
        val uri=preferences.text("tileArt:${app.id}")
        val style=preferences.text("tileStyle:${app.id}")
        val key="${app.id}:$uri:$style"
        cache.get(key)?.let{return it}
        val image=if(uri.isNotBlank())runCatching{customArtwork(uri)}.getOrNull() else null
        return (image?:if(app.id=="@market")BrandedTileArtwork.render(pm,app.copy(id="com.android.vending"),identities.get(app)) else if(app.id.startsWith("@"))TileArtwork.render(identities.get(app)) else BrandedTileArtwork.render(pm,app,identities.get(app),style!="original")).also{cache.put(key,it)}
    }
    /** Large cards are decoded on demand and held only while the switcher is visible. */
    fun switcherArtwork(app:AppEntry):AppArtwork {
        val uri=preferences.text("tileArt:${app.id}")
        val style=preferences.text("tileStyle:${app.id}")
        if(uri.isNotBlank())runCatching{customArtwork(uri,640,384)}.getOrNull()?.let{return it}
        return BrandedTileArtwork.render(pm,app,identities.get(app),style!="original",640,384)
    }
    private fun customArtwork(uri:String,width:Int=TileArtwork.WIDTH,height:Int=TileArtwork.HEIGHT):AppArtwork {
        fun stream()=context.contentResolver.openInputStream(android.net.Uri.parse(uri))?:error("Image unavailable")
        val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
        stream().use{android.graphics.BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0 && bounds.outHeight>0)
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>640)sample*=2
        val options=android.graphics.BitmapFactory.Options().apply{inSampleSize=sample}
        val decoded=stream().use{android.graphics.BitmapFactory.decodeStream(it,null,options)}?:error("Unsupported image")
        val out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val c=Canvas(out);val scale=maxOf(out.width.toFloat()/decoded.width,out.height.toFloat()/decoded.height)
        val w=decoded.width*scale;val h=decoded.height*scale
        c.drawBitmap(decoded,null,android.graphics.RectF((out.width-w)/2,(out.height-h)/2,(out.width+w)/2,(out.height+h)/2),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        decoded.recycle()
        return AppArtwork(out,true)
    }
    @Synchronized fun clear() = cache.evictAll()
    fun launch(app: AppEntry): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        true
    } catch (_: RuntimeException) { false }
}
