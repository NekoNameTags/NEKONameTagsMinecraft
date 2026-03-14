package uk.co.nekosunevr.nekonametags.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.apache.logging.log4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoHologramLayout;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;
import uk.co.nekosunevr.nekonametags.core.TagEffectType;
import uk.co.nekosunevr.nekonametags.core.TagEffects;
import uk.co.nekosunevr.nekonametags.core.NekoUpdateChecker;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class NekoNameTagsForgeClient {
    private static final long RELOAD_INTERVAL_MS = NekoHologramLayout.DEFAULT_REFRESH_INTERVAL_MS;
    private static final double BASE_OFFSET = 0.18D;
    private static final double VANILLA_NAME_CLEARANCE = 0.08D;
    private static final double SELF_LINE_GAP_BASE = 0.18D;
    private static final double SELF_LINE_GAP_EXTRA = 0.03D;
    private static volatile boolean started;
    private static volatile boolean enabled = true;
    private static volatile List<ParsedTagLine> selfLines = Collections.emptyList();
    private static volatile boolean updateCheckDone;
    private static volatile boolean reloadRequested;
    private static volatile boolean essentialInstalled;
    private static NekoClientSettings settings;
    private static final Map<UUID, List<ArmorStand>> playerHolograms = new HashMap<UUID, List<ArmorStand>>();

    private NekoNameTagsForgeClient() {
    }

    static synchronized void start(NekoTagRepository repository, Logger logger) {
        if (started) {
            return;
        }
        started = true;
        settings = NekoClientSettings.loadDefault();
        enabled = settings.isEnabled();
        essentialInstalled = isEssentialInstalled();

        Thread worker = new Thread(() -> runLoop(repository, logger), "NekoNameTags-Forge-Client");
        worker.setDaemon(true);
        worker.start();
    }

    private static void runLoop(NekoTagRepository repository, Logger logger) {
        long nextReloadAt = 0L;
        while (true) {
            long now = System.currentTimeMillis();
            if (reloadRequested) {
                reloadRequested = false;
                reloadNow(repository, logger);
                nextReloadAt = now + RELOAD_INTERVAL_MS;
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
            clearAllHolograms();
            return;
        }

        selfLines = Collections.emptyList();
        if (!enabled) {
            clearAllHolograms();
            return;
        }

        boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;
        long now = System.currentTimeMillis();
        Set<UUID> activePlayers = new HashSet<UUID>();
        for (Player player : mc.level.players()) {
            UUID uuid = player.getUUID();
            NekoTagUser user = repository.findForPlayer(NekoTagFormat.normalizePlayerId(uuid), player.getName().getString());
            if (user == null) {
                continue;
            }

            boolean isSelf = mc.player != null && player.getUUID().equals(mc.player.getUUID());
            if (isSelf && firstPerson) {
                continue;
            }
            boolean includeNameLine = !(essentialInstalled && isSelf);
            List<ParsedTagLine> lines = NekoHologramLayout.buildParsedLines(user, player.getName().getString(), includeNameLine);
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

    static void requestReload() {
        reloadRequested = true;
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

    private static void updatePlayerHolograms(Minecraft mc, Player player, List<ParsedTagLine> lines, long nowMs) {
        if (mc.level == null || player == null || lines == null || lines.isEmpty()) {
            return;
        }

        UUID playerId = player.getUUID();
        List<ArmorStand> stands = playerHolograms.get(playerId);
        if (stands == null) {
            stands = new ArrayList<ArmorStand>();
            playerHolograms.put(playerId, stands);
        }

        while (stands.size() < lines.size()) {
            ArmorStand stand = new ArmorStand(mc.level, player.getX(), player.getY(), player.getZ());
            configureHologramStand(stand);
            stand.setCustomNameVisible(true);
            mc.level.addFreshEntity(stand);
            stands.add(stand);
        }
        while (stands.size() > lines.size()) {
            ArmorStand removed = stands.remove(stands.size() - 1);
            if (removed != null && removed.isAlive()) {
                removed.discard();
            }
        }

        double y = player.getY() + player.getBbHeight() + BASE_OFFSET + VANILLA_NAME_CLEARANCE + NekoHologramLayout.computeStackHeight(lines, SELF_LINE_GAP_BASE, SELF_LINE_GAP_EXTRA);
        for (int i = 0; i < lines.size(); i++) {
            ParsedTagLine line = lines.get(i);
            ArmorStand stand = stands.get(i);
            if (stand == null || !stand.isAlive()) {
                continue;
            }
            if (i > 0) {
                y -= NekoHologramLayout.computeLineGap(lines.get(i - 1), SELF_LINE_GAP_BASE, SELF_LINE_GAP_EXTRA);
            }
            stand.setPos(player.getX(), y, player.getZ());
            stand.setCustomName(buildStyledLineComponent(line, nowMs));
            stand.setCustomNameVisible(true);
        }
    }

    private static void configureHologramStand(ArmorStand stand) {
        stand.setInvisible(true);
        applyMarkerData(stand);
        stand.setNoGravity(true);
        stand.noPhysics = true;
        stand.setSilent(true);
    }

    private static void applyMarkerData(ArmorStand stand) {
        CompoundTag standData = new CompoundTag();
        standData.putBoolean("Marker", true);
        try {
            stand.getClass().getMethod("readAdditionalSaveData", CompoundTag.class).invoke(stand, standData);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            stand.getClass().getMethod("load", CompoundTag.class).invoke(stand, standData);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            java.lang.reflect.Method setMarker = stand.getClass().getDeclaredMethod("setMarker", boolean.class);
            setMarker.setAccessible(true);
            setMarker.invoke(stand, true);
        } catch (ReflectiveOperationException ignored) {
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
        List<ArmorStand> stands = playerHolograms.remove(playerId);
        if (stands == null) {
            return;
        }
        for (ArmorStand stand : stands) {
            if (stand != null && stand.isAlive()) {
                stand.discard();
            }
        }
    }

    private static void clearAllHolograms() {
        for (List<ArmorStand> stands : playerHolograms.values()) {
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isAlive()) {
                    stand.discard();
                }
            }
        }
        playerHolograms.clear();
    }
}
