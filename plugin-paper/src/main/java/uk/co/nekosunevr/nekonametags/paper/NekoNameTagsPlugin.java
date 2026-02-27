package uk.co.nekosunevr.nekonametags.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.TagEffectType;
import uk.co.nekosunevr.nekonametags.core.TagEffects;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NekoNameTagsPlugin extends JavaPlugin implements Listener {
    private static final long RELOAD_INTERVAL_TICKS = 20L * 30L;
    private static final long DEFAULT_EFFECT_REFRESH_TICKS = 4L;
    private static final long IMMEDIATE_RELOAD_COOLDOWN_MS = 1500L;
    private static final double BASE_OFFSET = 0.55D;
    private static final double VANILLA_NAME_CLEARANCE = 0.35D;
    private static final String LEGACY_HIDE_TEAM_PREFIX = "nnt";

    private NekoTagRepository repository;
    private volatile boolean enabled = true;
    private final Map<UUID, List<ArmorStand>> activeHolograms = new HashMap<UUID, List<ArmorStand>>();
    private long effectRefreshTicks = DEFAULT_EFFECT_REFRESH_TICKS;
    private BukkitTask effectTask;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private volatile long lastImmediateReloadAtMs = 0L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String apiUrl = getConfig().getString("api-url", System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        ));
        repository = new NekoTagRepository(apiUrl);
        effectRefreshTicks = Math.max(1L, getConfig().getLong("refresh-ticks", DEFAULT_EFFECT_REFRESH_TICKS));

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskAsynchronously(this, this::reloadAndApply);
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::reloadAndApply, RELOAD_INTERVAL_TICKS, RELOAD_INTERVAL_TICKS);
        startOrRestartEffectTask();
    }

    @Override
    public void onDisable() {
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
        clearAppliedTags();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"nekonametags".equalsIgnoreCase(command.getName())) {
            return false;
        }

        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            reloadConfig();
            effectRefreshTicks = Math.max(1L, getConfig().getLong("refresh-ticks", DEFAULT_EFFECT_REFRESH_TICKS));
            startOrRestartEffectTask();
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                reloadAndApply();
                sender.sendMessage("NekoNameTags reloaded. refresh-ticks=" + effectRefreshTicks);
            });
            return true;
        }

        if (args.length > 0 && "toggle".equalsIgnoreCase(args[0])) {
            enabled = !enabled;
            if (!enabled) {
                clearAppliedTags();
            } else {
                getServer().getScheduler().runTaskAsynchronously(this, this::reloadAndApply);
            }
            sender.sendMessage("NekoNameTags is now " + (enabled ? "enabled" : "disabled") + ".");
            return true;
        }

        if (args.length > 0 && "resetscoreboard".equalsIgnoreCase(args[0])) {
            int removed = resetLegacyScoreboardTeams();
            sender.sendMessage("NekoNameTags reset scoreboard teams: " + removed);
            return true;
        }

        sender.sendMessage("/nekonametags reload");
        sender.sendMessage("/nekonametags toggle");
        sender.sendMessage("/nekonametags resetscoreboard");
        return true;
    }

    private void reloadAndApply() {
        if (!enabled || repository == null) {
            return;
        }
        if (!reloadInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            Map<String, NekoTagUser> users = repository.reload();
            getLogger().info("Loaded " + users.size() + " NekoNameTags entries.");
        } catch (Exception ex) {
            getLogger().warning("Failed to reload tags: " + ex.getMessage());
            return;
        } finally {
            reloadInProgress.set(false);
        }

        Bukkit.getScheduler().runTask(this, this::applyToOnlinePlayers);
    }

    private void requestImmediateReload() {
        long now = System.currentTimeMillis();
        if ((now - lastImmediateReloadAtMs) < IMMEDIATE_RELOAD_COOLDOWN_MS) {
            return;
        }
        lastImmediateReloadAtMs = now;
        getServer().getScheduler().runTaskAsynchronously(this, this::reloadAndApply);
    }

    private void applyToOnlinePlayers() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            String normalized = NekoTagFormat.normalizePlayerId(uuid);
            NekoTagUser tagUser = repository.findForPlayer(normalized, player.getName());
            if (tagUser == null) {
                removeHolograms(player.getUniqueId());
                continue;
            }

            List<RenderLine> lines = renderAllLines(tagUser, now);
            if (lines.isEmpty()) {
                removeHolograms(player.getUniqueId());
                continue;
            }
            updateHolograms(player, lines);
        }

        List<UUID> onlineIds = new ArrayList<UUID>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            onlineIds.add(online.getUniqueId());
        }
        List<UUID> stale = new ArrayList<UUID>();
        for (UUID uuid : activeHolograms.keySet()) {
            if (!onlineIds.contains(uuid)) {
                stale.add(uuid);
            }
        }
        for (UUID uuid : stale) {
            removeHolograms(uuid);
        }
    }

    private static List<RenderLine> renderAllLines(NekoTagUser user, long nowMs) {
        List<RenderLine> rows = new ArrayList<RenderLine>(6);
        for (String raw : user.getBigPlatesText()) {
            ParsedTagLine parsed = NekoTagFormat.parse(raw);
            Component rendered = renderLineComponent(parsed, nowMs);
            if (rendered != null) {
                rows.add(new RenderLine(rendered, parsed.getSize() <= 16.0f));
            }
        }
        for (String raw : user.getNamePlatesText()) {
            ParsedTagLine parsed = NekoTagFormat.parse(raw);
            Component rendered = renderLineComponent(parsed, nowMs);
            if (rendered != null) {
                rows.add(new RenderLine(rendered, parsed.getSize() <= 16.0f));
            }
        }
        return rows;
    }

    private static Component renderLineComponent(ParsedTagLine parsed, long nowMs) {
        String text = parsed.getText();
        if (parsed.getEffectType() == TagEffectType.ANIMATED) {
            text = TagEffects.animatedWindow(text, nowMs);
        }
        if (text == null || text.isEmpty()) {
            return null;
        }

        if (parsed.getEffectType() == TagEffectType.RAINBOW) {
            TextComponent.Builder rainbow = Component.text();
            for (int i = 0; i < text.length(); i++) {
                int rgb = TagEffects.rainbowRgb(nowMs, i * 80) & 0x00FFFFFF;
                TextComponent.Builder ch = Component.text()
                    .content(String.valueOf(text.charAt(i)))
                    .color(TextColor.color(rgb));
                if (parsed.isBold()) {
                    ch.decoration(TextDecoration.BOLD, true);
                }
                if (parsed.isItalic()) {
                    ch.decoration(TextDecoration.ITALIC, true);
                }
                rainbow.append(ch.build());
            }
            return rainbow.build();
        }

        TextComponent.Builder normal = Component.text()
            .content(text)
            .color(TextColor.color(parsed.getColorRgb() & 0x00FFFFFF));
        if (parsed.isBold()) {
            normal.decoration(TextDecoration.BOLD, true);
        }
        if (parsed.isItalic()) {
            normal.decoration(TextDecoration.ITALIC, true);
        }
        return normal.build();
    }

    private void startOrRestartEffectTask() {
        if (effectTask != null) {
            effectTask.cancel();
        }
        effectTask = getServer().getScheduler().runTaskTimer(this, this::applyToOnlinePlayers, effectRefreshTicks, effectRefreshTicks);
    }

    private void updateHolograms(Player player, List<RenderLine> lines) {
        List<ArmorStand> stands = activeHolograms.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<ArmorStand>());
        while (stands.size() < lines.size()) {
            ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(player.getLocation(), EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCustomNameVisible(true);
            stands.add(stand);
        }
        while (stands.size() > lines.size()) {
            ArmorStand removed = stands.remove(stands.size() - 1);
            if (removed != null && !removed.isDead()) {
                removed.remove();
            }
        }

        Location base = player.getLocation();
        double stackSpan = 0.0D;
        for (int i = 0; i < lines.size() - 1; i++) {
            stackSpan += lineStep(lines.get(i));
        }
        double y = base.getY() + player.getHeight() + BASE_OFFSET + VANILLA_NAME_CLEARANCE + stackSpan;
        for (int i = 0; i < lines.size(); i++) {
            ArmorStand stand = stands.get(i);
            if (stand == null || stand.isDead()) {
                continue;
            }
            if (i > 0) {
                y -= lineStep(lines.get(i - 1));
            }
            Location target = new Location(base.getWorld(), base.getX(), y, base.getZ(), base.getYaw(), base.getPitch());
            stand.teleport(target);
            RenderLine line = lines.get(i);
            stand.setSmall(line.small());
            stand.customName(line.component());
        }
    }

    private static double lineStep(RenderLine line) {
        return line.small() ? 0.32D : 0.48D;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        requestImmediateReload();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeHolograms(event.getPlayer().getUniqueId());
        requestImmediateReload();
    }

    private void clearAppliedTags() {
        List<UUID> all = new ArrayList<UUID>(activeHolograms.keySet());
        for (UUID uuid : all) {
            removeHolograms(uuid);
        }
    }

    private void removeHolograms(UUID uuid) {
        List<ArmorStand> stands = activeHolograms.remove(uuid);
        if (stands == null) {
            return;
        }
        for (ArmorStand stand : stands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
    }

    private int resetLegacyScoreboardTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard == null) {
            return 0;
        }

        int removed = 0;
        Collection<Team> teams = new ArrayList<Team>(scoreboard.getTeams());
        for (Team team : teams) {
            if (team.getName().startsWith(LEGACY_HIDE_TEAM_PREFIX)) {
                team.unregister();
                removed++;
            }
        }
        return removed;
    }

    private static final class RenderLine {
        private final Component component;
        private final boolean small;

        private RenderLine(Component component, boolean small) {
            this.component = component;
            this.small = small;
        }

        private Component component() {
            return component;
        }

        private boolean small() {
            return small;
        }
    }

}

