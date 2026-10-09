package ru.rulhot.rexplevel.command;

import lombok.RequiredArgsConstructor;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.manager.ConfigManager;
import ru.rulhot.rexplevel.util.MessageUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@SuppressWarnings("UnstableApiUsage")
public final class ExpCommand implements BasicCommand {

    private final @NotNull ConfigManager config;
    private volatile @NotNull List<SubCommand> subCommands = List.of();

    public void install(@NotNull List<SubCommand> commands) {
        subCommands = List.copyOf(commands);
    }

    @Override
    public void execute(
            @NotNull CommandSourceStack source,
            @NotNull String[] args
    ) {
        CommandSender sender = source.getSender();
        if (subCommands.isEmpty()) {
            send(sender, "not-ready");
            return;
        }
        if (!sender.hasPermission("rexp.use")) {
            send(sender, "no-permission");
            return;
        }

        SubCommand subCommand = args.length == 0 ? find("help") : find(args[0]);
        if (subCommand == null) {
            send(sender, "unknown");
            return;
        }
        String[] childArgs = args.length == 0
                ? new String[0]
                : Arrays.copyOfRange(args, 1, args.length);
        subCommand.execute(sender, childArgs);
    }

    @Override
    public @NotNull Collection<String> suggest(
            @NotNull CommandSourceStack source,
            @NotNull String[] args
    ) {
        CommandSender sender = source.getSender();
        if (subCommands.isEmpty() || !sender.hasPermission("rexp.use")) {
            return List.of();
        }
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return subCommands.stream()
                    .filter(command -> sender.hasPermission(command.getPermission()))
                    .map(SubCommand::getName)
                    .filter(name -> name.startsWith(input))
                    .toList();
        }

        SubCommand subCommand = find(args[0]);
        if (subCommand == null || !sender.hasPermission(subCommand.getPermission())) {
            return List.of();
        }
        return subCommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private @Nullable SubCommand find(@NotNull String name) {
        return subCommands.stream()
                .filter(command -> command.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private void send(@NotNull CommandSender sender, @NotNull String key) {
        MessageUtil.send(sender, config.getCommandMessage(key));
    }
}
