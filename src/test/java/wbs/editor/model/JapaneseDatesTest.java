package wbs.editor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class JapaneseDatesTest {
    @Test
    void parsesDirectInputFormats() {
        LocalDate expected = LocalDate.of(2026, 10, 6);
        assertEquals(expected, JapaneseDates.parseInput("20261006"));
        assertEquals(expected, JapaneseDates.parseInput("2026-10-06"));
        assertEquals(expected, JapaneseDates.parseInput("2026/10/6"));
        assertEquals(expected, JapaneseDates.parseInput("2026.10.06"));
        assertEquals(expected, JapaneseDates.parseInput("2026年10月6日"));
    }

    @Test
    void rejectsInvalidDate() {
        assertThrows(DateTimeParseException.class, () -> JapaneseDates.parseInput("20261301"));
        assertThrows(DateTimeParseException.class, () -> JapaneseDates.parseInput(""));
    }
}
