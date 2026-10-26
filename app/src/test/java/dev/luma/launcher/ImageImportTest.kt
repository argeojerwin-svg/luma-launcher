package dev.luma.launcher
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class ImageImportTest{
    @Test fun preservesImageBytes(){val bytes=ByteArray(20000){it.toByte()};val out=ByteArrayOutputStream();ImageImport.copy(ByteArrayInputStream(bytes),out);assertArrayEquals(bytes,out.toByteArray())}
    @Test fun boundsDiskUse(){assertThrows(IOException::class.java){ImageImport.copy(ByteArrayInputStream(ByteArray(101)),ByteArrayOutputStream(),100)}}
    @Test fun rejectsEmptyImages(){assertThrows(IOException::class.java){ImageImport.copy(ByteArrayInputStream(byteArrayOf()),ByteArrayOutputStream())}}
}
