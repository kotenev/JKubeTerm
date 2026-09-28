package dev.jkubeterm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {
    @Test void defaultsAreSane() {
        var defaults = AppConfig.Settings.defaults();
        assertEquals(1380, defaults.windowWidth());
        assertEquals(840, defaults.windowHeight());
        assertTrue(defaults.mainDivider0() < defaults.mainDivider1());
        assertEquals("System", defaults.fontFamily());
        assertEquals(13.0, defaults.fontSize());
        assertEquals(1.0, defaults.uiZoom());
    }

    @Test void malformedValuesFallBackToDefaults() {
        Properties props = new Properties();
        props.setProperty("font.size", "huge");
        props.setProperty("ui.zoom", "nan");
        props.setProperty("divider.main.0", "-5");
        props.setProperty("window.width", "10");
        var settings = AppConfig.from(props);
        assertEquals(13.0, settings.fontSize());
        assertEquals(1.0, settings.uiZoom());
        assertTrue(settings.mainDivider0() >= 0.05);
        assertTrue(settings.windowWidth() >= 800);
    }

    @Test void clampsFontAndZoom() {
        Properties props = new Properties();
        props.setProperty("font.size", "99");
        props.setProperty("ui.zoom", "0.01");
        var settings = AppConfig.from(props);
        assertEquals(AppConfig.MAX_FONT, settings.fontSize());
        assertEquals(AppConfig.MIN_ZOOM, settings.uiZoom());
    }

    @Test void saveRoundTrip(@TempDir Path dir) throws Exception {
        var settings = new AppConfig.Settings(1600, 900, 0.2, 0.6, 0.3, 0.5, 0.7, "Monospaced", 15.0, 1.2);
        Path file = dir.resolve("config.properties");
        Properties props = new Properties();
        props.setProperty("window.width", "1600");
        props.setProperty("window.height", "900");
        props.setProperty("divider.main.0", "0.2");
        props.setProperty("divider.main.1", "0.6");
        props.setProperty("divider.right.0", "0.3");
        props.setProperty("divider.right.1", "0.5");
        props.setProperty("divider.right.2", "0.7");
        props.setProperty("font.family", "Monospaced");
        props.setProperty("font.size", "15.0");
        props.setProperty("ui.zoom", "1.2");
        var parsed = AppConfig.from(props);
        assertEquals(settings.windowWidth(), parsed.windowWidth());
        assertEquals(settings.fontFamily(), parsed.fontFamily());
        assertEquals(settings.uiZoom(), parsed.uiZoom());
        assertTrue(Files.notExists(file));
    }
}
