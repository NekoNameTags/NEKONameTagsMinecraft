package uk.co.nekosunevr.nekonametags.core;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class NekoMinecraftWebClient {
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String apiKey;

    public NekoMinecraftWebClient(String baseUrl, String apiKey) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public NekoMinecraftWebSettings fetchSettings() throws IOException {
        return request("GET", "/v5/account/minecraft/settings", null, NekoMinecraftWebSettings.class);
    }

    public NekoMinecraftWebSettings updateSettings(boolean turnOnMinecraft) throws IOException {
        return request("POST", "/v5/account/minecraft/settings",
            "{\"TurnOnMinecraft\":" + turnOnMinecraft + "}", NekoMinecraftWebSettings.class);
    }

    public NekoMinecraftWebState fetchState() throws IOException {
        return request("GET", "/v5/account/minecraft/nametags", null, NekoMinecraftWebState.class);
    }

    public void createTag(String tagName, String bigText, String size, boolean hasAnimation, String animationType) throws IOException {
        String payload = new StringBuilder()
            .append("{\"tagName\":").append(json(tagName))
            .append(",\"bigText\":").append(json(bigText))
            .append(",\"size\":").append(json(size))
            .append(",\"hasAnimation\":").append(hasAnimation)
            .append(",\"animationType\":").append(json(animationType))
            .append('}')
            .toString();
        request("POST", "/v5/account/minecraft/nametags", payload, Void.class);
    }

    public void updateTag(int id, String tagName, String size, boolean hasAnimation, String animationType, boolean active) throws IOException {
        String payload = new StringBuilder()
            .append("{\"tagName\":").append(json(tagName))
            .append(",\"size\":").append(json(size))
            .append(",\"hasAnimation\":").append(hasAnimation)
            .append(",\"animationType\":").append(json(animationType))
            .append(",\"active\":").append(active)
            .append('}')
            .toString();
        request("POST", "/v5/account/minecraft/nametags/" + id + "/update", payload, Void.class);
    }

    public void updateBigTag(int id, String bigText, boolean active) throws IOException {
        String payload = "{\"bigText\":" + json(bigText) + ",\"active\":" + active + "}";
        request("POST", "/v5/account/minecraft/big-tags/" + id + "/update", payload, Void.class);
    }

    public void deleteTag(int id) throws IOException {
        request("POST", "/v5/account/minecraft/nametags/" + id + "/delete", "{}", Void.class);
    }

    public void deleteBigTag(int id) throws IOException {
        request("POST", "/v5/account/minecraft/big-tags/" + id + "/delete", "{}", Void.class);
    }

    public void syncPresence(String username, String uuid, String serverHost, String serverLabel, boolean online) throws IOException {
        String payload = new StringBuilder()
            .append("{\"username\":").append(json(username))
            .append(",\"uuid\":").append(json(uuid))
            .append(",\"serverHost\":").append(json(serverHost))
            .append(",\"serverLabel\":").append(json(serverLabel))
            .append(",\"online\":").append(online)
            .append('}')
            .toString();
        request("POST", "/v5/account/minecraft/presence", payload, Void.class);
    }

    private <T> T request(String method, String path, String body, Class<T> responseType) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("nekosunevr-api-key", apiKey);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = readBody(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new IOException(extractErrorMessage(responseBody, status));
        }
        if (responseType == Void.class || responseBody.isEmpty()) {
            return null;
        }
        return gson.fromJson(responseBody, responseType);
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
                ErrorResponse error = gson.fromJson(responseBody, ErrorResponse.class);
                if (error != null && error.error != null && !error.error.trim().isEmpty()) {
                    return error.error;
                }
            } catch (Exception ignored) {
            }
        }
        return "Request failed with status " + status;
    }

    private String json(String value) {
        return gson.toJson(value == null ? "" : value);
    }

    private static String normalizeBaseUrl(String value) {
        String base = value == null ? "" : value.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static final class ErrorResponse {
        private String error;
    }
}
