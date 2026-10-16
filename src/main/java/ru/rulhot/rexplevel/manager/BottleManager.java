package ru.rulhot.rexplevel.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rexplevel.model.BottleSettings.ExpGive;
import ru.rulhot.rexplevel.model.BottleSettings.Look;
import ru.rulhot.rexplevel.model.BottleSettings.PackStatus;
import ru.rulhot.rexplevel.util.ExperienceUtil;
import ru.rulhot.rexplevel.util.MessageUtil;

import java.util.Map;

@RequiredArgsConstructor
public final class BottleManager {

    private final @NotNull NamespacedKey pointsKey;
    private final @NotNull NamespacedKey levelsKey;
    private final @NotNull ConfigManager config;

    public @NotNull ItemStack create(int levels, int amount) {
        Look look = config.bottle().look(levels);
        int points = ExperienceUtil.pointsAtLevel(levels);
        Map<String, Object> placeholders = Map.of(
                "levels", levels,
                "points", points
        );
        ItemStack item = new ItemStack(look.material(), amount);
        item.editMeta(meta -> {
            meta.displayName(MessageUtil.parseText(look.name(), placeholders));
            meta.lore(look.lore().stream()
                    .map(line -> MessageUtil.parseText(line, placeholders))
                    .toList());
            meta.getPersistentDataContainer().set(pointsKey, PersistentDataType.INTEGER, points);
            meta.getPersistentDataContainer().set(levelsKey, PersistentDataType.INTEGER, levels);
            if (look.customModelData() > 0) {
                meta.setCustomModelData(look.customModelData());
            }
            meta.setEnchantmentGlintOverride(look.glint());
        });
        return item;
    }

    public boolean isBottle(@Nullable ItemStack item) {
        return points(item) > 0;
    }

    public int points(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer points = item.getItemMeta().getPersistentDataContainer()
                .get(pointsKey, PersistentDataType.INTEGER);
        return points == null ? 0 : Math.max(0, points);
    }

    public @NotNull PackStatus pack(@NotNull Player player, int levels, int amount) {
        return pack(player, levels, amount, config.bottle().requireGlassBottle());
    }

    public @NotNull PackStatus pack(
            @NotNull Player player,
            int levels,
            int amount,
            boolean requireGlass
    ) {
        if (affordable(player, levels, 0) < amount) {
            return PackStatus.NOT_ENOUGH_XP;
        }
        if (requireGlass && ExperienceUtil.Glass.count(player) < amount) {
            return PackStatus.NO_GLASS;
        }

        if (requireGlass && !ExperienceUtil.Glass.consume(player, amount)) {
            return PackStatus.NO_GLASS;
        }
        if (config.bottle().expGive() == ExpGive.ADD) {
            player.setLevel(player.getLevel() - levels * amount);
        } else {
            int points = ExperienceUtil.pointsAtLevel(levels);
            ExperienceUtil.setPoints(player, ExperienceUtil.totalPoints(player) - points * amount);
        }
        give(player, levels, amount);
        return PackStatus.SUCCESS;
    }

    public int affordable(@NotNull Player player, int levels, int keepLevels) {
        if (config.bottle().expGive() == ExpGive.ADD) {
            return Math.max(0, player.getLevel() - keepLevels) / levels;
        }
        int surplus = ExperienceUtil.totalPoints(player) - ExperienceUtil.pointsAtLevel(keepLevels);
        return Math.max(0, surplus) / ExperienceUtil.pointsAtLevel(levels);
    }

    public void give(@NotNull Player player, int levels, int amount) {
        int maxStack = config.bottle().look(levels).material().getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int stackSize = Math.min(left, maxStack);
            player.getInventory().addItem(create(levels, stackSize)).values()
                    .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            left -= stackSize;
        }
    }

    public int drink(@NotNull Player player, @NotNull EquipmentSlot hand) {
        ItemStack item = player.getInventory().getItem(hand);
        int bottlePoints = points(item);
        if (bottlePoints <= 0) {
            return 0;
        }

        int bottleLevels = levels(item);
        if (!canRedeem(player, bottlePoints, bottleLevels)) {
            return -1;
        }
        int left = item.getAmount() - 1;
        if (left == 0) {
            player.getInventory().setItem(hand, null);
        } else {
            item.setAmount(left);
            player.getInventory().setItem(hand, item);
        }

        redeem(player, bottlePoints, bottleLevels);
        if (config.returnGlassBottle()) {
            player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE)).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        return bottlePoints;
    }

    public void spawnLevelOrb(@NotNull Location location, int points, int levels) {
        location.getWorld().spawn(location, ExperienceOrb.class, orb -> {
            orb.setExperience(points);
            orb.getPersistentDataContainer().set(levelsKey, PersistentDataType.INTEGER, levels);
        });
    }

    public int orbLevels(@NotNull ExperienceOrb orb) {
        Integer levels = orb.getPersistentDataContainer().get(levelsKey, PersistentDataType.INTEGER);
        return levels == null ? 0 : levels;
    }

    public int levels(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer levels = item.getItemMeta().getPersistentDataContainer()
                .get(levelsKey, PersistentDataType.INTEGER);
        return levels == null ? ExperienceUtil.levelFromPoints(points(item)) : Math.max(0, levels);
    }

    public boolean canRedeem(@NotNull Player player, int points, int levels) {
        if (config.bottle().expGive() == ExpGive.ADD) {
            return player.getLevel() <= ExperienceUtil.MAX_LEVEL - levels;
        }
        return Integer.MAX_VALUE - ExperienceUtil.totalPoints(player) >= points;
    }

    public void redeem(@NotNull Player player, int points, int levels) {
        if (config.bottle().expGive() == ExpGive.ADD) {
            player.setLevel(player.getLevel() + levels);
        } else {
            ExperienceUtil.addPoints(player, points);
        }
    }
}
