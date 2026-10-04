package wbs.editor.model;

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

    public static LayoutConfig load(AppSettings settings) {
        LayoutConfig defaults = new LayoutConfig();
        LayoutConfig config = new LayoutConfig();
        config.sheetName = settings.get("sheetName", "");
        config.dateRow = settings.getInt("dateRow", defaults.dateRow);
        config.weekdayRow = settings.getInt("weekdayRow", defaults.weekdayRow);
        config.dataStartRow = settings.getInt("dataStartRow", defaults.dataStartRow);
        config.levelColumn = Columns.parseOrDefault(settings.get("levelColumn", "A"), defaults.levelColumn);
        config.wbsColumn = Columns.parseOrDefault(settings.get("wbsColumn", "B"), defaults.wbsColumn);
        config.nameColumn = Columns.parseOrDefault(settings.get("nameColumn", "C"), defaults.nameColumn);
        config.assigneeColumn = Columns.parseOrDefault(settings.get("assigneeColumn", "D"), defaults.assigneeColumn);
        config.kindColumn = Columns.parseOrDefault(settings.get("kindColumn", "E"), defaults.kindColumn);
        config.firstDateColumn = Columns.parseOrDefault(settings.get("firstDateColumn", "F"), defaults.firstDateColumn);
        config.planLabel = settings.get("planLabel", defaults.planLabel);
        config.actualLabel = settings.get("actualLabel", defaults.actualLabel);
        if (config.planLabel.isBlank()) {
            config.planLabel = defaults.planLabel;
        }
        if (config.actualLabel.isBlank()) {
            config.actualLabel = defaults.actualLabel;
        }
        return config;
    }

    public void save(AppSettings settings) {
        settings.put("sheetName", sheetName == null ? "" : sheetName);
        settings.putInt("dateRow", dateRow);
        settings.putInt("weekdayRow", weekdayRow);
        settings.putInt("dataStartRow", dataStartRow);
        settings.put("levelColumn", Columns.name(levelColumn));
        settings.put("wbsColumn", Columns.name(wbsColumn));
        settings.put("nameColumn", Columns.name(nameColumn));
        settings.put("assigneeColumn", Columns.name(assigneeColumn));
        settings.put("kindColumn", Columns.name(kindColumn));
        settings.put("firstDateColumn", Columns.name(firstDateColumn));
        settings.put("planLabel", planLabel);
        settings.put("actualLabel", actualLabel);
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
