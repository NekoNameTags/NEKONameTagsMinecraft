package uk.co.nekosunevr.nekonametags.neoforge;

import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;

@Mod("nekonametags")
public final class NekoNameTagsNeoForge {
    private static final Logger LOGGER = LogManager.getLogger("NekoNameTags");

    public NekoNameTagsNeoForge() {
        String apiUrl = System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        );
        NekoTagRepository repository = new NekoTagRepository(apiUrl);
        try {
            int count = repository.reload().size();
            LOGGER.info("NekoNameTags (NeoForge) loaded {} entries.", count);
        } catch (Exception ex) {
            LOGGER.warn("NekoNameTags (NeoForge) API reload failed: {}", ex.getMessage());
        }
    }
}

