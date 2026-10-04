package wbs.editor.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Application settings stored as a properties file under the user-home app folder.
 */
public final class AppSettings {
    private static final String APP_FOLDER = "WBS実績入力";
    private static final String SETTINGS_FILE = "settings.properties";

    private final Properties properties = new Properties();

    private AppSettings() {
    }

    public static Path appDirectory() {
        return Path.of(System.getProperty("user.home"), APP_FOLDER);
    }

    public static Path settingsFile() {
        return appDirectory().resolve(SETTINGS_FILE);
    }

    public static Path backupDirectory() {
        return appDirectory().resolve("backup");
    }

    public static AppSettings load() {
        AppSettings settings = new AppSettings();
        Path file = settingsFile();
        if (!Files.isRegularFile(file)) {
            return settings;
        }
        try (InputStream in = Files.newInputStream(file)) {
            settings.properties.load(in);
        } catch (IOException ex) {
            // Keep defaults when the settings file is unreadable.
        }
        return settings;
    }

    public void save() throws IOException {
        Path directory = appDirectory();
        Files.createDirectories(directory);
        Path file = settingsFile();
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "WBS実績入力 settings");
        }
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public void put(String key, String value) {
        properties.setProperty(key, value == null ? "" : value);
    }

    public void putInt(String key, int value) {
        properties.setProperty(key, Integer.toString(value));
    }
}
