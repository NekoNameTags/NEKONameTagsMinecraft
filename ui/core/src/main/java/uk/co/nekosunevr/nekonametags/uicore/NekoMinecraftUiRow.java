package uk.co.nekosunevr.nekonametags.uicore;

import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebBigTag;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebTag;

public final class NekoMinecraftUiRow {
    public enum Kind {
        NONE,
        NORMAL,
        BIG
    }

    private final Kind kind;
    private final int id;
    private final boolean active;
    private final String label;
    private final NekoMinecraftWebTag tag;
    private final NekoMinecraftWebBigTag bigTag;

    private NekoMinecraftUiRow(Kind kind, int id, boolean active, String label, NekoMinecraftWebTag tag, NekoMinecraftWebBigTag bigTag) {
        this.kind = kind;
        this.id = id;
        this.active = active;
        this.label = label;
        this.tag = tag;
        this.bigTag = bigTag;
    }

    public static NekoMinecraftUiRow normal(NekoMinecraftWebTag tag) {
        return new NekoMinecraftUiRow(
            Kind.NORMAL,
            tag.getId(),
            tag.isActive(),
            "[Tag] " + tag.getCleanText() + " | size=" + tag.getSize() + " | anim=" + (tag.isHasAnimation() ? tag.getAnimationType() : "none"),
            tag,
            null
        );
    }

    public static NekoMinecraftUiRow big(NekoMinecraftWebBigTag bigTag) {
        return new NekoMinecraftUiRow(
            Kind.BIG,
            bigTag.getId(),
            bigTag.isActive(),
            "[Big] " + bigTag.getText(),
            null,
            bigTag
        );
    }

    public Kind getKind() {
        return kind;
    }

    public int getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public String getLabel() {
        return label;
    }

    public NekoMinecraftWebTag getTag() {
        return tag;
    }

    public NekoMinecraftWebBigTag getBigTag() {
        return bigTag;
    }
}
