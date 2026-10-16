package ru.rulhot.rexplevel.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

@UtilityClass
public class MessageUtil {

    private final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final String TAG_PREFIX = "rexp_";

    public @NotNull Component parseText(@NotNull String text) {
        return MINI_MESSAGE.deserialize(text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public @NotNull Component parseText(@NotNull String text, @NotNull Map<String, ?> placeholders) {
        TagResolver.Builder resolvers = TagResolver.builder();
        String prepared = text;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            String tag = TAG_PREFIX + entry.getKey().toLowerCase(Locale.ROOT).replace('-', '_');
            prepared = prepared.replace("{" + entry.getKey() + "}", "<" + tag + ">");
            resolvers.resolver(entry.getValue() instanceof ComponentLike component
                    ? Placeholder.component(tag, component)
                    : Placeholder.unparsed(tag, String.valueOf(entry.getValue())));
        }
        return MINI_MESSAGE.deserialize(prepared, resolvers.build())
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
