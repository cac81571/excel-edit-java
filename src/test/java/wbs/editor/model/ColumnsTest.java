package wbs.editor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ColumnsTest {
    @Test
    void parsesLettersAndNumbers() {
        assertEquals(0, Columns.parse("A"));
        assertEquals(0, Columns.parse("1"));
        assertEquals(5, Columns.parse("F"));
        assertEquals(5, Columns.parse("6"));
        assertEquals(25, Columns.parse("Z"));
        assertEquals(26, Columns.parse("AA"));
        assertEquals("A", Columns.name(0));
        assertEquals("F", Columns.name(5));
        assertEquals("Z", Columns.name(25));
        assertEquals("AA", Columns.name(26));
        assertThrows(IllegalArgumentException.class, () -> Columns.parse("1A"));
        assertThrows(IllegalArgumentException.class, () -> Columns.parse(""));
    }

    @Test
    void rejectsDuplicateColumns() {
        LayoutConfig config = new LayoutConfig();
        config.levelColumn = 0;
        config.wbsColumn = 0;
        assertNotNull(config.validate());
        assertEquals(null, new LayoutConfig().validate());
    }
}
