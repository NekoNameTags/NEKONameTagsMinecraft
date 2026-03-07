package uk.co.nekosunevr.nekonametags.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.slf4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;
import uk.co.nekosunevr.nekonametags.core.TagEffectType;
import uk.co.nekosunevr.nekonametags.core.TagEffects;
import uk.co.nekosunevr.nekonametags.core.NekoUpdateChecker;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class NekoNameTagsFabricClient {
    private static final long RELOAD_INTERVAL_MS = 30_000L;
    private static final double BASE_OFFSET = 0.18D;
    private static final double VANILLA_NAME_CLEARANCE = 0.08D;
    private static final double SELF_LINE_GAP_BASE = 0.18D;
    private static final double SELF_LINE_GAP_EXTRA = 0.03D;
    private static volatile boolean started;
    private static volatile boolean enabled = true;
    private static volatile List<ParsedTagLine> selfLines = Collections.emptyList();
    private static volatile boolean updateCheckDone;
    private static NekoClientSettings settings;
    private static final Map<UUID, List<ArmorStandEntity>> playerHolograms = new HashMap<UUID, List<ArmorStandEntity>>();
    private static volatile boolean essentialInstalled;

    private NekoNameTagsFabricClient() {
    }

    static synchronized void start(NekoTagRepository repository, Logger logger) {
        if (started) {
            return;
        }
        started = true;
        NekoNameTagsFabricKeys.register();
        settings = NekoClientSettings.loadDefault();
        enabled = settings.isEnabled();
        essentialInstalled = isEssentialInstalled();

        Thread worker = new Thread(() -> runLoop(repository, logger), "NekoNameTags-Fabric-Client");
        worker.setDaemon(true);
        worker.start();
    }

    private static void runLoop(NekoTagRepository repository, Logger logger) {
        long nextReloadAt = 0L;
        while (true) {
            long now = System.currentTimeMillis();
            if (NekoNameTagsFabricKeys.consumeToggleRequested()) {
                enabled = !enabled;
                settings.setEnabled(enabled);
                settings.saveDefault();
                logger.info("NekoNameTags visibility toggled: {}", enabled ? "ON" : "OFF");
                sendClientMessage("NekoNameTags visibility: " + (enabled ? "ON" : "OFF"));
                if (!enabled) {
                    selfLines = Collections.emptyList();
                }
            }
            if (NekoNameTagsFabricKeys.consumeReloadRequested()) {
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
            MinecraftClient mc = MinecraftClient.getInstance();
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
            return FabricLoader.getInstance().getModContainer("nekonametags")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void sendClientMessage(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(message), false);
            }
        });
    }

    private static void applyTags(MinecraftClient mc, NekoTagRepository repository) {
        if (mc.world == null || mc.player == null) {
            clearAllHolograms();
            return;
        }

        selfLines = Collections.emptyList();
        if (!enabled) {
            clearAllHolograms();
            return;
        }

        boolean firstPerson = mc.options.getPerspective() == Perspective.FIRST_PERSON;
        long now = System.currentTimeMillis();
        Set<UUID> activePlayers = new HashSet<UUID>();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            UUID uuid = player.getUuid();
            NekoTagUser user = repository.findForPlayer(NekoTagFormat.normalizePlayerId(uuid), player.getName().getString());
            if (user == null) {
                continue;
            }

            boolean isSelf = mc.player != null && player.getUuid().equals(mc.player.getUuid());
            if (isSelf && firstPerson) {
                continue;
            }
            boolean includeNameLine = !(essentialInstalled && isSelf);
            List<ParsedTagLine> lines = buildParsedLines(user, player.getGameProfile().getName(), includeNameLine);
            if (lines.isEmpty()) {
                continue;
            }

            activePlayers.add(uuid);
            updatePlayerHolograms(mc, player, lines, now);
            if (isSelf) {
                selfLines = lines;
            }
        }
        clearStaleHolograms(activePlayers);
    }

    private static Text buildVanillaNameText(List<ParsedTagLine> lines, long nowMs) {
        MutableText combined = Text.empty();
        boolean hasAny = false;
        for (int i = 0; i < lines.size(); i++) {
            Text line = buildStyledLineText(lines.get(i), nowMs);
            if (line == null) {
                continue;
            }
            if (hasAny) {
                combined.append(Text.literal("\n"));
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
            return FabricLoader.getInstance().isModLoaded("essential");
        } catch (Exception ignored) {
            return false;
        }
    }

    static Text buildStyledLineText(ParsedTagLine line, long nowMs) {
        String text = line.getText();
        if (line.getEffectType() == TagEffectType.ANIMATED) {
            text = TagEffects.animatedWindow(text, nowMs);
        }
        if (text == null || text.isEmpty()) {
            return null;
        }

        if (line.getEffectType() == TagEffectType.RAINBOW) {
            MutableText rainbow = Text.empty();
            for (int i = 0; i < text.length(); i++) {
                int rgb = TagEffects.rainbowRgb(nowMs, i * 80) & 0x00FFFFFF;
                Style style = Style.EMPTY
                    .withColor(TextColor.fromRgb(rgb))
                    .withBold(line.isBold())
                    .withItalic(line.isItalic());
                rainbow.append(Text.literal(String.valueOf(text.charAt(i))).setStyle(style));
            }
            return rainbow;
        }

        int rgb = line.getColorRgb() & 0x00FFFFFF;
        Style style = Style.EMPTY
            .withColor(TextColor.fromRgb(rgb))
            .withBold(line.isBold())
            .withItalic(line.isItalic());
        return Text.literal(text).setStyle(style);
    }

    static boolean isEnabled() {
        return enabled;
    }

    static List<ParsedTagLine> getSelfLines() {
        return selfLines;
    }

    private static void updatePlayerHolograms(MinecraftClient mc, AbstractClientPlayerEntity player, List<ParsedTagLine> lines, long nowMs) {
        if (mc.world == null || player == null || lines == null || lines.isEmpty()) {
            return;
        }

        UUID playerId = player.getUuid();
        List<ArmorStandEntity> stands = playerHolograms.get(playerId);
        if (stands == null) {
            stands = new ArrayList<ArmorStandEntity>();
            playerHolograms.put(playerId, stands);
        }

        while (stands.size() < lines.size()) {
            ArmorStandEntity stand = new ArmorStandEntity(mc.world, player.getX(), player.getY(), player.getZ());
            stand.setInvisible(false);
            stand.setNoGravity(true);
            stand.setSilent(true);
            stand.setCustomNameVisible(true);
            mc.world.addEntity(stand);
            stands.add(stand);
        }
        while (stands.size() > lines.size()) {
            ArmorStandEntity removed = stands.remove(stands.size() - 1);
            if (removed != null && removed.isAlive()) {
                removed.discard();
            }
        }

        double y = player.getY() + player.getHeight() + BASE_OFFSET + VANILLA_NAME_CLEARANCE;
        for (int i = 0; i < lines.size(); i++) {
            ParsedTagLine line = lines.get(i);
            ArmorStandEntity stand = stands.get(i);
            if (stand == null || !stand.isAlive()) {
                continue;
            }
            if (i > 0) {
                ParsedTagLine prev = lines.get(i - 1);
                float prevRatio = Math.max(0.7f, Math.min(3.0f, prev.getSize() / 16.0f));
                y -= (SELF_LINE_GAP_BASE * prevRatio) + SELF_LINE_GAP_EXTRA;
            }
            stand.setPosition(player.getX(), y, player.getZ());
            stand.setCustomName(buildStyledLineText(line, nowMs));
            stand.setCustomNameVisible(true);
        }
    }

    private static void clearStaleHolograms(Set<UUID> activePlayers) {
        List<UUID> stalePlayers = new ArrayList<UUID>();
        for (UUID playerId : playerHolograms.keySet()) {
            if (!activePlayers.contains(playerId)) {
                stalePlayers.add(playerId);
            }
        }
        for (UUID playerId : stalePlayers) {
            clearHologramsFor(playerId);
        }
    }

    private static void clearHologramsFor(UUID playerId) {
        List<ArmorStandEntity> stands = playerHolograms.remove(playerId);
        if (stands == null) {
            return;
        }
        for (ArmorStandEntity stand : stands) {
            if (stand != null && stand.isAlive()) {
                stand.discard();
            }
        }
    }

    private static void clearAllHolograms() {
        for (List<ArmorStandEntity> stands : playerHolograms.values()) {
            for (ArmorStandEntity stand : stands) {
                if (stand != null && stand.isAlive()) {
                    stand.discard();
                }
            }
        }
        playerHolograms.clear();
    }
}
