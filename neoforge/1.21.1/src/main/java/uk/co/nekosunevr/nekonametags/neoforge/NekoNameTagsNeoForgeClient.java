package uk.co.nekosunevr.nekonametags.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;
import uk.co.nekosunevr.nekonametags.core.TagEffectType;
import uk.co.nekosunevr.nekonametags.core.TagEffects;
import uk.co.nekosunevr.nekonametags.core.NekoUpdateChecker;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class NekoNameTagsNeoForgeClient {
    private static final long RELOAD_INTERVAL_MS = 30_000L;
    private static final float MIN_LINE_SCALE = 0.7F;
    private static final float MAX_LINE_SCALE = 3.0F;
    private static final float ROW_PIXEL_GAP = 2.0F;
    private static final float VANILLA_NAME_SCALE = 0.025F;
    private static final float NAME_TAG_Y_OFFSET = 0.5F;
    private static final long DEBUG_LOG_INTERVAL_MS = 5_000L;
    private static volatile boolean started;
    private static volatile boolean enabled = true;
    private static volatile List<ParsedTagLine> selfLines = Collections.emptyList();
    private static volatile boolean updateCheckDone;
    private static volatile boolean essentialInstalled;
    private static NekoClientSettings settings;
    private static volatile Map<UUID, List<ParsedTagLine>> renderedTagLines = Collections.emptyMap();
    private static volatile long lastDebugLogAt;
    private static volatile Logger runtimeLogger;

    private NekoNameTagsNeoForgeClient() {
    }

    static synchronized void start(NekoTagRepository repository, Logger logger) {
        if (started) {
            return;
        }
        started = true;
        runtimeLogger = logger;
        settings = NekoClientSettings.loadDefault();
        enabled = settings.isEnabled();
        essentialInstalled = isEssentialInstalled();

        Thread worker = new Thread(() -> runLoop(repository, logger), "NekoNameTags-NeoForge-Client");
        worker.setDaemon(true);
        worker.start();
    }

    private static void runLoop(NekoTagRepository repository, Logger logger) {
        long nextReloadAt = 0L;
        while (true) {
            long now = System.currentTimeMillis();
            if (NekoNameTagsNeoForgeKeys.consumeToggleRequested()) {
                enabled = !enabled;
                settings.setEnabled(enabled);
                settings.saveDefault();
                logger.info("NekoNameTags visibility toggled: {}", enabled ? "ON" : "OFF");
                sendClientMessage("NekoNameTags visibility: " + (enabled ? "ON" : "OFF"));
                if (!enabled) {
                    selfLines = Collections.emptyList();
                }
            }
            if (NekoNameTagsNeoForgeKeys.consumeReloadRequested()) {
                int count = reloadNow(repository, logger);
                nextReloadAt = now + RELOAD_INTERVAL_MS;
                if (count >= 0) {
                    sendClientMessage("NekoNameTags reloaded: " + count + " entries.");
                } else {
                    sendClientMessage("NekoNameTags reload failed. Check latest.log.");
                }
            }
            if (now >= nextReloadAt) {
                reloadNow(repository, logger);
                nextReloadAt = now + RELOAD_INTERVAL_MS;
            }
            if (!updateCheckDone) {
                updateCheckDone = true;
                checkForUpdatesAndNotify(logger);
            }
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> applyTags(mc, repository));

            try {
                Thread.sleep(50L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static int reloadNow(NekoTagRepository repository, Logger logger) {
        try {
            return repository.reload().size();
        } catch (Exception ex) {
            logger.warn("NekoNameTags client reload failed: {}", ex.getMessage());
            return -1;
        }
    }

    private static void checkForUpdatesAndNotify(Logger logger) {
        String currentVersion = getCurrentVersion();
        NekoUpdateChecker.UpdateResult result = NekoUpdateChecker.checkForUpdate(currentVersion);
        if (!result.isUpdateAvailable()) {
            return;
        }
        logger.info("NekoNameTags update available: {} -> {}", result.getCurrentVersion(), result.getLatestVersion());
        sendClientMessage("NekoNameTags update available: " + result.getCurrentVersion() + " -> " + result.getLatestVersion());
        if (!result.getReleaseUrl().isEmpty()) {
            sendClientMessage("Download: " + result.getReleaseUrl());
        }
    }

    private static String getCurrentVersion() {
        try {
            return ModList.get().getModContainerById("nekonametags")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void sendClientMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }

    private static void applyTags(Minecraft mc, NekoTagRepository repository) {
        if (mc.level == null || mc.player == null) {
            renderedTagLines = Collections.emptyMap();
            return;
        }

        selfLines = Collections.emptyList();
        if (!enabled) {
            renderedTagLines = Collections.emptyMap();
            return;
        }

        boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;
        Map<UUID, List<ParsedTagLine>> nextRendered = new HashMap<UUID, List<ParsedTagLine>>();
        for (Player player : mc.level.players()) {
            UUID profileId = player.getGameProfile() != null && player.getGameProfile().getId() != null
                ? player.getGameProfile().getId()
                : player.getUUID();
            String profileName = player.getGameProfile() != null ? player.getGameProfile().getName() : player.getName().getString();
            NekoTagUser user = repository.findForPlayer(NekoTagFormat.normalizePlayerId(profileId), profileName);
            if (user == null) {
                continue;
            }

            boolean isSelf = mc.player != null && player.getUUID().equals(mc.player.getUUID());
            if (isSelf && firstPerson) {
                continue;
            }
            boolean includeNameLine = false;
            List<ParsedTagLine> lines = buildParsedLines(user, profileName, includeNameLine);
            if (lines.isEmpty()) {
                continue;
            }

            nextRendered.put(player.getUUID(), new ArrayList<ParsedTagLine>(lines));
            if (isSelf) {
                selfLines = lines;
            }
        }
        long nowMs = System.currentTimeMillis();
        if (runtimeLogger != null && nowMs - lastDebugLogAt >= DEBUG_LOG_INTERVAL_MS) {
            lastDebugLogAt = nowMs;
            runtimeLogger.info("NekoNameTags debug: visiblePlayers={}, renderedRows={}",
                mc.level.players().size(), nextRendered.size());
        }
        renderedTagLines = nextRendered.isEmpty()
            ? Collections.<UUID, List<ParsedTagLine>>emptyMap()
            : Collections.unmodifiableMap(nextRendered);
    }

    private static Component buildVanillaNameComponent(List<ParsedTagLine> lines, long nowMs) {
        MutableComponent combined = Component.empty();
        boolean hasAny = false;
        for (int i = 0; i < lines.size(); i++) {
            Component line = buildStyledLineComponent(lines.get(i), nowMs);
            if (line == null) {
                continue;
            }
            if (hasAny) {
                combined.append("\n");
            }
            combined.append(line);
            hasAny = true;
        }
        return hasAny ? combined : null;
    }

    private static List<ParsedTagLine> buildParsedLines(NekoTagUser user, String playerName, boolean includeNameLine) {
        List<ParsedTagLine> lines = new ArrayList<ParsedTagLine>(8);
        String[] bigLines = user.getBigPlatesText();
        for (String raw : bigLines) {
            ParsedTagLine big = NekoTagFormat.parse(raw);
            if (!big.getText().isEmpty()) {
                lines.add(big);
            }
        }

        String[] normalLines = user.getNamePlatesText();
        for (String raw : normalLines) {
            ParsedTagLine normal = NekoTagFormat.parse(raw);
            if (!normal.getText().isEmpty()) {
                lines.add(normal);
            }
        }

        if (lines.isEmpty()) {
            return lines;
        }

        if (includeNameLine) {
            String cleanName = playerName == null ? "" : playerName.trim();
            if (!cleanName.isEmpty()) {
                lines.add(new ParsedTagLine(cleanName, cleanName, TagEffectType.NONE, 0xFFFFFF, false, false, 16.0f));
            }
        }
        return lines;
    }

    private static boolean isEssentialInstalled() {
        try {
            return ModList.get().isLoaded("essential");
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isEnabled() {
        return enabled;
    }

    static List<ParsedTagLine> getSelfLines() {
        return selfLines;
    }

    static List<ParsedTagLine> getRenderedTagLines(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return renderedTagLines.get(playerId);
    }

    static void onRenderNameTag(RenderNameTagEvent event) {
        if (!enabled || event == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        List<ParsedTagLine> lines = getRenderedTagLines(player.getUUID());
        if (lines == null || lines.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON
            && mc.player != null
            && player.getUUID().equals(mc.player.getUUID())) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        Font font = mc.font;
        int bg = ((int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24);
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0.0D, player.getBbHeight() + NAME_TAG_Y_OFFSET, 0.0D);
        event.getPoseStack().mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        event.getPoseStack().scale(-VANILLA_NAME_SCALE, -VANILLA_NAME_SCALE, VANILLA_NAME_SCALE);

        float y = -(font.lineHeight + ROW_PIXEL_GAP);
        for (int i = lines.size() - 1; i >= 0; i--) {
            ParsedTagLine line = lines.get(i);
            Component content = buildStyledLineComponent(line, nowMs);
            if (content == null) {
                continue;
            }

            float ratio = clampLineScale(line.getSize() / 16.0f);
            event.getPoseStack().pushPose();
            event.getPoseStack().scale(ratio, ratio, 1.0F);
            Matrix4f scaledMatrix = event.getPoseStack().last().pose();
            float drawY = y / ratio;
            float x = -font.width(content) / 2.0F;
            font.drawInBatch(content, x, drawY, 0x20FFFFFF, false, scaledMatrix, event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, bg, event.getPackedLight());
            font.drawInBatch(content, x, drawY, 0xFFFFFFFF, false, scaledMatrix, event.getMultiBufferSource(), Font.DisplayMode.NORMAL, bg, event.getPackedLight());
            event.getPoseStack().popPose();

            y -= (font.lineHeight * ratio) + ROW_PIXEL_GAP;
        }
        event.getPoseStack().popPose();
        event.setCanRender(TriState.DEFAULT);
    }

    private static float clampLineScale(float ratio) {
        return Math.max(MIN_LINE_SCALE, Math.min(MAX_LINE_SCALE, ratio));
    }

    static Component buildStyledLineComponent(ParsedTagLine line, long nowMs) {
        String text = line.getText();
        if (line.getEffectType() == TagEffectType.ANIMATED) {
            text = TagEffects.animatedWindow(text, nowMs);
        }
        if (text == null || text.isEmpty()) {
            return null;
        }

        if (line.getEffectType() == TagEffectType.RAINBOW) {
            MutableComponent rainbow = Component.empty();
            for (int i = 0; i < text.length(); i++) {
                int rgb = TagEffects.rainbowRgb(nowMs, i * 80) & 0x00FFFFFF;
                rainbow.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(style -> style.withColor(rgb).withBold(line.isBold()).withItalic(line.isItalic())));
            }
            return rainbow;
        }

        int rgb = line.getColorRgb() & 0x00FFFFFF;
        return Component.literal(text)
            .withStyle(style -> style.withColor(rgb).withBold(line.isBold()).withItalic(line.isItalic()));
    }

}
