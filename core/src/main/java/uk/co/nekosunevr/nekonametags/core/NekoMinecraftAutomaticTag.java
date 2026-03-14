package uk.co.nekosunevr.nekonametags.core;

public final class NekoMinecraftAutomaticTag {
    private String text;
    private String service;
    private String username;
    private boolean isLive;

    public String getText() {
        return text == null ? "" : text;
    }

    public String getService() {
        return service == null ? "" : service;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public boolean isLive() {
        return isLive;
    }
}
