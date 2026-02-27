package uk.co.nekosunevr.nekonametags.core;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NekoUpdateChecker {
    private static final String RELEASE_REPO = "NekoNameTags/NEKONameTagsMinecraft";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + RELEASE_REPO + "/releases/latest";
    private static final String ALLOWED_SCHEME = "https";
    private static final String ALLOWED_HOST = "api.github.com";
    private static final String ALLOWED_PATH = "/repos/" + RELEASE_REPO + "/releases/latest";

    private NekoUpdateChecker() {
    }

    public static UpdateResult checkForUpdate(String currentVersion) {
        if (!isUpdateCheckEnabled()) {
            return UpdateResult.noUpdate(currentVersion);
        }

        try {
            validateEndpoint(LATEST_RELEASE_API);
            HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "NekoNameTags-VersionChecker");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return UpdateResult.noUpdate(currentVersion);
            }

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                GithubRelease release = new Gson().fromJson(reader, GithubRelease.class);
                if (release == null || release.tag_name == null || release.tag_name.trim().isEmpty()) {
                    return UpdateResult.noUpdate(currentVersion);
                }

                String latest = release.tag_name.trim();
                boolean updateAvailable = isLatestNewer(currentVersion, latest);
                String releaseUrl = "https://github.com/" + RELEASE_REPO + "/releases/latest";
                return new UpdateResult(updateAvailable, sanitizeVersion(currentVersion), latest, releaseUrl);
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            return UpdateResult.noUpdate(currentVersion);
        }
    }

    private static boolean isUpdateCheckEnabled() {
        return Boolean.parseBoolean(System.getProperty("nekonametags.update.check", "false"));
    }

    private static void validateEndpoint(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (!ALLOWED_SCHEME.equals(scheme)) {
            throw new IllegalStateException("Blocked update URL scheme");
        }
        if (!ALLOWED_HOST.equals(host)) {
            throw new IllegalStateException("Blocked update URL host");
        }
        if (!ALLOWED_PATH.equals(path)) {
            throw new IllegalStateException("Blocked update URL path");
        }
    }

    private static String sanitizeVersion(String version) {
        return version == null ? "" : version.trim();
    }

    private static boolean isLatestNewer(String current, String latest) {
        VersionParts currentParts = parseVersion(current);
        VersionParts latestParts = parseVersion(latest);

        int maxLen = Math.max(currentParts.numbers.size(), latestParts.numbers.size());
        for (int i = 0; i < maxLen; i++) {
            int c = i < currentParts.numbers.size() ? currentParts.numbers.get(i) : 0;
            int l = i < latestParts.numbers.size() ? latestParts.numbers.get(i) : 0;
            if (l > c) {
                return true;
            }
            if (l < c) {
                return false;
            }
        }
        if (currentParts.preRelease && !latestParts.preRelease) {
            return true;
        }
        return false;
    }

    private static VersionParts parseVersion(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        normalized = normalized.replace("snapshot", "-snapshot");
        normalized = normalized.replace("pre", "-pre");
        normalized = normalized.replace("rc", "-rc");
        String cleaned = normalized.replaceAll("[^0-9a-z.\\-]", "");
        String[] tokens = cleaned.split("[\\.\\-]+");
        List<Integer> numbers = new ArrayList<Integer>();
        boolean preRelease = false;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.matches("\\d+")) {
                numbers.add(Integer.parseInt(token));
            } else {
                preRelease = true;
            }
        }
        return new VersionParts(numbers, preRelease);
    }

    public static final class UpdateResult {
        private final boolean updateAvailable;
        private final String currentVersion;
        private final String latestVersion;
        private final String releaseUrl;

        public UpdateResult(boolean updateAvailable, String currentVersion, String latestVersion, String releaseUrl) {
            this.updateAvailable = updateAvailable;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.releaseUrl = releaseUrl;
        }

        public static UpdateResult noUpdate(String currentVersion) {
            return new UpdateResult(false, sanitizeVersion(currentVersion), "", "");
        }

        public boolean isUpdateAvailable() {
            return updateAvailable;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        public String getReleaseUrl() {
            return releaseUrl;
        }
    }

    private static final class VersionParts {
        private final List<Integer> numbers;
        private final boolean preRelease;

        private VersionParts(List<Integer> numbers, boolean preRelease) {
            this.numbers = numbers;
            this.preRelease = preRelease;
        }
    }

    private static final class GithubRelease {
        String tag_name;
    }
}
