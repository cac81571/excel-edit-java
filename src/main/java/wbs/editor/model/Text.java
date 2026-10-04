package wbs.editor.model;

public final class Text {
    private Text() {
    }

    public static String halfWidth(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '０' && c <= '９') {
                builder.append((char) ('0' + c - '０'));
            } else if (c == '．') {
                builder.append('.');
            } else if (c == '，') {
                builder.append(',');
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return halfWidth(text).replace('\u3000', ' ').trim();
    }
}
