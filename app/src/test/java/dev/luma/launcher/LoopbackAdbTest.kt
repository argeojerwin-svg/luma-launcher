package dev.luma.launcher

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LoopbackAdbTest {
    private data class Message(val command:Int,val arg0:Int,val arg1:Int,val data:String)
    private fun read(socket:Socket):Message{
        val input=DataInputStream(socket.getInputStream());val bytes=ByteArray(24);input.readFully(bytes)
        val header=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val command=header.int;val arg0=header.int;val arg1=header.int;val size=header.int
        val data=ByteArray(size);input.readFully(data)
        return Message(command,arg0,arg1,String(data,Charsets.UTF_8))
    }
    private fun send(socket:Socket,command:Int,arg0:Int=0,arg1:Int=0,text:String="",badChecksum:Boolean=false){
        val data=text.toByteArray(Charsets.UTF_8)
        val bytes=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(command).putInt(arg0).putInt(arg1)
            .putInt(data.size).putInt(if(badChecksum)999 else data.sumOf{it.toInt() and 255}).putInt(command.inv()).array()
        socket.getOutputStream().apply{write(bytes);write(data);flush()}
    }
    private fun withServer(serve:(Socket)->Unit,test:(LoopbackAdb)->Unit){
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use{server->
            server.soTimeout=3000
            val worker=Executors.newSingleThreadExecutor{Thread(it).apply{isDaemon=true}}
            val result=worker.submit{server.accept().use{it.soTimeout=3000;serve(it)}}
            try{test(LoopbackAdb(server.localPort));result.get(5,TimeUnit.SECONDS)}finally{worker.shutdownNow()}
        }
    }
    @Test fun shellHandshakeAndWriteAcknowledgements(){
        withServer({socket->
            val hello=read(socket);assertEquals(LoopbackAdb.CNXN,hello.command);assertEquals(0x01000000,hello.arg0)
            send(socket,LoopbackAdb.CNXN,0x01000000,4096,"device::\u0000")
            val request=read(socket);assertEquals(LoopbackAdb.OPEN,request.command);assertEquals("shell,raw:id -u\u0000",request.data)
            send(socket,LoopbackAdb.OKAY,8,1)
            send(socket,LoopbackAdb.WRTE,8,1,"0\n")
            val ack=read(socket);assertEquals(LoopbackAdb.OKAY,ack.command);assertEquals(1,ack.arg0);assertEquals(8,ack.arg1)
            send(socket,LoopbackAdb.CLSE,8,1);assertEquals(LoopbackAdb.CLSE,read(socket).command)
        }){assertEquals("0\n",it.request("shell:id -u"))}
    }
    @Test fun completedSetupClosesWithoutWaitingForBackgroundChildren(){
        withServer({socket->
            read(socket);send(socket,LoopbackAdb.CNXN,0x01000000,4096,"device::\u0000")
            read(socket);send(socket,LoopbackAdb.OKAY,8,1)
            send(socket,LoopbackAdb.WRTE,8,1,"running\nLUMA_MENU_")
            assertEquals(LoopbackAdb.OKAY,read(socket).command)
            send(socket,LoopbackAdb.WRTE,8,1,"INITIALIZED")
            assertEquals(LoopbackAdb.OKAY,read(socket).command)
            assertEquals(LoopbackAdb.CLSE,read(socket).command)
        }){assertEquals("running\nLUMA_MENU_INITIALIZED",it.request("shell:setup","LUMA_MENU_INITIALIZED"))}
    }
    @Test fun authenticationIsNotBypassedOrSilentlyAuthorized(){
        withServer({socket->read(socket);send(socket,LoopbackAdb.AUTH,1,0,"challenge")}){
            assertTrue(assertThrows(IOException::class.java){it.request("root:")}.message!!.contains("authentication"))
        }
    }
    @Test fun corruptPacketsAreRejected(){
        withServer({socket->read(socket);send(socket,LoopbackAdb.CNXN,0x01000000,4096,"device",true)}){
            assertThrows(IOException::class.java){it.request("shell:id -u")}
        }
    }
    @Test fun oversizedPacketsAreRejectedBeforeAllocation(){
        withServer({socket->
            read(socket)
            val header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(LoopbackAdb.CNXN).putInt(0).putInt(0)
                .putInt(Int.MAX_VALUE).putInt(0).putInt(LoopbackAdb.CNXN.inv()).array()
            socket.getOutputStream().apply{write(header);flush()}
        }){assertThrows(IOException::class.java){it.request("shell:id -u")}}
    }
}
