package uk.co.nekosunevr.nekonametags.forgeui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "nekonametags_ui", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
final class NekoNameTagsForgeUiKeys {
    private static KeyMapping openKey;

    private NekoNameTagsForgeUiKeys() {
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

    @Mod.EventBusSubscriber(modid = "nekonametags_ui", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class RuntimeEvents {
        private RuntimeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || openKey == null) {
                return;
            }
            while (openKey.consumeClick()) {
                NekoNameTagsForgeUiScreen.open();
            }
        }
    }
}
