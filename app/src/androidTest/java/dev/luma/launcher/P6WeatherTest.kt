package dev.luma.launcher

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class P6WeatherTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testLiveWeatherGeometryCacheAndSettings(){
        val a=activity
        val cache=a.getSharedPreferences("weather",0)
        val until=SystemClock.elapsedRealtime()+25000
        while(cache.getLong("saved",0)==0L && SystemClock.elapsedRealtime()<until)SystemClock.sleep(250)
        assertTrue("Live Open-Meteo response was not cached",cache.getLong("saved",0)>0)
        SystemClock.sleep(1000)
        instrumentation.runOnMainSync{
            val clock=tree(a.home).filterIsInstance<TextView>().first{it.contentDescription=="Clock and Control Centre"}
            val weather=tree(a.home).filterIsInstance<TextView>().first{it.text.contains("°C")}
            assertFalse(weather.isFocusable)
            val cr=Rect();val wr=Rect();assertTrue(clock.getGlobalVisibleRect(cr));assertTrue(weather.getGlobalVisibleRect(wr))
            assertEquals(cr.height(),wr.height());assertEquals(cr.top,wr.top)
            assertEquals(wr.left,a.home.width-cr.right)
            assertEquals(76,cr.height());assertEquals(277,cr.width())
            assertFalse(Rect.intersects(cr,wr))
            tree(a.home).filterIsInstance<AppTile>().forEach{val r=Rect();if(it.getGlobalVisibleRect(r)){assertFalse(Rect.intersects(wr,r));assertFalse(Rect.intersects(cr,r))}}
            var cached="";val client=Weather(a){text,_->cached=text};client.start();client.close()
            assertTrue("Recreated client should render persistent cache immediately",cached.contains("°C"))
        }
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(a.filesDir,"weather-home.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val latch=CountDownLatch(1);var matches:List<Weather.Place>?=null
        lateinit var lookup:Weather
        instrumentation.runOnMainSync{lookup=Weather(a){_,_->};lookup.search("London"){matches=it;latch.countDown()}}
        assertTrue("Location search timed out",latch.await(20,TimeUnit.SECONDS));assertTrue(matches?.any{it.name.contains("London")}==true)
        instrumentation.runOnMainSync{lookup.close();a.home.onControl()}
        fun click(prefix:String){instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.startsWith(prefix)}.performClick()}}
        click("Launcher settings");click("Weather ·")
        SystemClock.sleep(400)
        instrumentation.runOnMainSync{assertTrue(tree(a.window.decorView).filterIsInstance<TextView>().any{it.text.contains("Weather by Open-Meteo")})}
        val settings=instrumentation.uiAutomation.takeScreenshot()
        File(a.filesDir,"weather-settings.png").outputStream().use{settings.compress(Bitmap.CompressFormat.PNG,100,it)};settings.recycle()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    }
}
