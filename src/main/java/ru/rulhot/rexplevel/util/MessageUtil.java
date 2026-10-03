package ru.rulhot.rexplevel.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@UtilityClass
public class MessageUtil {

    private final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public @NotNull Component parseText(@NotNull String text) {
        return MINI_MESSAGE.deserialize(text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public @NotNull List<Component> parseText(@NotNull List<String> lines) {
        return lines.stream()
                .map(MessageUtil::parseText)
                .toList();
    }
}
