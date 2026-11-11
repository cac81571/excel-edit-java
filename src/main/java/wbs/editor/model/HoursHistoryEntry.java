package wbs.editor.model;

import java.time.LocalDate;

public record HoursHistoryEntry(
        LocalDate date,
        String dateLabel,
        String weekday,
        Double plan,
        Double actual) {
}
