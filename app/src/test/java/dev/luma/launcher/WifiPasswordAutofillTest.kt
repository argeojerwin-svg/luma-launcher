package dev.luma.launcher

import org.junit.Assert.*
import org.junit.Test

class WifiPasswordAutofillTest {
    @Test fun validatesWifiPassphrasesWithoutTrimmingOrAcceptingControlCharacters(){
        assertTrue(WifiPasswordAutofill.valid("a".repeat(8)))
        assertTrue(WifiPasswordAutofill.valid("a".repeat(63)))
        assertTrue(WifiPasswordAutofill.valid(" abcdef "))
        assertTrue(WifiPasswordAutofill.valid("aF01".repeat(16)))
        assertFalse(WifiPasswordAutofill.valid("short"))
        assertFalse(WifiPasswordAutofill.valid("g".repeat(64)))
        assertFalse(WifiPasswordAutofill.valid("password\n"))
    }
    @Test fun requiresTheSpecificProjectorWifiPasswordEditor(){
        // This OEM incorrectly marks its Wi-Fi password editor as plain text.
        assertTrue(WifiPasswordAutofill.matches("com.yhk.setting","edit_password"))
        assertFalse(WifiPasswordAutofill.matches("com.example.login","edit_password"))
        assertFalse(WifiPasswordAutofill.matches("com.yhk.setting","item_net_hotspot_pwd"))
        assertFalse(WifiPasswordAutofill.matches(null,null))
    }
}
