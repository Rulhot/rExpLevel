package ru.rulhot.rexplevel.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.command.SubCommand;
import ru.rulhot.rexplevel.manager.BottleManager;
import ru.rulhot.rexplevel.manager.ConfigManager;
import ru.rulhot.rexplevel.model.BottleSettings;
import ru.rulhot.rexplevel.util.MessageUtil;
import ru.rulhot.rexplevel.util.SchedulerUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
public final class GiveSubCommand implements SubCommand {

    private final @NotNull Plugin plugin;
    private final @NotNull BottleManager bottles;
    private final @NotNull ConfigManager config;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "give";
    }

    @Override
    public @NotNull String getPermission() {
        return "rexp.give";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            send(sender, "give.usage");
            return true;
        }

        Integer levels = parse(args[1]);
        Integer amount = args.length == 3 ? parse(args[2]) : 1;
        if (levels == null || amount == null) {
            send(sender, "invalid-number", Map.of("value", levels == null ? args[1] : args[2]));
            return true;
        }

        BottleSettings settings = config.bottle();
        if (levels > settings.maxLevels()) {
            send(sender, "levels-limit", Map.of("max", settings.maxLevels()));
            return true;
        }
        if (amount > BottleSettings.MAX_AMOUNT) {
            send(sender, "amount-limit", Map.of("max", BottleSettings.MAX_AMOUNT));
            return true;
        }
        scheduler.runGlobal(() -> findAndGive(sender, args[0], levels, amount));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String[] args
    ) {
        switch (args.length) {
            case 1: {
                String input = args[0].toLowerCase(Locale.ROOT);
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
            case 2: {
                return config.bottle().presets().keySet().stream()
                        .map(String::valueOf)
                        .filter(value -> value.startsWith(args[1]))
                        .toList();
            }
            default: {
                return List.of();
            }
        }
    }

    private void findAndGive(
            @NotNull CommandSender sender,
            @NotNull String playerName,
            int levels,
            int amount
    ) {
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null) {
            sendSafely(sender, "player-not-found", Map.of("player", playerName));
            return;
        }

        scheduler.runFor(target, () -> {
            bottles.give(target, levels, amount);
            send(target, "give.target", Map.of(
                    "amount", amount,
                    "levels", levels
            ));
        });
        if (!sender.equals(target)) {
            sendSafely(sender, "give.sender", Map.of(
                    "player", target.getName(),
                    "amount", amount,
                    "levels", levels
            ));
        }
    }

    private void sendSafely(
            @NotNull CommandSender sender,
            @NotNull String key,
            @NotNull Map<String, ?> placeholders
    ) {
        if (sender instanceof Player player) {
            scheduler.runFor(player, () -> send(player, key, placeholders));
            return;
        }
        send(sender, key, placeholders);
    }

    private @Nullable Integer parse(@NotNull String value) {
        try {
            int number = Integer.parseInt(value);
            return number > 0 ? number : null;
        } catch (NumberFormatException exception) {
            return null;
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
        MessageUtil.send(sender, config.formatCommandMessage(key, placeholders));
    }
}
