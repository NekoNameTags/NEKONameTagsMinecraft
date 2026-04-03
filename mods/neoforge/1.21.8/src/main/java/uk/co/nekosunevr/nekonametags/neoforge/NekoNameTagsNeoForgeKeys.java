package uk.co.nekosunevr.nekonametags.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

final class NekoNameTagsNeoForgeKeys {
    private static final AtomicBoolean TOGGLE_REQUESTED = new AtomicBoolean(false);
    private static final AtomicBoolean RELOAD_REQUESTED = new AtomicBoolean(false);

    private static KeyMapping toggleKey;
    private static KeyMapping reloadKey;

    private NekoNameTagsNeoForgeKeys() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(NekoNameTagsNeoForgeKeys::onRegisterKeys);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        toggleKey = new KeyMapping(
            "key.nekonametags.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.nekonametags"
        );
        reloadKey = new KeyMapping(
            "key.nekonametags.reload",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.nekonametags"
        );
        event.register(toggleKey);
        event.register(reloadKey);
    }

    static boolean consumeToggleRequested() {
        return consume(toggleKey, TOGGLE_REQUESTED);
    }

    static boolean consumeReloadRequested() {
        return consume(reloadKey, RELOAD_REQUESTED);
    }

    private static boolean consume(KeyMapping key, AtomicBoolean flag) {
        if (key != null) {
            while (key.consumeClick()) {
                flag.set(true);
            }
        }
        return flag.getAndSet(false);
    }
}
