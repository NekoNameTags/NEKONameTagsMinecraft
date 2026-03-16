package uk.co.nekosunevr.nekonametags.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.slf4j.Logger;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoGameProfileClient;
import uk.co.nekosunevr.nekonametags.core.NekoHologramLayout;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebClient;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NekoNameTagsFabricClient {
    private static final long RELOAD_INTERVAL_MS = NekoHologramLayout.DEFAULT_REFRESH_INTERVAL_MS;
    private static final long GAME_PROFILE_REFRESH_MS = 60_000L;
    private static final double BASE_OFFSET = -1.55D;
    private static final double VANILLA_NAME_CLEARANCE = -0.10D;
    private static final double HYPIXEL_NAMEPLATE_EXTRA_CLEARANCE = 0.72D;
    private static final double SELF_LINE_GAP_BASE = 0.18D;
    private static final double SELF_LINE_GAP_EXTRA = 0.03D;
    private static final long SERVER_CONTEXT_GRACE_MS = 15_000L;
    private static final Pattern MINECRAFT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static volatile boolean started;
    private static volatile boolean enabled = true;
    private static volatile List<ParsedTagLine> selfLines = Collections.emptyList();
    private static volatile boolean updateCheckDone;
    private static volatile boolean reloadRequested;
    private static NekoClientSettings settings;
    private static final Map<UUID, List<ArmorStandEntity>> playerHolograms = new HashMap<UUID, List<ArmorStandEntity>>();
    private static volatile ClientWorld lastRenderedWorld;
    private static volatile boolean essentialInstalled;
    private static volatile String dynamicServerHost = "";
    private static volatile long dynamicServerHostSeenAt;
    private static volatile long lastPresenceSyncAt;
    private static volatile boolean lastPresenceOnline;
    private static volatile String lastPresenceHost = "";
    private static final Map<String, CachedServerStats> serverStatsCache = new ConcurrentHashMap<String, CachedServerStats>();
    private static final Set<String> serverStatsRefreshInFlight = ConcurrentHashMap.newKeySet();
    private static final long PRESENCE_SYNC_INTERVAL_MS = 30_000L;
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
            syncPresenceIfNeeded(mc);

            try {
                Thread.sleep(50L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void syncPresenceIfNeeded(MinecraftClient mc) {
        if (settings == null || !settings.hasWebApiKey()) {
            return;
        }

        String username = mc.player == null ? "" : firstNonEmpty(
            safeTrim(mc.player.getNameForScoreboard()),
            mc.player.getName() == null ? null : safeTrim(mc.player.getName().getString()),
            ""
        );
        String uuid = mc.player == null ? "" : NekoTagFormat.normalizePlayerId(mc.player.getUuid());
        String serverHost = currentServerHost(mc);
        boolean online = mc.world != null && mc.player != null && !serverHost.isEmpty();
        String serverLabel = detectPresenceServerLabel(serverHost);
        long now = System.currentTimeMillis();
        boolean shouldSync = online != lastPresenceOnline
            || !serverHost.equals(lastPresenceHost)
            || (now - lastPresenceSyncAt) >= PRESENCE_SYNC_INTERVAL_MS;

        if (!shouldSync) {
            return;
        }

        lastPresenceSyncAt = now;
        lastPresenceOnline = online;
        lastPresenceHost = serverHost;

        new Thread(() -> {
            try {
                NekoMinecraftWebClient client = new NekoMinecraftWebClient(settings.getWebApiBaseUrl(), settings.getWebApiKey());
                client.syncPresence(username, uuid, serverHost, serverLabel, online);
            } catch (Exception ignored) {
            }
        }, "NekoNameTags-Fabric-Presence").start();
    }

    private static String detectPresenceServerLabel(String serverHost) {
        ServerKind serverKind = detectServerKind(serverHost);
        if (serverKind == ServerKind.WYNNCRAFT) {
            return "Wynncraft";
        }
        if (serverKind == ServerKind.HYPIXEL) {
            return "Hypixel";
        }
        return serverHost == null ? "" : serverHost;
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
            lastRenderedWorld = null;
            clearAllHolograms();
            return;
        }

        if (mc.world != lastRenderedWorld) {
            lastRenderedWorld = mc.world;
            clearAllHolograms();
        }

        selfLines = Collections.emptyList();
        if (!enabled) {
            clearAllHolograms();
            return;
        }

        ServerKind serverKind = updateDynamicServerContext(mc);

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
            boolean isSelf = mc.player != null && player.getUuid().equals(mc.player.getUuid());
            if (isSelf && firstPerson) {
                continue;
            }
            boolean includeNameLine = false;
            String lineName = firstNonEmpty(profileName, displayName, null);
            List<ParsedTagLine> lines = user == null
                ? new ArrayList<ParsedTagLine>()
                : NekoHologramLayout.buildParsedLines(user, lineName, includeNameLine);
            List<ParsedTagLine> serverLines = getServerStatsLines(serverKind, nameCandidates);
            if (!serverLines.isEmpty()) {
                List<ParsedTagLine> merged = new ArrayList<ParsedTagLine>(lines.size() + serverLines.size());
                merged.addAll(lines);
                merged.addAll(serverLines);
                lines = merged;
            }
            if (lines.isEmpty()) {
                continue;
            }

            activePlayers.add(uuid);
            updatePlayerHolograms(mc, player, lines, now, serverKind);
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

    private static ServerKind updateDynamicServerContext(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        String host = currentServerHost(mc);
        if (host.isEmpty() || mc.player == null) {
            if (!dynamicServerHost.isEmpty() && (now - dynamicServerHostSeenAt) <= SERVER_CONTEXT_GRACE_MS) {
                return detectServerKind(dynamicServerHost);
            }
            dynamicServerHost = "";
            serverStatsCache.clear();
            serverStatsRefreshInFlight.clear();
            return ServerKind.NONE;
        }

        ServerKind serverKind = detectServerKind(host);
        if (serverKind == ServerKind.NONE) {
            if (!dynamicServerHost.isEmpty() && (now - dynamicServerHostSeenAt) <= SERVER_CONTEXT_GRACE_MS) {
                return detectServerKind(dynamicServerHost);
            }
            dynamicServerHost = "";
            serverStatsCache.clear();
            serverStatsRefreshInFlight.clear();
            return ServerKind.NONE;
        }

        dynamicServerHostSeenAt = now;
        if (!host.equals(dynamicServerHost)) {
            ServerKind previousKind = detectServerKind(dynamicServerHost);
            dynamicServerHost = host;
            if (previousKind != serverKind) {
                serverStatsCache.clear();
                serverStatsRefreshInFlight.clear();
            }
        }
        return serverKind;
    }

    private static List<ParsedTagLine> getServerStatsLines(ServerKind serverKind, Set<String> nameCandidates) {
        if (serverKind == ServerKind.NONE || nameCandidates == null || nameCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        final String username = selectMinecraftUsername(nameCandidates);
        if (username == null || username.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        String cacheKey = serverKind.name() + ":" + username.toLowerCase();
        CachedServerStats cached = serverStatsCache.get(cacheKey);
        if (cached != null && cached.expiresAt > now) {
            return cached.lines;
        }

        if (serverStatsRefreshInFlight.add(cacheKey)) {
            requestServerStatsRefresh(serverKind, username, cacheKey);
        }

        return cached == null ? Collections.<ParsedTagLine>emptyList() : cached.lines;
    }

    private static void requestServerStatsRefresh(ServerKind serverKind, String username, String cacheKey) {
        new Thread(() -> {
            try {
                NekoGameProfileClient client = new NekoGameProfileClient(settings.getWebApiBaseUrl(), settings.getWebApiKey());
                NekoTagUser serverUser = serverKind == ServerKind.WYNNCRAFT
                    ? client.fetchWynncraftNametags(username)
                    : client.fetchHypixelNametags(username);
                List<ParsedTagLine> fetchedLines = serverUser == null
                    ? Collections.<ParsedTagLine>emptyList()
                    : NekoHologramLayout.buildParsedLines(serverUser, username, false);
                serverStatsCache.put(cacheKey, new CachedServerStats(fetchedLines, System.currentTimeMillis() + GAME_PROFILE_REFRESH_MS));
            } catch (Exception ignored) {
                serverStatsCache.put(cacheKey, new CachedServerStats(Collections.<ParsedTagLine>emptyList(), System.currentTimeMillis() + GAME_PROFILE_REFRESH_MS));
            } finally {
                serverStatsRefreshInFlight.remove(cacheKey);
            }
        }, "NekoNameTags-Fabric-GameStats").start();
    }

    private static String selectMinecraftUsername(Set<String> nameCandidates) {
        for (String candidate : nameCandidates) {
            if (candidate != null && MINECRAFT_NAME_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return null;
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

    private static void updatePlayerHolograms(MinecraftClient mc, AbstractClientPlayerEntity player, List<ParsedTagLine> lines, long nowMs, ServerKind serverKind) {
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

        double y = player.getY()
            + player.getHeight()
            + BASE_OFFSET
            + computeVanillaNameClearance(serverKind)
            + NekoHologramLayout.computeStackHeight(lines, SELF_LINE_GAP_BASE, SELF_LINE_GAP_EXTRA);
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

    private static double computeVanillaNameClearance(ServerKind serverKind) {
        if (serverKind == ServerKind.HYPIXEL) {
            return VANILLA_NAME_CLEARANCE + HYPIXEL_NAMEPLATE_EXTRA_CLEARANCE;
        }
        return VANILLA_NAME_CLEARANCE;
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

    private static final class CachedServerStats {
        private final List<ParsedTagLine> lines;
        private final long expiresAt;

        private CachedServerStats(List<ParsedTagLine> lines, long expiresAt) {
            this.lines = lines == null ? Collections.<ParsedTagLine>emptyList() : Collections.unmodifiableList(new ArrayList<ParsedTagLine>(lines));
            this.expiresAt = expiresAt;
        }
    }

}
