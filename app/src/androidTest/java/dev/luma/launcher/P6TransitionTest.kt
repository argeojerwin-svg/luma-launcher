package dev.luma.launcher

import android.graphics.Bitmap
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.*
import java.io.File

@Suppress("DEPRECATION")
class P6TransitionTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun bringForward(){
        val command=instrumentation.uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -n dev.luma.launcher/.LauncherActivity")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use{it.readBytes()}
        val deadline=SystemClock.elapsedRealtime()+5000
        while(!activity.hasWindowFocus() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
        assertTrue("Luma must be foreground for injected remote events",activity.hasWindowFocus())
    }
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testRetainedArtworkFocusAndContinuousMotion(){
        val a=activity;bringForward();SystemClock.sleep(1800)
        var shared:AppTile?=null;var artwork:AppArtwork?=null
        val positions=mutableListOf<Float>();var blanks=0
        val observer=ViewTreeObserver.OnPreDrawListener{
            shared?.let{positions.add(it.y+(it.parent?.parent as? View)?.y.orZero());if(it.art==null)blanks++};true
        }
        instrumentation.runOnMainSync{
            if(a.home.allApps)a.onBackPressed()
        };SystemClock.sleep(800)
        instrumentation.runOnMainSync{
            shared=tree(a.home).filterIsInstance<AppTile>().first{it.item!=null};artwork=shared!!.art
            a.home.viewTreeObserver.addOnPreDrawListener(observer)
            tree(a.home).filterIsInstance<AppTile>().first{it.label=="All Apps"}.apply{requestFocus();performClick()}
            assertTrue(a.home.transitioning)
            assertSame(shared,tree(a.home).filterIsInstance<AppTile>().first{it.item?.id==shared!!.item!!.id})
            assertSame(artwork,shared!!.art)
        }
        SystemClock.sleep(1000)
        instrumentation.runOnMainSync{
            a.home.viewTreeObserver.removeOnPreDrawListener(observer)
            assertFalse(a.home.transitioning);assertEquals(0,blanks)
            assertTrue("Transition should have multiple drawn positions",positions.distinct().size>=3)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(150)
        val libraryFocus=a.home.focusedId
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(850)
        instrumentation.runOnMainSync{assertEquals("@apps",a.home.focusedId);a.home.onAllApps()}
        SystemClock.sleep(850)
        instrumentation.runOnMainSync{assertEquals(libraryFocus,a.home.focusedId);a.onBackPressed()}
        SystemClock.sleep(850)
        repeat(6){instrumentation.sendKeyDownUpSync(a.preferences.shortcut);SystemClock.sleep(90);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(90)}
        SystemClock.sleep(800)
        instrumentation.runOnMainSync{
            assertEquals(0,tree(a.window.decorView).filterIsInstance<GlassPanel>().size)
            assertTrue(a.currentFocus is AppTile)
            File(a.filesDir,"continuity.txt").writeText("drawnPositions=${positions.distinct().size}\nblankArtworkFrames=$blanks\nsharedTileAndBitmapRetained=true\nseparateHomeLibraryFocusRestored=true\nrapidPanelsRemoved=true\npositions=$positions\n")
        }
    }
    private fun Float?.orZero()=this?:0f
    fun testRepeatedLibraryTransitionsAndRapidBack(){
        val a=activity;bringForward();SystemClock.sleep(1800)
        val frames=mutableListOf<Long>()
        val listener=Window.OnFrameMetricsAvailableListener{_,metrics,_->frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))}
        instrumentation.runOnMainSync{a.window.addOnFrameMetricsAvailableListener(listener,android.os.Handler(android.os.Looper.getMainLooper()))}
        try{
            repeat(10){
                instrumentation.runOnMainSync{if(a.home.allApps)a.onBackPressed();a.home.focusFirst()}
                SystemClock.sleep(700)
                instrumentation.runOnMainSync{tree(a.home).filterIsInstance<AppTile>().first{it.label=="All Apps"}.apply{requestFocus();performClick()}}
                SystemClock.sleep(850)
                instrumentation.runOnMainSync{assertTrue(a.home.allApps);assertTrue(a.currentFocus is AppTile);assertTrue(tree(a.home).filterIsInstance<AppTile>().filter{it.item!=null}.all{it.art!=null})}
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                SystemClock.sleep(850)
                instrumentation.runOnMainSync{assertFalse(a.home.allApps);assertTrue(a.currentFocus is AppTile)}
            }
            repeat(8){instrumentation.runOnMainSync{a.home.onAllApps()};SystemClock.sleep(65);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(65)}
            SystemClock.sleep(900)
            instrumentation.runOnMainSync{assertFalse(a.home.allApps);assertTrue(a.currentFocus is AppTile)}
            val bitmap=instrumentation.uiAutomation.takeScreenshot();File(a.filesDir,"alpha-home.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }finally{
            instrumentation.runOnMainSync{a.window.removeOnFrameMetricsAvailableListener(listener)
                val sorted=frames.sorted();fun percentile(p:Double)=if(sorted.isEmpty())0.0 else sorted[((sorted.size-1)*p).toInt()]/1000000.0
                File(a.filesDir,"transition-metrics.txt").writeText("frames=${sorted.size}\nmedianMs=${percentile(.5)}\np90Ms=${percentile(.9)}\np99Ms=${percentile(.99)}\nover16ms=${sorted.count{it>16666667}}\n")}
        }
    }
}
