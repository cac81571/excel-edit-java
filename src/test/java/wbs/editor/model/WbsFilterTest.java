package wbs.editor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WbsFilterTest {
    @Test
    void nameFilterKeepsParentsOfMatches() {
        List<WbsItem> items = sample();
        WbsFilter.linkParents(items);
        List<WbsRow> rows = WbsFilter.visible(items, "菅原", "小項目1");

        assertEquals(List.of("1", "1.1", "1.1.1"), wbsOf(rows));
        assertFalse(row(rows, "1").owned());
        assertFalse(row(rows, "1.1").owned());
        assertTrue(row(rows, "1.1.1").owned());
    }

    @Test
    void blankNameFilterKeepsAssigneeMatches() {
        List<WbsItem> items = sample();
        WbsFilter.linkParents(items);
        List<WbsRow> rows = WbsFilter.visible(items, "菅原", "  ");
        assertEquals(List.of("1", "1.1", "1.1.1", "1.1.2", "1.10"), wbsOf(rows));
    }

    @Test
    void actualOnlyKeepsParentsOfMatches() {
        List<WbsItem> items = sample();
        WbsFilter.linkParents(items);
        item(items, "1.1.1").setActual(2.0);
        List<WbsRow> rows = WbsFilter.visible(items, "菅原", "", true);

        assertEquals(List.of("1", "1.1", "1.1.1"), wbsOf(rows));
        assertFalse(row(rows, "1").owned());
        assertFalse(row(rows, "1.1").owned());
        assertTrue(row(rows, "1.1.1").owned());
    }

    @Test
    void actualZeroCountsAsEntered() {
        List<WbsItem> items = sample();
        WbsFilter.linkParents(items);
        item(items, "1.1.2").setActual(0.0);
        List<WbsRow> rows = WbsFilter.visible(items, "菅原", "", true);
        assertEquals(List.of("1", "1.1", "1.1.2"), wbsOf(rows));
    }

    private static List<WbsItem> sample() {
        List<WbsItem> items = new ArrayList<>();
        items.add(item(1, "1", "大項目", ""));
        items.add(item(2, "1.1", "中項目", "田中"));
        items.add(item(3, "1.1.1", "小項目1", "菅原"));
        items.add(item(3, "1.1.2", "小項目2", "菅原"));
        items.add(item(2, "1.10", "十項目", "菅原"));
        return items;
    }

    private static WbsItem item(int level, String wbs, String name, String assignee) {
        return new WbsItem(level, wbs, name, assignee, 0, 1);
    }

    private static List<String> wbsOf(List<WbsRow> rows) {
        return rows.stream().map(row -> row.item().wbsNo()).toList();
    }

    private static WbsRow row(List<WbsRow> rows, String wbs) {
        return rows.stream().filter(row -> row.item().wbsNo().equals(wbs)).findFirst().orElseThrow();
    }

    private static WbsItem item(List<WbsItem> items, String wbs) {
        return items.stream().filter(entry -> entry.wbsNo().equals(wbs)).findFirst().orElseThrow();
    }
}
