package dev.luma.launcher

import android.test.InstrumentationTestCase
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Suppress("DEPRECATION")
class TileIdentityTest:InstrumentationTestCase(){
    fun testOriginalIdentitiesAndPackageMapping(){
        val context=instrumentation.targetContext
        val repo=AppRepository(context)
        val apps=repo.discover()
        val directory=File(context.filesDir,"tile-artwork").apply{mkdirs()}
        val manifest=JSONArray()
        for(app in apps){
            val component=app.component.flattenToString()
            val identity=repo.identity(app)
            assertFalse("Missing bespoke identity: ${app.id}",identity.motif=="monogram")
            val art=TileArtwork.render(identity)
            assertEquals(320,art.bitmap.width);assertEquals(192,art.bitmap.height)
            assertNotNull(art.middle);assertNotNull(art.foreground)
            assertTrue(art.bytes<=800000)
            assertEquals(component,app.component.flattenToString())
            for((suffix,bitmap) in listOf("background" to art.bitmap,"middle" to art.middle!!,"foreground" to art.foreground!!)){
                File(directory,"${app.id}-$suffix.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
            manifest.put(JSONObject().put("package",app.id).put("originalLabel",app.label).put("displayName",identity.name).put("motif",identity.motif).put("launchComponent",component))
        }
        File(directory,"manifest.json").writeText(manifest.toString(2))
        apps.firstOrNull{it.id=="com.android.vending"}?.let{assertEquals("Apps",repo.displayName(it))}
        apps.firstOrNull{it.id=="com.spotify.tv.android"}?.let{assertEquals("Spotify",repo.displayName(it))}
        apps.firstOrNull{it.id=="com.google.android.youtube.tv"}?.let{assertEquals("YouTube",repo.displayName(it))}
    }
}
