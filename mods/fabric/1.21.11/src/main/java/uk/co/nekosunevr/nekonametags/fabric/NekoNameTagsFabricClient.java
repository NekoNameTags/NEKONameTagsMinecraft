package uk.co.nekosunevr.nekonametags.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoGameProfileClient;
import uk.co.nekosunevr.nekonametags.core.NekoHologramLayout;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NekoNameTagsFabricClient {
    private static final long RELOAD_INTERVAL_MS = NekoHologramLayout.DEFAULT_REFRESH_INTERVAL_MS;
    private static final double BASE_OFFSET = -1.55D;
    private static final double VANILLA_NAME_CLEARANCE = -0.10D;
    private static final double SELF_LINE_GAP_BASE = 0.18D;
    private static final double SELF_LINE_GAP_EXTRA = 0.03D;
    private static final Pattern MINECRAFT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static volatile boolean started;
    private static volatile boolean enabled = true;
    private static volatile List<ParsedTagLine> selfLines = Collections.emptyList();
    private static volatile boolean updateCheckDone;
    private static volatile boolean reloadRequested;
    private static NekoClientSettings settings;
    private static final Map<UUID, List<ArmorStandEntity>> playerHolograms = new HashMap<UUID, List<ArmorStandEntity>>();
    private static volatile boolean essentialInstalled;
    private static volatile List<ParsedTagLine> dynamicServerLines = Collections.emptyList();
    private static volatile String dynamicServerHost = "";
    private static volatile long nextDynamicServerRefreshAt = 0L;
    private static volatile boolean dynamicServerRefreshInFlight;

    private NekoNameTagsFabricClient() {
    }

    static synchronized void start(NekoTagRepository repository, Logger logger) {
        if (started) {
            return;
        }
        started = true;
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
            if (reloadRequested) {
                reloadRequested = false;
                int count = reloadNow(repository, logger);
                nextReloadAt = now + RELOAD_INTERVAL_MS;
                if (count >= 0) {
                    sendClientMessage("NekoNameTags reloaded: " + count + " entries.");
                } else {
                    sendClientMessage("NekoNameTags reload failed. Check latest.log.");
                }
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (now >= nextReloadAt) {
                reloadNow(repository, logger);
                nextReloadAt = now + RELOAD_INTERVAL_MS;
            }
            if (!updateCheckDone) {
                updateCheckDone = true;
                checkForUpdatesAndNotify(logger);
            }
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

        updateDynamicServerLines(mc);

        boolean firstPerson = mc.options.getPerspective() == Perspective.FIRST_PERSON;
        long now = System.currentTimeMillis();
        Set<UUID> activePlayers = new HashSet<UUID>();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            UUID uuid = player.getUuid();
            String profileName = safeTrim(player.getNameForScoreboard());
            String displayName = player.getName() == null ? null : safeTrim(player.getName().getString());
            String uuidKey = NekoTagFormat.normalizePlayerId(uuid);

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

            boolean isSelf = mc.player != null && player.getUuid().equals(mc.player.getUuid());
            if (isSelf && firstPerson) {
                continue;
            }
            boolean includeNameLine = false;
            String lineName = firstNonEmpty(profileName, displayName, null);
            List<ParsedTagLine> lines = NekoHologramLayout.buildParsedLines(user, lineName, includeNameLine);
            if (isSelf && !dynamicServerLines.isEmpty()) {
                List<ParsedTagLine> merged = new ArrayList<ParsedTagLine>(lines.size() + dynamicServerLines.size());
                merged.addAll(lines);
                merged.addAll(dynamicServerLines);
                lines = merged;
            }
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

    static void requestReload() {
        reloadRequested = true;
    }

    static List<ParsedTagLine> getSelfLines() {
        return selfLines;
    }

    private static void updateDynamicServerLines(MinecraftClient mc) {
        String host = currentServerHost(mc);
        if (host.isEmpty() || mc.player == null || !settings.hasWebApiKey()) {
            dynamicServerHost = "";
            dynamicServerLines = Collections.emptyList();
            nextDynamicServerRefreshAt = 0L;
            return;
        }

        ServerKind serverKind = detectServerKind(host);
        if (serverKind == ServerKind.NONE) {
            dynamicServerHost = "";
            dynamicServerLines = Collections.emptyList();
            nextDynamicServerRefreshAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (!host.equals(dynamicServerHost)) {
            dynamicServerHost = host;
            dynamicServerLines = Collections.emptyList();
            nextDynamicServerRefreshAt = 0L;
        }
        if (dynamicServerRefreshInFlight || now < nextDynamicServerRefreshAt) {
            return;
        }

        final String username = safeTrim(mc.player.getGameProfile() == null ? null : mc.player.getGameProfile().getName());
        if (username == null || username.isEmpty()) {
            return;
        }

        dynamicServerRefreshInFlight = true;
        nextDynamicServerRefreshAt = now + RELOAD_INTERVAL_MS;
        new Thread(() -> {
            try {
                NekoGameProfileClient client = new NekoGameProfileClient(settings.getWebApiBaseUrl(), settings.getWebApiKey());
                List<ParsedTagLine> fetchedLines = serverKind == ServerKind.WYNNCRAFT
                    ? buildWynncraftLines(client.fetchWynncraftProfile(username))
                    : buildHypixelLines(client.fetchHypixelProfile(username));
                dynamicServerLines = fetchedLines;
            } catch (Exception ignored) {
                dynamicServerLines = Collections.emptyList();
            } finally {
                dynamicServerRefreshInFlight = false;
            }
        }, "NekoNameTags-Fabric-GameStats").start();
    }

    private static String currentServerHost(MinecraftClient mc) {
        ServerInfo serverInfo = mc.getCurrentServerEntry();
        if (serverInfo == null || serverInfo.address == null) {
            return "";
        }
        String host = serverInfo.address.trim().toLowerCase();
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    private static ServerKind detectServerKind(String host) {
        if (host.equals("wynncraft.com") || host.endsWith(".wynncraft.com")) {
            return ServerKind.WYNNCRAFT;
        }
        if (host.equals("hypixel.net") || host.endsWith(".hypixel.net")) {
            return ServerKind.HYPIXEL;
        }
        return ServerKind.NONE;
    }

    private static List<ParsedTagLine> buildWynncraftLines(JsonObject payload) {
        List<ParsedTagLine> lines = new ArrayList<ParsedTagLine>(2);
        String supportRank = getString(payload, "supportRank");
        String rank = getString(payload, "rank");
        JsonObject global = getObject(payload, "global");
        String totalLevel = getNumberString(global, "totalLevel");
        String completedQuests = getNumberString(global, "completedQuests");
        String playtime = getNumberString(payload, "playtime");

        lines.add(makeInfoLine("Wynn " + firstNonEmpty(capitalize(supportRank), rank, "Player") + " | Total Lv " + firstNonEmpty(totalLevel, "0", null), 0x5BE7D7));
        lines.add(makeInfoLine("Quests " + firstNonEmpty(completedQuests, "0", null) + " | Playtime " + firstNonEmpty(playtime, "0", null) + "h", 0xFFD36E));
        return lines;
    }

    private static List<ParsedTagLine> buildHypixelLines(JsonObject payload) {
        List<ParsedTagLine> lines = new ArrayList<ParsedTagLine>(2);
        String rank = getString(payload, "rank");
        String level = getNumberString(payload, "level");
        String karma = getNumberString(payload, "karma");
        String yearsJoined = getNumberString(payload, "YearsJoined");

        lines.add(makeInfoLine("Hypixel " + firstNonEmpty(rank, "Player", null) + " | Level " + firstNonEmpty(level, "0", null), 0x59C3FF));
        lines.add(makeInfoLine("Karma " + firstNonEmpty(karma, "0", null) + " | Years " + firstNonEmpty(yearsJoined, "0", null), 0xFFD36E));
        return lines;
    }

    private static ParsedTagLine makeInfoLine(String text, int colorRgb) {
        return new ParsedTagLine(text, text, TagEffectType.NONE, colorRgb, false, false, 16.0f);
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return safeTrim(object.get(key).getAsString());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String getNumberString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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
            configureHologramStand(stand);
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

        double y = player.getY() + player.getHeight() + BASE_OFFSET + VANILLA_NAME_CLEARANCE + NekoHologramLayout.computeStackHeight(lines, SELF_LINE_GAP_BASE, SELF_LINE_GAP_EXTRA);
        for (int i = 0; i < lines.size(); i++) {
            ParsedTagLine line = lines.get(i);
            ArmorStandEntity stand = stands.get(i);
            if (stand == null || !stand.isAlive()) {
                continue;
            }
            if (i > 0) {
                y -= NekoHologramLayout.computeLineGap(lines.get(i - 1), SELF_LINE_GAP_BASE, SELF_LINE_GAP_EXTRA);
            }
            stand.setPosition(player.getX(), y, player.getZ());
            stand.setCustomName(buildStyledLineText(line, nowMs));
            stand.setCustomNameVisible(true);
        }
    }

    private static void configureHologramStand(ArmorStandEntity stand) {
        applyMarkerData(stand);
        stand.setInvisible(true);
        applyNoBasePlateData(stand);
        stand.setNoGravity(true);
        stand.noClip = true;
        try {
            stand.getClass().getField("noPhysics").setBoolean(stand, true);
        } catch (ReflectiveOperationException ignored) {
        }
        stand.setSilent(true);
    }

    private static void applyMarkerData(ArmorStandEntity stand) {
        NbtCompound standData = new NbtCompound();
        standData.putBoolean("Marker", true);
        try {
            stand.getClass().getMethod("readCustomDataFromNbt", NbtCompound.class).invoke(stand, standData);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            stand.getClass().getMethod("readCustomData", NbtCompound.class).invoke(stand, standData);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            stand.getClass().getMethod("setMarker", boolean.class).invoke(stand, true);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyNoBasePlateData(ArmorStandEntity stand) {
        try {
            stand.getClass().getMethod("setNoBasePlate", boolean.class).invoke(stand, true);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        NbtCompound standData = new NbtCompound();
        standData.putBoolean("NoBasePlate", true);
        try {
            stand.getClass().getMethod("readCustomDataFromNbt", NbtCompound.class).invoke(stand, standData);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            stand.getClass().getMethod("readCustomData", NbtCompound.class).invoke(stand, standData);
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

    private enum ServerKind {
        NONE,
        WYNNCRAFT,
        HYPIXEL
    }
}
