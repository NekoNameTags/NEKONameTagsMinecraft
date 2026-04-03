package uk.co.nekosunevr.nekonametags.forge;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;

@Mod(
    modid = NekoNameTagsForge.MOD_ID,
    name = NekoNameTagsForge.MOD_NAME,
    version = NekoNameTagsForge.VERSION,
    acceptableRemoteVersions = "*"
)
public final class NekoNameTagsForge {
    static final String MOD_ID = "nekonametags";
    static final String MOD_NAME = "NekoNameTags";
    static final String VERSION = "0.1.10";

    private static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public NekoNameTagsForge() {
        String apiUrl = System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        );
        NekoTagRepository repository = new NekoTagRepository(apiUrl);
        try {
            int count = repository.reload().size();
            LOGGER.info("{} (Forge {}) loaded {} entries.", MOD_NAME, System.getProperty("minecraft_version", "legacy"), count);
        } catch (Exception ex) {
            LOGGER.warn("{} API reload failed: {}", MOD_NAME, ex.getMessage());
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