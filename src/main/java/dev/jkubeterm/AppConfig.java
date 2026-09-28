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

    public record Settings(double windowX, double windowY, double windowWidth, double windowHeight,
                           double mainDivider0, double mainDivider1,
                           double rightDivider0, double rightDivider1, double rightDivider2, double rightDivider3, double rightDivider4,
                           double bottomDivider, double shelfDivider, double atticDivider,
                           String dockedSections,
                           String fontFamily, double fontSize, double uiZoom) {
        public static Settings defaults() {
            return new Settings(Double.NaN, Double.NaN, 1380, 840, 0.15, 0.55, 0.14, 0.34, 0.52, 0.68, 0.82, 0.78, 0.5, 0.12, "", "System", DEFAULT_FONT, 1.0);
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
        props.setProperty("window.x", coordinate(settings.windowX()));
        props.setProperty("window.y", coordinate(settings.windowY()));
        props.setProperty("window.width", Double.toString(settings.windowWidth()));
        props.setProperty("window.height", Double.toString(settings.windowHeight()));
        props.setProperty("divider.main.0", Double.toString(settings.mainDivider0()));
        props.setProperty("divider.main.1", Double.toString(settings.mainDivider1()));
        props.setProperty("divider.right.0", Double.toString(settings.rightDivider0()));
        props.setProperty("divider.right.1", Double.toString(settings.rightDivider1()));
        props.setProperty("divider.right.2", Double.toString(settings.rightDivider2()));
        props.setProperty("divider.right.3", Double.toString(settings.rightDivider3()));
        props.setProperty("divider.right.4", Double.toString(settings.rightDivider4()));
        props.setProperty("divider.bottom", Double.toString(settings.bottomDivider()));
        props.setProperty("divider.shelf", Double.toString(settings.shelfDivider()));
        props.setProperty("divider.attic", Double.toString(settings.atticDivider()));
        props.setProperty("docked.sections", settings.dockedSections() == null ? "" : settings.dockedSections());
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
            coordinate(props, "window.x", defaults.windowX()),
            coordinate(props, "window.y", defaults.windowY()),
            clampDouble(props, "window.width", defaults.windowWidth(), 800, 3840),
            clampDouble(props, "window.height", defaults.windowHeight(), 500, 2160),
            clamp01(props, "divider.main.0", defaults.mainDivider0()),
            clamp01(props, "divider.main.1", defaults.mainDivider1()),
            clamp01(props, "divider.right.0", defaults.rightDivider0()),
            clamp01(props, "divider.right.1", defaults.rightDivider1()),
            clamp01(props, "divider.right.2", defaults.rightDivider2()),
            clamp01(props, "divider.right.3", defaults.rightDivider3()),
            clamp01(props, "divider.right.4", defaults.rightDivider4()),
            clamp01(props, "divider.bottom", defaults.bottomDivider()),
            clamp01(props, "divider.shelf", defaults.shelfDivider()),
            clamp01(props, "divider.attic", defaults.atticDivider()),
            props.getProperty("docked.sections", defaults.dockedSections()),
            fontFamily(props.getProperty("font.family", defaults.fontFamily())),
            clampDouble(props, "font.size", defaults.fontSize(), MIN_FONT, MAX_FONT),
            clampDouble(props, "ui.zoom", defaults.uiZoom(), MIN_ZOOM, MAX_ZOOM));
    }

    private static String coordinate(double value) {
        return Double.isNaN(value) ? "" : Double.toString(value);
    }

    private static double coordinate(Properties props, String key, double fallback) {
        String raw = props.getProperty(key, "");
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value)) return fallback;
            return Math.clamp(value, -3840, 3840);
        } catch (NumberFormatException e) {
            return fallback;
        }
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
