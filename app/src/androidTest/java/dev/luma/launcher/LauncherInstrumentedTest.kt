package dev.luma.launcher

import android.test.ActivityInstrumentationTestCase2
import android.view.KeyEvent
import android.view.View
import android.os.SystemClock

@Suppress("DEPRECATION")
class LauncherInstrumentedTest : ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java) {
    private fun ready(): LauncherActivity {
        val a=activity
        SystemClock.sleep(1800)
        instrumentation.runOnMainSync {a.preferences.resetLayout();a.home.rebuild(AppRepository(a).discover(),false)}
        instrumentation.waitForIdleSync();SystemClock.sleep(350)
        return a
    }
    fun testDiscoveryUsesRealExportedActivities() {
        val a=ready();val entries=AppRepository(a).discover()
        assertFalse("TV image should contain launchable apps",entries.isEmpty())
        assertFalse(entries.any{it.id==a.packageName})
        assertEquals(entries.size,entries.map{it.id}.distinct().size)
        assertTrue(entries.all{it.component.packageName==it.id})
    }
    fun testDpadAndControlCentreRoundTrip() {
        val a=ready();val first=a.home.focusedId
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(300)
        assertFalse(first==a.home.focusedId)
        val second=a.home.focusedId
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);SystemClock.sleep(350)
        assertTrue(a.currentFocus is GlassButton)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(350)
        assertTrue(a.currentFocus is AppTile);assertEquals(second,a.home.focusedId)
    }
    fun testClockProvidesRemoteFallback() {
        val a=ready()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);SystemClock.sleep(250)
        assertTrue(a.currentFocus is GlassButton)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(300)
        assertEquals("All Apps",(a.currentFocus as GlassButton).text.toString())
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
    fun testProfilePreferencesAreIsolatedAndPersisted() {
        val a=ready()
        instrumentation.runOnMainSync {
            val p=a.preferences;val original=p.profile
            p.profile=0;p.put("test_marker","alpha");p.profile=1;p.put("test_marker","beta")
            val reload=Preferences(a);assertEquals(1,reload.profile);assertEquals("beta",reload.text("test_marker"))
            reload.profile=0;assertEquals("alpha",reload.text("test_marker"));reload.profile=original
        }
    }
    fun testFocusRestoresAfterRebuild() {
        val a=ready();instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(200)
        val focused=a.home.focusedId
        instrumentation.runOnMainSync {a.home.rebuild(AppRepository(a).discover(),false)}
        instrumentation.waitForIdleSync();SystemClock.sleep(300)
        assertEquals(focused,a.home.focusedId);assertEquals(focused,a.preferences.text("focus"))
    }
    fun testLongSelectOpensAppActions() {
        val a=ready()
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER))
        SystemClock.sleep(750)
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));SystemClock.sleep(300)
        assertTrue(a.currentFocus is GlassButton);assertEquals("Open",(a.currentFocus as GlassButton).text.toString())
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
    fun testScreensaverWakeConsumesFirstRemotePress() {
        val a=ready();val before=a.home.focusedId
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);SystemClock.sleep(300)
        repeat(4){instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)}
        assertTrue((a.currentFocus as GlassButton).text.toString().startsWith("Start screensaver"))
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(650)
        val root=(a.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.view.ViewGroup)
        assertEquals(3,root.childCount)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(400)
        assertEquals(2,root.childCount);assertEquals(before,a.home.focusedId)
    }
    fun testMovePersistsAcrossRebuild() {
        val a=ready();val moving=a.home.focusedId
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));SystemClock.sleep(650)
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));SystemClock.sleep(250)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("Move app",(a.currentFocus as GlassButton).text.toString())
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(250)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(350)
        assertEquals(moving,a.home.focusedId);assertEquals(moving,a.preferences.list("order")[1])
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        assertNull(a.home.moveId)
    }
    fun testFailedLaunchReturnsFalse() {
        val a=ready();val missing=AppEntry("dev.luma.missing","Missing",android.content.ComponentName("dev.luma.missing","Missing"),false)
        assertFalse(AppRepository(a).launch(missing))
    }
    fun testWallpaperDecodeIsBoundedAndInvalidFilesFail() {
        val a=ready();val repository=WallpaperRepository(a)
        val latch=java.util.concurrent.CountDownLatch(1)
        var decoded: android.graphics.Bitmap?=null;var frost: android.graphics.Bitmap?=null
        instrumentation.runOnMainSync {repository.load("",1280){b,f->decoded=b;frost=f;latch.countDown()}}
        assertTrue(latch.await(10,java.util.concurrent.TimeUnit.SECONDS))
        assertNotNull(decoded);assertTrue(kotlin.math.max(decoded!!.width,decoded!!.height)<=1280);assertEquals(96,frost!!.width)
        val bad=java.util.concurrent.CountDownLatch(1);var rejected=false
        instrumentation.runOnMainSync {repository.load("content://dev.luma.not.a.provider/no-image"){b,_->rejected=b==null;bad.countDown()}}
        assertTrue(bad.await(10,java.util.concurrent.TimeUnit.SECONDS));assertTrue(rejected)
        instrumentation.runOnMainSync {repository.close()}
    }

}
