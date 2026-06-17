package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Test

/// Tests for shared utility formatting helpers.
class TestUtilities {
    /// Test stable serial names for shared date/time serializers.
    @Test
    fun dateTimeSerializerNames() {
        assertEquals("LocalDateAsString", LocalDateAsStringSerializer.descriptor.serialName)
        assertEquals("LocalTimeAsString", LocalTimeAsStringSerializer.descriptor.serialName)
        assertEquals("ZoneIdAsString", ZoneIdAsStringSerializer.descriptor.serialName)
    }

    /// Test ordinal suffix formatting for ordinary values and teen exceptions.
    @Test
    fun ordinalText() {
        val expectedOrdinals = mapOf(
            1 to "1st",
            2 to "2nd",
            3 to "3rd",
            4 to "4th",
            10 to "10th",
            11 to "11th",
            12 to "12th",
            13 to "13th",
            20 to "20th",
            21 to "21st",
            22 to "22nd",
            23 to "23rd",
            24 to "24th",
            101 to "101st",
            111 to "111th",
            112 to "112th",
            113 to "113th",
            121 to "121st",
        )

        for ((value, expected) in expectedOrdinals) {
            assertEquals(expected, value.ordinalText())
        }
    }

    /// Test small ordinal word formatting with numeric fallback.
    @Test
    fun ordinalWordText() {
        val expectedOrdinals = mapOf(
            1 to "first",
            2 to "second",
            3 to "third",
            4 to "4th",
            10 to "10th",
            11 to "11th",
            12 to "12th",
            13 to "13th",
            21 to "21st",
            22 to "22nd",
            23 to "23rd",
        )

        for ((value, expected) in expectedOrdinals) {
            assertEquals(expected, value.ordinalWordText())
        }
    }
}
