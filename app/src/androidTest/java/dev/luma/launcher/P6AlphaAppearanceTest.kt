package dev.luma.launcher
import android.graphics.Bitmap
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import java.io.File
@Suppress("DEPRECATION")
class P6AlphaAppearanceTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testGlassModesAndWallpaperContrastRemainEditable(){
        val a=activity
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -n dev.luma.launcher/.LauncherActivity")).use{it.readBytes()}
        SystemClock.sleep(1400);val mode=a.preferences.number("mode",1);val dark=a.preferences.number("dark",0)
        fun click(prefix:String){instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.toString().removePrefix("✓").trimStart().startsWith(prefix)}.performClick()}}
        try{
            for(quality in 0..2){
                instrumentation.runOnMainSync{a.preferences.put("mode",quality);a.home.onControl()};SystemClock.sleep(800)
                instrumentation.runOnMainSync{val buttons=tree(a.window.decorView).filterIsInstance<GlassPanel>().last().buttons;buttons[2].requestFocus()};SystemClock.sleep(650)
                assertTrue("Luma must be foreground for visual capture",a.hasWindowFocus())
                val bitmap=instrumentation.uiAutomation.takeScreenshot();File(a.filesDir,"alpha-glass-$quality.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            click("Launcher settings");click("Appearance");click("Background contrast");click("Darker");SystemClock.sleep(200)
            instrumentation.runOnMainSync{assertEquals(.30f,tree(a.window.decorView).filterIsInstance<WallpaperView>().first().darkness)}
            click("Background contrast");click("Cinematic");SystemClock.sleep(200)
            instrumentation.runOnMainSync{assertEquals(.08f,tree(a.window.decorView).filterIsInstance<WallpaperView>().first().darkness)}
        }finally{
            instrumentation.runOnMainSync{a.preferences.put("mode",mode);a.preferences.put("dark",dark);a.home.onControl()}
            click("Launcher settings");click("Appearance");click("Background contrast");click(if(dark==1)"Darker" else "Cinematic")
        }
    }
}
