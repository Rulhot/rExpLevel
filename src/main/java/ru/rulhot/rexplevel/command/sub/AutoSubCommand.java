package ru.rulhot.rexplevel.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rexplevel.command.SubCommand;
import ru.rulhot.rexplevel.manager.AutoConvertManager;
import ru.rulhot.rexplevel.manager.ConfigManager;
import ru.rulhot.rexplevel.model.AutoSettings.ConfigureStatus;
import ru.rulhot.rexplevel.model.BottleSettings.BottleMode;
import ru.rulhot.rexplevel.util.MessageUtil;
import ru.rulhot.rexplevel.util.SchedulerUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
public final class AutoSubCommand implements SubCommand {

    private final @NotNull AutoConvertManager autoConvert;
    private final @NotNull ConfigManager config;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "auto";
    }

    @Override
    public @NotNull String getPermission() {
        return "rexp.auto";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "player-only");
            return true;
        }
        if (args.length != 2) {
            send(sender, "auto.usage");
            return true;
        }

        scheduler.runFor(player, () -> apply(player, args));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return List.of("on", "off").stream()
                    .filter(value -> value.startsWith(input))
                    .toList();
        }
        if (args.length != 2) {
            return List.of();
        }
        String input = args[1].toLowerCase(Locale.ROOT);
        return List.of("bottle", "experience").stream()
                .filter(value -> value.startsWith(input))
                .toList();
    }

    private void apply(@NotNull Player player, @NotNull String[] args) {
        boolean enabled = args[0].equalsIgnoreCase("on");
        if (!enabled && !args[0].equalsIgnoreCase("off")) {
            send(player, "auto.usage");
            return;
        }

        BottleMode mode = BottleMode.find(args[1]);
        if (mode == null) {
            send(player, "auto.usage");
            return;
        }

        ConfigureStatus status = autoConvert.configure(player, enabled, mode);
        var modeSettings = config.auto().mode(mode);
        switch (status) {
            case MODE_CONFLICT -> send(
                    player,
                    "auto.mode-conflict",
                    Map.of("mode", autoConvert.getMode(player).name().toLowerCase(Locale.ROOT))
            );
            case MODE_MISMATCH -> send(
                    player,
                    "auto.mode-mismatch",
                    Map.of("mode", autoConvert.getMode(player).name().toLowerCase(Locale.ROOT))
            );
            case NO_GLASS -> send(player, "auto.no-glass");
            case NOT_READY -> send(player, "not-ready");
            case ALREADY_ENABLED -> send(
                    player,
                    mode == BottleMode.BOTTLE
                            ? "auto.already-enabled-bottle"
                            : "auto.already-enabled-experience"
            );
            case ALREADY_DISABLED -> send(
                    player,
                    mode == BottleMode.BOTTLE
                            ? "auto.already-disabled-bottle"
                            : "auto.already-disabled-experience"
            );
            case ENABLED -> {
                if (mode == BottleMode.BOTTLE) {
                    send(player, "auto.enabled-bottle", Map.of(
                            "keep", modeSettings.keepLevels(),
                            "levels", modeSettings.bottleLevels()
                    ));
                } else {
                    send(player, "auto.enabled-experience", Map.of(
                            "keep", modeSettings.keepLevels()
                    ));
                }
            }
            case DISABLED -> send(
                    player,
                    mode == BottleMode.BOTTLE ? "auto.disabled-bottle" : "auto.disabled-experience"
            );
        }
    }

    private void send(@NotNull CommandSender sender, @NotNull String key) {
        send(sender, key, Map.of());
    }

    private void send(
            @NotNull CommandSender sender,
            @NotNull String key,
            @NotNull Map<String, ?> placeholders
    ) {
        sender.sendMessage(MessageUtil.parseText(config.formatCommandMessage(key, placeholders)));
    }
}
