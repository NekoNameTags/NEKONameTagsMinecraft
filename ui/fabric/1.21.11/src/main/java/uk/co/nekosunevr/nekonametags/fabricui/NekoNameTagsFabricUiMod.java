package uk.co.nekosunevr.nekonametags.fabricui;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NekoNameTagsFabricUiMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("NekoNameTags-UI");

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("nekoui")
                .executes(context -> openScreen()));
            dispatcher.register(ClientCommandManager.literal("nekonametagsui")
                .executes(context -> openScreen()));
        });

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("text.nekonametags_ui.loaded"), false);
            }
        });
        LOGGER.info("NekoNameTags UI addon loaded as an optional remote manager. Use /nekoui to edit nametags.");
    }

    private static int openScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(new NekoNameTagsFabricUiScreen(client.currentScreen)));
        return Command.SINGLE_SUCCESS;
    }
}
