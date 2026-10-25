package dev.luma.launcher

import android.test.ActivityInstrumentationTestCase2
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import java.io.File

/** Host installs/removes its own temporary fixture while the library stays open. */
@Suppress("DEPRECATION")
class P6PackageChangeTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testLiveInstallAndRemoval(){
        val a=activity;SystemClock.sleep(1800)
        instrumentation.runOnMainSync{a.home.onAllApps()}
        File(a.filesDir,"probe-ready").writeText("ready")
        fun present():Boolean {var found=false;instrumentation.runOnMainSync{found=tree(a.home).filterIsInstance<AppTile>().any{it.item?.id=="dev.luma.discoveryprobe"}};return found}
        var count=0
        while(!present() && count++<45)SystemClock.sleep(1000)
        assertTrue("Installed video-category fixture did not enter the open library",present())
        File(a.filesDir,"probe-visible").writeText("visible")
        count=0
        while(present() && count++<45)SystemClock.sleep(1000)
        assertFalse("Removed fixture stayed in library",present())
    }
}
