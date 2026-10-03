package ru.rulhot.rexplevel.placeholder;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.manager.AutoConvertManager;
import ru.rulhot.rexplevel.manager.ConfigManager;

import java.util.Locale;

@RequiredArgsConstructor
public final class RExpExpansion extends PlaceholderExpansion {

    private final @NotNull String version;
    private final @NotNull AutoConvertManager autoConvert;
    private final @NotNull ConfigManager config;

    @Override
    public @NotNull String getIdentifier() {
        return "rexp";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Rulhot";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offline, @NotNull String params) {
        if (offline == null) {
            return "";
        }
        Player player = offline.getPlayer();
        if (player == null || !player.isOnline()) {
            return "";
        }

        String key = params.toLowerCase(Locale.ROOT);
        if (!autoConvert.isLoaded(player)) {
            return "";
        }
        return switch (key) {
            case "auto" -> autoConvert.isEnabled(player)
                    ? config.placeholder("auto-on")
                    : config.placeholder("auto-off");
            case "mode" -> config.placeholder("mode-" + autoConvert.getMode(player).name().toLowerCase(Locale.ROOT));
            default -> null;
        };
    }
}
