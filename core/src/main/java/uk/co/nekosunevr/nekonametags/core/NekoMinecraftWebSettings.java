package uk.co.nekosunevr.nekonametags.core;

public final class NekoMinecraftWebSettings {
    private String MinecraftUserID;
    private boolean TurnOnMinecraft;

    public String getMinecraftUserId() {
        return MinecraftUserID == null ? "" : MinecraftUserID;
    }

    public boolean isTurnOnMinecraft() {
        return TurnOnMinecraft;
    }
}
