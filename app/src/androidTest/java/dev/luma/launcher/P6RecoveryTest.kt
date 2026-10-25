package dev.luma.launcher

import android.test.ActivityInstrumentationTestCase2
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.content.Intent

@Suppress("DEPRECATION")
class P6RecoveryTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testRecoveryLaunchesWithoutChangingDefault(){
        val a=activity;SystemClock.sleep(1600)
        fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).bufferedReader().use{it.readText()}
        val before=shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        fun click(prefix:String){instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.startsWith(prefix)}.performClick()};SystemClock.sleep(350)}
        for((label,pkg) in listOf("Open Smart TV" to "com.zeasn.launcher","Open Projectivy" to "com.spocky.projengmenu","Choose default Home app" to "com.google.android.permissioncontroller")){
            instrumentation.runOnMainSync{a.home.onControl()};SystemClock.sleep(350);click("Home app & recovery");click(label);SystemClock.sleep(2500)
            assertTrue("Recovery destination failed: $label",shell("dumpsys activity activities").lineSequence().any{it.contains("mResumedActivity:") && it.contains(pkg)})
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1200)
        }
        assertEquals(before,shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"))
    }
}
