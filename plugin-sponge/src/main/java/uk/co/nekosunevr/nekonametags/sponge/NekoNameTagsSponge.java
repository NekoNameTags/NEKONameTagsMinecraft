package uk.co.nekosunevr.nekonametags.sponge;

import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;
import uk.co.nekosunevr.nekonametags.core.NekoTagFormat;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;
import uk.co.nekosunevr.nekonametags.core.NekoTagRepository;
import uk.co.nekosunevr.nekonametags.core.TagEffectType;
import uk.co.nekosunevr.nekonametags.core.TagEffects;
import uk.co.nekosunevr.nekonametags.core.NekoTagUser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Plugin("nekonametags")
public final class NekoNameTagsSponge {
    private final PluginContainer pluginContainer;
    private final Logger logger;
    private NekoTagRepository repository;
    private volatile boolean enabled = true;

    @Inject
    public NekoNameTagsSponge(PluginContainer pluginContainer, Logger logger) {
        this.pluginContainer = pluginContainer;
        this.logger = logger;
    }

    @Listener
    public void onStarted(StartedEngineEvent<?> event) {
        String apiUrl = System.getProperty(
            "nekonametags.api.url",
            "https://nekont.nekosunevr.co.uk/api/minecraft/nametags"
        );
        repository = new NekoTagRepository(apiUrl);
        registerCommands();
        reloadAndApply();
        Sponge.server().scheduler().submit(Task.builder()
            .plugin(pluginContainer)
            .interval(Duration.ofSeconds(30))
            .execute(task -> reloadAndApply())
            .build());
        Sponge.server().scheduler().submit(Task.builder()
            .plugin(pluginContainer)
            .interval(Duration.ofMillis(100))
            .execute(task -> applyToOnlinePlayers())
            .build());
    }

    @Listener
    public void onJoin(ServerSideConnectionEvent.Join event) {
        applyToPlayer(event.player());
    }

    private void reloadAndApply() {
        if (!enabled) {
            return;
        }
        try {
            int count = repository.reload().size();
            logger.info("NekoNameTags (Sponge) loaded {} entries.", count);
            applyToOnlinePlayers();
        } catch (Exception ex) {
            logger.warn("NekoNameTags (Sponge) API reload failed: {}", ex.getMessage());
        }
    }

    private void applyToOnlinePlayers() {
        for (ServerPlayer player : Sponge.server().onlinePlayers()) {
            applyToPlayer(player);
        }
    }

    private void applyToPlayer(ServerPlayer player) {
        if (repository == null || !enabled) {
            return;
        }
        String uuid = NekoTagFormat.normalizePlayerId(player.uniqueId());
        NekoTagUser user = repository.findForPlayer(uuid, player.name());
        if (user == null) {
            return;
        }

        Component rendered = renderSingleLine(user, System.currentTimeMillis());
        if (rendered == null) {
            return;
        }

        Component display = rendered.append(Component.text(" " + player.name()));
        player.offer(Keys.DISPLAY_NAME, display);
    }

    private static Component renderSingleLine(NekoTagUser user, long nowMs) {
        List<Component> parts = new ArrayList<Component>(4);
        for (String raw : user.getBigPlatesText()) {
            Component line = renderLine(NekoTagFormat.parse(raw), nowMs);
            if (line != null) {
                parts.add(line);
            }
        }
        for (String raw : user.getNamePlatesText()) {
            Component line = renderLine(NekoTagFormat.parse(raw), nowMs);
            if (line != null) {
                parts.add(line);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }

        TextComponent.Builder joined = Component.text();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.append(Component.space());
            }
            joined.append(parts.get(i));
        }
        return joined.build();
    }

    private static Component renderLine(ParsedTagLine parsed, long nowMs) {
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
                TextComponent.Builder ch = Component.text().content(String.valueOf(text.charAt(i))).color(TextColor.color(rgb));
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

        TextComponent.Builder normal = Component.text().content(text).color(TextColor.color(parsed.getColorRgb() & 0x00FFFFFF));
        if (parsed.isBold()) {
            normal.decoration(TextDecoration.BOLD, true);
        }
        if (parsed.isItalic()) {
            normal.decoration(TextDecoration.ITALIC, true);
        }
        return normal.build();
    }

    private void registerCommands() {
        Parameter.Value<String> action = Parameter.string().key("action").optional().build();

        Command.Parameterized command = Command.builder()
            .addParameter(action)
            .executor(context -> {
                String value = context.one(action).orElse("reload").toLowerCase();
                if ("toggle".equals(value)) {
                    enabled = !enabled;
                    if (enabled) {
                        reloadAndApply();
                    }
                    return CommandResult.success();
                }
                reloadAndApply();
                return CommandResult.success();
            })
            .build();

        Sponge.server().commandManager()
            .registrar(Command.Parameterized.class)
            .orElseThrow(() -> new IllegalStateException("No parameterized command registrar available"))
            .register(pluginContainer, command, "nekonametags", "nnt");
    }
}
