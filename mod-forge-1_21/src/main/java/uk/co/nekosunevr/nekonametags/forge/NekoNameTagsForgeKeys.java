package uk.co.nekosunevr.nekonametags.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = "nekonametags", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
final class NekoNameTagsForgeKeys {
    private static final AtomicBoolean TOGGLE_REQUESTED = new AtomicBoolean(false);
    private static final AtomicBoolean RELOAD_REQUESTED = new AtomicBoolean(false);

    private static KeyMapping toggleKey;
    private static KeyMapping reloadKey;

    private NekoNameTagsForgeKeys() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
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
