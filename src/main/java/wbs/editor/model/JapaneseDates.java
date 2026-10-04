package wbs.editor.model;

import java.time.LocalDate;

public final class JapaneseDates {
    private static final String WEEKDAYS = "月火水木金土日";

    private JapaneseDates() {
    }

    public static String weekday(LocalDate date) {
        int index = date.getDayOfWeek().getValue() - 1;
        return WEEKDAYS.substring(index, index + 1);
    }
}
