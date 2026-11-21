package dev.luma.launcher

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest

/** Runs as the existing local debug shell. Accepts only fixed input actions on loopback. */
object PhoneInputBridge {
    @JvmStatic fun main(args:Array<String>){
        if(args.size!=4 || !Regex("[a-f0-9]{48}").matches(args[0]))return
        val token=args[0];val port=args[1].toInt();val width=args[2].toInt();val height=args[3].toInt()
        if(port !in 1024..65535 || width !in 1..16384 || height !in 1..16384)return
        val type=Class.forName("android.hardware.input.InputManager")
        val manager=type.getMethod("getInstance").invoke(null)
        val inject=type.getMethod("injectInputEvent",InputEvent::class.java,Int::class.javaPrimitiveType)
        fun send(event:InputEvent){check(inject.invoke(manager,event,0)==true)}
        var x=width/2f;var y=height/2f
        var hovering=false
        fun mouse(action:Int,buttons:Int=0,scroll:Float=0f,started:Long=SystemClock.uptimeMillis(),button:Int=0){
            val now=SystemClock.uptimeMillis()
            val properties=MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE}
            val coords=MotionEvent.PointerCoords().apply{this.x=x;this.y=y;pressure=if(buttons!=0)1f else 0f;size=1f;setAxisValue(MotionEvent.AXIS_VSCROLL,scroll)}
            val event=MotionEvent.obtain(started,now,action,1,arrayOf(properties),arrayOf(coords),0,buttons,1f,1f,0,0,InputDevice.SOURCE_MOUSE,0)
            if(button!=0)MotionEvent::class.java.getMethod("setActionButton",Int::class.javaPrimitiveType).invoke(event,button)
            try{send(event)}finally{event.recycle()}
        }
        fun scroll(amount:Float){
            // End the app's native tile hover before its contents move underneath us.
            if(hovering){mouse(MotionEvent.ACTION_HOVER_EXIT);hovering=false}
            mouse(MotionEvent.ACTION_SCROLL,scroll=amount)
        }
        fun command(value:String):Boolean{
            if(value=="ping")return true
            val key=when(value){"up"->19;"down"->20;"left"->21;"right"->22;"ok"->23;"back"->4;"home"->3;"recents"->187;"settings"->176;"volumeUp"->24;"volumeDown"->25;"mute"->164;"power"->26;else->null}
            if(key!=null){
                val now=SystemClock.uptimeMillis()
                send(KeyEvent(now,now,KeyEvent.ACTION_DOWN,key,0,0,android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD))
                send(KeyEvent(now,now,KeyEvent.ACTION_UP,key,0,0,android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD))
                return true
            }
            if(value=="scrollUp" || value=="scrollDown"){scroll(if(value=="scrollUp")3f else -3f);return true}
            if(value.startsWith("scroll:")){
                val amount=value.substringAfter(':').toIntOrNull()?.takeIf{it in -6..6 && it!=0}?:return false
                scroll(amount.toFloat());return true
            }
            if(!Regex("(move|tap):[0-9]{1,4},[0-9]{1,4}").matches(value))return false
            val point=value.substringAfter(':').split(',').map{it.toInt()}
            if(point.any{it !in 0..1000})return false
            x=point[0]*(width-1)/1000f;y=point[1]*(height-1)/1000f
            if(!hovering){mouse(MotionEvent.ACTION_HOVER_ENTER);hovering=true}
            mouse(MotionEvent.ACTION_HOVER_MOVE)
            if(value.startsWith("tap:")){
                // TV browsers often ignore mouse button events inside their WebView.
                // Keep hover/wheel as mouse input, and activate the pointed control with touch.
                val started=SystemClock.uptimeMillis()
                val down=MotionEvent.obtain(started,started,MotionEvent.ACTION_DOWN,x,y,0).apply{source=InputDevice.SOURCE_TOUCHSCREEN}
                try{send(down)}finally{down.recycle()}
                Thread.sleep(12)
                val up=MotionEvent.obtain(started,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0).apply{source=InputDevice.SOURCE_TOUCHSCREEN}
                try{send(up)}finally{up.recycle()}
            }
            return true
        }
        var last=SystemClock.uptimeMillis()
        ServerSocket().apply{reuseAddress=true;bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),2);soTimeout=1000}.use{server->
            while(SystemClock.uptimeMillis()-last<120000){
                try{server.accept().use{socket->
                    socket.tcpNoDelay=true;socket.soTimeout=30000
                    val input=socket.getInputStream()
                    fun line():String?{
                        val bytes=java.io.ByteArrayOutputStream()
                        while(bytes.size()<128){val b=input.read();if(b<0)return null;if(b==10)return bytes.toString("UTF-8");bytes.write(b)}
                        return null
                    }
                    val supplied=line()?:return@use
                    if(!MessageDigest.isEqual(token.toByteArray(),supplied.toByteArray()))return@use
                    val output=socket.getOutputStream()
                    while(true){
                        val value=line()?:break
                        val ok=runCatching{command(value)}.onFailure{it.printStackTrace()}.getOrDefault(false)
                        output.write((if(ok)"OK\n" else "ERROR\n").toByteArray());output.flush()
                        last=SystemClock.uptimeMillis()
                    }
                }}catch(_:SocketTimeoutException){}catch(_:java.io.IOException){}
            }
        }
    }
}
