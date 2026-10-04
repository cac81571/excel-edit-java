package wbs.editor;

import java.awt.Font;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import com.formdev.flatlaf.FlatLightLaf;
import wbs.editor.ui.MainFrame;

public final class WbsEditorApp {
    private WbsEditorApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> start(args));
    }

    private static void start(String[] args) {
        Font font = pickFont();
        if (font != null) {
            UIManager.put("defaultFont", font);
        }
        FlatLightLaf.setup();
        UIManager.put("Table.showHorizontalLines", Boolean.TRUE);
        UIManager.put("Table.showVerticalLines", Boolean.TRUE);
        Path initial = args.length > 0 ? Path.of(args[0]) : null;
        new MainFrame(initial).setVisible(true);
    }

    private static Font pickFont() {
        for (String name : new String[] {"Yu Gothic UI", "Meiryo UI", "MS UI Gothic"}) {
            Font font = new Font(name, Font.PLAIN, 13);
            if (name.equals(font.getFamily())) {
                return font;
            }
        }
        return null;
    }
}
