package uk.co.nekosunevr.nekonametags.core;

import java.util.Locale;
import java.util.UUID;

public final class NekoTagFormat {
    private NekoTagFormat() {
    }

    public static String normalizePlayerId(UUID uuid) {
        return uuid == null ? "" : uuid.toString().toLowerCase(Locale.ROOT);
    }

    public static String firstLine(NekoTagUser user) {
        if (user == null) {
            return "";
        }
        String[] lines = user.getNamePlatesText();
        return lines.length == 0 ? "" : cleanMarkup(lines[0]);
    }

    public static String cleanMarkup(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("#rainbow#", "")
            .replace("#animationtag#", "")
            .trim();
    }
}

