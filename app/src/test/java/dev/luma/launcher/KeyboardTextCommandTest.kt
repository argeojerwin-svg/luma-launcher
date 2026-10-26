package dev.luma.launcher
import org.junit.Assert.*
import org.junit.Test

class KeyboardTextCommandTest {
    @Test fun quotesShellMetacharacters(){
        val command=KeyboardTextCommand.build("com.example.tv","it's \$(id); `id`")
        assertTrue(command.contains("'it'\\''s \$(id); `id`'"))
        assertTrue(command.contains("*\" com.example.tv/\"*"))
    }
    @Test fun literalPercentIsNotConvertedToSpace(){
        val command=KeyboardTextCommand.build("com.example.tv","100%s")
        assertTrue(command.contains("input text '100'; input text '%'; input text 's'"))
    }
    @Test fun rejectsUnexpectedDestinationsAndUnsupportedFallbackText(){
        assertThrows(IllegalArgumentException::class.java){KeyboardTextCommand.build("x;id","test")}
        assertThrows(IllegalArgumentException::class.java){KeyboardTextCommand.build("com.example","café")}
        assertThrows(IllegalArgumentException::class.java){KeyboardTextCommand.build("com.example","a".repeat(257))}
    }
}
