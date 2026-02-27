package uk.co.nekosunevr.nekonametags.sponge;

import com.google.inject.Inject;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.plugin.builtin.jvm.Plugin;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;

@Plugin("nekonametags")
public final class NekoNameTagsSponge {
    private final Logger logger;

    @Inject
    public NekoNameTagsSponge(Logger logger) {
        this.logger = logger;
    }

    @Listener
    public void onStarted(StartedEngineEvent<?> event) {
        String apiUrl = System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        );
        NekoTagRepository repository = new NekoTagRepository(apiUrl);
        try {
            int count = repository.reload().size();
            logger.info("NekoNameTags (Sponge) loaded {} entries.", count);
        } catch (Exception ex) {
            logger.warn("NekoNameTags (Sponge) API reload failed: {}", ex.getMessage());
        }
    }
}

