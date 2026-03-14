package uk.co.nekosunevr.nekonametags.core;

public final class NekoMinecraftWebTag {
    private int id;
    private String text;
    private String cleanText;
    private String size;
    private boolean hasAnimation;
    private String animationType;
    private boolean active;

    public int getId() {
        return id;
    }

    public String getText() {
        return text == null ? "" : text;
    }

    public String getCleanText() {
        return cleanText == null ? "" : cleanText;
    }

    public String getSize() {
        return size == null ? "1" : size;
    }

    public boolean isHasAnimation() {
        return hasAnimation;
    }

    public String getAnimationType() {
        return animationType == null ? "" : animationType;
    }

    public boolean isActive() {
        return active;
    }
}
