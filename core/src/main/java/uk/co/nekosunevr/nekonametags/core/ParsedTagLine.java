package uk.co.nekosunevr.nekonametags.core;

public final class ParsedTagLine {
    private final String raw;
    private final String text;
    private final TagEffectType effectType;
    private final int colorRgb;
    private final boolean bold;
    private final boolean italic;
    private final float size;

    public ParsedTagLine(
        String raw,
        String text,
        TagEffectType effectType,
        int colorRgb,
        boolean bold,
        boolean italic,
        float size
    ) {
        this.raw = raw;
        this.text = text;
        this.effectType = effectType;
        this.colorRgb = colorRgb;
        this.bold = bold;
        this.italic = italic;
        this.size = size;
    }

    public String getRaw() {
        return raw;
    }

    public String getText() {
        return text;
    }

    public TagEffectType getEffectType() {
        return effectType;
    }

    public int getColorRgb() {
        return colorRgb;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public float getSize() {
        return size;
    }
}
