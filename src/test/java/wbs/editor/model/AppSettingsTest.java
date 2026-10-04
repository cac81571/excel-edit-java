package wbs.editor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSettingsTest {
    @TempDir
    Path temp;

    @Test
    void roundTripsPropertiesFile() throws Exception {
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.toString());
        try {
            AppSettings settings = AppSettings.load();
            settings.put("sheetName", "作業");
            settings.putInt("dateRow", 6);
            settings.put("lastAssignee", "菅原");
            settings.save();

            assertTrue(Files.isRegularFile(AppSettings.settingsFile()));
            AppSettings reloaded = AppSettings.load();
            LayoutConfig config = LayoutConfig.load(reloaded);
            assertEquals("作業", config.sheetName);
            assertEquals(6, config.dateRow);
            assertEquals("菅原", reloaded.get("lastAssignee", ""));
            assertEquals(temp.resolve("WBS実績入力").resolve("backup"), AppSettings.backupDirectory());
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }
}
