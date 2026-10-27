package dev.luma.launcher
import org.junit.Assert.*
import org.junit.Test

class KeyboardTextCommandTest {
    @Test fun statusIgnoresDiagnosticOutputButRejectsChangedApp(){
        assertTrue(KeyboardTextCommand.succeeded("diagnostic\nLUMA_INPUT_DONE\r\n"))
        assertFalse(KeyboardTextCommand.succeeded("APP_CHANGED"))
        assertFalse(KeyboardTextCommand.succeeded("LUMA_INPUT_DONE\nAPP_CHANGED"))
    }
    @Test fun quotesShellMetacharacters(){
        val command=KeyboardTextCommand.build("com.example.tv","it's \$(id); `id`")
        assertTrue(command.contains("'it'\\''s \$(id); `id`'"))
        assertTrue(command.contains("*\" com.example.tv/\"*"))
    }
    @Test fun literalPercentIsNotConvertedToSpace(){
        val command=KeyboardTextCommand.build("com.example.tv","100%s")
        assertTrue(command.contains("input text '100'; input text '%'; input text 's'"))
    }
    @Test fun appleTvSearchClearsOldQueryWithoutSelectingAResult(){
        val command=KeyboardTextCommand.build("com.apple.atve.androidtv.appletv","Silo")
        assertTrue(command.contains("input keyevent 67"))
        assertTrue(command.contains("input text 'Silo'"))
        assertFalse(command.contains("input keyevent 66"))
    }
    @Test fun rejectsUnexpectedDestinationsAndUnsupportedFallbackText(){
        assertThrows(IllegalArgumentException::class.java){KeyboardTextCommand.build("x;id","test")}
        assertThrows(IllegalArgumentException::class.java){KeyboardTextCommand.build("com.example","café")}
        assertThrows(IllegalArgumentException::class.java){KeyboardTextCommand.build("com.example","a".repeat(257))}
    }
}
