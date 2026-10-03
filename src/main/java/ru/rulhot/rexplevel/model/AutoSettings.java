package ru.rulhot.rexplevel.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record AutoSettings(
        boolean enabledByDefault,
        @NotNull BottleSettings.BottleMode defaultMode,
        @NotNull Trigger trigger,
        int intervalSeconds,
        @NotNull ModeSettings bottle,
        @NotNull ModeSettings experience
) {

    public @NotNull ModeSettings mode(@NotNull BottleSettings.BottleMode mode) {
        return mode == BottleSettings.BottleMode.BOTTLE ? bottle : experience;
    }

    public record ModeSettings(
            int keepLevels,
            int bottleLevels,
            int maxBottlesPerCycle,
            boolean requireGlassBottle,
            double hungerPerBottle
    ) {
    }

    public enum Trigger {
        EVENT,
        PERIODIC;

        public static @Nullable Trigger find(@Nullable String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    public enum ConfigureStatus {
        ENABLED,
        DISABLED,
        ALREADY_ENABLED,
        ALREADY_DISABLED,
        NO_GLASS,
        MODE_CONFLICT,
        MODE_MISMATCH,
        NOT_READY
    }

    public record ConvertResult(
            int bottles,
            @NotNull BottleSettings.BottleMode mode,
            boolean disabledNoGlass
    ) {
    }
}
