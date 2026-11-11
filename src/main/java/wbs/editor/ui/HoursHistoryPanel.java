package wbs.editor.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import wbs.editor.model.Hours;
import wbs.editor.model.HoursHistoryEntry;

final class HoursHistoryPanel extends JPanel {
    private static final Color HINT = new Color(0x667085);
    private static final String[] HEADERS = {"日付", "曜日", "予定", "実績"};

    private final JLabel title = new JLabel("工数履歴");
    private final DefaultTableModel model = new DefaultTableModel(HEADERS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);

    HoursHistoryPanel() {
        super(new BorderLayout(0, 4));
        setOpaque(false);
        setPreferredSize(new Dimension(260, 100));
        setMinimumSize(new Dimension(220, 80));
        // Keep top flush with the main table header; title sits below the history table.
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        title.setForeground(HINT);
        title.setFont(title.getFont().deriveFont(12f));

        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(2).setCellRenderer(right);
        table.getColumnModel().getColumn(3).setCellRenderer(right);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(36);
        table.getColumnModel().getColumn(2).setPreferredWidth(56);
        table.getColumnModel().getColumn(3).setPreferredWidth(56);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xD0D5DD)));
        add(scroll, BorderLayout.CENTER);
        add(title, BorderLayout.SOUTH);
        clear();
    }

    void showHistory(String taskName, List<HoursHistoryEntry> entries) {
        String label = taskName == null || taskName.isBlank() ? "工数履歴" : "工数履歴（" + taskName + "）";
        title.setText(label);
        title.setToolTipText(label);
        model.setRowCount(0);
        for (HoursHistoryEntry entry : entries) {
            model.addRow(new Object[] {
                    entry.dateLabel(),
                    entry.weekday(),
                    Hours.format(entry.plan()),
                    Hours.format(entry.actual())
            });
        }
    }

    void clear() {
        title.setText("工数履歴");
        title.setToolTipText("行を選択すると、予定または実績が入っている日付が表示されます");
        model.setRowCount(0);
    }
}
