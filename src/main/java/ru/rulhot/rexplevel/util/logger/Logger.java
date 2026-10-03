package ru.rulhot.rexplevel.util.logger;

import lombok.experimental.UtilityClass;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.util.logger.impl.LegacyLogger;

@UtilityClass
public class Logger {

    private final String INFO_PREFIX = "<green>INFO <white>";
    private final String WARN_PREFIX = "<gold>WARN <yellow>";
    private final String ERROR_PREFIX = "<dark_red>ERROR <red>";
    private final String DEBUG_PREFIX = "<aqua>DEBUG <white>";

    private volatile @Nullable ILogger logger;
    private volatile boolean debugEnabled;

    public void setup(@NotNull JavaPlugin plugin) {
        logger = new LegacyLogger(plugin.getComponentLogger());
    }

    public void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void info(@NotNull String message) {
        if (logger != null) {
            logger.info(INFO_PREFIX + message);
        }
    }

    public void warn(@NotNull String message) {
        if (logger != null) {
            logger.warn(WARN_PREFIX + message);
        }
    }

    public void error(@NotNull String message) {
        if (logger != null) {
            logger.error(ERROR_PREFIX + message);
        }
    }

    public void error(@NotNull String message, @NotNull Throwable throwable) {
        if (logger != null) {
            logger.error(ERROR_PREFIX + message, throwable);
        }
    }

    public void debug(@NotNull String message) {
        if (debugEnabled && logger != null) {
            logger.info(DEBUG_PREFIX + message);
        }
    }
}
