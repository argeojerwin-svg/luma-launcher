package dev.luma.launcher

import android.graphics.Rect
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.TextView

@Suppress("DEPRECATION")
class P6V071Test:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testMenuToggleRestoresFocusAndClock(){
        val a=activity;SystemClock.sleep(700)
        instrumentation.runOnMainSync{a.home.focusGrid()}
        val original=a.currentFocus
        val clock=tree(a.home).filterIsInstance<GlassButton>().first{it.text.toString().contains(Regex("\\d{1,2}:\\d{2}"))}
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);SystemClock.sleep(300)
        assertEquals("All Apps",a.currentFocus?.contentDescription)
        assertEquals(View.INVISIBLE,clock.visibility)
        assertEquals(1,tree(a.window.decorView).count{it is GlassPanel})
        assertTrue(tree(a.window.decorView).filterIsInstance<TextView>()
            .any{it.contentDescription=="Control Centre time" && it.text.isNotBlank()})
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);SystemClock.sleep(350)
        assertSame(original,a.currentFocus)
        assertEquals(View.VISIBLE,clock.visibility)
        assertFalse(tree(a.window.decorView).any{it is GlassPanel})
    }
    fun testControlHierarchyMoreActionsAndBack(){
        val a=activity;SystemClock.sleep(700)
        instrumentation.runOnMainSync{a.home.onControl()}
        SystemClock.sleep(400)
        val panel=tree(a.window.decorView).filterIsInstance<GlassPanel>().last()
        val actions=panel.buttons.filterIsInstance<ControlActionView>()
        assertEquals(listOf("All Apps","App Switcher","Search","Audio settings","Wallpaper","Start screensaver","More controls"),
            actions.map{it.contentDescription.toString()})
        instrumentation.runOnMainSync{
            val first=Rect();val second=Rect();assertTrue(actions[0].getGlobalVisibleRect(first));assertTrue(actions[1].getGlobalVisibleRect(second))
            assertFalse(Rect.intersects(first,second));assertTrue(actions.all{it.scaleX==1f && it.scaleY==1f})
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("Search",a.currentFocus?.contentDescription)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("All Apps",a.currentFocus?.contentDescription)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("App Switcher",a.currentFocus?.contentDescription)
        instrumentation.runOnMainSync{actions[6].performClick()}
        SystemClock.sleep(350)
        val more=tree(a.window.decorView).filterIsInstance<GlassPanel>().last()
        val labels=more.buttons.filterIsInstance<GlassButton>().map{it.text.toString()}
        assertTrue(labels.any{it.startsWith("Continue Watching")})
        assertTrue(labels.any{it.startsWith("Recently Used")})
        assertTrue(labels.any{it.startsWith("Launcher settings")})
        assertTrue(labels.any{it.startsWith("Selected tile")})
        assertTrue(labels.any{it.startsWith("Home app & recovery")})
        instrumentation.runOnMainSync{a.onBackPressed()};SystemClock.sleep(750)
        assertEquals(7,tree(a.window.decorView).filterIsInstance<GlassPanel>().last().buttons.filterIsInstance<ControlActionView>().size)
        assertEquals("More controls",a.currentFocus?.contentDescription)
        instrumentation.runOnMainSync{a.onBackPressed()};SystemClock.sleep(750)
        assertTrue(a.currentFocus is AppTile)
        assertFalse(tree(a.window.decorView).any{it is GlassPanel})
    }
}
