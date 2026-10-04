package wbs.editor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HoursTest {
    @Test
    void parsesHoursAndBlank() {
        assertEquals(2.5, Hours.parse("2.5"));
        assertEquals(1.5, Hours.parse("１．５"));
        assertEquals(1.5, Hours.parse("1,5"));
        assertEquals(1234.5, Hours.parse("1,234.5"));
        assertEquals(0.5, Hours.parse(".5"));
        assertEquals(0.0, Hours.parse("0"));
        assertNull(Hours.parse(""));
        assertNull(Hours.parse("  "));
        assertFalse(Hours.parsable("-1"));
        assertFalse(Hours.parsable("abc"));
    }

    @Test
    void formatsTrailingZeros() {
        assertEquals("2.00", Hours.format(2.0));
        assertEquals("1.25", Hours.format(1.25));
        assertEquals("0.00", Hours.format(0.0));
        assertEquals("", Hours.format(null));
    }
}
