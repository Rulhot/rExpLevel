package ru.rulhot.rexplevel.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.model.BottleSettings.BottleMode;
import ru.rulhot.rexplevel.util.SchedulerUtil;
import ru.rulhot.rexplevel.util.logger.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public final class PlayerSettingsManager {

    private static final String FILE_NAME = "data/players.yml";
    private static final String ROOT = "players";

    private final @NotNull Plugin plugin;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConcurrentMap<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();
    private final @NotNull AtomicBoolean dirty = new AtomicBoolean();
    private final @NotNull AtomicBoolean saveQueued = new AtomicBoolean();
    private final @NotNull Object saveLock = new Object();
    private volatile boolean ready;

    public void load() {
        Path file = file();
        settings.clear();
        if (Files.notExists(file)) {
            ready = true;
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            Logger.error("Файл " + FILE_NAME + " повреждён, начинаю с пустыми настройками", exception);
            backupBroken(file);
            ready = true;
            return;
        }

        ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root != null) {
            for (String key : root.getKeys(false)) {
                readPlayer(root, key);
            }
        }
        ready = true;
        Logger.debug("Загружено настроек игроков: " + settings.size());
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isAutoEnabled(@NotNull UUID playerUuid, boolean fallback) {
        if (!ready) {
            return false;
        }
        PlayerSettings current = settings.get(playerUuid);
        if (current == null || current.autoEnabled() == null) {
            return fallback;
        }
        return current.autoEnabled();
    }

    public @NotNull BottleMode getBottleMode(@NotNull UUID playerUuid, @NotNull BottleMode fallback) {
        PlayerSettings current = settings.get(playerUuid);
        if (current == null || current.mode() == null) {
            return fallback;
        }
        return current.mode();
    }

    public void setAutoEnabled(@NotNull UUID playerUuid, boolean enabled) {
        settings.compute(playerUuid, (uuid, current) -> current == null
                ? new PlayerSettings(enabled, null)
                : new PlayerSettings(enabled, current.mode()));
        markDirty();
    }

    public void setBottleMode(@NotNull UUID playerUuid, @NotNull BottleMode mode) {
        settings.compute(playerUuid, (uuid, current) -> current == null
                ? new PlayerSettings(null, mode)
                : new PlayerSettings(current.autoEnabled(), mode));
        markDirty();
    }

    public void shutdown() {
        saveIfDirty();
    }

    private void markDirty() {
        dirty.set(true);
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
        scheduler.runAsync(() -> {
            saveQueued.set(false);
            saveIfDirty();
        });
    }

    private void saveIfDirty() {
        synchronized (saveLock) {
            if (!ready || !dirty.getAndSet(false)) {
                return;
            }
            try {
                write(snapshot());
            } catch (IOException exception) {
                dirty.set(true);
                Logger.error("Не удалось сохранить " + FILE_NAME, exception);
            }
        }
    }

    private @NotNull YamlConfiguration snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        Map<UUID, PlayerSettings> sorted = new TreeMap<>(settings);
        for (Map.Entry<UUID, PlayerSettings> entry : sorted.entrySet()) {
            String path = ROOT + "." + entry.getKey();
            PlayerSettings value = entry.getValue();
            if (value.autoEnabled() != null) {
                yaml.set(path + ".auto", value.autoEnabled());
            }
            if (value.mode() != null) {
                yaml.set(path + ".mode", value.mode().name());
            }
        }
        return yaml;
    }

    private void write(@NotNull YamlConfiguration yaml) throws IOException {
        Path file = file();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void readPlayer(@NotNull ConfigurationSection root, @NotNull String key) {
        UUID playerUuid = parseUuid(key);
        ConfigurationSection section = root.getConfigurationSection(key);
        if (playerUuid == null || section == null) {
            Logger.warn("Пропущена некорректная запись в " + FILE_NAME + ": " + key);
            return;
        }
        Boolean auto = section.isBoolean("auto") ? section.getBoolean("auto") : null;
        BottleMode mode = BottleMode.find(section.getString("mode"));
        if (auto == null && mode == null) {
            return;
        }
        settings.put(playerUuid, new PlayerSettings(auto, mode));
    }

    private void backupBroken(@NotNull Path file) {
        Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(file, backup);
            Logger.warn("Повреждённый файл сохранён как " + backup.getFileName());
        } catch (IOException exception) {
            Logger.error("Не удалось переименовать повреждённый " + FILE_NAME, exception);
        }
    }

    private @NotNull Path file() {
        return plugin.getDataFolder().toPath().resolve(FILE_NAME);
    }

    private static @Nullable UUID parseUuid(@NotNull String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record PlayerSettings(@Nullable Boolean autoEnabled, @Nullable BottleMode mode) {
    }
}
