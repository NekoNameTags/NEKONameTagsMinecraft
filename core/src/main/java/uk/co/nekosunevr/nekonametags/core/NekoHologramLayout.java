package uk.co.nekosunevr.nekonametags.core;

import java.util.ArrayList;
import java.util.List;

public final class NekoHologramLayout {
    public static final long DEFAULT_REFRESH_INTERVAL_MS = 60_000L;

    private NekoHologramLayout() {
    }

    public static List<ParsedTagLine> buildParsedLines(NekoTagUser user, String playerName, boolean includeNameLine) {
        List<ParsedTagLine> lines = new ArrayList<ParsedTagLine>(8);
        if (user == null) {
            return lines;
        }

        String[] bigLines = user.getBigPlatesText();
        for (String raw : bigLines) {
            ParsedTagLine big = NekoTagFormat.parse(raw);
            if (!big.getText().isEmpty()) {
                lines.add(big);
            }
        }

        String[] normalLines = user.getNamePlatesText();
        for (String raw : normalLines) {
            ParsedTagLine normal = NekoTagFormat.parse(raw);
            if (!normal.getText().isEmpty()) {
                lines.add(normal);
            }
        }

        if (lines.isEmpty()) {
            return lines;
        }

        if (includeNameLine) {
            String cleanName = playerName == null ? "" : playerName.trim();
            if (!cleanName.isEmpty()) {
                lines.add(new ParsedTagLine(cleanName, cleanName, TagEffectType.NONE, 0xFFFFFF, false, false, 16.0f));
            }
        }

        return lines;
    }

    public static double computeStackHeight(List<ParsedTagLine> lines, double lineGapBase, double lineGapExtra) {
        double total = 0.0D;
        for (int i = 1; i < lines.size(); i++) {
            total += computeLineGap(lines.get(i - 1), lineGapBase, lineGapExtra);
        }
        return total;
    }

    public static double computeLineGap(ParsedTagLine line, double lineGapBase, double lineGapExtra) {
        float ratio = Math.max(0.7f, Math.min(3.0f, line.getSize() / 16.0f));
        return (lineGapBase * ratio) + lineGapExtra;
    }
}
