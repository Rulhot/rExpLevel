package ru.rulhot.rexplevel.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@UtilityClass
public class MessageUtil {

    private final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final String ACTION_BAR = "[actionbar]";

    public @NotNull Component parseText(@NotNull String text) {
        return MINI_MESSAGE.deserialize(text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public void send(@NotNull CommandSender sender, @NotNull String text) {
        String trimmed = text.stripLeading();
        boolean actionBar = trimmed.regionMatches(true, 0, ACTION_BAR, 0, ACTION_BAR.length());
        if (actionBar && sender instanceof Player player) {
            player.sendActionBar(parseText(trimmed.substring(ACTION_BAR.length())));
            return;
        }
        sender.sendMessage(parseText(actionBar ? trimmed.substring(ACTION_BAR.length()) : text));
    }

    public @NotNull List<Component> parseText(@NotNull List<String> lines) {
        return lines.stream()
                .map(MessageUtil::parseText)
                .toList();
    }
}
