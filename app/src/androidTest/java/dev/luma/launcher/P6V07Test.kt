package dev.luma.launcher

import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText

@Suppress("DEPRECATION")
class P6V07Test:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    private fun ready():LauncherActivity {
        val a=activity
        val command=instrumentation.uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -n dev.luma.launcher/.LauncherActivity")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use{it.readBytes()}
        val deadline=SystemClock.elapsedRealtime()+5000
        while(!a.hasWindowFocus() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
        assertTrue(a.hasWindowFocus());SystemClock.sleep(800);return a
    }
    fun testFolderOrderAndBackRestoresFocus(){
        val a=ready();val p=a.preferences;val saved=p.text("folders","[]")
        val entries=AppRepository(a).discover().filter{AppVisibility.visible(it,p)}.take(2)
        assertEquals(2,entries.size)
        val folder=AppFolder("v07-test","Test Folder",entries.map{it.id})
        try{
            Folders.write(p,Folders.read(p)+folder)
            instrumentation.runOnMainSync{a.home.showLibrary(entries,folder.tileId)}
            SystemClock.sleep(350)
            val tile=tree(a.home).filterIsInstance<AppTile>().first{it.label==folder.name}
            instrumentation.runOnMainSync{tile.requestFocus();tile.performClick()}
            SystemClock.sleep(350)
            assertEquals(folder.id,a.home.currentFolderId)
            assertEquals(entries.map{it.id},tree(a.home).filterIsInstance<AppTile>().filter{it.isFocusable}.mapNotNull{it.item?.id})
            instrumentation.runOnMainSync{a.home.beginMove(entries[1].id);a.home.onMove?.invoke(entries[1].id,entries[0].id)}
            SystemClock.sleep(350)
            assertEquals(entries[1].id,Folders.read(p).first{it.id==folder.id}.members.first())
            instrumentation.runOnMainSync{a.onBackPressed();a.onBackPressed()}
            SystemClock.sleep(350)
            assertNull(a.home.currentFolderId)
            assertEquals(folder.tileId,a.home.focusedId)
        }finally{
            p.put("folders",saved)
            instrumentation.runOnMainSync{a.home.finishMove();a.home.rebuild(AppRepository(a).discover(),false)}
        }
    }
    fun testHoldingOkStartsAndEndsEditMode(){
        val a=ready()
        instrumentation.runOnMainSync{
            if(a.home.allApps)a.onBackPressed()
            tree(a.home).filterIsInstance<AppTile>().first{it.item!=null}.requestFocus()
            a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER))
        }
        SystemClock.sleep(650)
        instrumentation.runOnMainSync{a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER))}
        assertEquals(a.home.focusedId,a.home.moveId)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue("Editing must retain tile focus at the top row",a.currentFocus is AppTile)
        instrumentation.runOnMainSync{a.onBackPressed()}
        assertNull(a.home.moveId)
    }
    fun testCreateRenameAndPopulateFolderThroughControls(){
        val a=ready();val p=a.preferences
        val beforeFolders=p.text("folders","[]");val beforeDock=p.list("dock")
        val entries=AppRepository(a).discover().filter{AppVisibility.visible(it,p) && Folders.containing(p,it.id)==null}.take(2)
        assertEquals(2,entries.size)
        val oldVisibility=p.text("visibility:${entries[0].id}")
        try{
            instrumentation.runOnMainSync{a.home.onContext(entries[0])}
            SystemClock.sleep(220)
            instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.startsWith("Create folder")}.performClick()}
            SystemClock.sleep(300)
            instrumentation.runOnMainSync{
                val dialog=a.folderNameDialog!!.window!!.decorView
                tree(dialog).filterIsInstance<EditText>().single().setText("Cinema Test")
                tree(dialog).filterIsInstance<GlassButton>().first{it.text=="Save"}.performClick()
            }
            SystemClock.sleep(400)
            val created=Folders.read(p).first{it.name=="Cinema Test"}
            assertEquals(listOf(entries[0].id),created.members)
            instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text.startsWith("Add application")}.performClick()}
            SystemClock.sleep(250)
            instrumentation.runOnMainSync{tree(a.window.decorView).filterIsInstance<GlassButton>().first{it.text==AppRepository(a).displayName(entries[1])}.performClick()}
            SystemClock.sleep(350)
            assertEquals(2,Folders.read(p).first{it.id==created.id}.members.size)
            assertEquals(created.id,a.home.currentFolderId)
        }finally{
            p.put("folders",beforeFolders);p.putList("dock",beforeDock);p.put("visibility:${entries[0].id}",oldVisibility)
            instrumentation.runOnMainSync{a.onBackPressed();a.home.rebuild(AppRepository(a).discover(),false)}
        }
    }
}
