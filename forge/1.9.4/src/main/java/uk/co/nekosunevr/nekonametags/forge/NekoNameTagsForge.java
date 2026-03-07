package uk.co.nekosunevr.nekonametags.forge;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;

@Mod("nekonametags")
public final class NekoNameTagsForge {
    private static final Logger LOGGER = LogManager.getLogger("NekoNameTags");

    public NekoNameTagsForge() {
        String apiUrl = System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        );
        NekoTagRepository repository = new NekoTagRepository(apiUrl);
        try {
            int count = repository.reload().size();
            LOGGER.info("NekoNameTags (Forge) loaded {} entries.", count);
        } catch (Exception ex) {
            LOGGER.warn("NekoNameTags (Forge) API reload failed: {}", ex.getMessage());
        }

        if (isClientEnvironment()) {
            NekoNameTagsForgeClient.start(repository, LOGGER);
        }
    }

    private static boolean isClientEnvironment() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
