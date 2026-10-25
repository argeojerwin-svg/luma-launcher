package dev.luma.launcher

import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

@Suppress("DEPRECATION")
class P6V06Test:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testEmptyTopShelfLeavesWallpaperClean(){
        val a=activity;SystemClock.sleep(1000)
        val app=AppRepository(a).discover().first{AppVisibility.visible(it,a.preferences) && TopShelfContent.list(a.preferences,it.id).isEmpty()}
        val tile=tree(a.home).filterIsInstance<AppTile>().firstOrNull{it.item?.id==app.id}
        if(tile!=null)instrumentation.runOnMainSync{tile.requestFocus()}
        SystemClock.sleep(300)
        assertEquals(View.GONE,tree(a.home).filterIsInstance<TopShelfView>().single().visibility)
        instrumentation.runOnMainSync{a.home.onTopShelf(app)};SystemClock.sleep(250)
        assertFalse(tree(a.window.decorView).any{it is TopShelfExpandedView})
    }
    fun testSwitcherRemoteBack(){
        val a=activity;SystemClock.sleep(1000)
        val app=AppRepository(a).discover().first{AppVisibility.visible(it,a.preferences)}
        val before=a.preferences.list("recentApps")
        try{
            RecentApps.record(a.preferences,app.id)
            instrumentation.runOnMainSync{a.home.onControl()};SystemClock.sleep(200)
            instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<ControlActionView>().first{it.contentDescription=="App Switcher"}.performClick()}
            SystemClock.sleep(350)
            assertTrue(tree(a.window.decorView).any{it is AppSwitcherView})
            instrumentation.runOnMainSync{a.onBackPressed()};SystemClock.sleep(400)
            assertFalse(tree(a.window.decorView).any{it is AppSwitcherView})
        }finally{a.preferences.putList("recentApps",before)}
    }
    fun testProfileEncryptedTraktStore(){
        val a=activity;val profile=a.preferences.profile
        val secure=SecureStore(a,profile)
        try{
            secure.put("test-v06","private history")
            assertEquals("private history",secure.get("test-v06"))
            assertNull(SecureStore(a,(profile+1)%3).get("test-v06"))
        }finally{secure.clear("test-v06")}
    }
    fun testTraktPlaybackAndUpNextRemainDistinct(){
        val a=activity;val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
        try{
            val client=TraktClient(a.preferences,SecureStore(a,a.preferences.profile),worker)
            val paused="""[{"id":5,"type":"movie","progress":42.5,"movie":{"title":"Example Film","ids":{"imdb":"tt1234567"}}}]"""
            val next="""[{"show":{"title":"Example Show","ids":{"imdb":"tt7654321"}},"progress":{"next_episode":{"season":2,"number":3,"title":"Example Episode"}}}]"""
            val p=client.parsePlayback(paused).single();val n=client.parseUpNext(next).single()
            assertEquals(42.5f,p.progress);assertNull(n.progress)
            assertTrue(n.subtitle.startsWith("Next · S2 E3"))
            assertTrue(n.stremioUri.contains("tt7654321:2:3"))
        }finally{worker.shutdownNow()}
    }
    fun testContinueCardsUseOnlyRealProgress(){
        val a=activity;val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
        val wallpaper=tree(a.window.decorView).filterIsInstance<WallpaperView>().first()
        val items=listOf(
            ContinueItem("fixture-paused","Fixture Film","Film",54f,"","",""),
            ContinueItem("fixture-next","Fixture Show","Next · S2 E3",null,"","","")
        )
        val container=a.findViewById<ViewGroup>(android.R.id.content)
        var view:ContinueWatchingView?=null
        try{
            instrumentation.runOnMainSync{
                view=ContinueWatchingView(a,wallpaper,a.preferences,TraktImageCache(a),worker,items,{},{},{})
                container.addView(view,ViewGroup.LayoutParams(-1,-1))
            }
            SystemClock.sleep(200)
            assertTrue(tree(view!!).filterIsInstance<TextView>().any{it.text.toString().contains("54% watched")})
            instrumentation.runOnMainSync{view?.step(1)}
            assertTrue(tree(view!!).filterIsInstance<TextView>().any{it.text.toString().contains("Next episode")})
            assertTrue(tree(view!!).filterIsInstance<android.widget.ProgressBar>().all{it.visibility==View.GONE})
        }finally{instrumentation.runOnMainSync{view?.let{container.removeView(it)}};worker.shutdownNow()}
    }
}
