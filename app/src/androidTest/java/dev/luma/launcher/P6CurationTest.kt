package dev.luma.launcher

import android.test.ActivityInstrumentationTestCase2
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.graphics.Rect

@Suppress("DEPRECATION")
class P6CurationTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun bringForward(){
        val command=instrumentation.uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -n dev.luma.launcher/.LauncherActivity")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use{it.readBytes()}
        val deadline=SystemClock.elapsedRealtime()+5000
        while(!activity.hasWindowFocus() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
        assertTrue("Luma must be foreground for injected remote events",activity.hasWindowFocus())
    }
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    private fun ready():LauncherActivity {val a=activity;bringForward();SystemClock.sleep(1800);return a}
    private fun click(a:LauncherActivity,prefix:String){instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.startsWith(prefix)}.performClick()};SystemClock.sleep(350)}
    fun testCuratedLibraryAndSeparateApps(){
        val a=ready();instrumentation.runOnMainSync{a.home.onAllApps()};SystemClock.sleep(500)
        instrumentation.runOnMainSync{
            val tiles=tree(a.home).filterIsInstance<AppTile>()
            tiles.take(6).forEach{tile->assertTrue("Library tile is offscreen: ${tile.label}",tile.getGlobalVisibleRect(Rect()))}
            val entries=tiles.mapNotNull{it.item}
            assertTrue(entries.any{it.id=="com.google.android.youtube.tv"})
            assertFalse(entries.any{it.id=="com.android.vending" || it.id=="com.yhk.factory" || it.isHome})
            assertTrue(entries.all{AppVisibility.visible(it,Preferences(a))})
            a.home.onApps()
        }
        SystemClock.sleep(350)
        assertTrue(tree(a.window.decorView).filterIsInstance<GlassButton>().any{it.text.startsWith("Open Google Play Store")})
    }
    fun testVisibilityOverridePersistsWithoutChangingPackage(){
        val a=ready();val prefs=Preferences(a);val repo=AppRepository(a)
        val utility=repo.discover().first{it.id=="com.android.calculator2"};val key="visibility:${utility.id}";val old=prefs.text(key)
        try{
            prefs.put(key,"show");assertTrue(AppVisibility.visible(utility,Preferences(a)))
            instrumentation.runOnMainSync{a.home.rebuild(repo.discover(),true)};SystemClock.sleep(400)
            assertTrue(tree(a.home).filterIsInstance<AppTile>().any{it.item?.id==utility.id})
            prefs.put(key,"hide");assertFalse(AppVisibility.visible(utility,Preferences(a)))
            assertTrue(a.packageManager.getApplicationInfo(utility.id,0).enabled)
        }finally{prefs.put(key,old);instrumentation.runOnMainSync{a.home.rebuild(repo.discover(),false)}}
    }
    fun testManagementListsHiddenUtilities(){
        val a=ready();instrumentation.runOnMainSync{a.home.onApps()};SystemClock.sleep(300)
        click(a,"Hidden applications")
        val labels=tree(a.window.decorView).filterIsInstance<GlassButton>().map{it.text.toString()}
        assertTrue(labels.any{it.startsWith("FactoryMenu")});assertTrue(labels.any{it.startsWith("File manager")})
        click(a,"FactoryMenu")
        assertTrue(tree(a.window.decorView).filterIsInstance<GlassButton>().any{it.text.startsWith("Show in entertainment library")})
    }
    fun testControlCentreRowsNeverScaleOrOverflow(){
        val a=ready();instrumentation.sendKeyDownUpSync(a.preferences.shortcut);SystemClock.sleep(400)
        val buttons=tree(a.window.decorView).filterIsInstance<GlassPanel>().last().buttons
        for(button in buttons){
            instrumentation.runOnMainSync{button.requestFocus()};SystemClock.sleep(180)
            instrumentation.runOnMainSync{
                assertEquals(1f,button.scaleX);assertEquals(1f,button.scaleY)
                val rect=Rect();val parent=Rect();button.getGlobalVisibleRect(rect);(button.parent as View).getGlobalVisibleRect(parent)
                assertTrue(rect.left>=parent.left && rect.right<=parent.right)
                assertTrue(button.height>button.paddingTop+button.paddingBottom)
            }
        }
    }
    fun testDockOrderingPersists(){
        val a=ready();val p=Preferences(a);val before=p.list("dock")
        try{p.putList("dock",before.reversed());assertEquals(before.reversed(),Preferences(a).list("dock"))}finally{p.putList("dock",before)}
    }
}
