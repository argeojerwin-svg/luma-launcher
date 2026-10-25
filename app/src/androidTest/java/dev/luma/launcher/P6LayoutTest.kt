package dev.luma.launcher

import android.graphics.Rect
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Non-destructive live-device checks: no layout reset, profile change or Home-default mutation. */
@Suppress("DEPRECATION")
class P6LayoutTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun ready():LauncherActivity{
        val a=activity;SystemClock.sleep(1600)
        instrumentation.runOnMainSync{a.home.focusFirst()};instrumentation.waitForIdleSync();SystemClock.sleep(400)
        return a
    }
    private fun descendants(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{descendants(view.getChildAt(it))}else emptyList()
    fun testLabelsAndFooterStaySeparatedWhileNavigating(){
        val a=ready()
        repeat(7){
            instrumentation.runOnMainSync{
                val footer=descendants(a.home).filterIsInstance<TextView>().first{it.text.toString().startsWith("Hold OK")}
                val footerRect=Rect();assertTrue(footer.getGlobalVisibleRect(footerRect))
                val tiles=descendants(a.home).filterIsInstance<AppTile>()
                for(tile in tiles){
                    val label=descendants(tile).filterIsInstance<TextView>().single()
                    assertEquals(1,label.maxLines);assertEquals(TextUtils.TruncateAt.END,label.ellipsize)
                    val labelRect=Rect()
                    if(label.getGlobalVisibleRect(labelRect))assertFalse("Label overlaps reserved footer: ${tile.label}",Rect.intersects(labelRect,footerRect))
                }
                val selected=a.currentFocus as? AppTile
                if(selected!=null){
                    val iconRect=Rect();val captionRect=Rect()
                    assertTrue(selected.getChildAt(0).getGlobalVisibleRect(iconRect))
                    assertTrue(selected.getChildAt(1).getGlobalVisibleRect(captionRect))
                    assertTrue("Focused icon collides with its label",iconRect.bottom+10<=captionRect.top)
                }
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(420)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
    fun testFocusRestoresAfterControlCentre(){
        val a=ready();instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(300)
        val id=a.home.focusedId
        instrumentation.sendKeyDownUpSync(a.preferences.shortcut);SystemClock.sleep(300)
        assertTrue(a.currentFocus is ControlActionView)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(300)
        assertEquals(id,a.home.focusedId);assertTrue(a.currentFocus is AppTile)
    }
    fun testOriginalHomeAppsRemainAvailable(){
        val a=ready();val entries=AppRepository(a).discover()
        assertTrue(entries.any{it.id=="com.zeasn.launcher" && it.isHome})
        assertTrue(entries.any{it.id=="com.spocky.projengmenu" && it.isHome})
    }
}
