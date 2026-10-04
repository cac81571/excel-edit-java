package wbs.editor.model;

import java.util.Locale;

public final class Columns {
    private Columns() {
    }

    public static int parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("列を入力してください。");
        }
        String value = text.trim().toUpperCase(Locale.ROOT);
        if (value.matches("[1-9]\\d*")) {
            int number = Integer.parseInt(value);
            if (number > 16384) {
                throw new IllegalArgumentException("列が大きすぎます: " + text);
            }
            return number - 1;
        }
        if (!value.matches("[A-Z]{1,3}")) {
            throw new IllegalArgumentException("列は A や F、または 1 からの番号で指定してください: " + text);
        }
        int number = 0;
        for (int i = 0; i < value.length(); i++) {
            number = number * 26 + (value.charAt(i) - 'A' + 1);
        }
        if (number < 1 || number > 16384) {
            throw new IllegalArgumentException("列が大きすぎます: " + text);
        }
        return number - 1;
    }

    public static int parseOrDefault(String text, int fallback) {
        try {
            return parse(text);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public static String name(int index) {
        if (index < 0 || index >= 16384) {
            throw new IllegalArgumentException("列が不正です: " + index);
        }
        int number = index + 1;
        StringBuilder builder = new StringBuilder();
        while (number > 0) {
            number--;
            builder.append((char) ('A' + (number % 26)));
            number /= 26;
        }
        return builder.reverse().toString();
    }
}
