package dev.luma.launcher

import android.test.ActivityInstrumentationTestCase2
import android.os.SystemClock
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.graphics.Bitmap
import java.io.File

@Suppress("DEPRECATION")
class P6NameDialogTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    fun testNameEditorSavesWithoutTouchingAppPreferences(){
        val a=activity;SystemClock.sleep(1200);var dialog:Dialog?=null;var saved=""
        instrumentation.runOnMainSync{dialog=GlassNameDialog.show(a,WallpaperView(a),Preferences(a),"Tile display name","Example",40){saved=it}}
        SystemClock.sleep(500)
        val screenshot=instrumentation.uiAutomation.takeScreenshot()
        val directory=File(a.filesDir,"curated-screens").apply{mkdirs()}
        File(directory,"Name-Editor.png").outputStream().use{screenshot.compress(Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
        instrumentation.runOnMainSync{
            val views=tree(dialog!!.window!!.decorView)
            views.filterIsInstance<EditText>().single().setText("Updated name")
            views.filterIsInstance<GlassButton>().first{it.text=="Save"}.performClick()
            assertEquals("Updated name",saved);assertFalse(dialog!!.isShowing)
        }
    }
}
