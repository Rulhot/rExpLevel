package ru.rulhot.rexplevel.util.logger;

import lombok.experimental.UtilityClass;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.util.logger.impl.LegacyLogger;

@UtilityClass
public class Logger {

    private final String DEBUG_PREFIX = "[debug] ";

    private volatile @Nullable ILogger logger;
    private volatile boolean debugEnabled;

    public void setup(@NotNull JavaPlugin plugin) {
        logger = new LegacyLogger(plugin.getComponentLogger());
    }

    public void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public void info(@NotNull String message) {
        if (logger != null) {
            logger.info(message);
        }
    }

    public void warn(@NotNull String message) {
        if (logger != null) {
            logger.warn(message);
        }
    }

    public void error(@NotNull String message) {
        if (logger != null) {
            logger.error(message);
        }
    }

    public void error(@NotNull String message, @NotNull Throwable throwable) {
        if (logger != null) {
            logger.error(message, throwable);
        }
    }

    public void debug(@NotNull String message) {
        if (debugEnabled && logger != null) {
            logger.info(DEBUG_PREFIX + message);
        }
    }
}
