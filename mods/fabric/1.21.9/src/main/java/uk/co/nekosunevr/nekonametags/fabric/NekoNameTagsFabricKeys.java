package uk.co.nekosunevr.nekonametags.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

final class NekoNameTagsFabricKeys {
    private static final AtomicBoolean TOGGLE_REQUESTED = new AtomicBoolean(false);
    private static final AtomicBoolean RELOAD_REQUESTED = new AtomicBoolean(false);

    private static KeyBinding toggleKey;
    private static KeyBinding reloadKey;
    private static boolean registered;
    private static final KeyBinding.Category NEKO_CATEGORY = KeyBinding.Category.create(
        Identifier.of("nekonametags", "nekonametags")
    );

    private NekoNameTagsFabricKeys() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.nekonametags.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            NEKO_CATEGORY
        ));
        reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.nekonametags.reload",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            NEKO_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                TOGGLE_REQUESTED.set(true);
            }
            while (reloadKey.wasPressed()) {
                RELOAD_REQUESTED.set(true);
            }
        });
    }

    static boolean consumeToggleRequested() {
        return TOGGLE_REQUESTED.getAndSet(false);
    }

    static boolean consumeReloadRequested() {
        return RELOAD_REQUESTED.getAndSet(false);
    }
}
