package uk.co.nekosunevr.nekonametags.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class NekoClientSettings {
    private static final String FILE_NAME = "nekonametags-client.properties";
    private static final String KEY_ENABLED = "enabled";

    private boolean enabled;

    private NekoClientSettings(boolean enabled) {
        this.enabled = enabled;
    }

    public static NekoClientSettings loadDefault() {
        Path path = defaultPath();
        if (!Files.exists(path)) {
            return new NekoClientSettings(true);
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ignored) {
            return new NekoClientSettings(true);
        }

        String raw = properties.getProperty(KEY_ENABLED, "true");
        return new NekoClientSettings(Boolean.parseBoolean(raw));
    }

    public void saveDefault() {
        Path path = defaultPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties properties = new Properties();
            properties.setProperty(KEY_ENABLED, Boolean.toString(this.enabled));
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "NekoNameTags client settings");
            }
        } catch (IOException ignored) {
            // Best effort; mod still runs if settings cannot be persisted.
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static Path defaultPath() {
        return Paths.get("config", FILE_NAME);
    }
}
