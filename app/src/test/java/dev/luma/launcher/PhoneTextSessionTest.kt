package dev.luma.launcher

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PhoneTextSessionTest {
    private val localhost=InetAddress.getByName("127.0.0.1")
    private fun session(limit:Int=60,initial:String="",onText:(String)->Unit={}):PhoneTextSession=
        PhoneTextSession(localhost,"<script nonce=\"__CSP_NONCE__\">local page</script>","Search \"apps\"",limit,initial,onText)
    private fun request(session:PhoneTextSession,path:String="/state",method:String="GET",body:ByteArray=byteArrayOf(),
        token:String?=URI(session.url).fragment,host:String?=null,origin:String?=null,extraHeaders:Map<String,String> = emptyMap()):String {
        val uri=URI(session.url)
        return Socket(uri.host,uri.port).use{socket->
            socket.soTimeout=2000
            val headers="$method $path HTTP/1.1\r\nHost: ${host?:"${uri.host}:${uri.port}"}\r\n"+
                (token?.let{"X-Luma-Token: $it\r\n"}?:"")+(origin?.let{"Origin: $it\r\n"}?:"")+
                (if(method=="POST")"Content-Length: ${body.size}\r\n" else "")+extraHeaders.entries.joinToString(""){"${it.key}: ${it.value}\r\n"}+"\r\n"
            socket.getOutputStream().apply{write(headers.toByteArray(Charsets.US_ASCII));write(body);flush()}
            readResponse(socket)
        }
    }
    private fun readResponse(socket:Socket):String {
            // HTTP keep-alive responses end at Content-Length, not at socket EOF.
            val input=socket.getInputStream()
            val header=StringBuilder()
            while(!header.endsWith("\r\n\r\n")){
                val byte=input.read();check(byte>=0){"Incomplete response headers"}
                header.append(byte.toChar());check(header.length<=8192)
            }
            val length=header.toString().lineSequence().first{it.startsWith("Content-Length:",true)}
                .substringAfter(':').trim().toInt()
            val response=ByteArray(length);var read=0
            while(read<length){val count=input.read(response,read,length-read);check(count>0);read+=count}
            return header.toString()+String(response,Charsets.UTF_8)
    }
    @Test fun hostedRemotePreflightDoesNotAuthorizeCommands(){
        val commands=java.util.concurrent.CopyOnWriteArrayList<String>()
        PhoneTextSession(localhost,"page","remote",40,"",{},onRemote={commands.add(it);true},textTarget={null}).use{s->
            val origin=PhoneTextSession.WEB_REMOTE_ORIGIN
            val response=request(s,"/remote","OPTIONS",token=null,origin=origin,extraHeaders=mapOf("Access-Control-Request-Method" to "POST","Access-Control-Request-Headers" to "x-luma-token,content-type"))
            assertTrue(response.startsWith("HTTP/1.1 204"));assertTrue(response.contains("Access-Control-Allow-Origin: $origin\r\n"))
            assertTrue(commands.isEmpty())
            assertTrue(request(s,"/remote","POST","right".toByteArray(),token="wrong",origin=origin).startsWith("HTTP/1.1 403"))
            assertTrue(commands.isEmpty())
            assertTrue(request(s,"/remote","POST","right".toByteArray(),origin=origin).endsWith("\r\n\r\nOK"))
            assertEquals(listOf("right"),commands)
        }
    }
    @Test fun corsRejectsForeignOriginsAndUnexpectedPreflightHeaders(){
        PhoneTextSession(localhost,"page","remote",40,"",{},onRemote={true},textTarget={null}).use{s->
            val response=request(s,origin="https://untrusted.example")
            assertTrue(response.startsWith("HTTP/1.1 403"));assertFalse(response.contains("Access-Control-Allow-Origin:"))
            assertTrue(request(s,"/remote","OPTIONS",origin=PhoneTextSession.WEB_REMOTE_ORIGIN,extraHeaders=mapOf("Access-Control-Request-Method" to "POST","Access-Control-Request-Headers" to "authorization")).startsWith("HTTP/1.1 403"))
        }
    }
    @Test fun temporaryEditorsDoNotAllowHostedRemoteOrigin(){
        session().use{s->assertTrue(request(s,origin=PhoneTextSession.WEB_REMOTE_ORIGIN).startsWith("HTTP/1.1 403"))}
    }
    @Test fun hostedAppPickerPointerAndScrollRoutesStayAuthenticated(){
        var launched="";var pointer=false
        val commands=java.util.concurrent.CopyOnWriteArrayList<String>()
        PhoneTextSession(localhost,"page","remote",40,"",{},onRemote={commands.add(it);true},textTarget={null},
            onApps={"[{\"id\":\"test.app\",\"name\":\"Test\"}]"},onLaunch={launched=it;true},
            onPointer={value->if(value!=null)pointer=value;"{\"enabled\":$pointer}"},onInputReady={false}).use{s->
            val origin=PhoneTextSession.WEB_REMOTE_ORIGIN
            assertTrue(request(s,"/apps",origin=origin,token="wrong").startsWith("HTTP/1.1 403"))
            assertTrue(request(s,"/apps",origin=origin).contains("test.app"))
            assertTrue(request(s,"/launch","POST","test.app".toByteArray(),origin=origin).startsWith("HTTP/1.1 200"));assertEquals("test.app",launched)
            assertTrue(request(s,"/pointer","POST","on".toByteArray(),origin=origin).contains("\"enabled\":true"));assertTrue(pointer)
            assertTrue(request(s,"/pointer","POST","invalid".toByteArray(),origin=origin).startsWith("HTTP/1.1 400"))
            assertTrue(request(s,origin=origin).contains("\"inputReady\":false"))
            assertTrue(request(s,"/remote","POST","scroll:2".toByteArray(),origin=origin).startsWith("HTTP/1.1 200"))
            assertTrue(request(s,"/remote","POST","scroll:7".toByteArray(),origin=origin).startsWith("HTTP/1.1 400"));assertEquals(listOf("scroll:2"),commands)
        }
    }
    @Test fun sequentialRemoteRequestsStayOrderedAndAuthenticated(){
        val commands=java.util.concurrent.CopyOnWriteArrayList<String>()
        PhoneTextSession(localhost,"page","remote",40,"",{},onRemote={commands.add(it);true}).use{s->
            assertTrue(request(s,"/remote","POST","right".toByteArray()).endsWith("\r\n\r\nOK"))
            assertTrue(request(s,"/remote","POST","left".toByteArray(),token="wrong").startsWith("HTTP/1.1 403"))
            assertTrue(request(s,"/remote","POST","left".toByteArray()).endsWith("\r\n\r\nOK"))
            assertEquals(listOf("right","left"),commands)
        }
    }
    @Test fun validPhoneInputAndStateUseUtf8(){
        val received=AtomicReference<String>()
        session(initial="original",onText={received.set(it)}).use{s->
            val initial=request(s)
            assertTrue(initial.startsWith("HTTP/1.1 200"));assertTrue(initial.contains("Search \\\"apps\\\""));assertTrue(initial.contains("original"))
            assertTrue(request(s,"/text","POST","Café ✨".toByteArray(Charsets.UTF_8)).startsWith("HTTP/1.1 200"))
            assertEquals("Café ✨",received.get());assertTrue(request(s).contains("Café ✨"))
        }
    }
    @Test fun menuCommandsRequireAuthenticationAndDispatchExactlyOnce(){
        val commands=java.util.concurrent.CopyOnWriteArrayList<String>()
        PhoneTextSession(localhost,"page","remote",40,"",{},onRemote={commands.add(it);true}).use{s->
            assertTrue(request(s,"/remote","POST","controlCentre".toByteArray(),token="wrong").startsWith("HTTP/1.1 403"))
            for(command in listOf("controlCentre","appSwitcher"))assertTrue(request(s,"/remote","POST",command.toByteArray()).endsWith("\r\n\r\nOK"))
            assertEquals(listOf("controlCentre","appSwitcher"),commands)
        }
    }
    @Test fun pageHasNoUserTextOrPairingToken(){
        session(initial="private draft").use{s->
            val response=request(s,"/",token=null)
            assertTrue(response.startsWith("HTTP/1.1 200"));assertTrue(response.contains("Content-Security-Policy:"))
            assertTrue(response.contains("Cache-Control: no-store"));assertTrue(response.contains("X-Frame-Options: DENY"))
            assertFalse(response.contains("__CSP_NONCE__"));assertFalse(response.contains("private draft"));assertFalse(response.contains(URI(s.url).fragment))
        }
    }
    @Test fun requestsWithoutCurrentTokenCannotReadOrEdit(){
        val edits=AtomicReference<String>()
        session(initial="unchanged",onText={edits.set(it)}).use{s->
            assertTrue(request(s,token=null).startsWith("HTTP/1.1 403"))
            assertTrue(request(s,"/text","POST","bad".toByteArray(),token="wrong").startsWith("HTTP/1.1 403"))
            assertNull(edits.get());assertTrue(request(s).contains("unchanged"))
        }
    }
    @Test fun anotherEditorsTokenIsNotAccepted(){
        session().use{first->session().use{second->
            assertNotEquals(URI(first.url).fragment,URI(second.url).fragment)
            assertTrue(request(second,token=URI(first.url).fragment).startsWith("HTTP/1.1 403"))
        }}
    }
    @Test fun foreignHostAndOriginAreRejected(){
        session().use{s->
            assertTrue(request(s,"/",host="other.example").startsWith("HTTP/1.1 403"))
            assertTrue(request(s,"/text","POST","bad".toByteArray(),origin="https://other.example").startsWith("HTTP/1.1 403"))
        }
    }
    @Test fun tooLongTextAndMalformedUtf8DoNotChangeTheDraft(){
        session(limit=4,initial="safe").use{s->
            assertTrue(request(s,"/text","POST","12345".toByteArray()).startsWith("HTTP/1.1 413"))
            assertTrue(request(s,"/text","POST",byteArrayOf(0xC3.toByte())).startsWith("HTTP/1.1 400"))
            assertTrue(request(s).contains("safe"))
        }
    }
    @Test fun remoteTextIsSingleLineAndLocalChangesAppearInState(){
        val received=AtomicReference<String>()
        session(onText={received.set(it)}).use{s->
            request(s,"/text","POST","hello\nworld\t!\u0000".toByteArray())
            assertEquals("hello world !",received.get())
            s.updateText("typed on TV");assertTrue(request(s).contains("typed on TV"))
        }
    }
    @Test fun expiryClosesTheSession(){
        val expired=CountDownLatch(1)
        val s=PhoneTextSession(localhost,"page","title",20,"",{}, {expired.countDown()},50)
        try{assertTrue(expired.await(2,TimeUnit.SECONDS));assertFalse(s.isOpen)}finally{s.close()}
    }
    @Test fun closingDisconnectsAnIncompleteRequestAndClosesThePort(){
        val s=session();val uri=URI(s.url)
        Socket(uri.host,uri.port).use{socket->
            socket.soTimeout=2000
            socket.getOutputStream().apply{write("GET / HTTP/1.1\r\n".toByteArray());flush()}
            s.close();assertFalse(s.isOpen)
            try{assertEquals(-1,socket.getInputStream().read())}catch(_:java.net.SocketException){/* Connection reset is also closed. */}
        }
        try{Socket(uri.host,uri.port).close();fail("Closed editor must not accept connections")}catch(_:java.io.IOException){}
    }
    @Test fun lengthLimitDoesNotSplitAnEmoji(){assertEquals("",PhoneTextSession.clean("😀",1));assertEquals("😀",PhoneTextSession.clean("😀",2))}
    @Test fun submitUsesFinalTextOnceAndRejectsLaterEdits(){
        val values=java.util.concurrent.CopyOnWriteArrayList<String>()
        PhoneTextSession(localhost,"page","title",40,"",{},onSubmit={values.add(it)}).use{s->
            assertTrue(request(s,"/submit","POST","   ".toByteArray()).startsWith("HTTP/1.1 400"))
            assertTrue(values.isEmpty())
            assertTrue(request(s,"/submit","POST","Café 🎬".toByteArray(Charsets.UTF_8),token="wrong").startsWith("HTTP/1.1 403"))
            assertTrue(request(s,"/text","POST","old draft".toByteArray()).startsWith("HTTP/1.1 200"))
            assertTrue(request(s,"/submit","POST","Café 🎬".toByteArray(Charsets.UTF_8)).endsWith("Done"))
            assertEquals(listOf("Café 🎬"),values)
            assertTrue(request(s,"/submit","POST","again".toByteArray()).startsWith("HTTP/1.1 410"))
            assertTrue(request(s,"/text","POST","stale".toByteArray()).startsWith("HTTP/1.1 410"))
            assertEquals(listOf("Café 🎬"),values)
        }
    }
    @Test fun submitResponseFinishesBeforeCallbackClosesSockets(){
        lateinit var s:PhoneTextSession
        s=PhoneTextSession(localhost,"page","title",40,"",{},onSubmit={s.close()})
        try{assertTrue(request(s,"/submit","POST","ready".toByteArray()).endsWith("\r\n\r\nDone"))}
        finally{s.close()}
    }
}
