package dev.luma.launcher

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Geometry runs on a separate local channel and never waits in the input path. */
object UniversalPointer {
    private val worker=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var point:Pair<Int,Int>?=null
    @Volatile private var generation=0
    private var socket:Socket?=null
    private var reader:java.io.BufferedReader?=null
    private var output:java.io.OutputStream?=null
    private var retryAt=0L
    private var lastChecked:Pair<Int,Int>?=null
    private var misses=0
    fun enabled(context:Context)=context.getSharedPreferences("phone-remote-private",Context.MODE_PRIVATE).getBoolean("universalPointer",false)
    fun settings(context:Context,value:Boolean?):String{
        if(value!=null){
            context.getSharedPreferences("phone-remote-private",Context.MODE_PRIVATE).edit().putBoolean("universalPointer",value).apply()
            if(!value)close()
        }
        return "{\"enabled\":${enabled(context)}}"
    }
    fun move(context:Context,x:Int,y:Int){
        if(!enabled(context))return
        point=x to y
        pump(context.applicationContext)
    }
    private fun pump(app:Context){
        if(!busy.compareAndSet(false,true))return
        val request=generation
        worker.execute{
            val p=point
            var followUp=false
            try{
                if(p==null)return@execute
                if(p!=lastChecked){lastChecked=p;misses=0}
                val now=SystemClock.uptimeMillis()
                if(socket==null && now<retryAt)return@execute
                if(socket==null){
                    retryAt=now+10000
                    val token=ByteArray(24).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it.toInt() and 255)}
                    val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use{it.localPort}
                    val path="'"+app.applicationInfo.sourceDir.replace("'","'\\''")+"'"
                    LoopbackAdb().request("shell:CLASSPATH=$path nohup app_process /system/bin dev.luma.launcher.UniversalPointerBridge $token $port >/dev/null 2>&1 </dev/null & printf LUMA_POINTER_STARTED","LUMA_POINTER_STARTED")
                    val deadline=SystemClock.uptimeMillis()+3000
                    while(socket==null && SystemClock.uptimeMillis()<deadline && request==generation){
                        runCatching{
                            val next=Socket().apply{tcpNoDelay=true;connect(InetSocketAddress("127.0.0.1",port),100);soTimeout=250}
                            socket=next;reader=next.getInputStream().bufferedReader();output=next.getOutputStream()
                            output!!.write((token+"\n").toByteArray());output!!.flush()
                        }.onFailure{Thread.sleep(50)}
                    }
                }
                val target=output?:return@execute
                target.write(("${p.first},${p.second}\n").toByteArray());target.flush()
                val line=reader?.readLine()?:error("Geometry helper disconnected")
                val coordinates=line.split(',').mapNotNull{it.toIntOrNull()}
                val rect=if(coordinates.size==4)Rect(coordinates[0],coordinates[1],coordinates[2],coordinates[3])else null
                misses=if(p==lastChecked && rect==null)misses+1 else 0
                lastChecked=p
                followUp=rect==null && misses<5
                main.post{if(request==generation && point==p)PhoneMouseCursor.external(rect)}
            }catch(_:Exception){
                disconnect();misses++
                followUp=misses<3
                retryAt=if(followUp)0 else SystemClock.uptimeMillis()+10000
                main.post{if(request==generation && point==p)PhoneMouseCursor.external(null)}
            }finally{
                busy.set(false)
                if(request==generation && point!=null){
                    if(point!=p)main.post{if(request==generation)pump(app)}
                    else if(followUp)main.postDelayed({if(request==generation)pump(app)},90)
                }
            }
        }
    }
    private fun disconnect(){runCatching{socket?.close()};socket=null;reader=null;output=null}
    fun pause(){generation++;point=null;PhoneMouseCursor.suspendHover()}
    fun close(){generation++;point=null;worker.execute{disconnect()};main.post{PhoneMouseCursor.external(null,true)}}
}
