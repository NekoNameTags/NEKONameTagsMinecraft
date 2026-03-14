package uk.co.nekosunevr.nekonametags.core;

import java.util.Collections;
import java.util.List;

public final class NekoMinecraftWebState {
    private String platform;
    private String minecraftUserId;
    private boolean supportUnlocked;
    private int tagLimit;
    private List<String> automaticTags;
    private List<NekoMinecraftWebTag> tags;
    private List<NekoMinecraftWebBigTag> bigTags;

    public String getPlatform() {
        return platform == null ? "Minecraft" : platform;
    }

    public String getMinecraftUserId() {
        return minecraftUserId == null ? "" : minecraftUserId;
    }

    public boolean isSupportUnlocked() {
        return supportUnlocked;
    }

    public int getTagLimit() {
        return tagLimit;
    }

    public List<String> getAutomaticTags() {
        return automaticTags == null ? Collections.<String>emptyList() : automaticTags;
    }

    public List<NekoMinecraftWebTag> getTags() {
        return tags == null ? Collections.<NekoMinecraftWebTag>emptyList() : tags;
    }

    public List<NekoMinecraftWebBigTag> getBigTags() {
        return bigTags == null ? Collections.<NekoMinecraftWebBigTag>emptyList() : bigTags;
    }
}
