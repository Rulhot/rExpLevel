package ru.rulhot.rexplevel.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rexplevel.command.SubCommand;
import ru.rulhot.rexplevel.manager.ConfigManager;

@RequiredArgsConstructor
public final class HelpSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;

    @Override
    public @NotNull String getName() {
        return "help";
    }

    @Override
    public @NotNull String getPermission() {
        return "rexp.use";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            config.send(sender, "no-permission");
            return true;
        }
        config.send(sender, "help");
        return true;
    }
}
