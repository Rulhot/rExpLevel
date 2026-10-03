package ru.rulhot.rexplevel.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rexplevel.command.SubCommand;
import ru.rulhot.rexplevel.listener.ExperienceListener;
import ru.rulhot.rexplevel.manager.ConfigManager;
import ru.rulhot.rexplevel.util.MessageUtil;
import ru.rulhot.rexplevel.util.SchedulerUtil;
import ru.rulhot.rexplevel.util.logger.Logger;

@RequiredArgsConstructor
public final class ReloadSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ExperienceListener experienceListener;

    @Override
    public @NotNull String getName() {
        return "reload";
    }

    @Override
    public @NotNull String getPermission() {
        return "rexp.reload";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length != 0) {
            send(sender, "reload.usage");
            return true;
        }

        scheduler.runAsync(() -> reload(sender));
        return true;
    }

    private void reload(@NotNull CommandSender sender) {
        try {
            config.reload();
            Logger.setDebugEnabled(config.isDebug());
            scheduler.runGlobal(experienceListener::restart);
            sendSafely(sender, "reload.success");
        } catch (Exception exception) {
            Logger.error("Не удалось перезагрузить конфигурацию", exception);
            sendSafely(sender, "reload.failed");
        }
    }

    private void sendSafely(@NotNull CommandSender sender, @NotNull String key) {
        if (sender instanceof Player player) {
            scheduler.runFor(player, () -> send(player, key));
            return;
        }
        scheduler.runGlobal(() -> send(sender, key));
    }

    private void send(@NotNull CommandSender sender, @NotNull String key) {
        sender.sendMessage(MessageUtil.parseText(config.getCommandMessage(key)));
    }
}
