package uk.co.nekosunevr.nekonametags.core;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NekoTagFormat {
    private static final Pattern SIZE_PATTERN = Pattern.compile("(?i)<size\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)\\s*>");
    private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)<color\\s*=\\s*#?([0-9a-f]{6})([0-9a-f]{2})?\\s*>");

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
            .replaceAll("<[^>]+>", "")
            .trim();
    }

    public static ParsedTagLine parse(String rawLine) {
        if (rawLine == null) {
            return new ParsedTagLine("", "", TagEffectType.NONE, 0xFFFFFF, false, false, 16.0f);
        }

        String source = rawLine;
        TagEffectType effectType = TagEffectType.NONE;
        if (source.contains("#rainbow#")) {
            effectType = TagEffectType.RAINBOW;
        } else if (source.contains("#animationtag#")) {
            effectType = TagEffectType.ANIMATED;
        }

        boolean bold = source.toLowerCase(Locale.ROOT).contains("<b>");
        boolean italic = source.toLowerCase(Locale.ROOT).contains("<i>");

        float size = 16.0f;
        Matcher sizeMatcher = SIZE_PATTERN.matcher(source);
        if (sizeMatcher.find()) {
            try {
                size = Float.parseFloat(sizeMatcher.group(1));
            } catch (NumberFormatException ignored) {
                size = 16.0f;
            }
        }

        int color = 0xFFFFFF;
        Matcher colorMatcher = COLOR_PATTERN.matcher(source);
        if (colorMatcher.find()) {
            try {
                color = Integer.parseInt(colorMatcher.group(1), 16);
            } catch (NumberFormatException ignored) {
                color = 0xFFFFFF;
            }
        }

        return new ParsedTagLine(rawLine, cleanMarkup(rawLine), effectType, color, bold, italic, size);
    }
}
