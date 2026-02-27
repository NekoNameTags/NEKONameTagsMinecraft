package uk.co.nekosunevr.nekonametags.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.apache.logging.log4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
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
import java.util.List;
import java.util.UUID;

final class NekoNameTagsForgeClient {
    private static final long RELOAD_INTERVAL_MS = 30_000L;
    private static final double BASE_OFFSET = 0.48D;
    private static final double VANILLA_NAME_CLEARANCE = 0.27D;
    private static final double SELF_LINE_GAP_BASE = 0.18D;
    private static final double SELF_LINE_GAP_EXTRA = 0.03D;
    private static volatile boolean started;
    private static volatile boolean enabled = true;
    private static volatile List<ParsedTagLine> selfLines = Collections.emptyList();
    private static volatile boolean updateCheckDone;
    private static volatile boolean essentialInstalled;
    private static NekoClientSettings settings;
    private static final List<ArmorStand> selfHolograms = new ArrayList<ArmorStand>();

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
            clearSelfHolograms();
            return;
        }

        selfLines = Collections.emptyList();
        long now = System.currentTimeMillis();
        List<ParsedTagLine> localLines = Collections.emptyList();
        for (Player player : mc.level.players()) {
            if (!enabled) {
                player.setCustomNameVisible(false);
                continue;
            }
            UUID uuid = player.getUUID();
            NekoTagUser user = repository.findForPlayer(NekoTagFormat.normalizePlayerId(uuid), player.getName().getString());
            if (user == null) {
                continue;
            }

            boolean isSelf = mc.player != null && player.getUUID().equals(mc.player.getUUID());
            boolean includeNameLine = !(essentialInstalled && isSelf);
            List<ParsedTagLine> lines = buildParsedLines(user, player.getName().getString(), includeNameLine);
            if (lines.isEmpty()) {
                continue;
            }

            Component rendered = buildVanillaNameComponent(lines, now);
            if (rendered == null) {
                continue;
            }

            player.setCustomName(rendered);
            player.setCustomNameVisible(true);
            if (isSelf) {
                selfLines = lines;
                localLines = lines;
            }
        }

        if (!enabled || mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            clearSelfHolograms();
            return;
        }
        updateSelfHolograms(mc, localLines, now);
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

    private static void updateSelfHolograms(Minecraft mc, List<ParsedTagLine> lines, long nowMs) {
        if (mc.player == null || mc.level == null || lines == null || lines.isEmpty()) {
            clearSelfHolograms();
            return;
        }

        while (selfHolograms.size() < lines.size()) {
            ArmorStand stand = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ());
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setSilent(true);
            stand.setCustomNameVisible(true);
            mc.level.addFreshEntity(stand);
            selfHolograms.add(stand);
        }
        while (selfHolograms.size() > lines.size()) {
            ArmorStand removed = selfHolograms.remove(selfHolograms.size() - 1);
            if (removed != null && removed.isAlive()) {
                removed.discard();
            }
        }

        double y = mc.player.getY() + mc.player.getBbHeight() + BASE_OFFSET + VANILLA_NAME_CLEARANCE;
        for (int i = 0; i < lines.size(); i++) {
            ParsedTagLine line = lines.get(i);
            ArmorStand stand = selfHolograms.get(i);
            if (stand == null || !stand.isAlive()) {
                continue;
            }
            if (i > 0) {
                ParsedTagLine prev = lines.get(i - 1);
                float prevRatio = Math.max(0.7f, Math.min(3.0f, prev.getSize() / 16.0f));
                y -= (SELF_LINE_GAP_BASE * prevRatio) + SELF_LINE_GAP_EXTRA;
            }
            stand.setPos(mc.player.getX(), y, mc.player.getZ());
            stand.setCustomName(buildStyledLineComponent(line, nowMs));
            stand.setCustomNameVisible(true);
        }
    }

    private static void clearSelfHolograms() {
        for (ArmorStand stand : selfHolograms) {
            if (stand != null && stand.isAlive()) {
                stand.discard();
            }
        }
        selfHolograms.clear();
    }
}
