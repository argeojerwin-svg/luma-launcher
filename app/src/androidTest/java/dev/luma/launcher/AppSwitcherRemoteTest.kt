package dev.luma.launcher

import android.content.Intent
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup

@Suppress("DEPRECATION")
class AppSwitcherRemoteTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap{tree(view.getChildAt(it))}else emptyList())
    private fun waitUntil(condition:()->Boolean){
        val deadline=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<deadline){
            var ready=false
            instrumentation.runOnMainSync{ready=condition()}
            if(ready)return
            SystemClock.sleep(50)
        }
        fail("App Switcher did not reach the expected focus state")
    }
    fun testArrowsRecoverLostFocusAndStayWithinCards(){
        val launcher=activity
        val prefs=launcher.preferences
        val originalRecent=prefs.text("recentApps")
        val originalEnabled=prefs.number("recentEnabled",1)
        val entries=AppRepository(launcher).discover().filter{AppVisibility.visible(it,prefs)}.take(3)
        assertTrue("At least two visible apps are required",entries.size>=2)
        try{
            instrumentation.runOnMainSync{
                prefs.put("recentEnabled",1);prefs.putList("recentApps",entries.map{it.id})
                launcher.startActivity(Intent(launcher,LauncherActivity::class.java).apply{
                    action=LauncherActivity.ACTION_GLOBAL_CONTROL
                    putExtra(LauncherActivity.EXTRA_GLOBAL_TARGET,"app_switcher")
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            }
            waitUntil{tree(launcher.window.decorView).any{it is AppSwitcherView}}
            lateinit var cards:List<AppTile>
            instrumentation.runOnMainSync{
                val switcher=tree(launcher.window.decorView).filterIsInstance<AppSwitcherView>().single()
                cards=tree(switcher).filterIsInstance<AppTile>()
            }
            waitUntil{cards[0].hasFocus()}
            instrumentation.runOnMainSync{launcher.currentFocus?.clearFocus()}
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntil{cards[1].hasFocus()}
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
            waitUntil{cards[0].hasFocus()}
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
            waitUntil{cards[0].hasFocus()}
            repeat(cards.size+1){instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)}
            waitUntil{cards.last().hasFocus()}
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitUntil{tree(launcher.window.decorView).none{it is AppSwitcherView}}
        }finally{
            instrumentation.runOnMainSync{prefs.put("recentApps",originalRecent);prefs.put("recentEnabled",originalEnabled)}
        }
    }
}
