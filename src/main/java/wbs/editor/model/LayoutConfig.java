package wbs.editor.model;

import java.util.prefs.Preferences;

/**
 * Positions are 1-based for rows, matching Excel. Columns are stored 0-based.
 * Defaults follow a sheet whose dates are on row 2, weekdays on row 3, and WBS rows start at row 4.
 */
public final class LayoutConfig {
    public String sheetName = "";
    public int dateRow = 2;
    public int weekdayRow = 3;
    public int dataStartRow = 4;
    public int levelColumn = 0;
    public int wbsColumn = 1;
    public int nameColumn = 2;
    public int assigneeColumn = 3;
    public int kindColumn = 4;
    public int firstDateColumn = 5;
    public String planLabel = "予定";
    public String actualLabel = "実績";

    public static Preferences preferences() {
        return Preferences.userNodeForPackage(LayoutConfig.class);
    }

    public static LayoutConfig load(Preferences prefs) {
        LayoutConfig defaults = new LayoutConfig();
        LayoutConfig config = new LayoutConfig();
        config.sheetName = prefs.get("sheetName", "");
        config.dateRow = prefs.getInt("dateRow", defaults.dateRow);
        config.weekdayRow = prefs.getInt("weekdayRow", defaults.weekdayRow);
        config.dataStartRow = prefs.getInt("dataStartRow", defaults.dataStartRow);
        config.levelColumn = Columns.parseOrDefault(prefs.get("levelColumn", "A"), defaults.levelColumn);
        config.wbsColumn = Columns.parseOrDefault(prefs.get("wbsColumn", "B"), defaults.wbsColumn);
        config.nameColumn = Columns.parseOrDefault(prefs.get("nameColumn", "C"), defaults.nameColumn);
        config.assigneeColumn = Columns.parseOrDefault(prefs.get("assigneeColumn", "D"), defaults.assigneeColumn);
        config.kindColumn = Columns.parseOrDefault(prefs.get("kindColumn", "E"), defaults.kindColumn);
        config.firstDateColumn = Columns.parseOrDefault(prefs.get("firstDateColumn", "F"), defaults.firstDateColumn);
        config.planLabel = prefs.get("planLabel", defaults.planLabel);
        config.actualLabel = prefs.get("actualLabel", defaults.actualLabel);
        if (config.planLabel.isBlank()) {
            config.planLabel = defaults.planLabel;
        }
        if (config.actualLabel.isBlank()) {
            config.actualLabel = defaults.actualLabel;
        }
        return config;
    }

    public void save(Preferences prefs) {
        prefs.put("sheetName", sheetName == null ? "" : sheetName);
        prefs.putInt("dateRow", dateRow);
        prefs.putInt("weekdayRow", weekdayRow);
        prefs.putInt("dataStartRow", dataStartRow);
        prefs.put("levelColumn", Columns.name(levelColumn));
        prefs.put("wbsColumn", Columns.name(wbsColumn));
        prefs.put("nameColumn", Columns.name(nameColumn));
        prefs.put("assigneeColumn", Columns.name(assigneeColumn));
        prefs.put("kindColumn", Columns.name(kindColumn));
        prefs.put("firstDateColumn", Columns.name(firstDateColumn));
        prefs.put("planLabel", planLabel);
        prefs.put("actualLabel", actualLabel);
    }

    public LayoutConfig copy() {
        LayoutConfig copy = new LayoutConfig();
        copy.sheetName = sheetName;
        copy.dateRow = dateRow;
        copy.weekdayRow = weekdayRow;
        copy.dataStartRow = dataStartRow;
        copy.levelColumn = levelColumn;
        copy.wbsColumn = wbsColumn;
        copy.nameColumn = nameColumn;
        copy.assigneeColumn = assigneeColumn;
        copy.kindColumn = kindColumn;
        copy.firstDateColumn = firstDateColumn;
        copy.planLabel = planLabel;
        copy.actualLabel = actualLabel;
        return copy;
    }

    public String validate() {
        if (dateRow < 1 || dataStartRow < 1) {
            return "行番号は 1 以上にしてください。";
        }
        if (weekdayRow < 0) {
            return "曜日の行は 0 以上にしてください。";
        }
        if (dateRow == dataStartRow) {
            return "日付の行とデータ開始行は別の行にしてください。";
        }
        if (weekdayRow > 0 && weekdayRow == dataStartRow) {
            return "曜日の行とデータ開始行は別の行にしてください。";
        }
        if (planLabel == null || planLabel.isBlank() || actualLabel == null || actualLabel.isBlank()) {
            return "予定と実績の文字を入力してください。";
        }
        if (planLabel.trim().equals(actualLabel.trim())) {
            return "予定と実績の文字は別にしてください。";
        }
        int[] columns = {levelColumn, wbsColumn, nameColumn, assigneeColumn, kindColumn, firstDateColumn};
        String[] labels = {"Level", "WBS No", "項目名", "担当", "予定／実績", "日付の開始列"};
        for (int i = 0; i < columns.length; i++) {
            if (columns[i] < 0) {
                return labels[i] + "の列が不正です。";
            }
            for (int j = i + 1; j < columns.length; j++) {
                if (columns[i] == columns[j]) {
                    return labels[i] + "と" + labels[j] + "の列が同じです。";
                }
            }
        }
        return null;
    }

    public String summary(String resolvedSheetName) {
        String sheet = sheetName == null || sheetName.isBlank()
                ? resolvedSheetName
                : resolvedSheetName;
        return "シート「" + sheet + "」  日付 " + dateRow + "行"
                + (weekdayRow > 0 ? "  曜日 " + weekdayRow + "行" : "  曜日なし")
                + "  データ " + dataStartRow + "行  "
                + Columns.name(levelColumn) + ":Level  "
                + Columns.name(wbsColumn) + ":WBS  "
                + Columns.name(nameColumn) + ":項目  "
                + Columns.name(assigneeColumn) + ":担当  "
                + Columns.name(kindColumn) + ":区分  日付 "
                + Columns.name(firstDateColumn) + "列〜";
    }
}
