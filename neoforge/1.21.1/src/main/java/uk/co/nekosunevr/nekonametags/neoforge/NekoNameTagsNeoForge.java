package uk.co.nekosunevr.nekonametags.neoforge;

import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;

@Mod("nekonametags")
public final class NekoNameTagsNeoForge {
    private static final Logger LOGGER = LogManager.getLogger("NekoNameTags");
    private static final long DEFAULT_REFRESH_SECONDS = 60L;

    public NekoNameTagsNeoForge() {
        String apiUrl = System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        );
        long refreshSeconds = parseRefreshSeconds(System.getProperty("nekonametags.refresh.seconds"));
        long refreshMillis = refreshSeconds * 1000L;
        NekoTagRepository repository = new NekoTagRepository(apiUrl, null, refreshMillis, null);
        try {
            int count = repository.reload().size();
            LOGGER.info("NekoNameTags (NeoForge) loaded {} entries. API refresh every {}s.", count, refreshSeconds);
        } catch (Exception ex) {
            LOGGER.warn("NekoNameTags (NeoForge) API reload failed: {}", ex.getMessage());
        }

        if (isClientEnvironment()) {
            NekoNameTagsNeoForgeClient.start(repository, LOGGER);
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
