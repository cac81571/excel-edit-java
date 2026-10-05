package wbs.editor.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public final class JapaneseDates {
    private static final String WEEKDAYS = "月火水木金土日";
    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DASHED = DateTimeFormatter.ofPattern("uuuu-M-d")
            .withResolverStyle(ResolverStyle.STRICT);

    private JapaneseDates() {
    }

    public static String weekday(LocalDate date) {
        int index = date.getDayOfWeek().getValue() - 1;
        return WEEKDAYS.substring(index, index + 1);
    }

    public static String formatBasic(LocalDate date) {
        return BASIC.format(date);
    }

    /** Accepts {@code YYYYMMDD}, {@code YYYY-M-D}, {@code YYYY/M/D}, {@code YYYY.M.D}. */
    public static LocalDate parseInput(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            throw new DateTimeParseException("空の日付です", raw, 0);
        }
        if (raw.matches("\\d{8}")) {
            return LocalDate.parse(raw, BASIC);
        }
        String normalized = raw.replace('年', '-').replace('月', '-').replace("日", "");
        normalized = normalized.replace('/', '-').replace('.', '-');
        if (normalized.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            return LocalDate.parse(normalized, DASHED);
        }
        throw new DateTimeParseException("日付の形式が不正です", raw, 0);
    }
}
