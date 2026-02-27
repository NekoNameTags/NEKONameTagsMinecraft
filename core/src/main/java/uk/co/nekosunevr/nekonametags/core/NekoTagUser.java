package uk.co.nekosunevr.nekonametags.core;

import com.google.gson.annotations.SerializedName;

import java.util.Arrays;

public final class NekoTagUser {
    @SerializedName(value = "id", alternate = {"Id"})
    private int id;

    @SerializedName(value = "UserId", alternate = {"userId"})
    private String userId;

    @SerializedName(value = "NamePlatesText", alternate = {"namePlatesText"})
    private String[] namePlatesText;

    @SerializedName(value = "BigPlatesText", alternate = {"bigPlatesText"})
    private String[] bigPlatesText;

    @SerializedName(value = "Color", alternate = {"color"})
    private int[] color;

    @SerializedName(value = "isLive", alternate = {"IsLive"})
    private boolean isLive;

    @SerializedName(value = "platform", alternate = {"Platform"})
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
