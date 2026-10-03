package ru.rulhot.rexplevel.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SubCommand {

    @NotNull String getName();

    @NotNull String getPermission();

    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);

    default @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String[] args
    ) {
        return List.of();
    }
}
