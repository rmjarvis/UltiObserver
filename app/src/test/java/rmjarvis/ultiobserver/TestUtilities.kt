package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/// Tests for shared utility helpers and serializers.
class TestUtilities {
    /**
     * Test stable serial names for shared date/time serializers.
     */
    @Test
    fun dateTimeSerializerNames() {
        // Serializer names are part of the persisted JSON contract.
        assertEquals("LocalDateAsString", LocalDateAsStringSerializer.descriptor.serialName)
        assertEquals("LocalTimeAsString", LocalTimeAsStringSerializer.descriptor.serialName)
        assertEquals("ZoneIdAsString", ZoneIdAsStringSerializer.descriptor.serialName)
    }

    /**
     * Test shared display formatting for dates, times, durations, and counted labels.
     */
    @Test
    fun displayFormatting() {
        // Game start times use a full display format in setup and a compact format in lists.
        assertEquals("3:30 PM", formatClockTime(LocalTime.of(15, 30)))
        assertEquals("April 5, 2026", formatStartDate(LocalDate.of(2026, 4, 5)))
        assertEquals(
            "4/5/26 3:30 PM",
            formatCompactStartDateTime(LocalDate.of(2026, 4, 5), LocalTime.of(15, 30)),
        )

        // Countdown durations clamp negative inputs to zero and keep two-digit seconds.
        assertEquals("0:00", formatDuration(Duration.ofSeconds(-1)))
        assertEquals("0:32", formatDuration(Duration.ofSeconds(32)))
        assertEquals("2:05", formatDuration(Duration.ofSeconds(125)))

        // Counted text uses singular/plural nouns and only adds action counts after the first.
        assertEquals("1 timeout", countedNounPhrase(1, "timeout"))
        assertEquals("2 timeouts", countedNounPhrase(2, "timeout"))
        assertEquals("False start", countedActionLabel("False start", 0))
        assertEquals("False start (2)", countedActionLabel("False start", 2))
    }

    /**
     * Test first-character capitalization for empty, ordinary, and expanding characters.
     */
    @Test
    fun capitalization() {
        // Empty text remains empty, while lowercase and already-uppercase ASCII behave normally.
        assertEquals("", "".capitalized())
        assertEquals("First", "first".capitalized())
        assertEquals("First", "First".capitalized())

        // Numeric ordinals remain unchanged, and Unicode uppercase expansions are preserved.
        assertEquals("4th", "4th".capitalized())
        assertEquals("SSeta", "ßeta".capitalized())
    }

    /**
     * Test compact status-line cap text fitting.
     */
    @Test
    fun statusCapFontSizeFitting() {
        // Text that already fits should use the preferred font size.
        assertEquals(
            22f,
            fittedStatusCapFontSize(
                preferredFontSizeSp = 22f,
                minimumFontSizeSp = 16f,
                measuredTextWidthPx = 100,
                maxWidthPx = 100,
            ),
        )
        assertEquals(
            22f,
            fittedStatusCapFontSize(
                preferredFontSizeSp = 22f,
                minimumFontSizeSp = 16f,
                measuredTextWidthPx = 80,
                maxWidthPx = 100,
            ),
        )

        // Text with only three quarters of its measured width should use three quarters of the
        // preferred size: 24sp * 90px / 120px = 18sp.
        assertEquals(
            18f,
            fittedStatusCapFontSize(
                preferredFontSizeSp = 24f,
                minimumFontSizeSp = 16f,
                measuredTextWidthPx = 120,
                maxWidthPx = 90,
            ),
        )

        // Extreme pressure would scale 24sp to 9sp, so it should clamp to the 16sp minimum.
        assertEquals(
            16f,
            fittedStatusCapFontSize(
                preferredFontSizeSp = 24f,
                minimumFontSizeSp = 16f,
                measuredTextWidthPx = 240,
                maxWidthPx = 90,
            ),
        )

        // A not-yet-measured container should not force a temporary shrink.
        assertEquals(
            22f,
            fittedStatusCapFontSize(
                preferredFontSizeSp = 22f,
                minimumFontSizeSp = 16f,
                measuredTextWidthPx = 100,
                maxWidthPx = 0,
            ),
        )
    }

    /**
     * Test ordinal suffix formatting for ordinary values and teen exceptions.
     */
    @Test
    fun ordinalText() {
        // Numeric ordinals use English suffix rules, including teen exceptions.
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

    /**
     * Test small ordinal word formatting with numeric fallback.
     */
    @Test
    fun ordinalWordText() {
        // Small ordinal words use names for first through third and numeric ordinals after that.
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
