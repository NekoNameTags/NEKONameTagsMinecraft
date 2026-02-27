package uk.co.nekosunevr.nekonametags.core;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NekoUpdateChecker {
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/NekoNameTags/NEKONameTagsMinecraft/releases/latest";

    private NekoUpdateChecker() {
    }

    public static UpdateResult checkForUpdate(String currentVersion) {
        if (currentVersion == null || currentVersion.trim().isEmpty()) {
            return UpdateResult.noUpdate();
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "NekoNameTags-Updater");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return UpdateResult.noUpdate();
            }

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                GithubRelease release = new Gson().fromJson(reader, GithubRelease.class);
                if (release == null || release.tag_name == null || release.tag_name.trim().isEmpty()) {
                    return UpdateResult.noUpdate();
                }

                String latest = release.tag_name.trim();
                if (!isNewer(latest, currentVersion)) {
                    return UpdateResult.noUpdate();
                }

                String url = release.html_url == null ? "" : release.html_url.trim();
                return new UpdateResult(true, currentVersion, latest, url);
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            return UpdateResult.noUpdate();
        }
    }

    private static boolean isNewer(String latestRaw, String currentRaw) {
        VersionParts latest = parseVersion(latestRaw);
        VersionParts current = parseVersion(currentRaw);

        int max = Math.max(latest.numbers.size(), current.numbers.size());
        for (int i = 0; i < max; i++) {
            int l = i < latest.numbers.size() ? latest.numbers.get(i) : 0;
            int c = i < current.numbers.size() ? current.numbers.get(i) : 0;
            if (l > c) {
                return true;
            }
            if (l < c) {
                return false;
            }
        }

        if (current.preRelease && !latest.preRelease) {
            return true;
        }
        return false;
    }

    private static VersionParts parseVersion(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        String[] tokens = normalized.split("[^a-z0-9]+");
        List<Integer> numbers = new ArrayList<Integer>();
        boolean preRelease = false;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.matches("\\d+")) {
                try {
                    numbers.add(Integer.parseInt(token));
                } catch (NumberFormatException ignored) {
                    numbers.add(0);
                }
                continue;
            }
            if (token.contains("snapshot") || token.contains("alpha") || token.contains("beta") || token.contains("rc") || token.contains("pre")) {
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

        public static UpdateResult noUpdate() {
            return new UpdateResult(false, "", "", "");
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
        String html_url;
    }
}
