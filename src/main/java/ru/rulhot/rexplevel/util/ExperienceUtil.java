package ru.rulhot.rexplevel.util;

import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class ExperienceUtil {

    public final int MAX_LEVEL = 21863;

    public int pointsAtLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    public int xpToNextLevel(int level) {
        if (level >= 30) {
            return 9 * level - 158;
        }
        if (level >= 15) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }

    public int levelFromPoints(int points) {
        int safe = Math.max(0, points);
        int low = 0;
        int high = MAX_LEVEL;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (pointsAtLevel(mid) <= safe) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    public int totalPoints(@NotNull Player player) {
        int levelPoints = pointsAtLevel(player.getLevel());
        int progressPoints = Math.round(player.getExp() * player.getExpToLevel());
        return (int) Math.min(Integer.MAX_VALUE, (long) levelPoints + progressPoints);
    }

    public void setPoints(@NotNull Player player, int points) {
        int safePoints = Math.max(0, points);
        int level = levelFromPoints(safePoints);
        int intoLevel = safePoints - pointsAtLevel(level);
        int bar = xpToNextLevel(level);
        float progress = bar <= 0 ? 0.0F : Math.min(0.9999F, (float) intoLevel / (float) bar);

        player.setLevel(level);
        player.setExp(progress);
        player.setTotalExperience(safePoints);
    }

    public void addPoints(@NotNull Player player, int points) {
        long total = (long) totalPoints(player) + points;
        setPoints(player, (int) Math.min(Integer.MAX_VALUE, total));
    }

    @UtilityClass
    public class Glass {

        public int count(@NotNull Player player) {
            int amount = 0;
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (isPlain(item)) {
                    amount += item.getAmount();
                }
            }
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (isPlain(offHand)) {
                amount += offHand.getAmount();
            }
            return amount;
        }

        public boolean consume(@NotNull Player player, int amount) {
            if (amount <= 0) {
                return true;
            }
            if (count(player) < amount) {
                return false;
            }

            int left = amount;
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int slot = 0; slot < contents.length && left > 0; slot++) {
                ItemStack item = contents[slot];
                if (!isPlain(item)) {
                    continue;
                }
                int consumed = Math.min(item.getAmount(), left);
                int remaining = item.getAmount() - consumed;
                if (remaining == 0) {
                    player.getInventory().setItem(slot, null);
                } else {
                    item.setAmount(remaining);
                    player.getInventory().setItem(slot, item);
                }
                left -= consumed;
            }
            if (left > 0) {
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (isPlain(offHand)) {
                    int consumed = Math.min(offHand.getAmount(), left);
                    int remaining = offHand.getAmount() - consumed;
                    if (remaining == 0) {
                        player.getInventory().setItemInOffHand(null);
                    } else {
                        offHand.setAmount(remaining);
                        player.getInventory().setItemInOffHand(offHand);
                    }
                    left -= consumed;
                }
            }
            return left == 0;
        }

        public boolean isPlain(@Nullable ItemStack item) {
            if (item == null || item.getType() != Material.GLASS_BOTTLE) {
                return false;
            }
            if (!item.hasItemMeta()) {
                return true;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return true;
            }
            return !meta.hasDisplayName()
                    && !meta.hasItemName()
                    && !meta.hasLore()
                    && meta.getEnchants().isEmpty()
                    && !meta.hasCustomModelData()
                    && meta.getPersistentDataContainer().isEmpty();
        }
    }
}
