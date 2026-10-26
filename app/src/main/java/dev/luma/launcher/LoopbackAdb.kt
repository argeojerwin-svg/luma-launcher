package dev.luma.launcher

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Small bounded client for this device's existing debug transport. Never connects off-device. */
class LoopbackAdb(private val port:Int=5555){
    fun request(service:String,completionMarker:String?=null):String{
        require(service=="root:" || service.startsWith("shell:"))
        Socket().use{socket->
            socket.connect(InetSocketAddress("127.0.0.1",port),2000);socket.soTimeout=5000
            val input=DataInputStream(socket.getInputStream())
            fun send(command:Int,arg0:Int,arg1:Int,payload:ByteArray=byteArrayOf()){
                val header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(command).putInt(arg0).putInt(arg1).putInt(payload.size)
                    .putInt(payload.sumOf{it.toInt() and 255}).putInt(command.inv()).array()
                socket.getOutputStream().apply{write(header);write(payload);flush()}
            }
            fun read():Packet{
                val bytes=ByteArray(24);input.readFully(bytes)
                val header=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val command=header.int;val arg0=header.int;val arg1=header.int
                val size=header.int;val checksum=header.int;val magic=header.int
                if(size !in 0..65536 || magic!=command.inv())throw IOException("Invalid local debug packet")
                val payload=ByteArray(size);input.readFully(payload)
                if(checksum!=payload.sumOf{it.toInt() and 255})throw IOException("Invalid local debug checksum")
                return Packet(command,arg0,arg1,payload)
            }
            send(CNXN,0x01000000,4096,"host::\u0000".toByteArray())
            val connection=read()
            if(connection.command==AUTH)throw IOException("Local debugging requires authentication; automatic setup is unavailable")
            if(connection.command!=CNXN)throw IOException("Local debugging handshake failed")
            // A raw shell avoids keeping a terminal open in the detached IR listener.
            val destination=if(service.startsWith("shell:"))"shell,raw:"+service.removePrefix("shell:")else service
            send(OPEN,1,0,(destination+"\u0000").toByteArray(Charsets.UTF_8))
            var remote=0
            val output=ByteArrayOutputStream()
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
            repeat(64){
                if(System.nanoTime()>deadline)throw IOException("Local debug response timed out")
                val packet=read()
                if(packet.arg1!=1 || (remote!=0 && packet.arg0!=remote))throw IOException("Invalid local debug stream")
                when(packet.command){
                    OKAY->{if(packet.arg0==0)throw IOException("Invalid local debug stream");remote=packet.arg0}
                    WRTE->{
                        if(remote==0 || output.size()+packet.payload.size>8192)throw IOException("Local debug response too large")
                        output.write(packet.payload);send(OKAY,1,remote)
                        // P6 shells can keep the connection alive in background children.
                        // Setup explicitly prints this marker only after its checks succeed.
                        if(completionMarker!=null && output.toString("UTF-8").trimEnd().endsWith(completionMarker)){
                            send(CLSE,1,remote);return output.toString("UTF-8")
                        }
                    }
                    CLSE->{send(CLSE,1,packet.arg0);return output.toString("UTF-8")}
                    else->throw IOException("Unexpected local debug packet")
                }
            }
            throw IOException("Local debug response did not finish")
        }
    }
    private data class Packet(val command:Int,val arg0:Int,val arg1:Int,val payload:ByteArray)
    companion object {
        const val CNXN=0x4e584e43
        const val AUTH=0x48545541
        const val OPEN=0x4e45504f
        const val OKAY=0x59414b4f
        const val WRTE=0x45545257
        const val CLSE=0x45534c43
    }
}
