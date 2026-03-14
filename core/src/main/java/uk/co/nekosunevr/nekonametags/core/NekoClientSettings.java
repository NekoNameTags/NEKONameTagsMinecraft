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
    private static final String KEY_WEB_API_BASE_URL = "web.api.baseUrl";
    private static final String KEY_WEB_API_KEY = "web.api.key";
    private static final String DEFAULT_WEB_API_BASE_URL = "https://nekont.nekosunevr.co.uk";

    private boolean enabled;
    private String webApiBaseUrl;
    private String webApiKey;

    private NekoClientSettings(boolean enabled, String webApiBaseUrl, String webApiKey) {
        this.enabled = enabled;
        this.webApiBaseUrl = webApiBaseUrl;
        this.webApiKey = webApiKey;
    }

    public static NekoClientSettings loadDefault() {
        Path path = defaultPath();
        if (!Files.exists(path)) {
            return new NekoClientSettings(true, DEFAULT_WEB_API_BASE_URL, "");
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ignored) {
            return new NekoClientSettings(true, DEFAULT_WEB_API_BASE_URL, "");
        }

        String raw = properties.getProperty(KEY_ENABLED, "true");
        String webApiBaseUrl = properties.getProperty(KEY_WEB_API_BASE_URL, DEFAULT_WEB_API_BASE_URL).trim();
        String webApiKey = properties.getProperty(KEY_WEB_API_KEY, "").trim();
        if (webApiBaseUrl.isEmpty()) {
            webApiBaseUrl = DEFAULT_WEB_API_BASE_URL;
        }
        return new NekoClientSettings(Boolean.parseBoolean(raw), webApiBaseUrl, webApiKey);
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
            properties.setProperty(KEY_WEB_API_BASE_URL, safeValue(this.webApiBaseUrl, DEFAULT_WEB_API_BASE_URL));
            properties.setProperty(KEY_WEB_API_KEY, safeValue(this.webApiKey, ""));
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

    public String getWebApiBaseUrl() {
        return webApiBaseUrl;
    }

    public void setWebApiBaseUrl(String webApiBaseUrl) {
        this.webApiBaseUrl = safeValue(webApiBaseUrl, DEFAULT_WEB_API_BASE_URL);
    }

    public String getWebApiKey() {
        return webApiKey;
    }

    public void setWebApiKey(String webApiKey) {
        this.webApiKey = safeValue(webApiKey, "");
    }

    public boolean hasWebApiKey() {
        return webApiKey != null && !webApiKey.trim().isEmpty();
    }

    private static Path defaultPath() {
        return Paths.get("config", FILE_NAME);
    }

    private static String safeValue(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
