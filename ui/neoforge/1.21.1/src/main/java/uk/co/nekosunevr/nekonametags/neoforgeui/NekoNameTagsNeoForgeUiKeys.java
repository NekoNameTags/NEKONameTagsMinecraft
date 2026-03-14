package uk.co.nekosunevr.nekonametags.neoforgeui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "nekonametags_ui", value = Dist.CLIENT)
final class NekoNameTagsNeoForgeUiKeys {
    private static KeyMapping openKey;

    private NekoNameTagsNeoForgeUiKeys() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        openKey = new KeyMapping(
            "key.nekonametags_ui.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.nekonametags_ui"
        );
        event.register(openKey);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (openKey == null) {
            return;
        }
        while (openKey.consumeClick()) {
            NekoNameTagsNeoForgeUiScreen.open();
        }
    }
}
