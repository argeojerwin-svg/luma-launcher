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
        token:String?=URI(session.url).fragment,host:String?=null,origin:String?=null):String {
        val uri=URI(session.url)
        return Socket(uri.host,uri.port).use{socket->
            socket.soTimeout=2000
            val headers="$method $path HTTP/1.1\r\nHost: ${host?:"${uri.host}:${uri.port}"}\r\n"+
                (token?.let{"X-Luma-Token: $it\r\n"}?:"")+(origin?.let{"Origin: $it\r\n"}?:"")+
                (if(method=="POST")"Content-Length: ${body.size}\r\n" else "")+"\r\n"
            socket.getOutputStream().apply{write(headers.toByteArray(Charsets.US_ASCII));write(body);flush()}
            String(socket.getInputStream().readBytes(),Charsets.UTF_8)
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
