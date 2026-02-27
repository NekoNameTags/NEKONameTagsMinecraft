package uk.co.nekosunevr.nekonametags.core;

public final class ParsedTagLine {
    private final String raw;
    private final String text;
    private final TagEffectType effectType;

    public ParsedTagLine(String raw, String text, TagEffectType effectType) {
        this.raw = raw;
        this.text = text;
        this.effectType = effectType;
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
}

