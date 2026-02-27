package uk.co.nekosunevr.nekonametags.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;

public final class NekoNameTagsFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("NekoNameTags");

    @Override
    public void onInitialize() {
        String apiUrl = System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        );
        NekoTagRepository repository = new NekoTagRepository(apiUrl);
        try {
            int count = repository.reload().size();
            LOGGER.info("NekoNameTags (Fabric) loaded {} entries.", count);
        } catch (Exception ex) {
            LOGGER.warn("NekoNameTags (Fabric) API reload failed: {}", ex.getMessage());
        }

        if (FabricLoader.getInstance().getEnvironmentType().name().equalsIgnoreCase("CLIENT")) {
            NekoNameTagsFabricClient.start(repository, LOGGER);
        }
    }
}
