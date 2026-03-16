package uk.co.nekosunevr.nekonametags.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class NekoGameProfileClient {
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String apiKey;

    public NekoGameProfileClient(String baseUrl, String apiKey) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public JsonObject fetchWynncraftProfile(String username) throws IOException {
        return request("wynncraft", username);
    }

    public JsonObject fetchHypixelProfile(String username) throws IOException {
        return request("hypixel", username);
    }

    public NekoTagUser fetchWynncraftNametags(String username) throws IOException {
        return requestNametags("wynncraft", username);
    }

    public NekoTagUser fetchHypixelNametags(String username) throws IOException {
        return requestNametags("hypixel", username);
    }

    private JsonObject request(String service, String username) throws IOException {
        String encodedUsername = URLEncoder.encode(username == null ? "" : username.trim(), StandardCharsets.UTF_8.name());
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/v5/games/api/" + service + "/profile/" + encodedUsername).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("nekosunevr-api-key", apiKey);

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = readBody(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new IOException(extractErrorMessage(responseBody, status));
        }
        if (responseBody.isEmpty()) {
            return new JsonObject();
        }
        return gson.fromJson(responseBody, JsonObject.class);
    }

    private NekoTagUser requestNametags(String service, String username) throws IOException {
        String encodedUsername = URLEncoder.encode(username == null ? "" : username.trim(), StandardCharsets.UTF_8.name());
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/v5/games/api/" + service + "/nametags/" + encodedUsername).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("nekosunevr-api-key", apiKey);

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = readBody(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new IOException(extractErrorMessage(responseBody, status));
        }
        if (responseBody.isEmpty()) {
            return null;
        }
        return gson.fromJson(responseBody, NekoTagUser.class);
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private String extractErrorMessage(String responseBody, int status) {
        if (responseBody != null && !responseBody.trim().isEmpty()) {
            try {
                JsonObject object = gson.fromJson(responseBody, JsonObject.class);
                if (object != null && object.has("error")) {
                    String value = object.get("error").getAsString();
                    if (value != null && !value.trim().isEmpty()) {
                        return value;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "Request failed with status " + status;
    }

    private static String normalizeBaseUrl(String value) {
        String base = value == null ? "" : value.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
