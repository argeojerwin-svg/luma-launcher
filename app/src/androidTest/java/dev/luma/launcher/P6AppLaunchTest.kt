package dev.luma.launcher

import android.test.ActivityInstrumentationTestCase2
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.content.Intent

@Suppress("DEPRECATION")
class P6AppLaunchTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun children(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{children(v.getChildAt(it))}else emptyList()
    fun testBrandedTilesLaunchTheirOriginalApps(){
        val a=activity;SystemClock.sleep(1800)
        for(id in listOf("com.apple.atve.androidtv.appletv","com.google.android.youtube.tv","com.spotify.tv.android","com.stremio.one","com.amazon.amazonvideo.livingroom","com.disney.disneyplus","com.android.vending").filter{id->AppRepository(a).discover().any{it.id==id}}){
            instrumentation.runOnMainSync{
                if(id=="com.android.vending"){
                    a.home.onApps()
                    children(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.startsWith("Open Google Play Store")}.performClick()
                }else{
                    a.home.onAllApps()
                    val tile=children(a.home).filterIsInstance<AppTile>().first{it.item?.id==id}
                    tile.requestFocus();tile.performClick()
                }
            }
            SystemClock.sleep(4000)
            val state=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")).bufferedReader().use{it.readText()}
            assertTrue("Wrong foreground app for $id",state.lineSequence().any{it.contains("mResumedActivity:") && it.contains(id)})
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(1800)
        }
        instrumentation.runOnMainSync{a.home.focusFirst()}
    }
}
