package uk.co.nekosunevr.nekonametags.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.TagEffectType;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;

import java.util.Map;
import java.util.UUID;

public final class NekoNameTagsPlugin extends JavaPlugin {
    private static final String TEAM_PREFIX = "nnt_";

    private NekoTagRepository repository;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String apiUrl = getConfig().getString("api-url", System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        ));
        repository = new NekoTagRepository(apiUrl);

        getServer().getScheduler().runTaskAsynchronously(this, this::reloadAndApply);
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

        sender.sendMessage("/nekonametags reload");
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

            String line = NekoTagFormat.firstLine(tagUser);
            if (!line.isEmpty()) {
                team.setPrefix(line.length() > 64 ? line.substring(0, 64) : line);
            }

            String[] lines = tagUser.getNamePlatesText();
            if (lines.length > 0) {
                ParsedTagLine parsed = NekoTagFormat.parse(lines[0]);
                if (parsed.getEffectType() == TagEffectType.RAINBOW || parsed.getEffectType() == TagEffectType.ANIMATED) {
                    getLogger().fine("Effect marker detected for " + player.getName() + ": " + parsed.getEffectType());
                }
            }
        }
    }
}
