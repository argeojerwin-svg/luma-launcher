package dev.luma.launcher

import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.graphics.Rect
import android.widget.TextView
import android.widget.EditText
import android.view.KeyEvent
import android.os.ParcelFileDescriptor

@Suppress("DEPRECATION")
class P6V05Test:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    private fun ready():LauncherActivity {
        val command=instrumentation.uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -n dev.luma.launcher/.LauncherActivity")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use{it.readBytes()}
        val a=activity;val deadline=SystemClock.elapsedRealtime()+5000
        while(!a.hasWindowFocus() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
        assertTrue(a.hasWindowFocus());SystemClock.sleep(1500);return a
    }
    fun testTopShelfFocusAndContextReturn(){
        val a=ready()
        val tile=tree(a.home).filterIsInstance<AppTile>().first{it.item!=null}
        instrumentation.runOnMainSync{tile.requestFocus()};SystemClock.sleep(500)
        val shelf=tree(a.home).filterIsInstance<TopShelfView>().single()
        assertEquals(View.VISIBLE,shelf.visibility)
        assertTrue(tree(shelf).filterIsInstance<TextView>().any{it.text.toString()==tile.label})
        val sr=Rect();val tr=Rect();assertTrue(shelf.getGlobalVisibleRect(sr));assertTrue(tile.getGlobalVisibleRect(tr));assertTrue(sr.bottom<tr.top)
        instrumentation.runOnMainSync{tile.performLongClick()};SystemClock.sleep(350)
        assertTrue(tree(a.window.decorView).filterIsInstance<GlassButton>().any{it.text.toString()=="Move app"})
        instrumentation.runOnMainSync{a.onBackPressed()};SystemClock.sleep(350)
        assertTrue(tile.hasFocus())
        instrumentation.runOnMainSync{a.home.onAllApps()};SystemClock.sleep(350)
        assertEquals(View.GONE,shelf.visibility)
    }
    fun testRecentOnlyTracksLauncherAndReconciles(){
        val a=ready();val p=a.preferences;val before=p.list("recentApps");val enabled=p.number("recentEnabled",1)
        val app=AppRepository(a).discover().first{AppVisibility.visible(it,p)}
        try{
            p.put("recentEnabled",1);RecentApps.clear(p)
            RecentApps.record(p,app.id);RecentApps.record(p,app.id)
            assertEquals(listOf(app.id),p.list("recentApps"))
            RecentApps.record(p,"missing.package.for.test")
            RecentApps.reconcile(p,AppRepository(a).discover())
            assertEquals(listOf(app.id),p.list("recentApps"))
            instrumentation.runOnMainSync{a.home.onControl()};SystemClock.sleep(300)
            assertTrue(tree(a.window.decorView).filterIsInstance<GlassButton>().any{it.text.toString().startsWith("Recently Used")})
            instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.toString().startsWith("Recently Used")}.performClick()};SystemClock.sleep(300)
            assertTrue(tree(a.home).filterIsInstance<AppTile>().any{it.item?.id==app.id})
        }finally{p.putList("recentApps",before);p.put("recentEnabled",enabled);instrumentation.runOnMainSync{a.home.rebuild(AppRepository(a).discover(),false)}}
    }
    fun testActualAppLaunchIsRecorded(){
        val a=ready();val id="com.google.android.youtube.tv";val p=a.preferences
        val app=AppRepository(a).discover().first{it.id==id};val before=p.list("recentApps");val enabled=p.number("recentEnabled",1)
        try{
            p.put("recentEnabled",1)
            instrumentation.runOnMainSync{a.home.onLaunch(app)}
            SystemClock.sleep(3500)
            val state=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")).bufferedReader().use{it.readText()}
            assertTrue(state.lineSequence().any{it.contains("mResumedActivity:") && it.contains(id)})
            assertEquals(id,p.list("recentApps").first())
        }finally{p.putList("recentApps",before);p.put("recentEnabled",enabled)}
    }
    fun testLocalSearchUsesVisibleTiles(){
        val a=ready()
        instrumentation.runOnMainSync{a.home.onControl()};SystemClock.sleep(250)
        instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.toString().startsWith("Search apps")}.performClick()};SystemClock.sleep(300)
        val dialog=a.searchDialog!!
        val editor=tree(dialog.window!!.decorView).filterIsInstance<EditText>().first()
        instrumentation.runOnMainSync{editor.setText("YouTube");tree(dialog.window!!.decorView).filterIsInstance<GlassButton>().first{it.text.toString()=="Save"}.performClick()};SystemClock.sleep(400)
        val results=tree(a.home).filterIsInstance<AppTile>().filter{it.isFocusable}.mapNotNull{it.item}
        assertTrue(results.any{it.id=="com.google.android.youtube.tv"})
        assertTrue(results.all{AppVisibility.visible(it,a.preferences) && (it.label.contains("YouTube",true)||AppRepository(a).displayName(it).contains("YouTube",true))})
    }
    fun testCollectionRemoteNavigationAndBack(){
        val a=ready();val entries=AppRepository(a).discover().filter{AppVisibility.visible(it,a.preferences)}.take(2)
        assertEquals(2,entries.size)
        instrumentation.runOnMainSync{a.home.showCollection(AppRepository(a).discover(),"Recently Used",entries.map{it.id})}
        SystemClock.sleep(350)
        val tiles=tree(a.home).filterIsInstance<AppTile>().filter{it.isFocusable && it.item!=null}
        instrumentation.runOnMainSync{tiles[0].requestFocus()}
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(tiles[1].hasFocus())
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        SystemClock.sleep(350)
        assertFalse(a.home.allApps)
    }
}
