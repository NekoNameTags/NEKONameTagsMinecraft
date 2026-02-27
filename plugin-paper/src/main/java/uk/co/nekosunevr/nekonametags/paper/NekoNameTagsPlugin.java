package uk.co.nekosunevr.nekonametags.paper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.TagEffectType;
import uk.co.nekosunevr.nekonametags.core.TagEffects;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NekoNameTagsPlugin extends JavaPlugin implements Listener {
    private static final String TEAM_PREFIX = "nnt_";
    private static final long RELOAD_INTERVAL_TICKS = 20L * 30L;
    private static final long EFFECT_REFRESH_TICKS = 2L;

    private NekoTagRepository repository;
    private volatile boolean enabled = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String apiUrl = getConfig().getString("api-url", System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        ));
        repository = new NekoTagRepository(apiUrl);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskAsynchronously(this, this::reloadAndApply);
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::reloadAndApply, RELOAD_INTERVAL_TICKS, RELOAD_INTERVAL_TICKS);
        getServer().getScheduler().runTaskTimer(this, this::applyToOnlinePlayers, EFFECT_REFRESH_TICKS, EFFECT_REFRESH_TICKS);
    }

    @Override
    public void onDisable() {
        // No explicit cleanup required.
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"nekonametags".equalsIgnoreCase(command.getName())) {
            return false;
        }

        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                reloadAndApply();
                sender.sendMessage("NekoNameTags reloaded.");
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

        sender.sendMessage("/nekonametags reload");
        sender.sendMessage("/nekonametags toggle");
        return true;
    }

    private void reloadAndApply() {
        try {
            Map<String, NekoTagUser> users = repository.reload();
            getLogger().info("Loaded " + users.size() + " NekoNameTags entries.");
        } catch (Exception ex) {
            getLogger().warning("Failed to reload tags: " + ex.getMessage());
            return;
        }

        Bukkit.getScheduler().runTask(this, this::applyToOnlinePlayers);
    }

    private void applyToOnlinePlayers() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            String normalized = NekoTagFormat.normalizePlayerId(uuid);
            NekoTagUser tagUser = repository.findForPlayer(normalized, player.getName());
            if (tagUser == null) {
                continue;
            }

            String compact = normalized.replace("-", "");
            String idPart = compact.length() > 12 ? compact.substring(0, 12) : compact;
            String teamName = TEAM_PREFIX + idPart;
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }

            String line = renderLegacySingleLine(tagUser, now);
            if (!line.isEmpty()) {
                team.setPrefix(line.length() > 64 ? line.substring(0, 64) : line);
            } else {
                team.setPrefix("");
            }
        }
    }

    private static String renderLegacySingleLine(NekoTagUser user, long nowMs) {
        List<String> rendered = new ArrayList<String>(4);
        for (String raw : user.getBigPlatesText()) {
            String value = renderLineLegacy(NekoTagFormat.parse(raw), nowMs);
            if (!value.isEmpty()) {
                rendered.add(value);
            }
        }
        for (String raw : user.getNamePlatesText()) {
            String value = renderLineLegacy(NekoTagFormat.parse(raw), nowMs);
            if (!value.isEmpty()) {
                rendered.add(value);
            }
        }
        if (rendered.isEmpty()) {
            return "";
        }
        String joined = String.join(" ", rendered);
        return joined.length() > 64 ? joined.substring(0, 64) : joined;
    }

    private static String renderLineLegacy(ParsedTagLine parsed, long nowMs) {
        String text = parsed.getText();
        if (parsed.getEffectType() == TagEffectType.ANIMATED) {
            text = TagEffects.animatedWindow(text, nowMs);
        }
        if (text == null || text.isEmpty()) {
            return "";
        }

        String stylePrefix = "";
        if (parsed.isBold()) {
            stylePrefix += ChatColor.BOLD;
        }
        if (parsed.isItalic()) {
            stylePrefix += ChatColor.ITALIC;
        }

        if (parsed.getEffectType() == TagEffectType.RAINBOW) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                int rgb = TagEffects.rainbowRgb(nowMs, i * 80) & 0x00FFFFFF;
                sb.append(toLegacyHex(rgb)).append(stylePrefix).append(text.charAt(i));
            }
            sb.append(ChatColor.RESET);
            return sb.toString();
        }

        return toLegacyHex(parsed.getColorRgb() & 0x00FFFFFF) + stylePrefix + text + ChatColor.RESET;
    }

    private static String toLegacyHex(int rgb) {
        String hex = String.format("%06X", rgb);
        return "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + "§" + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, this::applyToOnlinePlayers, 20L);
    }

    private void clearAppliedTags() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }
}
