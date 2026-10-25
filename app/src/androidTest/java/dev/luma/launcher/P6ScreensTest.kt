package dev.luma.launcher

import android.test.ActivityInstrumentationTestCase2
import android.os.SystemClock
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import java.io.File

@Suppress("DEPRECATION")
class P6ScreensTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testCaptureScreens(){
        val a=activity;SystemClock.sleep(1800)
        val directory=File(a.filesDir,"curated-screens").apply{mkdirs()}
        fun capture(name:String){SystemClock.sleep(700);val bitmap=instrumentation.uiAutomation.takeScreenshot();assertNotNull(bitmap);File(directory,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        fun click(prefix:String){instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.startsWith(prefix)}.performClick()}}
        instrumentation.runOnMainSync{a.home.focusFirst()};capture("Home")
        instrumentation.runOnMainSync{a.home.onAllApps()};capture("Library")
        instrumentation.runOnMainSync{a.home.onApps()};capture("Apps")
        click("App Management");capture("App-Management")
        click("Hidden applications");capture("Hidden-Applications")
        instrumentation.runOnMainSync{a.home.onControl()};capture("Control-Centre")
        click("Launcher settings");capture("Settings")
        click("Appearance");capture("Appearance")
        instrumentation.runOnMainSync{a.home.onControl()};click("Home app & recovery");capture("Recovery")
    }
}
