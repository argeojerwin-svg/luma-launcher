package dev.luma.launcher

import android.app.Dialog
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.net.Socket
import java.net.URI

@Suppress("DEPRECATION")
class PhoneKeyboardTest:ActivityInstrumentationTestCase2<LauncherActivity>(LauncherActivity::class.java){
    private fun tree(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{tree(view.getChildAt(it))}else emptyList()
    private fun waitUntil(condition:()->Boolean){
        val deadline=SystemClock.uptimeMillis()+6000
        while(SystemClock.uptimeMillis()<deadline){
            var ready=false;instrumentation.runOnMainSync{ready=condition()};if(ready)return;SystemClock.sleep(50)
        }
        fail("Keyboard did not reach its expected state")
    }
    private fun request(url:String,path:String,method:String="GET",value:String=""):String{
        val uri=URI(url);val bytes=value.toByteArray(Charsets.UTF_8)
        return Socket(uri.host,uri.port).use{socket->
            socket.soTimeout=3000
            val headers="$method $path HTTP/1.1\r\nHost: ${uri.host}:${uri.port}\r\nX-Luma-Token: ${uri.fragment}\r\nContent-Length: ${bytes.size}\r\n\r\n"
            socket.getOutputStream().apply{write(headers.toByteArray());write(bytes);flush()}
            String(socket.getInputStream().readBytes(),Charsets.UTF_8)
        }
    }
    fun testSystemKeyboardRemotePhoneGoAndPasswordIsolation(){
        val launcher=activity;lateinit var dialog:Dialog;lateinit var field:EditText;var saved=""
        instrumentation.runOnMainSync{
            field=EditText(launcher).apply{
                setSingleLine();inputType=android.text.InputType.TYPE_CLASS_TEXT
                imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                setOnEditorActionListener{_,action,_->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){saved=text.toString();true}else false}
            }
            dialog=Dialog(launcher).apply{setTitle("Android input test");setContentView(field);show()}
            field.requestFocus()
            launcher.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        try{
            var url=""
            val deadline=SystemClock.uptimeMillis()+10000
            while(url.isEmpty() && SystemClock.uptimeMillis()<deadline){
                val bitmap=instrumentation.uiAutomation.takeScreenshot()
                if(bitmap!=null){
                    val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    url=runCatching{QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels)))).text}.getOrDefault("")
                    bitmap.recycle()
                }
                if(url.isEmpty())SystemClock.sleep(100)
            }
            assertTrue("System keyboard QR did not appear",url.startsWith("http://"))
            instrumentation.runOnMainSync{assertFalse(launcher.getSystemService(android.view.inputmethod.InputMethodManager::class.java).isFullscreenMode)}
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil{field.text.toString()=="w"}
            assertTrue(request(url,"/text","POST","old query").startsWith("HTTP/1.1 200"))
            waitUntil{field.text.toString()=="old query"}
            assertTrue(request(url,"/submit","POST","final query").endsWith("Done"))
            waitUntil{saved=="final query"}
            instrumentation.runOnMainSync{
                field.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                field.setText("");field.requestFocus()
                launcher.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            val uri=URI(url)
            try{Socket(uri.host,uri.port).close();fail("Previous field QR remained accessible")}catch(_:java.io.IOException){}
            assertTrue(LumaInputMethodService.isPassword(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD))
            assertTrue(LumaInputMethodService.isPassword(android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD))
            assertFalse(LumaInputMethodService.isPassword(android.text.InputType.TYPE_CLASS_TEXT))
        }finally{instrumentation.runOnMainSync{dialog.dismiss()}}
    }
    fun testQwertyRemoteEditingAndExistingSaveBehavior(){
        val launcher=activity;lateinit var dialog:Dialog;var saved=""
        instrumentation.runOnMainSync{dialog=GlassNameDialog.show(launcher,WallpaperView(launcher),launcher.preferences,"Keyboard test","Old name",8){saved=it}}
        try{
            lateinit var keyboard:QwertyKeyboardView;lateinit var field:EditText
            instrumentation.runOnMainSync{
                val views=tree(dialog.window!!.decorView)
                keyboard=views.filterIsInstance<QwertyKeyboardView>().single();field=views.filterIsInstance<EditText>().single()
                val letterRow=tree(keyboard.getChildAt(1)).filterIsInstance<TextView>().joinToString(""){it.text}
                assertEquals("qwertyuiop",letterRow)
                assertFalse(field.showSoftInputOnFocus)
            }
            waitUntil{keyboard.firstKey.hasFocus()}
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil{field.text.toString()=="qw"}
            instrumentation.runOnMainSync{
                fun click(label:String){tree(keyboard).filterIsInstance<TextView>().first{it.text==label}.performClick()}
                click("Delete");assertEquals("q",field.text.toString())
                field.setText("😀");field.setSelection(field.length());click("Delete");assertEquals("",field.text.toString())
                field.setText("12345678");field.setSelection(field.length());click("q");assertEquals("12345678",field.text.toString())
                click("Clear");click("q");click("Space");click("w")
                tree(dialog.window!!.decorView).filterIsInstance<GlassButton>().first{it.text=="Save"}.performClick()
                assertEquals("q w",saved);assertFalse(dialog.isShowing)
            }
        }finally{instrumentation.runOnMainSync{dialog.dismiss()}}
    }
    fun testPhoneGoSavesFinalTextAndClosesDialog(){
        val launcher=activity;lateinit var dialog:Dialog;var saved="";var count=0
        instrumentation.runOnMainSync{dialog=GlassNameDialog.show(launcher,WallpaperView(launcher),launcher.preferences,"Go test","",40){saved=it;count++}}
        try{
            lateinit var qr:ImageView
            instrumentation.runOnMainSync{qr=tree(dialog.window!!.decorView).filterIsInstance<ImageView>().single()}
            waitUntil{qr.tag is String}
            var url="";instrumentation.runOnMainSync{url=qr.tag as String}
            assertTrue(request(url,"/submit","POST","  ").startsWith("HTTP/1.1 400"))
            instrumentation.runOnMainSync{assertTrue(dialog.isShowing);assertEquals(0,count)}
            assertTrue(request(url,"/text","POST","old draft").startsWith("HTTP/1.1 200"))
            assertTrue(request(url,"/submit","POST","  Café 🎬  ").endsWith("\r\n\r\nDone"))
            waitUntil{!dialog.isShowing}
            instrumentation.runOnMainSync{assertEquals("Café 🎬",saved);assertEquals(1,count)}
            val uri=URI(url)
            try{Socket(uri.host,uri.port).close();fail("Go must close the phone connection")}catch(_:java.io.IOException){}
        }finally{instrumentation.runOnMainSync{dialog.dismiss()}}
    }
    fun testQrDecodesPhoneTextArrivesAndDismissClosesConnection(){
        val launcher=activity;lateinit var dialog:Dialog
        instrumentation.runOnMainSync{dialog=GlassNameDialog.show(launcher,WallpaperView(launcher),launcher.preferences,"Phone test","From TV",40){}}
        try{
            lateinit var qr:ImageView;lateinit var field:EditText
            instrumentation.runOnMainSync{
                val views=tree(dialog.window!!.decorView);qr=views.filterIsInstance<ImageView>().single();field=views.filterIsInstance<EditText>().single()
            }
            waitUntil{qr.tag is String}
            var url=""
            instrumentation.runOnMainSync{
                url=qr.tag as String
                val bitmap=(qr.drawable as BitmapDrawable).bitmap
                val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val decoded=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels)))).text
                assertEquals(url,decoded)
            }
            assertTrue(request(url,"/state").contains("From TV"))
            assertTrue(request(url,"/text","POST","Café 🎬").startsWith("HTTP/1.1 200"))
            waitUntil{field.text.toString()=="Café 🎬"}
            instrumentation.runOnMainSync{
                // Search's caller replaces this listener; cleanup must remain owned by the dialog.
                dialog.setOnDismissListener{}
                dialog.dismiss()
            }
            val uri=URI(url)
            try{Socket(uri.host,uri.port).close();fail("Phone connection remained open after dismissal")}catch(_:java.io.IOException){}
        }finally{instrumentation.runOnMainSync{dialog.dismiss()}}
    }
}
