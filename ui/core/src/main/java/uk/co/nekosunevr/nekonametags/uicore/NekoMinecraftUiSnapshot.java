package uk.co.nekosunevr.nekonametags.uicore;

import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebSettings;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebState;

public final class NekoMinecraftUiSnapshot {
    private final NekoMinecraftWebSettings settings;
    private final NekoMinecraftWebState state;

    public NekoMinecraftUiSnapshot(NekoMinecraftWebSettings settings, NekoMinecraftWebState state) {
        this.settings = settings;
        this.state = state;
    }

    public NekoMinecraftWebSettings getSettings() {
        return settings;
    }

    public NekoMinecraftWebState getState() {
        return state;
    }
}
