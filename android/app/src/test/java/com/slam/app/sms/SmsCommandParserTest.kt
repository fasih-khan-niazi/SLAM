package com.slam.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsCommandParserTest {
    @Test
    fun parsesLocateCommandCaseInsensitively() {
        assertEquals(SlamCommand("1234", "LOCATE"), SmsCommandParser.parse("slam 1234 locate"))
    }

    @Test
    fun rejectsOtherCommandsAndInvalidPins() {
        assertNull(SmsCommandParser.parse("SLAM 1234 DELETE"))
        assertNull(SmsCommandParser.parse("SLAM 12 LOCATE"))
    }
}
