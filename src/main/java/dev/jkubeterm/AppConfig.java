package dev.jkubeterm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Application configuration: window size, SplitPane divider positions,
 * UI zoom and font settings. Stored as {@code ~/.jkubeterm/config.properties}.
 * Plain properties plus clamping — unit tested, no JavaFX.
 */
public final class AppConfig {
    private AppConfig() {}

    public static final double MIN_FONT = 9.0;
    public static final double MAX_FONT = 20.0;
    public static final double MIN_ZOOM = 0.7;
    public static final double MAX_ZOOM = 1.8;
    private static final double DEFAULT_FONT = 13.0;

    public record Settings(double windowWidth, double windowHeight,
                           double mainDivider0, double mainDivider1,
                           double rightDivider0, double rightDivider1, double rightDivider2,
                           String fontFamily, double fontSize, double uiZoom) {
        public static Settings defaults() {
            return new Settings(1380, 840, 0.15, 0.55, 0.34, 0.52, 0.68, "System", DEFAULT_FONT, 1.0);
        }
    }

    public static Path configFile() {
        return Path.of(System.getProperty("user.home"), ".jkubeterm", "config.properties");
    }

    public static Settings load() {
        Properties props = new Properties();
        Path file = configFile();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) { /* fall back to defaults */ }
        }
        return from(props);
    }

    public static void save(Settings settings) throws IOException {
        Path file = configFile();
        Files.createDirectories(file.getParent());
        Properties props = new Properties();
        props.setProperty("window.width", Double.toString(settings.windowWidth()));
        props.setProperty("window.height", Double.toString(settings.windowHeight()));
        props.setProperty("divider.main.0", Double.toString(settings.mainDivider0()));
        props.setProperty("divider.main.1", Double.toString(settings.mainDivider1()));
        props.setProperty("divider.right.0", Double.toString(settings.rightDivider0()));
        props.setProperty("divider.right.1", Double.toString(settings.rightDivider1()));
        props.setProperty("divider.right.2", Double.toString(settings.rightDivider2()));
        props.setProperty("font.family", settings.fontFamily());
        props.setProperty("font.size", Double.toString(settings.fontSize()));
        props.setProperty("ui.zoom", Double.toString(settings.uiZoom()));
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "JKubeTerm application configuration");
        }
    }

    static Settings from(Properties props) {
        Settings defaults = Settings.defaults();
        return new Settings(
            clampDouble(props, "window.width", defaults.windowWidth(), 800, 3840),
            clampDouble(props, "window.height", defaults.windowHeight(), 500, 2160),
            clamp01(props, "divider.main.0", defaults.mainDivider0()),
            clamp01(props, "divider.main.1", defaults.mainDivider1()),
            clamp01(props, "divider.right.0", defaults.rightDivider0()),
            clamp01(props, "divider.right.1", defaults.rightDivider1()),
            clamp01(props, "divider.right.2", defaults.rightDivider2()),
            fontFamily(props.getProperty("font.family", defaults.fontFamily())),
            clampDouble(props, "font.size", defaults.fontSize(), MIN_FONT, MAX_FONT),
            clampDouble(props, "ui.zoom", defaults.uiZoom(), MIN_ZOOM, MAX_ZOOM));
    }

    private static double clampDouble(Properties props, String key, double fallback, double min, double max) {
        double value;
        try {
            value = Double.parseDouble(props.getProperty(key, Double.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
        if (Double.isNaN(value)) return fallback;
        return Math.clamp(value, min, max);
    }

    private static double clamp01(Properties props, String key, double fallback) {
        return clampDouble(props, key, fallback, 0.05, 0.95);
    }

    static String fontFamily(String raw) {
        if (raw == null || raw.isBlank()) return Settings.defaults().fontFamily();
        return raw.trim();
    }
}
