package uk.co.nekosunevr.nekonametags.neoforge;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;
import uk.co.nekosunevr.nekonametags.core.NekoUpdateChecker;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;
import uk.co.nekosunevr.nekonametags.core.TagEffectType;
import uk.co.nekosunevr.nekonametags.core.TagEffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NekoNameTagsNeoForgeClient {
    private static final long RELOAD_INTERVAL_MS = 60_000L;
    private static final double BASE_OFFSET = -0.55D;
    private static final double VANILLA_NAME_CLEARANCE = -0.04D;
    private static final double SELF_LINE_GAP_BASE = 0.18D;
    private static final double SELF_LINE_GAP_EXTRA = 0.03D;
    private static final Pattern MINECRAFT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static volatile boolean started;
    private static volatile boolean enabled = true;
    private static volatile List<ParsedTagLine> selfLines = Collections.emptyList();
    private static volatile boolean updateCheckDone;
    private static NekoClientSettings settings;
    private static volatile boolean essentialInstalled;
    private static final Map<UUID, List<ArmorStand>> playerHolograms = new HashMap<UUID, List<ArmorStand>>();

    private NekoNameTagsNeoForgeClient() {
    }

    static synchronized void start(NekoTagRepository repository, Logger logger) {
        if (started) {
            return;
        }
        started = true;
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
            UUID profileId = player.getGameProfile() != null && player.getGameProfile().getId() != null ? player.getGameProfile().getId() : uuid;
            String profileName = safeTrim(player.getScoreboardName());
            String displayName = player.getName() == null ? null : safeTrim(player.getName().getString());
            String uuidKey = NekoTagFormat.normalizePlayerId(profileId);

            Set<String> nameCandidates = new LinkedHashSet<String>();
            if (profileName != null) {
                nameCandidates.add(profileName);
            }
            if (displayName != null) {
                nameCandidates.add(displayName);
            }
            nameCandidates.addAll(extractPotentialMinecraftNames(displayName));

            NekoTagUser user = repository.findForPlayer(uuidKey, null);
            if (user == null) {
                for (String candidate : nameCandidates) {
                    user = repository.findForPlayer(null, candidate);
                    if (user != null) {
                        break;
                    }
                }
            }
            if (user == null) {
                continue;
            }

            boolean isSelf = mc.player.getUUID().equals(player.getUUID());
            if (isSelf && firstPerson) {
                continue;
            }
            String lineName = firstNonEmpty(profileName, displayName, null);
            List<ParsedTagLine> lines = buildParsedLines(user, lineName, false);
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
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setSilent(true);
            stand.setCustomNameVisible(true);
            mc.level.addEntity(stand);
            stands.add(stand);
        }
        while (stands.size() > lines.size()) {
            ArmorStand removed = stands.remove(stands.size() - 1);
            if (removed != null && removed.isAlive()) {
                removed.discard();
            }
        }

        double y = player.getY() + player.getBbHeight() + BASE_OFFSET + VANILLA_NAME_CLEARANCE;
        for (int i = 0; i < lines.size(); i++) {
            ParsedTagLine line = lines.get(i);
            ArmorStand stand = stands.get(i);
            if (stand == null || !stand.isAlive()) {
                continue;
            }
            if (i > 0) {
                ParsedTagLine prev = lines.get(i - 1);
                float prevRatio = Math.max(0.7f, Math.min(3.0f, prev.getSize() / 16.0f));
                y -= (SELF_LINE_GAP_BASE * prevRatio) + SELF_LINE_GAP_EXTRA;
            }
            stand.setPos(player.getX(), y, player.getZ());
            stand.setCustomName(buildStyledLineComponent(line, nowMs));
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

    private static String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonEmpty(String a, String b, String c) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        if (b != null && !b.isEmpty()) {
            return b;
        }
        if (c != null && !c.isEmpty()) {
            return c;
        }
        return "";
    }

    private static Set<String> extractPotentialMinecraftNames(String text) {
        Set<String> names = new LinkedHashSet<String>();
        if (text == null || text.isEmpty()) {
            return names;
        }
        Matcher matcher = MINECRAFT_NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = safeTrim(matcher.group());
            if (candidate != null) {
                names.add(candidate);
            }
        }
        return names;
    }
}
