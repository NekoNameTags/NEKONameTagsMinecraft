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
        return lines.length == 0 ? "" : parse(lines[0]).getText();
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

    public static ParsedTagLine parse(String rawLine) {
        if (rawLine == null) {
            return new ParsedTagLine("", "", TagEffectType.NONE);
        }

        TagEffectType effectType = TagEffectType.NONE;
        if (rawLine.contains("#rainbow#")) {
            effectType = TagEffectType.RAINBOW;
        } else if (rawLine.contains("#animationtag#")) {
            effectType = TagEffectType.ANIMATED;
        }
        return new ParsedTagLine(rawLine, cleanMarkup(rawLine), effectType);
    }
}
