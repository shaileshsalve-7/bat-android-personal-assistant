package dev.campusevents.assistant

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class EventParserTest {
    @Test fun detectsHighPriorityWorkshopAndFields() {
        val event = EventParser.parse("AI workshop on 18/10/2030 at 3:30 PM. Venue: Innovation Hall. Register https://example.org/r", "CSE 2027")!!
        assertEquals(2, event.priority); assertEquals("Innovation Hall", event.location); assertEquals("https://example.org/r", event.link)
        assertFalse(event.needsDateConfirmation)
    }
    @Test fun ignoresOrdinaryConversation() { assertNull(EventParser.parse("Can you send the assignment later?", "Saved Contact")) }
    @Test fun retainsDatedUnknownEventAsTentative() { assertTrue(EventParser.parse("Career talk coming soon", "College group")!!.needsDateConfirmation) }
    @Test fun voiceRequiresNamedEventDateAndTime() {
        assertTrue(EventParser.isCompleteVoiceEvent("AI workshop on 18 October at 3 PM in Innovation Hall"))
        assertFalse(EventParser.isCompleteVoiceEvent("workshop tomorrow"))
        assertFalse(EventParser.isCompleteVoiceEvent("add this"))
    }
    @Test fun capturesExplicitAllDayDateRangeWithInclusiveFinalDate() {
        val event = EventParser.parse("AI workshop runs from 7th through 13th August 2030", "College group")!!
        assertTrue(event.allDay)
        assertEquals(LocalDate.of(2030, 8, 7).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), event.startMillis)
        // Calendar DTEND is exclusive, so the inclusive 13th ends at midnight on the 14th.
        assertEquals(LocalDate.of(2030, 8, 14).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), event.endMillis)
    }
    @Test fun capturesDurationAsOneTimedSpanningEvent() {
        val event = EventParser.parse("AI hackathon on 07/09/2030 at 10 AM for five days", "College group")!!
        assertFalse(event.allDay)
        val zone = ZoneId.systemDefault()
        assertEquals(LocalDateTime.of(LocalDate.of(2030, 9, 7), LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli(), event.startMillis)
        assertEquals(LocalDateTime.of(LocalDate.of(2030, 9, 11), LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli(), event.endMillis)
    }
    @Test fun supportsDayAfterTomorrowAndDottedPm() {
        val event = EventParser.parse("AI workshop day after tomorrow at 4 p.m.", "College group")!!
        assertFalse(event.needsDateConfirmation)
        assertEquals(16, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.startMillis), ZoneId.systemDefault()).hour)
    }
}
