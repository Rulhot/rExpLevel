package ru.rulhot.rexplevel.model;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record BottleSettings(
        @NotNull Material material,
        @NotNull String name,
        @NotNull List<String> lore,
        boolean glint,
        int customModelData,
        int maxLevels,
        boolean requireGlassBottle,
        boolean returnGlassBottle,
        @NotNull RedeemMode redeem,
        @NotNull ExpGive expGive,
        boolean create,
        @NotNull Map<Integer, Look> presets
) {

    public static final int MAX_AMOUNT = 2304;

    public @NotNull Look look(int levels) {
        Look preset = presets.get(levels);
        return preset == null ? new Look(material, name, lore, glint, customModelData) : preset;
    }

    public boolean allowed(int levels) {
        return create || presets.containsKey(levels);
    }

    public record Look(
            @NotNull Material material,
            @NotNull String name,
            @NotNull List<String> lore,
            boolean glint,
            int customModelData
    ) {
    }

    public enum ExpGive {
        ADD,
        FOLD;

        public static @Nullable ExpGive find(@Nullable String value) {
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

    public enum BottleMode {
        BOTTLE,
        EXPERIENCE;

        public static @Nullable BottleMode find(@Nullable String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        public static @NotNull BottleMode parse(
                @Nullable String value,
                @NotNull BottleMode fallback
        ) {
            BottleMode mode = find(value);
            return mode == null ? fallback : mode;
        }
    }

    public enum RedeemMode {
        DRINK,
        THROW;

        public static @Nullable RedeemMode find(@Nullable String value) {
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

    public enum PackStatus {
        SUCCESS,
        NOT_ENOUGH_XP,
        NO_GLASS
    }
}
