package uk.co.nekosunevr.nekonametags.core;

import java.util.Arrays;

public final class NekoTagUser {
    private int id;
    private String userId;
    private String[] namePlatesText;
    private String[] bigPlatesText;
    private int[] color;
    private boolean isLive;
    private String platform;

    public int getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String[] getNamePlatesText() {
        return namePlatesText == null ? new String[0] : namePlatesText;
    }

    public String[] getBigPlatesText() {
        return bigPlatesText == null ? new String[0] : bigPlatesText;
    }

    public int[] getColor() {
        if (color == null || color.length < 3) {
            return new int[] {255, 255, 255, 255};
        }
        if (color.length < 4) {
            return Arrays.copyOf(color, 4);
        }
        return color;
    }

    public boolean isLive() {
        return isLive;
    }

    public String getPlatform() {
        return platform;
    }
}

