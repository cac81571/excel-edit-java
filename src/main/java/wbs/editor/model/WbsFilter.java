package wbs.editor.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WbsFilter {
    private WbsFilter() {
    }

    public static void linkParents(List<WbsItem> items) {
        Map<String, Integer> byWbs = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            String wbs = items.get(i).wbsNo();
            if (!wbs.isEmpty()) {
                byWbs.putIfAbsent(wbs, i);
            }
        }
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < items.size(); i++) {
            WbsItem item = items.get(i);
            int parent = parentByWbs(items, byWbs, item, i);
            while (!stack.isEmpty() && items.get(stack.peek()).level() >= item.level()) {
                stack.pop();
            }
            if (parent < 0 && !stack.isEmpty()) {
                parent = stack.peek();
            }
            item.setParentIndex(parent);
            stack.push(i);
        }
    }

    private static int parentByWbs(List<WbsItem> items, Map<String, Integer> byWbs, WbsItem item, int index) {
        String wbs = item.wbsNo();
        int dot = wbs.lastIndexOf('.');
        if (dot <= 0) {
            return -1;
        }
        Integer parent = byWbs.get(wbs.substring(0, dot));
        if (parent == null || parent >= index) {
            return -1;
        }
        return parent;
    }

    public static List<WbsRow> visible(List<WbsItem> items, String person) {
        return visible(items, person, "", false);
    }

    public static List<WbsRow> visible(List<WbsItem> items, String person, String nameKeyword) {
        return visible(items, person, nameKeyword, false);
    }

    public static List<WbsRow> visible(
            List<WbsItem> items, String person, String nameKeyword, boolean onlyWithActual) {
        String keyword = Text.normalize(nameKeyword).toLowerCase(Locale.ROOT);
        boolean[] keep = new boolean[items.size()];
        boolean[] owned = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            WbsItem item = items.get(i);
            if (!assignedTo(item.assignee(), person)) {
                continue;
            }
            if (!keyword.isEmpty() && !nameContains(item.name(), keyword)) {
                continue;
            }
            if (onlyWithActual && item.actual() == null) {
                continue;
            }
            owned[i] = true;
            for (int cursor = i, guard = 0; cursor >= 0 && guard < items.size(); guard++) {
                keep[cursor] = true;
                int parent = items.get(cursor).parentIndex();
                if (parent == cursor) {
                    break;
                }
                cursor = parent;
            }
        }
        List<WbsRow> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!keep[i]) {
                continue;
            }
            int depth = 0;
            for (int cursor = items.get(i).parentIndex(), guard = 0; cursor >= 0 && guard < items.size(); guard++) {
                depth++;
                int parent = items.get(cursor).parentIndex();
                if (parent == cursor) {
                    break;
                }
                cursor = parent;
            }
            rows.add(new WbsRow(items.get(i), depth, owned[i]));
        }
        return rows;
    }

    public static boolean nameContains(String name, String keyword) {
        String needle = Text.normalize(keyword).toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        return Text.normalize(name).toLowerCase(Locale.ROOT).contains(needle);
    }

    public static boolean assignedTo(String assigneeCell, String person) {
        String wanted = Text.normalize(person);
        if (wanted.isEmpty()) {
            return true;
        }
        String cell = Text.normalize(assigneeCell);
        if (cell.equals(wanted)) {
            return true;
        }
        for (String token : tokens(cell)) {
            if (token.equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> tokens(String assigneeCell) {
        String cell = Text.normalize(assigneeCell);
        if (cell.isEmpty()) {
            return List.of();
        }
        String[] parts = cell.split("[、,，/／・;；]");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String token = Text.normalize(part);
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
