package dev.blaiseagent.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolCallParserTest {
    @Test
    fun parsesAJsonToolCallEmittedAsPlainText() {
        val parsed = parseTextToolCall("{\"name\":\"test_woovi_connection\",\"arguments\":{}}")

        assertEquals("test_woovi_connection", parsed?.name)
        assertEquals("{}", parsed?.arguments)
    }

    @Test
    fun ignoresOrdinaryAssistantText() {
        assertNull(parseTextToolCall("The connection is configured and ready."))
    }

    @Test
    fun rejectsToolCallWithoutAnObjectArgumentsValue() {
        assertNull(parseTextToolCall("{\"name\":\"test_woovi_connection\",\"arguments\":\"secret\"}"))
    }
}
