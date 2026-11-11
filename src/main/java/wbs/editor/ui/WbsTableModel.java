package wbs.editor.ui;

import java.util.List;
import javax.swing.table.AbstractTableModel;
import wbs.editor.model.Hours;
import wbs.editor.model.WbsItem;
import wbs.editor.model.WbsRow;

final class WbsTableModel extends AbstractTableModel {
    static final int COL_LEVEL = 0;
    static final int COL_WBS = 1;
    static final int COL_NAME = 2;
    static final int COL_ASSIGNEE = 3;
    static final int COL_TOTAL_HOURS = 4;
    static final int COL_PLAN = 5;
    static final int COL_ACTUAL = 6;

    private static final String[] HEADERS = {"Level", "WBS No", "項目名", "担当", "工数合計", "予定", "実績"};

    private List<WbsRow> rows = List.of();

    void setRows(List<WbsRow> rows) {
        this.rows = List.copyOf(rows);
        fireTableDataChanged();
    }

    void refreshValues() {
        if (!rows.isEmpty()) {
            fireTableRowsUpdated(0, rows.size() - 1);
        }
    }

    WbsRow row(int index) {
        return rows.get(index);
    }

    List<WbsRow> rows() {
        return rows;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return HEADERS.length;
    }

    @Override
    public String getColumnName(int column) {
        return HEADERS[column];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        WbsRow row = rows.get(rowIndex);
        if (!row.owned()) {
            return false;
        }
        if (columnIndex == COL_PLAN) {
            return row.item().planRow() >= 0;
        }
        if (columnIndex == COL_ACTUAL) {
            return row.item().actualRow() >= 0;
        }
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        WbsItem item = rows.get(rowIndex).item();
        return switch (columnIndex) {
            case COL_LEVEL -> item.level();
            case COL_WBS -> item.wbsNo();
            case COL_NAME -> item.name();
            case COL_ASSIGNEE -> item.assignee();
            case COL_TOTAL_HOURS -> Hours.format(item.totalHours());
            case COL_PLAN -> Hours.format(item.plan());
            case COL_ACTUAL -> Hours.format(item.actual());
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex != COL_PLAN && columnIndex != COL_ACTUAL) {
            return;
        }
        Double parsed;
        try {
            parsed = Hours.parse(value == null ? "" : value.toString());
        } catch (NumberFormatException ex) {
            return;
        }
        WbsItem item = rows.get(rowIndex).item();
        if (columnIndex == COL_PLAN) {
            item.setPlan(parsed);
        } else {
            item.setActual(parsed);
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}
