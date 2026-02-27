package uk.co.nekosunevr.nekonametags.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class NekoTagRepository {
    private static final Type USER_LIST_TYPE = new TypeToken<List<NekoTagUser>>() {}.getType();

    private final Gson gson = new Gson();
    private final String apiUrl;
    private final AtomicReference<Map<String, NekoTagUser>> cache = new AtomicReference<>(Collections.emptyMap());

    public NekoTagRepository(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public Map<String, NekoTagUser> getCached() {
        return cache.get();
    }

    public Map<String, NekoTagUser> reload() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
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
            List<NekoTagUser> users = gson.fromJson(reader, USER_LIST_TYPE);
            Map<String, NekoTagUser> mapped = new LinkedHashMap<String, NekoTagUser>();
            if (users != null) {
                for (NekoTagUser user : users) {
                    if (user != null && user.getUserId() != null && !user.getUserId().trim().isEmpty()) {
                        mapped.put(normalizeUserKey(user.getUserId()), user);
                    }
                }
            }
            Map<String, NekoTagUser> unmodifiable = Collections.unmodifiableMap(mapped);
            cache.set(unmodifiable);
            return unmodifiable;
        } finally {
            connection.disconnect();
        }
    }

    public NekoTagUser findForPlayer(String playerUuid, String playerName) {
        Map<String, NekoTagUser> users = cache.get();
        if (users.isEmpty()) {
            return null;
        }

        if (playerUuid != null && !playerUuid.trim().isEmpty()) {
            NekoTagUser byUuid = users.get(normalizeUserKey(playerUuid));
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
}
