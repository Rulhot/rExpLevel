package ru.rulhot.rexplevel.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rexplevel.command.SubCommand;
import ru.rulhot.rexplevel.manager.ConfigManager;
import ru.rulhot.rexplevel.util.MessageUtil;

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
            sender.sendMessage(MessageUtil.parseText(config.getCommandMessage("no-permission")));
            return true;
        }
        config.getCommandMessages("help").stream()
                .map(MessageUtil::parseText)
                .forEach(sender::sendMessage);
        return true;
    }
}
