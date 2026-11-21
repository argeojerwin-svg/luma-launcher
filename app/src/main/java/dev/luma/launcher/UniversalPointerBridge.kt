package dev.luma.launcher

import android.app.UiAutomation
import android.graphics.Rect
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest

/** Opt-in shell helper. Retrieves geometry only; never reads node text or injects input. */
object UniversalPointerBridge {
    @JvmStatic fun main(args:Array<String>){
        if(args.size!=2 || !Regex("[a-f0-9]{48}").matches(args[0]))return
        val port=args[1].toIntOrNull()?.takeIf{it in 1024..65535}?:return
        val thread=HandlerThread("Luma pointer bounds").apply{start()}
        val scanner=java.util.concurrent.Executors.newSingleThreadExecutor()
        var automation:UiAutomation?=null
        var snapshot=emptyList<Rect>()
        var scanAt=0L
        val lastQuery=java.util.concurrent.atomic.AtomicLong(0)
        val lock=Any()
        var queued=false
        fun scan(){
            val api=automation?:return
            val start=SystemClock.uptimeMillis()
            val targets=ArrayList<Rect>()
            var visited=0
            fun visit(node:AccessibilityNodeInfo?,depth:Int){
                if(node==null)return
                try{
                    if(++visited>300 || depth>24 || SystemClock.uptimeMillis()-start>250 || node.isPassword || node.isEditable)return
                    val rect=Rect();node.getBoundsInScreen(rect)
                    if(node.isVisibleToUser && node.isEnabled && (node.isClickable || node.isFocusable) && rect.width()>8 && rect.height()>8)targets.add(rect)
                    for(i in 0 until node.childCount){
                        if(visited>=300 || SystemClock.uptimeMillis()-start>250)break
                        visit(node.getChild(i),depth+1)
                    }
                }finally{@Suppress("DEPRECATION") node.recycle()}
            }
            runCatching{
                val root=api.rootInActiveWindow
                if(root?.packageName=="dev.luma.launcher"){@Suppress("DEPRECATION") root.recycle()}
                else visit(root,0)
            }.onFailure{targets.clear()}
            synchronized(lock){snapshot=targets;scanAt=SystemClock.uptimeMillis();queued=false}
        }
        fun schedule(){
            synchronized(lock){if(queued)return;queued=true}
            scanner.execute{scan()}
        }
        try{
            val connectionType=Class.forName("android.app.IUiAutomationConnection")
            val connection=Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance()
            automation=UiAutomation::class.java.getDeclaredConstructor(Looper::class.java,connectionType).apply{isAccessible=true}
                .newInstance(thread.looper,connection) as UiAutomation
            UiAutomation::class.java.getDeclaredMethod("connect",Int::class.javaPrimitiveType).apply{isAccessible=true}.invoke(automation,1)
            automation.serviceInfo=automation.serviceInfo.apply{
                eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
                notificationTimeout=100
            }
            automation.setOnAccessibilityEventListener{event->
                // Our non-focusable drawing windows must not invalidate their own target.
                val own=event.packageName?.toString()=="dev.luma.launcher"
                val type=event.className?.toString().orEmpty()
                if(own && !type.endsWith("Activity") && !type.contains("Dialog"))return@setOnAccessibilityEventListener
                if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED){
                    val now=SystemClock.uptimeMillis()
                    val reset=event.eventType!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    if(reset)synchronized(lock){snapshot=emptyList();scanAt=0}
                    if(now-lastQuery.get()<1000 && (reset || now-scanAt>1000))schedule()
                }
            }
            ServerSocket().apply{reuseAddress=true;bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),1);soTimeout=10000}.use{server->
                server.accept().use{socket->
                    socket.tcpNoDelay=true;socket.soTimeout=10000
                    val input=socket.getInputStream().bufferedReader();val output=socket.getOutputStream()
                    val supplied=input.readLine()?:return
                    if(!MessageDigest.isEqual(args[0].toByteArray(),supplied.toByteArray()))return
                    while(true){
                        val line=input.readLine()?:break
                        if(line.length>40)break
                        val p=line.split(',').mapNotNull{it.toIntOrNull()}
                        if(p.size!=2 || p.any{it !in 0..16384})break
                        val now=SystemClock.uptimeMillis();lastQuery.set(now)
                        val result=synchronized(lock){
                            if(now-scanAt>2500)null else snapshot.filter{it.contains(p[0],p[1]) && it.width()<1500 && it.height()<800}
                                .minByOrNull{it.width().toLong()*it.height()}
                        }
                        val refresh=synchronized(lock){now-scanAt>1000 || snapshot.isEmpty() && now-scanAt>120}
                        if(refresh)schedule()
                        val response=result?.let{"${it.left},${it.top},${it.right},${it.bottom}"}?:"NONE"
                        output.write((response+"\n").toByteArray());output.flush()
                    }
                }
            }
        }catch(_:Exception){/* Optional geometry is unavailable; normal input uses its own helper. */}
        finally{
            automation?.let{runCatching{UiAutomation::class.java.getMethod("disconnect").invoke(it)}}
            scanner.shutdownNow()
            thread.quitSafely()
        }
    }
}
