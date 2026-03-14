package uk.co.nekosunevr.nekonametags.core;

public final class NekoMinecraftWebBigTag {
    private int id;
    private String text;
    private boolean active;

    public int getId() {
        return id;
    }

    public String getText() {
        return text == null ? "" : text;
    }

    public boolean isActive() {
        return active;
    }
}
