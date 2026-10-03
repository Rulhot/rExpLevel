package ru.rulhot.rexplevel.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rexplevel.util.ExperienceUtil;

@RequiredArgsConstructor
public final class ExperienceBottleManager {

    private final @NotNull ConfigManager config;

    public @NotNull Result convert(@NotNull Player player, int requestedXp) {
        return convert(player, requestedXp, Integer.MAX_VALUE, false);
    }

    public @NotNull Result convert(
            @NotNull Player player,
            int requestedXp,
            int maxBottles
    ) {
        return convert(player, requestedXp, maxBottles, false);
    }

    public @NotNull Result convert(
            @NotNull Player player,
            int requestedXp,
            int maxBottles,
            boolean requireGlass
    ) {
        int xpPerBottle = config.xpPerExperienceBottle();
        int currentXp = ExperienceUtil.totalPoints(player);
        int selectedXp = Math.min(currentXp, requestedXp);
        int glassBottles = requireGlass ? ExperienceUtil.Glass.count(player) : Integer.MAX_VALUE;
        int amount = Math.min(
                Math.min(selectedXp / xpPerBottle, glassBottles),
                maxBottles
        );
        if (amount == 0) {
            return new Result(
                    0,
                    0,
                    currentXp,
                    selectedXp >= xpPerBottle,
                    !requireGlass || glassBottles > 0
            );
        }

        int spentXp = amount * xpPerBottle;
        if (requireGlass) {
            ExperienceUtil.Glass.consume(player, amount);
        }
        ExperienceUtil.setPoints(player, currentXp - spentXp);
        giveBottles(player, amount);
        return new Result(
                amount,
                spentXp,
                currentXp - spentXp,
                true,
                true
        );
    }

    private void giveBottles(@NotNull Player player, int amount) {
        int left = amount;
        int maxStack = Material.EXPERIENCE_BOTTLE.getMaxStackSize();
        while (left > 0) {
            int stackSize = Math.min(left, maxStack);
            ItemStack bottles = new ItemStack(Material.EXPERIENCE_BOTTLE, stackSize);
            player.getInventory().addItem(bottles).values()
                    .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            left -= stackSize;
        }
    }

    public record Result(
            int bottles,
            int spentXp,
            int remainingXp,
            boolean hasEnoughXp,
            boolean hasGlassBottles
    ) {
    }
}
