package dev.luma.launcher

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A temporary, token-protected LAN editor. No Android, cloud service, storage or logging. */
class PhoneTextSession(
    address:InetAddress,
    private val page:String,
    private val title:String,
    val limit:Int,
    initial:String,
    private val onText:(String)->Unit,
    private val onExpired:()->Unit={},
    lifetimeMillis:Long=10*60*1000L,
    private val onSubmit:(String)->Unit={}
):Closeable {
    init{require(limit in 1..2048);require(lifetimeMillis>0);require(address is java.net.Inet4Address)}
    private val token=randomToken(24)
    private val nonce=randomToken(16)
    private val deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(lifetimeMillis)
    private val server=ServerSocket().apply{reuseAddress=true;bind(InetSocketAddress(address,0));soTimeout=500}
    private val sockets=Collections.synchronizedSet(mutableSetOf<Socket>())
    private val workers=ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,ArrayBlockingQueue<Runnable>(8),
        {job->Thread(job,"Luma phone request").apply{isDaemon=true}})
    private val origin="http://${address.hostAddress}:${server.localPort}"
    val url="$origin/#$token"
    @Volatile private var active=true
    @Volatile private var text=clean(initial,limit)
    private val edits=Any()
    private var submitted=false
    val isOpen:Boolean get()=active && System.nanoTime()<deadline

    init {
        Thread({
            try {
                while(isOpen){
                    try {
                        val socket=server.accept().apply{soTimeout=2000}
                        sockets.add(socket)
                        try { workers.execute{socket.use{runCatching{serve(it)}};sockets.remove(socket)} }
                        catch(_:RuntimeException){sockets.remove(socket);socket.close()}
                    }catch(_:SocketTimeoutException){/* Check expiry without holding a connection. */}
                }
            }catch(_:java.io.IOException){/* Closing the editor closes the listening socket. */}
            finally {
                val expired=active && System.nanoTime()>=deadline
                close()
                if(expired)onExpired()
            }
        },"Luma phone typing").apply{isDaemon=true;start()}
    }

    fun updateText(value:String){if(isOpen)text=clean(value,limit)}

    private fun serve(socket:Socket){
        val input=socket.getInputStream()
        val header=readHeader(input)?:return
        val lines=header.split("\r\n")
        val request=lines.first().split(' ')
        if(request.size!=3){reply(socket,400,"Bad request");return}
        val headers=linkedMapOf<String,String>()
        for(line in lines.drop(1).filter{it.isNotEmpty()}){
            val split=line.indexOf(':')
            if(split<=0){reply(socket,400,"Bad header");return}
            val key=line.substring(0,split).trim().lowercase()
            if(headers.put(key,line.substring(split+1).trim())!=null){reply(socket,400,"Duplicate header");return}
        }
        if(!isOpen){reply(socket,410,"This code has expired");return}
        val method=request[0];val path=request[1]
        // Consume a bounded rejected POST body before closing the socket. Otherwise the TCP
        // connection can reset and the phone never receives the useful 403 response.
        fun reject(message:String){
            val length=headers["content-length"]?.toIntOrNull()
            if(method=="POST" && length!=null && length in 0..limit*4){
                var remaining=length
                val buffer=ByteArray(1024)
                while(remaining>0){val count=input.read(buffer,0,minOf(buffer.size,remaining));if(count<0)break;remaining-=count}
            }
            reply(socket,403,message)
        }
        if(headers["host"]!=origin.removePrefix("http://")){reject("Host not allowed");return}
        if(method=="GET" && path=="/"){
            reply(socket,200,page.replace("__CSP_NONCE__",nonce),"text/html; charset=utf-8");return
        }
        val supplied=headers["x-luma-token"]?:""
        if(!MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8),supplied.toByteArray(Charsets.UTF_8))){
            reject("Scan the current QR code on your TV");return
        }
        if(headers["origin"]!=null && headers["origin"]!=origin){reject("Origin not allowed");return}
        if(method=="GET" && path=="/state"){
            reply(socket,200,"{\"title\":${json(title)},\"limit\":$limit,\"text\":${json(text)}}","application/json; charset=utf-8");return
        }
        if(method!="POST" || path !in listOf("/text","/submit")){reply(socket,404,"Not found");return}
        val length=headers["content-length"]?.toIntOrNull()
        if(headers.containsKey("transfer-encoding") || length==null || length !in 0..limit*4){
            reply(socket,413,"Text is too long");return
        }
        val bytes=ByteArray(length);var read=0
        while(read<length){val count=input.read(bytes,read,length-read);if(count<0)return;read+=count}
        val value=try{Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()}
            catch(_:java.nio.charset.CharacterCodingException){reply(socket,400,"Invalid text");return}
        if(value.length>limit){reply(socket,413,"Text is too long");return}
        if(!isOpen){reply(socket,410,"This code has expired");return}
        val next=clean(value,limit)
        synchronized(edits){
            if(submitted || !isOpen){reply(socket,410,"This code has ended");return}
            if(path=="/submit"){
                if(next.trim().isEmpty()){reply(socket,400,"Enter some text");return}
                // Finish the response before the dialog callback closes this server's sockets.
                reply(socket,200,"Done")
                submitted=true;text=next
                onSubmit(next)
            }else{
                text=next;onText(next)
                reply(socket,200,"Text sent")
            }
        }
    }

    private fun readHeader(input:InputStream):String?{
        val buffer=ByteArrayOutputStream()
        var ending=0
        while(buffer.size()<8192){
            val value=input.read();if(value<0)return null
            buffer.write(value)
            ending=when{ending==0 && value==13->1;ending==1 && value==10->2;ending==2 && value==13->3;ending==3 && value==10->4;value==13->1;else->0}
            if(ending==4)return String(buffer.toByteArray(),Charsets.ISO_8859_1)
        }
        return null
    }
    private fun reply(socket:Socket,status:Int,body:String,type:String="text/plain; charset=utf-8"){
        val bytes=body.toByteArray(Charsets.UTF_8)
        val phrase=when(status){200->"OK";400->"Bad Request";403->"Forbidden";404->"Not Found";410->"Gone";else->"Payload Too Large"}
        val headers="HTTP/1.1 $status $phrase\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n"+
            "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\n"+
            "Content-Security-Policy: default-src 'none'; script-src 'nonce-$nonce'; style-src 'nonce-$nonce'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'\r\n"+
            "Connection: close\r\n\r\n"
        socket.getOutputStream().apply{write(headers.toByteArray(Charsets.US_ASCII));write(bytes);flush()}
    }
    @Synchronized override fun close(){
        if(!active)return
        active=false;text=""
        runCatching{server.close()}
        synchronized(sockets){sockets.toList().forEach{runCatching{it.close()}};sockets.clear()}
        workers.shutdownNow()
    }
    companion object {
        private fun randomToken(bytes:Int)=ByteArray(bytes).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it.toInt() and 255)}
        internal fun clean(value:String,limit:Int):String{
            val result=value.replace('\r',' ').replace('\n',' ').replace('\t',' ').filterNot{it.isISOControl()}.take(limit)
            return if(result.lastOrNull()?.isHighSurrogate()==true)result.dropLast(1)else result
        }
        private fun json(value:String):String="\""+buildString{
            value.forEach{char->when(char){'"'->append("\\\"");'\\'->append("\\\\");'\n'->append("\\n");'\r'->append("\\r");'\t'->append("\\t");else->if(char<' ')append("\\u%04x".format(char.code))else append(char)}}
        }+"\""
    }
}
