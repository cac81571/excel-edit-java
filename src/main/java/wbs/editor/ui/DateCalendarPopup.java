package wbs.editor.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;

/** Month calendar popup for picking a {@link LocalDate}. */
final class DateCalendarPopup {
    private static final Color TODAY_BG = new Color(0xE8F1FB);
    private static final Color SELECTED_BG = new Color(0x2F6FED);
    private static final Color SELECTED_FG = Color.WHITE;
    private static final Color SUNDAY = new Color(0xC62828);
    private static final Color SATURDAY = new Color(0x1565C0);
    private static final String[] WEEKDAYS = {"日", "月", "火", "水", "木", "金", "土"};

    private DateCalendarPopup() {
    }

    static void show(Component anchor, LocalDate selected, Consumer<LocalDate> onSelect) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(new Color(0xD0D5DD)));
        popup.add(new CalendarPanel(selected, date -> {
            popup.setVisible(false);
            onSelect.accept(date);
        }));
        popup.show(anchor, 0, anchor.getHeight());
    }

    private static final class CalendarPanel extends JPanel {
        private YearMonth month;
        private final LocalDate selected;
        private final Consumer<LocalDate> onSelect;
        private final JLabel title = new JLabel("", SwingConstants.CENTER);
        private final JPanel days = new JPanel(new GridLayout(0, 7, 2, 2));

        CalendarPanel(LocalDate selected, Consumer<LocalDate> onSelect) {
            super(new BorderLayout(0, 6));
            this.selected = selected;
            this.onSelect = onSelect;
            this.month = YearMonth.from(selected);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setPreferredSize(new Dimension(252, 240));

            JButton prev = navButton("◀");
            prev.addActionListener(event -> {
                month = month.minusMonths(1);
                refresh();
            });
            JButton next = navButton("▶");
            next.addActionListener(event -> {
                month = month.plusMonths(1);
                refresh();
            });
            title.setFont(title.getFont().deriveFont(Font.BOLD));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.add(prev, BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);

            JPanel weekdayRow = new JPanel(new GridLayout(1, 7, 2, 2));
            weekdayRow.setOpaque(false);
            for (int i = 0; i < WEEKDAYS.length; i++) {
                JLabel label = new JLabel(WEEKDAYS[i], SwingConstants.CENTER);
                label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
                if (i == 0) {
                    label.setForeground(SUNDAY);
                } else if (i == 6) {
                    label.setForeground(SATURDAY);
                }
                weekdayRow.add(label);
            }

            JPanel body = new JPanel(new BorderLayout(0, 4));
            body.setOpaque(false);
            body.add(weekdayRow, BorderLayout.NORTH);
            body.add(days, BorderLayout.CENTER);

            add(header, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            refresh();
        }

        private void refresh() {
            title.setText(month.getYear() + "年" + month.getMonthValue() + "月");
            days.removeAll();
            LocalDate first = month.atDay(1);
            int lead = first.getDayOfWeek().getValue() % 7; // Sunday=0
            for (int i = 0; i < lead; i++) {
                days.add(blank());
            }
            LocalDate today = LocalDate.now();
            int length = month.lengthOfMonth();
            for (int day = 1; day <= length; day++) {
                LocalDate date = month.atDay(day);
                days.add(dayButton(date, today));
            }
            days.revalidate();
            days.repaint();
        }

        private JButton dayButton(LocalDate date, LocalDate today) {
            JButton button = new JButton(String.valueOf(date.getDayOfMonth()));
            button.setMargin(new Insets(2, 2, 2, 2));
            button.setFocusable(false);
            button.setFont(button.getFont().deriveFont(Font.PLAIN, 12f));
            DayOfWeek dow = date.getDayOfWeek();
            if (date.equals(selected)) {
                button.setBackground(SELECTED_BG);
                button.setForeground(SELECTED_FG);
                button.setOpaque(true);
            } else if (date.equals(today)) {
                button.setBackground(TODAY_BG);
                button.setOpaque(true);
            } else if (dow == DayOfWeek.SUNDAY) {
                button.setForeground(SUNDAY);
            } else if (dow == DayOfWeek.SATURDAY) {
                button.setForeground(SATURDAY);
            }
            button.addActionListener(event -> onSelect.accept(date));
            return button;
        }

        private static JLabel blank() {
            return new JLabel(" ");
        }

        private static JButton navButton(String text) {
            JButton button = new JButton(text);
            button.setMargin(new Insets(2, 8, 2, 8));
            button.setFocusable(false);
            return button;
        }
    }
}
