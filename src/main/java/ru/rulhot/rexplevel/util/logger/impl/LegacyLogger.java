package ru.rulhot.rexplevel.util.logger.impl;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rexplevel.util.MessageUtil;
import ru.rulhot.rexplevel.util.logger.ILogger;

@RequiredArgsConstructor
public final class LegacyLogger implements ILogger {

    private final @NotNull ComponentLogger logger;

    @Override
    public void info(@NotNull String message) {
        logger.info(MessageUtil.parseText(message));
    }

    @Override
    public void warn(@NotNull String message) {
        logger.warn(MessageUtil.parseText(message));
    }

    @Override
    public void error(@NotNull String message) {
        logger.error(MessageUtil.parseText(message));
    }

    @Override
    public void error(@NotNull String message, @NotNull Throwable throwable) {
        logger.error(MessageUtil.parseText(message), throwable);
    }
}
