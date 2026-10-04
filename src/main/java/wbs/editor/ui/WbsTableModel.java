package wbs.editor.ui;

import java.util.List;
import javax.swing.table.AbstractTableModel;
import wbs.editor.model.Hours;
import wbs.editor.model.WbsItem;
import wbs.editor.model.WbsRow;

final class WbsTableModel extends AbstractTableModel {
    private static final String[] HEADERS = {"Level", "WBS No", "項目名", "担当", "予定", "実績"};

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
        if (columnIndex == 4) {
            return row.item().planRow() >= 0;
        }
        if (columnIndex == 5) {
            return row.item().actualRow() >= 0;
        }
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        WbsItem item = rows.get(rowIndex).item();
        return switch (columnIndex) {
            case 0 -> item.level();
            case 1 -> item.wbsNo();
            case 2 -> item.name();
            case 3 -> item.assignee();
            case 4 -> Hours.format(item.plan());
            case 5 -> Hours.format(item.actual());
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex != 4 && columnIndex != 5) {
            return;
        }
        Double parsed;
        try {
            parsed = Hours.parse(value == null ? "" : value.toString());
        } catch (NumberFormatException ex) {
            return;
        }
        WbsItem item = rows.get(rowIndex).item();
        if (columnIndex == 4) {
            item.setPlan(parsed);
        } else {
            item.setActual(parsed);
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}
