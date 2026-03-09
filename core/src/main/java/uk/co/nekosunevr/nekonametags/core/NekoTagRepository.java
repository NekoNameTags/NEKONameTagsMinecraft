package uk.co.nekosunevr.nekonametags.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class NekoTagRepository {
    private static final Type USER_LIST_TYPE = new TypeToken<List<NekoTagUser>>() {}.getType();
    private static final String ALLOWED_SCHEME = "https";
    private static final String ALLOWED_HOST = "nekont.nekosunevr.co.uk";
    private static final String ALLOWED_PATH = "/api/minecraft/nametags";
    private static final long DEFAULT_REFRESH_MILLIS = 300_000L;
    private static final String MOJANG_SESSION_PROFILE_BASE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Map<String, String> UUID_NAME_CACHE = new ConcurrentHashMap<String, String>();

    private final Gson gson = new Gson();
    private final String apiUrl;
    private final Path cacheFile;
    private final long refreshMillis;
    private final DebugLogger debugLogger;
    private final AtomicReference<Map<String, NekoTagUser>> cache = new AtomicReference<>(Collections.emptyMap());
    private volatile long lastApiAttemptAt = 0L;
    private volatile String lastCacheJson = "";

    public NekoTagRepository(String apiUrl) {
        this(apiUrl, null, DEFAULT_REFRESH_MILLIS, null);
    }

    public NekoTagRepository(String apiUrl, Path cacheFile, long refreshMillis, DebugLogger debugLogger) {
        this.apiUrl = apiUrl;
        this.cacheFile = cacheFile;
        this.refreshMillis = refreshMillis <= 0L ? DEFAULT_REFRESH_MILLIS : refreshMillis;
        this.debugLogger = debugLogger;
        loadFromDiskCacheIfPresent();
    }

    public Map<String, NekoTagUser> getCached() {
        return cache.get();
    }

    public synchronized Map<String, NekoTagUser> reload() throws Exception {
        validateApiEndpoint();

        long now = System.currentTimeMillis();
        Map<String, NekoTagUser> current = cache.get();
        if (!current.isEmpty() && (now - lastApiAttemptAt) < refreshMillis) {
            debug("Using in-memory cache (refresh window active).");
            return current;
        }

        lastApiAttemptAt = now;
        try {
            List<NekoTagUser> users = fetchFromApi();
            Map<String, NekoTagUser> mapped = mapUsers(users);
            cache.set(mapped);
            writeDiskCache(users);
            debug("Reloaded " + mapped.size() + " entries from API.");
            return mapped;
        } catch (Exception ex) {
            debug("API reload failed: " + ex.getMessage());

            if (!current.isEmpty()) {
                debug("Using in-memory fallback cache.");
                return current;
            }

            List<NekoTagUser> fromDisk = readDiskCacheUsers();
            if (fromDisk != null) {
                Map<String, NekoTagUser> mapped = mapUsers(fromDisk);
                cache.set(mapped);
                debug("Using disk cache fallback with " + mapped.size() + " entries.");
                return mapped;
            }

            throw ex;
        }
    }

    public NekoTagUser findForPlayer(String playerUuid, String playerName) {
        Map<String, NekoTagUser> users = cache.get();
        if (users.isEmpty()) {
            return null;
        }

        if (playerUuid != null && !playerUuid.trim().isEmpty()) {
            String normalized = normalizeUserKey(playerUuid);
            NekoTagUser byUuid = users.get(normalized);
            if (byUuid == null) {
                byUuid = users.get(compactUuidKey(normalized));
            }
            if (byUuid != null) {
                return byUuid;
            }
        }
        if (playerName != null && !playerName.trim().isEmpty()) {
            return users.get(normalizeUserKey(playerName));
        }
        return null;
    }

    private static String normalizeUserKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String compactUuidKey(String value) {
        return value == null ? "" : value.replace("-", "");
    }

    private void validateApiEndpoint() {
        URI uri = URI.create(apiUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (!ALLOWED_SCHEME.equals(scheme)) {
            throw new IllegalStateException("Blocked API URL: only HTTPS is allowed.");
        }
        if (!ALLOWED_HOST.equals(host)) {
            throw new IllegalStateException("Blocked API URL host: " + host);
        }
        if (!ALLOWED_PATH.equals(path)) {
            throw new IllegalStateException("Blocked API URL path: " + path);
        }
    }

    private List<NekoTagUser> fetchFromApi() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/json");

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("API request failed with status: " + status);
        }

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, USER_LIST_TYPE);
        } finally {
            connection.disconnect();
        }
    }

    private Map<String, NekoTagUser> mapUsers(List<NekoTagUser> users) {
        Map<String, NekoTagUser> mapped = new LinkedHashMap<String, NekoTagUser>();
        if (users != null) {
            for (NekoTagUser user : users) {
                if (user != null && user.getUserId() != null && !user.getUserId().trim().isEmpty()) {
                    String key = normalizeUserKey(user.getUserId());
                    mapped.put(key, user);
                    String compact = compactUuidKey(key);
                    if (!compact.equals(key)) {
                        mapped.put(compact, user);
                    }
                    if (isUuidKey(key)) {
                        String resolvedName = resolveNameForUuid(compact);
                        if (resolvedName != null && !resolvedName.isEmpty()) {
                            mapped.put(normalizeUserKey(resolvedName), user);
                        }
                    }
                }
            }
        }
        return Collections.unmodifiableMap(mapped);
    }

    private static boolean isUuidKey(String value) {
        if (value == null) {
            return false;
        }
        String compact = compactUuidKey(value);
        if (compact.length() != 32) {
            return false;
        }
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private String resolveNameForUuid(String uuidCompact) {
        if (uuidCompact == null || uuidCompact.length() != 32) {
            return null;
        }
        String key = uuidCompact.toLowerCase(Locale.ROOT);
        String cached = UUID_NAME_CACHE.get(key);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(MOJANG_SESSION_PROFILE_BASE + key);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject object = gson.fromJson(reader, JsonObject.class);
                if (object == null || !object.has("name")) {
                    return null;
                }
                String name = object.get("name").getAsString();
                if (name == null || name.trim().isEmpty()) {
                    return null;
                }
                String trimmed = name.trim();
                UUID_NAME_CACHE.put(key, trimmed);
                return trimmed;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void loadFromDiskCacheIfPresent() {
        try {
            List<NekoTagUser> users = readDiskCacheUsers();
            if (users == null) {
                return;
            }
            Map<String, NekoTagUser> mapped = mapUsers(users);
            cache.set(mapped);
            debug("Loaded " + mapped.size() + " entries from disk cache.");
        } catch (Exception ignored) {
            debug("Disk cache preload failed.");
        }
    }

    private List<NekoTagUser> readDiskCacheUsers() {
        if (cacheFile == null || !Files.exists(cacheFile)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            List<NekoTagUser> users = gson.fromJson(reader, USER_LIST_TYPE);
            if (users == null) {
                return null;
            }
            lastCacheJson = gson.toJson(users);
            return users;
        } catch (IOException ex) {
            debug("Disk cache read failed: " + ex.getMessage());
            return null;
        }
    }

    private void writeDiskCache(List<NekoTagUser> users) {
        if (cacheFile == null) {
            return;
        }
        List<NekoTagUser> safeUsers = users == null ? Collections.<NekoTagUser>emptyList() : users;
        String json = gson.toJson(safeUsers);
        if (json.equals(lastCacheJson)) {
            return;
        }

        try {
            Path parent = cacheFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            lastCacheJson = json;
            debug("Disk cache updated: " + cacheFile.toString());
        } catch (IOException ex) {
            debug("Disk cache write failed: " + ex.getMessage());
        }
    }

    private void debug(String message) {
        if (debugLogger != null && message != null) {
            debugLogger.log(message);
        }
    }

    public interface DebugLogger {
        void log(String message);
    }
}
