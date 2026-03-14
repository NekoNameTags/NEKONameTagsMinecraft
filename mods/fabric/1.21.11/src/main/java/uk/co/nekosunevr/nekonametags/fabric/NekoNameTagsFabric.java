package uk.co.nekosunevr.nekonametags.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;

public final class NekoNameTagsFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("NekoNameTags");
    private static final long DEFAULT_REFRESH_SECONDS = 60L;

    @Override
    public void onInitialize() {
        String apiUrl = System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        );
        long refreshSeconds = parseRefreshSeconds(System.getProperty("nekonametags.refresh.seconds"));
        long refreshMillis = refreshSeconds * 1000L;
        NekoTagRepository repository = new NekoTagRepository(apiUrl, null, refreshMillis, null);
        try {
            int count = repository.reload().size();
            LOGGER.info("NekoNameTags (Fabric) loaded {} entries. API refresh every {}s.", count, refreshSeconds);
        } catch (Exception ex) {
            LOGGER.warn("NekoNameTags (Fabric) API reload failed: {}", ex.getMessage());
        }

        if (FabricLoader.getInstance().getEnvironmentType().name().equalsIgnoreCase("CLIENT")) {
            NekoNameTagsFabricClient.start(repository, LOGGER);
        }
    }

    private static long parseRefreshSeconds(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_REFRESH_SECONDS;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value <= 0L ? DEFAULT_REFRESH_SECONDS : value;
        } catch (NumberFormatException ignored) {
            return DEFAULT_REFRESH_SECONDS;
        }
    }
}
