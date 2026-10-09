package ru.rulhot.rexplevel.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.model.AutoSettings.ConfigureStatus;
import ru.rulhot.rexplevel.model.AutoSettings.ConvertResult;
import ru.rulhot.rexplevel.model.AutoSettings.ModeSettings;
import ru.rulhot.rexplevel.model.BottleSettings;
import ru.rulhot.rexplevel.model.BottleSettings.BottleMode;
import ru.rulhot.rexplevel.model.BottleSettings.PackStatus;
import ru.rulhot.rexplevel.util.ExperienceUtil;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class AutoConvertManager {

    private final @NotNull PlayerSettingsManager playerSettings;
    private final @NotNull ConfigManager config;
    private final @NotNull BottleManager bottles;
    private final @NotNull ExperienceBottleManager experienceBottles;
    private final @NotNull Set<UUID> converting = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(@NotNull Player player) {
        return playerSettings.isAutoEnabled(player.getUniqueId(), config.auto().enabledByDefault());
    }

    public boolean isLoaded(@NotNull Player player) {
        return playerSettings.isReady();
    }

    public @NotNull BottleMode getMode(@NotNull Player player) {
        return playerSettings.getBottleMode(player.getUniqueId(), config.auto().defaultMode());
    }

    public int bottleLevels(@NotNull Player player) {
        Integer chosen = playerSettings.getBottleLevels(player.getUniqueId());
        BottleSettings bottle = config.bottle();
        if (chosen != null && chosen <= bottle.maxLevels() && bottle.allowed(chosen)) {
            return chosen;
        }
        return config.auto().bottle().bottleLevels();
    }

    public @NotNull ConfigureStatus configure(
            @NotNull Player player,
            boolean enabled,
            @NotNull BottleMode mode,
            @Nullable Integer levels
    ) {
        UUID playerUuid = player.getUniqueId();
        if (!playerSettings.isReady()) {
            return ConfigureStatus.NOT_READY;
        }
        boolean currentlyEnabled = isEnabled(player);
        BottleMode currentMode = getMode(player);
        ModeSettings modeSettings = config.auto().mode(mode);

        if (enabled) {
            boolean sameLevels = levels == null || levels == bottleLevels(player);
            if (currentlyEnabled && currentMode == mode && sameLevels) {
                return ConfigureStatus.ALREADY_ENABLED;
            }
            if (currentlyEnabled && currentMode == mode) {
                playerSettings.setBottleLevels(playerUuid, levels);
                return ConfigureStatus.ENABLED;
            }
            if (currentlyEnabled) {
                return ConfigureStatus.MODE_CONFLICT;
            }
            if (modeSettings.requireGlassBottle() && ExperienceUtil.Glass.count(player) <= 0) {
                return ConfigureStatus.NO_GLASS;
            }
            if (levels != null) {
                playerSettings.setBottleLevels(playerUuid, levels);
            }
            playerSettings.setBottleMode(playerUuid, mode);
            playerSettings.setAutoEnabled(playerUuid, true);
            return ConfigureStatus.ENABLED;
        }

        if (!currentlyEnabled) {
            return ConfigureStatus.ALREADY_DISABLED;
        }
        if (currentMode != mode) {
            return ConfigureStatus.MODE_MISMATCH;
        }
        playerSettings.setBottleMode(playerUuid, mode);
        playerSettings.setAutoEnabled(playerUuid, false);
        return ConfigureStatus.DISABLED;
    }

    public boolean isConverting(@NotNull UUID playerUuid) {
        return converting.contains(playerUuid);
    }

    public @NotNull ConvertResult convert(@NotNull Player player) {
        BottleMode mode = getMode(player);
        if (!playerSettings.isReady()
                || !isEnabled(player)
                || !player.hasPermission("rexp.auto")) {
            return new ConvertResult(0, mode, false);
        }

        ModeSettings settings = config.auto().mode(mode);
        if (settings.requireGlassBottle() && ExperienceUtil.Glass.count(player) <= 0) {
            playerSettings.setAutoEnabled(player.getUniqueId(), false);
            return new ConvertResult(0, mode, true);
        }

        int surplus = surplusPoints(player, settings);
        if (surplus == 0) {
            return new ConvertResult(0, mode, false);
        }

        UUID playerUuid = player.getUniqueId();
        converting.add(playerUuid);
        try {
            int amount = switch (mode) {
                case BOTTLE -> packCustom(player, settings);
                case EXPERIENCE -> packExperience(player, surplus, settings);
            };
            if (amount == 0
                    && settings.requireGlassBottle()
                    && ExperienceUtil.Glass.count(player) <= 0) {
                playerSettings.setAutoEnabled(playerUuid, false);
                return new ConvertResult(0, mode, true);
            }
            applyHunger(player, amount, settings.hungerPerBottle());
            return new ConvertResult(amount, mode, false);
        } finally {
            converting.remove(playerUuid);
        }
    }

    public boolean hasConvertibleSurplus(@NotNull Player player) {
        if (!playerSettings.isReady()
                || !isEnabled(player)
                || !player.hasPermission("rexp.auto")
                || isConverting(player.getUniqueId())) {
            return false;
        }
        BottleMode mode = getMode(player);
        ModeSettings settings = config.auto().mode(mode);
        if (settings.requireGlassBottle() && ExperienceUtil.Glass.count(player) <= 0) {
            return false;
        }
        if (settings.hungerPerBottle() > 0.0D
                && availableHunger(player) < settings.hungerPerBottle()) {
            return false;
        }
        int surplus = surplusPoints(player, settings);
        return switch (mode) {
            case BOTTLE -> bottles.affordable(player, bottleLevels(player), settings.keepLevels()) > 0;
            case EXPERIENCE -> surplus >= config.xpPerExperienceBottle();
        };
    }

    private int surplusPoints(@NotNull Player player, @NotNull ModeSettings settings) {
        int current = ExperienceUtil.totalPoints(player);
        int keep = ExperienceUtil.pointsAtLevel(settings.keepLevels());
        return Math.max(0, current - keep);
    }

    public void stop() {
        converting.clear();
    }

    private int packCustom(@NotNull Player player, @NotNull ModeSettings settings) {
        int levels = bottleLevels(player);
        int amount = Math.min(
                bottles.affordable(player, levels, settings.keepLevels()),
                settings.maxBottlesPerCycle()
        );
        amount = limitByGlass(player, amount, settings);
        amount = limitByHunger(player, amount, settings.hungerPerBottle());
        if (amount == 0) {
            return 0;
        }
        return bottles.pack(
                player,
                levels,
                amount,
                settings.requireGlassBottle()
        ) == PackStatus.SUCCESS ? amount : 0;
    }

    private int packExperience(
            @NotNull Player player,
            int surplus,
            @NotNull ModeSettings settings
    ) {
        int maxBottles = limitByHunger(
                player,
                settings.maxBottlesPerCycle(),
                settings.hungerPerBottle()
        );
        if (maxBottles == 0) {
            return 0;
        }
        return experienceBottles.convert(player, surplus, maxBottles, false).bottles();
    }

    private int limitByGlass(
            @NotNull Player player,
            int amount,
            @NotNull ModeSettings settings
    ) {
        if (!settings.requireGlassBottle()) {
            return amount;
        }
        return Math.min(amount, ExperienceUtil.Glass.count(player));
    }

    private int limitByHunger(@NotNull Player player, int amount, double hungerPerBottle) {
        if (hungerPerBottle <= 0.0D || amount <= 0) {
            return amount;
        }
        return Math.min(amount, (int) (availableHunger(player) / hungerPerBottle));
    }

    private double availableHunger(@NotNull Player player) {
        return player.getFoodLevel() + (double) player.getSaturation();
    }

    private void applyHunger(@NotNull Player player, int amount, double hungerPerBottle) {
        if (hungerPerBottle <= 0.0D || amount <= 0) {
            return;
        }
        double cost = amount * hungerPerBottle;
        float saturation = player.getSaturation();
        int food = player.getFoodLevel();

        if (saturation >= cost) {
            player.setSaturation((float) (saturation - cost));
            return;
        }

        double remainingCost = cost - saturation;
        player.setSaturation(0.0F);
        int newFood = Math.max(0, (int) Math.floor(food - remainingCost));
        player.setFoodLevel(Math.min(20, newFood));
    }
}
