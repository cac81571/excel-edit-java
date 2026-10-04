package wbs.editor.model;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Hours {
    private Hours() {
    }

    public static Double parse(String text) {
        if (text == null) {
            return null;
        }
        String normalized = Text.halfWidth(text).replace(" ", "").replace("\u3000", "").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.indexOf(',') >= 0 && normalized.indexOf('.') < 0) {
            normalized = normalized.replace(',', '.');
        } else {
            normalized = normalized.replace(",", "");
        }
        double value = Double.parseDouble(normalized);
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw new NumberFormatException(text);
        }
        return value;
    }

    public static boolean parsable(String text) {
        try {
            parse(text);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static String format(Double value) {
        if (value == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }

    public static boolean same(Double left, Double right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return Math.abs(left - right) < 1e-9;
    }
}
