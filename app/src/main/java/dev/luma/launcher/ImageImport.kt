package dev.luma.launcher
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

internal object ImageImport{
    fun copy(input:InputStream,output:OutputStream,limit:Long=32L*1024*1024){
        val buffer=ByteArray(16384);var total=0L
        while(true){
            if(Thread.currentThread().isInterrupted)throw IOException("Image import cancelled")
            val count=input.read(buffer);if(count<0)break
            total+=count;if(total>limit)throw IOException("Image is too large")
            output.write(buffer,0,count)
        }
        if(total==0L)throw IOException("Empty image")
    }
}
