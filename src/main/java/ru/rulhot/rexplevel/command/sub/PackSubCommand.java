package ru.rulhot.rexplevel.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.command.SubCommand;
import ru.rulhot.rexplevel.manager.BottleManager;
import ru.rulhot.rexplevel.manager.ConfigManager;
import ru.rulhot.rexplevel.manager.ExperienceBottleManager;
import ru.rulhot.rexplevel.manager.ExperienceBottleManager.Result;
import ru.rulhot.rexplevel.model.BottleSettings;
import ru.rulhot.rexplevel.model.BottleSettings.ExpGive;
import ru.rulhot.rexplevel.model.BottleSettings.PackStatus;
import ru.rulhot.rexplevel.util.ExperienceUtil;
import ru.rulhot.rexplevel.util.SchedulerUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class PackSubCommand implements SubCommand {

    private final @NotNull BottleManager bottles;
    private final @NotNull ExperienceBottleManager experienceBottles;
    private final @NotNull ConfigManager config;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "pack";
    }

    @Override
    public @NotNull String getPermission() {
        return "rexp.pack";
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
        if (args.length < 2 || args.length > 3) {
            send(sender, "pack.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("bottle")) {
            return packBottle(player, args);
        }
        if (args[0].equalsIgnoreCase("experience")) {
            return packExperience(player, args);
        }
        send(sender, "pack.usage");
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String[] args
    ) {
        return switch (args.length) {
            case 1 -> {
                String input = args[0].toLowerCase(Locale.ROOT);
                yield Stream.of("bottle", "experience")
                        .filter(value -> value.startsWith(input))
                        .toList();
            }
            case 2 -> {
                String input = args[1].toLowerCase(Locale.ROOT);
                Stream<String> values = args[0].equalsIgnoreCase("experience")
                        ? Stream.of("all")
                        : config.bottle().presets().keySet().stream().map(String::valueOf);
                yield values.filter(value -> value.startsWith(input)).toList();
            }
            default -> List.of();
        };
    }

    private boolean packBottle(@NotNull Player player, @NotNull String[] args) {
        Integer levels = parse(args[1]);
        Integer amount = args.length == 3 ? parse(args[2]) : 1;
        if (levels == null || amount == null) {
            send(player, "invalid-number", Map.of("value", levels == null ? args[1] : args[2]));
            return true;
        }
        BottleSettings settings = config.bottle();
        if (levels > settings.maxLevels()) {
            send(player, "levels-limit", Map.of("max", settings.maxLevels()));
            return true;
        }
        if (amount > BottleSettings.MAX_AMOUNT) {
            send(player, "amount-limit", Map.of("max", BottleSettings.MAX_AMOUNT));
            return true;
        }
        if (!settings.allowed(levels)) {
            send(player, "pack.levels-not-allowed", Map.of(
                    "value", levels,
                    "levels", settings.presets().keySet().stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(", "))
            ));
            return true;
        }
        scheduler.runFor(player, () -> packCustom(player, levels, amount));
        return true;
    }

    private boolean packExperience(@NotNull Player player, @NotNull String[] args) {
        if (args.length != 2) {
            send(player, "pack.usage");
            return true;
        }
        Integer requestedXp = args[1].equalsIgnoreCase("all")
                ? Integer.MAX_VALUE
                : parse(args[1]);
        if (requestedXp == null) {
            send(player, "invalid-number", Map.of("value", args[1]));
            return true;
        }
        scheduler.runFor(player, () -> packExperience(player, requestedXp));
        return true;
    }

    private void packCustom(@NotNull Player player, int levels, int amount) {
        int points = ExperienceUtil.pointsAtLevel(levels);
        long required = (long) points * amount;
        int current = ExperienceUtil.totalPoints(player);
        PackStatus status = bottles.pack(player, levels, amount);
        if (status == PackStatus.NO_GLASS) {
            send(player, "pack.no-glass");
            return;
        }
        if (status != PackStatus.SUCCESS && config.bottle().expGive() == ExpGive.ADD) {
            send(player, "pack.not-enough-levels", Map.of(
                    "required", (long) levels * amount,
                    "current", player.getLevel()
            ));
            return;
        }
        if (status != PackStatus.SUCCESS) {
            send(player, "pack.not-enough", Map.of(
                    "required", required,
                    "current", current
            ));
            return;
        }
        send(player, "pack.success", Map.of(
                "amount", amount,
                "levels", levels,
                "points", points
        ));
    }

    private void packExperience(@NotNull Player player, int requestedXp) {
        Result result = experienceBottles.convert(player, requestedXp);
        if (!result.hasEnoughXp()) {
            send(player, "pack.experience-not-enough", Map.of(
                    "xp", config.xpPerExperienceBottle()
            ));
            return;
        }
        send(player, "pack.experience-success", Map.of(
                "amount", result.bottles(),
                "spent", result.spentXp(),
                "remaining", result.remainingXp()
        ));
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
        config.send(sender, key, placeholders);
    }
}
